package de.steppicrew.kladde.adx

import de.steppicrew.adx.AdxCodec
import de.steppicrew.adx.AdxDelete
import de.steppicrew.adx.AdxUpsert
import de.steppicrew.adx.receiver.AdxBusyException
import de.steppicrew.adx.receiver.AdxConsent
import de.steppicrew.adx.receiver.AdxStore
import de.steppicrew.adx.receiver.AdxStreamGrant
import de.steppicrew.kladde.data.DataPointRepository
import de.steppicrew.kladde.data.SubTypeLabelRepository
import de.steppicrew.kladde.data.TrackerRepository
import de.steppicrew.kladde.model.ExternalSource
import de.steppicrew.kladde.model.Ids
import de.steppicrew.kladde.model.SubTypeLabel
import de.steppicrew.kladde.model.SubTypeLabelStatus
import de.steppicrew.kladde.model.Tracker
import de.steppicrew.kladde.model.VaultLock
import java.time.Instant

/**
 * Stores ADX entries as data points of the tracker each stream is mapped to.
 *
 * A label ([AdxUpsert.sub]) the tracker has no record of yet becomes a label record. Unless the
 * sender described it completely, it is open and goes to [listener], so the user can say what it
 * is; what the sender says about a label is kept current (docs/decisions/0002).
 *
 * While the vault is locked every call answers BUSY and the sender keeps its outbox
 * (option B in docs/decisions/0001). TODO(option A): accept while locked by writing
 * sealed-box pending records and applying the rev rule again when they merge on unlock.
 */
class KladdeAdxStore(
    private val vault: VaultLock,
    private val trackers: TrackerRepository,
    private val points: DataPointRepository,
    private val labels: SubTypeLabelRepository,
    private val clock: () -> Instant = Instant::now,
    private val listener: LabelListener = object : LabelListener {},
) : AdxStore {
    override fun currentRev(
        sender: AdxConsent,
        stream: AdxStreamGrant,
        id: String,
    ): Long? {
        requireUnlocked()
        return points.externalRev(sender.packageName, stream.proposal.key, id)
    }

    override fun upsert(
        sender: AdxConsent,
        stream: AdxStreamGrant,
        op: AdxUpsert,
    ) {
        requireUnlocked()
        val tracker = trackerFor(sender, stream)
        val withSub = tracker.withSubType(op.sub)
        if (withSub !== tracker) trackers.save(withSub)
        points.upsertExternal(AdxMapping.dataPoint(sender.packageName, tracker.id, tracker.unit, op, AdxCodec.encodeOp(op)))
        op.sub?.let { key -> noteLabel(sender, stream, tracker, key) }
    }

    /**
     * The sender renamed subs, changed their defaults or added subs; [sender] still holds the
     * stream as it was. Known labels follow; a sub new since then is recorded and asked about
     * now, as a new drink is the moment the user knows what it is. Subs the sender listed before
     * but no entry used yet are left until an entry does.
     */
    override fun describe(
        sender: AdxConsent,
        stream: AdxStreamGrant,
    ) {
        requireUnlocked()
        var tracker = trackerFor(sender, stream)
        val before =
            sender.streams[stream.proposal.key]
                ?.proposal
                ?.subKeys
                .orEmpty()
                .toSet()
        for (sub in stream.proposal.subs) {
            val known = labels.findByKey(tracker.id, sub.key)
            when {
                known != null -> refresh(known, stream)
                sub.key !in before -> {
                    tracker = tracker.withSubType(sub.key).also { if (it !== tracker) trackers.save(it) }
                    noteLabel(sender, stream, tracker, sub.key)
                }
            }
        }
    }

    /** Records a label the first time an entry uses it, else keeps what the sender says about it current. */
    private fun noteLabel(
        sender: AdxConsent,
        stream: AdxStreamGrant,
        tracker: Tracker,
        key: String,
    ) {
        labels.findByKey(tracker.id, key)?.let { return refresh(it, stream) }
        val label =
            SubTypeLabel(
                id = Ids.newId(),
                trackerId = tracker.id,
                key = key,
                status = SubTypeLabelStatus.OPEN,
                createdAt = clock(),
                sourcePackage = sender.packageName,
                sourceStream = stream.proposal.key,
            ).describedBy(stream.proposal)
        labels
            .recordIfNew(label)
            ?.takeIf { it.status == SubTypeLabelStatus.OPEN }
            ?.let { listener.onNewLabel(NewLabel(it, tracker.name, sender.label)) }
    }

    private fun refresh(
        label: SubTypeLabel,
        stream: AdxStreamGrant,
    ) {
        val updated = label.describedBy(stream.proposal)
        if (updated == label) return
        labels.update(updated)
        if (label.status == SubTypeLabelStatus.OPEN && updated.status != SubTypeLabelStatus.OPEN) listener.onLabelSettled(label.id)
    }

    override fun delete(
        sender: AdxConsent,
        stream: AdxStreamGrant,
        op: AdxDelete,
    ) {
        requireUnlocked()
        val tracker = trackerFor(sender, stream)
        points.deleteExternal(Ids.newId(), tracker.id, ExternalSource(sender.packageName, op.stream, op.id, op.rev))
    }

    private fun requireUnlocked() {
        if (!vault.isUnlocked) throw AdxBusyException("vault locked")
    }

    /**
     * The tracker the user mapped the stream to. If it was deleted meanwhile, or the
     * grant has no target, it is recreated from the proposal once and then found again
     * by its source.
     */
    private fun trackerFor(
        sender: AdxConsent,
        stream: AdxStreamGrant,
    ): Tracker =
        stream.target?.let(trackers::find)
            ?: trackers.findBySource(sender.packageName, stream.proposal.key)
            ?: AdxMapping
                .newTracker(sender.packageName, stream.proposal, clock(), stream.target ?: Ids.newId())
                .also(trackers::save)
}

/** What the store tells the app about labels; both default to doing nothing. */
interface LabelListener {
    /** A label to ask the user about. */
    fun onNewLabel(new: NewLabel) = Unit

    /** An open label that needs no answer any more, because the sender described it completely. */
    fun onLabelSettled(labelId: String) = Unit
}

/** A label seen for the first time, with what a notification about it shows. */
data class NewLabel(
    val label: SubTypeLabel,
    val trackerName: String,
    val senderLabel: String,
)

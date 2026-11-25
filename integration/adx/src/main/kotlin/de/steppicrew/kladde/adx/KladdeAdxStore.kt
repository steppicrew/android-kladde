package de.steppicrew.kladde.adx

import de.steppicrew.adx.AdxCodec
import de.steppicrew.adx.AdxDelete
import de.steppicrew.adx.AdxUpsert
import de.steppicrew.adx.receiver.AdxBusyException
import de.steppicrew.adx.receiver.AdxConsent
import de.steppicrew.adx.receiver.AdxStore
import de.steppicrew.adx.receiver.AdxStreamGrant
import de.steppicrew.kladde.data.DataPointRepository
import de.steppicrew.kladde.data.TrackerRepository
import de.steppicrew.kladde.model.ExternalSource
import de.steppicrew.kladde.model.Ids
import de.steppicrew.kladde.model.Tracker
import de.steppicrew.kladde.model.VaultLock
import java.time.Instant

/**
 * Stores ADX entries as data points of the tracker each stream is mapped to.
 *
 * While the vault is locked every call answers BUSY and the sender keeps its outbox
 * (option B in docs/decisions/0001). TODO(option A): accept while locked by writing
 * sealed-box pending records and applying the rev rule again when they merge on unlock.
 */
class KladdeAdxStore(
    private val vault: VaultLock,
    private val trackers: TrackerRepository,
    private val points: DataPointRepository,
    private val clock: () -> Instant = Instant::now,
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

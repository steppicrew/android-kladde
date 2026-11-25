package de.steppicrew.kladde.adx

import de.steppicrew.adx.AdxKind
import de.steppicrew.adx.AdxStreamProposal
import de.steppicrew.adx.AdxUpsert
import de.steppicrew.kladde.model.DataPoint
import de.steppicrew.kladde.model.ExternalSource
import de.steppicrew.kladde.model.Ids
import de.steppicrew.kladde.model.Tracker
import de.steppicrew.kladde.model.TrackerSource
import de.steppicrew.kladde.model.TrackerType
import java.time.Instant

/** How ADX streams become Kladde trackers and data points. */
internal object AdxMapping {
    /** The tracker type for a stream kind, or null for a kind this version doesn't know. */
    fun trackerType(kind: String): TrackerType? =
        when (AdxKind.fromWire(kind)) {
            AdxKind.COUNTER -> TrackerType.COUNTER
            AdxKind.QUANTITY -> TrackerType.NUMBER
            AdxKind.DURATION -> TrackerType.DURATION
            AdxKind.SCALE -> TrackerType.SCALE
            AdxKind.RANGE -> TrackerType.RANGE
            null -> null
        }

    /** An existing tracker can take a stream when type and unit match exactly; no unit conversion yet. */
    fun accepts(
        tracker: Tracker,
        proposal: AdxStreamProposal,
    ): Boolean = tracker.type == trackerType(proposal.kind) && tracker.unit == proposal.unit

    fun newTracker(
        packageName: String,
        proposal: AdxStreamProposal,
        now: Instant,
        id: String = Ids.newId(),
        name: String = proposal.name,
    ): Tracker =
        Tracker(
            id = id,
            name = name,
            type = trackerType(proposal.kind) ?: TrackerType.NUMBER,
            unit = proposal.unit,
            createdAt = now,
            subTypes = proposal.subKeys,
            scaleMin = proposal.min,
            scaleMax = proposal.max,
            source = TrackerSource(packageName, proposal.key),
        )

    fun dataPoint(
        packageName: String,
        trackerId: String,
        unit: String?,
        op: AdxUpsert,
        raw: String,
    ): DataPoint =
        DataPoint(
            id = Ids.newId(),
            trackerId = trackerId,
            at = op.at,
            zone = op.zone,
            value = op.value,
            high = op.high,
            end = op.end,
            unit = unit,
            subType = op.sub,
            note = op.note,
            source = ExternalSource(packageName, op.stream, op.id, op.rev, raw),
        )
}

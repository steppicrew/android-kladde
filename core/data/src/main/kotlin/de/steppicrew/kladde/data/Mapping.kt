package de.steppicrew.kladde.data

import de.steppicrew.kladde.model.DataPoint
import de.steppicrew.kladde.model.ExternalSource
import de.steppicrew.kladde.model.Tracker
import de.steppicrew.kladde.model.TrackerSource
import de.steppicrew.kladde.model.TrackerType
import java.time.Instant
import java.time.ZoneId

internal fun TrackerEntity.toModel() =
    Tracker(
        id = id,
        name = name,
        type = TrackerType.valueOf(type),
        unit = unit,
        createdAt = Instant.ofEpochMilli(createdAt),
        subTypes = if (subTypes.isEmpty()) emptyList() else subTypes.split(SUB_TYPE_SEPARATOR),
        scaleMin = scaleMin,
        scaleMax = scaleMax,
        source = if (sourcePackage != null && sourceStream != null) TrackerSource(sourcePackage, sourceStream) else null,
    )

internal fun Tracker.toEntity() =
    TrackerEntity(
        id = id,
        name = name,
        type = type.name,
        unit = unit,
        createdAt = createdAt.toEpochMilli(),
        subTypes = subTypes.joinToString(SUB_TYPE_SEPARATOR),
        scaleMin = scaleMin,
        scaleMax = scaleMax,
        sourcePackage = source?.packageName,
        sourceStream = source?.stream,
    )

internal fun DataPointEntity.toModel() =
    DataPoint(
        id = id,
        trackerId = trackerId,
        at = Instant.ofEpochMilli(at),
        zone = ZoneId.of(zone),
        value = value,
        high = high,
        end = end?.let(Instant::ofEpochMilli),
        unit = unit,
        subType = subType,
        note = note,
        // The source columns are written together, so one of them decides.
        source =
            sourceId?.let {
                ExternalSource(checkNotNull(sourcePackage), checkNotNull(sourceStream), it, checkNotNull(sourceRev), sourceRaw)
            },
    )

internal fun DataPoint.toEntity(deleted: Boolean = false) =
    DataPointEntity(
        id = id,
        trackerId = trackerId,
        at = at.toEpochMilli(),
        zone = zone.id,
        value = value,
        high = high,
        end = end?.toEpochMilli(),
        unit = unit,
        subType = subType,
        note = note,
        sourcePackage = source?.packageName,
        sourceStream = source?.stream,
        sourceId = source?.externalId,
        sourceRev = source?.rev,
        sourceRaw = source?.raw,
        deleted = deleted,
    )

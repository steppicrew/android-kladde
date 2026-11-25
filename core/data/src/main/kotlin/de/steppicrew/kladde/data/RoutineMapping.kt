package de.steppicrew.kladde.data

import de.steppicrew.kladde.model.CompletionStatus
import de.steppicrew.kladde.model.Recurrence
import de.steppicrew.kladde.model.Routine
import de.steppicrew.kladde.model.RoutineCompletion
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

internal fun RoutineEntity.toModel(): Routine? =
    Recurrence.fromRrule(rrule)?.let { recurrence ->
        Routine(
            id = id,
            title = title,
            recurrence = recurrence,
            startDate = LocalDate.parse(startDate),
            time = time?.let(LocalTime::parse),
            checkable = checkable,
            createdAt = Instant.ofEpochMilli(createdAt),
        )
    }

internal fun Routine.toEntity() =
    RoutineEntity(
        id = id,
        title = title,
        rrule = recurrence.toRrule(),
        startDate = startDate.toString(),
        time = time?.toString(),
        checkable = checkable,
        createdAt = createdAt.toEpochMilli(),
    )

internal fun RoutineCompletionEntity.toModel() =
    RoutineCompletion(
        routineId = routineId,
        date = LocalDate.parse(date),
        status = CompletionStatus.valueOf(status),
        recordedAt = Instant.ofEpochMilli(recordedAt),
        zone = ZoneId.of(zone),
    )

internal fun RoutineCompletion.toEntity() =
    RoutineCompletionEntity(
        routineId = routineId,
        date = date.toString(),
        status = status.name,
        recordedAt = recordedAt.toEpochMilli(),
        zone = zone.id,
    )

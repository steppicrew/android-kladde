package de.steppicrew.kladde.data

import de.steppicrew.kladde.model.CompletionStatus
import de.steppicrew.kladde.model.Recurrence
import de.steppicrew.kladde.model.Routine
import de.steppicrew.kladde.model.RoutineCompletion
import de.steppicrew.kladde.model.RoutinePause
import de.steppicrew.kladde.model.ScheduleVersion
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** The routine with its earlier schedules; an earlier one whose rule this version can't read is left out. */
internal fun RoutineEntity.toModel(versions: List<ScheduleVersionEntity> = emptyList()): Routine? =
    Recurrence.fromRrule(rrule)?.let { recurrence ->
        Routine(
            id = id,
            title = title,
            recurrence = recurrence,
            startDate = LocalDate.parse(startDate),
            time = time?.let(LocalTime::parse),
            checkable = checkable,
            createdAt = Instant.ofEpochMilli(createdAt),
            ignoresPauses = ignoresPauses,
            earlier =
                versions.mapNotNull { version ->
                    Recurrence.fromRrule(version.rrule)?.let {
                        ScheduleVersion(LocalDate.parse(version.validFrom), it, version.time?.let(LocalTime::parse))
                    }
                },
        )
    }

internal fun Routine.versionEntities() =
    earlier.map { ScheduleVersionEntity(id, it.validFrom.toString(), it.recurrence.toRrule(), it.time?.toString()) }

internal fun Routine.toEntity() =
    RoutineEntity(
        id = id,
        title = title,
        rrule = recurrence.toRrule(),
        startDate = startDate.toString(),
        time = time?.toString(),
        checkable = checkable,
        createdAt = createdAt.toEpochMilli(),
        ignoresPauses = ignoresPauses,
    )

internal fun RoutineCompletionEntity.toModel() =
    RoutineCompletion(
        routineId = routineId,
        date = LocalDate.parse(date),
        status = CompletionStatus.valueOf(status),
        recordedAt = Instant.ofEpochMilli(recordedAt),
        zone = ZoneId.of(zone),
        doneAt = Instant.ofEpochMilli(doneAt ?: recordedAt),
    )

internal fun RoutineCompletion.toEntity() =
    RoutineCompletionEntity(
        routineId = routineId,
        date = date.toString(),
        status = status.name,
        recordedAt = recordedAt.toEpochMilli(),
        zone = zone.id,
        doneAt = doneAt.toEpochMilli().takeIf { doneAt != recordedAt },
    )

internal fun RoutinePauseEntity.toModel() =
    RoutinePause(
        id = id,
        from = LocalDate.parse(fromDate),
        until = untilDate?.let(LocalDate::parse),
        routineIds = if (routineIds.isEmpty()) emptySet() else routineIds.split(SUB_TYPE_SEPARATOR).toSet(),
        note = note,
    )

internal fun RoutinePause.toEntity() =
    RoutinePauseEntity(
        id = id,
        fromDate = from.toString(),
        untilDate = until?.toString(),
        routineIds = routineIds.sorted().joinToString(SUB_TYPE_SEPARATOR),
        note = note,
    )

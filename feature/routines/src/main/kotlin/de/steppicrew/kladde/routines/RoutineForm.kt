package de.steppicrew.kladde.routines

import de.steppicrew.kladde.model.BlockRule
import de.steppicrew.kladde.model.Recurrence
import de.steppicrew.kladde.model.Routine
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** How often, as the form offers it. */
internal enum class Repeat {
    EVERY_DAY,
    WEEKDAYS,
    EVERY_N_DAYS,
}

/** What the routine form holds while it is being filled in. */
internal data class RoutineDraft(
    val title: String = "",
    val repeat: Repeat = Repeat.EVERY_DAY,
    val days: Set<DayOfWeek> = emptySet(),
    val interval: String = "2",
    val time: LocalTime? = null,
    val checkable: Boolean = true,
    /** The ids of the routines this one gives way to. */
    val blockers: Set<String> = emptySet(),
) {
    /** The schedule the form describes, or null while it is incomplete. */
    val recurrence: Recurrence?
        get() =
            when (repeat) {
                Repeat.EVERY_DAY -> Recurrence.Daily
                Repeat.WEEKDAYS -> days.takeIf { it.isNotEmpty() }?.let(Recurrence::Weekly)
                Repeat.EVERY_N_DAYS ->
                    interval
                        .trim()
                        .toIntOrNull()
                        ?.takeIf { it in 2..MAX_INTERVAL }
                        ?.let(Recurrence::EveryNDays)
            }

    val isComplete: Boolean get() = title.isNotBlank() && recurrence != null

    /**
     * The routine this draft makes, in place of [editing] if given. A changed schedule starts
     * [today], so every N days counts from the day it was set; an unchanged one keeps its start.
     */
    fun toRoutine(
        id: String,
        today: LocalDate,
        now: Instant,
        editing: Routine? = null,
    ): Routine? {
        val recurrence = recurrence?.takeIf { title.isNotBlank() } ?: return null
        val start = editing?.startDate?.takeIf { editing.recurrence == recurrence } ?: today
        return Routine(
            id = editing?.id ?: id,
            title = title.trim(),
            recurrence = recurrence,
            startDate = start,
            time = time,
            checkable = checkable,
            createdAt = editing?.createdAt ?: now,
        )
    }

    companion object {
        const val MAX_INTERVAL = 365

        /** The form for an existing [routine], with what it gives way to under [rules]. */
        fun of(
            routine: Routine,
            rules: List<BlockRule> = emptyList(),
        ): RoutineDraft =
            when (val recurrence = routine.recurrence) {
                Recurrence.Daily -> RoutineDraft(routine.title, Repeat.EVERY_DAY)
                is Recurrence.Weekly -> RoutineDraft(routine.title, Repeat.WEEKDAYS, days = recurrence.days)
                is Recurrence.EveryNDays -> RoutineDraft(routine.title, Repeat.EVERY_N_DAYS, interval = recurrence.interval.toString())
            }.copy(
                time = routine.time,
                checkable = routine.checkable,
                blockers = rules.filter { it.routineId == routine.id }.mapTo(HashSet()) { it.blockerId },
            )
    }
}

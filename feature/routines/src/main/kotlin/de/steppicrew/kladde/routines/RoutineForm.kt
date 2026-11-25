package de.steppicrew.kladde.routines

import de.steppicrew.kladde.model.BlockRule
import de.steppicrew.kladde.model.NthWeekday
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

    /** E.g. the 1st and 3rd Monday: places in the month ([RoutineDraft.nths]) times weekdays. */
    MONTHLY_WEEKDAYS,

    /** E.g. the 1st and 15th ([RoutineDraft.monthDays]). */
    MONTHLY_DAYS,
}

/** What the routine form holds while it is being filled in. */
internal data class RoutineDraft(
    val title: String = "",
    val repeat: Repeat = Repeat.EVERY_DAY,
    val days: Set<DayOfWeek> = emptySet(),
    val interval: String = "2",
    /** For [Repeat.MONTHLY_WEEKDAYS]: 1–4, or [NthWeekday.LAST]; combined with every one of [days]. */
    val nths: Set<Int> = setOf(1),
    /** For [Repeat.MONTHLY_DAYS]: 1–31, or [Recurrence.MonthlyByDay.LAST_DAY]. */
    val monthDays: Set<Int> = emptySet(),
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
                Repeat.MONTHLY_WEEKDAYS ->
                    nths
                        .flatMap { n -> days.map { NthWeekday(n, it) } }
                        .toSet()
                        .takeIf { it.isNotEmpty() }
                        ?.let(Recurrence::MonthlyByWeekday)
                Repeat.MONTHLY_DAYS -> monthDays.takeIf { it.isNotEmpty() }?.let(Recurrence::MonthlyByDay)
            }

    val isComplete: Boolean get() = title.isNotBlank() && recurrence != null

    /**
     * The routine this draft makes, in place of [editing] if given. A new one starts [today]. For
     * an edit, a changed repeat or time applies [from] the event it was made at; the days before
     * keep their schedule. Name, ticking off and conditions are not schedule and change throughout.
     */
    fun toRoutine(
        id: String,
        today: LocalDate,
        now: Instant,
        editing: Routine? = null,
        from: LocalDate = today,
    ): Routine? {
        val recurrence = recurrence?.takeIf { title.isNotBlank() } ?: return null
        val scheduled =
            when {
                editing == null -> Routine(id, title, recurrence, today, time, checkable, now)
                editing.recurrence == recurrence && editing.time == time -> editing
                else -> editing.withSchedule(recurrence, time, from)
            }
        return scheduled.copy(title = title.trim(), checkable = checkable)
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
                is Recurrence.MonthlyByWeekday ->
                    RoutineDraft(
                        routine.title,
                        Repeat.MONTHLY_WEEKDAYS,
                        days = recurrence.days.mapTo(HashSet()) { it.day },
                        nths = recurrence.days.mapTo(HashSet()) { it.n },
                    )
                is Recurrence.MonthlyByDay -> RoutineDraft(routine.title, Repeat.MONTHLY_DAYS, monthDays = recurrence.days)
            }.copy(
                time = routine.time,
                checkable = routine.checkable,
                blockers = rules.filter { it.routineId == routine.id }.mapTo(HashSet()) { it.blockerId },
            )
    }
}

package de.steppicrew.kladde.routines

import de.steppicrew.kladde.model.NthWeekday
import de.steppicrew.kladde.model.Recurrence
import de.steppicrew.kladde.model.ScheduleVersion
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.MonthDay

/** How often, as the form offers it. */
internal enum class Repeat {
    EVERY_DAY,
    WEEKDAYS,
    EVERY_N_DAYS,

    /** E.g. the 1st and 3rd Monday: places in the month ([ScheduleDraft.nths]) times weekdays. */
    MONTHLY_WEEKDAYS,

    /** E.g. the 1st and 15th ([ScheduleDraft.monthDays]). */
    MONTHLY_DAYS,

    /** Every year from–until, e.g. 1–12 December ([ScheduleDraft.yearFrom], [ScheduleDraft.yearUntil]). */
    YEARLY,

    /** Not planned ([Recurrence.None]): only extra days, or from a schedule change on, none. */
    NONE,
}

/** One schedule card of the routine form while it is being filled in: from [validFrom] on. */
internal data class ScheduleDraft(
    val validFrom: LocalDate,
    val repeat: Repeat = Repeat.EVERY_DAY,
    val days: Set<DayOfWeek> = emptySet(),
    val interval: String = "2",
    /** For [Repeat.MONTHLY_WEEKDAYS]: 1–4, or [NthWeekday.LAST]; combined with every one of [days]. */
    val nths: Set<Int> = setOf(1),
    /** For [Repeat.MONTHLY_DAYS]: 1–31, or [Recurrence.MonthlyByDay.LAST_DAY]. */
    val monthDays: Set<Int> = emptySet(),
    /** For [Repeat.YEARLY]: the first and last day of the span; not 29 February. */
    val yearFrom: MonthDay? = null,
    val yearUntil: MonthDay? = null,
    val time: LocalTime? = null,
) {
    /** The repeat the card describes, or null while it is incomplete. */
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
                Repeat.YEARLY -> yearly()
                Repeat.NONE -> Recurrence.None
            }

    private fun yearly(): Recurrence? {
        val from = yearFrom?.takeIf { it != LEAP_DAY }
        val until = yearUntil?.takeIf { it != LEAP_DAY }
        return if (from != null && until != null) Recurrence.Yearly(from, until) else null
    }

    fun toVersion(): ScheduleVersion? = recurrence?.let { ScheduleVersion(validFrom, it, time) }

    companion object {
        const val MAX_INTERVAL = 365

        fun of(version: ScheduleVersion): ScheduleDraft {
            val card = ScheduleDraft(version.validFrom, time = version.time)
            return when (val recurrence = version.recurrence) {
                Recurrence.Daily -> card
                is Recurrence.Weekly -> card.copy(repeat = Repeat.WEEKDAYS, days = recurrence.days)
                is Recurrence.EveryNDays -> card.copy(repeat = Repeat.EVERY_N_DAYS, interval = recurrence.interval.toString())
                is Recurrence.MonthlyByWeekday ->
                    card.copy(
                        repeat = Repeat.MONTHLY_WEEKDAYS,
                        days = recurrence.days.mapTo(HashSet()) { it.day },
                        nths = recurrence.days.mapTo(HashSet()) { it.n },
                    )
                is Recurrence.MonthlyByDay -> card.copy(repeat = Repeat.MONTHLY_DAYS, monthDays = recurrence.days)
                is Recurrence.Yearly -> card.copy(repeat = Repeat.YEARLY, yearFrom = recurrence.from, yearUntil = recurrence.until)
                Recurrence.None -> card.copy(repeat = Repeat.NONE)
            }
        }
    }
}

/** 29 February: not an end of a yearly span, as most years lack it. */
internal val LEAP_DAY: MonthDay = MonthDay.of(Month.FEBRUARY, 29)

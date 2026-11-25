package de.steppicrew.kladde.model

import java.time.DayOfWeek
import java.time.LocalDate

/** A weekday's place in its month: the [n]th (1–4) or, for -1, the last [day] of the month. */
data class NthWeekday(
    val n: Int,
    val day: DayOfWeek,
) {
    init {
        require(n == LAST || n in 1..MAX_N) { "not a place in a month: $n" }
    }

    fun matches(date: LocalDate): Boolean =
        date.dayOfWeek == day &&
            if (n == LAST) date.dayOfMonth + DAYS_IN_WEEK > date.lengthOfMonth() else (date.dayOfMonth - 1) / DAYS_IN_WEEK + 1 == n

    companion object {
        const val LAST = -1

        /** A fifth one exists in some months only; "last" covers what people mean by it. */
        const val MAX_N = 4
        private const val DAYS_IN_WEEK = 7
    }
}

/**
 * When a routine happens. Stored as an RFC 5545 RRULE ([toRrule]); only the forms the UI offers
 * are read back: every day, chosen weekdays, every N days, chosen weekdays of the month ("1st
 * and 3rd Monday"), chosen days of the month.
 */
sealed interface Recurrence {
    data object Daily : Recurrence

    data class Weekly(
        val days: Set<DayOfWeek>,
    ) : Recurrence {
        init {
            require(days.isNotEmpty()) { "no weekday" }
        }
    }

    /** Every [interval] days, counted from the routine's start date. */
    data class EveryNDays(
        val interval: Int,
    ) : Recurrence {
        init {
            require(interval >= 2) { "use Daily for every day" }
        }
    }

    /** E.g. the 1st and 3rd Monday of every month. */
    data class MonthlyByWeekday(
        val days: Set<NthWeekday>,
    ) : Recurrence {
        init {
            require(days.isNotEmpty()) { "no weekday" }
        }
    }

    /**
     * On these days of every month, 1–31, or -1 for the last day. As in RFC 5545, a month without
     * such a day (the 31st in November) is skipped.
     */
    data class MonthlyByDay(
        val days: Set<Int>,
    ) : Recurrence {
        init {
            require(days.isNotEmpty() && days.all { it == LAST_DAY || it in 1..MAX_DAY }) { "not days of a month: $days" }
        }

        companion object {
            const val LAST_DAY = -1
            const val MAX_DAY = 31
        }
    }

    /** Whether [date] is in the pattern; [Routine.occursOn] adds the start date and every N days. */
    fun matches(date: LocalDate): Boolean =
        when (this) {
            Daily, is EveryNDays -> true
            is Weekly -> date.dayOfWeek in days
            is MonthlyByWeekday -> days.any { it.matches(date) }
            is MonthlyByDay -> date.dayOfMonth in days || (MonthlyByDay.LAST_DAY in days && date.dayOfMonth == date.lengthOfMonth())
        }

    fun toRrule(): String =
        when (this) {
            Daily -> "FREQ=DAILY"
            is Weekly -> "FREQ=WEEKLY;BYDAY=" + days.sorted().joinToString(",") { RRULE_DAYS.getValue(it) }
            is EveryNDays -> "FREQ=DAILY;INTERVAL=$interval"
            is MonthlyByWeekday ->
                "FREQ=MONTHLY;BYDAY=" +
                    days
                        .sortedWith(compareBy({ if (it.n == NthWeekday.LAST) Int.MAX_VALUE else it.n }, { it.day }))
                        .joinToString(",") { "${it.n}${RRULE_DAYS.getValue(it.day)}" }
            is MonthlyByDay -> "FREQ=MONTHLY;BYMONTHDAY=" + days.sortedBy { if (it < 0) Int.MAX_VALUE else it }.joinToString(",")
        }

    companion object {
        private val RRULE_DAYS =
            mapOf(
                DayOfWeek.MONDAY to "MO",
                DayOfWeek.TUESDAY to "TU",
                DayOfWeek.WEDNESDAY to "WE",
                DayOfWeek.THURSDAY to "TH",
                DayOfWeek.FRIDAY to "FR",
                DayOfWeek.SATURDAY to "SA",
                DayOfWeek.SUNDAY to "SU",
            )
        private val BY_CODE = RRULE_DAYS.entries.associate { (day, code) -> code to day }
        private val KNOWN_PARTS = setOf("FREQ", "INTERVAL", "BYDAY", "BYMONTHDAY")
        private val NTH_DAY = Regex("([+-]?\\d)?([A-Z]{2})")

        /** The recurrence [rule] stands for, or null for a rule outside the supported forms. */
        fun fromRrule(rule: String): Recurrence? {
            val parts =
                rule
                    .removePrefix("RRULE:")
                    .split(';')
                    .map { it.split('=', limit = 2) }
                    .associate { it.first().uppercase() to it.getOrElse(1) { "" }.uppercase() }
            val interval = parts["INTERVAL"]?.toIntOrNull() ?: if ("INTERVAL" in parts) 0 else 1
            if (interval < 1 || !KNOWN_PARTS.containsAll(parts.keys)) return null
            val byDay = parts["BYDAY"]
            val byMonthDay = parts["BYMONTHDAY"]
            return when (parts["FREQ"]) {
                "DAILY" -> daily(interval, byDay ?: byMonthDay)
                "WEEKLY" -> if (interval == 1 && byMonthDay == null) weekdays(byDay)?.let(::Weekly) else null
                "MONTHLY" -> if (interval == 1) monthly(byDay, byMonthDay) else null
                else -> null
            }
        }

        private fun daily(
            interval: Int,
            byAnything: String?,
        ): Recurrence? =
            when {
                byAnything != null -> null
                interval == 1 -> Daily
                else -> EveryNDays(interval)
            }

        private fun monthly(
            byDay: String?,
            byMonthDay: String?,
        ): Recurrence? =
            when {
                byDay != null && byMonthDay == null -> nthWeekdays(byDay)?.let(::MonthlyByWeekday)
                byMonthDay != null && byDay == null -> monthDays(byMonthDay)?.let(::MonthlyByDay)
                else -> null
            }

        private fun <T> list(
            value: String?,
            item: (String) -> T?,
        ): Set<T>? {
            val items =
                value
                    ?.takeIf { it.isNotEmpty() }
                    ?.split(',')
                    ?.map(item)
                    .orEmpty()
            return if (items.isEmpty() || null in items) null else items.filterNotNull().toSet()
        }

        private fun weekdays(byDay: String?): Set<DayOfWeek>? = list(byDay) { BY_CODE[it] }

        private fun nthWeekdays(byDay: String): Set<NthWeekday>? =
            list(byDay) { entry ->
                val match = NTH_DAY.matchEntire(entry)
                val n =
                    match
                        ?.groupValues
                        ?.get(1)
                        ?.toIntOrNull()
                        ?.takeIf { it == NthWeekday.LAST || it in 1..NthWeekday.MAX_N }
                val day = match?.groupValues?.get(2)?.let(BY_CODE::get)
                if (day != null && n != null) NthWeekday(n, day) else null
            }

        private fun monthDays(byMonthDay: String): Set<Int>? =
            list(byMonthDay) { entry ->
                entry.toIntOrNull()?.takeIf { it == MonthlyByDay.LAST_DAY || it in 1..MonthlyByDay.MAX_DAY }
            }
    }
}

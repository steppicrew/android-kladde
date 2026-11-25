package de.steppicrew.kladde.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * When a routine happens. Stored as an RFC 5545 RRULE ([toRrule]); only the forms the UI offers
 * are read back: every day, chosen weekdays, every N days.
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

    fun toRrule(): String =
        when (this) {
            Daily -> "FREQ=DAILY"
            is Weekly -> "FREQ=WEEKLY;BYDAY=" + days.sorted().joinToString(",") { RRULE_DAYS.getValue(it) }
            is EveryNDays -> "FREQ=DAILY;INTERVAL=$interval"
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
        private val KNOWN_PARTS = setOf("FREQ", "INTERVAL", "BYDAY")

        /** The recurrence [rule] stands for, or null for a rule outside the supported forms. */
        fun fromRrule(rule: String): Recurrence? {
            val parts =
                rule
                    .removePrefix("RRULE:")
                    .split(';')
                    .map { it.split('=', limit = 2) }
                    .associate { it.first().uppercase() to it.getOrElse(1) { "" }.uppercase() }
            val interval = parts["INTERVAL"]?.toIntOrNull() ?: if ("INTERVAL" in parts) 0 else 1
            val byDay = parts["BYDAY"]
            return when {
                interval < 1 || !KNOWN_PARTS.containsAll(parts.keys) -> null
                parts["FREQ"] == "DAILY" && byDay == null -> if (interval == 1) Daily else EveryNDays(interval)
                parts["FREQ"] == "WEEKLY" && interval == 1 -> weekdays(byDay)?.let(::Weekly)
                else -> null
            }
        }

        private fun weekdays(byDay: String?): Set<DayOfWeek>? {
            val byCode = RRULE_DAYS.entries.associate { (day, code) -> code to day }
            val days =
                byDay
                    ?.takeIf { it.isNotEmpty() }
                    ?.split(',')
                    ?.map { byCode[it] }
                    .orEmpty()
            return if (days.isEmpty() || null in days) null else days.filterNotNull().toSet()
        }
    }
}

/**
 * A planned, recurring thing: a task like "Sport" that is ticked off, or an event like
 * "Rüdersdorf" that just happens ([checkable] false). One kind of item for both.
 */
data class Routine(
    val id: String,
    val title: String,
    val recurrence: Recurrence,
    /** The first day it can happen, and where every N days counts from. */
    val startDate: LocalDate,
    /** Wall-clock time in whatever zone the device is in (floating); null for no set time. */
    val time: LocalTime?,
    val checkable: Boolean,
    val createdAt: Instant,
) {
    /** Whether the schedule has an occurrence on [date]. */
    fun occursOn(date: LocalDate): Boolean {
        if (date.isBefore(startDate)) return false
        return when (recurrence) {
            Recurrence.Daily -> true
            is Recurrence.Weekly -> date.dayOfWeek in recurrence.days
            is Recurrence.EveryNDays -> ChronoUnit.DAYS.between(startDate, date) % recurrence.interval == 0L
        }
    }
}

enum class CompletionStatus {
    DONE,
    SKIPPED,
}

/** The outcome of one occurrence, keyed by the routine and its local date. */
data class RoutineCompletion(
    val routineId: String,
    val date: LocalDate,
    val status: CompletionStatus,
    val recordedAt: Instant,
    /** The zone in effect when it was recorded, for showing the history correctly. */
    val zone: ZoneId,
)

/** What an occurrence is on its day. Only [DONE] and [SKIPPED] are stored; the rest is derived. */
enum class OccurrenceStatus {
    /** Due today or later and not ticked off yet. */
    OPEN,
    DONE,
    SKIPPED,

    /** A past day of a checkable routine with no answer. */
    MISSED,

    /** An event that isn't ticked off: it simply takes place. */
    HAPPENS,

    /** Doesn't take place: a routine it gives way to happens that day ([Occurrence.blockedBy]). */
    BLOCKED,

    /** Called off for this one day; listed on its planned day so it can be brought back. */
    CANCELLED,
}

data class Occurrence(
    val routine: Routine,
    val date: LocalDate,
    val status: OccurrenceStatus,
    /** For [OccurrenceStatus.BLOCKED]: the routines that take its place that day. */
    val blockedBy: List<Routine> = emptyList(),
    /** The day it was planned for; differs from [date] when it was moved. Answers are keyed by it. */
    val originalDate: LocalDate = date,
) {
    val isMoved: Boolean get() = originalDate != date
}

/** "[routineId] doesn't take place on days when [blockerId] does." */
data class BlockRule(
    val routineId: String,
    val blockerId: String,
)

/**
 * Whether making [routineId] give way to [blockerId] would close a loop (A gives way to B gives
 * way to A), given the [rules] there are. Such a rule is not offered.
 */
fun wouldCycle(
    routineId: String,
    blockerId: String,
    rules: List<BlockRule>,
): Boolean {
    val blockersOf = rules.groupBy({ it.routineId }, { it.blockerId })
    val seen = HashSet<String>()
    val queue = ArrayDeque(listOf(blockerId))
    while (queue.isNotEmpty()) {
        val next = queue.removeFirst()
        if (next == routineId) return true
        if (seen.add(next)) queue.addAll(blockersOf[next].orEmpty())
    }
    return false
}

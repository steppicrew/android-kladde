package de.steppicrew.kladde.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * A schedule a routine had before it was changed: [recurrence] and [time] from [validFrom] until
 * the next version starts. Every N days counts from [validFrom].
 */
data class ScheduleVersion(
    val validFrom: LocalDate,
    val recurrence: Recurrence,
    val time: LocalTime?,
) {
    fun matches(date: LocalDate): Boolean =
        when (recurrence) {
            is Recurrence.EveryNDays -> ChronoUnit.DAYS.between(validFrom, date) % recurrence.interval == 0L
            else -> recurrence.matches(date)
        }
}

/**
 * A planned, recurring thing: a task like "Sport" that is ticked off, or an event like
 * "Rüdersdorf" that just happens ([checkable] false). One kind of item for both.
 *
 * [recurrence] and [time] are the schedule from [startDate] on; a change applies from the event it
 * was made at, so the days before keep the schedules in [earlier].
 */
data class Routine(
    val id: String,
    val title: String,
    val recurrence: Recurrence,
    /** Where the current schedule starts, and where every N days counts from. */
    val startDate: LocalDate,
    /** Wall-clock time in whatever zone the device is in (floating); null for no set time. */
    val time: LocalTime?,
    val checkable: Boolean,
    val createdAt: Instant,
    /** The schedules before [startDate], oldest first; the first one's start is the routine's. */
    val earlier: List<ScheduleVersion> = emptyList(),
    /**
     * Goes on during pauses for all routines, e.g. taking a medicine on vacation; a pause that
     * names it still pauses it.
     */
    val ignoresPauses: Boolean = false,
) {
    private val current get() = ScheduleVersion(startDate, recurrence, time)

    /** The schedule in effect on [date], or null before the routine began. */
    private fun versionOn(date: LocalDate): ScheduleVersion? =
        if (!date.isBefore(startDate)) current else earlier.lastOrNull { !date.isBefore(it.validFrom) }

    /** Whether the schedule in effect on [date] has an occurrence then. */
    fun occursOn(date: LocalDate): Boolean = versionOn(date)?.matches(date) == true

    /** The time the occurrence on [date] has under the schedule in effect then. */
    fun timeOn(date: LocalDate): LocalTime? = (versionOn(date) ?: current).time

    /** The first day the routine can happen. */
    val firstDay: LocalDate get() = earlier.firstOrNull()?.validFrom ?: startDate

    /**
     * This routine with [recurrence] and [time] from [from] on; the days before keep what they had.
     * A change before the routine began applies from its first day.
     */
    fun withSchedule(
        recurrence: Recurrence,
        time: LocalTime?,
        from: LocalDate,
    ): Routine {
        val start = maxOf(from, firstDay)
        val kept = (earlier + current).filter { it.validFrom.isBefore(start) }
        return copy(recurrence = recurrence, time = time, startDate = start, earlier = kept)
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

    /** Inside a pause ([Occurrence.pausedBy]): not due, never missed, but can still be ticked off. */
    PAUSED,
}

data class Occurrence(
    val routine: Routine,
    val date: LocalDate,
    val status: OccurrenceStatus,
    /** For [OccurrenceStatus.BLOCKED]: the routines that take its place that day. */
    val blockedBy: List<Routine> = emptyList(),
    /** The day it was planned for; differs from [date] when it was moved. Answers are keyed by it. */
    val originalDate: LocalDate = date,
    /** For [OccurrenceStatus.PAUSED]: the pause it falls into. */
    val pausedBy: RoutinePause? = null,
    /** Added on a day the schedule doesn't have ([RoutineExtra]). */
    val isExtra: Boolean = false,
) {
    /** Its time under the schedule in effect on the day it was planned for. */
    val time: LocalTime? get() = routine.timeOn(originalDate)

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

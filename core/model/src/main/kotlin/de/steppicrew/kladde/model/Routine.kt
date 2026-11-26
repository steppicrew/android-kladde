package de.steppicrew.kladde.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * One of a routine's schedules: [recurrence] and [time] from [validFrom] until the next one starts.
 * Every N days counts from [validFrom]; [Recurrence.None] means not planned from then on.
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
 * [recurrence] and [time] are its base schedule, from [startDate] on; [changes] replace it from
 * their own start dates on ([schedules] has them all, in order).
 */
data class Routine(
    val id: String,
    val title: String,
    val recurrence: Recurrence,
    /** Where the routine and its base schedule start, and where every N days counts from. */
    val startDate: LocalDate,
    /** Wall-clock time in whatever zone the device is in (floating); null for no set time. */
    val time: LocalTime?,
    val checkable: Boolean,
    val createdAt: Instant,
    /** Later schedules, each from its [ScheduleVersion.validFrom] (after [startDate]) on, oldest first. */
    val changes: List<ScheduleVersion> = emptyList(),
    /**
     * Goes on during pauses for all routines, e.g. taking a medicine on vacation; a pause that
     * names it still pauses it.
     */
    val ignoresPauses: Boolean = false,
    /**
     * Null (the default): the time is wall-clock time wherever the device is (floating). Else the
     * time belongs to this zone, e.g. a call at 18:00 Europe/Berlin, and is shown converted.
     */
    val timeZone: ZoneId? = null,
    /** Groups such as "Arbeit" or "Privat", e.g. to pause all work routines at once. */
    val groups: Set<String> = emptySet(),
    /** How long before its time a reminder comes (zero: at the time); null for no reminder. Needs a time. */
    val remindBefore: Duration? = null,
) {
    private val base get() = ScheduleVersion(startDate, recurrence, time)

    /** The base schedule and its changes, by start date. */
    val schedules: List<ScheduleVersion> get() = listOf(base) + changes

    /** The schedule in effect on [date], or null before the routine began. */
    private fun versionOn(date: LocalDate): ScheduleVersion? =
        if (date.isBefore(startDate)) null else changes.lastOrNull { !date.isBefore(it.validFrom) } ?: base

    /** Whether the schedule in effect on [date] has an occurrence then. */
    fun occursOn(date: LocalDate): Boolean = versionOn(date)?.matches(date) == true

    /** The time the occurrence on [date] has under the schedule in effect then. */
    fun timeOn(date: LocalDate): LocalTime? = (versionOn(date) ?: base).time

    /**
     * This routine with [schedules] in place of its own: the earliest one is the base, the rest
     * are changes. One that repeats the schedule before it is dropped, as it changes nothing.
     */
    fun withSchedules(schedules: List<ScheduleVersion>): Routine {
        val sorted = schedules.sortedBy { it.validFrom }
        require(sorted.isNotEmpty() && sorted.map { it.validFrom }.distinct().size == sorted.size) { "schedules need distinct start dates" }
        val kept =
            sorted.filterIndexed { i, version ->
                i == 0 || version.recurrence != sorted[i - 1].recurrence || version.time != sorted[i - 1].time
            }
        val first = kept.first()
        return copy(recurrence = first.recurrence, time = first.time, startDate = first.validFrom, changes = kept.drop(1))
    }

    /**
     * This routine with [recurrence] and [time] from [from] on, in place of the schedules from then
     * on; the days before keep theirs. A change before the routine began applies from its start.
     */
    fun withSchedule(
        recurrence: Recurrence,
        time: LocalTime?,
        from: LocalDate,
    ): Routine {
        val start = maxOf(from, startDate)
        return withSchedules(schedules.filter { it.validFrom.isBefore(start) } + ScheduleVersion(start, recurrence, time))
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
    /** When it was done: when "Erledigt" was tapped, or an earlier time the user set. */
    val doneAt: Instant = recordedAt,
)

/** What an occurrence is on its day. Only [DONE] and [SKIPPED] are stored; the rest is derived. */
enum class OccurrenceStatus {
    /** Due today or later and not ticked off yet. */
    OPEN,
    DONE,
    SKIPPED,

    /** A past day of a checkable routine with no answer: shown as skipped, but can still be done. */
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
    /** A time it was moved to, in place of the routine's. */
    val movedTime: LocalTime? = null,
    /** For [OccurrenceStatus.DONE]: when it was done. */
    val doneAt: Instant? = null,
) {
    /** Its time: the one it was moved to, or the one under the schedule in effect on its planned day. */
    val time: LocalTime? get() = movedTime ?: plannedTime

    /** The time it had before it was moved. */
    val plannedTime: LocalTime? get() = routine.timeOn(originalDate)

    val isMoved: Boolean get() = originalDate != date || movedTime != null
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

/**
 * An occurrence's time as shown to someone in a zone: the same, or converted for a routine bound
 * to another zone, [dayShift] days off its own day, with [zone] to show the original next to it.
 */
data class ShownTime(
    val time: LocalTime,
    val dayShift: Long = 0,
    /** For a routine bound to another zone: that zone, whose wall-clock [Occurrence.time] is. */
    val zone: ZoneId? = null,
)

/** This occurrence's time for someone in [here]; null for a routine without a time. */
fun Occurrence.shownTime(here: ZoneId): ShownTime? {
    val time = time ?: return null
    val zone = routine.timeZone?.takeIf { it != here }
    val there = zone?.let { date.atTime(time).atZone(it).withZoneSameInstant(here) }
    return if (there == null) ShownTime(time) else ShownTime(there.toLocalTime(), ChronoUnit.DAYS.between(date, there.toLocalDate()), zone)
}

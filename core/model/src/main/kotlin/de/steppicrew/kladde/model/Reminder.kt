package de.steppicrew.kladde.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A reminder due [at] for [occurrence]. */
data class Reminder(
    val occurrence: Occurrence,
    val at: Instant,
)

/** About a year and a day: far enough for a yearly routine's next occurrence. */
private const val HORIZON_DAYS = 367L

/** Occurrences that are still to come: open ones, and events that just happen. */
private val REMINDED = setOf(OccurrenceStatus.OPEN, OccurrenceStatus.HAPPENS)

/**
 * The next reminder of each routine that has one, due after [after], for someone in [here]. A
 * floating routine's time is wall-clock time in [here], a zone-bound one's in its own zone. Only
 * open occurrences and events remind, so one done early, skipped, cancelled, paused or blocked
 * doesn't; a time that has passed (e.g. after flying east) gets no late reminder.
 */
fun RoutinePlan.nextReminders(
    after: Instant,
    here: ZoneId,
): List<Reminder> {
    val wanted = routines.filter { it.remindBefore != null }.mapTo(HashSet()) { it.id }
    val found = LinkedHashMap<String, Reminder>()
    val today = after.atZone(here).toLocalDate()
    // From yesterday: a zone-bound routine's day can lie behind the day here.
    var date = today.minusDays(1)
    while (found.size < wanted.size && !date.isAfter(today.plusDays(HORIZON_DAYS))) {
        for (occurrence in day(date, today)) {
            val id = occurrence.routine.id
            if (id !in wanted || id in found || occurrence.status !in REMINDED) continue
            occurrence.reminderAt(here)?.takeIf { it.isAfter(after) }?.let { found[id] = Reminder(occurrence, it) }
        }
        date = date.plusDays(1)
    }
    return found.values.sortedBy { it.at }
}

/** When this occurrence's reminder is due, or null for a routine without a time or a reminder. */
fun Occurrence.reminderAt(here: ZoneId): Instant? {
    val before = routine.remindBefore
    val time = time
    return if (before == null || time == null) {
        null
    } else {
        date
            .atTime(time)
            .atZone(routine.timeZone ?: here)
            .toInstant()
            .minus(before)
    }
}

/** The occurrence of [routineId] that was planned for [originalDate], wherever it was moved, or null. */
fun RoutinePlan.occurrenceOf(
    routineId: String,
    originalDate: LocalDate,
    date: LocalDate,
    today: LocalDate,
): Occurrence? = day(date, today).firstOrNull { it.routine.id == routineId && it.originalDate == originalDate }

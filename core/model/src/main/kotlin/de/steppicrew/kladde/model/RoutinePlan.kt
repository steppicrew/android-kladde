package de.steppicrew.kladde.model

import java.time.LocalDate

/**
 * A change to one occurrence, keyed by the routine and the date it was planned for
 * ([originalDate]), like an iCalendar RECURRENCE-ID: moved to [newDate], or cancelled when that
 * is null.
 */
data class OccurrenceOverride(
    val routineId: String,
    val originalDate: LocalDate,
    val newDate: LocalDate?,
) {
    val isCancelled: Boolean get() = newDate == null
}

/**
 * What is planned on which day, from the routines, what was ticked off, the block rules and the
 * single-day changes. All of it is worked out per day, never stored as dates: only a blocker that
 * takes place that day blocks, so moving or cancelling one carries through by itself.
 */
class RoutinePlan(
    private val routines: List<Routine>,
    completions: List<RoutineCompletion>,
    rules: List<BlockRule> = emptyList(),
    overrides: List<OccurrenceOverride> = emptyList(),
) {
    private val completions = completions.associateBy { it.routineId to it.date }
    private val byId = routines.associateBy { it.id }
    private val blockersOf = rules.groupBy({ it.routineId }, { it.blockerId })
    private val overrides = overrides.filter { it.routineId in byId }.associateBy { it.routineId to it.originalDate }
    private val movedTo =
        this.overrides.values
            .filter { it.newDate != null }
            .groupBy { it.newDate }

    /** The routines that block [routine]'s planned occurrence on [date]; [path] guards against a loop in stored rules. */
    private fun blockers(
        routine: Routine,
        date: LocalDate,
        path: Set<String> = emptySet(),
    ): List<Routine> =
        blockersOf[routine.id]
            .orEmpty()
            .mapNotNull(byId::get)
            .filter { it.id !in path && takesPlace(it, date, path + routine.id) }

    /** Whether [routine] happens on [date]: moved there, or planned there, unchanged and not blocked. */
    private fun takesPlace(
        routine: Routine,
        date: LocalDate,
        path: Set<String>,
    ): Boolean =
        movedTo[date].orEmpty().any { it.routineId == routine.id } ||
            (isPlannedUnchanged(routine, date) && blockers(routine, date, path).isEmpty())

    private fun isPlannedUnchanged(
        routine: Routine,
        date: LocalDate,
    ) = routine.occursOn(date) && (routine.id to date) !in overrides

    /**
     * The occurrences on [date] in time order, untimed ones first; [today] decides what counts as
     * missed. A cancelled occurrence is listed on its planned day, so it can be brought back.
     */
    fun day(
        date: LocalDate,
        today: LocalDate,
    ): List<Occurrence> {
        val planned =
            routines.filter { it.occursOn(date) }.map { routine ->
                val change = overrides[routine.id to date]
                when {
                    change == null -> occurrence(routine, date, date, today)
                    change.isCancelled -> Occurrence(routine, date, OccurrenceStatus.CANCELLED)
                    else -> null
                }
            }
        val movedHere =
            movedTo[date].orEmpty().map { change ->
                occurrence(byId.getValue(change.routineId), date, change.originalDate, today)
            }
        return (planned.filterNotNull() + movedHere)
            .sortedWith(
                compareBy(nullsFirst()) { occurrence: Occurrence -> occurrence.routine.time }.thenBy { it.routine.title.lowercase() },
            )
    }

    private fun occurrence(
        routine: Routine,
        date: LocalDate,
        originalDate: LocalDate,
        today: LocalDate,
    ): Occurrence {
        // What the user ticked off stands, even if a blocker was added to that day later; and an
        // occurrence the user moved onto a day is never hidden by a block rule.
        val answer = completions[routine.id to originalDate]?.status
        val moved = originalDate != date
        val blockedBy = if (answer == null && !moved) blockers(routine, date) else emptyList()
        val status =
            when {
                blockedBy.isNotEmpty() -> OccurrenceStatus.BLOCKED
                !routine.checkable -> OccurrenceStatus.HAPPENS
                answer == CompletionStatus.DONE -> OccurrenceStatus.DONE
                answer == CompletionStatus.SKIPPED -> OccurrenceStatus.SKIPPED
                date.isBefore(today) -> OccurrenceStatus.MISSED
                else -> OccurrenceStatus.OPEN
            }
        return Occurrence(routine, date, status, blockedBy, originalDate)
    }
}

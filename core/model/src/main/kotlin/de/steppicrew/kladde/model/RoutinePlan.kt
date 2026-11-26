package de.steppicrew.kladde.model

import java.time.LocalDate
import java.time.LocalTime

/**
 * A change to one occurrence, keyed by the routine and the date it was planned for
 * ([originalDate]), like an iCalendar RECURRENCE-ID: moved to [newDate], or cancelled when that
 * is null.
 */
data class OccurrenceOverride(
    val routineId: String,
    val originalDate: LocalDate,
    val newDate: LocalDate?,
    /** The new time, or null to keep the routine's; may be the only change, with [newDate] the same day. */
    val newTime: LocalTime? = null,
) {
    val isCancelled: Boolean get() = newDate == null
}

/**
 * One more occurrence of a routine on a day its schedule doesn't have ("Rüdersdorf this Friday
 * too"), like an iCalendar RDATE. On a day the routine is planned anyway it adds nothing.
 */
data class RoutineExtra(
    val routineId: String,
    val date: LocalDate,
)

/**
 * A pause, e.g. a vacation: from [from] to [until] (local dates, both included; null = until the
 * user ends it). It covers the routines in [routineIds] and those in [groups], also ones that join
 * a group later; with neither, all routines but those that go on during pauses ([Routine.ignoresPauses]).
 */
data class RoutinePause(
    val id: String,
    val from: LocalDate,
    val until: LocalDate?,
    val routineIds: Set<String> = emptySet(),
    /** What it is, e.g. "Urlaub"; may be blank. */
    val note: String = "",
    /** Groups whose routines it covers ([Routine.groups]). */
    val groups: Set<String> = emptySet(),
) {
    /** For all routines: it names neither routines nor groups. */
    val isForAll: Boolean get() = routineIds.isEmpty() && groups.isEmpty()

    /** Whether [date] lies inside the pause. */
    fun isOn(date: LocalDate): Boolean = !date.isBefore(from) && (until == null || !date.isAfter(until))

    fun covers(
        routine: Routine,
        date: LocalDate,
    ): Boolean =
        isOn(date) &&
            if (isForAll) !routine.ignoresPauses else routine.id in routineIds || routine.groups.any { it in groups }
}

/**
 * What is planned on which day, from the routines, what was ticked off, the block rules and the
 * single-day changes. All of it is worked out per day, never stored as dates: only a blocker that
 * takes place that day blocks, so moving or cancelling one carries through by itself.
 */
class RoutinePlan(
    val routines: List<Routine>,
    completions: List<RoutineCompletion>,
    rules: List<BlockRule> = emptyList(),
    overrides: List<OccurrenceOverride> = emptyList(),
    private val pauses: List<RoutinePause> = emptyList(),
    extras: List<RoutineExtra> = emptyList(),
) {
    private val completions = completions.associateBy { it.routineId to it.date }
    private val byId = routines.associateBy { it.id }
    private val blockersOf = rules.groupBy({ it.routineId }, { it.blockerId })
    private val overrides = overrides.filter { it.routineId in byId }.associateBy { it.routineId to it.originalDate }
    private val movedTo =
        this.overrides.values
            .filter { it.newDate != null }
            .groupBy { it.newDate }
    private val extrasOn =
        extras
            .filter { extra -> byId[extra.routineId]?.occursOn(extra.date) == false }
            .groupBy({ it.date }, { byId.getValue(it.routineId) })

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

    private fun pauseOf(
        routine: Routine,
        date: LocalDate,
    ): RoutinePause? = pauses.firstOrNull { it.covers(routine, date) }

    /** Whether [routine] happens on [date]: moved there, or planned there, unchanged, not paused and not blocked. */
    private fun takesPlace(
        routine: Routine,
        date: LocalDate,
        path: Set<String>,
    ): Boolean =
        movedTo[date].orEmpty().any { it.routineId == routine.id } ||
            extrasOn[date].orEmpty().any { it.id == routine.id } ||
            (isPlannedUnchanged(routine, date) && pauseOf(routine, date) == null && blockers(routine, date, path).isEmpty())

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
                    change == null -> occurrence(routine, date, today)
                    change.isCancelled -> Occurrence(routine, date, OccurrenceStatus.CANCELLED)
                    else -> null
                }
            }
        val movedHere =
            movedTo[date].orEmpty().map { change ->
                occurrence(byId.getValue(change.routineId), date, today, change)
            }
        val extra = extrasOn[date].orEmpty().map { occurrence(it, date, today, isExtra = true) }
        return (planned.filterNotNull() + movedHere + extra)
            .sortedWith(
                compareBy(nullsFirst()) { occurrence: Occurrence -> occurrence.time }.thenBy { it.routine.title.lowercase() },
            )
    }

    /** The occurrence of [routine] on [date]: planned there, moved there by [change], or an extra. */
    private fun occurrence(
        routine: Routine,
        date: LocalDate,
        today: LocalDate,
        change: OccurrenceOverride? = null,
        isExtra: Boolean = false,
    ): Occurrence {
        val originalDate = change?.originalDate ?: date
        // What the user ticked off stands, even if a pause or blocker was added to that day later;
        // and an occurrence the user moved or added onto a day is never hidden by a pause or a block rule.
        val completion = completions[routine.id to originalDate]
        val answer = completion?.status
        val explicit = change != null || isExtra
        val derived = answer == null && !explicit
        val pause = if (derived) pauseOf(routine, date) else null
        val blockedBy = if (derived && pause == null) blockers(routine, date) else emptyList()
        val status =
            when {
                pause != null -> OccurrenceStatus.PAUSED
                blockedBy.isNotEmpty() -> OccurrenceStatus.BLOCKED
                else -> answered(routine, answer, date.isBefore(today))
            }
        return Occurrence(
            routine,
            date,
            status,
            blockedBy,
            originalDate,
            pause,
            isExtra,
            change?.newTime,
            completion?.doneAt?.takeIf { status == OccurrenceStatus.DONE },
        )
    }

    /** The status of an occurrence that takes place: as ticked off, missed if past, or open. */
    private fun answered(
        routine: Routine,
        answer: CompletionStatus?,
        past: Boolean,
    ): OccurrenceStatus =
        when {
            !routine.checkable -> OccurrenceStatus.HAPPENS
            answer == CompletionStatus.DONE -> OccurrenceStatus.DONE
            answer == CompletionStatus.SKIPPED -> OccurrenceStatus.SKIPPED
            past -> OccurrenceStatus.MISSED
            else -> OccurrenceStatus.OPEN
        }
}

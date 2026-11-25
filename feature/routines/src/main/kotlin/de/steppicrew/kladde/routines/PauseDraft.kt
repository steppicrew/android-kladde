package de.steppicrew.kladde.routines

import de.steppicrew.kladde.model.Routine
import de.steppicrew.kladde.model.RoutinePause
import java.time.LocalDate

/** What the pause form holds while it is being filled in. */
internal data class PauseDraft(
    val note: String,
    val from: LocalDate,
    /** Null: until the user ends it. */
    val until: LocalDate?,
    /** All routines (the default), or only [routineIds] and the routines in [groups]. */
    val all: Boolean = true,
    val routineIds: Set<String> = emptySet(),
    /** Whole groups, which also cover routines that join them later. */
    val groups: Set<String> = emptySet(),
) {
    val isComplete: Boolean
        get() = (until == null || !until.isBefore(from)) && (all || routineIds.isNotEmpty() || groups.isNotEmpty())

    /** The pause this draft makes, keeping [id]; null while it is incomplete. */
    fun toPause(id: String): RoutinePause? =
        if (isComplete) {
            RoutinePause(id, from, until, if (all) emptySet() else routineIds, note.trim(), if (all) emptySet() else groups)
        } else {
            null
        }

    /** Whether [routine] is covered through one of the picked [groups]. */
    fun coversByGroup(routine: Routine): Boolean = routine.groups.any { it in groups }

    /** Picks [group] as a whole, or takes it out; its routines need no picking of their own then. */
    fun toggleGroup(
        group: String,
        routines: List<Routine>,
    ): PauseDraft {
        if (group in groups) return copy(groups = groups - group)
        val members = routines.filter { group in it.groups }.mapTo(HashSet()) { it.id }
        return copy(groups = groups + group, routineIds = routineIds - members)
    }

    companion object {
        /** A new pause: from today for a week, for all routines. */
        fun new(today: LocalDate) = PauseDraft("", today, today.plusWeeks(1))

        fun of(pause: RoutinePause) = PauseDraft(pause.note, pause.from, pause.until, pause.isForAll, pause.routineIds, pause.groups)
    }
}

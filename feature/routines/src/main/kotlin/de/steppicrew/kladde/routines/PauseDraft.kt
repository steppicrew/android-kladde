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
    /** All routines (the default), or only [routineIds]. */
    val all: Boolean = true,
    val routineIds: Set<String> = emptySet(),
) {
    val isComplete: Boolean get() = (until == null || !until.isBefore(from)) && (all || routineIds.isNotEmpty())

    /** The pause this draft makes, keeping [id]; null while it is incomplete. */
    fun toPause(id: String): RoutinePause? =
        if (isComplete) RoutinePause(id, from, until, if (all) emptySet() else routineIds, note.trim()) else null

    /** Whether every routine in [group] is picked. */
    fun hasGroup(
        group: String,
        routines: List<Routine>,
    ): Boolean {
        val members = routines.filter { group in it.groups }.map { it.id }
        return members.isNotEmpty() && routineIds.containsAll(members)
    }

    /** Picks all routines in [group], or, when they are all picked already, takes them out. */
    fun toggleGroup(
        group: String,
        routines: List<Routine>,
    ): PauseDraft {
        val members = routines.filter { group in it.groups }.mapTo(HashSet()) { it.id }
        return copy(routineIds = if (hasGroup(group, routines)) routineIds - members else routineIds + members)
    }

    companion object {
        /** A new pause: from today for a week, for all routines. */
        fun new(today: LocalDate) = PauseDraft("", today, today.plusWeeks(1))

        fun of(pause: RoutinePause) = PauseDraft(pause.note, pause.from, pause.until, pause.routineIds.isEmpty(), pause.routineIds)
    }
}

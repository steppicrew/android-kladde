package de.steppicrew.kladde.routines

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

    companion object {
        /** A new pause: from today for a week, for all routines. */
        fun new(today: LocalDate) = PauseDraft("", today, today.plusWeeks(1))

        fun of(pause: RoutinePause) = PauseDraft(pause.note, pause.from, pause.until, pause.routineIds.isEmpty(), pause.routineIds)
    }
}

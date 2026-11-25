package de.steppicrew.kladde.routines

import de.steppicrew.kladde.model.Occurrence
import de.steppicrew.kladde.model.OccurrenceStatus
import de.steppicrew.kladde.model.Recurrence
import de.steppicrew.kladde.model.Routine
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class RowActionsTest {
    private val today = LocalDate.of(2026, 11, 25)
    private val sport = Routine("s", "Sport", Recurrence.Daily, today.minusDays(7), null, true, Instant.EPOCH)
    private val event = sport.copy(id = "e", checkable = false)

    private fun actions(
        status: OccurrenceStatus,
        date: LocalDate,
        routine: Routine = sport,
        isExtra: Boolean = false,
    ) = rowActions(Occurrence(routine, date, status, isExtra = isExtra), today)

    @Test
    fun `today and before can be done or skipped`() {
        assertEquals(listOf(RowAction.DONE, RowAction.SKIP), actions(OccurrenceStatus.OPEN, today))
        assertEquals(listOf(RowAction.DONE, RowAction.SKIP), actions(OccurrenceStatus.MISSED, today.minusDays(1)))
    }

    @Test
    fun `a day still to come can only be skipped, which cancels it`() {
        assertEquals(listOf(RowAction.CANCEL_ONCE), actions(OccurrenceStatus.OPEN, today.plusDays(1)))
        assertEquals(listOf(RowAction.RESTORE), actions(OccurrenceStatus.CANCELLED, today.plusDays(1)))
    }

    @Test
    fun `an extra day still to come has no button, it is removed from its menu`() {
        assertEquals(emptyList<RowAction>(), actions(OccurrenceStatus.OPEN, today.plusDays(1), isExtra = true))
    }

    @Test
    fun `done or skipped can be taken back`() {
        assertEquals(listOf(RowAction.UNDO), actions(OccurrenceStatus.DONE, today))
        assertEquals(listOf(RowAction.UNDO), actions(OccurrenceStatus.SKIPPED, today.minusDays(2)))
    }

    @Test
    fun `a paused day can be done anyway, but not in the future`() {
        assertEquals(listOf(RowAction.DONE), actions(OccurrenceStatus.PAUSED, today))
        assertEquals(emptyList<RowAction>(), actions(OccurrenceStatus.PAUSED, today.plusDays(1)))
        assertEquals(emptyList<RowAction>(), actions(OccurrenceStatus.PAUSED, today, event))
    }

    @Test
    fun `events and blocked days have no buttons`() {
        assertEquals(emptyList<RowAction>(), actions(OccurrenceStatus.HAPPENS, today, event))
        assertEquals(emptyList<RowAction>(), actions(OccurrenceStatus.BLOCKED, today))
    }
}

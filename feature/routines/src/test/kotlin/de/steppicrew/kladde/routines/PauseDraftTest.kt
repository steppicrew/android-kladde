package de.steppicrew.kladde.routines

import de.steppicrew.kladde.model.RoutinePause
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PauseDraftTest {
    private val today = LocalDate.of(2026, 11, 25)

    @Test
    fun `a new pause is a week from today for all routines`() {
        assertEquals(RoutinePause("p1", today, today.plusWeeks(1)), PauseDraft.new(today).toPause("p1"))
    }

    @Test
    fun `until may not lie before from, but may be open or the same day`() {
        assertFalse(PauseDraft("", today, today.minusDays(1)).isComplete)
        assertTrue(PauseDraft("", today, today).isComplete)
        assertTrue(PauseDraft("", today, null).isComplete)
        assertNull(PauseDraft("", today, today.minusDays(1)).toPause("p1"))
    }

    @Test
    fun `only chosen routines needs at least one`() {
        assertFalse(PauseDraft("", today, null, all = false).isComplete)
        assertEquals(
            RoutinePause("p1", today, null, setOf("r1"), "Urlaub"),
            PauseDraft(" Urlaub ", today, null, all = false, routineIds = setOf("r1")).toPause("p1"),
        )
    }

    @Test
    fun `all routines stores no list, even if some were picked before`() {
        assertEquals(emptySet<String>(), PauseDraft("", today, null, all = true, routineIds = setOf("r1")).toPause("p1")!!.routineIds)
    }

    @Test
    fun `the form shows a stored pause as it was set`() {
        val pause = RoutinePause("p1", today, today.plusDays(3), setOf("r1"), "Kur")

        assertEquals(pause, PauseDraft.of(pause).toPause("p1"))
    }

    @Test
    fun `a group is kept as a group in the pause, and its routines need no picking`() {
        fun routine(
            id: String,
            vararg groups: String,
        ) = de.steppicrew.kladde.model.Routine(
            id,
            id,
            de.steppicrew.kladde.model.Recurrence.Daily,
            LocalDate.of(2026, 11, 25),
            null,
            true,
            java.time.Instant.EPOCH,
            groups = groups.toSet(),
        )
        val routines = listOf(routine("mail", "Arbeit"), routine("standup", "Arbeit"), routine("sport", "Privat"))
        val draft = PauseDraft.new(LocalDate.of(2026, 11, 25)).copy(all = false, routineIds = setOf("sport", "mail"))

        val work = draft.toggleGroup("Arbeit", routines)
        assertEquals(setOf("Arbeit"), work.groups)
        assertEquals(setOf("sport"), work.routineIds)
        assertTrue(work.coversByGroup(routines[1]))
        assertFalse(work.coversByGroup(routines[2]))
        val pause = work.toPause("p1")!!
        assertEquals(setOf("Arbeit"), pause.groups)
        assertEquals(work, PauseDraft.of(pause))

        val off = work.toggleGroup("Arbeit", routines)
        assertEquals(emptySet<String>(), off.groups)
        assertTrue(PauseDraft.new(LocalDate.of(2026, 11, 25)).copy(all = false, groups = setOf("Arbeit")).isComplete)
    }
}

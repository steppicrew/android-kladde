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
}

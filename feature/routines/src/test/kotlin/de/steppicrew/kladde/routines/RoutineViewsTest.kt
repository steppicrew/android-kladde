package de.steppicrew.kladde.routines

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

class RoutineViewsTest {
    // 2026-11-25 is a Wednesday.
    private val wednesday = LocalDate.of(2026, 11, 25)

    @Test
    fun `the week starts the day before, the list on the day, for four weeks and more`() {
        assertEquals(wednesday.minusDays(1)..wednesday.plusDays(6), shownDays(RoutineView.WEEK, wednesday))
        assertEquals(wednesday..wednesday.plusDays(27), shownDays(RoutineView.LIST, wednesday))
        assertEquals(wednesday..wednesday.plusDays(55), shownDays(RoutineView.LIST, wednesday, listWeeks = 8))
    }

    @Test
    fun `a month shows whole weeks from the locale's first weekday`() {
        // November 2026 begins on a Sunday and ends on a Monday.
        val november = YearMonth.of(2026, 11)

        assertEquals(LocalDate.of(2026, 10, 26)..LocalDate.of(2026, 12, 6), monthGrid(november, Locale.GERMANY))
        assertEquals(LocalDate.of(2026, 11, 1)..LocalDate.of(2026, 12, 5), monthGrid(november, Locale.US))
    }

    @Test
    fun `steps go by a week or a month, keeping the day where it can`() {
        assertEquals(wednesday.plusWeeks(1), step(RoutineView.WEEK, wednesday, 1))
        assertEquals(LocalDate.of(2027, 2, 28), step(RoutineView.MONTH, LocalDate.of(2027, 1, 31), 1))
        assertEquals(LocalDate.of(2026, 10, 25), step(RoutineView.MONTH, wednesday, -1))
        assertEquals(wednesday, step(RoutineView.LIST, wednesday, 1))
    }
}

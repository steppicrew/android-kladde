package de.steppicrew.kladde.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class ScheduleVersionTest {
    // 2026-11-25 is a Wednesday.
    private val wednesday = LocalDate.of(2026, 11, 25)
    private val start = wednesday.minusWeeks(4)
    private val ruedersdorf =
        Routine("rued", "Rüdersdorf", Recurrence.Weekly(setOf(WEDNESDAY)), start, LocalTime.of(18, 0), false, Instant.EPOCH)

    @Test
    fun `a change applies from the edited event on, the days before keep theirs`() {
        val changed = ruedersdorf.withSchedule(Recurrence.Weekly(setOf(THURSDAY)), LocalTime.of(19, 0), wednesday)

        assertTrue(changed.occursOn(wednesday.minusWeeks(1)))
        assertEquals(LocalTime.of(18, 0), changed.timeOn(wednesday.minusWeeks(1)))
        assertFalse(changed.occursOn(wednesday))
        assertTrue(changed.occursOn(wednesday.plusDays(1)))
        assertEquals(LocalTime.of(19, 0), changed.timeOn(wednesday.plusDays(1)))
        assertFalse(changed.occursOn(start.minusWeeks(1)))
        assertEquals(start, changed.firstDay)
    }

    @Test
    fun `a second change keeps both earlier schedules`() {
        val once = ruedersdorf.withSchedule(Recurrence.Weekly(setOf(THURSDAY)), null, wednesday.minusWeeks(2))
        val twice = once.withSchedule(Recurrence.Weekly(setOf(MONDAY)), null, wednesday)

        assertEquals(listOf(start, wednesday.minusWeeks(2)), twice.earlier.map { it.validFrom })
        assertTrue(twice.occursOn(wednesday.minusWeeks(3)))
        assertTrue(twice.occursOn(wednesday.minusWeeks(1).plusDays(1)))
        assertTrue(twice.occursOn(wednesday.plusDays(5)))
        assertFalse(twice.occursOn(wednesday.plusDays(1)))
    }

    @Test
    fun `changing again from an earlier event replaces the later schedules`() {
        val later = ruedersdorf.withSchedule(Recurrence.Weekly(setOf(THURSDAY)), null, wednesday)
        val earlierChange = later.withSchedule(Recurrence.Weekly(setOf(MONDAY)), null, wednesday.minusWeeks(2))

        assertEquals(listOf(start), earlierChange.earlier.map { it.validFrom })
        assertEquals(wednesday.minusWeeks(2), earlierChange.startDate)
        assertFalse(earlierChange.occursOn(wednesday.plusDays(1)))
    }

    @Test
    fun `a change at the first event replaces the schedule entirely`() {
        val changed = ruedersdorf.withSchedule(Recurrence.Daily, null, start)

        assertTrue(changed.earlier.isEmpty())
        assertEquals(start, changed.startDate)
    }

    @Test
    fun `a change before the routine began applies from its first day`() {
        assertEquals(start, ruedersdorf.withSchedule(Recurrence.Daily, null, start.minusWeeks(1)).startDate)
    }

    @Test
    fun `every n days counts from the edited event`() {
        val changed = ruedersdorf.withSchedule(Recurrence.EveryNDays(3), null, wednesday)

        assertEquals(
            listOf(wednesday, wednesday.plusDays(3), wednesday.plusDays(6)),
            (0L..7).map(wednesday::plusDays).filter(changed::occursOn),
        )
    }

    @Test
    fun `past occurrences keep their time in the plan`() {
        val changed = ruedersdorf.withSchedule(ruedersdorf.recurrence, LocalTime.of(20, 0), wednesday)
        val plan = RoutinePlan(listOf(changed), emptyList())

        assertEquals(LocalTime.of(18, 0), plan.day(wednesday.minusWeeks(1), wednesday).single().time)
        assertEquals(LocalTime.of(20, 0), plan.day(wednesday, wednesday).single().time)
    }
}

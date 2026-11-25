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
        assertEquals(start, changed.startDate)
        assertEquals(listOf(wednesday), changed.changes.map { it.validFrom })
    }

    @Test
    fun `a second change keeps both earlier schedules`() {
        val once = ruedersdorf.withSchedule(Recurrence.Weekly(setOf(THURSDAY)), null, wednesday.minusWeeks(2))
        val twice = once.withSchedule(Recurrence.Weekly(setOf(MONDAY)), null, wednesday)

        assertEquals(listOf(start, wednesday.minusWeeks(2), wednesday), twice.schedules.map { it.validFrom })
        assertTrue(twice.occursOn(wednesday.minusWeeks(3)))
        assertTrue(twice.occursOn(wednesday.minusWeeks(1).plusDays(1)))
        assertTrue(twice.occursOn(wednesday.plusDays(5)))
        assertFalse(twice.occursOn(wednesday.plusDays(1)))
    }

    @Test
    fun `changing again from an earlier event replaces the later schedules`() {
        val later = ruedersdorf.withSchedule(Recurrence.Weekly(setOf(THURSDAY)), null, wednesday)
        val earlierChange = later.withSchedule(Recurrence.Weekly(setOf(MONDAY)), null, wednesday.minusWeeks(2))

        assertEquals(listOf(wednesday.minusWeeks(2)), earlierChange.changes.map { it.validFrom })
        assertEquals(start, earlierChange.startDate)
        assertFalse(earlierChange.occursOn(wednesday.plusDays(1)))
    }

    @Test
    fun `a change at the first event replaces the schedule entirely`() {
        val changed = ruedersdorf.withSchedule(Recurrence.Daily, null, start)

        assertTrue(changed.changes.isEmpty())
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

    private val thursdays = Recurrence.Weekly(setOf(THURSDAY))
    private val mondays = Recurrence.Weekly(setOf(MONDAY))

    @Test
    fun `editing a change in the middle keeps the later ones`() {
        val stacked =
            ruedersdorf.withSchedules(
                ruedersdorf.schedules +
                    ScheduleVersion(wednesday.minusWeeks(2), thursdays, null) +
                    ScheduleVersion(wednesday, mondays, null),
            )
        val edited =
            stacked.withSchedules(
                stacked.schedules.map {
                    if (it.recurrence ==
                        thursdays
                    ) {
                        it.copy(time = LocalTime.of(9, 0))
                    } else {
                        it
                    }
                },
            )

        assertEquals(LocalTime.of(9, 0), edited.timeOn(wednesday.minusWeeks(1).plusDays(1)))
        assertTrue(edited.occursOn(wednesday.plusDays(5)))
    }

    @Test
    fun `deleting a change lets the one before it run on, deleting the base starts with the next`() {
        val stacked = ruedersdorf.withSchedules(ruedersdorf.schedules + ScheduleVersion(wednesday, thursdays, null))

        val withoutChange = stacked.withSchedules(stacked.schedules.dropLast(1))
        assertEquals(ruedersdorf, withoutChange)
        assertTrue(withoutChange.occursOn(wednesday.plusWeeks(1)))

        val withoutBase = stacked.withSchedules(stacked.schedules.drop(1))
        assertEquals(wednesday, withoutBase.startDate)
        assertFalse(withoutBase.occursOn(wednesday.minusWeeks(1)))
    }

    @Test
    fun `not planned from a date on, and planned again later`() {
        val paused =
            ruedersdorf.withSchedules(
                ruedersdorf.schedules +
                    ScheduleVersion(wednesday, Recurrence.None, null) +
                    ScheduleVersion(wednesday.plusWeeks(2), ruedersdorf.recurrence, LocalTime.of(18, 0)),
            )

        assertTrue(paused.occursOn(wednesday.minusWeeks(1)))
        assertFalse(paused.occursOn(wednesday))
        assertFalse(paused.occursOn(wednesday.plusWeeks(1)))
        assertTrue(paused.occursOn(wednesday.plusWeeks(2)))
        assertEquals(Recurrence.None, Recurrence.fromRrule(""))
        assertEquals("", Recurrence.None.toRrule())
    }

    @Test
    fun `a schedule repeating the one before is dropped, two on one day are refused`() {
        val same = ruedersdorf.withSchedules(ruedersdorf.schedules + ScheduleVersion(wednesday, ruedersdorf.recurrence, ruedersdorf.time))
        assertEquals(ruedersdorf, same)

        val twice = runCatching { ruedersdorf.withSchedules(ruedersdorf.schedules + ScheduleVersion(start, mondays, null)) }
        assertTrue(twice.isFailure)
    }
}

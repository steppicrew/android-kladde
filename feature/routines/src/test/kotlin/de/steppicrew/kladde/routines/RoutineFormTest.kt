package de.steppicrew.kladde.routines

import de.steppicrew.kladde.model.BlockRule
import de.steppicrew.kladde.model.NthWeekday
import de.steppicrew.kladde.model.Recurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class RoutineFormTest {
    private val today = LocalDate.of(2026, 11, 25)
    private val now = Instant.parse("2026-11-25T20:00:00Z")

    @Test
    fun `a draft needs a title and a complete schedule`() {
        assertFalse(RoutineDraft().isComplete)
        assertTrue(RoutineDraft("Sport").isComplete)
        assertFalse(RoutineDraft("Rüdersdorf", Repeat.WEEKDAYS).isComplete)
        assertFalse(RoutineDraft("Sport", Repeat.EVERY_N_DAYS, interval = "1").isComplete)
        assertFalse(RoutineDraft("Sport", Repeat.EVERY_N_DAYS, interval = "zwei").isComplete)
        assertTrue(RoutineDraft("Sport", Repeat.EVERY_N_DAYS, interval = " 3 ").isComplete)
        assertNull(RoutineDraft("  ").toRoutine("r1", today, now))
    }

    @Test
    fun `a new routine starts today`() {
        val routine = RoutineDraft(" Sport ", time = LocalTime.of(18, 0)).toRoutine("r1", today, now)!!

        assertEquals("Sport", routine.title)
        assertEquals(Recurrence.Daily, routine.recurrence)
        assertEquals(today, routine.startDate)
        assertEquals(LocalTime.of(18, 0), routine.time)
        assertEquals(now, routine.createdAt)
    }

    @Test
    fun `an edit keeps id and creation, and a new schedule applies from the edited event`() {
        val earlier = RoutineDraft("Sport", Repeat.EVERY_N_DAYS, interval = "2").toRoutine("r1", today.minusDays(10), Instant.EPOCH)!!
        val event = today.plusDays(2)

        val renamed = RoutineDraft.of(earlier).copy(title = "Workout").toRoutine("new", today, now, earlier, event)!!
        val rescheduled = RoutineDraft.of(earlier).copy(interval = "3").toRoutine("new", today, now, earlier, event)!!
        val retimed = RoutineDraft.of(earlier).copy(time = LocalTime.of(7, 0)).toRoutine("new", today, now, earlier, event)!!

        assertEquals(earlier.copy(title = "Workout"), renamed)
        assertEquals(earlier.withSchedule(Recurrence.EveryNDays(3), null, event), rescheduled)
        assertEquals(event, rescheduled.startDate)
        assertEquals(earlier.withSchedule(earlier.recurrence, LocalTime.of(7, 0), event), retimed)
    }

    @Test
    fun `the form shows an existing routine as it was set`() {
        val ruedersdorf =
            RoutineDraft("Rüdersdorf", Repeat.WEEKDAYS, days = setOf(DayOfWeek.WEDNESDAY), checkable = false).toRoutine("r2", today, now)!!

        assertEquals(
            RoutineDraft("Rüdersdorf", Repeat.WEEKDAYS, days = setOf(DayOfWeek.WEDNESDAY), checkable = false),
            RoutineDraft.of(ruedersdorf),
        )
    }

    @Test
    fun `the form shows what a routine gives way to`() {
        val sport = RoutineDraft("Sport").toRoutine("r1", today, now)!!
        val rules = listOf(BlockRule("r1", "r2"), BlockRule("r1", "r3"), BlockRule("r2", "r3"))

        assertEquals(setOf("r2", "r3"), RoutineDraft.of(sport, rules).blockers)
    }

    @Test
    fun `monthly on the 1st and 3rd Monday combines places and weekdays`() {
        val draft = RoutineDraft("Treffen", Repeat.MONTHLY_WEEKDAYS, days = setOf(DayOfWeek.MONDAY), nths = setOf(1, 3))

        assertEquals(
            Recurrence.MonthlyByWeekday(setOf(NthWeekday(1, DayOfWeek.MONDAY), NthWeekday(3, DayOfWeek.MONDAY))),
            draft.recurrence,
        )
        assertEquals(draft, RoutineDraft.of(draft.toRoutine("r1", today, now)!!))
        assertFalse(draft.copy(nths = emptySet()).isComplete)
        assertFalse(draft.copy(days = emptySet()).isComplete)
    }

    @Test
    fun `monthly on days of the month`() {
        val draft = RoutineDraft("Miete", Repeat.MONTHLY_DAYS, monthDays = setOf(1, Recurrence.MonthlyByDay.LAST_DAY))

        assertEquals(Recurrence.MonthlyByDay(setOf(1, -1)), draft.recurrence)
        assertEquals(draft, RoutineDraft.of(draft.toRoutine("r1", today, now)!!))
        assertFalse(draft.copy(monthDays = emptySet()).isComplete)
    }

    @Test
    fun `going on during pauses is kept through the form and is no schedule change`() {
        val pills = RoutineDraft("Tabletten", ignoresPauses = true).toRoutine("r1", today, now)!!
        assertTrue(pills.ignoresPauses)
        assertEquals(RoutineDraft("Tabletten", ignoresPauses = true), RoutineDraft.of(pills))

        val off = RoutineDraft.of(pills).copy(ignoresPauses = false).toRoutine("new", today, now, pills, today.plusDays(3))!!
        assertEquals(pills.copy(ignoresPauses = false), off)
    }
}

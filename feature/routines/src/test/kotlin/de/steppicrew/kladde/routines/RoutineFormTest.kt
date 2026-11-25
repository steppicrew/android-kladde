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

        val renamed = RoutineDraft.of(earlier).copy(title = "Workout").toRoutine("new", event, now, earlier)!!
        val rescheduled = RoutineDraft.of(earlier).copy(interval = "3").toRoutine("new", event, now, earlier)!!
        val retimed = RoutineDraft.of(earlier).copy(time = LocalTime.of(7, 0)).toRoutine("new", event, now, earlier)!!

        assertEquals(earlier.copy(title = "Workout"), renamed)
        assertEquals(earlier.withSchedule(Recurrence.EveryNDays(3), null, event), rescheduled)
        assertEquals(event, rescheduled.schedules.last().validFrom)
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

        val off = RoutineDraft.of(pills).copy(ignoresPauses = false).toRoutine("new", today.plusDays(3), now, pills)!!
        assertEquals(pills.copy(ignoresPauses = false), off)
    }

    @Test
    fun `a zone binds only a routine with a time`() {
        val call = RoutineDraft("Call", time = LocalTime.of(18, 0), timeZone = "Europe/Berlin").toRoutine("r1", today, now)!!
        assertEquals(java.time.ZoneId.of("Europe/Berlin"), call.timeZone)
        assertEquals("Europe/Berlin", RoutineDraft.of(call).timeZone)

        assertEquals(null, RoutineDraft("Call", timeZone = "Europe/Berlin").toRoutine("r1", today, now)!!.timeZone)
    }

    @Test
    fun `groups are trimmed and kept through the form`() {
        val routine = RoutineDraft("Mails", groups = setOf(" Arbeit ", " ")).toRoutine("r1", today, now)!!

        assertEquals(setOf("Arbeit"), routine.groups)
        assertEquals(setOf("Arbeit"), RoutineDraft.of(routine).groups)
    }

    @Test
    fun `picking an earlier "Gilt ab" alone moves the current schedule's start back`() {
        // Like Eileen: Thursdays from the 25th, then Wednesday and Thursday from the 26th.
        val thursdays =
            RoutineDraft("Eileen", Repeat.WEEKDAYS, days = setOf(java.time.DayOfWeek.THURSDAY)).toRoutine("e", today, now)!!
        val both = setOf(java.time.DayOfWeek.WEDNESDAY, java.time.DayOfWeek.THURSDAY)
        val changed = RoutineDraft.of(thursdays).copy(days = both).toRoutine("x", today.plusDays(1), now, thursdays)!!
        assertEquals(today.plusDays(1), changed.schedules.last().validFrom)

        val moved = RoutineDraft.of(changed).toRoutine("x", today.minusDays(2), now, changed, fromPicked = true)!!
        assertEquals(today, moved.startDate)
        assertEquals(emptyList<de.steppicrew.kladde.model.ScheduleVersion>(), moved.changes)
        assertTrue(moved.occursOn(today))

        // Opened at an earlier event but not picked: a rename leaves the schedule alone.
        assertEquals(changed, RoutineDraft.of(changed).toRoutine("x", today.minusDays(2), now, changed))
    }

    @Test
    fun `a yearly span goes through the form, and 29 February can't be an end`() {
        val dec = { day: Int -> java.time.MonthDay.of(java.time.Month.DECEMBER, day) }
        val draft = RoutineDraft("Advent of Code", Repeat.YEARLY, yearFrom = dec(1), yearUntil = dec(12), time = LocalTime.of(6, 0))
        val routine = draft.toRoutine("aoc", today, now)!!

        assertEquals(
            de.steppicrew.kladde.model.Recurrence
                .Yearly(dec(1), dec(12)),
            routine.recurrence,
        )
        assertEquals(draft, RoutineDraft.of(routine))
        assertNull(draft.copy(yearUntil = null).recurrence)
        assertNull(draft.copy(yearFrom = LEAP_DAY).recurrence)
    }
}

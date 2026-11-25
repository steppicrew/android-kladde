package de.steppicrew.kladde.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class RoutineTest {
    // 2026-11-25 is a Wednesday.
    private val wednesday = LocalDate.of(2026, 11, 25)

    private fun routine(
        recurrence: Recurrence,
        title: String = "Sport",
        time: LocalTime? = null,
        checkable: Boolean = true,
        start: LocalDate = wednesday,
    ) = Routine(title.lowercase(), title, recurrence, start, time, checkable, Instant.EPOCH)

    @Test
    fun `rrules are written and read back`() {
        val cases =
            mapOf(
                Recurrence.Daily to "FREQ=DAILY",
                Recurrence.Weekly(setOf(THURSDAY, MONDAY)) to "FREQ=WEEKLY;BYDAY=MO,TH",
                Recurrence.EveryNDays(3) to "FREQ=DAILY;INTERVAL=3",
            )
        for ((recurrence, rule) in cases) {
            assertEquals(rule, recurrence.toRrule())
            assertEquals(recurrence, Recurrence.fromRrule(rule))
        }
        assertEquals(Recurrence.Weekly(setOf(WEDNESDAY)), Recurrence.fromRrule("RRULE:freq=weekly;byday=we"))
        assertEquals(Recurrence.Daily, Recurrence.fromRrule("FREQ=DAILY;INTERVAL=1"))
    }

    @Test
    fun `rules outside the supported forms are not read`() {
        for (rule in listOf(
            "FREQ=MONTHLY",
            "FREQ=WEEKLY",
            "FREQ=WEEKLY;BYDAY=XX",
            "FREQ=DAILY;COUNT=3",
            "FREQ=DAILY;INTERVAL=0",
            // "" is not here: it is Recurrence.None.
            "FREQ=WEEKLY;INTERVAL=2;BYDAY=MO",
        )) {
            assertNull(rule, Recurrence.fromRrule(rule))
        }
    }

    @Test
    fun `weekly happens on its weekdays only`() {
        val ruedersdorf = routine(Recurrence.Weekly(setOf(WEDNESDAY)), "Rüdersdorf")

        assertTrue(ruedersdorf.occursOn(wednesday))
        assertFalse(ruedersdorf.occursOn(wednesday.plusDays(1)))
        assertTrue(ruedersdorf.occursOn(wednesday.plusWeeks(5)))
    }

    @Test
    fun `nothing happens before the start date`() {
        assertFalse(routine(Recurrence.Daily).occursOn(wednesday.minusDays(1)))
    }

    @Test
    fun `every n days counts from the start date, across a month end and a leap day`() {
        val everyThird = routine(Recurrence.EveryNDays(3), start = LocalDate.of(2028, 2, 26))

        val days = (0L..9).map { LocalDate.of(2028, 2, 26).plusDays(it) }.filter(everyThird::occursOn)

        assertEquals(listOf(LocalDate.of(2028, 2, 26), LocalDate.of(2028, 2, 29), LocalDate.of(2028, 3, 3), LocalDate.of(2028, 3, 6)), days)
    }

    @Test
    fun `a day lists untimed routines first, then by time`() {
        val sport = routine(Recurrence.Daily, "Sport", LocalTime.of(18, 0))
        val walk = routine(Recurrence.Daily, "Walk", LocalTime.of(7, 30))
        val ruedersdorf = routine(Recurrence.Weekly(setOf(WEDNESDAY)), "Rüdersdorf", checkable = false)
        val tuesdays = routine(Recurrence.Weekly(setOf(TUESDAY)), "Tuesdays")

        val day = RoutinePlan(listOf(sport, tuesdays, walk, ruedersdorf), emptyList()).day(wednesday, wednesday)

        assertEquals(listOf("Rüdersdorf", "Walk", "Sport"), day.map { it.routine.title })
    }

    @Test
    fun `status comes from what was ticked off, and from the day`() {
        val sport = routine(Recurrence.Daily, start = wednesday.minusDays(3))
        val event = routine(Recurrence.Daily, "Rüdersdorf", checkable = false, start = wednesday.minusDays(3))
        val zone = ZoneId.of("Europe/Berlin")
        val completions =
            listOf(
                RoutineCompletion(sport.id, wednesday.minusDays(2), CompletionStatus.DONE, Instant.EPOCH, zone),
                RoutineCompletion(sport.id, wednesday.minusDays(1), CompletionStatus.SKIPPED, Instant.EPOCH, zone),
            )
        val plan = RoutinePlan(listOf(sport, event), completions)

        fun status(
            date: LocalDate,
            title: String = "Sport",
        ) = plan.day(date, wednesday).single { it.routine.title == title }.status

        assertEquals(OccurrenceStatus.MISSED, status(wednesday.minusDays(3)))
        assertEquals(OccurrenceStatus.DONE, status(wednesday.minusDays(2)))
        assertEquals(OccurrenceStatus.SKIPPED, status(wednesday.minusDays(1)))
        assertEquals(OccurrenceStatus.OPEN, status(wednesday))
        assertEquals(OccurrenceStatus.OPEN, status(wednesday.plusDays(1)))
        assertEquals(OccurrenceStatus.HAPPENS, status(wednesday.minusDays(3), "Rüdersdorf"))
    }

    private val sport = routine(Recurrence.Daily, "Sport", LocalTime.of(18, 0), start = wednesday.minusDays(7))
    private val ruedersdorf = routine(Recurrence.Weekly(setOf(WEDNESDAY)), "Rüdersdorf", checkable = false, start = wednesday.minusDays(7))
    private val treffen = routine(Recurrence.Weekly(setOf(THURSDAY)), "Treffen", checkable = false, start = wednesday.minusDays(7))

    private fun RoutinePlan.status(
        date: LocalDate,
        routine: Routine = sport,
    ) = day(date, wednesday).single { it.routine.id == routine.id }

    @Test
    fun `a routine gives way on days its blockers happen`() {
        val rules = listOf(BlockRule(sport.id, ruedersdorf.id), BlockRule(sport.id, treffen.id))
        val plan = RoutinePlan(listOf(sport, ruedersdorf, treffen), emptyList(), rules)

        val wed = plan.status(wednesday)
        assertEquals(OccurrenceStatus.BLOCKED, wed.status)
        assertEquals(listOf(ruedersdorf), wed.blockedBy)
        assertEquals(OccurrenceStatus.BLOCKED, plan.status(wednesday.plusDays(1)).status)
        assertEquals(OccurrenceStatus.OPEN, plan.status(wednesday.plusDays(2)).status)
        assertEquals(OccurrenceStatus.HAPPENS, plan.status(wednesday, ruedersdorf).status)
    }

    @Test
    fun `a blocked day in the past is not missed`() {
        val plan = RoutinePlan(listOf(sport, ruedersdorf), emptyList(), listOf(BlockRule(sport.id, ruedersdorf.id)))

        assertEquals(OccurrenceStatus.BLOCKED, plan.status(wednesday.minusDays(7)).status)
        assertEquals(OccurrenceStatus.MISSED, plan.status(wednesday.minusDays(6)).status)
    }

    @Test
    fun `what was ticked off stands on a day that is blocked`() {
        val done = RoutineCompletion(sport.id, wednesday, CompletionStatus.DONE, Instant.EPOCH, ZoneId.of("Europe/Berlin"))
        val plan = RoutinePlan(listOf(sport, ruedersdorf), listOf(done), listOf(BlockRule(sport.id, ruedersdorf.id)))

        assertEquals(OccurrenceStatus.DONE, plan.status(wednesday).status)
    }

    @Test
    fun `a blocker that is itself blocked doesn't block`() {
        // Rüdersdorf gives way to a trip, so Sport is back on that Wednesday.
        val trip = routine(Recurrence.Weekly(setOf(WEDNESDAY)), "Trip", checkable = false, start = wednesday)
        val rules = listOf(BlockRule(sport.id, ruedersdorf.id), BlockRule(ruedersdorf.id, trip.id))
        val plan = RoutinePlan(listOf(sport, ruedersdorf, trip), emptyList(), rules)

        assertEquals(OccurrenceStatus.BLOCKED, plan.status(wednesday, ruedersdorf).status)
        assertEquals(OccurrenceStatus.OPEN, plan.status(wednesday).status)
        assertEquals(OccurrenceStatus.BLOCKED, plan.status(wednesday.minusDays(7)).status)
    }

    @Test
    fun `a loop in stored rules doesn't hang`() {
        val rules = listOf(BlockRule(sport.id, ruedersdorf.id), BlockRule(ruedersdorf.id, sport.id))

        RoutinePlan(listOf(sport, ruedersdorf), emptyList(), rules).day(wednesday, wednesday)
    }

    @Test
    fun `rules that would close a loop are found`() {
        val rules = listOf(BlockRule("a", "b"), BlockRule("b", "c"))

        assertTrue(wouldCycle("c", "a", rules))
        assertTrue(wouldCycle("b", "a", rules))
        assertTrue(wouldCycle("a", "a", rules))
        assertFalse(wouldCycle("a", "c", rules))
        assertFalse(wouldCycle("d", "a", rules))
    }
}

package de.steppicrew.kladde.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek.WEDNESDAY
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class RoutinePauseTest {
    // 2026-11-25 is a Wednesday.
    private val wednesday = LocalDate.of(2026, 11, 25)
    private val start = wednesday.minusDays(14)
    private val sport = Routine("sport", "Sport", Recurrence.Daily, start, LocalTime.of(18, 0), true, Instant.EPOCH)
    private val reading = Routine("read", "Lesen", Recurrence.Daily, start, null, true, Instant.EPOCH)
    private val ruedersdorf = Routine("rued", "Rüdersdorf", Recurrence.Weekly(setOf(WEDNESDAY)), start, null, false, Instant.EPOCH)
    private val vacation = RoutinePause("p1", wednesday.minusDays(3), wednesday.plusDays(3), note = "Urlaub")

    private fun plan(
        vararg pauses: RoutinePause,
        completions: List<RoutineCompletion> = emptyList(),
        overrides: List<OccurrenceOverride> = emptyList(),
    ) = RoutinePlan(
        listOf(sport, reading, ruedersdorf),
        completions,
        listOf(BlockRule(sport.id, ruedersdorf.id)),
        overrides,
        pauses.toList(),
    )

    private fun RoutinePlan.of(
        date: LocalDate,
        routine: Routine = sport,
    ) = day(date, wednesday).single { it.routine.id == routine.id }

    @Test
    fun `a pause for all routines pauses every one, from and until included`() {
        val plan = plan(vacation)

        assertEquals(OccurrenceStatus.MISSED, plan.of(wednesday.minusDays(4)).status)
        for (offset in -3L..3) {
            val occurrence = plan.of(wednesday.plusDays(offset), reading)
            assertEquals(OccurrenceStatus.PAUSED, occurrence.status)
            assertEquals(vacation, occurrence.pausedBy)
        }
        assertEquals(OccurrenceStatus.OPEN, plan.of(wednesday.plusDays(4)).status)
    }

    @Test
    fun `a past paused day is not missed`() {
        assertEquals(OccurrenceStatus.PAUSED, plan(vacation).of(wednesday.minusDays(2)).status)
    }

    @Test
    fun `a pause for chosen routines leaves the others alone`() {
        val plan = plan(vacation.copy(routineIds = setOf(reading.id)))

        assertEquals(OccurrenceStatus.PAUSED, plan.of(wednesday.plusDays(1), reading).status)
        assertEquals(OccurrenceStatus.OPEN, plan.of(wednesday.plusDays(1)).status)
    }

    @Test
    fun `an open-ended pause lasts`() {
        val plan = plan(RoutinePause("p2", wednesday, null))

        assertEquals(OccurrenceStatus.PAUSED, plan.of(wednesday.plusYears(1)).status)
    }

    @Test
    fun `a paused blocker doesn't block`() {
        val plan = plan(vacation.copy(routineIds = setOf(ruedersdorf.id)))

        assertEquals(OccurrenceStatus.PAUSED, plan.of(wednesday, ruedersdorf).status)
        assertEquals(OccurrenceStatus.OPEN, plan.of(wednesday).status)
    }

    @Test
    fun `done on vacation anyway counts as done`() {
        val done = RoutineCompletion(sport.id, wednesday.plusDays(1), CompletionStatus.DONE, Instant.EPOCH, ZoneId.of("Europe/Berlin"))

        assertEquals(OccurrenceStatus.DONE, plan(vacation, completions = listOf(done)).of(wednesday.plusDays(1)).status)
    }

    @Test
    fun `overlapping pauses pause once`() {
        val plan = plan(vacation, RoutinePause("p2", wednesday, wednesday.plusDays(10)))

        assertEquals(OccurrenceStatus.PAUSED, plan.of(wednesday.plusDays(8)).status)
        assertEquals(1, plan.day(wednesday, wednesday).count { it.routine.id == sport.id })
    }

    @Test
    fun `an occurrence moved into a pause still shows as due`() {
        val plan = plan(vacation, overrides = listOf(OccurrenceOverride(sport.id, wednesday.plusDays(5), wednesday.plusDays(2))))

        val moved = plan.day(wednesday.plusDays(2), wednesday).filter { it.routine.id == sport.id }
        assertEquals(listOf(OccurrenceStatus.PAUSED, OccurrenceStatus.OPEN), moved.map { it.status })
    }
}

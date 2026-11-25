package de.steppicrew.kladde.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.WEDNESDAY
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class RoutineExtraTest {
    // 2026-11-25 is a Wednesday.
    private val wednesday = LocalDate.of(2026, 11, 25)
    private val friday = wednesday.plusDays(2)
    private val start = wednesday.minusDays(7)
    private val sport = Routine("sport", "Sport", Recurrence.Daily, start, LocalTime.of(18, 0), true, Instant.EPOCH)
    private val ruedersdorf = Routine("rued", "Rüdersdorf", Recurrence.Weekly(setOf(WEDNESDAY)), start, null, false, Instant.EPOCH)
    private val rules = listOf(BlockRule(sport.id, ruedersdorf.id))

    private fun plan(
        vararg extras: RoutineExtra,
        pauses: List<RoutinePause> = emptyList(),
        completions: List<RoutineCompletion> = emptyList(),
    ) = RoutinePlan(listOf(sport, ruedersdorf), completions, rules, emptyList(), pauses, extras.toList())

    private fun RoutinePlan.of(
        date: LocalDate,
        routine: Routine,
    ) = day(date, wednesday).filter { it.routine.id == routine.id }

    @Test
    fun `an extra adds the routine on that day and blocks there too`() {
        val plan = plan(RoutineExtra(ruedersdorf.id, friday))

        val extra = plan.of(friday, ruedersdorf).single()
        assertTrue(extra.isExtra)
        assertEquals(OccurrenceStatus.HAPPENS, extra.status)
        assertEquals(OccurrenceStatus.BLOCKED, plan.of(friday, sport).single().status)
        // The planned Wednesday is unchanged.
        assertEquals(OccurrenceStatus.HAPPENS, plan.of(wednesday, ruedersdorf).single().status)
    }

    @Test
    fun `an extra on a day the routine is planned anyway adds nothing`() {
        assertEquals(1, plan(RoutineExtra(ruedersdorf.id, wednesday)).of(wednesday, ruedersdorf).size)
    }

    @Test
    fun `an extra is ticked off under its own day`() {
        val gym = Routine("gym", "Gym", Recurrence.Weekly(setOf(WEDNESDAY)), start, null, true, Instant.EPOCH)
        val done = RoutineCompletion(gym.id, friday, CompletionStatus.DONE, Instant.EPOCH, ZoneId.of("Europe/Berlin"))
        val plan = RoutinePlan(listOf(gym), listOf(done), extras = listOf(RoutineExtra(gym.id, friday)))

        assertEquals(OccurrenceStatus.DONE, plan.of(friday, gym).single().status)
        assertEquals(OccurrenceStatus.OPEN, plan.of(wednesday, gym).single().status)
    }

    @Test
    fun `an extra is shown even inside a pause`() {
        val pause = RoutinePause("p", wednesday, null)

        assertEquals(
            OccurrenceStatus.HAPPENS,
            plan(RoutineExtra(ruedersdorf.id, friday), pauses = listOf(pause)).of(friday, ruedersdorf).single().status,
        )
    }

    @Test
    fun `extras for a deleted routine are ignored`() {
        assertEquals(
            listOf(
                sport,
            ),
            RoutinePlan(listOf(sport), emptyList(), extras = listOf(RoutineExtra("gone", friday))).day(friday, wednesday).map {
                it.routine
            },
        )
    }
}

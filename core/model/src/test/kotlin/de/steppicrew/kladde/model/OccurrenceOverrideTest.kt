package de.steppicrew.kladde.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class OccurrenceOverrideTest {
    // 2026-11-25 is a Wednesday.
    private val wednesday = LocalDate.of(2026, 11, 25)
    private val thursday = wednesday.plusDays(1)
    private val friday = wednesday.plusDays(2)
    private val start = wednesday.minusDays(7)
    private val sport = Routine("sport", "Sport", Recurrence.Daily, start, LocalTime.of(18, 0), true, Instant.EPOCH)
    private val gym = Routine("gym", "Gym", Recurrence.Weekly(setOf(WEDNESDAY, FRIDAY)), start, LocalTime.of(7, 0), true, Instant.EPOCH)
    private val ruedersdorf = Routine("rued", "Rüdersdorf", Recurrence.Weekly(setOf(WEDNESDAY)), start, null, false, Instant.EPOCH)
    private val sportGivesWay = listOf(BlockRule(sport.id, ruedersdorf.id))

    private fun plan(
        vararg overrides: OccurrenceOverride,
        completions: List<RoutineCompletion> = emptyList(),
        rules: List<BlockRule> = sportGivesWay,
    ) = RoutinePlan(listOf(sport, gym, ruedersdorf), completions, rules, overrides.toList())

    private fun RoutinePlan.of(
        date: LocalDate,
        routine: Routine,
    ) = day(date, wednesday).filter { it.routine.id == routine.id }

    @Test
    fun `a moved occurrence leaves its day and shows on the new one`() {
        val plan = plan(OccurrenceOverride(gym.id, wednesday, thursday))

        assertTrue(plan.of(wednesday, gym).isEmpty())
        val moved = plan.of(thursday, gym).single()
        assertEquals(wednesday, moved.originalDate)
        assertTrue(moved.isMoved)
        assertEquals(OccurrenceStatus.OPEN, moved.status)
    }

    @Test
    fun `a cancelled occurrence stays listed as cancelled on its day`() {
        val plan = plan(OccurrenceOverride(gym.id, wednesday, null))

        assertEquals(OccurrenceStatus.CANCELLED, plan.of(wednesday, gym).single().status)
        assertEquals(OccurrenceStatus.OPEN, plan.of(friday, gym).single().status)
    }

    @Test
    fun `cancelling the blocker brings the blocked routine back`() {
        assertEquals(OccurrenceStatus.BLOCKED, plan().of(wednesday, sport).single().status)

        val plan = plan(OccurrenceOverride(ruedersdorf.id, wednesday, null))

        assertEquals(OccurrenceStatus.OPEN, plan.of(wednesday, sport).single().status)
    }

    @Test
    fun `moving the blocker moves the block with it`() {
        val plan = plan(OccurrenceOverride(ruedersdorf.id, wednesday, friday))

        assertEquals(OccurrenceStatus.OPEN, plan.of(wednesday, sport).single().status)
        val fri = plan.of(friday, sport).single()
        assertEquals(OccurrenceStatus.BLOCKED, fri.status)
        assertEquals(listOf(ruedersdorf), fri.blockedBy)
        // Next week's Rüdersdorf is unchanged.
        assertEquals(OccurrenceStatus.BLOCKED, plan.of(wednesday.plusWeeks(1), sport).single().status)
    }

    @Test
    fun `an occurrence moved onto a blocked day is shown, not hidden`() {
        val plan = plan(OccurrenceOverride(sport.id, thursday, wednesday))

        val onWednesday = plan.of(wednesday, sport)
        assertEquals(listOf(OccurrenceStatus.BLOCKED, OccurrenceStatus.OPEN), onWednesday.map { it.status })
        assertTrue(plan.of(thursday, sport).isEmpty())
    }

    @Test
    fun `a moved occurrence keeps what was ticked off for it`() {
        val done = RoutineCompletion(gym.id, wednesday, CompletionStatus.DONE, Instant.EPOCH, ZoneId.of("Europe/Berlin"))

        val plan = plan(OccurrenceOverride(gym.id, wednesday, thursday), completions = listOf(done))

        assertEquals(OccurrenceStatus.DONE, plan.of(thursday, gym).single().status)
    }

    @Test
    fun `moving onto a day that already has one shows both`() {
        val plan = plan(OccurrenceOverride(gym.id, wednesday, friday))

        assertEquals(listOf(friday, wednesday), plan.of(friday, gym).map { it.originalDate })
    }

    @Test
    fun `changes for a deleted routine are ignored`() {
        val plan = RoutinePlan(listOf(sport), emptyList(), emptyList(), listOf(OccurrenceOverride("gone", wednesday, thursday)))

        assertEquals(listOf(sport), plan.day(thursday, wednesday).map { it.routine })
    }

    @Test
    fun `a move can change the time too, or only the time`() {
        val plan =
            plan(
                OccurrenceOverride(gym.id, wednesday, thursday, LocalTime.of(9, 30)),
                OccurrenceOverride(gym.id, friday, friday, LocalTime.of(20, 0)),
            )

        val moved = plan.of(thursday, gym).single()
        assertEquals(LocalTime.of(9, 30), moved.time)
        assertEquals(LocalTime.of(7, 0), moved.plannedTime)
        val retimed = plan.of(friday, gym).single()
        assertEquals(LocalTime.of(20, 0), retimed.time)
        assertTrue(retimed.isMoved)
    }

    @Test
    fun `a day moved to another time sorts by its new time and is never hidden`() {
        val plan = plan(OccurrenceOverride(sport.id, wednesday, wednesday, LocalTime.of(6, 0)))

        // Untimed first; Sport at its new 06:00 before Gym at 07:00, and not hidden by Rüdersdorf.
        assertEquals(listOf("Rüdersdorf", "Sport", "Gym"), plan.day(wednesday, wednesday).map { it.routine.title })
        assertEquals(OccurrenceStatus.OPEN, plan.of(wednesday, sport).single().status)
    }

    @Test
    fun `done records when it was done`() {
        val at = Instant.parse("2026-11-25T17:42:00Z")
        val done =
            RoutineCompletion(
                gym.id,
                wednesday,
                CompletionStatus.DONE,
                Instant.parse("2026-11-25T19:00:00Z"),
                ZoneId.of("Europe/Berlin"),
                at,
            )

        assertEquals(at, plan(completions = listOf(done)).of(wednesday, gym).single().doneAt)
        assertEquals(done.recordedAt, done.copy(doneAt = done.recordedAt).doneAt)
    }
}

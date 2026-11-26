package de.steppicrew.kladde.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.MonthDay
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderTest {
    private val berlin = ZoneId.of("Europe/Berlin")
    private val tokyo = ZoneId.of("Asia/Tokyo")

    // 2026-11-25 is a Wednesday.
    private val wednesday = LocalDate.of(2026, 11, 25)

    private fun at(
        date: LocalDate,
        hour: Int,
        minute: Int = 0,
        zone: ZoneId = berlin,
    ): Instant = ZonedDateTime.of(date.atTime(hour, minute), zone).toInstant()

    private fun routine(
        title: String = "Sport",
        recurrence: Recurrence = Recurrence.Daily,
        time: LocalTime? = LocalTime.of(18, 0),
        remindBefore: Duration? = Duration.ZERO,
        timeZone: ZoneId? = null,
    ) = Routine(
        title.lowercase(),
        title,
        recurrence,
        wednesday.minusWeeks(1),
        time,
        true,
        Instant.EPOCH,
        timeZone = timeZone,
        remindBefore = remindBefore,
    )

    private fun next(
        routines: List<Routine>,
        after: Instant,
        here: ZoneId = berlin,
    ) = RoutinePlan(routines, emptyList()).nextReminders(after, here)

    @Test
    fun `the next reminder is today's time, or tomorrow's once it has passed`() {
        val sport = routine()

        assertEquals(at(wednesday, 18), next(listOf(sport), at(wednesday, 9)).single().at)
        assertEquals(at(wednesday.plusDays(1), 18), next(listOf(sport), at(wednesday, 18)).single().at)
    }

    @Test
    fun `an offset comes before the time, routines without a reminder or a time don't remind`() {
        val early = routine("Early", remindBefore = Duration.ofMinutes(15))
        val silent = routine("Silent", remindBefore = null)
        val untimed = routine("Untimed", time = null)

        val reminders = next(listOf(early, silent, untimed), at(wednesday, 9))

        assertEquals(listOf(at(wednesday, 17, 45)), reminders.map { it.at })
    }

    @Test
    fun `done early, cancelled or paused days are passed over, events remind too`() {
        val sport = routine()
        val done = RoutineCompletion(sport.id, wednesday, CompletionStatus.DONE, Instant.EPOCH, berlin)
        val cancelled = OccurrenceOverride(sport.id, wednesday.plusDays(1), null)
        val pause = RoutinePause("p", wednesday.plusDays(2), wednesday.plusDays(3))

        val plan = RoutinePlan(listOf(sport), listOf(done), overrides = listOf(cancelled), pauses = listOf(pause))
        assertEquals(at(wednesday.plusDays(4), 18), plan.nextReminders(at(wednesday, 9), berlin).single().at)

        val event = routine("Rüdersdorf").copy(checkable = false)
        assertEquals(at(wednesday, 18), next(listOf(event), at(wednesday, 9)).single().at)
    }

    @Test
    fun `a moved occurrence reminds at its new day and time`() {
        val sport = routine(recurrence = Recurrence.Weekly(setOf(wednesday.dayOfWeek)))
        val moved = OccurrenceOverride(sport.id, wednesday, wednesday.plusDays(1), LocalTime.of(7, 0))

        val reminder = RoutinePlan(listOf(sport), emptyList(), overrides = listOf(moved)).nextReminders(at(wednesday, 9), berlin).single()

        assertEquals(at(wednesday.plusDays(1), 7), reminder.at)
        assertEquals(wednesday, reminder.occurrence.originalDate)
    }

    @Test
    fun `a floating routine reminds at its time where the phone is, a zone-bound one at its own zone's`() {
        val floating = routine("Floating")
        val call = routine("Call", timeZone = berlin)

        val inTokyo = next(listOf(floating, call), at(wednesday, 0, zone = tokyo), here = tokyo)

        assertEquals(at(wednesday, 18, zone = tokyo), inTokyo.single { it.occurrence.routine == floating }.at)
        assertEquals(at(wednesday, 18), inTokyo.single { it.occurrence.routine == call }.at)
    }

    @Test
    fun `Advent of Code reminds at 6 in Berlin on 1 December, a year ahead if need be`() {
        val dec = { day: Int -> MonthDay.of(Month.DECEMBER, day) }
        val aoc = routine("AoC", Recurrence.Yearly(dec(1), dec(12)), LocalTime.of(6, 0), timeZone = berlin)

        assertEquals(at(LocalDate.of(2026, 12, 1), 6), next(listOf(aoc), at(wednesday, 9)).single().at)
        assertEquals(at(LocalDate.of(2027, 12, 1), 6), next(listOf(aoc), at(LocalDate.of(2026, 12, 12), 7)).single().at)
    }

    @Test
    fun `the clock change keeps the wall-clock time`() {
        // Summer time ends in Berlin on Sunday 2026-10-25.
        val sport = routine().copy(startDate = LocalDate.of(2026, 10, 1))

        val reminder = next(listOf(sport), at(LocalDate.of(2026, 10, 25), 12)).single()

        assertEquals(at(LocalDate.of(2026, 10, 25), 18), reminder.at)
        assertTrue(reminder.at.atZone(berlin).toLocalTime() == LocalTime.of(18, 0))
    }
}

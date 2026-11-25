package de.steppicrew.kladde.routines

import de.steppicrew.kladde.model.BlockRule
import de.steppicrew.kladde.model.NthWeekday
import de.steppicrew.kladde.model.Recurrence
import de.steppicrew.kladde.model.ScheduleVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.MonthDay
import java.time.ZoneId

class RoutineFormTest {
    // 2026-11-25 is a Wednesday.
    private val today = LocalDate.of(2026, 11, 25)
    private val now = Instant.parse("2026-11-25T20:00:00Z")

    private fun draft(
        title: String,
        vararg cards: ScheduleDraft,
    ) = RoutineDraft(title, cards.toList().ifEmpty { listOf(ScheduleDraft(today)) })

    private val thursdays = ScheduleDraft(today, Repeat.WEEKDAYS, days = setOf(DayOfWeek.THURSDAY))

    @Test
    fun `a draft needs a title and complete cards on different days`() {
        assertFalse(RoutineDraft.new(today).isComplete)
        assertTrue(draft("Sport").isComplete)
        assertFalse(draft("Rüdersdorf", ScheduleDraft(today, Repeat.WEEKDAYS)).isComplete)
        assertFalse(draft("Sport", ScheduleDraft(today, Repeat.EVERY_N_DAYS, interval = "1")).isComplete)
        assertTrue(draft("Sport", ScheduleDraft(today, Repeat.EVERY_N_DAYS, interval = " 3 ")).isComplete)
        assertFalse(draft("Sport", ScheduleDraft(today), thursdays).isComplete)
        assertNull(draft("  ").toRoutine("r1", now))
    }

    @Test
    fun `a new routine starts on its card's day`() {
        val routine = draft(" Sport ", ScheduleDraft(today, time = LocalTime.of(18, 0))).toRoutine("r1", now)!!

        assertEquals("Sport", routine.title)
        assertEquals(Recurrence.Daily, routine.recurrence)
        assertEquals(today, routine.startDate)
        assertEquals(LocalTime.of(18, 0), routine.time)
        assertEquals(now, routine.createdAt)
    }

    @Test
    fun `the form shows every schedule of a routine, and gives it back unchanged`() {
        val cards =
            listOf(
                thursdays.copy(validFrom = today.minusWeeks(2)),
                ScheduleDraft(today, Repeat.WEEKDAYS, days = setOf(DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY)),
                ScheduleDraft(today.plusWeeks(3), Repeat.NONE),
            )
        val eileen = RoutineDraft("Eileen", cards, checkable = false).toRoutine("e", now)!!

        assertEquals(3, eileen.schedules.size)
        assertEquals(RoutineDraft("Eileen", cards, checkable = false), RoutineDraft.of(eileen))
        assertEquals(eileen, RoutineDraft.of(eileen).toRoutine("other", Instant.EPOCH, eileen))
    }

    @Test
    fun `changing a card in the middle keeps the later ones`() {
        val eileen =
            RoutineDraft(
                "Eileen",
                listOf(
                    thursdays.copy(validFrom = today.minusWeeks(2)),
                    ScheduleDraft(today),
                    ScheduleDraft(today.plusWeeks(1), Repeat.NONE),
                ),
            ).toRoutine("e", now)!!
        val form = RoutineDraft.of(eileen)
        val middle = form.byDate[1]

        val changed = form.replace(middle.index, middle.value.copy(time = LocalTime.of(9, 0))).toRoutine("x", now, eileen)!!
        assertEquals(LocalTime.of(9, 0), changed.timeOn(today))
        assertFalse(changed.occursOn(today.plusWeeks(2)))
        assertEquals("e", changed.id)
    }

    @Test
    fun `deleting a change lets the card before it go on`() {
        val eileen =
            RoutineDraft(
                "Eileen",
                listOf(thursdays.copy(validFrom = today.minusWeeks(2)), ScheduleDraft(today, Repeat.NONE)),
            ).toRoutine("e", now)!!
        val form = RoutineDraft.of(eileen)

        val back = form.remove(form.byDate.last().index).toRoutine("x", now, eileen)!!
        assertTrue(back.changes.isEmpty())
        assertTrue(back.occursOn(today.plusDays(1)))
    }

    @Test
    fun `an added card starts at the opened event as a copy of the schedule then, or after the last card`() {
        val form = RoutineDraft("Eileen", listOf(thursdays.copy(validFrom = today.minusWeeks(2), time = LocalTime.of(17, 0))))

        val added = form.added(today)
        assertEquals(thursdays.copy(time = LocalTime.of(17, 0)), added.schedules.last())

        val again = added.added(today)
        assertEquals(today.plusDays(1), again.schedules.last().validFrom)
        assertTrue(again.isComplete)
    }

    @Test
    fun `only the card in effect today and later ones are shown by default`() {
        val form =
            RoutineDraft(
                "Eileen",
                listOf(
                    ScheduleDraft(today.plusWeeks(1)),
                    thursdays.copy(validFrom = today.minusWeeks(4)),
                    ScheduleDraft(today.minusWeeks(1)),
                ),
            )

        assertEquals(listOf(today.minusWeeks(1), today.plusWeeks(1)), form.currentAndLater(today).map { it.value.validFrom })
        assertEquals(3, form.currentAndLater(today.minusYears(1)).size)
    }

    @Test
    fun `the form shows what a routine gives way to`() {
        val sport = draft("Sport").toRoutine("r1", now)!!
        val rules = listOf(BlockRule("r1", "r2"), BlockRule("r1", "r3"), BlockRule("r2", "r3"))

        assertEquals(setOf("r2", "r3"), RoutineDraft.of(sport, rules).blockers)
    }

    @Test
    fun `monthly on the 1st and 3rd Monday combines places and weekdays`() {
        val card = ScheduleDraft(today, Repeat.MONTHLY_WEEKDAYS, days = setOf(DayOfWeek.MONDAY), nths = setOf(1, 3))

        assertEquals(
            Recurrence.MonthlyByWeekday(setOf(NthWeekday(1, DayOfWeek.MONDAY), NthWeekday(3, DayOfWeek.MONDAY))),
            card.recurrence,
        )
        assertEquals(card, ScheduleDraft.of(card.toVersion()!!))
        assertNull(card.copy(nths = emptySet()).recurrence)
        assertNull(card.copy(days = emptySet()).recurrence)
    }

    @Test
    fun `monthly on days of the month`() {
        val card = ScheduleDraft(today, Repeat.MONTHLY_DAYS, monthDays = setOf(1, Recurrence.MonthlyByDay.LAST_DAY))

        assertEquals(Recurrence.MonthlyByDay(setOf(1, -1)), card.recurrence)
        assertEquals(card, ScheduleDraft.of(card.toVersion()!!))
        assertNull(card.copy(monthDays = emptySet()).recurrence)
    }

    @Test
    fun `a yearly span, and 29 February can't be an end`() {
        val dec = { day: Int -> MonthDay.of(Month.DECEMBER, day) }
        val card = ScheduleDraft(today, Repeat.YEARLY, yearFrom = dec(1), yearUntil = dec(12), time = LocalTime.of(6, 0))

        assertEquals(ScheduleVersion(today, Recurrence.Yearly(dec(1), dec(12)), LocalTime.of(6, 0)), card.toVersion())
        assertEquals(card, ScheduleDraft.of(card.toVersion()!!))
        assertNull(card.copy(yearUntil = null).recurrence)
        assertNull(card.copy(yearFrom = LEAP_DAY).recurrence)
    }

    @Test
    fun `not planned is a card like any other`() {
        assertEquals(Recurrence.None, ScheduleDraft(today, Repeat.NONE).recurrence)
    }

    @Test
    fun `going on during pauses, groups and the zone are kept through the form`() {
        val form =
            RoutineDraft(
                "Call",
                listOf(ScheduleDraft(today, time = LocalTime.of(18, 0))),
                ignoresPauses = true,
                timeZone = "Europe/Berlin",
                groups = setOf(" Arbeit ", " "),
            )
        val call = form.toRoutine("r1", now)!!

        assertTrue(call.ignoresPauses)
        assertEquals(ZoneId.of("Europe/Berlin"), call.timeZone)
        assertEquals(setOf("Arbeit"), call.groups)
        assertEquals(form.copy(groups = setOf("Arbeit")), RoutineDraft.of(call))
    }

    @Test
    fun `a zone binds only a routine with a time`() {
        assertNull(RoutineDraft("Call", listOf(ScheduleDraft(today)), timeZone = "Europe/Berlin").toRoutine("r1", now)!!.timeZone)
    }

    @Test
    fun `a card that started before today is past, the one running today is changed by a new card`() {
        val running = thursdays.copy(validFrom = today.minusWeeks(2))
        val form = RoutineDraft("Eileen", listOf(running, ScheduleDraft(today.plusWeeks(1))))

        assertTrue(form.isPast(running, today))
        assertFalse(form.isPast(ScheduleDraft(today), today))
        assertEquals(running, form.running(today)!!.value)
        assertEquals(
            today,
            form
                .added(today)
                .schedules
                .last()
                .validFrom,
        )

        // A card starting today is not past: it is changed in place.
        assertNull(RoutineDraft("Sport", listOf(ScheduleDraft(today))).running(today))
    }
}

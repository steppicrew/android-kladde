package de.steppicrew.kladde.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.LocalDate
import java.time.YearMonth

class RecurrenceTest {
    private fun days(
        recurrence: Recurrence,
        month: YearMonth,
    ) = (1..month.lengthOfMonth()).map(month::atDay).filter(recurrence::matches).map { it.dayOfMonth }

    @Test
    fun `monthly rrules are written and read back`() {
        val cases =
            mapOf(
                Recurrence.MonthlyByWeekday(setOf(NthWeekday(3, MONDAY), NthWeekday(1, MONDAY))) to "FREQ=MONTHLY;BYDAY=1MO,3MO",
                Recurrence.MonthlyByWeekday(setOf(NthWeekday(NthWeekday.LAST, FRIDAY), NthWeekday(2, FRIDAY))) to
                    "FREQ=MONTHLY;BYDAY=2FR,-1FR",
                Recurrence.MonthlyByDay(setOf(15, -1, 1)) to "FREQ=MONTHLY;BYMONTHDAY=1,15,-1",
            )
        for ((recurrence, rule) in cases) {
            assertEquals(rule, recurrence.toRrule())
            assertEquals(recurrence, Recurrence.fromRrule(rule))
        }
        assertEquals(Recurrence.MonthlyByWeekday(setOf(NthWeekday(1, MONDAY))), Recurrence.fromRrule("FREQ=MONTHLY;BYDAY=+1MO"))
    }

    @Test
    fun `monthly rules outside the supported forms are not read`() {
        val unsupported =
            listOf(
                "FREQ=MONTHLY",
                "FREQ=MONTHLY;BYDAY=MO",
                "FREQ=MONTHLY;BYDAY=5MO",
                "FREQ=MONTHLY;BYDAY=-2MO",
                "FREQ=MONTHLY;BYDAY=1XX",
                "FREQ=MONTHLY;BYMONTHDAY=32",
                "FREQ=MONTHLY;BYMONTHDAY=0",
                "FREQ=MONTHLY;BYMONTHDAY=-2",
                "FREQ=MONTHLY;BYDAY=1MO;BYMONTHDAY=1",
                "FREQ=MONTHLY;INTERVAL=2;BYMONTHDAY=1",
                "FREQ=WEEKLY;BYDAY=1MO",
                "FREQ=DAILY;BYMONTHDAY=1",
            )
        for (rule in unsupported) assertNull(rule, Recurrence.fromRrule(rule))
    }

    @Test
    fun `the 1st and 3rd Monday`() {
        val rule = Recurrence.MonthlyByWeekday(setOf(NthWeekday(1, MONDAY), NthWeekday(3, MONDAY)))

        // November 2026 starts on a Sunday; December 2026 on a Tuesday.
        assertEquals(listOf(2, 16), days(rule, YearMonth.of(2026, 11)))
        assertEquals(listOf(7, 21), days(rule, YearMonth.of(2026, 12)))
    }

    @Test
    fun `the last Friday, also in a month with five`() {
        val rule = Recurrence.MonthlyByWeekday(setOf(NthWeekday(NthWeekday.LAST, FRIDAY)))

        assertEquals(listOf(27), days(rule, YearMonth.of(2026, 11)))
        // January 2027 has five Fridays: 1, 8, 15, 22, 29.
        assertEquals(listOf(29), days(rule, YearMonth.of(2027, 1)))
        assertEquals(listOf(22), days(Recurrence.MonthlyByWeekday(setOf(NthWeekday(4, FRIDAY))), YearMonth.of(2027, 1)))
    }

    @Test
    fun `days of the month skip months without them, and the last day follows the month`() {
        val rule = Recurrence.MonthlyByDay(setOf(1, 31))
        val last = Recurrence.MonthlyByDay(setOf(Recurrence.MonthlyByDay.LAST_DAY))

        assertEquals(listOf(1, 31), days(rule, YearMonth.of(2026, 12)))
        assertEquals(listOf(1), days(rule, YearMonth.of(2026, 11)))
        assertEquals(listOf(28), days(last, YearMonth.of(2027, 2)))
        assertEquals(listOf(29), days(last, YearMonth.of(2028, 2)))
        assertEquals(listOf(30), days(last, YearMonth.of(2026, 11)))
    }

    @Test
    fun `the last day and day 31 on the same day count once`() {
        val rule = Recurrence.MonthlyByDay(setOf(31, Recurrence.MonthlyByDay.LAST_DAY))

        assertEquals(listOf(31), days(rule, YearMonth.of(2026, 12)))
    }

    @Test
    fun `a monthly routine starts on its start date`() {
        val routine =
            Routine(
                "r",
                "Treffen",
                Recurrence.MonthlyByWeekday(setOf(NthWeekday(1, MONDAY), NthWeekday(3, MONDAY))),
                LocalDate.of(2026, 11, 10),
                null,
                false,
                java.time.Instant.EPOCH,
            )

        assertEquals(false, routine.occursOn(LocalDate.of(2026, 11, 2)))
        assertEquals(true, routine.occursOn(LocalDate.of(2026, 11, 16)))
    }
}

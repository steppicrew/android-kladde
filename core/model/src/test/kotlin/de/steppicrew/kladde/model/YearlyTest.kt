package de.steppicrew.kladde.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.Month.DECEMBER
import java.time.Month.FEBRUARY
import java.time.Month.JANUARY
import java.time.Month.MARCH
import java.time.MonthDay

class YearlyTest {
    private val advent = Recurrence.Yearly(MonthDay.of(DECEMBER, 1), MonthDay.of(DECEMBER, 12))
    private val newYear = Recurrence.Yearly(MonthDay.of(DECEMBER, 28), MonthDay.of(JANUARY, 3))

    @Test
    fun `a span within a month, both ends included, every year`() {
        for (year in listOf(2026, 2027, 2028)) {
            assertTrue(advent.matches(LocalDate.of(year, 12, 1)))
            assertTrue(advent.matches(LocalDate.of(year, 12, 12)))
            assertFalse(advent.matches(LocalDate.of(year, 11, 30)))
            assertFalse(advent.matches(LocalDate.of(year, 12, 13)))
        }
    }

    @Test
    fun `a span past New Year wraps`() {
        assertTrue(newYear.matches(LocalDate.of(2026, 12, 31)))
        assertTrue(newYear.matches(LocalDate.of(2027, 1, 1)))
        assertTrue(newYear.matches(LocalDate.of(2027, 1, 3)))
        assertFalse(newYear.matches(LocalDate.of(2027, 1, 4)))
        assertFalse(newYear.matches(LocalDate.of(2026, 12, 27)))
    }

    @Test
    fun `29 February lies inside a span in leap years but can't be an end`() {
        val late = Recurrence.Yearly(MonthDay.of(FEBRUARY, 27), MonthDay.of(MARCH, 1))
        assertTrue(late.matches(LocalDate.of(2028, 2, 29)))
        assertTrue(late.matches(LocalDate.of(2027, 3, 1)))
        assertEquals(
            "29 February as an end",
            runCatching { Recurrence.Yearly(MonthDay.of(FEBRUARY, 29), MonthDay.of(MARCH, 1)) }.exceptionOrNull()?.message,
        )
    }

    @Test
    fun `within a month it is a plain RRULE, across months a Kladde part, both read back`() {
        assertEquals("FREQ=YEARLY;BYMONTH=12;BYMONTHDAY=1,2,3,4,5,6,7,8,9,10,11,12", advent.toRrule())
        assertEquals("FREQ=YEARLY;X-KLADDE-SPAN=1228/0103", newYear.toRrule())
        for (rule in listOf(advent, newYear, Recurrence.Yearly(MonthDay.of(DECEMBER, 24), MonthDay.of(DECEMBER, 24)))) {
            assertEquals(rule, Recurrence.fromRrule(rule.toRrule()))
        }
    }

    @Test
    fun `yearly rules outside the offered forms are not read`() {
        assertNull(Recurrence.fromRrule("FREQ=YEARLY;BYMONTH=12;BYMONTHDAY=1,3"))
        assertNull(Recurrence.fromRrule("FREQ=YEARLY;BYMONTH=12"))
        assertNull(Recurrence.fromRrule("FREQ=YEARLY;INTERVAL=2;BYMONTH=12;BYMONTHDAY=1"))
        assertNull(Recurrence.fromRrule("FREQ=YEARLY;X-KLADDE-SPAN=1340/0103"))
        assertNull(Recurrence.fromRrule("FREQ=MONTHLY;BYMONTH=12;BYMONTHDAY=1"))
        assertNull(Recurrence.fromRrule("FREQ=DAILY;BYMONTH=12"))
    }

    @Test
    fun `a routine on a yearly span`() {
        val aoc = Routine("aoc", "Advent of Code", advent, LocalDate.of(2026, 11, 25), null, true, java.time.Instant.EPOCH)

        assertTrue(aoc.occursOn(LocalDate.of(2026, 12, 5)))
        assertTrue(aoc.occursOn(LocalDate.of(2027, 12, 5)))
        assertFalse(aoc.occursOn(LocalDate.of(2025, 12, 5)))
    }
}

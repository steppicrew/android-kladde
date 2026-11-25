package de.steppicrew.kladde.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class ShownTimeTest {
    private val berlin = ZoneId.of("Europe/Berlin")
    private val tokyo = ZoneId.of("Asia/Tokyo")
    private val newYork = ZoneId.of("America/New_York")
    private val day = LocalDate.of(2026, 11, 25)
    private val call = Routine("c", "Call", Recurrence.Daily, day, LocalTime.of(18, 0), false, Instant.EPOCH)

    private fun shown(
        routine: Routine,
        here: ZoneId,
        date: LocalDate = day,
    ) = Occurrence(routine, date, OccurrenceStatus.HAPPENS).shownTime(here)

    @Test
    fun `a floating routine keeps its wall-clock time anywhere`() {
        assertEquals(ShownTime(LocalTime.of(18, 0)), shown(call, tokyo))
    }

    @Test
    fun `a routine bound to a zone is the same there`() {
        assertEquals(ShownTime(LocalTime.of(18, 0)), shown(call.copy(timeZone = berlin), berlin))
    }

    @Test
    fun `a routine bound to a zone is converted elsewhere, with a day shift`() {
        val bound = call.copy(timeZone = berlin)

        assertEquals(ShownTime(LocalTime.of(2, 0), 1, berlin), shown(bound, tokyo))
        assertEquals(ShownTime(LocalTime.of(12, 0), 0, berlin), shown(bound, newYork))
    }

    @Test
    fun `conversion follows daylight saving time on both sides`() {
        val bound = call.copy(timeZone = berlin)

        // 2026-03-29: Berlin is on summer time, New York already since 03-08.
        assertEquals(LocalTime.of(12, 0), shown(bound, newYork, LocalDate.of(2026, 3, 29))!!.time)
        // 2026-03-20: New York on summer time, Berlin not yet: only five hours apart.
        assertEquals(LocalTime.of(13, 0), shown(bound, newYork, LocalDate.of(2026, 3, 20))!!.time)
    }

    @Test
    fun `no time, nothing to show`() {
        assertNull(shown(call.copy(time = null, timeZone = berlin), tokyo))
    }
}

package de.steppicrew.kladde.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.Instant

class TrackerTest {
    private val beer = Tracker("t1", "Beer", TrackerType.COUNTER, "count", Instant.EPOCH, listOf("Pils"))

    @Test
    fun `a new sub type is appended`() {
        assertEquals(listOf("Pils", "Weizen"), beer.withSubType("Weizen").subTypes)
    }

    @Test
    fun `a known or missing sub type changes nothing`() {
        assertSame(beer, beer.withSubType("Pils"))
        assertSame(beer, beer.withSubType(null))
    }
}

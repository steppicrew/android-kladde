package de.steppicrew.kladde.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class SubTypeLabelTest {
    private val small = SubTypeLabel("l1", "t1", "beerSmall", SubTypeLabelStatus.OPEN, Instant.EPOCH)

    @Test
    fun `a label shows its key until it has a display name`() {
        assertEquals("beerSmall", small.shownName)
        assertEquals("Kleines Bier", small.copy(displayName = "Kleines Bier").shownName)
    }

    @Test
    fun `a blank display name shows the key`() {
        assertEquals("beerSmall", small.copy(displayName = "  ").shownName)
    }
}

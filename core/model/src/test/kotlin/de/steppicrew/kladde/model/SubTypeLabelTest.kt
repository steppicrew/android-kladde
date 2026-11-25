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
    fun `the user's name beats the sending app's, which beats the key`() {
        val described = small.copy(senderLabel = "Small beer")

        assertEquals("Small beer", described.shownName)
        assertEquals("Kleines Bier", described.copy(displayName = "Kleines Bier").shownName)
    }

    @Test
    fun `a blank display name shows the key`() {
        assertEquals("beerSmall", small.copy(displayName = "  ").shownName)
    }
}

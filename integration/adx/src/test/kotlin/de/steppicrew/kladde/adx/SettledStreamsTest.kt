package de.steppicrew.kladde.adx

import de.steppicrew.adx.AdxStreamDecision
import de.steppicrew.adx.AdxStreamProposal
import de.steppicrew.adx.receiver.AdxStreamGrant
import org.junit.Assert.assertEquals
import org.junit.Test

class SettledStreamsTest {
    private val beer = AdxStreamProposal("drinks.beer", "Beer", "counter", "count")
    private val wine = AdxStreamProposal("drinks.wine", "Wine", "counter", "count")
    private val water = AdxStreamProposal("drinks.water", "Water", "counter", "count")
    private val tea = AdxStreamProposal("drinks.tea", "Tea", "counter", "count")

    @Test
    fun `a first approval settles nothing`() {
        assertEquals(emptySet<String>(), settledStreams(listOf(beer, wine), emptyMap(), setOf("t1")))
    }

    @Test
    fun `accepted into a tracker that exists, or declined, is settled, a new stream is not`() {
        val earlier =
            mapOf(
                beer.key to AdxStreamGrant(beer, AdxStreamDecision.ACCEPTED, "t1"),
                water.key to AdxStreamGrant(water, AdxStreamDecision.DECLINED),
            )

        assertEquals(setOf(beer.key, water.key), settledStreams(listOf(beer, water, tea), earlier, setOf("t1")))
    }

    @Test
    fun `a stream whose tracker was deleted is asked again`() {
        val earlier = mapOf(wine.key to AdxStreamGrant(wine, AdxStreamDecision.ACCEPTED, "gone"))

        assertEquals(emptySet<String>(), settledStreams(listOf(wine), earlier, setOf("t1")))
    }
}

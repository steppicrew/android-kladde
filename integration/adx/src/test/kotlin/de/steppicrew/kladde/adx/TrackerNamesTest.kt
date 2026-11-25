package de.steppicrew.kladde.adx

import de.steppicrew.adx.AdxStreamProposal
import de.steppicrew.kladde.model.Tracker
import de.steppicrew.kladde.model.TrackerSource
import de.steppicrew.kladde.model.TrackerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class TrackerNamesTest {
    private fun tracker(
        id: String,
        name: String,
        app: String? = null,
    ) = Tracker(id, name, TrackerType.COUNTER, "count", Instant.EPOCH, source = app?.let { TrackerSource(it, "drinks.beer") })

    private val senders = mapOf("beer.app" to "Beer Counter", "sample.app" to "Sample")
    private val withApp = { name: String, app: String -> "$name (from $app)" }

    @Test
    fun `a name several trackers share also names their app`() {
        val labels =
            trackerLabels(
                listOf(tracker("t1", "Beer", "sample.app"), tracker("t2", "beer ", "beer.app"), tracker("t3", "Wine", "beer.app")),
                senders,
                withApp,
            )

        assertEquals(mapOf("t1" to "Beer (from Sample)", "t2" to "beer  (from Beer Counter)", "t3" to "Wine"), labels)
    }

    @Test
    fun `a tracker of an app no longer connected names its package, one without an app just its name`() {
        val labels = trackerLabels(listOf(tracker("t1", "Beer", "gone.app"), tracker("t2", "Beer")), senders, withApp)

        assertEquals(mapOf("t1" to "Beer (from gone.app)", "t2" to "Beer"), labels)
    }

    @Test
    fun `new trackers start with free names, unique among themselves`() {
        val proposals = listOf(proposal("drinks.beer", "Beer"), proposal("drinks.wine", "Wine"), proposal("drinks.ale", "Beer"))
        val names = defaultNames(proposals, "Sample", listOf(tracker("t1", "Beer", "beer.app")))

        assertEquals(mapOf("drinks.beer" to "Beer (Sample)", "drinks.wine" to "Wine", "drinks.ale" to "Beer (Sample) 2"), names)
    }

    @Test
    fun `a new name must not be empty or taken, whatever its case`() {
        assertEquals(NameProblem.EMPTY, nameProblem(" ", listOf("Beer")))
        assertEquals(NameProblem.TAKEN, nameProblem("beer ", listOf("Beer")))
        assertNull(nameProblem("Pils", listOf("Beer")))
    }

    private fun proposal(
        key: String,
        name: String,
    ) = AdxStreamProposal(key, name, "counter", "count")
}

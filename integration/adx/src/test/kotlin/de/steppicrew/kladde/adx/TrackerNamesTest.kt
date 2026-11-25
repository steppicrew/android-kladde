package de.steppicrew.kladde.adx

import de.steppicrew.adx.AdxStreamProposal
import de.steppicrew.kladde.model.SubTypeLabel
import de.steppicrew.kladde.model.SubTypeLabelStatus
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

    @Test
    fun `a new entry type starts with its key, numbered when another of its tracker goes by it`() {
        val labels =
            listOf(
                label("l1", "Pils", SubTypeLabelStatus.SPECIFIED, displayName = "Pils"),
                label("l2", "beerSmall", SubTypeLabelStatus.IGNORED),
                label("l3", "Pils"),
                label("l4", "beerSmall"),
                label("l5", "Pils", trackerId = "t2"),
            )
        val open = labels.filter { it.status == SubTypeLabelStatus.OPEN }

        val names = presetLabelNames(open, otherLabelNames(labels))

        assertEquals(mapOf("l3" to "Pils 2", "l4" to "beerSmall 2", "l5" to "Pils"), names)
    }

    @Test
    fun `open entry types get names unique among themselves`() {
        val open = listOf(label("l1", "Pils"), label("l2", "pils"))

        assertEquals(mapOf("l1" to "Pils", "l2" to "pils 2"), presetLabelNames(open, otherLabelNames(open)))
    }

    @Test
    fun `only entry types already looked at count as taken`() {
        val others = otherLabelNames(listOf(label("l1", "Pils"), label("l2", "Weizen", SubTypeLabelStatus.SPECIFIED, "Weißbier")))

        assertEquals(listOf("Weißbier"), others["l1"])
        assertEquals(emptyList<String>(), others["l2"])
    }

    private fun label(
        id: String,
        key: String,
        status: SubTypeLabelStatus = SubTypeLabelStatus.OPEN,
        displayName: String? = null,
        trackerId: String = "t1",
    ) = SubTypeLabel(id, trackerId, key, status, Instant.EPOCH, displayName)

    private fun proposal(
        key: String,
        name: String,
    ) = AdxStreamProposal(key, name, "counter", "count")
}

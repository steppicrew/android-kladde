package de.steppicrew.kladde.adx

import androidx.test.core.app.ApplicationProvider
import de.steppicrew.adx.AdxAttr
import de.steppicrew.adx.AdxOp
import de.steppicrew.adx.AdxRevRule
import de.steppicrew.adx.AdxStatus
import de.steppicrew.adx.AdxStreamDecision
import de.steppicrew.adx.AdxStreamProposal
import de.steppicrew.adx.AdxSub
import de.steppicrew.adx.receiver.AdxBusyException
import de.steppicrew.adx.receiver.AdxConsent
import de.steppicrew.adx.receiver.AdxConsentState
import de.steppicrew.adx.receiver.AdxStreamGrant
import de.steppicrew.kladde.data.DataPointRepository
import de.steppicrew.kladde.data.KladdeDatabase
import de.steppicrew.kladde.data.SubTypeLabelRepository
import de.steppicrew.kladde.data.TrackerRepository
import de.steppicrew.kladde.model.SubTypeLabelStatus
import de.steppicrew.kladde.model.TrackerType
import de.steppicrew.kladde.model.VaultLock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KladdeAdxStoreTest {
    private val db = KladdeDatabase.inMemory(ApplicationProvider.getApplicationContext())
    private val trackers = TrackerRepository(db)
    private val points = DataPointRepository(db)
    private val vault =
        object : VaultLock {
            override var isUnlocked = true
        }
    private val labels = SubTypeLabelRepository(db)
    private val newLabels = mutableListOf<NewLabel>()
    private val settled = mutableListOf<String>()
    private val listener =
        object : LabelListener {
            override fun onNewLabel(new: NewLabel) {
                newLabels += new
            }

            override fun onLabelSettled(labelId: String) {
                settled += labelId
            }
        }
    private val store = KladdeAdxStore(vault, trackers, points, labels, { Instant.EPOCH }, listener)

    private val proposal =
        AdxStreamProposal(
            "drinks.beer",
            "Beer",
            "counter",
            "count",
            listOf(AdxSub("Pils")),
            listOf(AdxAttr("intake.volume_ml"), AdxAttr("intake.abv_pct")),
        )
    private val sender = AdxConsent("beer.app", "cert", "Beer Counter", AdxConsentState.APPROVED, emptyMap())
    private val grant = AdxStreamGrant(proposal, AdxStreamDecision.ACCEPTED, "tracker-1")

    @After
    fun close() = db.close()

    private fun beer(
        rev: Long,
        sub: String = "Pils",
    ) = AdxOp
        .upsert("drinks.beer", "e1", rev, Instant.parse("2026-11-25T18:00:00Z"), ZoneId.of("Europe/Berlin"))
        .value(1L)
        .sub(sub)
        .attr("intake.volume_ml", 500L)
        .build()

    private fun recent() = runBlocking { points.observeRecent(10).first() }

    @Test
    fun `an upsert lands on the mapped tracker with unit and source`() {
        store.upsert(sender, grant, beer(1))

        val point = recent().single()
        assertEquals("tracker-1", point.trackerId)
        assertEquals(1.0, point.value)
        assertEquals("count", point.unit)
        assertEquals("Pils", point.subType)
        assertEquals(1L, point.source?.rev)
        assertTrue(
            point.source
                ?.raw
                .orEmpty()
                .contains("intake.volume_ml"),
        )
        assertEquals(1L, store.currentRev(sender, grant, "e1"))
    }

    @Test
    fun `a missing target tracker is recreated from the proposal under its id`() {
        store.upsert(sender, grant, beer(1))

        val tracker = trackers.find("tracker-1")!!
        assertEquals(TrackerType.COUNTER, tracker.type)
        assertEquals("Beer", tracker.name)
    }

    @Test
    fun `a grant without target makes one tracker, not one per entry`() {
        val untargeted = grant.copy(target = null)
        store.upsert(sender, untargeted, beer(1))
        store.upsert(sender, untargeted, beer(2))

        assertEquals(1, trackers.all().size)
    }

    @Test
    fun `a new sub type is added to the tracker`() {
        store.upsert(sender, grant, beer(1, sub = "Weizen"))

        assertEquals(listOf("Pils", "Weizen"), trackers.find("tracker-1")!!.subTypes)
    }

    @Test
    fun `the first entry with a label opens a label record and reports it once`() {
        store.upsert(sender, grant, beer(1, sub = "beerSmall"))
        store.upsert(sender, grant, beer(2, sub = "beerSmall"))

        val reported = newLabels.single()
        assertEquals("beerSmall", reported.label.key)
        assertEquals("Beer", reported.trackerName)
        assertEquals("Beer Counter", reported.senderLabel)
        assertEquals(SubTypeLabelStatus.OPEN, reported.label.status)
        assertEquals(listOf("intake.volume_ml", "intake.abv_pct"), reported.label.attrKeys)
        assertEquals("beer.app", reported.label.sourcePackage)
        assertEquals(reported.label, labels.find(reported.label.id))
    }

    @Test
    fun `an entry without a label opens nothing`() {
        store.upsert(sender, grant, AdxOp.upsert("drinks.beer", "e2", 1, Instant.EPOCH, ZoneId.of("UTC")).value(1L).build())

        assertTrue(newLabels.isEmpty())
    }

    @Test
    fun `a label already looked at is not reported again`() {
        store.upsert(sender, grant, beer(1, sub = "beerSmall"))
        labels.ignore(newLabels.single().label.id)
        newLabels.clear()

        store.upsert(sender, grant, beer(2, sub = "beerSmall"))

        assertTrue(newLabels.isEmpty())
    }

    private fun described(vararg subs: AdxSub) = grant.copy(proposal = proposal.copy(subs = subs.toList()))

    private val small = AdxSub("beerSmall", "Small beer")
    private val smallComplete = AdxSub("beerSmall", "Small beer", mapOf("intake.volume_ml" to 330, "intake.abv_pct" to 5))

    @Test
    fun `a label the sender named is asked about with its name and kept as the sender's`() {
        store.upsert(sender, described(small), beer(1, sub = "beerSmall"))

        val label = newLabels.single().label
        assertEquals("Small beer", label.senderLabel)
        assertEquals("Small beer", label.shownName)
        assertEquals("drinks.beer", label.sourceStream)
        assertEquals(SubTypeLabelStatus.OPEN, label.status)
    }

    @Test
    fun `a label the sender described completely asks nothing`() {
        store.upsert(sender, described(smallComplete), beer(1, sub = "beerSmall"))

        assertTrue(newLabels.isEmpty())
        val label = labels.findByKey("tracker-1", "beerSmall")!!
        assertEquals(SubTypeLabelStatus.DESCRIBED, label.status)
        assertEquals(mapOf("intake.volume_ml" to 330.0, "intake.abv_pct" to 5.0), label.senderDefaults)
    }

    @Test
    fun `describe renames a label and settles an open one the sender now describes completely`() {
        store.upsert(sender, described(small), beer(1, sub = "beerSmall"))
        val id = newLabels.single().label.id

        store.describe(sender, described(smallComplete.copy(label = "Kleines Bier")))

        val label = labels.find(id)!!
        assertEquals("Kleines Bier", label.senderLabel)
        assertEquals(SubTypeLabelStatus.DESCRIBED, label.status)
        assertEquals(listOf(id), settled)
    }

    @Test
    fun `the user's answer survives what the sender says later`() {
        store.upsert(sender, described(small), beer(1, sub = "beerSmall"))
        val id = newLabels.single().label.id
        labels.specify(id, "Kleines", mapOf("intake.volume_ml" to 300.0))

        store.describe(sender, described(smallComplete))
        store.upsert(sender, described(smallComplete.copy(label = "Small")), beer(2, sub = "beerSmall"))

        val label = labels.find(id)!!
        assertEquals(SubTypeLabelStatus.SPECIFIED, label.status)
        assertEquals("Kleines", label.shownName)
        assertEquals(mapOf("intake.volume_ml" to 300.0), label.defaults)
        assertEquals("Small", label.senderLabel)
        assertTrue(settled.isEmpty())
    }

    @Test
    fun `a locked vault answers busy to describe`() {
        vault.isUnlocked = false

        assertBusy { store.describe(sender, described(small)) }
    }

    @Test
    fun `a delete hides the entry and keeps its rev`() {
        store.upsert(sender, grant, beer(1))
        store.delete(sender, grant, AdxOp.delete("drinks.beer", "e1", 2))

        assertTrue(recent().isEmpty())
        assertEquals(2L, store.currentRev(sender, grant, "e1"))
    }

    @Test
    fun `a delete for an unseen id leaves a tombstone a late upsert can't overwrite`() {
        // A sender that counts and undoes before its outbox runs sends only the delete.
        store.delete(sender, grant, AdxOp.delete("drinks.beer", "e1", 2))

        assertTrue(recent().isEmpty())
        assertEquals(2L, store.currentRev(sender, grant, "e1"))
        assertEquals(AdxStatus.STALE, AdxRevRule.decide(store.currentRev(sender, grant, "e1"), 1))
    }

    @Test
    fun `a locked vault answers busy and stores nothing`() {
        vault.isUnlocked = false

        assertBusy { store.currentRev(sender, grant, "e1") }
        assertBusy { store.upsert(sender, grant, beer(1)) }
        assertBusy { store.delete(sender, grant, AdxOp.delete("drinks.beer", "e1", 2)) }

        vault.isUnlocked = true
        assertNull(store.currentRev(sender, grant, "e1"))
        assertTrue(trackers.all().isEmpty())
    }

    private fun assertBusy(call: () -> Unit) {
        try {
            call()
        } catch (expected: AdxBusyException) {
            return
        }
        throw AssertionError("expected AdxBusyException")
    }
}

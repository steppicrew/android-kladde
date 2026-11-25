package de.steppicrew.kladde.data

import androidx.test.core.app.ApplicationProvider
import de.steppicrew.kladde.model.Ids
import de.steppicrew.kladde.model.SubTypeLabel
import de.steppicrew.kladde.model.SubTypeLabelStatus
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SubTypeLabelRepositoryTest {
    private val db = KladdeDatabase.inMemory(ApplicationProvider.getApplicationContext())
    private val labels = SubTypeLabelRepository(db)
    private val attrs = listOf("intake.volume_ml", "intake.abv_pct")

    @After
    fun close() = db.close()

    private fun record(
        key: String = "beerSmall",
        trackerId: String = "t1",
    ) = labels.recordIfNew(
        SubTypeLabel(Ids.newId(), trackerId, key, SubTypeLabelStatus.OPEN, Instant.EPOCH, attrKeys = attrs, sourcePackage = "beer.app"),
    )

    private fun open() = runBlocking { labels.observeOpen().first() }

    @Test
    fun `a new label is recorded once per tracker`() {
        val first = assertNotNull(record())
        assertNull(record())
        assertNotNull(record(trackerId = "t2"))

        assertEquals(SubTypeLabelStatus.OPEN, first.status)
        assertEquals(attrs, labels.find(first.id)!!.attrKeys)
        assertEquals(2, open().size)
    }

    @Test
    fun `a specified label keeps its name and values and is no longer open`() {
        val label = record()!!
        labels.specify(label.id, " Kleines Bier ", mapOf("intake.volume_ml" to 300.0, "intake.abv_pct" to 4.9))

        val stored = labels.find(label.id)!!
        assertEquals(SubTypeLabelStatus.SPECIFIED, stored.status)
        assertEquals("Kleines Bier", stored.displayName)
        assertEquals(mapOf("intake.volume_ml" to 300.0, "intake.abv_pct" to 4.9), stored.defaults)
        assertTrue(open().isEmpty())
    }

    @Test
    fun `an empty name specifies the label without one`() {
        val label = record()!!
        labels.specify(label.id, "  ", emptyMap())

        assertNull(labels.find(label.id)!!.displayName)
    }

    @Test
    fun `an ignored label is no longer open and is not recorded again`() {
        val label = record()!!
        labels.ignore(label.id)

        assertEquals(SubTypeLabelStatus.IGNORED, labels.find(label.id)!!.status)
        assertTrue(open().isEmpty())
        assertNull(record())
    }

    @Test
    fun `what the sending app says is stored apart from the user's answer`() {
        val label = record()!!
        labels.update(
            label.copy(sourceStream = "drinks.beer", senderLabel = "Small beer", senderDefaults = mapOf("intake.volume_ml" to 330.0)),
        )
        labels.specify(label.id, "Kleines", mapOf("intake.volume_ml" to 300.0))

        val stored = labels.findByKey("t1", "beerSmall")!!
        assertEquals("drinks.beer", stored.sourceStream)
        assertEquals("Small beer", stored.senderLabel)
        assertEquals(mapOf("intake.volume_ml" to 330.0), stored.senderDefaults)
        assertEquals("Kleines", stored.displayName)
        assertEquals(mapOf("intake.volume_ml" to 300.0), stored.defaults)
    }

    private fun <T> assertNotNull(value: T?): T {
        assertTrue("expected a value", value != null)
        return value!!
    }
}

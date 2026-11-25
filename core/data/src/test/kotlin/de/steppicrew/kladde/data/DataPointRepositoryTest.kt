package de.steppicrew.kladde.data

import androidx.test.core.app.ApplicationProvider
import de.steppicrew.kladde.model.DataPoint
import de.steppicrew.kladde.model.ExternalSource
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
class DataPointRepositoryTest {
    private val db = KladdeDatabase.inMemory(ApplicationProvider.getApplicationContext())
    private val points = DataPointRepository(db)

    @After
    fun close() = db.close()

    private fun beer(
        id: String,
        rev: Long,
        sub: String,
    ) = DataPoint(
        id = id,
        trackerId = "t1",
        at = Instant.parse("2026-11-25T18:00:00Z"),
        zone = ZoneId.of("Europe/Berlin"),
        value = 1.0,
        unit = "count",
        subType = sub,
        source = ExternalSource("beer.app", "drinks.beer", "e1", rev, "{}"),
    )

    private fun recent() = runBlocking { points.observeRecent(10).first() }

    @Test
    fun `an unseen entry has no rev`() {
        assertNull(points.externalRev("beer.app", "drinks.beer", "e1"))
    }

    @Test
    fun `an upsert replaces the entry with the same source key and keeps its id`() {
        points.upsertExternal(beer("p1", 1, "Pils"))
        points.upsertExternal(beer("p2", 2, "Weizen"))

        val stored = recent().single()
        assertEquals("p1", stored.id)
        assertEquals("Weizen", stored.subType)
        assertEquals(2L, points.externalRev("beer.app", "drinks.beer", "e1"))
    }

    @Test
    fun `a delete leaves a hidden tombstone with its rev`() {
        points.upsertExternal(beer("p1", 1, "Pils"))
        points.deleteExternal("p2", "t1", ExternalSource("beer.app", "drinks.beer", "e1", 3))

        assertTrue(recent().isEmpty())
        assertEquals(3L, points.externalRev("beer.app", "drinks.beer", "e1"))
    }

    @Test
    fun `a delete of an unseen entry still leaves a tombstone`() {
        points.deleteExternal("p9", "t1", ExternalSource("beer.app", "drinks.beer", "e1", 5))

        assertEquals(5L, points.externalRev("beer.app", "drinks.beer", "e1"))
    }
}

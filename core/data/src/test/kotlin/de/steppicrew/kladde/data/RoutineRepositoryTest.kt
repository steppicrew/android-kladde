package de.steppicrew.kladde.data

import androidx.test.core.app.ApplicationProvider
import de.steppicrew.kladde.model.BlockRule
import de.steppicrew.kladde.model.CompletionStatus
import de.steppicrew.kladde.model.Recurrence
import de.steppicrew.kladde.model.Routine
import de.steppicrew.kladde.model.RoutineCompletion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoutineRepositoryTest {
    private val db = KladdeDatabase.inMemory(ApplicationProvider.getApplicationContext())
    private val routines = RoutineRepository(db)
    private val day = LocalDate.of(2026, 11, 25)
    private val zone = ZoneId.of("Europe/Berlin")
    private val sport = Routine("r1", "Sport", Recurrence.Daily, day, LocalTime.of(18, 0), true, Instant.EPOCH)
    private val ruedersdorf =
        Routine("r2", "Rüdersdorf", Recurrence.Weekly(setOf(DayOfWeek.WEDNESDAY)), day, null, false, Instant.EPOCH)

    @After
    fun close() = db.close()

    private fun all() = runBlocking { routines.observeAll().first() }

    private fun completions(
        from: LocalDate = day,
        to: LocalDate = day.plusDays(6),
    ) = runBlocking { routines.observeCompletions(from, to).first() }

    @Test
    fun `routines are stored and read back`() {
        routines.save(sport)
        routines.save(ruedersdorf)

        assertEquals(listOf(ruedersdorf, sport), all())
    }

    @Test
    fun `saving again replaces the routine`() {
        routines.save(sport)
        routines.save(sport.copy(title = "Workout", recurrence = Recurrence.EveryNDays(2), time = null))

        assertEquals(listOf(sport.copy(title = "Workout", recurrence = Recurrence.EveryNDays(2), time = null)), all())
    }

    @Test
    fun `one answer per occurrence, which can be changed and taken back`() {
        routines.save(sport)
        routines.complete(RoutineCompletion(sport.id, day, CompletionStatus.DONE, Instant.EPOCH, zone))
        routines.complete(RoutineCompletion(sport.id, day, CompletionStatus.SKIPPED, Instant.EPOCH, zone))
        routines.complete(RoutineCompletion(sport.id, day.plusDays(1), CompletionStatus.DONE, Instant.EPOCH, zone))

        assertEquals(listOf(CompletionStatus.SKIPPED), completions(to = day).map { it.status })

        routines.reopen(sport.id, day)
        assertEquals(listOf(day.plusDays(1)), completions().map { it.date })
    }

    @Test
    fun `completions are read for the dates asked for only`() {
        routines.save(sport)
        for (offset in -1L..7) {
            routines.complete(RoutineCompletion(sport.id, day.plusDays(offset), CompletionStatus.DONE, Instant.EPOCH, zone))
        }

        assertEquals((0L..6).map { day.plusDays(it) }, completions().map { it.date }.sorted())
    }

    private fun rules() = runBlocking { routines.observeRules().first() }

    @Test
    fun `saving a routine replaces what it gives way to`() {
        routines.save(ruedersdorf)
        routines.save(sport, setOf(ruedersdorf.id, "r3"))
        routines.save(sport, setOf(ruedersdorf.id))

        assertEquals(listOf(BlockRule(sport.id, ruedersdorf.id)), rules())
    }

    @Test
    fun `deleting a blocker deletes the rules it is part of`() {
        routines.save(sport, setOf(ruedersdorf.id))
        routines.save(ruedersdorf, setOf("r3"))

        routines.delete(ruedersdorf.id)

        assertTrue(rules().isEmpty())
        assertEquals(listOf(sport), all())
    }

    @Test
    fun `deleting a routine deletes its answers`() {
        routines.save(sport)
        routines.complete(RoutineCompletion(sport.id, day, CompletionStatus.DONE, Instant.EPOCH, zone))

        routines.delete(sport.id)

        assertTrue(all().isEmpty())
        assertTrue(completions().isEmpty())
    }
}

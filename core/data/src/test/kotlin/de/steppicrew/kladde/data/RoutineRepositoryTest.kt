package de.steppicrew.kladde.data

import androidx.test.core.app.ApplicationProvider
import de.steppicrew.kladde.model.BlockRule
import de.steppicrew.kladde.model.CompletionStatus
import de.steppicrew.kladde.model.OccurrenceOverride
import de.steppicrew.kladde.model.Recurrence
import de.steppicrew.kladde.model.Routine
import de.steppicrew.kladde.model.RoutineCompletion
import de.steppicrew.kladde.model.RoutineExtra
import de.steppicrew.kladde.model.RoutinePause
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
    private val pauseRepository = RoutinePauseRepository(db)
    private val extraRepository = RoutineExtraRepository(db)
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
    fun `earlier schedules are stored and read back with the routine`() {
        val changed =
            ruedersdorf
                .withSchedule(Recurrence.Weekly(setOf(DayOfWeek.THURSDAY)), LocalTime.of(19, 0), day.plusWeeks(1))
                .withSchedule(Recurrence.Daily, null, day.plusWeeks(2))
        routines.save(changed)

        assertEquals(listOf(changed), all())

        routines.save(changed.withSchedule(Recurrence.Daily, null, day))
        assertTrue(all().single().earlier.isEmpty())
    }

    @Test
    fun `groups are stored, replaced on save and gone with the routine`() {
        routines.save(sport.copy(groups = setOf("Arbeit", "Gesundheit")))
        assertEquals(setOf("Arbeit", "Gesundheit"), all().single().groups)

        routines.save(sport.copy(groups = setOf("Privat")))
        assertEquals(setOf("Privat"), all().single().groups)

        routines.delete(sport.id)
        routines.save(sport.copy(id = "other"))
        assertEquals(emptySet<String>(), all().single().groups)
    }

    @Test
    fun `a routine's time zone is stored`() {
        routines.save(sport.copy(timeZone = ZoneId.of("Europe/Berlin")))

        assertEquals(ZoneId.of("Europe/Berlin"), all().single().timeZone)
    }

    @Test
    fun `going on during pauses is stored`() {
        routines.save(sport.copy(ignoresPauses = true))

        assertTrue(all().single().ignoresPauses)
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

    private fun overrides() = runBlocking { routines.observeOverrides().first() }

    @Test
    fun `an occurrence is moved, cancelled and brought back`() {
        routines.save(sport)
        routines.change(sport.id, day, day.plusDays(2))
        assertEquals(listOf(OccurrenceOverride(sport.id, day, day.plusDays(2))), overrides())

        routines.change(sport.id, day, null)
        assertEquals(listOf(OccurrenceOverride(sport.id, day, null)), overrides())

        routines.restore(sport.id, day)
        assertTrue(overrides().isEmpty())
    }

    @Test
    fun `a move keeps a new time, also on the same day`() {
        routines.change(sport.id, day, day.plusDays(1), LocalTime.of(9, 0))
        routines.change(sport.id, day.plusDays(2), day.plusDays(2), LocalTime.of(20, 0))

        assertEquals(
            listOf(
                OccurrenceOverride(sport.id, day, day.plusDays(1), LocalTime.of(9, 0)),
                OccurrenceOverride(sport.id, day.plusDays(2), day.plusDays(2), LocalTime.of(20, 0)),
            ),
            overrides().sortedBy { it.originalDate },
        )
    }

    @Test
    fun `when it was done is stored apart from when it was entered`() {
        val entered = Instant.parse("2026-11-25T19:00:00Z")
        val earlier = Instant.parse("2026-11-25T17:30:00Z")
        routines.complete(RoutineCompletion(sport.id, day, CompletionStatus.DONE, entered, zone, earlier))
        routines.complete(RoutineCompletion(sport.id, day.plusDays(1), CompletionStatus.DONE, entered, zone))

        assertEquals(listOf(earlier, entered), completions().sortedBy { it.date }.map { it.doneAt })
    }

    @Test
    fun `moving an occurrence back to its day takes the move back`() {
        routines.change(sport.id, day, day.plusDays(1))
        routines.change(sport.id, day, day)

        assertTrue(overrides().isEmpty())
    }

    @Test
    fun `deleting a routine deletes its single-day changes`() {
        routines.save(sport)
        routines.change(sport.id, day, null)

        routines.delete(sport.id)

        assertTrue(overrides().isEmpty())
    }

    private fun pauses() = runBlocking { pauseRepository.observeAll().first() }

    @Test
    fun `pauses are stored, changed and deleted`() {
        val vacation = RoutinePause("p1", day, day.plusDays(7), note = "Urlaub")
        val sportOnly = RoutinePause("p2", day.plusDays(10), null, setOf(sport.id, ruedersdorf.id))
        pauseRepository.save(sportOnly)
        pauseRepository.save(vacation)
        assertEquals(listOf(vacation, sportOnly), pauses())

        pauseRepository.save(vacation.copy(until = day.plusDays(9)))
        pauseRepository.delete(sportOnly.id)
        assertEquals(listOf(vacation.copy(until = day.plusDays(9))), pauses())
    }

    @Test
    fun `deleting a routine takes it out of pauses, and drops a pause left with none`() {
        routines.save(sport)
        routines.save(ruedersdorf)
        pauseRepository.save(RoutinePause("all", day, null))
        pauseRepository.save(RoutinePause("both", day, null, setOf(sport.id, ruedersdorf.id)))
        pauseRepository.save(RoutinePause("sport", day, null, setOf(sport.id)))

        routines.delete(sport.id)

        assertEquals(
            listOf(RoutinePause("all", day, null), RoutinePause("both", day, null, setOf(ruedersdorf.id))),
            pauses().sortedBy { it.id },
        )
    }

    private fun extras() = runBlocking { extraRepository.observeAll().first() }

    @Test
    fun `extras are added once, moved and removed`() {
        val friday = RoutineExtra(ruedersdorf.id, day.plusDays(2))
        extraRepository.add(friday)
        extraRepository.add(friday)
        assertEquals(listOf(friday), extras())

        extraRepository.move(friday, day.plusDays(3))
        assertEquals(listOf(friday.copy(date = day.plusDays(3))), extras())

        extraRepository.remove(friday.copy(date = day.plusDays(3)))
        assertTrue(extras().isEmpty())
    }

    @Test
    fun `deleting a routine deletes its extras`() {
        routines.save(ruedersdorf)
        extraRepository.add(RoutineExtra(ruedersdorf.id, day.plusDays(2)))

        routines.delete(ruedersdorf.id)

        assertTrue(extras().isEmpty())
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

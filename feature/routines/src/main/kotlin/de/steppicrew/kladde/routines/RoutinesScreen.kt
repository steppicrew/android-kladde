package de.steppicrew.kladde.routines

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.steppicrew.kladde.data.RoutineExtraRepository
import de.steppicrew.kladde.data.RoutinePauseRepository
import de.steppicrew.kladde.data.RoutineRepository
import de.steppicrew.kladde.model.BlockRule
import de.steppicrew.kladde.model.CompletionStatus
import de.steppicrew.kladde.model.Ids
import de.steppicrew.kladde.model.Occurrence
import de.steppicrew.kladde.model.OccurrenceOverride
import de.steppicrew.kladde.model.Routine
import de.steppicrew.kladde.model.RoutineCompletion
import de.steppicrew.kladde.model.RoutineExtra
import de.steppicrew.kladde.model.RoutinePause
import de.steppicrew.kladde.model.RoutinePlan
import de.steppicrew.kladde.model.wouldCycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** The routines tab: a list, a week or a month, and the form to add or change a routine. */
@Composable
fun RoutinesScreen(
    repository: RoutineRepository,
    pauseRepository: RoutinePauseRepository,
    extraRepository: RoutineExtraRepository,
    reminders: Reminders,
) {
    val today = rememberToday()
    // Null until loaded, so "no routines yet" doesn't flash up on opening.
    val loaded by repository.observeAll().collectAsStateWithLifecycle(null)
    val routines = loaded.orEmpty()
    val rules by repository.observeRules().collectAsStateWithLifecycle(emptyList())
    val overrides by repository.observeOverrides().collectAsStateWithLifecycle(emptyList())
    val pauses by pauseRepository.observeAll().collectAsStateWithLifecycle(emptyList())
    val extras by extraRepository.observeAll().collectAsStateWithLifecycle(emptyList())
    var menuFor by remember { mutableStateOf<Occurrence?>(null) }
    val scope = rememberCoroutineScope()

    fun io(block: () -> Unit) = scope.launch(Dispatchers.IO) { block() }

    // Null: the week; "" a new routine; else the id of the routine being changed.
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    // The event the form was opened at: a schedule change applies from its day on.
    var editingFrom by rememberSaveable { mutableStateOf<String?>(null) }
    val current = editing
    if (current != null) {
        EditRoutine(current, today, editingFrom?.let(LocalDate::parse) ?: today, routines, rules, repository, ::io) { editing = null }
        return
    }
    var showPauses by rememberSaveable { mutableStateOf(false) }
    if (showPauses) {
        PausesScreen(
            pauses,
            routines,
            today,
            onSave = { io { pauseRepository.save(it) } },
            onDelete = { io { pauseRepository.delete(it) } },
        ) {
            showPauses = false
        }
        return
    }
    RoutineViews(
        plan = { range -> rememberPlan(repository, range, routines, rules, overrides, pauses, extras) },
        today = today,
        hasRoutines = loaded?.isEmpty() != true,
        activePause = pauses.firstOrNull { it.isOn(today) },
        callbacks =
            RowCallbacks(
                onAction = { occurrence, action -> io { repository.act(occurrence, action) } },
                onDoneAt = { occurrence, time -> io { repository.doneAt(occurrence, time) } },
                onOpen = { menuFor = it },
            ),
        onPauses = { showPauses = true },
        onAdd = {
            editing = ""
            editingFrom = null
        },
        hints = { ReminderHints(reminders, routines.any(Routine::reminds)) },
    )
    menuFor?.let { occurrence ->
        val onEdit = {
            editing = occurrence.routine.id
            editingFrom = occurrence.originalDate.toString()
        }
        ChangeOccurrence(occurrence, repository, extraRepository, ::io, onEdit) { menuFor = null }
    }
}

/** The menu for one occurrence, its changes saved through [io] on the screen's scope. */
@Composable
private fun ChangeOccurrence(
    occurrence: Occurrence,
    repository: RoutineRepository,
    extraRepository: RoutineExtraRepository,
    io: (() -> Unit) -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val id = occurrence.routine.id
    val planned = occurrence.originalDate
    OccurrenceMenu(
        occurrence = occurrence,
        onMove = { newDate, newTime ->
            if (occurrence.isExtra) {
                io { extraRepository.move(RoutineExtra(id, planned), newDate) }
            } else {
                io { repository.change(id, planned, newDate, newTime) }
            }
        },
        onCancel = { io { repository.change(id, planned, null) } },
        onRestore = { io { repository.restore(id, planned) } },
        onAddExtra = { date -> io { extraRepository.add(RoutineExtra(id, date)) } },
        onRemoveExtra = { io { extraRepository.remove(RoutineExtra(id, planned)) } },
        onEdit = onEdit,
        onDismiss = onDismiss,
    )
}

/**
 * The plan for [range], with what was ticked off around it: answers are kept under the planned
 * day, which for a moved occurrence can lie outside it. Asked for by the year, so stepping
 * through months asks once.
 */
@Composable
private fun rememberPlan(
    repository: RoutineRepository,
    range: ClosedRange<LocalDate>,
    routines: List<Routine>,
    rules: List<BlockRule>,
    overrides: List<OccurrenceOverride>,
    pauses: List<RoutinePause>,
    extras: List<RoutineExtra>,
): RoutinePlan {
    val year = range.start.year
    val completions by remember(year) {
        repository.observeCompletions(
            range.start.minusYears(1).withDayOfYear(1),
            range.start.plusYears(2).with(TemporalAdjusters.lastDayOfYear()),
        )
    }.collectAsStateWithLifecycle(emptyList())
    return remember(routines, completions, rules, overrides, pauses, extras) {
        RoutinePlan(routines, completions, rules, overrides, pauses, extras)
    }
}

/** Has a reminder that can come: one set, and a time on some schedule. */
private fun Routine.reminds(): Boolean = remindBefore != null && schedules.any { it.time != null }

private const val MINUTE_MS = 60_000L

/** Today, moving on to the next day at midnight while the screen is open. */
@Composable
private fun rememberToday(): LocalDate {
    val today by produceState(LocalDate.now()) {
        while (true) {
            delay(MINUTE_MS)
            value = LocalDate.now()
        }
    }
    return today
}

/** Records done at [time] on the occurrence's day, e.g. earlier than "Erledigt" was tapped. */
private fun RoutineRepository.doneAt(
    occurrence: Occurrence,
    time: LocalTime,
) {
    val zone = ZoneId.systemDefault()
    val at =
        occurrence.date
            .atTime(time)
            .atZone(zone)
            .toInstant()
    complete(RoutineCompletion(occurrence.routine.id, occurrence.originalDate, CompletionStatus.DONE, Instant.now(), zone, at))
}

/** Does what a row's button says; answers and changes are kept under the day it was planned for. */
private fun RoutineRepository.act(
    occurrence: Occurrence,
    action: RowAction,
) {
    val id = occurrence.routine.id
    val planned = occurrence.originalDate
    when (action) {
        RowAction.DONE -> complete(RoutineCompletion(id, planned, CompletionStatus.DONE, Instant.now(), ZoneId.systemDefault()))
        RowAction.SKIP -> complete(RoutineCompletion(id, planned, CompletionStatus.SKIPPED, Instant.now(), ZoneId.systemDefault()))
        RowAction.UNDO -> reopen(id, planned)
        RowAction.CANCEL_ONCE -> change(id, planned, null)
        RowAction.RESTORE -> restore(id, planned)
    }
}

/** The form for the routine [id] ("" for a new one), saved with what it gives way to. */
@Composable
private fun EditRoutine(
    id: String,
    today: LocalDate,
    from: LocalDate,
    routines: List<Routine>,
    rules: List<BlockRule>,
    repository: RoutineRepository,
    // The screen's scope: this form's own would end, and could cancel the save, as it closes.
    io: (() -> Unit) -> Unit,
    onDone: () -> Unit,
) {
    val routine = routines.firstOrNull { it.id == id }
    RoutineEditor(
        initial = routine?.let { RoutineDraft.of(it, rules) } ?: RoutineDraft.new(today),
        isNew = routine == null,
        today = today,
        openedAt = from,
        // What it can give way to: the others, bar those that would close a loop.
        blockerChoices = routines.filter { it.id != id && !wouldCycle(id, it.id, rules) },
        groupChoices = routines.flatMap { it.groups }.distinct(),
        onSave = { draft ->
            draft.toRoutine(Ids.newId(), Instant.now(), routine)?.let { saved ->
                val blockers = draft.blockers.filterTo(HashSet()) { blocker -> routines.any { it.id == blocker } }
                io { repository.save(saved, blockers) }
            }
            onDone()
        },
        onDelete = {
            routine?.let { io { repository.delete(it.id) } }
            onDone()
        },
        onCancel = onDone,
    )
}

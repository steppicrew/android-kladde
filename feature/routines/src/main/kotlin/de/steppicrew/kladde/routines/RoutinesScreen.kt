package de.steppicrew.kladde.routines

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.steppicrew.kladde.data.RoutineRepository
import de.steppicrew.kladde.model.BlockRule
import de.steppicrew.kladde.model.CompletionStatus
import de.steppicrew.kladde.model.Ids
import de.steppicrew.kladde.model.Occurrence
import de.steppicrew.kladde.model.OccurrenceStatus
import de.steppicrew.kladde.model.Recurrence
import de.steppicrew.kladde.model.Routine
import de.steppicrew.kladde.model.RoutineCompletion
import de.steppicrew.kladde.model.RoutinePlan
import de.steppicrew.kladde.model.wouldCycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Yesterday (to tick off late), today and the days up to a week ahead. */
private const val DAYS_AHEAD = 6L

/** The routines tab: the week, and the form to add or change a routine. */
@Composable
fun RoutinesScreen(repository: RoutineRepository) {
    val today by produceState(LocalDate.now()) {
        // Moves on to the next day at midnight while the screen is open.
        while (true) {
            delay(MINUTE_MS)
            value = LocalDate.now()
        }
    }
    val from = today.minusDays(1)
    val to = today.plusDays(DAYS_AHEAD)
    val routines by repository.observeAll().collectAsStateWithLifecycle(emptyList())
    val rules by repository.observeRules().collectAsStateWithLifecycle(emptyList())
    // Answers are kept under the planned day, which for a moved occurrence can lie outside the week.
    val completions by remember(from, to) {
        repository.observeCompletions(from.minusYears(1), to.plusYears(1))
    }.collectAsStateWithLifecycle(emptyList())
    val overrides by repository.observeOverrides().collectAsStateWithLifecycle(emptyList())
    var menuFor by remember { mutableStateOf<Occurrence?>(null) }
    val scope = rememberCoroutineScope()

    fun io(block: () -> Unit) = scope.launch(Dispatchers.IO) { block() }

    // Null: the week; "" a new routine; else the id of the routine being changed.
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    val current = editing
    if (current != null) {
        EditRoutine(current, routines, rules, repository, ::io) { editing = null }
        return
    }
    val plan = RoutinePlan(routines, completions, rules, overrides)
    WeekView(
        days = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.map { it to plan.day(it, today) }.toList(),
        today = today,
        hasRoutines = routines.isNotEmpty(),
        onAnswer = { occurrence, status ->
            io {
                repository.complete(
                    RoutineCompletion(occurrence.routine.id, occurrence.originalDate, status, Instant.now(), ZoneId.systemDefault()),
                )
            }
        },
        onReopen = { occurrence -> io { repository.reopen(occurrence.routine.id, occurrence.originalDate) } },
        onOpen = { menuFor = it },
        onAdd = { editing = "" },
    )
    menuFor?.let { occurrence ->
        OccurrenceMenu(
            occurrence = occurrence,
            onMove = { newDate -> io { repository.change(occurrence.routine.id, occurrence.originalDate, newDate) } },
            onCancel = { io { repository.change(occurrence.routine.id, occurrence.originalDate, null) } },
            onRestore = { io { repository.restore(occurrence.routine.id, occurrence.originalDate) } },
            onEdit = { editing = occurrence.routine.id },
            onDismiss = { menuFor = null },
        )
    }
}

private const val MINUTE_MS = 60_000L

/** The form for the routine [id] ("" for a new one), saved with what it gives way to. */
@Composable
private fun EditRoutine(
    id: String,
    routines: List<Routine>,
    rules: List<BlockRule>,
    repository: RoutineRepository,
    // The screen's scope: this form's own would end, and could cancel the save, as it closes.
    io: (() -> Unit) -> Unit,
    onDone: () -> Unit,
) {
    val routine = routines.firstOrNull { it.id == id }
    RoutineEditor(
        initial = routine?.let { RoutineDraft.of(it, rules) } ?: RoutineDraft(),
        isNew = routine == null,
        // What it can give way to: the others, bar those that would close a loop.
        blockerChoices = routines.filter { it.id != id && !wouldCycle(id, it.id, rules) },
        onSave = { draft ->
            draft.toRoutine(Ids.newId(), LocalDate.now(), Instant.now(), routine)?.let { saved ->
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

@Composable
internal fun WeekView(
    days: List<Pair<LocalDate, List<Occurrence>>>,
    today: LocalDate,
    hasRoutines: Boolean,
    onAnswer: (Occurrence, CompletionStatus) -> Unit,
    onReopen: (Occurrence) -> Unit,
    onOpen: (Occurrence) -> Unit,
    onAdd: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        if (!hasRoutines) {
            Text(stringResource(R.string.routines_empty), Modifier.padding(24.dp))
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                for ((date, occurrences) in days) {
                    item(key = date.toString()) { DayHeader(date, today) }
                    if (occurrences.isEmpty()) {
                        item(key = "$date-none") {
                            Text(
                                stringResource(R.string.routines_day_free),
                                Modifier.padding(vertical = 4.dp),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    items(occurrences, key = { "$date-${it.routine.id}" }) { occurrence ->
                        OccurrenceRow(occurrence, onAnswer, onReopen, onOpen)
                    }
                    item(key = "$date-divider") { HorizontalDivider(Modifier.padding(top = 8.dp)) }
                }
                // Room under the last day, so the add button covers nothing.
                item(key = "end") { Box(Modifier.padding(40.dp)) }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onAdd,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Text(stringResource(R.string.routines_add)) }
    }
}

@Composable
private fun DayHeader(
    date: LocalDate,
    today: LocalDate,
) {
    val formatted = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    val weekday = date.dayOfWeek.displayName()
    val text =
        when (date) {
            today -> stringResource(R.string.routines_today, formatted)
            today.minusDays(1) -> stringResource(R.string.routines_yesterday, formatted)
            today.plusDays(1) -> stringResource(R.string.routines_tomorrow, formatted)
            else -> "$weekday, $formatted"
        }
    Text(
        text,
        Modifier.padding(top = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = if (date == today) FontWeight.Bold else null,
    )
}

@Composable
private fun OccurrenceRow(
    occurrence: Occurrence,
    onAnswer: (Occurrence, CompletionStatus) -> Unit,
    onReopen: (Occurrence) -> Unit,
    onOpen: (Occurrence) -> Unit,
) {
    val routine = occurrence.routine
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        val blocked = occurrence.status == OccurrenceStatus.BLOCKED || occurrence.status == OccurrenceStatus.CANCELLED
        Column(Modifier.weight(1f).clickable { onOpen(occurrence) }.padding(vertical = 6.dp)) {
            val time = routine.time?.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
            Text(
                listOfNotNull(time, routine.title).joinToString("  "),
                style = MaterialTheme.typography.bodyLarge,
                color = if (blocked) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                textDecoration = if (blocked) TextDecoration.LineThrough else null,
            )
            statusText(occurrence)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (occurrence.isMoved) {
                Text(
                    stringResource(
                        R.string.routines_moved_from,
                        occurrence.originalDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        when (occurrence.status) {
            OccurrenceStatus.OPEN, OccurrenceStatus.MISSED -> {
                TextButton(onClick = { onAnswer(occurrence, CompletionStatus.DONE) }) { Text(stringResource(R.string.routines_done)) }
                TextButton(onClick = { onAnswer(occurrence, CompletionStatus.SKIPPED) }) { Text(stringResource(R.string.routines_skip)) }
            }
            OccurrenceStatus.DONE, OccurrenceStatus.SKIPPED ->
                TextButton(onClick = { onReopen(occurrence) }) { Text(stringResource(R.string.routines_undo)) }
            OccurrenceStatus.HAPPENS, OccurrenceStatus.BLOCKED, OccurrenceStatus.CANCELLED -> Unit
        }
    }
}

@Composable
private fun statusText(occurrence: Occurrence): String? =
    when (occurrence.status) {
        OccurrenceStatus.BLOCKED -> stringResource(R.string.routines_status_blocked, occurrence.blockedBy.joinToString(", ") { it.title })
        OccurrenceStatus.DONE -> stringResource(R.string.routines_status_done)
        OccurrenceStatus.SKIPPED -> stringResource(R.string.routines_status_skipped)
        OccurrenceStatus.MISSED -> stringResource(R.string.routines_status_missed)
        OccurrenceStatus.CANCELLED -> stringResource(R.string.routines_status_cancelled)
        OccurrenceStatus.OPEN, OccurrenceStatus.HAPPENS -> null
    }

internal fun DayOfWeek.displayName(style: java.time.format.TextStyle = java.time.format.TextStyle.FULL): String =
    getDisplayName(style, java.util.Locale.getDefault())

@Suppress("MagicNumber") // Sample dates and times.
@Preview
@Composable
private fun WeekViewPreview() {
    val today = LocalDate.of(2026, 11, 25)
    val sport = Routine("s", "Sport", Recurrence.Daily, today.minusDays(1), LocalTime.of(18, 0), true, Instant.EPOCH)
    val ruedersdorf = Routine("r", "Rüdersdorf", Recurrence.Weekly(setOf(DayOfWeek.WEDNESDAY)), today, null, false, Instant.EPOCH)
    val plan = RoutinePlan(listOf(sport, ruedersdorf), emptyList(), listOf(BlockRule(sport.id, ruedersdorf.id)))
    MaterialTheme {
        WeekView(
            days = (-1L..2).map { today.plusDays(it) }.map { it to plan.day(it, today) },
            today = today,
            hasRoutines = true,
            onAnswer = { _, _ -> },
            onReopen = {},
            onOpen = {},
            onAdd = {},
        )
    }
}

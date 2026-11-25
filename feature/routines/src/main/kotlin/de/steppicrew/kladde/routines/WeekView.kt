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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.steppicrew.kladde.model.BlockRule
import de.steppicrew.kladde.model.CompletionStatus
import de.steppicrew.kladde.model.Occurrence
import de.steppicrew.kladde.model.OccurrenceStatus
import de.steppicrew.kladde.model.Recurrence
import de.steppicrew.kladde.model.Routine
import de.steppicrew.kladde.model.RoutinePause
import de.steppicrew.kladde.model.RoutinePlan
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun WeekView(
    days: List<Pair<LocalDate, List<Occurrence>>>,
    today: LocalDate,
    hasRoutines: Boolean,
    activePause: RoutinePause?,
    onPauses: () -> Unit,
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
                item(key = "pauses") { PauseBar(activePause, onPauses) }
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
                    // A routine can be on a day twice when one was moved there: the planned day tells them apart.
                    items(occurrences, key = { "$date-${it.routine.id}-${it.originalDate}" }) { occurrence ->
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

/** The way to the pauses, and which one is on today. */
@Composable
private fun PauseBar(
    active: RoutinePause?,
    onPauses: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            active?.let { pauseSummary(it) }.orEmpty(),
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = onPauses) { Text(stringResource(R.string.routines_pauses)) }
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
        val muted = blocked || occurrence.status == OccurrenceStatus.PAUSED
        Column(Modifier.weight(1f).clickable { onOpen(occurrence) }.padding(vertical = 6.dp)) {
            val time = routine.time?.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
            Text(
                listOfNotNull(time, routine.title).joinToString("  "),
                style = MaterialTheme.typography.bodyLarge,
                color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                textDecoration = if (blocked) TextDecoration.LineThrough else null,
            )
            statusText(occurrence)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (occurrence.isExtra) Text(stringResource(R.string.routines_extra), style = MaterialTheme.typography.bodySmall)
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
            // Done on vacation anyway counts; skipping a paused day says nothing new.
            OccurrenceStatus.PAUSED ->
                if (routine.checkable) {
                    TextButton(onClick = { onAnswer(occurrence, CompletionStatus.DONE) }) { Text(stringResource(R.string.routines_done)) }
                }
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
        OccurrenceStatus.PAUSED ->
            occurrence.pausedBy
                ?.note
                ?.takeIf { it.isNotBlank() }
                ?.let { stringResource(R.string.routines_status_paused_for, it) }
                ?: stringResource(R.string.routines_status_paused)
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
            activePause = null,
            onPauses = {},
            onAnswer = { _, _ -> },
            onReopen = {},
            onOpen = {},
            onAdd = {},
        )
    }
}

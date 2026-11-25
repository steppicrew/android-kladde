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
import androidx.compose.runtime.remember
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
import de.steppicrew.kladde.model.Occurrence
import de.steppicrew.kladde.model.OccurrenceStatus
import de.steppicrew.kladde.model.Recurrence
import de.steppicrew.kladde.model.Routine
import de.steppicrew.kladde.model.RoutinePause
import de.steppicrew.kladde.model.RoutinePlan
import de.steppicrew.kladde.model.ShownTime
import de.steppicrew.kladde.model.shownTime
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun WeekView(
    days: List<Pair<LocalDate, List<Occurrence>>>,
    today: LocalDate,
    hasRoutines: Boolean,
    activePause: RoutinePause?,
    onPauses: () -> Unit,
    onAction: (Occurrence, RowAction) -> Unit,
    /** Done at a time the user picked by holding "Erledigt". */
    onDoneAt: (Occurrence, LocalTime) -> Unit,
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
                        OccurrenceRow(occurrence, rowActions(occurrence, today), onAction, onDoneAt, onOpen)
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
    actions: List<RowAction>,
    onAction: (Occurrence, RowAction) -> Unit,
    onDoneAt: (Occurrence, LocalTime) -> Unit,
    onOpen: (Occurrence) -> Unit,
) {
    var pickDoneTime by remember { mutableStateOf(false) }
    val routine = occurrence.routine
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        val blocked = occurrence.status == OccurrenceStatus.BLOCKED || occurrence.status == OccurrenceStatus.CANCELLED
        val muted = blocked || occurrence.status == OccurrenceStatus.PAUSED
        Column(Modifier.weight(1f).clickable { onOpen(occurrence) }.padding(vertical = 6.dp)) {
            val shown = occurrence.shownTime(ZoneId.systemDefault())
            val time = shown?.let { shownTimeText(it) }
            Text(
                listOfNotNull(time, routine.title).joinToString("  "),
                style = MaterialTheme.typography.bodyLarge,
                color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                textDecoration = if (blocked) TextDecoration.LineThrough else null,
            )
            RowDetails(occurrence, shown)
        }
        for (action in actions) {
            if (action == RowAction.DONE) {
                // Tap: done now. Hold: done earlier, at a time to pick.
                LongPressTextButton(
                    stringResource(action.label),
                    onClick = { onAction(occurrence, action) },
                    onLongClick = { pickDoneTime = true },
                    onLongClickLabel = stringResource(R.string.routines_done_at_pick),
                )
            } else {
                TextButton(onClick = { onAction(occurrence, action) }) { Text(stringResource(action.label)) }
            }
        }
    }
    if (pickDoneTime) {
        TimeDialog(
            initial = LocalTime.now().withSecond(0).withNano(0),
            onPick = {
                onDoneAt(occurrence, it)
                pickDoneTime = false
            },
            onDismiss = { pickDoneTime = false },
        )
    }
}

/** What a row's buttons do. */
internal enum class RowAction(
    val label: Int,
) {
    DONE(R.string.routines_done),
    SKIP(R.string.routines_skip),

    /** Takes back done or skipped. */
    UNDO(R.string.routines_undo),

    /** "Auslassen" on a day still to come: the same as "Diesmal absagen". */
    CANCEL_ONCE(R.string.routines_skip),

    /** Brings a cancelled one back. */
    RESTORE(R.string.routines_undo),
}

/**
 * The buttons a row offers. Nothing still to come can be done yet: skipping it cancels it for
 * that day, so a blocker stops blocking. Done on vacation anyway counts; skipping a paused day
 * says nothing new. An extra day is removed from its menu, not skipped.
 */
internal fun rowActions(
    occurrence: Occurrence,
    today: LocalDate,
): List<RowAction> {
    val future = occurrence.date.isAfter(today)
    return when (occurrence.status) {
        OccurrenceStatus.OPEN ->
            when {
                !future -> listOf(RowAction.DONE, RowAction.SKIP)
                occurrence.isExtra -> emptyList()
                else -> listOf(RowAction.CANCEL_ONCE)
            }
        // A past day not ticked off counts as skipped, and can still be done later; so can one
        // skipped on purpose once its day is over.
        OccurrenceStatus.MISSED -> listOf(RowAction.DONE)
        OccurrenceStatus.SKIPPED -> if (occurrence.date.isBefore(today)) listOf(RowAction.DONE) else listOf(RowAction.UNDO)
        OccurrenceStatus.DONE -> listOf(RowAction.UNDO)
        OccurrenceStatus.PAUSED -> if (occurrence.routine.checkable && !future) listOf(RowAction.DONE) else emptyList()
        OccurrenceStatus.CANCELLED -> listOf(RowAction.RESTORE)
        OccurrenceStatus.HAPPENS, OccurrenceStatus.BLOCKED -> emptyList()
    }
}

/** The small lines under a row's title: status, the time in its own zone, extra, moved from. */
@Composable
private fun RowDetails(
    occurrence: Occurrence,
    shown: ShownTime?,
) {
    val small = MaterialTheme.typography.bodySmall
    statusText(occurrence)?.let { Text(it, style = small) }
    shown?.zone?.let { zone ->
        val there = occurrence.time?.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)).orEmpty()
        Text(stringResource(R.string.routines_zone_time, there, zone.id.replace('_', ' ')), style = small)
    }
    if (occurrence.isExtra) Text(stringResource(R.string.routines_extra), style = small)
    if (occurrence.isMoved) {
        val planned =
            listOfNotNull(
                occurrence.originalDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                occurrence.plannedTime?.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)),
            ).joinToString(", ")
        Text(stringResource(R.string.routines_moved_from, planned), style = small)
    }
}

@Composable
private fun statusText(occurrence: Occurrence): String? =
    when (occurrence.status) {
        OccurrenceStatus.BLOCKED -> stringResource(R.string.routines_status_blocked, occurrence.blockedBy.joinToString(", ") { it.title })
        OccurrenceStatus.DONE ->
            occurrence.doneAt?.let { doneAtText(it, occurrence.date) } ?: stringResource(R.string.routines_status_done)
        OccurrenceStatus.SKIPPED, OccurrenceStatus.MISSED -> stringResource(R.string.routines_status_skipped)
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
            onAction = { _, _ -> },
            onDoneAt = { _, _ -> },
            onOpen = {},
            onAdd = {},
        )
    }
}

package de.steppicrew.kladde.routines

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import de.steppicrew.kladde.model.Occurrence
import de.steppicrew.kladde.model.OccurrenceStatus
import de.steppicrew.kladde.model.ShownTime
import de.steppicrew.kladde.model.shownTime
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** What a row does when tapped or ticked off. */
internal class RowCallbacks(
    val onAction: (Occurrence, RowAction) -> Unit,
    /** Done at a time the user picked by holding "Erledigt". */
    val onDoneAt: (Occurrence, LocalTime) -> Unit,
    val onOpen: (Occurrence) -> Unit,
)

/** A day's heading: "Heute, 25.11.2026", or the weekday. */
@Composable
internal fun DayHeader(
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

/** One occurrence: its time and title, a tap opens its menu, and its buttons. */
@Composable
internal fun OccurrenceRow(
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
        // What doesn't take place stays readable but steps back.
        Column(
            Modifier
                .weight(1f)
                .clickable { onOpen(occurrence) }
                .padding(vertical = 6.dp)
                .alpha(if (muted) MUTED else 1f),
        ) {
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

/** How strongly a row shows that doesn't take place: blocked, cancelled or paused. */
private const val MUTED = 0.5f

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

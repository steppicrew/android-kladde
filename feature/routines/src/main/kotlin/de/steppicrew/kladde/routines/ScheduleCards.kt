package de.steppicrew.kladde.routines

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle

private const val DEFAULT_HOUR = 18

/**
 * "Zeitplan": the routine's schedules as cards by start date, each in effect until the next one
 * starts, all collapsed until one is tapped. Shown from the one in effect [today] on, the earlier
 * ones on request. The past stays as it was: a card that started before today can't be changed;
 * tapping the running one adds a change from today (or the opened event) on instead. A change
 * card can be deleted, then the card before it goes on; the earliest one is the base and stays.
 */
@Composable
internal fun ScheduleCards(
    draft: RoutineDraft,
    today: LocalDate,
    /** The day the form was opened at: a new card starts then, or today if that is past. */
    openedAt: LocalDate,
    onChange: (RoutineDraft) -> Unit,
) {
    // The open card, by its place in draft.schedules.
    var open by rememberSaveable { mutableStateOf<Int?>(null) }
    var showEarlier by rememberSaveable { mutableStateOf(false) }
    val shown = if (showEarlier) draft.byDate else draft.currentAndLater(today)
    val base = draft.byDate.first().index
    val running = draft.running(today)?.index
    val addChange = {
        open = draft.schedules.size
        onChange(draft.added(maxOf(today, openedAt)))
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.routines_schedules), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            val add = stringResource(R.string.routines_schedule_add)
            FilledTonalButton(onClick = addChange, modifier = Modifier.semantics { contentDescription = add }) { Text("+") }
        }
        val hidden = draft.schedules.size - shown.size
        if (hidden > 0) {
            TextButton(onClick = { showEarlier = true }) { Text(stringResource(R.string.routines_schedules_earlier, hidden)) }
        }
        for ((index, card) in shown) {
            val past = draft.isPast(card, today)
            ScheduleCard(
                card = card,
                isOpen = open == index && !past,
                sharesDay = draft.sharesDay(card),
                hint = if (index == running) R.string.routines_schedule_running else null,
                onToggle =
                    when {
                        index == running -> addChange
                        past -> null
                        else -> ({ open = if (open == index) null else index })
                    },
                onChange = { onChange(draft.replace(index, it)) },
                onDelete =
                    if (index == base || past) {
                        null
                    } else {
                        {
                            open = null
                            onChange(draft.remove(index))
                        }
                    },
                earliest = today,
            )
        }
        // Below the cards, so the "+" stays next to the list it adds to.
        Text(stringResource(R.string.routines_schedules_hint), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ScheduleCard(
    card: ScheduleDraft,
    isOpen: Boolean,
    sharesDay: Boolean,
    /** A line under the summary, e.g. what tapping the running card does. */
    hint: Int?,
    /** Null for a past card, which can't be opened. */
    onToggle: (() -> Unit)?,
    onChange: (ScheduleDraft) -> Unit,
    /** Null for the base or a past card, which can't be deleted. */
    onDelete: (() -> Unit)?,
    earliest: LocalDate,
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val tap = if (onToggle != null) Modifier.clickable(onClick = onToggle) else Modifier
            Row(Modifier.fillMaxWidth().then(tap), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        scheduleSummary(card),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (onToggle == null) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                    )
                    if (hint != null) Text(stringResource(hint), style = MaterialTheme.typography.bodySmall)
                }
                if (onDelete != null) TextButton(onClick = onDelete) { Text(stringResource(R.string.routines_delete)) }
            }
            if (sharesDay) {
                Text(
                    stringResource(R.string.routines_schedule_same_day),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (isOpen) CardFields(card, earliest, onChange)
        }
    }
}

/** An open card: its start day, its repeat and its time. */
@Composable
private fun CardFields(
    card: ScheduleDraft,
    /** The first day it can start: changes never reach into the past. */
    earliest: LocalDate,
    onChange: (ScheduleDraft) -> Unit,
) {
    // Which picker is open: "from", "time" or none.
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    val close = { picking = null }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.routines_schedule_from))
        OutlinedButton(onClick = { picking = FROM }) { Text(card.validFrom.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))) }
    }
    RepeatFields(card, onChange)
    Text(stringResource(R.string.routines_time), style = MaterialTheme.typography.titleSmall)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val time = card.time
        OutlinedButton(onClick = { picking = TIME }) {
            Text(time?.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)) ?: stringResource(R.string.routines_time_none))
        }
        if (time != null) TextButton(onClick = { onChange(card.copy(time = null)) }) { Text(stringResource(R.string.routines_time_clear)) }
    }
    when (picking) {
        FROM ->
            DateDialog(maxOf(card.validFrom, earliest), onPick = {
                onChange(card.copy(validFrom = it)).also { close() }
            }, onDismiss = close, earliest = earliest)
        TIME ->
            TimeDialog(card.time ?: LocalTime.of(DEFAULT_HOUR, 0), onPick = {
                onChange(card.copy(time = it)).also { close() }
            }, onDismiss = close)
    }
}

private const val FROM = "from"
private const val TIME = "time"

/** "ab 25.11.2026 · Mi, Do · 18:30" */
@Composable
private fun scheduleSummary(card: ScheduleDraft): String {
    val parts =
        listOfNotNull(
            stringResource(R.string.routines_schedule_since, card.validFrom.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))),
            repeatText(card),
            card.time?.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)),
        )
    return parts.joinToString(" · ")
}

@Composable
private fun repeatText(card: ScheduleDraft): String {
    val days = card.days.sorted().joinToString(", ") { it.getDisplayName(TextStyle.SHORT, java.util.Locale.getDefault()) }
    val last = stringResource(R.string.routines_last_day)
    val lastNth = stringResource(R.string.routines_nth_last)
    return when (card.repeat) {
        Repeat.WEEKDAYS -> days.ifEmpty { stringResource(R.string.routines_weekdays) }
        Repeat.EVERY_N_DAYS -> stringResource(R.string.routines_every_n, card.interval)
        Repeat.MONTHLY_WEEKDAYS ->
            card.nths.sortedBy { if (it < 0) Int.MAX_VALUE else it }.joinToString(", ") {
                if (it < 0) lastNth else "$it."
            } + " " + days
        Repeat.MONTHLY_DAYS ->
            stringResource(
                R.string.routines_on_days,
                card.monthDays.sortedBy { if (it < 0) Int.MAX_VALUE else it }.joinToString(", ") { if (it < 0) last else "$it." },
            )
        Repeat.YEARLY ->
            listOfNotNull(card.yearFrom, card.yearUntil).joinToString(" – ", transform = ::monthDayText).ifEmpty {
                stringResource(R.string.routines_yearly)
            }
        Repeat.EVERY_DAY, Repeat.NONE -> stringResource(card.repeat.label())
    }
}

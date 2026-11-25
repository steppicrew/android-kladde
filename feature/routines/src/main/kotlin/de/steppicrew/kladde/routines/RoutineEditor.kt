package de.steppicrew.kladde.routines

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.steppicrew.kladde.model.NthWeekday
import de.steppicrew.kladde.model.Recurrence
import de.steppicrew.kladde.model.Routine
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle

/** The form to add or change a routine; stateless apart from the draft it edits. */
@Composable
internal fun RoutineEditor(
    initial: RoutineDraft,
    isNew: Boolean,
    /** For an edit: the day a change to repeat or time applies from, unless the user picks another. */
    scheduleFrom: LocalDate,
    blockerChoices: List<Routine>,
    onSave: (RoutineDraft, from: LocalDate) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    var draft by rememberSaveable(stateSaver = DraftSaver) { mutableStateOf(initial) }
    var pickTime by rememberSaveable { mutableStateOf(false) }
    var from by rememberSaveable { mutableStateOf(scheduleFrom.toString()) }
    var pickFrom by rememberSaveable { mutableStateOf(false) }
    BackHandler(onBack = onCancel)
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(if (isNew) R.string.routines_new else R.string.routines_change),
                style = MaterialTheme.typography.headlineSmall,
            )
            OutlinedTextField(
                value = draft.title,
                onValueChange = { draft = draft.copy(title = it) },
                label = { Text(stringResource(R.string.routines_title)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (!isNew) AppliesFrom(LocalDate.parse(from)) { pickFrom = true }
            RepeatFields(draft) { draft = it }
            TimeAndTicking(draft, onPickTime = { pickTime = true }) { draft = it }
            BlockerFields(draft, blockerChoices) { draft = it }
        }
        Row(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { onSave(draft, LocalDate.parse(from)) },
                enabled = draft.isComplete,
            ) { Text(stringResource(R.string.routines_save)) }
            OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.routines_cancel)) }
            if (!isNew) TextButton(onClick = onDelete) { Text(stringResource(R.string.routines_delete)) }
        }
    }
    if (pickFrom) {
        DateDialog(
            initial = LocalDate.parse(from),
            onPick = {
                from = it.toString()
                pickFrom = false
            },
            onDismiss = { pickFrom = false },
        )
    }
    if (pickTime) {
        TimeDialog(
            initial = draft.time ?: LocalTime.of(DEFAULT_HOUR, 0),
            onPick = {
                draft = draft.copy(time = it)
                pickTime = false
            },
            onDismiss = { pickTime = false },
        )
    }
}

private const val DEFAULT_HOUR = 18
private const val INTERVAL_DIGITS = 3

private fun Repeat.label(): Int =
    when (this) {
        Repeat.EVERY_DAY -> R.string.routines_every_day
        Repeat.WEEKDAYS -> R.string.routines_weekdays
        Repeat.EVERY_N_DAYS -> R.string.routines_every_n_days
        Repeat.MONTHLY_WEEKDAYS -> R.string.routines_monthly_weekdays
        Repeat.MONTHLY_DAYS -> R.string.routines_monthly_days
    }

@Composable
private fun Choice(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onSelect, role = Role.RadioButton).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The row handles the click, so screen readers announce one control.
        RadioButton(selected = selected, onClick = null)
        Text(label, Modifier.padding(start = 8.dp))
    }
}

/** Keeps a draft across rotation and process death as plain values. */
private val DraftSaver =
    androidx.compose.runtime.saveable.listSaver<RoutineDraft, Any?>(
        save = {
            listOf(
                it.title,
                it.repeat.name,
                it.days.map(DayOfWeek::name),
                it.interval,
                it.time?.toString(),
                it.checkable,
                it.blockers.toList(),
                it.nths.toList(),
                it.monthDays.toList(),
            )
        },
        restore = { values ->
            @Suppress("UNCHECKED_CAST")
            RoutineDraft(
                title = values[0] as String,
                repeat = Repeat.valueOf(values[1] as String),
                days = (values[2] as List<String>).map(DayOfWeek::valueOf).toSet(),
                interval = values[3] as String,
                time = (values[4] as String?)?.let(LocalTime::parse),
                checkable = values[5] as Boolean,
                blockers = (values[6] as List<String>).toSet(),
                nths = (values[7] as List<Int>).toSet(),
                monthDays = (values[8] as List<Int>).toSet(),
            )
        },
    )

@Suppress("MagicNumber") // A sample date.
@Preview
@Composable
private fun RoutineEditorPreview() {
    MaterialTheme {
        RoutineEditor(
            initial = RoutineDraft("Rüdersdorf", Repeat.WEEKDAYS, days = setOf(DayOfWeek.WEDNESDAY), checkable = false),
            isNew = false,
            scheduleFrom = LocalDate.of(2026, 11, 25),
            blockerChoices = emptyList(),
            onSave = { _, _ -> },
            onDelete = {},
            onCancel = {},
        )
    }
}

/** How often: the choice, and the weekdays or the interval it needs. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RepeatFields(
    draft: RoutineDraft,
    onChange: (RoutineDraft) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.routines_repeat), style = MaterialTheme.typography.titleSmall)
        Column(Modifier.selectableGroup()) {
            for (repeat in Repeat.entries) {
                Choice(stringResource(repeat.label()), draft.repeat == repeat) { onChange(draft.copy(repeat = repeat)) }
            }
        }
        when (draft.repeat) {
            Repeat.WEEKDAYS -> WeekdayChips(draft.days) { onChange(draft.copy(days = it)) }
            Repeat.MONTHLY_WEEKDAYS -> {
                val last = stringResource(R.string.routines_nth_last)
                ToggleChips(NTHS, draft.nths, { if (it == NthWeekday.LAST) last else "$it." }) { onChange(draft.copy(nths = it)) }
                WeekdayChips(draft.days) { onChange(draft.copy(days = it)) }
            }
            Repeat.MONTHLY_DAYS -> {
                val last = stringResource(R.string.routines_last_day)
                ToggleChips(MONTH_DAYS, draft.monthDays, { if (it == Recurrence.MonthlyByDay.LAST_DAY) last else "$it." }) {
                    onChange(draft.copy(monthDays = it))
                }
                Text(stringResource(R.string.routines_month_days_hint), style = MaterialTheme.typography.bodySmall)
            }
            Repeat.EVERY_N_DAYS ->
                OutlinedTextField(
                    value = draft.interval,
                    onValueChange = { onChange(draft.copy(interval = it.filter(Char::isDigit).take(INTERVAL_DIGITS))) },
                    label = { Text(stringResource(R.string.routines_interval)) },
                    isError = draft.recurrence == null,
                    supportingText = { Text(stringResource(R.string.routines_interval_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            Repeat.EVERY_DAY -> Unit
        }
    }
}

@Composable
private fun TimeAndTicking(
    draft: RoutineDraft,
    onPickTime: () -> Unit,
    onChange: (RoutineDraft) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.routines_time), style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val time = draft.time
            OutlinedButton(onClick = onPickTime) {
                Text(time?.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)) ?: stringResource(R.string.routines_time_none))
            }
            if (time != null) {
                TextButton(onClick = { onChange(draft.copy(time = null)) }) { Text(stringResource(R.string.routines_time_clear)) }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .toggleable(draft.checkable, role = Role.Switch) { onChange(draft.copy(checkable = it)) }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.routines_checkable))
                Text(stringResource(R.string.routines_checkable_hint), style = MaterialTheme.typography.bodySmall)
            }
            // The row handles the toggle, so screen readers announce one control.
            Switch(checked = draft.checkable, onCheckedChange = null)
        }
    }
}

/** "Nicht an Tagen mit…": the routines this one gives way to. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BlockerFields(
    draft: RoutineDraft,
    choices: List<Routine>,
    onChange: (RoutineDraft) -> Unit,
) {
    if (choices.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.routines_blockers), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.routines_blockers_hint), style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (choice in choices) {
                val selected = choice.id in draft.blockers
                FilterChip(
                    selected = selected,
                    onClick = { onChange(draft.copy(blockers = if (selected) draft.blockers - choice.id else draft.blockers + choice.id)) },
                    label = { Text(choice.title) },
                )
            }
        }
    }
}

private val NTHS = (1..NthWeekday.MAX_N).toList() + NthWeekday.LAST
private val MONTH_DAYS = (1..Recurrence.MonthlyByDay.MAX_DAY).toList() + Recurrence.MonthlyByDay.LAST_DAY

@Composable
private fun WeekdayChips(
    selected: Set<DayOfWeek>,
    onChange: (Set<DayOfWeek>) -> Unit,
) = ToggleChips(DayOfWeek.entries, selected, { it.displayName(TextStyle.SHORT) }, onChange)

/** Chips that each add or remove their option from [selected]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ToggleChips(
    options: List<T>,
    selected: Set<T>,
    label: (T) -> String,
    onChange: (Set<T>) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (option in options) {
            FilterChip(
                selected = option in selected,
                onClick = { onChange(if (option in selected) selected - option else selected + option) },
                label = { Text(label(option)) },
            )
        }
    }
}

/** "Gilt ab": the day a change to repeat or time applies from; the days before keep theirs. */
@Composable
private fun AppliesFrom(
    from: LocalDate,
    onPick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.routines_applies_from), style = MaterialTheme.typography.titleSmall)
            OutlinedButton(onClick = onPick) { Text(from.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))) }
        }
        Text(stringResource(R.string.routines_applies_from_hint), style = MaterialTheme.typography.bodySmall)
    }
}

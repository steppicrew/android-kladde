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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
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
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle

/** The form to add or change a routine; stateless apart from the draft it edits. */
@Composable
internal fun RoutineEditor(
    initial: RoutineDraft,
    isNew: Boolean,
    onSave: (RoutineDraft) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    var draft by rememberSaveable(stateSaver = DraftSaver) { mutableStateOf(initial) }
    var pickTime by rememberSaveable { mutableStateOf(false) }
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
            RepeatFields(draft) { draft = it }
            TimeAndTicking(draft, onPickTime = { pickTime = true }) { draft = it }
        }
        Row(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { onSave(draft) }, enabled = draft.isComplete) { Text(stringResource(R.string.routines_save)) }
            OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.routines_cancel)) }
            if (!isNew) TextButton(onClick = onDelete) { Text(stringResource(R.string.routines_delete)) }
        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(
    initial: LocalTime,
    onPick: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(initial.hour, initial.minute)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onPick(LocalTime.of(state.hour, state.minute)) },
            ) { Text(stringResource(R.string.routines_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.routines_cancel)) } },
        text = { TimePicker(state) },
    )
}

/** Keeps a draft across rotation and process death as plain values. */
private val DraftSaver =
    androidx.compose.runtime.saveable.listSaver<RoutineDraft, Any?>(
        save = { listOf(it.title, it.repeat.name, it.days.map(DayOfWeek::name), it.interval, it.time?.toString(), it.checkable) },
        restore = { values ->
            @Suppress("UNCHECKED_CAST")
            RoutineDraft(
                title = values[0] as String,
                repeat = Repeat.valueOf(values[1] as String),
                days = (values[2] as List<String>).map(DayOfWeek::valueOf).toSet(),
                interval = values[3] as String,
                time = (values[4] as String?)?.let(LocalTime::parse),
                checkable = values[5] as Boolean,
            )
        },
    )

@Preview
@Composable
private fun RoutineEditorPreview() {
    MaterialTheme {
        RoutineEditor(
            initial = RoutineDraft("Rüdersdorf", Repeat.WEEKDAYS, days = setOf(DayOfWeek.WEDNESDAY), checkable = false),
            isNew = false,
            onSave = {},
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
            Repeat.WEEKDAYS ->
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (day in DayOfWeek.entries) {
                        FilterChip(
                            selected = day in draft.days,
                            onClick = { onChange(draft.copy(days = if (day in draft.days) draft.days - day else draft.days + day)) },
                            label = { Text(day.displayName(TextStyle.SHORT)) },
                        )
                    }
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

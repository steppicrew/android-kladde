package de.steppicrew.kladde.routines

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import de.steppicrew.kladde.model.Occurrence
import de.steppicrew.kladde.model.OccurrenceStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * What can be done with one occurrence: move or cancel just this one, bring it back, add the
 * routine on another day too, or change the routine. An extra one is moved or removed instead.
 */
@Composable
internal fun OccurrenceMenu(
    occurrence: Occurrence,
    onMove: (LocalDate) -> Unit,
    onCancel: () -> Unit,
    onRestore: () -> Unit,
    onAddExtra: (LocalDate) -> Unit,
    onRemoveExtra: () -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Which date is being picked for: a move, an extra day, or none.
    var picking by remember { mutableStateOf<((LocalDate) -> Unit)?>(null) }
    picking?.let { action ->
        DateDialog(
            initial = occurrence.date,
            onPick = {
                action(it)
                onDismiss()
            },
            onDismiss = onDismiss,
        )
        return
    }
    val changed = occurrence.isMoved || occurrence.status == OccurrenceStatus.CANCELLED

    fun then(action: () -> Unit): () -> Unit =
        {
            action()
            onDismiss()
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${occurrence.routine.title}, ${occurrence.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))}") },
        text = {
            Column {
                TextButton(onClick = { picking = onMove }) { Text(stringResource(R.string.routines_move)) }
                if (occurrence.isExtra) {
                    TextButton(onClick = then(onRemoveExtra)) { Text(stringResource(R.string.routines_remove_extra)) }
                } else {
                    if (occurrence.status != OccurrenceStatus.CANCELLED) {
                        TextButton(onClick = then(onCancel)) { Text(stringResource(R.string.routines_cancel_once)) }
                    }
                    if (changed) TextButton(onClick = then(onRestore)) { Text(stringResource(R.string.routines_restore)) }
                }
                TextButton(onClick = { picking = onAddExtra }) { Text(stringResource(R.string.routines_add_extra)) }
                TextButton(onClick = then(onEdit)) { Text(stringResource(R.string.routines_edit)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.routines_close)) } },
    )
}

/** Picks a day. The date picker works in UTC midnight millis; the dates here are local days. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateDialog(
    initial: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } },
                enabled = state.selectedDateMillis != null,
            ) { Text(stringResource(R.string.routines_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.routines_cancel)) } },
    ) { DatePicker(state) }
}

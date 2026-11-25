package de.steppicrew.kladde.routines

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import de.steppicrew.kladde.model.Occurrence
import de.steppicrew.kladde.model.OccurrenceStatus
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * What can be done with one occurrence: move or cancel just this one, bring it back, add the
 * routine on another day too, or change the routine. An extra one is moved or removed instead.
 */
@Composable
internal fun OccurrenceMenu(
    occurrence: Occurrence,
    /** The new day and, for a timed routine, a new time (null: its usual one). */
    onMove: (LocalDate, LocalTime?) -> Unit,
    onCancel: () -> Unit,
    onRestore: () -> Unit,
    onAddExtra: (LocalDate) -> Unit,
    onRemoveExtra: () -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Which date is being picked for: a move, an extra day, or none.
    var picking by remember { mutableStateOf<((LocalDate) -> Unit)?>(null) }
    // A timed routine's move asks for the time after the day.
    var movingTo by remember { mutableStateOf<LocalDate?>(null) }
    val time = occurrence.time
    movingTo?.let { date ->
        TimeDialog(
            initial = time ?: LocalTime.NOON,
            onPick = {
                onMove(date, it.takeIf { picked -> picked != occurrence.plannedTime })
                onDismiss()
            },
            onDismiss = onDismiss,
        )
        return
    }
    picking?.let { action ->
        DateDialog(initial = occurrence.date, onPick = action, onDismiss = onDismiss)
        return
    }
    val move = { date: LocalDate ->
        if (time != null && !occurrence.isExtra) {
            movingTo = date
        } else {
            onMove(date, null)
            onDismiss()
        }
    }
    val addExtra = { date: LocalDate ->
        onAddExtra(date)
        onDismiss()
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
                TextButton(onClick = { picking = move }) { Text(stringResource(R.string.routines_move)) }
                if (occurrence.isExtra) {
                    TextButton(onClick = then(onRemoveExtra)) { Text(stringResource(R.string.routines_remove_extra)) }
                } else {
                    if (occurrence.status != OccurrenceStatus.CANCELLED) {
                        TextButton(onClick = then(onCancel)) { Text(stringResource(R.string.routines_cancel_once)) }
                    }
                    if (changed) TextButton(onClick = then(onRestore)) { Text(stringResource(R.string.routines_restore)) }
                }
                TextButton(onClick = { picking = addExtra }) { Text(stringResource(R.string.routines_add_extra)) }
                TextButton(onClick = then(onEdit)) { Text(stringResource(R.string.routines_edit)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.routines_close)) } },
    )
}

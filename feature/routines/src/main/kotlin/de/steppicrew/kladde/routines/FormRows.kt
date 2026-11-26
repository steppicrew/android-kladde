package de.steppicrew.kladde.routines

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** A radio choice; the whole row is the target. */
@Composable
internal fun Choice(
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

/** A labelled switch; the whole row toggles, so screen readers announce one control. */
@Composable
internal fun SwitchRow(
    label: Int,
    hint: Int,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = onChange).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(label))
            Text(stringResource(hint), style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** Looks like a text button, but also reacts to a long press, which [onLongClickLabel] names for screen readers. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LongPressTextButton(
    label: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onLongClickLabel: String,
) {
    Text(
        label,
        Modifier
            .clip(MaterialTheme.shapes.extraLarge)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = onLongClickLabel, role = Role.Button)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelLarge,
    )
}

/**
 * Save, Cancel and, for something that exists already, Delete, below a form. With large text the
 * buttons move to a second line rather than breaking their labels.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SaveBar(
    canSave: Boolean,
    canDelete: Boolean,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    /** Asked before deleting, e.g. "„Sport“ löschen?". */
    deleteQuestion: String = "",
) {
    FlowRow(
        Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(onClick = onSave, enabled = canSave) { OneLine(R.string.routines_save) }
        OutlinedButton(onClick = onCancel) { OneLine(R.string.routines_cancel) }
        if (canDelete) DeleteButton(stringResource(R.string.routines_delete), deleteQuestion, onDelete)
    }
}

@Composable
private fun OneLine(label: Int) {
    Text(stringResource(label), maxLines = 1, softWrap = false)
}

/** A button that deletes only after [question] is answered with yes: nothing is deleted by a single tap. */
@Composable
internal fun DeleteButton(
    label: String,
    question: String,
    onDelete: () -> Unit,
) {
    var asking by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { asking = true }) { Text(label, maxLines = 1, softWrap = false) }
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            text = { Text(question) },
            confirmButton = {
                TextButton(onClick = {
                    asking = false
                    onDelete()
                }) { Text(label) }
            },
            dismissButton = { TextButton(onClick = { asking = false }) { Text(stringResource(R.string.routines_cancel)) } },
        )
    }
}

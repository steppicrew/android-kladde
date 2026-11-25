package de.steppicrew.kladde.routines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import de.steppicrew.kladde.model.Routine

/** The groups a routine is in: the ones other routines have as chips, and a field for a new one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GroupFields(
    draft: RoutineDraft,
    choices: List<String>,
    onChange: (RoutineDraft) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.routines_groups), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.routines_groups_hint), style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (group in (choices + draft.groups).distinct().sortedBy { it.lowercase() }) {
                val selected = group in draft.groups
                FilterChip(
                    selected = selected,
                    onClick = { onChange(draft.copy(groups = if (selected) draft.groups - group else draft.groups + group)) },
                    label = { Text(group) },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.routines_group_new)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = {
                    onChange(draft.copy(groups = draft.groups + name.trim()))
                    name = ""
                },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.routines_group_add)) }
        }
    }
}

/** For a pause of only some routines: a chip per group that picks all its routines, then one per routine. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PausedRoutines(
    draft: PauseDraft,
    routines: List<Routine>,
    onChange: (PauseDraft) -> Unit,
) {
    val groups = routines.flatMap { it.groups }.distinct().sortedBy { it.lowercase() }
    if (groups.isNotEmpty()) {
        Text(stringResource(R.string.routines_pause_groups), style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (group in groups) {
                FilterChip(
                    selected = draft.hasGroup(group, routines),
                    onClick = { onChange(draft.toggleGroup(group, routines)) },
                    label = { Text(group) },
                )
            }
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (routine in routines) {
            val selected = routine.id in draft.routineIds
            FilterChip(
                selected = selected,
                onClick = {
                    onChange(
                        draft.copy(routineIds = if (selected) draft.routineIds - routine.id else draft.routineIds + routine.id),
                    )
                },
                label = { Text(routine.title) },
            )
        }
    }
}

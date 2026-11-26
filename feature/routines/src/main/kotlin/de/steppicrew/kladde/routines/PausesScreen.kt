package de.steppicrew.kladde.routines

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import de.steppicrew.kladde.model.Ids
import de.steppicrew.kladde.model.Routine
import de.steppicrew.kladde.model.RoutinePause
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The pauses (vacations and the like): a list, and the form to add or change one. */
@Composable
internal fun PausesScreen(
    pauses: List<RoutinePause>,
    routines: List<Routine>,
    today: LocalDate,
    onSave: (RoutinePause) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit,
) {
    // Null: the list; "" a new pause; else the id of the pause being changed.
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    val current = editing
    if (current != null) {
        val pause = pauses.firstOrNull { it.id == current }
        PauseEditor(
            initial = pause?.let(PauseDraft::of) ?: PauseDraft.new(today),
            isNew = pause == null,
            routines = routines,
            onSave = { draft ->
                draft.toPause(pause?.id ?: Ids.newId())?.let(onSave)
                editing = null
            },
            onDelete = {
                pause?.let { onDelete(it.id) }
                editing = null
            },
            onCancel = { editing = null },
        )
        return
    }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.routines_pauses), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.routines_pauses_hint), style = MaterialTheme.typography.bodyMedium)
        }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 24.dp)) {
            items(pauses, key = { it.id }) { pause ->
                Column(Modifier.fillMaxWidth().clickable { editing = pause.id }.padding(vertical = 12.dp)) {
                    Text(pauseSummary(pause), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (pause.isForAll) {
                            val goOn = routines.filter { it.ignoresPauses }
                            if (goOn.isEmpty()) {
                                stringResource(R.string.routines_pause_all)
                            } else {
                                stringResource(R.string.routines_pause_all_but, goOn.joinToString(", ") { it.title })
                            }
                        } else {
                            (pause.groups.sorted() + routines.filter { it.id in pause.routineIds }.map { it.title }).joinToString(", ")
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                HorizontalDivider()
            }
        }
        Row(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { editing = "" }) { Text(stringResource(R.string.routines_pause_add)) }
            OutlinedButton(onClick = onBack) { Text(stringResource(R.string.routines_back)) }
        }
    }
}

/** "Urlaub: 1.12.2026 – 8.12.2026", or "ab 1.12.2026" for an open one. */
@Composable
internal fun pauseSummary(pause: RoutinePause): String {
    val format = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    val dates =
        pause.until?.let { stringResource(R.string.routines_pause_range, pause.from.format(format), it.format(format)) }
            ?: stringResource(R.string.routines_pause_open_range, pause.from.format(format))
    val name = pause.note.ifBlank { stringResource(R.string.routines_pause_default_name) }
    return "$name: $dates"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PauseEditor(
    initial: PauseDraft,
    isNew: Boolean,
    routines: List<Routine>,
    onSave: (PauseDraft) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    var draft by rememberSaveable(stateSaver = PauseDraftSaver) { mutableStateOf(initial) }
    // Which date is being picked: "from", "until" or none.
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(onBack = onCancel)
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(if (isNew) R.string.routines_pause_new else R.string.routines_pause_change),
                style = MaterialTheme.typography.headlineSmall,
            )
            OutlinedTextField(
                value = draft.note,
                onValueChange = { draft = draft.copy(note = it) },
                label = { Text(stringResource(R.string.routines_pause_note)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            DatesFields(draft, onPick = { picking = it }) { draft = it }
            Column(Modifier.selectableGroup()) {
                Choice(stringResource(R.string.routines_pause_all), draft.all) { draft = draft.copy(all = true) }
                Choice(stringResource(R.string.routines_pause_some), !draft.all) { draft = draft.copy(all = false) }
            }
            if (!draft.all) PausedRoutines(draft, routines) { draft = it }
        }
        SaveBar(
            draft.isComplete,
            !isNew,
            onSave = { onSave(draft) },
            onCancel = onCancel,
            onDelete = onDelete,
            deleteQuestion = stringResource(R.string.routines_pause_delete_question),
        )
    }
    picking?.let { which ->
        DateDialog(
            initial = if (which == UNTIL) draft.until ?: draft.from else draft.from,
            onPick = {
                draft = if (which == UNTIL) draft.copy(until = it) else draft.copy(from = it)
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
}

private const val FROM = "from"
private const val UNTIL = "until"

/** From, until, and whether it is open until the user ends it. */
@Composable
private fun DatesFields(
    draft: PauseDraft,
    onPick: (String) -> Unit,
    onChange: (PauseDraft) -> Unit,
) {
    val format = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.routines_pause_from))
        OutlinedButton(onClick = { onPick(FROM) }) { Text(draft.from.format(format)) }
    }
    val until = draft.until
    if (until != null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.routines_pause_until))
            OutlinedButton(onClick = { onPick(UNTIL) }) { Text(until.format(format)) }
        }
        if (until.isBefore(draft.from)) {
            Text(
                stringResource(R.string.routines_pause_until_before_from),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(until == null, role = Role.Switch) { open ->
                onChange(draft.copy(until = if (open) null else draft.from.plusWeeks(1)))
            }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.routines_pause_open), Modifier.weight(1f))
        // The row handles the toggle, so screen readers announce one control.
        Switch(checked = until == null, onCheckedChange = null)
    }
}

/** Keeps a draft across rotation and process death as plain values. */
private val PauseDraftSaver =
    listSaver<PauseDraft, Any?>(
        save = { listOf(it.note, it.from.toString(), it.until?.toString(), it.all, it.routineIds.toList(), it.groups.toList()) },
        restore = { values ->
            @Suppress("UNCHECKED_CAST")
            PauseDraft(
                note = values[0] as String,
                from = LocalDate.parse(values[1] as String),
                until = (values[2] as String?)?.let(LocalDate::parse),
                all = values[3] as Boolean,
                routineIds = (values[4] as List<String>).toSet(),
                groups = (values[5] as List<String>).toSet(),
            )
        },
    )

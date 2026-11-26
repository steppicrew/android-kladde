package de.steppicrew.kladde.routines

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.steppicrew.kladde.model.Routine
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.MonthDay
import java.time.ZoneId

/** The form to add or change a routine; stateless apart from the draft it edits. */
@Composable
internal fun RoutineEditor(
    initial: RoutineDraft,
    isNew: Boolean,
    today: LocalDate,
    /** The day of the event the form was opened at: an added schedule card starts then. */
    openedAt: LocalDate,
    blockerChoices: List<Routine>,
    /** The groups other routines have, to pick from. */
    groupChoices: List<String>,
    onSave: (RoutineDraft) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    var draft by rememberSaveable(stateSaver = DraftSaver) { mutableStateOf(initial) }
    var pickZone by rememberSaveable { mutableStateOf(false) }
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
            ScheduleCards(draft, today, openedAt) { draft = it }
            ZoneAndTicking(draft, onPickZone = { pickZone = true }) { draft = it }
            GroupFields(draft, groupChoices) { draft = it }
            BlockerFields(draft, blockerChoices) { draft = it }
        }
        SaveBar(
            draft.isComplete,
            !isNew,
            onSave = { onSave(draft) },
            onCancel = onCancel,
            onDelete = onDelete,
            deleteQuestion = stringResource(R.string.routines_delete_question, initial.title),
        )
    }
    if (pickZone) {
        ZoneDialog(onPick = { draft = draft.copy(timeZone = it.id).also { pickZone = false } }, onDismiss = { pickZone = false })
    }
}

/** What holds for every schedule: the time zone and reminder (when some card has a time), ticking off, pauses. */
@Composable
private fun ZoneAndTicking(
    draft: RoutineDraft,
    onPickZone: () -> Unit,
    onChange: (RoutineDraft) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (draft.hasTime) {
            val zone = draft.timeZone
            SwitchRow(R.string.routines_zone_bound, R.string.routines_zone_bound_hint, zone != null) { bound ->
                onChange(draft.copy(timeZone = if (bound) ZoneId.systemDefault().id else null))
            }
            if (zone != null) OutlinedButton(onClick = onPickZone) { Text(zone) }
            ReminderFields(draft.remindBefore) { onChange(draft.copy(remindBefore = it)) }
        }
        SwitchRow(R.string.routines_checkable, R.string.routines_checkable_hint, draft.checkable) { onChange(draft.copy(checkable = it)) }
        SwitchRow(R.string.routines_ignores_pauses, R.string.routines_ignores_pauses_hint, draft.ignoresPauses) {
            onChange(draft.copy(ignoresPauses = it))
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

/** A schedule card as plain values, for [DraftSaver]. */
private fun ScheduleDraft.values(): List<Any?> =
    listOf(
        validFrom.toString(),
        repeat.name,
        days.map(DayOfWeek::name),
        interval,
        nths.toList(),
        monthDays.toList(),
        yearFrom?.toString(),
        yearUntil?.toString(),
        time?.toString(),
    )

@Suppress("UNCHECKED_CAST")
private fun scheduleOf(values: List<Any?>) =
    ScheduleDraft(
        validFrom = LocalDate.parse(values[0] as String),
        repeat = Repeat.valueOf(values[1] as String),
        days = (values[2] as List<String>).map(DayOfWeek::valueOf).toSet(),
        interval = values[3] as String,
        nths = (values[4] as List<Int>).toSet(),
        monthDays = (values[5] as List<Int>).toSet(),
        yearFrom = (values[6] as String?)?.let(MonthDay::parse),
        yearUntil = (values[7] as String?)?.let(MonthDay::parse),
        time = (values[8] as String?)?.let(LocalTime::parse),
    )

/** Keeps a draft across rotation and process death as plain values. */
private val DraftSaver =
    listSaver<RoutineDraft, Any?>(
        save = {
            listOf(
                it.title,
                it.schedules.map { card -> card.values() },
                it.checkable,
                it.ignoresPauses,
                it.timeZone,
                it.blockers.toList(),
                it.groups.toList(),
                it.remindBefore,
            )
        },
        restore = { values ->
            @Suppress("UNCHECKED_CAST")
            RoutineDraft(
                title = values[0] as String,
                schedules = (values[1] as List<List<Any?>>).map(::scheduleOf),
                checkable = values[2] as Boolean,
                ignoresPauses = values[3] as Boolean,
                timeZone = values[4] as String?,
                blockers = (values[5] as List<String>).toSet(),
                groups = (values[6] as List<String>).toSet(),
                remindBefore = values[7] as Int?,
            )
        },
    )

@Suppress("MagicNumber") // Sample dates.
@Preview
@Composable
private fun RoutineEditorPreview() {
    val day = LocalDate.of(2026, 11, 25)
    MaterialTheme {
        RoutineEditor(
            initial =
                RoutineDraft(
                    "Rüdersdorf",
                    listOf(
                        ScheduleDraft(day.minusWeeks(4), Repeat.WEEKDAYS, days = setOf(DayOfWeek.WEDNESDAY)),
                        ScheduleDraft(day.plusWeeks(2), Repeat.NONE),
                    ),
                    checkable = false,
                ),
            isNew = false,
            today = day,
            openedAt = day,
            blockerChoices = emptyList(),
            groupChoices = listOf("Arbeit", "Privat"),
            onSave = {},
            onDelete = {},
            onCancel = {},
        )
    }
}

package de.steppicrew.kladde.routines

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Picks a day. The date picker works in UTC midnight millis; the dates here are local days. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateDialog(
    initial: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    /** The first day that can be picked; null for any. */
    earliest: LocalDate? = null,
) {
    val first = earliest?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
    val state =
        rememberDatePickerState(
            initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates =
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long) = first == null || utcTimeMillis >= first

                    override fun isSelectableYear(year: Int) = earliest == null || year >= earliest.year
                },
        )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeDialog(
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

/** The zones a routine can be bound to: region-based IDs such as Europe/Berlin, sorted. */
private val ZONES: List<String> by lazy { ZoneId.getAvailableZoneIds().filter { '/' in it && !it.startsWith("Etc/") }.sorted() }

/** Picks a time zone by searching its ID, e.g. "berlin". */
@Composable
internal fun ZoneDialog(
    onPick: (ZoneId) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val matches = remember(query) { ZONES.filter { it.replace('_', ' ').contains(query.trim(), ignoreCase = true) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.routines_cancel)) } },
        title = { Text(stringResource(R.string.routines_zone_pick)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.routines_zone_search)) },
                    singleLine = true,
                )
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(matches, key = { it }) { id ->
                        Text(
                            id.replace('_', ' '),
                            Modifier.fillMaxWidth().clickable { onPick(ZoneId.of(id)) }.padding(vertical = 10.dp),
                        )
                    }
                }
            }
        },
    )
}

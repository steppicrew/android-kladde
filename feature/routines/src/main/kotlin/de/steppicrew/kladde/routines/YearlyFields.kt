package de.steppicrew.kladde.routines

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.MonthDay
import java.time.format.DateTimeFormatter
import java.util.Locale

/** "Jährlich": the first and last day of the span, picked as dates whose year doesn't matter. */
@Composable
internal fun YearlyFields(
    draft: ScheduleDraft,
    onChange: (ScheduleDraft) -> Unit,
) {
    // Which end is being picked: "from", "until" or none.
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SpanEnd(R.string.routines_yearly_from, draft.yearFrom) { picking = FROM }
        SpanEnd(R.string.routines_yearly_until, draft.yearUntil) { picking = UNTIL }
        if (draft.yearFrom == LEAP_DAY || draft.yearUntil == LEAP_DAY) {
            Text(
                stringResource(R.string.routines_yearly_leap_day),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    picking?.let { which ->
        val today = LocalDate.now()
        val current = if (which == FROM) draft.yearFrom else draft.yearUntil ?: draft.yearFrom
        DateDialog(
            initial = current?.atYear(today.year) ?: today,
            onPick = { date ->
                val day = MonthDay.from(date)
                // A first pick of the start sets the end too, for a one-day span or a start to move on from.
                onChange(if (which == FROM) draft.copy(yearFrom = day, yearUntil = draft.yearUntil ?: day) else draft.copy(yearUntil = day))
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
}

@Composable
private fun SpanEnd(
    label: Int,
    day: MonthDay?,
    onPick: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(label))
        OutlinedButton(onClick = onPick) { Text(day?.let(::monthDayText) ?: stringResource(R.string.routines_yearly_pick)) }
    }
}

/** "1. Dezember" / "December 1", as the device's language writes it. */
internal fun monthDayText(day: MonthDay): String {
    val locale = Locale.getDefault()
    return day.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "dMMMM"), locale))
}

private const val FROM = "from"
private const val UNTIL = "until"

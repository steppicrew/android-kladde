package de.steppicrew.kladde.routines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.steppicrew.kladde.model.NthWeekday
import de.steppicrew.kladde.model.Recurrence
import java.time.DayOfWeek
import java.time.format.TextStyle

private const val INTERVAL_DIGITS = 3

internal fun Repeat.label(): Int =
    when (this) {
        Repeat.EVERY_DAY -> R.string.routines_every_day
        Repeat.WEEKDAYS -> R.string.routines_weekdays
        Repeat.EVERY_N_DAYS -> R.string.routines_every_n_days
        Repeat.MONTHLY_WEEKDAYS -> R.string.routines_monthly_weekdays
        Repeat.MONTHLY_DAYS -> R.string.routines_monthly_days
        Repeat.YEARLY -> R.string.routines_yearly
        Repeat.NONE -> R.string.routines_not_planned
    }

/** How often, on one schedule card: the choice, and the days or the interval it needs. */
@Composable
internal fun RepeatFields(
    card: ScheduleDraft,
    onChange: (ScheduleDraft) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.selectableGroup()) {
            for (repeat in Repeat.entries) {
                Choice(stringResource(repeat.label()), card.repeat == repeat) { onChange(card.copy(repeat = repeat)) }
            }
        }
        when (card.repeat) {
            Repeat.WEEKDAYS -> WeekdayChips(card.days) { onChange(card.copy(days = it)) }
            Repeat.MONTHLY_WEEKDAYS -> {
                val last = stringResource(R.string.routines_nth_last)
                ToggleChips(NTHS, card.nths, { if (it == NthWeekday.LAST) last else "$it." }) { onChange(card.copy(nths = it)) }
                WeekdayChips(card.days) { onChange(card.copy(days = it)) }
            }
            Repeat.MONTHLY_DAYS -> {
                val last = stringResource(R.string.routines_last_day)
                ToggleChips(MONTH_DAYS, card.monthDays, { if (it == Recurrence.MonthlyByDay.LAST_DAY) last else "$it." }) {
                    onChange(card.copy(monthDays = it))
                }
                Text(stringResource(R.string.routines_month_days_hint), style = MaterialTheme.typography.bodySmall)
            }
            Repeat.EVERY_N_DAYS ->
                OutlinedTextField(
                    value = card.interval,
                    onValueChange = { onChange(card.copy(interval = it.filter(Char::isDigit).take(INTERVAL_DIGITS))) },
                    label = { Text(stringResource(R.string.routines_interval)) },
                    isError = card.recurrence == null,
                    supportingText = { Text(stringResource(R.string.routines_interval_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            Repeat.YEARLY -> YearlyFields(card, onChange)
            Repeat.NONE -> Text(stringResource(R.string.routines_not_planned_hint), style = MaterialTheme.typography.bodySmall)
            Repeat.EVERY_DAY -> Unit
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

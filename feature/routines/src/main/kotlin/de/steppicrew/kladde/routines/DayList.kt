package de.steppicrew.kladde.routines

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.steppicrew.kladde.model.Occurrence
import java.time.LocalDate

/**
 * Days one under the other, each with its occurrences: the week shows every day, the list
 * ([skipFree]) only days with something on them, and offers more days at its end ([onMore]).
 */
@Composable
internal fun DayList(
    days: List<Pair<LocalDate, List<Occurrence>>>,
    today: LocalDate,
    callbacks: RowCallbacks,
    modifier: Modifier = Modifier,
    skipFree: Boolean = false,
    onMore: (() -> Unit)? = null,
) {
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        for ((date, occurrences) in days) {
            if (skipFree && occurrences.isEmpty()) continue
            day(date, occurrences, today, callbacks)
        }
        if (skipFree && days.all { it.second.isEmpty() }) {
            item(key = "nothing") { Text(stringResource(R.string.routines_nothing_planned), Modifier.padding(vertical = 16.dp)) }
        }
        if (onMore != null) {
            item(key = "more") { TextButton(onClick = onMore) { Text(stringResource(R.string.routines_show_more)) } }
        }
        // Room under the last day, so the add button covers nothing.
        item(key = "end") { Box(Modifier.padding(40.dp)) }
    }
}

/** One day: its heading, its occurrences or "frei", and a line under it. */
internal fun LazyListScope.day(
    date: LocalDate,
    occurrences: List<Occurrence>,
    today: LocalDate,
    callbacks: RowCallbacks,
) {
    item(key = date.toString()) { DayHeader(date, today) }
    if (occurrences.isEmpty()) {
        item(key = "$date-none") {
            Text(
                stringResource(R.string.routines_day_free),
                Modifier.padding(vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    // A routine can be on a day twice when one was moved there: the planned day tells them apart.
    items(occurrences, key = { "$date-${it.routine.id}-${it.originalDate}" }) { occurrence ->
        OccurrenceRow(occurrence, rowActions(occurrence, today), callbacks.onAction, callbacks.onDoneAt, callbacks.onOpen)
    }
    item(key = "$date-divider") { HorizontalDivider(Modifier.padding(top = 8.dp)) }
}

package de.steppicrew.kladde.routines

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.steppicrew.kladde.model.RoutinePause
import de.steppicrew.kladde.model.RoutinePlan
import java.time.LocalDate
import java.time.YearMonth

/** The list shows this many weeks at first, and this many more on "Mehr anzeigen". */
private const val LIST_WEEKS = 4

/**
 * The days [view] shows around [anchor]: the week from the day before (to tick off late) and
 * the next six, the list's weeks from it, or the weeks of its month.
 */
internal fun shownDays(
    view: RoutineView,
    anchor: LocalDate,
    listWeeks: Int = LIST_WEEKS,
): ClosedRange<LocalDate> =
    when (view) {
        RoutineView.WEEK -> anchor.minusDays(1)..anchor.plusDays(DAYS_AHEAD)
        RoutineView.LIST -> anchor..anchor.plusWeeks(listWeeks.toLong()).minusDays(1)
        RoutineView.MONTH -> monthGrid(YearMonth.from(anchor))
    }

/** A week or a month earlier ([direction] -1) or later (+1); the list has no steps. */
internal fun step(
    view: RoutineView,
    anchor: LocalDate,
    direction: Int,
): LocalDate =
    when (view) {
        RoutineView.WEEK -> anchor.plusWeeks(direction.toLong())
        RoutineView.MONTH -> anchor.plusMonths(direction.toLong())
        RoutineView.LIST -> anchor
    }

/** Yesterday (to tick off late), today and the days up to a week ahead. */
private const val DAYS_AHEAD = 6L

/**
 * The routines as a list, a week or a month (the user's choice, kept), from today or any day
 * picked; with [header] (hints, the pauses) above and the add button over them.
 */
@Composable
internal fun RoutineViews(
    /** The plan for the days shown. */
    plan: @Composable (ClosedRange<LocalDate>) -> RoutinePlan,
    today: LocalDate,
    hasRoutines: Boolean,
    activePause: RoutinePause?,
    callbacks: RowCallbacks,
    onPauses: () -> Unit,
    onAdd: () -> Unit,
    hints: @Composable () -> Unit = {},
) {
    var view by rememberStoredView()
    // Null follows today, also past midnight.
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    val anchor = picked?.let(LocalDate::parse) ?: today
    var listWeeks by rememberSaveable { mutableIntStateOf(LIST_WEEKS) }
    var pickDate by rememberSaveable { mutableStateOf(false) }
    val range = shownDays(view, anchor, listWeeks)
    val shown = plan(range)
    val days =
        remember(shown, range, today) {
            generateSequence(range.start) { it.plusDays(1) }.takeWhile { it <= range.endInclusive }.associateWith { shown.day(it, today) }
        }
    val dayOf = { date: LocalDate -> days[date] ?: shown.day(date, today) }

    fun go(date: LocalDate) {
        picked = date.takeIf { it != today }?.toString()
    }
    Box(Modifier.fillMaxSize()) {
        Column {
            ViewBar(
                view,
                onView = { view = it },
                title = viewTitle(view, range, anchor),
                onStep = { direction: Int -> go(step(view, anchor, direction)) }.takeIf { view != RoutineView.LIST },
                onToday = { go(today) }.takeIf { picked != null },
                onPickDate = { pickDate = true },
            )
            Column(Modifier.padding(horizontal = 16.dp)) {
                hints()
                PauseBar(activePause, onPauses)
            }
            when {
                !hasRoutines -> Text(stringResource(R.string.routines_empty), Modifier.padding(24.dp))
                view == RoutineView.MONTH -> MonthView(anchor, today, dayOf, ::go, { go(step(view, anchor, it)) }, callbacks)
                else ->
                    DayList(
                        days.toList(),
                        today,
                        callbacks,
                        skipFree = view == RoutineView.LIST,
                        onMore = { listWeeks += LIST_WEEKS }.takeIf { view == RoutineView.LIST },
                    )
            }
        }
        ExtendedFloatingActionButton(
            onClick = onAdd,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Text(stringResource(R.string.routines_add)) }
    }
    if (pickDate) {
        DateDialog(anchor, onPick = {
            go(it)
            pickDate = false
        }, onDismiss = { pickDate = false })
    }
}

/** The way to the pauses, and which one is on today. */
@Composable
private fun PauseBar(
    active: RoutinePause?,
    onPauses: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            active?.let { pauseSummary(it) }.orEmpty(),
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = onPauses) { Text(stringResource(R.string.routines_pauses)) }
    }
}

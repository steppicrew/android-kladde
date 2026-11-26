package de.steppicrew.kladde.routines

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.steppicrew.kladde.model.Occurrence
import de.steppicrew.kladde.model.OccurrenceStatus
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.abs

private const val DAYS_IN_WEEK = 7L

/** At most this many dots in a day; more show as "+". */
private const val MAX_DOTS = 4

/** How strongly the past, the months around and paused days show. */
private const val SOFT = 0.5f

/** How far a swipe has to go to turn the month. */
private const val SWIPE_PX = 120f

/** The weeks [month] spans, from the locale's first day of the week: the days the grid shows. */
internal fun monthGrid(
    month: YearMonth,
    locale: Locale = Locale.getDefault(),
): ClosedRange<LocalDate> {
    val first = WeekFields.of(locale).firstDayOfWeek
    val start = month.atDay(1).with(TemporalAdjusters.previousOrSame(first))
    val end = month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(first.plus(DAYS_IN_WEEK - 1)))
    return start..end
}

/**
 * A month as weeks under thin lines, a dot per occurrence that takes place (filled: done or an
 * event, ringed: open, a red ring: skipped or missed; muted once past), today ringed, paused days
 * shaded, days of the months around faint and without dots; below it the [selected] day's occurrences,
 * to tick off as in the week. Swiping turns the month ([onStep]).
 */
@Composable
internal fun MonthView(
    selected: LocalDate,
    today: LocalDate,
    dayOf: (LocalDate) -> List<Occurrence>,
    onSelect: (LocalDate) -> Unit,
    onStep: (Int) -> Unit,
    callbacks: RowCallbacks,
) {
    val month = YearMonth.from(selected)
    val grid = monthGrid(month)
    val weeks = generateSequence(grid.start) { it.plusWeeks(1) }.takeWhile { !it.isAfter(grid.endInclusive) }.toList()
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item(key = "grid") {
            Column(Modifier.swipeToStep(onStep)) {
                WeekdayHeads(grid.start)
                for (week in weeks) {
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth()) {
                        for (offset in 0 until DAYS_IN_WEEK) {
                            val date = week.plusDays(offset)
                            DayCell(date, month, date == selected, today, dayOf(date), Modifier.weight(1f)) { onSelect(date) }
                        }
                    }
                }
            }
        }
        day(selected, dayOf(selected), today, callbacks)
        item(key = "end") { Box(Modifier.padding(40.dp)) }
    }
}

private fun Modifier.swipeToStep(onStep: (Int) -> Unit): Modifier =
    pointerInput(onStep) {
        var dragged = 0f
        detectHorizontalDragGestures(
            onDragStart = { dragged = 0f },
            onDragEnd = { if (abs(dragged) > SWIPE_PX) onStep(if (dragged < 0) 1 else -1) },
        ) { _, amount -> dragged += amount }
    }

@Composable
private fun WeekdayHeads(first: LocalDate) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        for (offset in 0 until DAYS_IN_WEEK) {
            Text(
                first.plusDays(offset).dayOfWeek.displayName(TextStyle.SHORT),
                Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    month: YearMonth,
    isSelected: Boolean,
    today: LocalDate,
    occurrences: List<Occurrence>,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val isToday = date == today
    val colors = MaterialTheme.colorScheme
    val inMonth = YearMonth.from(date) == month
    val paused = inMonth && occurrences.any { it.status == OccurrenceStatus.PAUSED }
    val label = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))
    Column(
        modifier
            .height(40.dp)
            .background(if (paused) colors.surfaceVariant.copy(alpha = SOFT) else Color.Transparent, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = label
                selected = isSelected
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .padding(top = 2.dp)
                .size(22.dp)
                .background(if (isSelected) colors.primary else Color.Transparent, CircleShape)
                .then(if (isToday && !isSelected) Modifier.border(1.dp, colors.primary, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isToday) FontWeight.Bold else null,
                color =
                    when {
                        isSelected -> colors.onPrimary
                        !inMonth -> colors.outline.copy(alpha = SOFT)
                        isToday -> colors.primary
                        else -> Color.Unspecified
                    },
            )
        }
        if (inMonth) Dots(occurrences, past = date < today)
    }
}

/** A dot per occurrence that takes place, up to [MAX_DOTS]. */
@Composable
private fun Dots(
    occurrences: List<Occurrence>,
    past: Boolean,
) {
    val shown = occurrences.filter { it.status in DOTTED }
    Row(
        Modifier.padding(top = 2.dp).alpha(if (past) SOFT else 1f),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (occurrence in shown.take(MAX_DOTS)) Dot(occurrence.status)
        if (shown.size > MAX_DOTS) Text("+", style = MaterialTheme.typography.labelSmall, lineHeight = 6.sp)
    }
}

private val DOTTED =
    setOf(OccurrenceStatus.OPEN, OccurrenceStatus.DONE, OccurrenceStatus.HAPPENS, OccurrenceStatus.SKIPPED, OccurrenceStatus.MISSED)

@Composable
private fun Dot(status: OccurrenceStatus) {
    val colors = MaterialTheme.colorScheme
    val dot = Modifier.size(6.dp)
    val shape = CircleShape
    when (status) {
        OccurrenceStatus.DONE -> Box(dot.background(colors.primary, shape))
        OccurrenceStatus.HAPPENS -> Box(dot.background(colors.tertiary, shape))
        OccurrenceStatus.SKIPPED, OccurrenceStatus.MISSED -> Box(dot.border(1.dp, colors.error, shape))
        else -> Box(dot.border(1.dp, colors.primary, shape))
    }
}

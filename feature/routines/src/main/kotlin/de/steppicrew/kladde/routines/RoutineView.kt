package de.steppicrew.kladde.routines

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** How the routines are shown; the user's choice is kept. */
internal enum class RoutineView(
    val label: Int,
) {
    LIST(R.string.routines_view_list),
    WEEK(R.string.routines_view_week),
    MONTH(R.string.routines_view_month),
}

/**
 * The choice of view, then the stretch of time shown: earlier and later (not in the list, which
 * just goes on), its name, which picks any date when tapped, and the way back to today.
 */
@Composable
internal fun ViewBar(
    view: RoutineView,
    onView: (RoutineView) -> Unit,
    title: String,
    /** Earlier (-1) or later (+1). */
    onStep: ((Int) -> Unit)?,
    /** Null while today is in view. */
    onToday: (() -> Unit)?,
    onPickDate: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp).padding(top = 8.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            for (choice in RoutineView.entries) {
                SegmentedButton(
                    selected = choice == view,
                    onClick = { onView(choice) },
                    shape = SegmentedButtonDefaults.itemShape(choice.ordinal, RoutineView.entries.size),
                ) { Text(stringResource(choice.label), maxLines = 1) }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (onStep != null) {
                IconButton(onClick = { onStep(-1) }) {
                    Icon(painterResource(R.drawable.ic_kladde_chevron_left), stringResource(R.string.routines_earlier))
                }
            }
            TextButton(onClick = onPickDate, Modifier.weight(1f)) { Text(title, maxLines = 1) }
            if (onStep != null) {
                IconButton(onClick = { onStep(1) }) {
                    Icon(painterResource(R.drawable.ic_kladde_chevron_right), stringResource(R.string.routines_later))
                }
            }
            TextButton(onClick = { onToday?.invoke() }, enabled = onToday != null) { Text(stringResource(R.string.routines_go_today)) }
        }
    }
}

/** "25. Nov. – 2. Dez.", "November 2026" or "Ab 25.11.2026". */
@Composable
internal fun viewTitle(
    view: RoutineView,
    shown: ClosedRange<LocalDate>,
    anchor: LocalDate,
): String {
    val locale = LocalConfiguration.current.locales[0]
    val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    return when (view) {
        RoutineView.LIST -> stringResource(R.string.routines_list_from, anchor.format(date))
        RoutineView.WEEK -> {
            val short = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "dMMM"), locale)
            "${shown.start.format(short)} – ${shown.endInclusive.format(short)}"
        }
        RoutineView.MONTH -> monthText(YearMonth.from(anchor), locale)
    }
}

internal fun monthText(
    month: YearMonth,
    locale: Locale,
): String = month.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMMyyyy"), locale))

/** The view chosen last time, kept on the device; the week until one was chosen. */
@Composable
internal fun rememberStoredView(): MutableState<RoutineView> {
    val prefs = LocalContext.current.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val state =
        remember {
            mutableStateOf(RoutineView.entries.firstOrNull { it.name == prefs.getString(KEY_VIEW, null) } ?: RoutineView.WEEK)
        }
    LaunchedEffect(state.value) { prefs.edit().putString(KEY_VIEW, state.value.name).apply() }
    return state
}

private const val PREFS = "routines"
private const val KEY_VIEW = "view"

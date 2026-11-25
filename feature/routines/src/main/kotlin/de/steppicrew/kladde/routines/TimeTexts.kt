package de.steppicrew.kladde.routines

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.steppicrew.kladde.model.ShownTime
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** A time as the row shows it: "02:00 (+1 Tag)" when a zone-bound time falls on another day here. */
@Composable
internal fun shownTimeText(shown: ShownTime): String {
    val time = shown.time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    return if (shown.dayShift == 0L) time else stringResource(R.string.routines_day_shift, time, "%+d".format(shown.dayShift))
}

/** "Erledigt um 18:42", with the date when it was done on another day than its own. */
@Composable
internal fun doneAtText(
    at: Instant,
    day: LocalDate,
): String {
    val local = at.atZone(ZoneId.systemDefault())
    val time = local.toLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    return if (local.toLocalDate() == day) {
        stringResource(R.string.routines_done_at, time)
    } else {
        stringResource(
            R.string.routines_done_at_day,
            local.toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
            time,
        )
    }
}

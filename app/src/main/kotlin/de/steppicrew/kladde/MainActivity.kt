package de.steppicrew.kladde

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.steppicrew.kladde.adx.LabelsActivity
import de.steppicrew.kladde.model.DataPoint
import de.steppicrew.kladde.model.SubTypeLabel
import de.steppicrew.kladde.model.Tracker
import de.steppicrew.kladde.routines.Reminders
import de.steppicrew.kladde.routines.RoutinesScreen
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import de.steppicrew.kladde.adx.R as AdxR

/** The main screen's tabs. */
enum class MainTab {
    ENTRIES,
    ROUTINES,
}

class MainActivity : ComponentActivity() {
    /** A tab the debug backdoor asked for; the user's own choice is kept otherwise. */
    private val requestedTab = mutableStateOf<MainTab?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as KladdeApp
        requestedTab.value = DebugCommand.handle(app, intent) ?: shownByReminder(intent)
        setContent {
            val unlocked by app.vault.state.collectAsStateWithLifecycle()
            MaterialTheme {
                // Edge-to-edge is forced from SDK 35; keep content clear of the system bars.
                Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
                    if (!unlocked) {
                        Message(stringResource(R.string.locked_state))
                    } else {
                        val trackers by app.trackerRepository.observeAll().collectAsStateWithLifecycle(emptyList())
                        val points by app.dataPointRepository.observeRecent(RECENT).collectAsStateWithLifecycle(emptyList())
                        val labels by app.subTypeLabelRepository.observeAll().collectAsStateWithLifecycle(emptyList())
                        val open by app.subTypeLabelRepository.observeOpen().collectAsStateWithLifecycle(emptyList())
                        var tab by rememberSaveable { mutableStateOf(MainTab.ENTRIES) }
                        requestedTab.value?.let {
                            tab = it
                            requestedTab.value = null
                        }
                        Column {
                            PrimaryTabRow(selectedTabIndex = tab.ordinal) {
                                Tab(
                                    tab == MainTab.ENTRIES,
                                    onClick = { tab = MainTab.ENTRIES },
                                    text = { Text(stringResource(R.string.tab_entries)) },
                                )
                                Tab(
                                    tab == MainTab.ROUTINES,
                                    onClick = { tab = MainTab.ROUTINES },
                                    text = { Text(stringResource(R.string.tab_routines)) },
                                )
                            }
                            when (tab) {
                                MainTab.ENTRIES -> {
                                    OpenLabels(open.size) { startActivity(LabelsActivity.intent(this@MainActivity)) }
                                    RecentPoints(trackers, points, labels)
                                }
                                MainTab.ROUTINES ->
                                    RoutinesScreen(
                                        app.routineRepository,
                                        app.routinePauseRepository,
                                        app.routineExtraRepository,
                                        app.reminders,
                                    )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        (DebugCommand.handle(application as KladdeApp, intent) ?: shownByReminder(intent))?.let { requestedTab.value = it }
    }

    /** A tap on a reminder opens the routines. */
    private fun shownByReminder(intent: Intent?): MainTab? =
        MainTab.ROUTINES.takeIf { intent?.getBooleanExtra(Reminders.EXTRA_SHOW_ROUTINES, false) == true }

    private companion object {
        const val RECENT = 100
    }
}

/** A small counter of labels still to look at, until there are none (docs/decisions/0002). */
@Composable
private fun OpenLabels(
    count: Int,
    onOpen: () -> Unit,
) {
    if (count == 0) return
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
        IconButton(onClick = onOpen) {
            BadgedBox(badge = { Badge { Text(count.toString()) } }) {
                Icon(
                    painterResource(AdxR.drawable.ic_kladde_label),
                    contentDescription = pluralStringResource(AdxR.plurals.adx_labels_badge, count, count),
                )
            }
        }
    }
}

@Composable
private fun RecentPoints(
    trackers: List<Tracker>,
    points: List<DataPoint>,
    labels: List<SubTypeLabel>,
) {
    if (points.isEmpty()) {
        Message(stringResource(R.string.empty_state))
        return
    }
    val names = trackers.associate { it.id to it.name }
    val shown = labels.associate { (it.trackerId to it.key) to it.shownName }
    val format = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
    LazyColumn(Modifier.padding(horizontal = 16.dp)) {
        items(points, key = { it.id }) { point ->
            Column(Modifier.padding(vertical = 8.dp)) {
                val sub = point.subType?.let { shown[point.trackerId to it] ?: it }
                val value = listOfNotNull(point.value?.let(::formatNumber), point.unit, sub).joinToString(" ")
                Text("${names[point.trackerId] ?: "?"}: $value", style = MaterialTheme.typography.bodyLarge)
                Text(
                    listOfNotNull(
                        point.at.atZone(point.zone).format(format),
                        point.source?.let { stringResource(R.string.source_app, it.packageName) },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            HorizontalDivider()
        }
    }
}

private fun formatNumber(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

@Composable
private fun Message(text: String) {
    Text(text, Modifier.padding(24.dp))
}

@Preview
@Composable
private fun EmptyStatePreview() {
    MaterialTheme { RecentPoints(emptyList(), emptyList(), emptyList()) }
}

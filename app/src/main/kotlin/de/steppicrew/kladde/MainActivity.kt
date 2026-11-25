package de.steppicrew.kladde

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.steppicrew.kladde.model.DataPoint
import de.steppicrew.kladde.model.Tracker
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as KladdeApp
        DebugCommand.handle(app, intent)
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
                        RecentPoints(trackers, points)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        DebugCommand.handle(application as KladdeApp, intent)
    }

    private companion object {
        const val RECENT = 100
    }
}

@Composable
private fun RecentPoints(
    trackers: List<Tracker>,
    points: List<DataPoint>,
) {
    if (points.isEmpty()) {
        Message(stringResource(R.string.empty_state))
        return
    }
    val names = trackers.associate { it.id to it.name }
    val format = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
    LazyColumn(Modifier.padding(horizontal = 16.dp)) {
        items(points, key = { it.id }) { point ->
            Column(Modifier.padding(vertical = 8.dp)) {
                val value = listOfNotNull(point.value?.let(::formatNumber), point.unit, point.subType).joinToString(" ")
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
    MaterialTheme { RecentPoints(emptyList(), emptyList()) }
}

package de.steppicrew.kladde.adx

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import de.steppicrew.adx.AdxStreamDecision
import de.steppicrew.adx.AdxStreamProposal
import de.steppicrew.adx.receiver.AdxConnectSession
import de.steppicrew.adx.receiver.AdxStreamGrant
import de.steppicrew.kladde.model.Tracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

/** What the user picked for one proposed stream. */
internal sealed interface StreamChoice {
    data object NewTracker : StreamChoice

    data class Existing(
        val trackerId: String,
    ) : StreamChoice

    data object Decline : StreamChoice
}

/** The ADX approval screen: per proposed stream, a new tracker, an existing one, or no. */
class ConnectActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = AdxConnectSession.from(this) ?: return
        val host = application as KladdeAdxHost
        setContent {
            MaterialTheme {
                // Edge-to-edge is forced from SDK 35; keep content clear of the system bars.
                Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
                    if (!host.vaultLock.isUnlocked) {
                        Locked(onClose = session::cancel)
                    } else {
                        var trackers by remember { mutableStateOf<List<Tracker>?>(null) }
                        LaunchedEffect(Unit) {
                            trackers = withContext(Dispatchers.IO) { host.trackerRepository.all() }
                        }
                        trackers?.let { known ->
                            ConnectScreen(
                                senderLabel = session.senderLabel,
                                proposals = session.request.streams,
                                trackers = known,
                                initial = initialChoices(session, known),
                                onAllow = { choices -> approve(host, session, choices) },
                                onCancel = session::cancel,
                            )
                        }
                    }
                }
            }
        }
    }

    /** Earlier mappings of the same app are preselected while their tracker still exists. */
    private fun initialChoices(
        session: AdxConnectSession,
        trackers: List<Tracker>,
    ): Map<String, StreamChoice> =
        session.request.streams.associate { proposal ->
            val earlier = session.existing?.streams?.get(proposal.key)
            val target = earlier?.target?.takeIf { id -> trackers.any { it.id == id } }
            proposal.key to
                when {
                    AdxMapping.trackerType(proposal.kind) == null -> StreamChoice.Decline
                    earlier?.decision == AdxStreamDecision.DECLINED -> StreamChoice.Decline
                    target != null -> StreamChoice.Existing(target)
                    else -> StreamChoice.NewTracker
                }
        }

    private fun approve(
        host: KladdeAdxHost,
        session: AdxConnectSession,
        choices: Map<String, StreamChoice>,
    ) {
        lifecycleScope.launch {
            val grants =
                withContext(Dispatchers.IO) {
                    session.request.streams.associate { proposal ->
                        val grant =
                            when (val choice = choices[proposal.key] ?: StreamChoice.Decline) {
                                StreamChoice.Decline -> AdxStreamGrant(proposal, AdxStreamDecision.DECLINED)
                                is StreamChoice.Existing -> AdxStreamGrant(proposal, AdxStreamDecision.ACCEPTED, choice.trackerId)
                                StreamChoice.NewTracker -> {
                                    val tracker = AdxMapping.newTracker(session.caller.packageName, proposal, Instant.now())
                                    host.trackerRepository.save(tracker)
                                    AdxStreamGrant(proposal, AdxStreamDecision.ACCEPTED, tracker.id)
                                }
                            }
                        proposal.key to grant
                    }
                }
            session.approve(grants)
        }
    }
}

@Composable
internal fun ConnectScreen(
    senderLabel: String,
    proposals: List<AdxStreamProposal>,
    trackers: List<Tracker>,
    initial: Map<String, StreamChoice>,
    onAllow: (Map<String, StreamChoice>) -> Unit,
    onCancel: () -> Unit,
) {
    val choices = remember { mutableStateMapOf<String, StreamChoice>().apply { putAll(initial) } }
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.adx_connect_title, senderLabel), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.adx_connect_explainer), style = MaterialTheme.typography.bodyMedium)
            for (proposal in proposals) {
                StreamOptions(
                    proposal = proposal,
                    trackers = trackers.filter { AdxMapping.accepts(it, proposal) },
                    choice = choices[proposal.key] ?: StreamChoice.Decline,
                    onChoice = { choices[proposal.key] = it },
                )
            }
        }
        // Pinned below the list, so a sender with many streams is still one tap to allow.
        Row(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { onAllow(choices.toMap()) }) { Text(stringResource(R.string.adx_connect_allow)) }
            OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.adx_connect_cancel)) }
        }
    }
}

@Composable
private fun StreamOptions(
    proposal: AdxStreamProposal,
    trackers: List<Tracker>,
    choice: StreamChoice,
    onChoice: (StreamChoice) -> Unit,
) {
    Column(Modifier.selectableGroup()) {
        Text(
            stringResource(R.string.adx_connect_stream, proposal.name, listOfNotNull(proposal.kind, proposal.unit).joinToString(", ")),
            style = MaterialTheme.typography.titleMedium,
        )
        if (AdxMapping.trackerType(proposal.kind) == null) {
            Text(stringResource(R.string.adx_connect_unsupported), style = MaterialTheme.typography.bodySmall)
            return@Column
        }
        Option(stringResource(R.string.adx_connect_new_tracker, proposal.name), choice == StreamChoice.NewTracker) {
            onChoice(StreamChoice.NewTracker)
        }
        for (tracker in trackers) {
            Option(stringResource(R.string.adx_connect_existing_tracker, tracker.name), choice == StreamChoice.Existing(tracker.id)) {
                onChoice(StreamChoice.Existing(tracker.id))
            }
        }
        Option(stringResource(R.string.adx_connect_decline), choice == StreamChoice.Decline) { onChoice(StreamChoice.Decline) }
    }
}

@Composable
private fun Option(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        Modifier
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The row handles the click, so screen readers announce one control.
        RadioButton(selected = selected, onClick = null)
        Text(label, Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun Locked(onClose: () -> Unit) {
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.adx_connect_locked))
        Button(onClick = onClose) { Text(stringResource(R.string.adx_connect_close)) }
    }
}

@Preview
@Composable
private fun ConnectScreenPreview() {
    // What the beer counter proposes: six counter streams at once.
    val drinks =
        listOf("Beer", "Wine", "Schnapps", "Cocktail", "Coffee", "Water").map { name ->
            AdxStreamProposal("drinks.${name.lowercase()}", name, "counter", "count")
        }
    MaterialTheme {
        ConnectScreen(
            senderLabel = "Steppi's Beer Counter",
            proposals = drinks,
            trackers = emptyList(),
            initial = drinks.associate { it.key to StreamChoice.NewTracker },
            onAllow = {},
            onCancel = {},
        )
    }
}

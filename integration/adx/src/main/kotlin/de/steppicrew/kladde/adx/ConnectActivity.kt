package de.steppicrew.kladde.adx

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import de.steppicrew.adx.receiver.AdxReceiver
import de.steppicrew.adx.receiver.AdxStreamGrant
import de.steppicrew.kladde.model.Tracker
import de.steppicrew.kladde.model.TrackerType
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

/** Why a new tracker's name can't be used. */
internal enum class NameProblem {
    EMPTY,
    TAKEN,
}

/** The trackers there are, and the names of the apps that sent to them, by package. */
private data class Known(
    val trackers: List<Tracker>,
    val senders: Map<String, String>,
)

/**
 * The streams an earlier approval already settled: accepted into a tracker that still exists, or
 * declined. A repeat approval only asks about the rest; these stay as they were unless changed.
 */
internal fun settledStreams(
    proposals: List<AdxStreamProposal>,
    earlier: Map<String, AdxStreamGrant>,
    trackerIds: Set<String>,
): Set<String> =
    proposals
        .filter { proposal ->
            val grant = earlier[proposal.key] ?: return@filter false
            grant.decision == AdxStreamDecision.DECLINED || grant.target in trackerIds
        }.map { it.key }
        .toSet()

/** The ADX approval screen: per proposed stream, a new tracker, an existing one, or no. */
class ConnectActivity : ComponentActivity() {
    /** The choices waiting for the answer to the notification permission. */
    private var pending: (() -> Unit)? = null

    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Allowed either way: without notifications the app's counter still shows new labels.
            pending?.invoke()
            pending = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showSystemBars()
        val session = AdxConnectSession.from(this) ?: return
        val host = application as KladdeAdxHost
        setContent {
            MaterialTheme {
                // Edge-to-edge is forced from SDK 35; keep content clear of the system bars.
                Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
                    if (!host.vaultLock.isUnlocked) {
                        Locked(onClose = session::cancel)
                    } else {
                        var known by remember { mutableStateOf<Known?>(null) }
                        LaunchedEffect(Unit) {
                            known =
                                withContext(Dispatchers.IO) {
                                    val senders = AdxReceiver.consents(this@ConnectActivity).all().associate { it.packageName to it.label }
                                    Known(host.trackerRepository.all(), senders)
                                }
                        }
                        known?.let { (trackers, senders) ->
                            ConnectScreen(
                                senderLabel = session.senderLabel,
                                proposals = session.request.streams,
                                trackers = trackers,
                                senders = senders,
                                initial = initialChoices(session, trackers),
                                settled =
                                    settledStreams(
                                        session.request.streams,
                                        session.existing?.streams.orEmpty(),
                                        trackers.map { it.id }.toSet(),
                                    ),
                                initialNames = defaultNames(session.request.streams, session.senderLabel, trackers),
                                onAllow = { choices, names -> withNotificationPermission { approve(host, session, choices, names) } },
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

    /**
     * Asks for the notification permission first, the moment notifications about the
     * sender's new labels become possible (docs/decisions/0002), then runs [then].
     */
    private fun withNotificationPermission(then: () -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            return then()
        }
        pending = then
        askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun approve(
        host: KladdeAdxHost,
        session: AdxConnectSession,
        choices: Map<String, StreamChoice>,
        names: Map<String, String>,
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
                                    val name = names[proposal.key]?.trim()?.ifEmpty { null } ?: proposal.name
                                    val tracker = AdxMapping.newTracker(session.caller.packageName, proposal, Instant.now(), name = name)
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
    senders: Map<String, String>,
    initial: Map<String, StreamChoice>,
    settled: Set<String>,
    initialNames: Map<String, String>,
    onAllow: (choices: Map<String, StreamChoice>, names: Map<String, String>) -> Unit,
    onCancel: () -> Unit,
) {
    var changeSettled by rememberSaveable { mutableStateOf(false) }
    val fresh = proposals.filter { it.key !in settled }
    val choices = remember { mutableStateMapOf<String, StreamChoice>().apply { putAll(initial) } }
    val names = remember { mutableStateMapOf<String, String>().apply { putAll(initialNames) } }
    // The template, so the names follow a configuration change like the rest of the screen.
    val ofApp = stringResource(R.string.adx_connect_tracker_of_app)
    val labels = remember(trackers, senders, ofApp) { trackerLabels(trackers, senders) { name, app -> ofApp.format(name, app) } }

    // A new tracker's name must be free among the trackers there are and the other new ones.
    fun problem(key: String): NameProblem? {
        if (choices[key] != StreamChoice.NewTracker) return null
        val others = proposals.filter { it.key != key && choices[it.key] == StreamChoice.NewTracker }.map { names[it.key].orEmpty() }
        return nameProblem(names[key].orEmpty(), trackers.map { it.name } + others)
    }
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (settled.isEmpty()) {
                Text(stringResource(R.string.adx_connect_title, senderLabel), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.adx_connect_explainer), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.adx_connect_labels_note), style = MaterialTheme.typography.bodyMedium)
            } else {
                // A repeat: only what is new is asked; the rest is one summary line each.
                val title = if (fresh.isEmpty()) R.string.adx_connect_again_title else R.string.adx_connect_more_title
                Text(stringResource(title, senderLabel), style = MaterialTheme.typography.headlineSmall)
            }
            val shown = if (changeSettled) proposals else fresh
            for (proposal in shown) {
                StreamOptions(
                    proposal = proposal,
                    trackers = trackers.filter { AdxMapping.accepts(it, proposal) },
                    labels = labels,
                    choice = choices[proposal.key] ?: StreamChoice.Decline,
                    onChoice = { choices[proposal.key] = it },
                    name = names[proposal.key].orEmpty(),
                    onName = { names[proposal.key] = it },
                    problem = problem(proposal.key),
                )
            }
            if (settled.isNotEmpty() && !changeSettled) {
                SettledSummary(
                    proposals.filter { it.key in settled },
                    choices,
                    labels,
                    onChange = { changeSettled = true },
                )
            }
        }
        // Pinned below the list, so a sender with many streams is still one tap to allow.
        Row(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { onAllow(choices.toMap(), names.toMap()) }, enabled = proposals.none { problem(it.key) != null }) {
                Text(stringResource(R.string.adx_connect_allow))
            }
            OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.adx_connect_cancel)) }
        }
    }
}

/** The streams an earlier approval settled, one line each, with a way to change them. */
@Composable
private fun SettledSummary(
    proposals: List<AdxStreamProposal>,
    choices: Map<String, StreamChoice>,
    labels: Map<String, String>,
    onChange: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.adx_connect_settled), style = MaterialTheme.typography.titleSmall)
        for (proposal in proposals) {
            val line =
                when (val choice = choices[proposal.key]) {
                    is StreamChoice.Existing ->
                        stringResource(R.string.adx_connect_settled_into, proposal.name, labels[choice.trackerId] ?: proposal.name)
                    else -> stringResource(R.string.adx_connect_settled_declined, proposal.name)
                }
            Text(line, style = MaterialTheme.typography.bodyMedium)
        }
        TextButton(onClick = onChange) { Text(stringResource(R.string.adx_connect_change)) }
    }
}

@Composable
private fun StreamOptions(
    proposal: AdxStreamProposal,
    trackers: List<Tracker>,
    labels: Map<String, String>,
    choice: StreamChoice,
    onChoice: (StreamChoice) -> Unit,
    name: String,
    onName: (String) -> Unit,
    problem: NameProblem?,
) {
    Column(Modifier.selectableGroup()) {
        Text(
            stringResource(R.string.adx_connect_stream, proposal.name, listOfNotNull(proposal.kind, proposal.unit).joinToString(", ")),
            style = MaterialTheme.typography.titleMedium,
        )
        // The tracker takes the stream's name; its entry types (an app's drinks) are listed here.
        proposal.subs.takeIf { it.isNotEmpty() }?.let { subs ->
            Text(
                stringResource(R.string.adx_connect_stream_subs, subs.joinToString(", ") { it.label ?: it.key }),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (AdxMapping.trackerType(proposal.kind) == null) {
            Text(stringResource(R.string.adx_connect_unsupported), style = MaterialTheme.typography.bodySmall)
            return@Column
        }
        Option(stringResource(R.string.adx_connect_new_tracker, name.ifBlank { proposal.name }), choice == StreamChoice.NewTracker) {
            onChoice(StreamChoice.NewTracker)
        }
        if (choice == StreamChoice.NewTracker) {
            OutlinedTextField(
                value = name,
                onValueChange = onName,
                label = { Text(stringResource(R.string.adx_connect_new_tracker_name)) },
                isError = problem != null,
                supportingText =
                    when (problem) {
                        NameProblem.EMPTY -> ({ Text(stringResource(R.string.adx_connect_name_empty)) })
                        NameProblem.TAKEN -> ({ Text(stringResource(R.string.adx_connect_name_taken)) })
                        null -> null
                    },
                singleLine = true,
                // Lined up with the option's text, under its radio button.
                modifier = Modifier.fillMaxWidth().padding(start = 32.dp),
            )
        }
        for (tracker in trackers) {
            val label = labels[tracker.id] ?: tracker.name
            Option(stringResource(R.string.adx_connect_existing_tracker, label), choice == StreamChoice.Existing(tracker.id)) {
                onChoice(StreamChoice.Existing(tracker.id))
            }
        }
        Option(stringResource(R.string.adx_connect_decline), choice == StreamChoice.Decline) { onChoice(StreamChoice.Decline) }
    }
}

@Composable
internal fun Option(
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
            senders = emptyMap(),
            initial = drinks.associate { it.key to StreamChoice.NewTracker },
            settled = emptySet(),
            initialNames = drinks.associate { it.key to it.name },
            onAllow = { _, _ -> },
            onCancel = {},
        )
    }
}

@Preview
@Composable
private fun RepeatConnectScreenPreview() {
    // The beer counter connecting again with one new stream; the others were approved before.
    val drinks =
        listOf("Beer", "Wine", "Tea").map { name ->
            AdxStreamProposal("drinks.${name.lowercase()}", name, "counter", "count")
        }
    val trackers = listOf("Beer", "Wine").map { Tracker(it.lowercase(), it, TrackerType.COUNTER, null, Instant.EPOCH) }
    MaterialTheme {
        ConnectScreen(
            senderLabel = "Steppi's Beer Counter",
            proposals = drinks,
            trackers = trackers,
            senders = emptyMap(),
            initial =
                mapOf(
                    "drinks.beer" to StreamChoice.Existing(trackers[0].id),
                    "drinks.wine" to StreamChoice.Existing(trackers[1].id),
                    "drinks.tea" to StreamChoice.NewTracker,
                ),
            settled = setOf("drinks.beer", "drinks.wine"),
            initialNames = mapOf("drinks.tea" to "Tea"),
            onAllow = { _, _ -> },
            onCancel = {},
        )
    }
}

package de.steppicrew.kladde.adx

import android.content.Context
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import de.steppicrew.adx.receiver.AdxReceiver
import de.steppicrew.kladde.model.SubTypeLabel
import de.steppicrew.kladde.model.SubTypeLabelStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

/** A label with what the screen shows about where it came from. */
internal data class LabelItem(
    val label: SubTypeLabel,
    val trackerName: String,
    /** The sending app's name, or null when it isn't connected any more. */
    val senderLabel: String?,
    /** The name the field starts with, free among the tracker's entry types. */
    val presetName: String = label.key,
    /** Names the tracker's other entry types go by; this one's must differ. */
    val otherNames: List<String> = emptyList(),
)

/**
 * Every open label, the one a notification was about first (docs/decisions/0002). Reached
 * from a label notification and from the counter on the main screen.
 */
class LabelsActivity : ComponentActivity() {
    private var focus by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        focus = intent.getStringExtra(EXTRA_LABEL_ID)
        val host = application as KladdeAdxHost
        setContent {
            MaterialTheme {
                // Edge-to-edge is forced from SDK 35; keep content clear of the system bars.
                Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
                    if (!host.vaultLock.isUnlocked) {
                        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(stringResource(R.string.adx_labels_locked))
                            Button(onClick = ::finish) { Text(stringResource(R.string.adx_labels_close)) }
                        }
                    } else {
                        val open by host.subTypeLabelRepository.observeOpen().collectAsStateWithLifecycle(null)
                        var items by remember { mutableStateOf<List<LabelItem>?>(null) }
                        LaunchedEffect(open) {
                            open?.let { labels -> items = withContext(Dispatchers.IO) { describe(host, labels) } }
                        }
                        items?.let {
                            LabelsScreen(
                                items = it,
                                focusId = focus,
                                onSave = { id, name, defaults -> resolve(id) { host.subTypeLabelRepository.specify(id, name, defaults) } },
                                onIgnore = { id -> resolve(id) { host.subTypeLabelRepository.ignore(id) } },
                                onClose = ::finish,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        focus = intent.getStringExtra(EXTRA_LABEL_ID)
    }

    private fun describe(
        host: KladdeAdxHost,
        labels: List<SubTypeLabel>,
    ): List<LabelItem> {
        val trackers = host.trackerRepository.all().associateBy { it.id }
        val senders = AdxReceiver.consents(this).all().associateBy { it.packageName }
        val others = otherLabelNames(host.subTypeLabelRepository.all())
        val presets = presetLabelNames(labels, others)
        return labels.map { label ->
            LabelItem(
                label = label,
                trackerName = trackers[label.trackerId]?.name ?: "?",
                senderLabel = label.sourcePackage?.let { senders[it]?.label },
                presetName = presets[label.id] ?: label.key,
                otherNames = others[label.id].orEmpty(),
            )
        }
    }

    /** Stores the user's answer, then removes the label's notification; the list follows the database. */
    private fun resolve(
        id: String,
        store: () -> Unit,
    ) {
        lifecycleScope.launch(Dispatchers.IO) {
            store()
            LabelNotifications(this@LabelsActivity).cancel(id)
        }
    }

    companion object {
        const val EXTRA_LABEL_ID = "de.steppicrew.kladde.adx.extra.LABEL_ID"

        /** Opens the screen, with [labelId]'s label first when given. */
        fun intent(
            context: Context,
            labelId: String? = null,
        ): Intent =
            Intent(context, LabelsActivity::class.java)
                .putExtra(EXTRA_LABEL_ID, labelId)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}

@Composable
internal fun LabelsScreen(
    items: List<LabelItem>,
    focusId: String?,
    onSave: (id: String, name: String, defaults: Map<String, Double>) -> Unit,
    onIgnore: (id: String) -> Unit,
    onClose: () -> Unit,
) {
    // Stable: the rest keep their order.
    val ordered = items.sortedByDescending { it.label.id == focusId }
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.adx_labels_title), style = MaterialTheme.typography.headlineSmall)
            if (ordered.isEmpty()) {
                Text(stringResource(R.string.adx_labels_done), style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(stringResource(R.string.adx_labels_explainer), style = MaterialTheme.typography.bodyMedium)
            }
            for (item in ordered) {
                key(item.label.id) { LabelCard(item, onSave, onIgnore) }
            }
        }
        // Pinned below the list, like the approval screen's buttons.
        Row(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            OutlinedButton(onClick = onClose) { Text(stringResource(R.string.adx_labels_close)) }
        }
    }
}

@Composable
private fun LabelCard(
    item: LabelItem,
    onSave: (id: String, name: String, defaults: Map<String, Double>) -> Unit,
    onIgnore: (id: String) -> Unit,
) {
    val label = item.label
    val kinds = labelKinds(label.attrKeys)
    var name by rememberSaveable { mutableStateOf(item.presetName) }
    val nameProblem = nameProblem(name, item.otherNames)
    var kind by rememberSaveable { mutableStateOf(kinds.first()) }
    val typed = remember { mutableStateMapOf<String, String>() }
    val defaults = labelDefaults(kind, label.attrKeys, typed)
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.adx_labels_sent_as, label.key), style = MaterialTheme.typography.titleMedium)
            Text(
                item.senderLabel?.let { stringResource(R.string.adx_labels_from, item.trackerName, it) } ?: item.trackerName,
                style = MaterialTheme.typography.bodySmall,
            )
            NameField(name, { name = it }, nameProblem, item.trackerName)
            if (kinds.size > 1) {
                Column(Modifier.selectableGroup()) {
                    for (option in kinds) {
                        Option(kindName(option), kind == option) { kind = option }
                    }
                }
            }
            val fields = labelFields(kind, label.attrKeys)
            for (field in fields) {
                val text = typed[field].orEmpty()
                val invalid = text.isNotBlank() && parseValue(text) == null
                OutlinedTextField(
                    value = text,
                    onValueChange = { typed[field] = it },
                    label = { Text(attrName(field)) },
                    isError = invalid,
                    supportingText = if (invalid) ({ Text(stringResource(R.string.adx_labels_not_a_number)) }) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (fields.isNotEmpty()) {
                Text(stringResource(R.string.adx_labels_values_hint), style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { defaults?.let { onSave(label.id, name, it) } }, enabled = defaults != null && nameProblem == null) {
                    Text(stringResource(R.string.adx_labels_save))
                }
                OutlinedButton(onClick = { onIgnore(label.id) }) { Text(stringResource(R.string.adx_labels_ignore)) }
            }
        }
    }
}

@Composable
private fun NameField(
    name: String,
    onName: (String) -> Unit,
    problem: NameProblem?,
    trackerName: String,
) {
    OutlinedTextField(
        value = name,
        onValueChange = onName,
        label = { Text(stringResource(R.string.adx_labels_name)) },
        isError = problem != null,
        supportingText =
            when (problem) {
                NameProblem.EMPTY -> ({ Text(stringResource(R.string.adx_connect_name_empty)) })
                NameProblem.TAKEN -> ({ Text(stringResource(R.string.adx_labels_name_taken, trackerName)) })
                null -> null
            },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun kindName(kind: LabelKind): String =
    stringResource(
        when (kind) {
            LabelKind.ALCOHOLIC -> R.string.adx_labels_kind_alcoholic
            LabelKind.NON_ALCOHOLIC -> R.string.adx_labels_kind_non_alcoholic
            LabelKind.VALUES -> R.string.adx_labels_kind_values
            LabelKind.COUNT_ONLY -> R.string.adx_labels_kind_count_only
        },
    )

@Composable
private fun attrName(key: String): String =
    when (key) {
        VOLUME_ML -> stringResource(R.string.adx_attr_volume_ml)
        ABV_PCT -> stringResource(R.string.adx_attr_abv_pct)
        ALCOHOL_G -> stringResource(R.string.adx_attr_alcohol_g)
        "intake.kcal" -> stringResource(R.string.adx_attr_kcal)
        "intake.caffeine_mg" -> stringResource(R.string.adx_attr_caffeine_mg)
        "food.protein_g" -> stringResource(R.string.adx_attr_protein_g)
        "food.carbs_g" -> stringResource(R.string.adx_attr_carbs_g)
        "food.fat_g" -> stringResource(R.string.adx_attr_fat_g)
        "food.fibre_g" -> stringResource(R.string.adx_attr_fibre_g)
        else -> key
    }

@Preview
@Composable
private fun LabelsScreenPreview() {
    fun item(
        key: String,
        attrs: List<String>,
    ) = LabelItem(
        SubTypeLabel(key, "t1", key, SubTypeLabelStatus.OPEN, Instant.EPOCH, attrKeys = attrs),
        "Beer",
        "Steppi's Beer Counter",
    )
    MaterialTheme {
        LabelsScreen(
            items = listOf(item("beerSmall", listOf(VOLUME_ML, ABV_PCT)), item("Bier", listOf(VOLUME_ML, ABV_PCT))),
            focusId = "Bier",
            onSave = { _, _, _ -> },
            onIgnore = {},
            onClose = {},
        )
    }
}

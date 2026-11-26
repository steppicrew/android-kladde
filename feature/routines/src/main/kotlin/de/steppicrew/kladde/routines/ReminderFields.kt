package de.steppicrew.kladde.routines

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** How long before its time a reminder can come, in minutes. */
private val OFFSETS = listOf(0, 5, 15, 30, 60)

/**
 * "Erinnern" and how long before the time; [remindBefore] null is off. While it is on, the
 * permission to post notifications is asked for, once per form.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReminderFields(
    remindBefore: Int?,
    onChange: (Int?) -> Unit,
) {
    AskForNotifications(remindBefore != null)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SwitchRow(R.string.routines_remind, R.string.routines_remind_hint, remindBefore != null) { on ->
            onChange(if (on) 0 else null)
        }
        if (remindBefore != null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (minutes in OFFSETS) {
                    FilterChip(
                        selected = minutes == remindBefore,
                        onClick = { onChange(minutes) },
                        label = { Text(offsetText(minutes)) },
                    )
                }
            }
            SoundRow()
        }
    }
}

@Composable
private fun offsetText(minutes: Int): String =
    if (minutes == 0) {
        stringResource(R.string.routines_remind_at_time)
    } else {
        pluralStringResource(R.plurals.routines_remind_minutes_before, minutes, minutes)
    }

/** Asks for the notification permission (Android 13 on) when [wanted] and not granted yet. */
@Composable
private fun AskForNotifications(wanted: Boolean) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { asked = true }
    LaunchedEffect(wanted, asked) {
        val granted = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (wanted && !asked && !granted) {
            asked = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/** "Ton: …" for all reminders, picked with Android's sound picker. */
@Composable
private fun SoundRow() {
    val context = LocalContext.current
    val sound = remember { ReminderSound(context) }
    var title by remember { mutableStateOf(sound.title()) }
    val pick =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                sound.set(result.data?.pickedSound())
                title = sound.title()
            }
        }
    val pickerTitle = stringResource(R.string.routines_remind_sound)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(pickerTitle, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = {
            val intent =
                Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, sound.uri)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, pickerTitle)
            pick.launch(intent)
        }) { Text(title ?: stringResource(R.string.routines_remind_silent)) }
    }
    Text(stringResource(R.string.routines_remind_sound_hint), style = MaterialTheme.typography.bodySmall)
}

private fun Intent.pickedSound(): Uri? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
    }

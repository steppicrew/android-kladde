package de.steppicrew.kladde.routines

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * What stands in the way of reminders, while some routine has one: notifications that aren't
 * allowed, and alarms that may come late without exact alarms. The latter is asked once; after
 * "Nicht jetzt" reminders just come within ten minutes.
 */
@Composable
internal fun ReminderHints(
    reminders: Reminders,
    anyReminder: Boolean,
) {
    if (!anyReminder) return
    val context = LocalContext.current
    // Counts returns to the screen, e.g. from the settings, so what was allowed there is seen.
    var resumed by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumed++
        onPauseOrDispose {}
    }
    var dismissed by remember { mutableIntStateOf(0) }
    val key = resumed + dismissed
    val canNotify = remember(key) { canNotify(context) }
    val askExact = remember(key) { !reminders.canBeExact && !reminders.exactHintDismissed }
    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!canNotify) {
            Hint(
                R.string.routines_hint_notifications,
                R.string.routines_hint_allow,
                onAllow = { context.startActivity(notificationSettings(context)) },
            )
        } else if (askExact) {
            Hint(
                R.string.routines_hint_exact,
                R.string.routines_hint_allow,
                onAllow = { context.startActivity(exactAlarmSettings(context)) },
                onDismiss = {
                    reminders.exactHintDismissed = true
                    dismissed++
                },
            )
        }
    }
}

@Composable
private fun Hint(
    text: Int,
    allow: Int,
    onAllow: () -> Unit,
    onDismiss: (() -> Unit)? = null,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp)) {
            Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (onDismiss != null) TextButton(onClick = onDismiss) { Text(stringResource(R.string.routines_hint_not_now)) }
                TextButton(onClick = onAllow) { Text(stringResource(allow)) }
            }
        }
    }
}

private fun canNotify(context: Context): Boolean {
    val permitted =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    return permitted && context.getSystemService(android.app.NotificationManager::class.java).areNotificationsEnabled()
}

private fun notificationSettings(context: Context) =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

/** Only asked for from Android 12 on, where [Reminders.canBeExact] can be false. */
private fun exactAlarmSettings(context: Context) =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
    }

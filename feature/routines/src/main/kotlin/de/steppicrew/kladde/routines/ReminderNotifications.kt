package de.steppicrew.kladde.routines

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import de.steppicrew.kladde.model.Occurrence
import de.steppicrew.kladde.model.shownTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * One notification per due occurrence: the routine's name and time, with Erledigt and Auslassen
 * for one that is ticked off, and "In 10 Min." The owner chose to show the name on the lock
 * screen too, so it is public. Without the permission nothing is posted.
 */
internal class ReminderNotifications(
    context: Context,
) {
    private val context = context.applicationContext
    private val manager = this.context.getSystemService(NotificationManager::class.java)

    fun show(
        occurrence: Occurrence,
        key: OccurrenceKey,
    ) {
        if (!canPost()) return
        ensureChannel()
        val routine = occurrence.routine
        val builder =
            Notification
                .Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_kladde_reminder)
                .setContentTitle(routine.title)
                .setContentText(timeText(occurrence))
                .setCategory(Notification.CATEGORY_REMINDER)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setContentIntent(openApp(key))
        if (routine.checkable) {
            builder.addAction(action(R.string.routines_done, ReminderReceiver.ACTION_DONE, key))
            builder.addAction(action(R.string.routines_skip, ReminderReceiver.ACTION_SKIP, key))
        }
        builder.addAction(action(R.string.routines_remind_later, ReminderReceiver.ACTION_SNOOZE, key))
        try {
            manager.notify(key.notificationId, builder.build())
        } catch (ignored: SecurityException) {
            // The permission was taken back since canPost(); the routines tab still shows it.
        }
    }

    fun cancel(key: OccurrenceKey) = manager.cancel(key.notificationId)

    /** "18:00", converted for a routine bound to another zone. */
    private fun timeText(occurrence: Occurrence): String {
        val shown = occurrence.shownTime(ZoneId.systemDefault()) ?: return ""
        val time = shown.time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
        return if (shown.dayShift == 0L) time else context.getString(R.string.routines_day_shift, time, "%+d".format(shown.dayShift))
    }

    private fun action(
        label: Int,
        action: String,
        key: OccurrenceKey,
    ): Notification.Action {
        val intent =
            key.putInto(
                Intent(context, ReminderReceiver::class.java)
                    .setAction(action)
                    .setData(Uri.parse("${Reminders.SCHEME}://$action/${key.routineId}/${key.originalDate}")),
            )
        val pending = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Action.Builder(null as Icon?, context.getString(label), pending).build()
    }

    private fun openApp(key: OccurrenceKey): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        launch
            .putExtra(
                Reminders.EXTRA_SHOW_ROUTINES,
                true,
            ).setData(Uri.parse("${Reminders.SCHEME}://open/${key.routineId}/${key.originalDate}"))
        return PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun canPost(): Boolean {
        val permitted =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return permitted && manager.areNotificationsEnabled()
    }

    private fun ensureChannel() {
        val channel =
            NotificationChannel(CHANNEL, context.getString(R.string.routines_reminder_channel), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.routines_reminder_channel_description)
            }
        manager.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL = "reminders"
    }
}

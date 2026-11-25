package de.steppicrew.kladde.adx

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build

/**
 * One notification per new label, grouped (docs/decisions/0002). Its text is outside
 * Kladde's storage, so it is private on the lock screen, where a public version names no
 * label. Without the permission, or with the channel turned off, nothing is posted and the
 * app's counter is the only hint.
 */
class LabelNotifications(
    context: Context,
) {
    private val context = context.applicationContext
    private val manager = this.context.getSystemService(NotificationManager::class.java)

    fun show(new: NewLabel) {
        if (!canPost()) return
        ensureChannel()
        val id = new.label.id
        val open =
            PendingIntent.getActivity(
                context,
                id.hashCode(),
                LabelsActivity.intent(context, id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val ignore =
            PendingIntent.getBroadcast(
                context,
                id.hashCode(),
                LabelActionReceiver.ignoreIntent(context, id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val notification =
            builder()
                .setContentTitle(context.getString(R.string.adx_label_notification_title, new.senderLabel))
                .setContentText(context.getString(R.string.adx_label_notification_text, new.label.key, new.trackerName))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPublicVersion(builder().setContentText(context.getString(R.string.adx_label_notification_public)).build())
                .addAction(Notification.Action.Builder(null as Icon?, context.getString(R.string.adx_label_specify), open).build())
                .addAction(Notification.Action.Builder(null as Icon?, context.getString(R.string.adx_labels_ignore), ignore).build())
                .build()
        post(notificationId(id), notification)
        updateSummary()
    }

    /** Removes the notification of a label the user has dealt with, wherever they did. */
    fun cancel(labelId: String) {
        manager.cancel(notificationId(labelId))
        updateSummary()
    }

    /** Android groups notifications only under a summary; it is needed from two on. */
    private fun updateSummary() {
        val count = manager.activeNotifications.count { it.notification.group == GROUP && it.id != SUMMARY_ID }
        if (count < 2 || !canPost()) {
            manager.cancel(SUMMARY_ID)
            return
        }
        val text = context.resources.getQuantityString(R.plurals.adx_label_summary, count, count)
        val summary =
            builder()
                .setContentTitle(text)
                .setGroupSummary(true)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPublicVersion(builder().setContentText(text).build())
                .setContentIntent(
                    PendingIntent.getActivity(
                        context,
                        SUMMARY_ID,
                        LabelsActivity.intent(context),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                ).build()
        post(SUMMARY_ID, summary)
    }

    private fun builder() =
        Notification
            .Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_kladde_label)
            .setGroup(GROUP)

    private fun post(
        id: Int,
        notification: Notification,
    ) {
        try {
            manager.notify(id, notification)
        } catch (ignored: SecurityException) {
            // The permission was taken back since canPost(); the in-app counter still shows the label.
        }
    }

    private fun canPost(): Boolean {
        val permitted =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return permitted && manager.areNotificationsEnabled()
    }

    private fun ensureChannel() {
        val channel =
            NotificationChannel(CHANNEL, context.getString(R.string.adx_label_channel), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.adx_label_channel_description)
            }
        manager.createNotificationChannel(channel)
    }

    private fun notificationId(labelId: String) = labelId.hashCode().let { if (it == SUMMARY_ID) it + 1 else it }

    private companion object {
        const val CHANNEL = "labels"
        const val GROUP = "de.steppicrew.kladde.labels"
        const val SUMMARY_ID = 1
    }
}

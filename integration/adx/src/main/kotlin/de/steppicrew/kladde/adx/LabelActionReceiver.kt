package de.steppicrew.kladde.adx

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlin.concurrent.thread

/** "Ignore" on a label notification. Not exported: only that notification's PendingIntent reaches it. */
class LabelActionReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val id = intent.getStringExtra(LabelsActivity.EXTRA_LABEL_ID)?.takeIf { intent.action == ACTION_IGNORE }
        val host = context.applicationContext as KladdeAdxHost
        // Locked, the label can't be written; the notification stays until it can.
        if (id == null || !host.vaultLock.isUnlocked) return
        val pending = goAsync()
        thread {
            try {
                host.subTypeLabelRepository.ignore(id)
                LabelNotifications(context).cancel(id)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val ACTION_IGNORE = "de.steppicrew.kladde.adx.action.IGNORE_LABEL"

        fun ignoreIntent(
            context: Context,
            labelId: String,
        ): Intent =
            Intent(context, LabelActionReceiver::class.java)
                .setAction(ACTION_IGNORE)
                .putExtra(LabelsActivity.EXTRA_LABEL_ID, labelId)
    }
}

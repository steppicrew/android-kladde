package de.steppicrew.kladde.routines

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.steppicrew.kladde.model.CompletionStatus
import de.steppicrew.kladde.model.OccurrenceStatus
import de.steppicrew.kladde.model.RoutineCompletion
import de.steppicrew.kladde.model.occurrenceOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * A reminder's alarm, and the Erledigt / Auslassen / later buttons on its notification. Not
 * exported: only the app's own alarms and notifications reach it.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val key = OccurrenceKey.from(intent) ?: return
        val host = context.applicationContext as RoutinesHost
        val action = intent.action
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handle(context, host, action, key)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(
        context: Context,
        host: RoutinesHost,
        action: String?,
        key: OccurrenceKey,
    ) {
        val notifications = ReminderNotifications(context)
        when (action) {
            ACTION_FIRE -> {
                val occurrence =
                    host.reminders
                        .plan(
                            LocalDate.now(),
                        ).occurrenceOf(key.routineId, key.originalDate, key.date, LocalDate.now())
                // Done early, cancelled or moved since: nothing to remind of.
                if (occurrence != null && occurrence.status in DUE) notifications.show(occurrence, key)
                host.reminders.reschedule(maxOf(Instant.now(), key.at))
            }
            ACTION_DONE, ACTION_SKIP -> {
                val status = if (action == ACTION_DONE) CompletionStatus.DONE else CompletionStatus.SKIPPED
                host.routineRepository.complete(
                    RoutineCompletion(key.routineId, key.originalDate, status, Instant.now(), ZoneId.systemDefault()),
                )
                notifications.cancel(key)
            }
            ACTION_SNOOZE -> {
                notifications.cancel(key)
                host.reminders.snooze(key)
            }
        }
    }

    companion object {
        internal const val ACTION_FIRE = "de.steppicrew.kladde.routines.action.REMIND"
        internal const val ACTION_DONE = "de.steppicrew.kladde.routines.action.DONE"
        internal const val ACTION_SKIP = "de.steppicrew.kladde.routines.action.SKIP"
        internal const val ACTION_SNOOZE = "de.steppicrew.kladde.routines.action.SNOOZE"
        private val DUE = setOf(OccurrenceStatus.OPEN, OccurrenceStatus.HAPPENS)
    }
}

/** Sets the alarms again after a reboot, an app update, a change of clock or zone, or of the exact-alarm permission. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val host = context.applicationContext as RoutinesHost
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                host.reminders.reschedule()
            } finally {
                pending.finish()
            }
        }
    }
}

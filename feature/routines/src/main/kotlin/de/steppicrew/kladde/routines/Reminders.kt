package de.steppicrew.kladde.routines

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import de.steppicrew.kladde.data.RoutineExtraRepository
import de.steppicrew.kladde.data.RoutinePauseRepository
import de.steppicrew.kladde.data.RoutineRepository
import de.steppicrew.kladde.model.Reminder
import de.steppicrew.kladde.model.RoutinePlan
import de.steppicrew.kladde.model.nextReminders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Keeps one alarm per routine, for its next reminder. Exact when the user allows exact alarms,
 * else within [WINDOW] after the time. Only routine ids are kept outside the database: which
 * routines have an alarm set, so a dropped reminder's alarm can be taken back.
 */
class Reminders(
    context: Context,
    private val routines: RoutineRepository,
    private val pauses: RoutinePauseRepository,
    private val extras: RoutineExtraRepository,
) {
    private val context = context.applicationContext
    private val alarms = this.context.getSystemService(AlarmManager::class.java)
    private val prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val lock = Mutex()

    /** Whether alarms come on the minute; before Android 12 they always may. */
    val canBeExact: Boolean get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()

    /** "Nicht jetzt" on the hint asking for exact alarms: it isn't shown again. */
    var exactHintDismissed: Boolean
        get() = prefs.getBoolean(KEY_EXACT_DISMISSED, false)
        set(value) = prefs.edit().putBoolean(KEY_EXACT_DISMISSED, value).apply()

    /** Sets the alarms again whenever something about routines changes, as long as [scope] lives. */
    fun watch(scope: CoroutineScope) {
        val today = LocalDate.now()
        scope.launch {
            merge(
                routines.observeAll().map {},
                routines.observeCompletions(today.minusYears(1), today.plusYears(2)).map {},
                routines.observeRules().map {},
                routines.observeOverrides().map {},
                pauses.observeAll().map {},
                extras.observeAll().map {},
            ).collectLatest {
                // One reschedule for a burst of changes, such as a routine saved with its rules.
                delay(SETTLE_MS)
                reschedule()
            }
        }
    }

    /** The plan as stored now, for working out reminders around [today]. */
    suspend fun plan(today: LocalDate): RoutinePlan =
        RoutinePlan(
            routines.observeAll().first(),
            routines.observeCompletions(today.minusYears(1), today.plusYears(2)).first(),
            routines.observeRules().first(),
            routines.observeOverrides().first(),
            pauses.observeAll().first(),
            extras.observeAll().first(),
        )

    /** Sets each routine's alarm for its next reminder after [after], and takes back the rest. */
    suspend fun reschedule(after: Instant = Instant.now()) =
        lock.withLock {
            val here = ZoneId.systemDefault()
            val next = plan(after.atZone(here).toLocalDate()).nextReminders(after, here)
            val ids = next.mapTo(HashSet()) { it.occurrence.routine.id }
            for (id in prefs.getStringSet(KEY_SET, emptySet()).orEmpty() - ids) {
                alarms.cancel(alarmIntent(Uri.parse("$SCHEME://routine/$id"), null))
            }
            for (reminder in next) set(reminder.at, alarmIntent(Uri.parse("$SCHEME://routine/${reminder.occurrence.routine.id}"), reminder))
            prefs.edit().putStringSet(KEY_SET, ids).apply()
        }

    /** The reminder for [key] once more in ten minutes. */
    fun snooze(key: OccurrenceKey) {
        val at = Instant.now().plus(SNOOZE)
        set(at, alarmIntent(Uri.parse("$SCHEME://snooze/${key.routineId}/${key.originalDate}"), null, key.copy(at = at)))
    }

    private fun set(
        at: Instant,
        intent: PendingIntent,
    ) {
        val millis = at.toEpochMilli()
        try {
            if (canBeExact) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
                return
            }
        } catch (ignored: SecurityException) {
            // Exact alarms were taken back just now: fall back to a window.
        }
        alarms.setWindow(AlarmManager.RTC_WAKEUP, millis, WINDOW.toMillis(), intent)
    }

    /** One alarm per [uri]: setting it again replaces it, cancelling needs no extras. */
    private fun alarmIntent(
        uri: Uri,
        reminder: Reminder?,
        key: OccurrenceKey? = reminder?.let { OccurrenceKey.of(it) },
    ): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_FIRE).setData(uri)
        key?.putInto(intent)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    companion object {
        /** Added to the app's launch intent by a reminder: open the routines tab. */
        const val EXTRA_SHOW_ROUTINES = "de.steppicrew.kladde.extra.SHOW_ROUTINES"
        internal const val SCHEME = "kladde-reminder"
        private const val PREFS = "reminders"
        private const val KEY_SET = "alarms_set"
        private const val KEY_EXACT_DISMISSED = "exact_hint_dismissed"
        private const val SETTLE_MS = 300L
        private val WINDOW: Duration = Duration.ofMinutes(10)
        private val SNOOZE: Duration = Duration.ofMinutes(10)
    }
}

/** Which occurrence a reminder or its action is about, and when it was due. */
data class OccurrenceKey(
    val routineId: String,
    val originalDate: LocalDate,
    val date: LocalDate,
    val at: Instant,
) {
    fun putInto(intent: Intent): Intent =
        intent
            .putExtra(EXTRA_ROUTINE, routineId)
            .putExtra(EXTRA_ORIGINAL, originalDate.toString())
            .putExtra(EXTRA_DATE, date.toString())
            .putExtra(EXTRA_AT, at.toEpochMilli())

    /** Tells notifications apart: one per occurrence. */
    val notificationId: Int get() = "$routineId/$originalDate".hashCode()

    companion object {
        private const val EXTRA_ROUTINE = "routine"
        private const val EXTRA_ORIGINAL = "original_date"
        private const val EXTRA_DATE = "date"
        private const val EXTRA_AT = "at"

        fun of(reminder: Reminder) =
            OccurrenceKey(reminder.occurrence.routine.id, reminder.occurrence.originalDate, reminder.occurrence.date, reminder.at)

        fun from(intent: Intent): OccurrenceKey? {
            val id = intent.getStringExtra(EXTRA_ROUTINE)
            val original = intent.getStringExtra(EXTRA_ORIGINAL)
            val date = intent.getStringExtra(EXTRA_DATE)
            return if (id == null || original == null || date == null) {
                null
            } else {
                OccurrenceKey(id, LocalDate.parse(original), LocalDate.parse(date), Instant.ofEpochMilli(intent.getLongExtra(EXTRA_AT, 0)))
            }
        }
    }
}

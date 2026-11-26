package de.steppicrew.kladde.routines

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings

/**
 * The sound of all reminders, chosen in the app. Android fixes a channel's sound once it exists,
 * so a new choice replaces the channel with a fresh one. A sound set in Android's settings since
 * then is what the channel has, and wins.
 */
internal class ReminderSound(
    context: Context,
) {
    private val context = context.applicationContext
    private val manager = this.context.getSystemService(NotificationManager::class.java)
    private val prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val channelId: String get() = prefs.getInt(KEY_VERSION, 0).let { if (it == 0) CHANNEL else "$CHANNEL-$it" }

    /** The sound reminders make; null for none. */
    val uri: Uri?
        get() {
            manager.getNotificationChannel(channelId)?.let { return it.sound }
            val stored = prefs.getString(KEY_SOUND, null) ?: return Settings.System.DEFAULT_NOTIFICATION_URI
            return stored.takeIf { it.isNotEmpty() }?.let(Uri::parse)
        }

    /** The sound's name as Android shows it, or null for none. */
    fun title(): String? = uri?.let { RingtoneManager.getRingtone(context, it)?.getTitle(context) }

    /** Reminders make [sound] from now on, or none when null. */
    fun set(sound: Uri?) {
        manager.deleteNotificationChannel(channelId)
        prefs
            .edit()
            .putString(KEY_SOUND, sound?.toString().orEmpty())
            .putInt(KEY_VERSION, prefs.getInt(KEY_VERSION, 0) + 1)
            .apply()
    }

    /** The channel reminders are posted to, made with the chosen sound if it doesn't exist yet. */
    fun ensureChannel(): String {
        val id = channelId
        if (manager.getNotificationChannel(id) == null) {
            val channel =
                NotificationChannel(id, context.getString(R.string.routines_reminder_channel), NotificationManager.IMPORTANCE_HIGH).apply {
                    description = context.getString(R.string.routines_reminder_channel_description)
                    val attributes =
                        AudioAttributes
                            .Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    setSound(uri, attributes)
                }
            manager.createNotificationChannel(channel)
        }
        return id
    }

    private companion object {
        /** Shared with [Reminders]: the app's reminder settings on this device. */
        const val PREFS = "reminders"
        const val KEY_SOUND = "sound"
        const val KEY_VERSION = "channel_version"
        const val CHANNEL = "reminders"
    }
}

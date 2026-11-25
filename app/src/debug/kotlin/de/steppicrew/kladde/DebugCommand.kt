package de.steppicrew.kladde

import android.content.Intent
import android.util.Log

/**
 * Debug builds only: `adb shell am start -n de.steppicrew.kladde.debug/de.steppicrew.kladde.MainActivity -e cmd <cmd>`.
 * Commands: lock, unlock, status. Logs under tag "Kladde" without any user content.
 */
object DebugCommand {
    private const val TAG = "Kladde"

    fun handle(
        app: KladdeApp,
        intent: Intent?,
    ) {
        val cmd = intent?.getStringExtra("cmd") ?: return
        intent.removeExtra("cmd")
        when (cmd) {
            "lock" -> app.vault.lock()
            "unlock" -> app.vault.unlock()
            "status" -> Unit
            else -> Log.w(TAG, "unknown cmd $cmd")
        }
        Log.i(TAG, "cmd $cmd: vault ${if (app.vault.isUnlocked) "unlocked" else "locked"}")
    }
}

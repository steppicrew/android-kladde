package de.steppicrew.kladde

import android.content.Intent
import android.util.Log

/**
 * Debug builds only: `adb shell am start -n de.steppicrew.kladde.debug/de.steppicrew.kladde.MainActivity -e cmd <cmd>`.
 * Commands: lock, unlock, status, tab:entries, tab:routines. Logs under tag "Kladde" without any
 * user content. Returns the tab to show, if the command named one.
 */
object DebugCommand {
    private const val TAG = "Kladde"

    fun handle(
        app: KladdeApp,
        intent: Intent?,
    ): MainTab? {
        val cmd = intent?.getStringExtra("cmd") ?: return null
        intent.removeExtra("cmd")
        val tab = MainTab.entries.firstOrNull { cmd == "tab:${it.name.lowercase()}" }
        when (cmd) {
            "lock" -> app.vault.lock()
            "unlock" -> app.vault.unlock()
            "status" -> Unit
            else -> if (tab == null) Log.w(TAG, "unknown cmd $cmd")
        }
        Log.i(TAG, "cmd $cmd: vault ${if (app.vault.isUnlocked) "unlocked" else "locked"}")
        return tab
    }
}

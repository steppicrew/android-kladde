package de.steppicrew.kladde

import android.content.Intent

/** Release twin of the debug backdoor: does nothing. */
object DebugCommand {
    @Suppress("UNUSED_PARAMETER")
    fun handle(
        app: KladdeApp,
        intent: Intent?,
    ) = Unit
}

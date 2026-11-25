package de.steppicrew.kladde

import de.steppicrew.kladde.model.VaultLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Stands in for the vault until core/crypto exists: unlocked at start, locked only
 * through the debug backdoor, so the locked paths can be tried on a device.
 */
class StubVault : VaultLock {
    private val unlocked = MutableStateFlow(true)

    val state: StateFlow<Boolean> = unlocked

    override val isUnlocked: Boolean get() = unlocked.value

    fun lock() {
        unlocked.value = false
    }

    fun unlock() {
        unlocked.value = true
    }
}

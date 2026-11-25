package de.steppicrew.kladde.model

/**
 * Whether the vault key is in memory. Code that reads or writes user data while locked
 * must not touch the database. Stub until core/crypto exists.
 */
interface VaultLock {
    val isUnlocked: Boolean
}

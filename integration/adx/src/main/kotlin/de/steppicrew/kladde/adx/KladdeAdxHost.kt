package de.steppicrew.kladde.adx

import de.steppicrew.kladde.data.TrackerRepository
import de.steppicrew.kladde.model.VaultLock

/** Implemented by the Application, so [ConnectActivity] reaches the app's singletons. */
interface KladdeAdxHost {
    val vaultLock: VaultLock
    val trackerRepository: TrackerRepository
}

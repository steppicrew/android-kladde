package de.steppicrew.kladde.adx

import de.steppicrew.kladde.data.SubTypeLabelRepository
import de.steppicrew.kladde.data.TrackerRepository
import de.steppicrew.kladde.model.VaultLock

/** Implemented by the Application, so the module's activities and receivers reach the app's singletons. */
interface KladdeAdxHost {
    val vaultLock: VaultLock
    val trackerRepository: TrackerRepository
    val subTypeLabelRepository: SubTypeLabelRepository
}

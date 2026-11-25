package de.steppicrew.kladde

import android.app.Application
import de.steppicrew.adx.receiver.AdxReceiver
import de.steppicrew.kladde.adx.KladdeAdxHost
import de.steppicrew.kladde.adx.KladdeAdxStore
import de.steppicrew.kladde.adx.LabelNotifications
import de.steppicrew.kladde.data.DataPointRepository
import de.steppicrew.kladde.data.KladdeDatabase
import de.steppicrew.kladde.data.RoutineRepository
import de.steppicrew.kladde.data.SubTypeLabelRepository
import de.steppicrew.kladde.data.TrackerRepository

/** Wires the app by hand until DI is set up. */
class KladdeApp :
    Application(),
    KladdeAdxHost {
    val vault = StubVault()
    private val database by lazy { KladdeDatabase.open(this) }
    override val vaultLock get() = vault
    override val trackerRepository by lazy { TrackerRepository(database) }
    val dataPointRepository by lazy { DataPointRepository(database) }
    override val subTypeLabelRepository by lazy { SubTypeLabelRepository(database) }
    val routineRepository by lazy { RoutineRepository(database) }

    override fun onCreate() {
        super.onCreate()
        val labelNotifications = LabelNotifications(this)
        AdxReceiver.init(
            this,
            KladdeAdxStore(vault, trackerRepository, dataPointRepository, subTypeLabelRepository, listener = labelNotifications),
        )
    }
}

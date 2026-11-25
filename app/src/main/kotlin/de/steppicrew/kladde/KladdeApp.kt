package de.steppicrew.kladde

import android.app.Application
import de.steppicrew.adx.receiver.AdxReceiver
import de.steppicrew.kladde.adx.KladdeAdxHost
import de.steppicrew.kladde.adx.KladdeAdxStore
import de.steppicrew.kladde.data.DataPointRepository
import de.steppicrew.kladde.data.KladdeDatabase
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

    override fun onCreate() {
        super.onCreate()
        AdxReceiver.init(this, KladdeAdxStore(vault, trackerRepository, dataPointRepository))
    }
}

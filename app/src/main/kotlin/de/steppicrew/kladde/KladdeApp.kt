package de.steppicrew.kladde

import android.app.Application
import de.steppicrew.adx.receiver.AdxReceiver
import de.steppicrew.kladde.adx.KladdeAdxHost
import de.steppicrew.kladde.adx.KladdeAdxStore
import de.steppicrew.kladde.adx.LabelNotifications
import de.steppicrew.kladde.data.DataPointRepository
import de.steppicrew.kladde.data.KladdeDatabase
import de.steppicrew.kladde.data.RoutineExtraRepository
import de.steppicrew.kladde.data.RoutinePauseRepository
import de.steppicrew.kladde.data.RoutineRepository
import de.steppicrew.kladde.data.SubTypeLabelRepository
import de.steppicrew.kladde.data.TrackerRepository
import de.steppicrew.kladde.routines.Reminders
import de.steppicrew.kladde.routines.RoutinesHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Wires the app by hand until DI is set up. */
class KladdeApp :
    Application(),
    KladdeAdxHost,
    RoutinesHost {
    val vault = StubVault()
    private val database by lazy { KladdeDatabase.open(this) }
    override val vaultLock get() = vault
    override val trackerRepository by lazy { TrackerRepository(database) }
    val dataPointRepository by lazy { DataPointRepository(database) }
    override val subTypeLabelRepository by lazy { SubTypeLabelRepository(database) }
    override val routineRepository by lazy { RoutineRepository(database) }
    override val routinePauseRepository by lazy { RoutinePauseRepository(database) }
    override val routineExtraRepository by lazy { RoutineExtraRepository(database) }
    override val reminders by lazy { Reminders(this, routineRepository, routinePauseRepository, routineExtraRepository) }

    /** Lives as long as the process: keeps the reminder alarms in step with the routines. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        val labelNotifications = LabelNotifications(this)
        AdxReceiver.init(
            this,
            KladdeAdxStore(vault, trackerRepository, dataPointRepository, subTypeLabelRepository, listener = labelNotifications),
        )
        reminders.watch(scope)
    }
}

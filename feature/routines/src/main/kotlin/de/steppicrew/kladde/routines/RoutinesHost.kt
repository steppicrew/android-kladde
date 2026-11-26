package de.steppicrew.kladde.routines

import de.steppicrew.kladde.data.RoutineExtraRepository
import de.steppicrew.kladde.data.RoutinePauseRepository
import de.steppicrew.kladde.data.RoutineRepository

/** What the app gives the routine receivers, which have no screen to be handed it. */
interface RoutinesHost {
    val routineRepository: RoutineRepository
    val routinePauseRepository: RoutinePauseRepository
    val routineExtraRepository: RoutineExtraRepository
    val reminders: Reminders
}

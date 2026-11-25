package de.steppicrew.kladde.data

import de.steppicrew.kladde.model.BlockRule
import de.steppicrew.kladde.model.DataPoint
import de.steppicrew.kladde.model.ExternalSource
import de.steppicrew.kladde.model.OccurrenceOverride
import de.steppicrew.kladde.model.Routine
import de.steppicrew.kladde.model.RoutineCompletion
import de.steppicrew.kladde.model.RoutineExtra
import de.steppicrew.kladde.model.RoutinePause
import de.steppicrew.kladde.model.SubTypeLabel
import de.steppicrew.kladde.model.SubTypeLabelStatus
import de.steppicrew.kladde.model.Tracker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Blocking calls run on the caller's thread; never call them from the main thread. */
class TrackerRepository(
    private val db: KladdeDatabase,
) {
    fun observeAll(): Flow<List<Tracker>> = db.trackers().observeAll().map { list -> list.map { it.toModel() } }

    fun all(): List<Tracker> = db.trackers().all().map { it.toModel() }

    fun find(id: String): Tracker? = db.trackers().find(id)?.toModel()

    fun findBySource(
        packageName: String,
        stream: String,
    ): Tracker? = db.trackers().findBySource(packageName, stream)?.toModel()

    fun save(tracker: Tracker) = db.trackers().save(tracker.toEntity())
}

class DataPointRepository(
    private val db: KladdeDatabase,
) {
    fun observeRecent(limit: Int): Flow<List<DataPoint>> = db.dataPoints().observeRecent(limit).map { list -> list.map { it.toModel() } }

    /** The rev stored for another app's entry, including a tombstone's, or null if it was never seen. */
    fun externalRev(
        packageName: String,
        stream: String,
        externalId: String,
    ): Long? = db.dataPoints().findBySource(packageName, stream, externalId)?.sourceRev

    /**
     * Stores another app's entry in place of what is stored for the same source key.
     * The stored point keeps its own id; [point]'s id is used only for a new one.
     */
    fun upsertExternal(point: DataPoint) {
        val source = requireNotNull(point.source) { "not an external data point" }
        db.runInTransaction {
            val existing = db.dataPoints().findBySource(source.packageName, source.stream, source.externalId)
            db.dataPoints().save(point.copy(id = existing?.id ?: point.id).toEntity())
        }
    }

    /** Turns another app's entry into a tombstone that keeps [source]'s rev; values are cleared. */
    fun deleteExternal(
        newId: String,
        trackerId: String,
        source: ExternalSource,
    ) {
        db.runInTransaction {
            val existing = db.dataPoints().findBySource(source.packageName, source.stream, source.externalId)
            db.dataPoints().save(
                DataPointEntity(
                    id = existing?.id ?: newId,
                    trackerId = existing?.trackerId ?: trackerId,
                    at = existing?.at ?: 0L,
                    zone = existing?.zone ?: "UTC",
                    value = null,
                    high = null,
                    end = null,
                    unit = null,
                    subType = null,
                    note = null,
                    sourcePackage = source.packageName,
                    sourceStream = source.stream,
                    sourceId = source.externalId,
                    sourceRev = source.rev,
                    sourceRaw = null,
                    deleted = true,
                ),
            )
        }
    }
}

/** Labels of sub types and what the user made of them; see docs/decisions/0002. */
class SubTypeLabelRepository(
    private val db: KladdeDatabase,
) {
    fun find(id: String): SubTypeLabel? = db.subTypeLabels().find(id)?.toModel()

    fun findByKey(
        trackerId: String,
        key: String,
    ): SubTypeLabel? = db.subTypeLabels().findByKey(trackerId, key)?.toModel()

    /** Stores [label] over its earlier version; for what the sending app says about it. */
    fun update(label: SubTypeLabel) = db.subTypeLabels().save(label.toEntity())

    fun observeOpen(): Flow<List<SubTypeLabel>> = db.subTypeLabels().observeOpen().map { list -> list.map { it.toModel() } }

    fun observeAll(): Flow<List<SubTypeLabel>> = db.subTypeLabels().observeAll().map { list -> list.map { it.toModel() } }

    fun all(): List<SubTypeLabel> = db.subTypeLabels().all().map { it.toModel() }

    /** Stores [label] unless its tracker already has a record for its key; returns it when it was new. */
    fun recordIfNew(label: SubTypeLabel): SubTypeLabel? = label.takeIf { db.subTypeLabels().insert(it.toEntity()) != -1L }

    /** Leaves the label as it is; its entries just count. */
    fun ignore(id: String) = change(id) { it.copy(status = SubTypeLabelStatus.IGNORED) }

    fun specify(
        id: String,
        displayName: String?,
        defaults: Map<String, Double>,
    ) = change(id) {
        it.copy(status = SubTypeLabelStatus.SPECIFIED, displayName = displayName?.trim()?.ifEmpty { null }, defaults = defaults)
    }

    private fun change(
        id: String,
        update: (SubTypeLabel) -> SubTypeLabel,
    ) = db.runInTransaction {
        db.subTypeLabels().find(id)?.let { db.subTypeLabels().save(update(it.toModel()).toEntity()) }
    }
}

class RoutineRepository(
    private val db: KladdeDatabase,
) {
    /** A routine whose stored rule this version can't read is left out, not dropped. */
    fun observeAll(): Flow<List<Routine>> =
        combine(db.routines().observeAll(), db.scheduleVersions().observeAll()) { routines, versions ->
            val byRoutine = versions.groupBy { it.routineId }
            routines.mapNotNull { it.toModel(byRoutine[it.id].orEmpty()) }
        }

    fun observeCompletions(
        from: LocalDate,
        to: LocalDate,
    ): Flow<List<RoutineCompletion>> =
        db.routines().observeCompletions(from.toString(), to.toString()).map { list ->
            list.map { it.toModel() }
        }

    fun observeRules(): Flow<List<BlockRule>> =
        db.blockRules().observeRules().map { list -> list.map { BlockRule(it.routineId, it.blockerId) } }

    /** Saves [routine] and, in place of its earlier ones, the routines it gives way to. */
    fun save(
        routine: Routine,
        blockers: Set<String> = emptySet(),
    ) = db.runInTransaction {
        db.routines().save(routine.toEntity())
        db.scheduleVersions().deleteAllOf(routine.id)
        db.scheduleVersions().insert(routine.versionEntities())
        db.blockRules().deleteRulesOf(routine.id)
        db.blockRules().insertRules(blockers.map { BlockRuleEntity(routine.id, it) })
    }

    fun observeOverrides(): Flow<List<OccurrenceOverride>> =
        db.overrides().observeAll().map { list ->
            list.map { OccurrenceOverride(it.routineId, LocalDate.parse(it.originalDate), it.newDate?.let(LocalDate::parse)) }
        }

    /**
     * Moves the occurrence planned for [originalDate] to [newDate], or cancels it when that is
     * null; moving it back to its planned day takes the change back.
     */
    fun change(
        routineId: String,
        originalDate: LocalDate,
        newDate: LocalDate?,
    ) = if (newDate == originalDate) {
        restore(routineId, originalDate)
    } else {
        db.overrides().save(OccurrenceOverrideEntity(routineId, originalDate.toString(), newDate?.toString()))
    }

    /** Takes back a move or a cancel: the occurrence is on its planned day again. */
    fun restore(
        routineId: String,
        originalDate: LocalDate,
    ) = db.overrides().delete(routineId, originalDate.toString())

    /**
     * Deletes the routine, what was ticked off for it, its single-day changes and every rule it is
     * part of. It leaves the pauses naming it; one that named only it is deleted, as an empty list
     * would mean all routines.
     */
    fun delete(id: String) =
        db.runInTransaction {
            for (pause in db
                .pauses()
                .all()
                .map { it.toModel() }
                .filter { id in it.routineIds }) {
                val rest = pause.routineIds - id
                if (rest.isEmpty()) db.pauses().delete(pause.id) else db.pauses().save(pause.copy(routineIds = rest).toEntity())
            }
            db.routines().deleteCompletions(id)
            db.overrides().deleteAllOf(id)
            db.extras().removeAllOf(id)
            db.blockRules().deleteRulesWith(id)
            db.scheduleVersions().deleteAllOf(id)
            db.routines().delete(id)
        }

    /** Records done or skipped for one occurrence, in place of an earlier answer. */
    fun complete(completion: RoutineCompletion) = db.routines().saveCompletion(completion.toEntity())

    /** Takes an answer back: the occurrence is open (or missed) again. */
    fun reopen(
        routineId: String,
        date: LocalDate,
    ) = db.routines().deleteCompletion(routineId, date.toString())
}

/** Pauses such as vacations; see [RoutineRepository.delete] for what deleting a routine does to them. */
class RoutinePauseRepository(
    private val db: KladdeDatabase,
) {
    fun observeAll(): Flow<List<RoutinePause>> = db.pauses().observeAll().map { list -> list.map { it.toModel() } }

    fun save(pause: RoutinePause) = db.pauses().save(pause.toEntity())

    fun delete(id: String) = db.pauses().delete(id)
}

/** Extra occurrences on days a routine's schedule doesn't have; [RoutineRepository.delete] removes a routine's. */
class RoutineExtraRepository(
    private val db: KladdeDatabase,
) {
    fun observeAll(): Flow<List<RoutineExtra>> =
        db.extras().observeAll().map { list -> list.map { RoutineExtra(it.routineId, LocalDate.parse(it.date)) } }

    fun add(extra: RoutineExtra) = db.extras().add(RoutineExtraEntity(extra.routineId, extra.date.toString()))

    fun remove(extra: RoutineExtra) = db.extras().remove(extra.routineId, extra.date.toString())

    /** Moves an extra to [newDate], keeping what was ticked off for it under the old day. */
    fun move(
        extra: RoutineExtra,
        newDate: LocalDate,
    ) = db.runInTransaction {
        remove(extra)
        add(extra.copy(date = newDate))
    }
}

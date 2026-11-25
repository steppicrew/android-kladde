package de.steppicrew.kladde.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** Plain Room for now; moves onto SQLCipher with the vault key once core/crypto exists. */
@Database(
    entities = [
        TrackerEntity::class,
        DataPointEntity::class,
        SubTypeLabelEntity::class,
        RoutineEntity::class,
        RoutineCompletionEntity::class,
        BlockRuleEntity::class,
        OccurrenceOverrideEntity::class,
        RoutinePauseEntity::class,
        RoutineExtraEntity::class,
        ScheduleVersionEntity::class,
    ],
    version = 11,
    // Each step only adds: 2 sub_type_labels, 3 columns to it, 4 the routine tables, 5 block_rules,
    // 6 occurrence_overrides, 7 routine_pauses, 8 routine_extras, 9 routine_schedule_versions,
    // 10 routines.ignores_pauses, 11 occurrence_overrides.new_time and routine_completions.done_at.
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8),
        AutoMigration(from = 8, to = 9),
        AutoMigration(from = 9, to = 10),
        AutoMigration(from = 10, to = 11),
    ],
)
abstract class KladdeDatabase : RoomDatabase() {
    internal abstract fun trackers(): TrackerDao

    internal abstract fun dataPoints(): DataPointDao

    internal abstract fun subTypeLabels(): SubTypeLabelDao

    internal abstract fun routines(): RoutineDao

    internal abstract fun blockRules(): BlockRuleDao

    internal abstract fun overrides(): OccurrenceOverrideDao

    internal abstract fun pauses(): RoutinePauseDao

    internal abstract fun extras(): RoutineExtraDao

    internal abstract fun scheduleVersions(): ScheduleVersionDao

    companion object {
        fun open(context: Context): KladdeDatabase = Room.databaseBuilder(context, KladdeDatabase::class.java, "kladde.db").build()

        /** For tests. */
        fun inMemory(context: Context): KladdeDatabase =
            Room.inMemoryDatabaseBuilder(context, KladdeDatabase::class.java).allowMainThreadQueries().build()
    }
}

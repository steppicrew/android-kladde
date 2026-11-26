package de.steppicrew.kladde.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
        RoutineGroupEntity::class,
    ],
    version = 16,
    // Each step only adds: 2 sub_type_labels, 3 columns to it, 4 the routine tables, 5 block_rules,
    // 6 occurrence_overrides, 7 routine_pauses, 8 routine_extras, 9 routine_schedule_versions,
    // 10 routines.ignores_pauses, 11 occurrence_overrides.new_time and routine_completions.done_at,
    // 12 routines.time_zone, 13 routine_groups, 14 routine_pauses.groups.
    // 15 changes no table but turns the schedules around ([MIGRATION_14_15]); 16 adds routines.remind_before.
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
        AutoMigration(from = 11, to = 12),
        AutoMigration(from = 12, to = 13),
        AutoMigration(from = 13, to = 14),
        AutoMigration(from = 15, to = 16),
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

    internal abstract fun groups(): RoutineGroupDao

    companion object {
        fun open(context: Context): KladdeDatabase =
            Room.databaseBuilder(context, KladdeDatabase::class.java, "kladde.db").addMigrations(MIGRATION_14_15).build()

        /** For tests. */
        fun inMemory(context: Context): KladdeDatabase =
            Room.inMemoryDatabaseBuilder(context, KladdeDatabase::class.java).allowMainThreadQueries().build()
    }
}

/**
 * Up to 14 a routine's row held its current schedule and routine_schedule_versions the earlier
 * ones; from 15 the row holds the first (base) schedule and the table the later changes. For a
 * routine with versions, the row becomes a version and the earliest version becomes the row.
 */
internal val MIGRATION_14_15 =
    object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val changed = "id IN (SELECT routine_id FROM routine_schedule_versions)"
            db.execSQL(
                "INSERT INTO routine_schedule_versions (routine_id, valid_from, rrule, time) " +
                    "SELECT id, start_date, rrule, time FROM routines WHERE $changed",
            )
            val earliest =
                "FROM routine_schedule_versions v WHERE v.routine_id = routines.id ORDER BY v.valid_from LIMIT 1"
            db.execSQL(
                "UPDATE routines SET start_date = (SELECT valid_from $earliest), rrule = (SELECT rrule $earliest), " +
                    "time = (SELECT time $earliest) WHERE $changed",
            )
            db.execSQL(
                "DELETE FROM routine_schedule_versions WHERE valid_from = " +
                    "(SELECT MIN(v.valid_from) FROM routine_schedule_versions v WHERE v.routine_id = routine_schedule_versions.routine_id)",
            )
        }
    }

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
    ],
    version = 6,
    // Each step only adds: 2 sub_type_labels, 3 columns to it, 4 the routine tables, 5 block_rules,
    // 6 occurrence_overrides.
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
    ],
)
abstract class KladdeDatabase : RoomDatabase() {
    internal abstract fun trackers(): TrackerDao

    internal abstract fun dataPoints(): DataPointDao

    internal abstract fun subTypeLabels(): SubTypeLabelDao

    internal abstract fun routines(): RoutineDao

    internal abstract fun blockRules(): BlockRuleDao

    internal abstract fun overrides(): OccurrenceOverrideDao

    companion object {
        fun open(context: Context): KladdeDatabase = Room.databaseBuilder(context, KladdeDatabase::class.java, "kladde.db").build()

        /** For tests. */
        fun inMemory(context: Context): KladdeDatabase =
            Room.inMemoryDatabaseBuilder(context, KladdeDatabase::class.java).allowMainThreadQueries().build()
    }
}

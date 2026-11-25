package de.steppicrew.kladde.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
internal interface TrackerDao {
    @Query("SELECT * FROM trackers ORDER BY name")
    fun observeAll(): Flow<List<TrackerEntity>>

    @Query("SELECT * FROM trackers ORDER BY name")
    fun all(): List<TrackerEntity>

    @Query("SELECT * FROM trackers WHERE id = :id")
    fun find(id: String): TrackerEntity?

    @Query("SELECT * FROM trackers WHERE source_package = :packageName AND source_stream = :stream ORDER BY created_at LIMIT 1")
    fun findBySource(
        packageName: String,
        stream: String,
    ): TrackerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun save(tracker: TrackerEntity)
}

@Dao
internal interface SubTypeLabelDao {
    /** Returns -1 when the tracker already has a record for the label. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(label: SubTypeLabelEntity): Long

    @Query("SELECT * FROM sub_type_labels WHERE id = :id")
    fun find(id: String): SubTypeLabelEntity?

    @Query("SELECT * FROM sub_type_labels WHERE tracker_id = :trackerId AND label_key = :key")
    fun findByKey(
        trackerId: String,
        key: String,
    ): SubTypeLabelEntity?

    @Query("SELECT * FROM sub_type_labels WHERE status = 'OPEN' ORDER BY created_at")
    fun observeOpen(): Flow<List<SubTypeLabelEntity>>

    @Query("SELECT * FROM sub_type_labels")
    fun observeAll(): Flow<List<SubTypeLabelEntity>>

    @Query("SELECT * FROM sub_type_labels")
    fun all(): List<SubTypeLabelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun save(label: SubTypeLabelEntity)
}

@Dao
internal interface DataPointDao {
    @Query("SELECT * FROM data_points WHERE deleted = 0 ORDER BY at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<DataPointEntity>>

    @Query("SELECT * FROM data_points WHERE source_package = :packageName AND source_stream = :stream AND source_id = :sourceId")
    fun findBySource(
        packageName: String,
        stream: String,
        sourceId: String,
    ): DataPointEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun save(point: DataPointEntity)
}

@Dao
internal interface RoutineDao {
    @Query("SELECT * FROM routines ORDER BY title")
    fun observeAll(): Flow<List<RoutineEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun save(routine: RoutineEntity)

    @Query("DELETE FROM routines WHERE id = :id")
    fun delete(id: String)

    @Query("SELECT * FROM routine_completions WHERE date BETWEEN :from AND :to")
    fun observeCompletions(
        from: String,
        to: String,
    ): Flow<List<RoutineCompletionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveCompletion(completion: RoutineCompletionEntity)

    @Query("DELETE FROM routine_completions WHERE routine_id = :routineId AND date = :date")
    fun deleteCompletion(
        routineId: String,
        date: String,
    )

    @Query("DELETE FROM routine_completions WHERE routine_id = :routineId")
    fun deleteCompletions(routineId: String)
}

@Dao
internal interface BlockRuleDao {
    @Query("SELECT * FROM block_rules")
    fun observeRules(): Flow<List<BlockRuleEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertRules(rules: List<BlockRuleEntity>)

    @Query("DELETE FROM block_rules WHERE routine_id = :routineId")
    fun deleteRulesOf(routineId: String)

    @Query("DELETE FROM block_rules WHERE routine_id = :id OR blocker_id = :id")
    fun deleteRulesWith(id: String)
}

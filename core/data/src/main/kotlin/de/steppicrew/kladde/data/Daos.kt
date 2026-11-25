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

package de.steppicrew.kladde.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "trackers",
    indices = [Index("source_package", "source_stream")],
)
internal data class TrackerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val unit: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    /** Sub types joined by [SUB_TYPE_SEPARATOR]; empty when there are none. */
    @ColumnInfo(name = "sub_types") val subTypes: String,
    @ColumnInfo(name = "scale_min") val scaleMin: Long?,
    @ColumnInfo(name = "scale_max") val scaleMax: Long?,
    @ColumnInfo(name = "source_package") val sourcePackage: String?,
    @ColumnInfo(name = "source_stream") val sourceStream: String?,
)

/**
 * A data point, or the tombstone of a deleted one from another app ([deleted]): the
 * tombstone keeps the source key and rev, so a late, older upsert can't bring it back.
 */
@Entity(
    tableName = "data_points",
    indices = [
        Index("tracker_id", "at"),
        Index("source_package", "source_stream", "source_id", unique = true),
    ],
)
internal data class DataPointEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "tracker_id") val trackerId: String,
    val at: Long,
    val zone: String,
    val value: Double?,
    val high: Double?,
    val end: Long?,
    val unit: String?,
    @ColumnInfo(name = "sub_type") val subType: String?,
    val note: String?,
    @ColumnInfo(name = "source_package") val sourcePackage: String?,
    @ColumnInfo(name = "source_stream") val sourceStream: String?,
    @ColumnInfo(name = "source_id") val sourceId: String?,
    @ColumnInfo(name = "source_rev") val sourceRev: Long?,
    @ColumnInfo(name = "source_raw") val sourceRaw: String?,
    val deleted: Boolean,
)

internal const val SUB_TYPE_SEPARATOR = "\u001F"

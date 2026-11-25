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

/** What the user made of one sub type of a tracker; see docs/decisions/0002. */
@Entity(
    tableName = "sub_type_labels",
    indices = [Index("tracker_id", "label_key", unique = true), Index("status")],
)
internal data class SubTypeLabelEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "tracker_id") val trackerId: String,
    @ColumnInfo(name = "label_key") val key: String,
    val status: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "display_name") val displayName: String?,
    /** Default values as a JSON object of attr key to number. */
    val defaults: String,
    /** Attr keys joined by [SUB_TYPE_SEPARATOR]; empty when there are none. */
    @ColumnInfo(name = "attr_keys") val attrKeys: String,
    @ColumnInfo(name = "source_package") val sourcePackage: String?,
    @ColumnInfo(name = "source_stream") val sourceStream: String?,
    @ColumnInfo(name = "sender_label") val senderLabel: String?,
    /** The sending app's defaults as a JSON object of attr key to number. */
    @ColumnInfo(name = "sender_defaults", defaultValue = "{}") val senderDefaults: String,
)

internal const val SUB_TYPE_SEPARATOR = "\u001F"

/** A routine; its schedule is an RRULE, its time "HH:mm" in floating wall-clock time. */
@Entity(tableName = "routines")
internal data class RoutineEntity(
    @PrimaryKey val id: String,
    val title: String,
    val rrule: String,
    /** ISO date, yyyy-MM-dd. */
    @ColumnInfo(name = "start_date") val startDate: String,
    val time: String?,
    val checkable: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** Done or skipped for one occurrence; the date is ISO yyyy-MM-dd, so text order is date order. */
@Entity(tableName = "routine_completions", primaryKeys = ["routine_id", "date"], indices = [Index("date")])
internal data class RoutineCompletionEntity(
    @ColumnInfo(name = "routine_id") val routineId: String,
    val date: String,
    val status: String,
    @ColumnInfo(name = "recorded_at") val recordedAt: Long,
    val zone: String,
)

/** "routine_id doesn't take place on days when blocker_id does." */
@Entity(tableName = "block_rules", primaryKeys = ["routine_id", "blocker_id"], indices = [Index("blocker_id")])
internal data class BlockRuleEntity(
    @ColumnInfo(name = "routine_id") val routineId: String,
    @ColumnInfo(name = "blocker_id") val blockerId: String,
)

/** One occurrence moved to new_date, or cancelled when that is null; dates are ISO yyyy-MM-dd. */
@Entity(tableName = "occurrence_overrides", primaryKeys = ["routine_id", "original_date"])
internal data class OccurrenceOverrideEntity(
    @ColumnInfo(name = "routine_id") val routineId: String,
    @ColumnInfo(name = "original_date") val originalDate: String,
    @ColumnInfo(name = "new_date") val newDate: String?,
)

/** A pause from–until (ISO dates, until null = open); routine_ids joined by [SUB_TYPE_SEPARATOR], empty = all. */
@Entity(tableName = "routine_pauses")
internal data class RoutinePauseEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "from_date") val fromDate: String,
    @ColumnInfo(name = "until_date") val untilDate: String?,
    @ColumnInfo(name = "routine_ids") val routineIds: String,
    val note: String,
)

/** One more occurrence of a routine on an ISO date its schedule doesn't have. */
@Entity(tableName = "routine_extras", primaryKeys = ["routine_id", "date"])
internal data class RoutineExtraEntity(
    @ColumnInfo(name = "routine_id") val routineId: String,
    val date: String,
)

/** A schedule a routine had before a change: from valid_from (ISO date) until the next one starts. */
@Entity(tableName = "routine_schedule_versions", primaryKeys = ["routine_id", "valid_from"])
internal data class ScheduleVersionEntity(
    @ColumnInfo(name = "routine_id") val routineId: String,
    @ColumnInfo(name = "valid_from") val validFrom: String,
    val rrule: String,
    val time: String?,
)

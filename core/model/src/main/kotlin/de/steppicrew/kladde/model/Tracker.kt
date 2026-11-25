package de.steppicrew.kladde.model

import java.time.Instant

enum class TrackerType {
    NUMBER,
    MULTI_NUMBER,
    COUNTER,
    DURATION,
    SCALE,
    BOOLEAN,
    SHORT_TEXT,
    CHOICE,
    RANGE,
}

/** A user-defined tracker. [unit] is stored explicitly; a tracker without one has no unit. */
data class Tracker(
    val id: String,
    val name: String,
    val type: TrackerType,
    val unit: String?,
    val createdAt: Instant,
    /** Presets such as "Pils", "Weizen" for a beer counter, in the order they were added. */
    val subTypes: List<String> = emptyList(),
    val scaleMin: Long? = null,
    val scaleMax: Long? = null,
) {
    /** This tracker with [subType] added at the end, or unchanged when it is null or known. */
    fun withSubType(subType: String?): Tracker = if (subType == null || subType in subTypes) this else copy(subTypes = subTypes + subType)
}

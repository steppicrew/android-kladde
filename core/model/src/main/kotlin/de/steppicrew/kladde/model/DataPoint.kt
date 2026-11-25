package de.steppicrew.kladde.model

import java.time.Instant
import java.time.ZoneId

/** Where a data point came from when another app logged it. */
data class ExternalSource(
    /** The sending app's package; identity was checked by its signing certificate. */
    val packageName: String,
    val stream: String,
    /** The sender's own id for the event; unique per (packageName, stream). */
    val externalId: String,
    /** The sender's revision; a stored point only ever moves to a higher one. */
    val rev: Long,
    /** The entry as the sender sent it (ADX JSON), so attrs and fields we don't map aren't lost. */
    val raw: String? = null,
)

/**
 * One measurement or event of a tracker. Stored as a UTC instant plus the zone it was
 * recorded in, because the user's local day matters. [unit] travels with the value.
 */
data class DataPoint(
    val id: String,
    val trackerId: String,
    val at: Instant,
    val zone: ZoneId,
    val value: Double? = null,
    /** Upper bound of a range value; [value] is the lower bound. */
    val high: Double? = null,
    /** End of a duration or time range. */
    val end: Instant? = null,
    val unit: String? = null,
    val subType: String? = null,
    val note: String? = null,
    val source: ExternalSource? = null,
)

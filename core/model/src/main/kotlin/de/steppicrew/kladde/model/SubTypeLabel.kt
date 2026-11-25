package de.steppicrew.kladde.model

import java.time.Instant

/** What the user made of a label so far; see docs/decisions/0002. */
enum class SubTypeLabelStatus {
    /** Seen in an entry, not looked at yet. */
    OPEN,

    /** Looked at and left as it is: entries just count. */
    IGNORED,

    /** Named and, where it has any, given default values. */
    SPECIFIED,
}

/**
 * The user's view of one sub type of a tracker, such as `beerSmall` sent by another app.
 * [key] is the label as the entries carry it and never changes; [displayName] is what Kladde shows.
 */
data class SubTypeLabel(
    val id: String,
    val trackerId: String,
    val key: String,
    val status: SubTypeLabelStatus,
    val createdAt: Instant,
    val displayName: String? = null,
    /** Values by attr key (e.g. `intake.abv_pct`) for entries that don't carry their own. */
    val defaults: Map<String, Double> = emptyMap(),
    /** The attr keys the sending app said this tracker's entries may carry. */
    val attrKeys: List<String> = emptyList(),
    /** The app whose entry brought the label, if one did. */
    val sourcePackage: String? = null,
) {
    /** What to show for the label: its display name, or the key while it has none. */
    val shownName: String get() = displayName?.takeIf { it.isNotBlank() } ?: key
}

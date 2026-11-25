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

    /** The sending app named it and gave every value Kladde would ask for: nothing to ask. */
    DESCRIBED,
}

/**
 * The user's view of one sub type of a tracker, such as `beerSmall` sent by another app.
 * [key] is the label as the entries carry it and never changes. What the user entered in Kladde
 * ([displayName], [defaults]) beats what the sending app said ([senderLabel], [senderDefaults]).
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
    /** That app's stream the label came in on. */
    val sourceStream: String? = null,
    /** The sending app's name for the label, in its current language. */
    val senderLabel: String? = null,
    /** The sending app's default values for the label, by attr key. */
    val senderDefaults: Map<String, Double> = emptyMap(),
) {
    /** What to show for the label: the user's name, else the sending app's, else the key. */
    val shownName: String get() = listOfNotNull(displayName, senderLabel).firstOrNull { it.isNotBlank() } ?: key
}

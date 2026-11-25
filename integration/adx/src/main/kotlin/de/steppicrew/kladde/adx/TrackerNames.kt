package de.steppicrew.kladde.adx

import de.steppicrew.adx.AdxStreamProposal
import de.steppicrew.kladde.model.SubTypeLabel
import de.steppicrew.kladde.model.SubTypeLabelStatus
import de.steppicrew.kladde.model.Tracker

/**
 * What the approval screen calls each existing tracker, by id: its name, and for a name
 * several trackers share, also the app it came from ([withApp] formats that), so the options
 * can be told apart. [senders] maps package names to app names.
 */
internal fun trackerLabels(
    trackers: List<Tracker>,
    senders: Map<String, String>,
    withApp: (name: String, app: String) -> String,
): Map<String, String> {
    val shared = trackers.groupBy { normalized(it.name) }.filterValues { it.size > 1 }.keys
    return trackers.associate { tracker ->
        val app = tracker.source?.let { senders[it.packageName] ?: it.packageName }
        tracker.id to if (normalized(tracker.name) in shared && app != null) withApp(tracker.name, app) else tracker.name
    }
}

/**
 * First names for the proposals that start as new trackers, by stream key: the proposed name
 * while no tracker has it, else one with the sender's name added, and unique among themselves.
 */
internal fun defaultNames(
    proposals: List<AdxStreamProposal>,
    senderLabel: String,
    trackers: List<Tracker>,
): Map<String, String> {
    val taken = trackers.mapTo(HashSet()) { normalized(it.name) }
    return proposals.associate { proposal ->
        val name = uniqueName(proposal.name, senderLabel, taken)
        taken += normalized(name)
        proposal.key to name
    }
}

/** What is wrong with [name] for a new tracker, given the names [others] already have; null when nothing. */
internal fun nameProblem(
    name: String,
    others: Collection<String>,
): NameProblem? =
    when {
        name.isBlank() -> NameProblem.EMPTY
        others.any { normalized(it) == normalized(name) } -> NameProblem.TAKEN
        else -> null
    }

/**
 * The names the other entry types of a label's tracker go by, by label id: those already
 * looked at, which keep their names. A label's own name must differ from them.
 */
internal fun otherLabelNames(all: List<SubTypeLabel>): Map<String, List<String>> {
    val settled = all.filter { it.status != SubTypeLabelStatus.OPEN }
    return all.associate { label ->
        label.id to settled.filter { it.trackerId == label.trackerId && it.id != label.id }.map { it.shownName }
    }
}

/**
 * First names for [open] labels, by id: the key while no other entry type of the tracker goes
 * by it, else the key with a number; unique among themselves too. [others] is [otherLabelNames].
 */
internal fun presetLabelNames(
    open: List<SubTypeLabel>,
    others: Map<String, List<String>>,
): Map<String, String> {
    val given = HashMap<String, MutableSet<String>>()
    return open.associate { label ->
        val taken = given.getOrPut(label.trackerId) { HashSet() }
        val name =
            generateSequence(1) { it + 1 }
                .map { n -> if (n == 1) label.key else "${label.key} $n" }
                .first { candidate -> normalized(candidate) !in taken && nameProblem(candidate, others[label.id].orEmpty()) == null }
        taken += normalized(name)
        label.id to name
    }
}

private fun uniqueName(
    name: String,
    senderLabel: String,
    taken: Set<String>,
): String =
    generateSequence(1) { it + 1 }
        .map { n ->
            when (n) {
                1 -> name
                2 -> "$name ($senderLabel)"
                else -> "$name ($senderLabel) ${n - 1}"
            }
        }.first { normalized(it) !in taken }

private fun normalized(name: String) = name.trim().lowercase()

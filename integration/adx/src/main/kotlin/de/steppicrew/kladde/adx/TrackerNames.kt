package de.steppicrew.kladde.adx

import de.steppicrew.adx.AdxStreamProposal
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

package de.steppicrew.kladde.routines

import de.steppicrew.kladde.model.BlockRule
import de.steppicrew.kladde.model.Recurrence
import de.steppicrew.kladde.model.Routine
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** What the routine form holds while it is being filled in. */
internal data class RoutineDraft(
    val title: String = "",
    /** The schedule cards; the earliest is the base. Kept in the order they were added, shown by date. */
    val schedules: List<ScheduleDraft>,
    val checkable: Boolean = true,
    /** Goes on during pauses for all routines ([Routine.ignoresPauses]). */
    val ignoresPauses: Boolean = false,
    /** A zone ID when the time is bound to a zone; null for wherever the device is. */
    val timeZone: String? = null,
    /** The ids of the routines this one gives way to. */
    val blockers: Set<String> = emptySet(),
    /** Group names such as "Arbeit" ([Routine.groups]). */
    val groups: Set<String> = emptySet(),
    /** Minutes before the time a reminder comes; null for none ([Routine.remindBefore]). */
    val remindBefore: Int? = null,
) {
    /** Whether a card starts on the same day as another one. */
    fun sharesDay(card: ScheduleDraft): Boolean = schedules.count { it.validFrom == card.validFrom } > 1

    val isComplete: Boolean
        get() = title.isNotBlank() && schedules.isNotEmpty() && schedules.all { it.recurrence != null && !sharesDay(it) }

    /** Some card has a time, so the time zone setting means something. */
    val hasTime: Boolean get() = schedules.any { it.time != null }

    /** The cards by start date, with their place in [schedules]. */
    val byDate: List<IndexedValue<ScheduleDraft>> get() = schedules.withIndex().sortedBy { it.value.validFrom }

    /**
     * The cards to show unless the earlier ones are asked for: the one in effect on [today] and the
     * later ones; all of them when none is in effect yet.
     */
    fun currentAndLater(today: LocalDate): List<IndexedValue<ScheduleDraft>> {
        val cutoff = byDate.lastOrNull { !it.value.validFrom.isAfter(today) }?.value?.validFrom ?: return byDate
        return byDate.filter { !it.value.validFrom.isBefore(cutoff) }
    }

    /** The past stays as it was: a card that started before [today] can't be changed or deleted. */
    fun isPast(
        card: ScheduleDraft,
        today: LocalDate,
    ): Boolean = card.validFrom.isBefore(today)

    /** The card in effect on [today], if it started before then; changing it means a new card from today on. */
    fun running(today: LocalDate): IndexedValue<ScheduleDraft>? =
        byDate.lastOrNull { !it.value.validFrom.isAfter(today) }?.takeIf { isPast(it.value, today) }

    fun replace(
        index: Int,
        card: ScheduleDraft,
    ) = copy(schedules = schedules.toMutableList().also { it[index] = card })

    fun remove(index: Int) = copy(schedules = schedules.filterIndexed { i, _ -> i != index })

    /**
     * A new card from [on], or the day after the last card when one starts then already, as a copy
     * of the schedule in effect that day, so only what differs needs changing.
     */
    fun added(on: LocalDate): RoutineDraft {
        val taken = schedules.map { it.validFrom }.toSet()
        val day = if (on in taken) taken.max().plusDays(1) else on
        val inEffect = byDate.lastOrNull { !it.value.validFrom.isAfter(day) }?.value ?: byDate.first().value
        return copy(schedules = schedules + inEffect.copy(validFrom = day))
    }

    /**
     * The routine this draft makes, in place of [editing] if given, keeping its id and creation
     * time. The earliest card is the base; a card that repeats the one before is dropped.
     */
    fun toRoutine(
        id: String,
        now: Instant,
        editing: Routine? = null,
    ): Routine? {
        val versions = schedules.mapNotNull { it.toVersion() }.takeIf { isComplete && it.size == schedules.size } ?: return null
        val routine = editing ?: Routine(id, title, Recurrence.Daily, versions.minOf { it.validFrom }, null, checkable, now)
        return routine.withSchedules(versions).copy(
            title = title.trim(),
            checkable = checkable,
            ignoresPauses = ignoresPauses,
            groups = groups.map { it.trim() }.filterTo(HashSet()) { it.isNotEmpty() },
            // Without a time there is nothing for a zone to bind.
            timeZone = timeZone?.takeIf { hasTime }?.let(ZoneId::of),
            // Likewise for a reminder.
            remindBefore = remindBefore?.takeIf { hasTime }?.let { Duration.ofMinutes(it.toLong()) },
        )
    }

    companion object {
        /** A new routine: one card, every day from [today], reminding at its time once it has one. */
        fun new(today: LocalDate) = RoutineDraft(schedules = listOf(ScheduleDraft(today)), remindBefore = 0)

        /** The form for an existing [routine], with what it gives way to under [rules]. */
        fun of(
            routine: Routine,
            rules: List<BlockRule> = emptyList(),
        ) = RoutineDraft(
            title = routine.title,
            schedules = routine.schedules.map(ScheduleDraft::of),
            checkable = routine.checkable,
            ignoresPauses = routine.ignoresPauses,
            timeZone = routine.timeZone?.id,
            groups = routine.groups,
            remindBefore = routine.remindBefore?.toMinutes()?.toInt(),
            blockers = rules.filter { it.routineId == routine.id }.mapTo(HashSet()) { it.blockerId },
        )
    }
}

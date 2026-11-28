// Made-up example data: its times and dates are the point, not magic numbers.
@file:Suppress("MagicNumber")

package de.steppicrew.kladde

import android.util.Log
import de.steppicrew.kladde.model.CompletionStatus
import de.steppicrew.kladde.model.DataPoint
import de.steppicrew.kladde.model.ExternalSource
import de.steppicrew.kladde.model.NthWeekday
import de.steppicrew.kladde.model.Recurrence
import de.steppicrew.kladde.model.Routine
import de.steppicrew.kladde.model.RoutineCompletion
import de.steppicrew.kladde.model.RoutinePause
import de.steppicrew.kladde.model.Tracker
import de.steppicrew.kladde.model.TrackerSource
import de.steppicrew.kladde.model.TrackerType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.MonthDay
import java.time.ZoneId

/**
 * Debug builds only: made-up routines and entries for the Play Store screenshots
 * (`scripts/play-screenshots.sh`, on an emulator). Refuses to touch an app that has data, so it
 * can never mix into or overwrite real data on a phone.
 */
object DemoData {
    private const val TAG = "Kladde"
    private const val BEER_COUNTER = "de.steppicrew.beercounter"

    /** How long ago the routines started; every day since is ticked off. */
    private const val DAYS = 14L

    fun fill(
        app: KladdeApp,
        german: Boolean,
    ) {
        Thread {
            if (runBlocking { app.routineRepository.observeAll().first() }.isNotEmpty() || app.trackerRepository.all().isNotEmpty()) {
                Log.w(TAG, "demo: refused, the app has data")
                return@Thread
            }
            Texts(german).fill(app)
            Log.i(TAG, "demo: filled")
        }.start()
    }

    private class Texts(
        german: Boolean,
    ) {
        val sport = if (german) "Sport" else "Workout"
        val choir = if (german) "Chorprobe" else "Choir practice"
        val pressure = if (german) "Blutdruck messen" else "Check blood pressure"
        val reading = if (german) "Lesen" else "Reading"
        val bins = if (german) "Mülltonnen raus" else "Put the bins out"
        val drinks = if (german) "Getränke" else "Drinks"
        val holiday = if (german) "Weihnachten" else "Christmas"
        val beer = if (german) "Pils" else "Lager"
        val water = if (german) "Wasser" else "Water"

        private val zone = ZoneId.systemDefault()
        private val today = LocalDate.now()
        private val created = Instant.now()

        fun fill(app: KladdeApp) {
            routines(app)
            ticks(app)
            drinks(app)
        }

        private fun routines(app: KladdeApp) {
            val start = today.minusDays(DAYS)
            val created = Instant.now()

            fun routine(
                id: String,
                title: String,
                recurrence: Recurrence,
                time: LocalTime?,
                checkable: Boolean = true,
            ) = Routine(id, title, recurrence, start, time, checkable, created, remindBefore = Duration.ZERO)

            val routines =
                listOf(
                    routine("sport", sport, Recurrence.Daily, LocalTime.of(18, 0)),
                    routine("choir", choir, Recurrence.Weekly(setOf(DayOfWeek.WEDNESDAY)), LocalTime.of(19, 30), checkable = false),
                    routine("pressure", pressure, Recurrence.Daily, LocalTime.of(8, 0)),
                    routine("reading", reading, Recurrence.Daily, LocalTime.of(21, 30)).copy(remindBefore = null),
                    routine(
                        "bins",
                        bins,
                        Recurrence.MonthlyByWeekday(setOf(NthWeekday(1, DayOfWeek.MONDAY), NthWeekday(3, DayOfWeek.MONDAY))),
                        null,
                    ),
                    routine(
                        "aoc",
                        "Advent of Code",
                        Recurrence.Yearly(MonthDay.of(Month.DECEMBER, 1), MonthDay.of(Month.DECEMBER, 12)),
                        LocalTime.of(6, 0),
                    ).copy(timeZone = ZoneId.of("Europe/Berlin")),
                )
            routines.forEach { app.routineRepository.save(it, if (it.id == "sport") setOf("choir") else emptySet()) }
            app.routinePauseRepository.save(
                RoutinePause("holiday", LocalDate.of(today.year, 12, 24), LocalDate.of(today.year, 12, 26), note = holiday),
            )
        }

        private fun ticks(app: KladdeApp) {
            // Every day since the start ticked off, a workout skipped now and then.
            for (daysAgo in 1L..DAYS) {
                val day = today.minusDays(daysAgo)

                fun done(
                    id: String,
                    time: LocalTime,
                    status: CompletionStatus = CompletionStatus.DONE,
                ) {
                    val at = day.atTime(time).atZone(zone).toInstant()
                    app.routineRepository.complete(RoutineCompletion(id, day, status, at, zone, at))
                }
                done("pressure", LocalTime.of(8, 5))
                if (day.dayOfWeek != DayOfWeek.WEDNESDAY) {
                    done("sport", LocalTime.of(18, 40), if (daysAgo % 6 == 3L) CompletionStatus.SKIPPED else CompletionStatus.DONE)
                }
                done("reading", LocalTime.of(22, 10))
                if (day.dayOfWeek == DayOfWeek.MONDAY && (day.dayOfMonth - 1) / 7 in setOf(0, 2)) {
                    done("bins", LocalTime.of(7, 30))
                }
            }
        }

        private fun drinks(app: KladdeApp) {
            // A few drinks from Beer Counter, as they arrive through ADX.
            val tracker =
                Tracker(
                    "drinks",
                    drinks,
                    TrackerType.COUNTER,
                    null,
                    created,
                    listOf(beer, water),
                    source = TrackerSource(BEER_COUNTER, "drinks"),
                )
            app.trackerRepository.save(tracker)
            listOf(1L to beer, 1L to water, 2L to beer, 3L to water).forEachIndexed { index, (daysAgo, sub) ->
                val at =
                    today
                        .minusDays(daysAgo)
                        .atTime(LocalTime.of(19 + index % 2, 15 * index % 60))
                        .atZone(zone)
                        .toInstant()
                app.dataPointRepository.upsertExternal(
                    DataPoint(
                        "drink-$index",
                        tracker.id,
                        at,
                        zone,
                        value = 1.0,
                        subType = sub,
                        source = ExternalSource(BEER_COUNTER, "drinks", "demo-$index", 1),
                    ),
                )
            }
        }
    }
}

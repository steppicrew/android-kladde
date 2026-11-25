package de.steppicrew.kladde.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), KladdeDatabase::class.java)

    @Test
    fun `1 to 2 keeps trackers and entries and adds an empty label table`() {
        helper.createDatabase(DB, 1).use { db ->
            db.execSQL(
                "INSERT INTO trackers (id, name, type, unit, created_at, sub_types, scale_min, scale_max, source_package, source_stream) " +
                    "VALUES ('t1', 'Beer', 'COUNTER', 'count', 0, 'Pils', NULL, NULL, 'beer.app', 'drinks.beer')",
            )
            db.execSQL(
                "INSERT INTO data_points (id, tracker_id, at, zone, value, high, `end`, unit, sub_type, note, " +
                    "source_package, source_stream, source_id, source_rev, source_raw, deleted) " +
                    "VALUES ('p1', 't1', 0, 'UTC', 1.0, NULL, NULL, 'count', 'Pils', NULL, 'beer.app', 'drinks.beer', 'e1', 1, '{}', 0)",
            )
        }

        helper.runMigrationsAndValidate(DB, 2, true).use { db ->
            assertEquals(1, db.count("trackers"))
            assertEquals(1, db.count("data_points"))
            assertEquals(0, db.count("sub_type_labels"))
        }
    }

    @Test
    fun `2 to 3 keeps label records and gives them empty sender columns`() {
        helper.createDatabase(DB, 2).use { db ->
            db.execSQL(
                "INSERT INTO sub_type_labels " +
                    "(id, tracker_id, label_key, status, created_at, display_name, defaults, attr_keys, source_package) " +
                    "VALUES ('l1', 't1', 'Pils', 'SPECIFIED', 0, NULL, '{\"intake.abv_pct\":5.0}', '', 'beer.app')",
            )
        }

        helper.runMigrationsAndValidate(DB, 3, true).use { db ->
            db.query("SELECT status, defaults, sender_label, sender_defaults, source_stream FROM sub_type_labels").use { cursor ->
                cursor.moveToFirst()
                assertEquals("SPECIFIED", cursor.getString(0))
                assertEquals("{\"intake.abv_pct\":5.0}", cursor.getString(1))
                assertEquals(null, cursor.getString(2))
                assertEquals("{}", cursor.getString(3))
                assertEquals(null, cursor.getString(4))
            }
        }
    }

    @Test
    fun `3 to 4 keeps trackers and adds empty routine tables`() {
        helper.createDatabase(DB, 3).use { db ->
            db.execSQL(
                "INSERT INTO trackers (id, name, type, unit, created_at, sub_types, scale_min, scale_max, source_package, source_stream) " +
                    "VALUES ('t1', 'Beer', 'COUNTER', NULL, 0, '', NULL, NULL, NULL, NULL)",
            )
        }

        helper.runMigrationsAndValidate(DB, 4, true).use { db ->
            assertEquals(1, db.count("trackers"))
            assertEquals(0, db.count("routines"))
            assertEquals(0, db.count("routine_completions"))
        }
    }

    @Test
    fun `4 to 5 keeps routines and adds an empty block_rules table`() {
        helper.createDatabase(DB, 4).use { db ->
            db.execSQL(
                "INSERT INTO routines (id, title, rrule, start_date, time, checkable, created_at) " +
                    "VALUES ('r1', 'Sport', 'FREQ=DAILY', '2026-11-25', '18:00', 1, 0)",
            )
        }

        helper.runMigrationsAndValidate(DB, 5, true).use { db ->
            assertEquals(1, db.count("routines"))
            assertEquals(0, db.count("block_rules"))
        }
    }

    @Test
    fun `5 to 6 keeps block rules and adds an empty occurrence_overrides table`() {
        helper.createDatabase(DB, 5).use { db ->
            db.execSQL("INSERT INTO block_rules (routine_id, blocker_id) VALUES ('r1', 'r2')")
        }

        helper.runMigrationsAndValidate(DB, 6, true).use { db ->
            assertEquals(1, db.count("block_rules"))
            assertEquals(0, db.count("occurrence_overrides"))
        }
    }

    @Test
    fun `6 to 7 keeps single-day changes and adds an empty routine_pauses table`() {
        helper.createDatabase(DB, 6).use { db ->
            db.execSQL("INSERT INTO occurrence_overrides (routine_id, original_date, new_date) VALUES ('r1', '2026-11-25', NULL)")
        }

        helper.runMigrationsAndValidate(DB, 7, true).use { db ->
            assertEquals(1, db.count("occurrence_overrides"))
            assertEquals(0, db.count("routine_pauses"))
        }
    }

    @Test
    fun `7 to 8 keeps pauses and adds an empty routine_extras table`() {
        helper.createDatabase(DB, 7).use { db ->
            db.execSQL(
                "INSERT INTO routine_pauses (id, from_date, until_date, routine_ids, note) VALUES ('p1', '2026-12-01', NULL, '', 'Urlaub')",
            )
        }

        helper.runMigrationsAndValidate(DB, 8, true).use { db ->
            assertEquals(1, db.count("routine_pauses"))
            assertEquals(0, db.count("routine_extras"))
        }
    }

    @Test
    fun `8 to 9 keeps extras and adds an empty routine_schedule_versions table`() {
        helper.createDatabase(DB, 8).use { db ->
            db.execSQL("INSERT INTO routine_extras (routine_id, date) VALUES ('r1', '2026-11-27')")
        }

        helper.runMigrationsAndValidate(DB, 9, true).use { db ->
            assertEquals(1, db.count("routine_extras"))
            assertEquals(0, db.count("routine_schedule_versions"))
        }
    }

    @Test
    fun `9 to 10 keeps routines, which are paused as before`() {
        helper.createDatabase(DB, 9).use { db ->
            db.execSQL(
                "INSERT INTO routines (id, title, rrule, start_date, time, checkable, created_at) " +
                    "VALUES ('r1', 'Sport', 'FREQ=DAILY', '2026-11-25', '18:00', 1, 0)",
            )
        }

        helper.runMigrationsAndValidate(DB, 10, true).use { db ->
            db.query("SELECT ignores_pauses FROM routines").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    @Test
    fun `10 to 11 keeps moves and answers, with no new time and no separate done time`() {
        helper.createDatabase(DB, 10).use { db ->
            db.execSQL("INSERT INTO occurrence_overrides (routine_id, original_date, new_date) VALUES ('r1', '2026-11-25', '2026-11-26')")
            db.execSQL(
                "INSERT INTO routine_completions (routine_id, date, status, recorded_at, zone) " +
                    "VALUES ('r1', '2026-11-24', 'DONE', 5, 'Europe/Berlin')",
            )
        }

        helper.runMigrationsAndValidate(DB, 11, true).use { db ->
            db.query("SELECT new_time FROM occurrence_overrides").use { cursor ->
                cursor.moveToFirst()
                assertEquals(null, cursor.getString(0))
            }
            db.query("SELECT done_at FROM routine_completions").use { cursor ->
                cursor.moveToFirst()
                assertEquals(true, cursor.isNull(0))
            }
        }
    }

    @Test
    fun `11 to 12 keeps routines with floating time`() {
        helper.createDatabase(DB, 11).use { db ->
            db.execSQL(
                "INSERT INTO routines (id, title, rrule, start_date, time, checkable, created_at, ignores_pauses) " +
                    "VALUES ('r1', 'Sport', 'FREQ=DAILY', '2026-11-25', '18:00', 1, 0, 0)",
            )
        }

        helper.runMigrationsAndValidate(DB, 12, true).use { db ->
            db.query("SELECT time_zone FROM routines").use { cursor ->
                cursor.moveToFirst()
                assertEquals(true, cursor.isNull(0))
            }
        }
    }

    @Test
    fun `12 to 13 keeps routines, with no groups`() {
        helper.createDatabase(DB, 12).use { db ->
            db.execSQL(
                "INSERT INTO routines (id, title, rrule, start_date, time, checkable, created_at, ignores_pauses) " +
                    "VALUES ('r1', 'Sport', 'FREQ=DAILY', '2026-11-25', '18:00', 1, 0, 0)",
            )
        }

        helper.runMigrationsAndValidate(DB, 13, true).use { db ->
            assertEquals(1, db.count("routines"))
            assertEquals(0, db.count("routine_groups"))
        }
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.count(table: String): Int =
        query("SELECT COUNT(*) FROM $table").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private companion object {
        const val DB = "migration-test.db"
    }
}

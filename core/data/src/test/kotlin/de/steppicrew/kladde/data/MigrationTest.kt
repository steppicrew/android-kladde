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

    private fun androidx.sqlite.db.SupportSQLiteDatabase.count(table: String): Int =
        query("SELECT COUNT(*) FROM $table").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private companion object {
        const val DB = "migration-test.db"
    }
}

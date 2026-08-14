package com.lucasserafin94.iptvburo.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Adding reminders must not disturb anything already on the device.
 *
 * The table is new, so the risk is not the data it holds — it is the migration touching tables that
 * already have years of a user's favourites, progress and profiles in them.
 */
@RunWith(AndroidJUnit4::class)
class ReminderMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        IptvBuroDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migration8To9AddsRemindersAndLeavesProfilesAlone() {
        helper.createDatabase(DATABASE_NAME, 8).use { db ->
            db.execSQL(
                "INSERT INTO profiles(id, name, avatar_key, profile_type, language_tag, " +
                    "audio_language_tag, subtitle_language_tag, sort_order, created_at_epoch_millis) " +
                    "VALUES('profile-1', 'Lucas', 'ember', 'ADULT', 'pt-BR', 'pt-BR', 'pt-BR', 0, 1)",
            )
        }

        val migrated =
            helper.runMigrationsAndValidate(
                DATABASE_NAME,
                9,
                true,
                IptvBuroDatabase.MIGRATION_8_9,
            )

        migrated.use { db ->
            db.query("SELECT id, name FROM profiles").use { cursor ->
                assertTrue("the existing profile was lost", cursor.moveToFirst())
                assertEquals("profile-1", cursor.getString(0))
                assertEquals("Lucas", cursor.getString(1))
            }

            // The new table exists and takes a row with no catalogue entry behind it, which is the
            // upcoming-title case: nothing in `channels` to point at, and the insert must still work.
            db.execSQL(
                "INSERT INTO reminders(profile_id, content_key, title, artwork_url, release_date, " +
                    "created_at_epoch_millis) " +
                    "VALUES('profile-1', 'movie:duna-3:2026', 'Duna 3', NULL, '2026-09-12', 1)",
            )
            db.query("SELECT content_key, release_date FROM reminders").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("movie:duna-3:2026", cursor.getString(0))
                assertEquals("2026-09-12", cursor.getString(1))
            }
        }
    }

    /**
     * Deleting a profile takes its reminders with it.
     *
     * Left behind, they would be notified about on behalf of somebody who no longer exists — and
     * the primary key would collide the next time a profile reused the id.
     */
    @Test
    fun deletingAProfileRemovesItsReminders() {
        helper.createDatabase(DATABASE_NAME, 8).use { db ->
            db.execSQL(
                "INSERT INTO profiles(id, name, avatar_key, profile_type, language_tag, " +
                    "audio_language_tag, subtitle_language_tag, sort_order, created_at_epoch_millis) " +
                    "VALUES('profile-1', 'Lucas', 'ember', 'ADULT', 'pt-BR', 'pt-BR', 'pt-BR', 0, 1)",
            )
        }

        helper.runMigrationsAndValidate(DATABASE_NAME, 9, true, IptvBuroDatabase.MIGRATION_8_9)
            .use { db ->
                db.execSQL("PRAGMA foreign_keys = ON")
                db.execSQL(
                    "INSERT INTO reminders(profile_id, content_key, title, created_at_epoch_millis) " +
                        "VALUES('profile-1', 'movie:duna:2021', 'Duna', 1)",
                )
                db.execSQL("DELETE FROM profiles WHERE id = 'profile-1'")

                db.query("SELECT COUNT(*) FROM reminders").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(0, cursor.getInt(0))
                }
            }
    }

    /** Its own file, so one test's leftovers cannot decide another's starting state. */
    private companion object { const val DATABASE_NAME = "migration-reminders" }
}

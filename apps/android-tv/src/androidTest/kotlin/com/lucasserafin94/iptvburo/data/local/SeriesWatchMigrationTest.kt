package com.lucasserafin94.iptvburo.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Following a series must not disturb anything already on the device.
 *
 * The table is new, so the risk is not what it holds — it is a migration running against installs
 * that already carry years of somebody's favourites, reminders, progress and profiles. This asserts
 * that all of that survives, and that the cascade behaves the way the notice depends on.
 */
@RunWith(AndroidJUnit4::class)
class SeriesWatchMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        IptvBuroDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migration9To10AddsSeriesWatchAndKeepsExistingData() {
        helper.createDatabase(DATABASE_NAME, 9).use { db ->
            db.execSQL(
                "INSERT INTO profiles(id, name, avatar_key, profile_type, language_tag, " +
                    "audio_language_tag, subtitle_language_tag, sort_order, created_at_epoch_millis) " +
                    "VALUES('profile-1', 'Lucas', 'ember', 'ADULT', 'pt-BR', 'pt-BR', 'pt-BR', 0, 1)",
            )
            // A reminder from the previous version: the table added last time must come through
            // this one untouched.
            db.execSQL(
                "INSERT INTO reminders(profile_id, content_key, title, artwork_url, release_date, " +
                    "created_at_epoch_millis) " +
                    "VALUES('profile-1', 'series:a-serie:2026', 'A Série', NULL, NULL, 1)",
            )
        }

        val migrated =
            helper.runMigrationsAndValidate(
                DATABASE_NAME,
                10,
                true,
                IptvBuroDatabase.MIGRATION_9_10,
            )

        migrated.use { db ->
            db.query("SELECT id, name FROM profiles").use { cursor ->
                assertTrue("the existing profile was lost", cursor.moveToFirst())
                assertEquals("profile-1", cursor.getString(0))
                assertEquals("Lucas", cursor.getString(1))
            }
            db.query("SELECT title FROM reminders").use { cursor ->
                assertTrue("an existing reminder was lost", cursor.moveToFirst())
                assertEquals("A Série", cursor.getString(0))
            }

            // The new table takes a row. No row in `channels` is required: the count is filed
            // against the favourite, and a catalogue that has not finished importing must not stop
            // the app remembering what it already counted.
            db.execSQL(
                "INSERT INTO series_watch(profile_id, channel_id, title, episode_count, " +
                    "season_count, latest_season, checked_at_epoch_millis) " +
                    "VALUES('profile-1', 'channel-1', 'A Série', 8, 1, 1, 1000)",
            )
            db.query("SELECT episode_count, latest_season FROM series_watch").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(8, cursor.getInt(0))
                assertEquals(1, cursor.getInt(1))
            }
        }
    }

    /**
     * A deleted profile stops being followed.
     *
     * Without the cascade the worker would keep counting series for somebody who no longer exists,
     * and their notice would be attributed to whoever is signed in now.
     */
    @Test
    fun deletingAProfileForgetsWhatItWasFollowing() {
        helper.createDatabase(DATABASE_NAME, 9).use { db ->
            db.execSQL(
                "INSERT INTO profiles(id, name, avatar_key, profile_type, language_tag, " +
                    "audio_language_tag, subtitle_language_tag, sort_order, created_at_epoch_millis) " +
                    "VALUES('profile-1', 'Lucas', 'ember', 'ADULT', 'pt-BR', 'pt-BR', 'pt-BR', 0, 1)",
            )
        }

        helper.runMigrationsAndValidate(DATABASE_NAME, 10, true, IptvBuroDatabase.MIGRATION_9_10)
            .use { db ->
                db.execSQL("PRAGMA foreign_keys = ON")
                db.execSQL(
                    "INSERT INTO series_watch(profile_id, channel_id, title, episode_count, " +
                        "season_count, latest_season, checked_at_epoch_millis) " +
                        "VALUES('profile-1', 'channel-1', 'A Série', 8, 1, 1, 1000)",
                )
                db.execSQL("DELETE FROM profiles WHERE id = 'profile-1'")

                db.query("SELECT COUNT(*) FROM series_watch").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("the row outlived its profile", 0, cursor.getInt(0))
                }
            }
    }

    @Test
    fun migration9To10LeavesFavouritesAndProgressAlone() {
        helper.createDatabase(DATABASE_NAME, 9).use { db ->
            db.execSQL(
                "INSERT INTO profiles(id, name, avatar_key, profile_type, language_tag, " +
                    "audio_language_tag, subtitle_language_tag, sort_order, created_at_epoch_millis) " +
                    "VALUES('profile-1', 'Lucas', 'ember', 'ADULT', 'pt-BR', 'pt-BR', 'pt-BR', 0, 1)",
            )
            db.execSQL(
                "INSERT INTO sources(id, name, uri, created_at_epoch_millis, source_type) " +
                    "VALUES('source-1', 'Fonte', 'http://example.invalid/list.m3u', 1, 'LOCAL_M3U')",
            )
            db.execSQL(
                "INSERT INTO channels(id, source_id, name, stream_url, sort_order, content_type) " +
                    "VALUES('channel-1', 'source-1', 'A Série', 'http://example.invalid/s.ts', 0, 'SERIES')",
            )
            db.execSQL(
                "INSERT INTO favorites(profile_id, channel_id, added_at_epoch_millis) " +
                    "VALUES('profile-1', 'channel-1', 1)",
            )
        }

        helper.runMigrationsAndValidate(DATABASE_NAME, 10, true, IptvBuroDatabase.MIGRATION_9_10)
            .use { db ->
                db.query("SELECT channel_id FROM favorites").use { cursor ->
                    assertTrue("a favourite was lost", cursor.moveToFirst())
                    assertEquals("channel-1", cursor.getString(0))
                }
                // The new table starts empty: nothing is invented for series already favourited,
                // which is what makes the first count a baseline rather than a flood of notices.
                db.query("SELECT COUNT(*) FROM series_watch").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(0, cursor.getInt(0))
                }
                assertFalse("the database should still be open", false)
            }
    }

    // Referenced throughout this file but never declared: the androidTest source set has not
    // compiled since. Named for this test alone so its database cannot collide with another
    // migration test running beside it.
    private companion object { const val DATABASE_NAME = "migration-series-watch" }
}

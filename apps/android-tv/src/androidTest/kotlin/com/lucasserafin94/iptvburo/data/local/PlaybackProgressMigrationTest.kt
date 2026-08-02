package com.lucasserafin94.iptvburo.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackProgressMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        IptvBuroDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migration4To5PreservesLibraryAndCreatesProgressSchema() {
        helper.createDatabase(DATABASE_NAME, 4).use { db ->
            db.execSQL(
                "INSERT INTO sources(id, display_name, type, created_at_epoch_millis, updated_at_epoch_millis, channel_count, preferred_live_extension) " +
                    "VALUES('source', 'Library', 'XTREAM', 1, 1, 0, NULL)",
            )
            db.execSQL(
                "INSERT INTO profiles(id, name, avatar_key, profile_type, language_tag, audio_language_tag, subtitle_language_tag, sort_order, created_at_epoch_millis) " +
                    "VALUES('profile', 'Lucas', 'amber', 'ADULT', 'pt-BR', 'pt-BR', 'pt-BR', 0, 1)",
            )
        }

        helper.runMigrationsAndValidate(
            DATABASE_NAME,
            5,
            true,
            IptvBuroDatabase.MIGRATION_4_5,
        ).use { db ->
            db.query("SELECT COUNT(*) FROM sources WHERE id = 'source'").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
            db.query("SELECT COUNT(*) FROM profiles WHERE id = 'profile'").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
            db.execSQL(
                "INSERT INTO playback_progress(profile_id, source_id, content_id, content_type, series_id, season_number, episode_number, position_ms, duration_ms, progress_percent, last_watched_at_epoch_millis, completed_at_epoch_millis, updated_at_epoch_millis, revision) " +
                    "VALUES('profile', 'source', 'movie', 'MOVIE', NULL, NULL, NULL, 60000, 1000000, 0.06, 2, NULL, 2, 1)",
            )
            db.query("SELECT position_ms FROM playback_progress WHERE content_id = 'movie'").use { cursor ->
                cursor.moveToFirst()
                assertEquals(60_000L, cursor.getLong(0))
            }
        }
    }

    private companion object { const val DATABASE_NAME = "migration-4-5" }
}

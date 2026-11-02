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
 * Remembering a channel's catch-up window must not disturb the catalogue already on the device.
 *
 * Every favourite, progress row and reminder is filed under a channel id, so a migration that
 * rebuilt the table would orphan them all. And the new column starts null for every existing row:
 * a channel imported before it existed offers no catch-up until its source is refreshed, exactly
 * as before.
 */
@RunWith(AndroidJUnit4::class)
class CatchUpMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        IptvBuroDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migration11To12KeepsChannelsAndLeavesCatchUpUnknown() {
        helper.createDatabase(DATABASE_NAME, 11).use { db ->
            db.execSQL(
                "INSERT INTO sources(id, display_name, type, created_at_epoch_millis, " +
                    "updated_at_epoch_millis, channel_count, preferred_live_extension) " +
                    "VALUES('source-1', 'BURO', 'XTREAM', 1, 2, 1, 'ts')",
            )
            db.execSQL(
                "INSERT INTO channels(id, source_id, category_id, tvg_id, tvg_name, name, logo_url, " +
                    "stream_url, user_agent, referer, origin, sort_order, content_type, " +
                    "provider_item_id) VALUES('channel-1', 'source-1', NULL, NULL, NULL, 'News', NULL, " +
                    "'xtream://catalog/source-1/live/42', NULL, NULL, NULL, 0, 'LIVE', '42')",
            )
        }

        helper.runMigrationsAndValidate(
            DATABASE_NAME,
            12,
            true,
            IptvBuroDatabase.MIGRATION_11_12,
        ).use { db ->
            db.query("SELECT name, provider_item_id, catch_up_days FROM channels WHERE id = 'channel-1'").use { cursor ->
                assertTrue("The channel must survive: favourites and progress are filed under its id.", cursor.moveToFirst())
                assertEquals("News", cursor.getString(0))
                assertEquals("42", cursor.getString(1))
                assertTrue("An existing channel offers no catch-up until refreshed.", cursor.isNull(2))
            }
            db.execSQL("UPDATE channels SET catch_up_days = 7 WHERE id = 'channel-1'")
            db.query("SELECT catch_up_days FROM channels WHERE id = 'channel-1'").use { cursor ->
                cursor.moveToFirst()
                assertEquals(7, cursor.getInt(0))
            }
        }
    }

    private companion object { const val DATABASE_NAME = "migration-catch-up" }
}

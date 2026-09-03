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
 * Remembering when a subscription ends must not disturb the sources already on the device.
 *
 * A source's id is what every channel, category and profile is filed under. A migration that
 * recreated the table would orphan the whole library, and the viewer would open the app to find it
 * empty after an update.
 *
 * The new column must also default to null rather than zero. Null means "this panel never told us",
 * which the screens show as nothing; zero would read as a date in 1970 and announce that a
 * perfectly good subscription had expired.
 */
@RunWith(AndroidJUnit4::class)
class SubscriptionExpiryMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        IptvBuroDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migration10To11KeepsSourcesAndLeavesTheExpiryUnknown() {
        helper.createDatabase(DATABASE_NAME, 10).use { db ->
            db.execSQL(
                "INSERT INTO sources(id, display_name, type, created_at_epoch_millis, " +
                    "updated_at_epoch_millis, channel_count, preferred_live_extension) " +
                    "VALUES('source-1', 'BURO', 'XTREAM', 1, 2, 41698, 'm3u8')",
            )
            db.execSQL(
                "INSERT INTO sources(id, display_name, type, created_at_epoch_millis, " +
                    "updated_at_epoch_millis, channel_count, preferred_live_extension) " +
                    "VALUES('source-2', 'Local', 'LOCAL_M3U', 3, 4, 12, NULL)",
            )
        }

        helper.runMigrationsAndValidate(
            DATABASE_NAME,
            11,
            true,
            IptvBuroDatabase.MIGRATION_10_11,
        ).use { db ->
            db.query("SELECT COUNT(*) FROM sources").use { cursor ->
                cursor.moveToFirst()
                assertEquals(
                    "Both sources must survive: their ids key the whole catalogue.",
                    2,
                    cursor.getInt(0),
                )
            }

            db.query(
                "SELECT display_name, channel_count, preferred_live_extension, " +
                    "subscription_expires_at_epoch_seconds FROM sources WHERE id = 'source-1'",
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals("BURO", cursor.getString(0))
                assertEquals(41698, cursor.getInt(1))
                assertEquals("m3u8", cursor.getString(2))
                assertTrue(
                    "A source imported before this column existed has no date until it is " +
                        "refreshed, and unknown must never be shown as expired.",
                    cursor.isNull(3),
                )
            }
        }
    }

    @Test
    fun migration10To11AcceptsADateAfterwards() {
        helper.createDatabase(DATABASE_NAME, 10).use { db ->
            db.execSQL(
                "INSERT INTO sources(id, display_name, type, created_at_epoch_millis, " +
                    "updated_at_epoch_millis, channel_count, preferred_live_extension) " +
                    "VALUES('source-1', 'BURO', 'XTREAM', 1, 2, 10, NULL)",
            )
        }

        helper.runMigrationsAndValidate(
            DATABASE_NAME,
            11,
            true,
            IptvBuroDatabase.MIGRATION_10_11,
        ).use { db ->
            db.execSQL(
                "UPDATE sources SET subscription_expires_at_epoch_seconds = 1790000000 " +
                    "WHERE id = 'source-1'",
            )
            db.query(
                "SELECT subscription_expires_at_epoch_seconds FROM sources WHERE id = 'source-1'",
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals(1_790_000_000L, cursor.getLong(0))
            }
        }
    }

    private companion object { const val DATABASE_NAME = "migration-subscription-expiry" }
}

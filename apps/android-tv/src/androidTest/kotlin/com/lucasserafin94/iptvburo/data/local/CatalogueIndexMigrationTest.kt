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
 * The catalogue indexes change, the catalogue does not.
 *
 * Every favourite, progress row and reminder is filed under a channel id, so the migration may only
 * touch indexes. And the point of it is that the home's two rows stop scanning the whole source,
 * which is asserted through SQLite's own query plan rather than assumed.
 */
@RunWith(AndroidJUnit4::class)
class CatalogueIndexMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        IptvBuroDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migration12To13KeepsRowsAndTheHomeRowsUseTheirIndexes() {
        helper.createDatabase(DATABASE_NAME, 12).use { db ->
            db.execSQL(
                "INSERT INTO sources(id, display_name, type, created_at_epoch_millis, " +
                    "updated_at_epoch_millis, channel_count, preferred_live_extension) " +
                    "VALUES('source-1', 'BURO', 'XTREAM', 1, 2, 2, NULL)",
            )
            for ((id, year) in listOf("film-1" to 2026, "film-2" to 2025)) {
                db.execSQL(
                    "INSERT INTO channels(id, source_id, name, stream_url, sort_order, content_type, " +
                        "provider_item_id, year, added_at_epoch_seconds) VALUES('$id', 'source-1', '$id', " +
                        "'xtream://catalog/source-1/movie/$id', 0, 'MOVIE', '$id', $year, 1790000000)",
                )
            }
        }

        helper.runMigrationsAndValidate(
            DATABASE_NAME,
            13,
            true,
            IptvBuroDatabase.MIGRATION_12_13,
        ).use { db ->
            db.query("SELECT COUNT(*) FROM channels").use { cursor ->
                cursor.moveToFirst()
                assertEquals("No row may move: everything personal is filed under these ids.", 2, cursor.getInt(0))
            }
            val yearPlan = plan(db, "SELECT * FROM channels WHERE source_id = 'source-1' AND year = 2026")
            assertTrue(yearPlan, yearPlan.contains("index_channels_source_id_year"))
            val recentPlan =
                plan(
                    db,
                    "SELECT * FROM channels WHERE source_id = 'source-1' AND content_type IN ('MOVIE','SERIES') " +
                        "ORDER BY added_at_epoch_seconds DESC, sort_order, id LIMIT 24",
                )
            assertTrue(recentPlan, recentPlan.contains("index_channels_source_id_added_at_epoch_seconds_sort_order_id"))
        }
    }

    private fun plan(db: androidx.sqlite.db.SupportSQLiteDatabase, sql: String): String =
        db.query("EXPLAIN QUERY PLAN $sql").use { cursor ->
            buildString { while (cursor.moveToNext()) append(cursor.getString(cursor.columnCount - 1)).append('\n') }
        }

    private companion object { const val DATABASE_NAME = "migration-catalogue-indexes" }
}

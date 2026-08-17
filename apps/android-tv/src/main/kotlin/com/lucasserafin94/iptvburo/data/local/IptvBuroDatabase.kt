package com.lucasserafin94.iptvburo.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lucasserafin94.iptvburo.data.local.dao.CategoryDao
import com.lucasserafin94.iptvburo.data.local.dao.ChannelDao
import com.lucasserafin94.iptvburo.data.local.dao.FavoriteDao
import com.lucasserafin94.iptvburo.data.local.dao.ProfileDao
import com.lucasserafin94.iptvburo.data.local.dao.ReminderDao
import com.lucasserafin94.iptvburo.data.local.dao.SeriesWatchDao
import com.lucasserafin94.iptvburo.data.local.dao.PlaybackProgressDao
import com.lucasserafin94.iptvburo.data.local.dao.SourceDao
import com.lucasserafin94.iptvburo.data.local.entity.CategoryEntity
import com.lucasserafin94.iptvburo.data.local.entity.ChannelEntity
import com.lucasserafin94.iptvburo.data.local.dao.LibraryEntryDao
import com.lucasserafin94.iptvburo.data.local.entity.FavoriteEntity
import com.lucasserafin94.iptvburo.data.local.entity.LibraryEntryEntity
import com.lucasserafin94.iptvburo.data.local.entity.ReminderEntity
import com.lucasserafin94.iptvburo.data.local.entity.SeriesWatchEntity
import com.lucasserafin94.iptvburo.data.local.entity.ProfileEntity
import com.lucasserafin94.iptvburo.data.local.entity.PlaybackProgressEntity
import com.lucasserafin94.iptvburo.data.local.entity.SourceEntity

@Database(
    entities = [
        SourceEntity::class,
        CategoryEntity::class,
        ChannelEntity::class,
        ProfileEntity::class,
        FavoriteEntity::class,
        PlaybackProgressEntity::class,
        LibraryEntryEntity::class,
        ReminderEntity::class,
        SeriesWatchEntity::class,
    ],
    version = 10,
    exportSchema = true,
)
abstract class IptvBuroDatabase : RoomDatabase() {
    abstract fun sourceDao(): SourceDao

    abstract fun categoryDao(): CategoryDao

    abstract fun channelDao(): ChannelDao

    abstract fun profileDao(): ProfileDao

    abstract fun reminderDao(): ReminderDao

    abstract fun seriesWatchDao(): SeriesWatchDao

    abstract fun favoriteDao(): FavoriteDao

    abstract fun playbackProgressDao(): PlaybackProgressDao

    abstract fun libraryEntryDao(): LibraryEntryDao

    companion object {
        val MIGRATION_1_2: Migration =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "ALTER TABLE sources ADD COLUMN preferred_live_extension TEXT",
                    )
                    db.execSQL(
                        "ALTER TABLE categories ADD COLUMN content_type TEXT NOT NULL DEFAULT 'UNKNOWN'",
                    )
                    db.execSQL(
                        "ALTER TABLE categories ADD COLUMN provider_category_id TEXT",
                    )
                    db.execSQL(
                        "ALTER TABLE channels ADD COLUMN content_type TEXT NOT NULL DEFAULT 'UNKNOWN'",
                    )
                    db.execSQL(
                        "ALTER TABLE channels ADD COLUMN provider_item_id TEXT",
                    )
                    db.execSQL(
                        "ALTER TABLE channels ADD COLUMN container_extension TEXT",
                    )
                    db.execSQL("ALTER TABLE channels ADD COLUMN year INTEGER")
                    db.execSQL("ALTER TABLE channels ADD COLUMN rating REAL")
                    db.execSQL(
                        "ALTER TABLE channels ADD COLUMN added_at_epoch_seconds INTEGER",
                    )
                    db.execSQL("DROP INDEX IF EXISTS index_categories_source_id_name")
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_categories_source_id_name " +
                            "ON categories(source_id, name)",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_categories_source_id_content_type " +
                            "ON categories(source_id, content_type)",
                    )
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS " +
                            "index_categories_source_id_content_type_provider_category_id " +
                            "ON categories(source_id, content_type, provider_category_id)",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS " +
                            "index_channels_source_id_content_type_category_id_sort_order " +
                            "ON channels(source_id, content_type, category_id, sort_order)",
                    )
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS " +
                            "index_channels_source_id_content_type_provider_item_id " +
                            "ON channels(source_id, content_type, provider_item_id)",
                    )
                }
            }

        val MIGRATION_2_3: Migration =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS " +
                            "index_channels_source_id_content_type_category_id_sort_order_id " +
                            "ON channels(source_id, content_type, category_id, sort_order, id)",
                    )
                }
            }

        val MIGRATION_3_4: Migration =
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS profiles (
                            id TEXT NOT NULL PRIMARY KEY,
                            name TEXT NOT NULL,
                            avatar_key TEXT NOT NULL,
                            profile_type TEXT NOT NULL,
                            language_tag TEXT NOT NULL,
                            audio_language_tag TEXT NOT NULL,
                            subtitle_language_tag TEXT NOT NULL,
                            sort_order INTEGER NOT NULL,
                            created_at_epoch_millis INTEGER NOT NULL
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS favorites (
                            profile_id TEXT NOT NULL,
                            channel_id TEXT NOT NULL,
                            added_at_epoch_millis INTEGER NOT NULL,
                            PRIMARY KEY(profile_id, channel_id),
                            FOREIGN KEY(profile_id) REFERENCES profiles(id) ON DELETE CASCADE,
                            FOREIGN KEY(channel_id) REFERENCES channels(id) ON DELETE CASCADE
                        )
                        """.trimIndent(),
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_favorites_profile_id ON favorites(profile_id)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_favorites_channel_id ON favorites(channel_id)")
                }
            }

        val MIGRATION_4_5: Migration =
            object : Migration(4, 5) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS playback_progress (
                            profile_id TEXT NOT NULL,
                            source_id TEXT NOT NULL,
                            content_id TEXT NOT NULL,
                            content_type TEXT NOT NULL,
                            series_id TEXT,
                            season_number INTEGER,
                            episode_number INTEGER,
                            position_ms INTEGER NOT NULL,
                            duration_ms INTEGER NOT NULL,
                            progress_percent REAL NOT NULL,
                            last_watched_at_epoch_millis INTEGER NOT NULL,
                            completed_at_epoch_millis INTEGER,
                            updated_at_epoch_millis INTEGER NOT NULL,
                            revision INTEGER NOT NULL,
                            PRIMARY KEY(profile_id, source_id, content_id, content_type),
                            FOREIGN KEY(profile_id) REFERENCES profiles(id) ON DELETE CASCADE,
                            FOREIGN KEY(source_id) REFERENCES sources(id) ON DELETE CASCADE
                        )
                        """.trimIndent(),
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_playback_progress_profile_id_completed_at_epoch_millis_last_watched_at_epoch_millis ON playback_progress(profile_id, completed_at_epoch_millis, last_watched_at_epoch_millis)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_playback_progress_profile_id_content_type_last_watched_at_epoch_millis ON playback_progress(profile_id, content_type, last_watched_at_epoch_millis)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_playback_progress_source_id_content_id ON playback_progress(source_id, content_id)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_playback_progress_profile_id_series_id_season_number_episode_number ON playback_progress(profile_id, series_id, season_number, episode_number)")
                }
            }

        /**
         * Adds `library_entries`, a favourites table with no foreign key into the catalogue.
         *
         * `favorites` cascades from `channels`, and every import mints a fresh random `sourceId`
         * and therefore fresh channel rows. Replacing a playlist made SQLite *delete* the user's
         * favourites, not merely orphan them. Entries are carried across keyed on content identity
         * so they survive the next import.
         */
        /**
         * Adds `photo_uri` to `profiles`, so a profile can show a photograph the user chose.
         *
         * Nullable with no default: every existing profile keeps its drawn avatar, which is what a
         * user who never picked a photo expects to still see after updating.
         */
        /**
         * Adds `source_id` to `profiles`, so each profile can sign in to its own playlist.
         *
         * Nullable with no default, and no foreign key. Every existing profile keeps working
         * exactly as before — null means "no preference", which is what a household with a single
         * playlist should never have to think about.
         */
        /**
         * Reminders.
         *
         * No foreign key to `channels`, deliberately: an upcoming title has no catalogue row to
         * point at, and the cascade favourites carry would delete every reminder on the next
         * playlist import — losing exactly the titles the feature exists to watch for.
         */
        /**
         * How large each favourited series was at the last count, for the new-episode notice.
         *
         * Additive: a table that did not exist before, so nothing already stored is read, rewritten
         * or at risk. An install upgrading through this keeps every favourite, reminder and resume
         * point exactly as it was, and simply starts counting series from the next check — which is
         * also why the first count of any series announces nothing.
         */
        val MIGRATION_9_10: Migration =
            object : Migration(9, 10) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS series_watch (
                            profile_id TEXT NOT NULL,
                            channel_id TEXT NOT NULL,
                            title TEXT NOT NULL,
                            episode_count INTEGER NOT NULL,
                            season_count INTEGER NOT NULL,
                            latest_season INTEGER NOT NULL,
                            checked_at_epoch_millis INTEGER NOT NULL,
                            PRIMARY KEY(profile_id, channel_id),
                            FOREIGN KEY(profile_id) REFERENCES profiles(id) ON DELETE CASCADE
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_series_watch_profile_id ON series_watch(profile_id)",
                    )
                }
            }

        val MIGRATION_8_9: Migration =
            object : Migration(8, 9) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS reminders (
                            profile_id TEXT NOT NULL,
                            content_key TEXT NOT NULL,
                            title TEXT NOT NULL,
                            artwork_url TEXT,
                            release_date TEXT,
                            created_at_epoch_millis INTEGER NOT NULL,
                            PRIMARY KEY(profile_id, content_key),
                            FOREIGN KEY(profile_id) REFERENCES profiles(id) ON DELETE CASCADE
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_reminders_profile_id ON reminders(profile_id)",
                    )
                }
            }

        val MIGRATION_7_8: Migration =
            object : Migration(7, 8) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE profiles ADD COLUMN source_id TEXT")
                }
            }

        val MIGRATION_6_7: Migration =
            object : Migration(6, 7) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE profiles ADD COLUMN photo_uri TEXT")
                }
            }

        val MIGRATION_5_6: Migration =
            object : Migration(5, 6) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS library_entries (
                            profile_id TEXT NOT NULL,
                            content_key TEXT NOT NULL,
                            last_known_title TEXT NOT NULL,
                            content_type TEXT NOT NULL,
                            added_at_epoch_millis INTEGER NOT NULL,
                            PRIMARY KEY(profile_id, content_key),
                            FOREIGN KEY(profile_id) REFERENCES profiles(id) ON DELETE CASCADE
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_library_entries_profile_id " +
                            "ON library_entries(profile_id)",
                    )
                    // Carry over favourites that still resolve to a channel. The content key is
                    // computed in SQL here rather than in Kotlin because the migration has no
                    // access to the app's mappers; it mirrors ContentIdentity for the common
                    // shapes and any imperfect key is corrected the next time the user toggles
                    // the item.
                    db.execSQL(
                        """
                        INSERT OR IGNORE INTO library_entries (
                            profile_id, content_key, last_known_title, content_type, added_at_epoch_millis
                        )
                        SELECT
                            f.profile_id,
                            lower(c.content_type) || ':' || lower(trim(c.name)),
                            c.name,
                            c.content_type,
                            f.added_at_epoch_millis
                        FROM favorites AS f
                        INNER JOIN channels AS c ON c.id = f.channel_id
                        """.trimIndent(),
                    )
                }
            }
    }
}

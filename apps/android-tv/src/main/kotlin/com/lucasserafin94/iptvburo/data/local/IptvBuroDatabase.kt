package com.lucasserafin94.iptvburo.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lucasserafin94.iptvburo.data.local.dao.CategoryDao
import com.lucasserafin94.iptvburo.data.local.dao.ChannelDao
import com.lucasserafin94.iptvburo.data.local.dao.FavoriteDao
import com.lucasserafin94.iptvburo.data.local.dao.ProfileDao
import com.lucasserafin94.iptvburo.data.local.dao.SourceDao
import com.lucasserafin94.iptvburo.data.local.entity.CategoryEntity
import com.lucasserafin94.iptvburo.data.local.entity.ChannelEntity
import com.lucasserafin94.iptvburo.data.local.entity.FavoriteEntity
import com.lucasserafin94.iptvburo.data.local.entity.ProfileEntity
import com.lucasserafin94.iptvburo.data.local.entity.SourceEntity

@Database(
    entities = [
        SourceEntity::class,
        CategoryEntity::class,
        ChannelEntity::class,
        ProfileEntity::class,
        FavoriteEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class IptvBuroDatabase : RoomDatabase() {
    abstract fun sourceDao(): SourceDao

    abstract fun categoryDao(): CategoryDao

    abstract fun channelDao(): ChannelDao

    abstract fun profileDao(): ProfileDao

    abstract fun favoriteDao(): FavoriteDao

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
    }
}

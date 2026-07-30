package com.lucasserafin94.iptvburo.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.lucasserafin94.iptvburo.data.local.dao.CategoryDao
import com.lucasserafin94.iptvburo.data.local.dao.ChannelDao
import com.lucasserafin94.iptvburo.data.local.dao.SourceDao
import com.lucasserafin94.iptvburo.data.local.entity.CategoryEntity
import com.lucasserafin94.iptvburo.data.local.entity.ChannelEntity
import com.lucasserafin94.iptvburo.data.local.entity.SourceEntity

@Database(
    entities = [
        SourceEntity::class,
        CategoryEntity::class,
        ChannelEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class IptvBuroDatabase : RoomDatabase() {
    abstract fun sourceDao(): SourceDao

    abstract fun categoryDao(): CategoryDao

    abstract fun channelDao(): ChannelDao
}

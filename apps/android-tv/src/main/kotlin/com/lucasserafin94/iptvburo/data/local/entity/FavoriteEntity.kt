package com.lucasserafin94.iptvburo.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "favorites",
    primaryKeys = ["profile_id", "channel_id"],
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ChannelEntity::class,
            parentColumns = ["id"],
            childColumns = ["channel_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("profile_id"), Index("channel_id")],
)
data class FavoriteEntity(
    @ColumnInfo(name = "profile_id")
    val profileId: String,
    @ColumnInfo(name = "channel_id")
    val channelId: String,
    @ColumnInfo(name = "added_at_epoch_millis")
    val addedAtEpochMillis: Long,
)

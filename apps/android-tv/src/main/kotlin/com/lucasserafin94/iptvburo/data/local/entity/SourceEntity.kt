package com.lucasserafin94.iptvburo.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sources",
    indices = [
        Index(value = ["display_name"]),
    ],
)
data class SourceEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "display_name")
    val displayName: String,
    val type: String,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long,
    @ColumnInfo(name = "channel_count")
    val channelCount: Int,
    @ColumnInfo(name = "preferred_live_extension")
    val preferredLiveExtension: String? = null,
    /**
     * When the viewer's subscription to this source ends, in epoch seconds.
     *
     * Null when the panel does not report `exp_date`, when the line never expires, and on every
     * source imported before this column existed. All three mean the same thing to the screens:
     * say nothing rather than invent a warning.
     */
    @ColumnInfo(name = "subscription_expires_at_epoch_seconds")
    val subscriptionExpiresAtEpochSeconds: Long? = null,
)

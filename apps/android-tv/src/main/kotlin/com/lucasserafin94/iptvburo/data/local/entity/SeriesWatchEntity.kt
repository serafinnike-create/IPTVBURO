package com.lucasserafin94.iptvburo.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * How large a favourited series was the last time the app counted it.
 *
 * This is what turns "is there a new episode?" into a question the data can answer. A playlist
 * carries no air dates worth trusting, but it does say how many episodes a series has — so the app
 * remembers yesterday's count and compares.
 *
 * Counts rather than episode ids, deliberately: a provider renumbers and re-imports its catalogue
 * routinely, so ids churn for reasons that have nothing to do with new content. Comparing ids would
 * announce a new episode every time the playlist was refreshed.
 *
 * Keyed by profile and channel because a favourite is per profile: two people in a household
 * following the same series are each told once, from their own last check.
 *
 * The cascade to `profiles` matches the one on reminders, and for the same reason: a deleted profile
 * follows nothing, and an orphan row would have the worker counting for somebody who no longer
 * exists.
 */
@Entity(
    tableName = "series_watch",
    primaryKeys = ["profile_id", "channel_id"],
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("profile_id")],
)
data class SeriesWatchEntity(
    @ColumnInfo(name = "profile_id")
    val profileId: String,
    @ColumnInfo(name = "channel_id")
    val channelId: String,
    /** Shown in the notification. Presentation only; the channel id is the identity. */
    val title: String,
    @ColumnInfo(name = "episode_count")
    val episodeCount: Int,
    @ColumnInfo(name = "season_count")
    val seasonCount: Int,
    /** Highest season number seen, so a season added out of order is still recognised. */
    @ColumnInfo(name = "latest_season")
    val latestSeason: Int,
    @ColumnInfo(name = "checked_at_epoch_millis")
    val checkedAtEpochMillis: Long,
)

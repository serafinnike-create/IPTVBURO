package com.lucasserafin94.iptvburo.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "playback_progress",
    primaryKeys = ["profile_id", "source_id", "content_id", "content_type"],
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["source_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["profile_id", "completed_at_epoch_millis", "last_watched_at_epoch_millis"]),
        Index(value = ["profile_id", "content_type", "last_watched_at_epoch_millis"]),
        Index(value = ["source_id", "content_id"]),
        Index(value = ["profile_id", "series_id", "season_number", "episode_number"]),
    ],
)
data class PlaybackProgressEntity(
    @ColumnInfo(name = "profile_id") val profileId: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "content_id") val contentId: String,
    @ColumnInfo(name = "content_type") val contentType: String,
    @ColumnInfo(name = "series_id") val seriesId: String?,
    @ColumnInfo(name = "season_number") val seasonNumber: Int?,
    @ColumnInfo(name = "episode_number") val episodeNumber: Int?,
    @ColumnInfo(name = "position_ms") val positionMs: Long,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "progress_percent") val progressPercent: Double,
    @ColumnInfo(name = "last_watched_at_epoch_millis") val lastWatchedAtEpochMillis: Long,
    @ColumnInfo(name = "completed_at_epoch_millis") val completedAtEpochMillis: Long?,
    @ColumnInfo(name = "updated_at_epoch_millis") val updatedAtEpochMillis: Long,
    val revision: Long,
)

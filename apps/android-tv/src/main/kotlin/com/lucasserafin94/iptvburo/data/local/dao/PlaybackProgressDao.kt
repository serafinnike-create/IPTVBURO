package com.lucasserafin94.iptvburo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lucasserafin94.iptvburo.data.local.entity.PlaybackProgressEntity

@Dao
interface PlaybackProgressDao {
    @Query(
        "SELECT * FROM playback_progress WHERE profile_id = :profileId AND source_id = :sourceId " +
            "AND content_id = :contentId AND content_type = :contentType LIMIT 1",
    )
    fun find(profileId: String, sourceId: String, contentId: String, contentType: String): PlaybackProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entity: PlaybackProgressEntity)

    @Query(
        "DELETE FROM playback_progress WHERE profile_id = :profileId AND source_id = :sourceId " +
            "AND content_id = :contentId AND content_type = :contentType",
    )
    fun remove(profileId: String, sourceId: String, contentId: String, contentType: String)

    @Query(
        "SELECT * FROM playback_progress WHERE profile_id = :profileId AND completed_at_epoch_millis IS NULL " +
            "ORDER BY last_watched_at_epoch_millis DESC LIMIT :limit",
    )
    fun continueWatching(profileId: String, limit: Int): List<PlaybackProgressEntity>

    @Query(
        "SELECT * FROM playback_progress WHERE profile_id = :profileId " +
            "ORDER BY last_watched_at_epoch_millis DESC LIMIT :limit",
    )
    fun history(profileId: String, limit: Int): List<PlaybackProgressEntity>
}

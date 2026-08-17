package com.lucasserafin94.iptvburo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lucasserafin94.iptvburo.data.local.entity.SeriesWatchEntity

@Dao
interface SeriesWatchDao {
    @Query("SELECT * FROM series_watch WHERE profile_id = :profileId AND channel_id = :channelId")
    suspend fun find(profileId: String, channelId: String): SeriesWatchEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: SeriesWatchEntity)

    /**
     * Everything being followed, for the daily check.
     *
     * All profiles at once, like `ReminderDao.all`: the worker runs with nobody signed in, so it
     * cannot ask "what is the current profile following".
     */
    @Query("SELECT * FROM series_watch")
    suspend fun all(): List<SeriesWatchEntity>

    /**
     * Forgets a series, which is what unfavouriting one means.
     *
     * Without this the app would keep counting a series nobody follows any more, and re-favouriting
     * it later would compare against a months-old number and announce a flood.
     */
    @Query("DELETE FROM series_watch WHERE profile_id = :profileId AND channel_id = :channelId")
    suspend fun remove(profileId: String, channelId: String)
}

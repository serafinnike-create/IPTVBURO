package com.lucasserafin94.iptvburo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lucasserafin94.iptvburo.data.local.entity.ChannelEntity
import com.lucasserafin94.iptvburo.data.local.entity.FavoriteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query("SELECT channel_id FROM favorites WHERE profile_id = :profileId")
    fun observeIds(profileId: String): Flow<List<String>>

    @Query(
        """
        SELECT channels.* FROM channels
        INNER JOIN favorites ON favorites.channel_id = channels.id
        WHERE favorites.profile_id = :profileId
        ORDER BY favorites.added_at_epoch_millis DESC
        LIMIT :limit
        """,
    )
    suspend fun loadChannels(profileId: String, limit: Int): List<ChannelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE profile_id = :profileId AND channel_id = :channelId")
    suspend fun remove(profileId: String, channelId: String)

    /** Clears a deleted profile's favourites, so its rows do not outlive the profile itself. */
    @Query("DELETE FROM favorites WHERE profile_id = :profileId")
    suspend fun removeAllForProfile(profileId: String)
}

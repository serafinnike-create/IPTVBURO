package com.lucasserafin94.iptvburo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lucasserafin94.iptvburo.data.local.entity.ChannelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {
    @Query(
        """
        SELECT * FROM channels
        WHERE source_id = :sourceId
          AND (:categoryId IS NULL OR category_id = :categoryId)
        ORDER BY sort_order, name COLLATE NOCASE
        """,
    )
    fun observeForSource(
        sourceId: String,
        categoryId: String?,
    ): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ChannelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(channels: List<ChannelEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAllBlocking(channels: List<ChannelEntity>)

    @Query("DELETE FROM channels WHERE source_id = :sourceId")
    suspend fun deleteForSource(sourceId: String)
}

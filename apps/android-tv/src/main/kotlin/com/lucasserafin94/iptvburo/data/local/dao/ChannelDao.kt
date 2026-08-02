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
          AND (:contentType IS NULL OR content_type = :contentType)
        ORDER BY sort_order, name COLLATE NOCASE
        """,
    )
    fun observeForSource(
        sourceId: String,
        categoryId: String?,
        contentType: String?,
    ): Flow<List<ChannelEntity>>

    @Query(
        """
        SELECT * FROM channels
        WHERE source_id = :sourceId
          AND (:categoryId IS NULL OR category_id = :categoryId)
          AND (:contentType IS NULL OR content_type = :contentType)
        ORDER BY sort_order, name COLLATE NOCASE
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun loadPage(
        sourceId: String,
        categoryId: String?,
        contentType: String?,
        limit: Int,
        offset: Int,
    ): List<ChannelEntity>

    @Query(
        """
        SELECT * FROM channels
        WHERE source_id = :sourceId
          AND (:categoryId IS NULL OR category_id = :categoryId)
          AND (:contentType IS NULL OR content_type = :contentType)
        ORDER BY sort_order, id
        LIMIT :limit
        """,
    )
    suspend fun loadFirstPage(
        sourceId: String,
        categoryId: String?,
        contentType: String?,
        limit: Int,
    ): List<ChannelEntity>

    @Query(
        """
        SELECT * FROM channels
        WHERE source_id = :sourceId
          AND (:categoryId IS NULL OR category_id = :categoryId)
          AND (:contentType IS NULL OR content_type = :contentType)
          AND (
            sort_order > :afterSortOrder OR
            (sort_order = :afterSortOrder AND id > :afterId)
          )
        ORDER BY sort_order, id
        LIMIT :limit
        """,
    )
    suspend fun loadPageAfter(
        sourceId: String,
        categoryId: String?,
        contentType: String?,
        afterSortOrder: Int,
        afterId: String,
        limit: Int,
    ): List<ChannelEntity>

    @Query(
        """
        SELECT COUNT(*) FROM channels
        WHERE source_id = :sourceId
          AND (:categoryId IS NULL OR category_id = :categoryId)
          AND (:contentType IS NULL OR content_type = :contentType)
        """,
    )
    suspend fun countForSource(
        sourceId: String,
        categoryId: String?,
        contentType: String?,
    ): Int

    @Query("SELECT * FROM channels WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ChannelEntity?

    @Query(
        """
        SELECT * FROM channels
        WHERE source_id = :sourceId
          AND content_type = 'MOVIE'
          AND id != :excludeChannelId
          AND name LIKE :titlePrefix || '%' COLLATE NOCASE
        ORDER BY
          CASE WHEN UPPER(name) LIKE '%4K%'
                 OR UPPER(name) LIKE '%HDR%'
                 OR UPPER(name) LIKE '%DV%'
                 OR UPPER(name) LIKE '%HEVC%'
                 OR UPPER(name) LIKE '%H265%'
               THEN 1 ELSE 0 END,
          sort_order,
          id
        LIMIT 1
        """,
    )
    suspend fun findCompatibleMovieAlternative(
        sourceId: String,
        titlePrefix: String,
        excludeChannelId: String,
    ): ChannelEntity?

    @Query(
        """
        SELECT * FROM channels
        WHERE source_id = :sourceId
          AND content_type IN ('MOVIE', 'SERIES')
          AND year = :releaseYear
        ORDER BY COALESCE(rating, 0) DESC, COALESCE(added_at_epoch_seconds, 0) DESC, id
        LIMIT :limit
        """,
    )
    suspend fun loadForReleaseYear(sourceId: String, releaseYear: Int, limit: Int): List<ChannelEntity>

    @Query(
        """
        SELECT * FROM channels
        WHERE source_id = :sourceId
          AND content_type IN ('MOVIE', 'SERIES')
        ORDER BY COALESCE(added_at_epoch_seconds, 0) DESC, sort_order, id
        LIMIT :limit
        """,
    )
    suspend fun loadRecentlyAdded(sourceId: String, limit: Int): List<ChannelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(channels: List<ChannelEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAllBlocking(channels: List<ChannelEntity>)

    @Query("DELETE FROM channels WHERE source_id = :sourceId")
    suspend fun deleteForSource(sourceId: String)
}

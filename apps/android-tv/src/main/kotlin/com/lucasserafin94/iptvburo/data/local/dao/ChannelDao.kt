package com.lucasserafin94.iptvburo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lucasserafin94.iptvburo.data.local.entity.ChannelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {
    /**
     * One stable, non-empty image per category. `MIN` is intentional: it keeps the result
     * deterministic without loading every catalogue row merely to decorate the category grid.
     */
    @Query(
        """
        SELECT category_id AS categoryId, MIN(logo_url) AS artworkUrl
        FROM channels
        WHERE source_id = :sourceId
          AND (:contentType IS NULL OR content_type = :contentType)
          AND category_id IS NOT NULL
          AND logo_url IS NOT NULL
          AND TRIM(logo_url) != ''
        GROUP BY category_id
        """,
    )
    fun observeCategoryArtwork(
        sourceId: String,
        contentType: String?,
    ): Flow<List<CategoryArtworkProjection>>

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
        "SELECT * FROM channels WHERE source_id = :sourceId " +
            "AND provider_item_id = :providerItemId AND content_type = :contentType LIMIT 1",
    )
    suspend fun findByProviderItem(
        sourceId: String,
        providerItemId: String,
        contentType: String,
    ): ChannelEntity?

    /**
     * Films and series whose name contains [titleFragment], across every source.
     *
     * Deliberately loose: this only gathers *candidates*, and `LibraryMatchingPolicy` decides which
     * of them is confident enough to claim as the user's own copy. A tighter query here would hide
     * real matches from a policy built to judge them — a provider writing "O Poderoso Chefão [4K]"
     * still has the film, and an exact-title query would never find it.
     */
    @Query(
        """
        SELECT * FROM channels
        WHERE content_type IN ('MOVIE', 'SERIES')
          AND name LIKE '%' || :titleFragment || '%' COLLATE NOCASE
        ORDER BY sort_order, id
        LIMIT :limit
        """,
    )
    suspend fun findLibraryCandidates(titleFragment: String, limit: Int = 40): List<ChannelEntity>

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

data class CategoryArtworkProjection(
    val categoryId: String,
    val artworkUrl: String,
)

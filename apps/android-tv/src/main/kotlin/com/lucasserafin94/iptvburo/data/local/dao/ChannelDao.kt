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
    /**
     * Every distinct artwork address in the library, for the cache fill to warm.
     *
     * Only the one column, and `DISTINCT`: the fill needs addresses, not catalogue rows, and
     * loading forty thousand fully-formed entities to read one field from each would cost more
     * memory than the images it is about to fetch. Providers also reuse a placeholder across many
     * titles, so the distinct set is materially smaller than the row count.
     *
     * Ordered by `sort_order` so the first thing cached is the first thing seen: somebody who
     * starts the fill and opens the catalogue a minute later finds the top of the list already
     * drawn, rather than waiting for an arbitrary walk to reach it.
     */
    @Query(
        """
        SELECT DISTINCT logo_url
        FROM channels
        WHERE logo_url IS NOT NULL
          AND TRIM(logo_url) != ''
        ORDER BY sort_order
        LIMIT :limit
        """,
    )
    suspend fun artworkUrlsForCaching(limit: Int): List<String>

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

    /**
     * A page across every subscription, each title once.
     *
     * Somebody who buys a second list to fill the gaps in the first ends up switching between them
     * to find which has the film they want — work the app should be doing.
     *
     * The biggest list wins a shared title. `MIN(rank)` picks it: the ranking subquery numbers the
     * sources by size, so the row kept is the one from the largest catalogue. It is a heuristic,
     * but a stable one — choosing per title would make the same film play from a different provider
     * on different days for no reason the viewer could see.
     *
     * Grouped on the lowercased name rather than the provider's id, because two subscriptions
     * number the same film differently and grouping by id would keep both.
     */
    @Query(
        """
        SELECT c.* FROM channels c
        JOIN (
            SELECT LOWER(TRIM(name)) AS title, MIN(source_rank) AS best
            FROM channels
            JOIN (
                SELECT source_id AS ranked_source,
                       ROW_NUMBER() OVER (ORDER BY COUNT(*) DESC, source_id) AS source_rank
                FROM channels GROUP BY source_id
            ) ON ranked_source = source_id
            WHERE (:categoryId IS NULL OR category_id = :categoryId)
              AND (:contentType IS NULL OR content_type = :contentType)
            GROUP BY LOWER(TRIM(name))
        ) winners ON winners.title = LOWER(TRIM(c.name))
        JOIN (
            SELECT source_id AS ranked_source,
                   ROW_NUMBER() OVER (ORDER BY COUNT(*) DESC, source_id) AS source_rank
            FROM channels GROUP BY source_id
        ) ranks ON ranks.ranked_source = c.source_id AND ranks.source_rank = winners.best
        WHERE (:categoryId IS NULL OR c.category_id = :categoryId)
          AND (:contentType IS NULL OR c.content_type = :contentType)
        GROUP BY LOWER(TRIM(c.name))
        ORDER BY c.sort_order, c.name COLLATE NOCASE
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun loadMergedPage(
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

    /**
     * How many distinct titles every subscription holds between them.
     *
     * Counted on the same lowercased name the merged page groups by, so the total matches what the
     * grid can actually show — a count of rows would promise pages that are not there.
     */
    @Query(
        """
        SELECT COUNT(DISTINCT LOWER(TRIM(name))) FROM channels
        WHERE (:categoryId IS NULL OR category_id = :categoryId)
          AND (:contentType IS NULL OR content_type = :contentType)
        """,
    )
    suspend fun countMerged(
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

    /**
     * Anything whose name contains [query], for the search screen.
     *
     * Separate from [findLibraryCandidates] rather than a parameter on it, because the two want
     * opposite things. That one gathers *candidates* for a matching policy and is restricted to
     * films and series; this one answers a person typing into a box, so live channels count — "band"
     * is a channel, and a search that hid it would look broken.
     *
     * Films and series first, then live: someone searching by name is usually after a title, and a
     * catalogue with three hundred matching channels would otherwise bury the one film they meant.
     * Within a kind the catalogue's own order is kept, which is the order they see everywhere else.
     */
    @Query(
        """
        SELECT * FROM channels
        WHERE name LIKE '%' || :query || '%' COLLATE NOCASE
        ORDER BY
          CASE content_type
            WHEN 'MOVIE' THEN 0
            WHEN 'SERIES' THEN 1
            ELSE 2
          END,
          sort_order,
          id
        LIMIT :limit
        """,
    )
    suspend fun search(query: String, limit: Int = 200): List<ChannelEntity>

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

package com.lucasserafin94.iptvburo.data.repository

import com.lucasserafin94.iptvburo.domain.model.CatalogContentType
import com.lucasserafin94.iptvburo.domain.model.Category
import com.lucasserafin94.iptvburo.domain.model.Channel
import com.lucasserafin94.iptvburo.domain.model.Episode
import com.lucasserafin94.iptvburo.domain.model.MovieDetails
import com.lucasserafin94.iptvburo.domain.model.SeriesDetails
import com.lucasserafin94.iptvburo.domain.model.Source
import java.io.InputStream
import kotlinx.coroutines.flow.Flow

interface CatalogRepository {
    fun observeSources(): Flow<List<Source>>

    fun observeCategories(
        sourceId: String,
        contentType: CatalogContentType? = null,
    ): Flow<List<Category>>

    fun observeCategoryItemCounts(
        sourceId: String,
        contentType: CatalogContentType? = null,
    ): Flow<Map<String?, Int>>

    /** Representative item artwork keyed by category id. */
    fun observeCategoryArtwork(
        sourceId: String,
        contentType: CatalogContentType? = null,
    ): Flow<Map<String, String>> = kotlinx.coroutines.flow.flowOf(emptyMap())

    fun observeChannels(
        sourceId: String,
        categoryId: String? = null,
        contentType: CatalogContentType? = null,
    ): Flow<List<Channel>>

    suspend fun loadChannelsPage(
        sourceId: String,
        categoryId: String? = null,
        contentType: CatalogContentType? = null,
        offset: Int = 0,
        limit: Int = 200,
    ): CatalogPage

    /**
     * Cursor-based page used by production catalogs. The default keeps test doubles and older
     * repository implementations compatible while Room overrides it with a true keyset query.
     */
    suspend fun loadChannelsPageAfter(
        sourceId: String,
        categoryId: String? = null,
        contentType: CatalogContentType? = null,
        cursor: CatalogCursor? = null,
        limit: Int = 200,
    ): CatalogPage {
        val offset = cursor?.loadedItemCount ?: 0
        val page =
            loadChannelsPage(
                sourceId = sourceId,
                categoryId = categoryId,
                contentType = contentType,
                offset = offset,
                limit = limit,
            )
        return page.copy(
            nextCursor =
                if (page.hasMore) {
                    CatalogCursor(
                        sortOrder = null,
                        itemId = null,
                        loadedItemCount = offset + page.items.size,
                    )
                } else {
                    null
                },
        )
    }

    suspend fun getChannel(id: String): Channel?

    /**
     * Local items whose name contains [titleFragment], as candidates for library matching.
     *
     * Candidates, not matches: the caller hands these to `LibraryMatchingPolicy`, which decides
     * whether any of them is confident enough to claim as the user's own copy.
     */
    suspend fun findLibraryCandidates(titleFragment: String, limit: Int = 40): List<Channel>

    /**
     * Everything whose name contains [query], for the search screen.
     *
     * Unlike [findLibraryCandidates] this includes live channels and is not feeding a matching
     * policy: the caller is a person typing, and what comes back is shown to them directly.
     */
    suspend fun search(query: String, limit: Int = 200): List<Channel> = emptyList()

    /** Resolves persisted playback history without ever persisting a credential-bearing URL. */
    suspend fun findStoredContent(
        sourceId: String,
        providerItemId: String,
        contentType: CatalogContentType,
    ): Channel? = null

    suspend fun findCompatibleMovieAlternative(
        sourceId: String,
        titlePrefix: String,
        excludeChannelId: String,
    ): Channel? = null

    suspend fun loadForReleaseYear(sourceId: String, releaseYear: Int, limit: Int = 24): List<Channel> = emptyList()

    suspend fun loadRecentlyAdded(sourceId: String, limit: Int = 24): List<Channel> = emptyList()

    /** Optional now/next data; an empty result must never block live playback. */
    suspend fun loadShortEpg(sourceId: String, providerStreamId: String): LiveEpg = LiveEpg()

    suspend fun importPlaylist(
        displayName: String,
        inputStream: InputStream,
    ): PlaylistImportResult

    suspend fun importXtream(
        request: XtreamImportRequest,
        onProgress: (XtreamImportStage) -> Unit = {},
    ): XtreamImportResult

    /**
     * Imports a Stalker/Ministra portal.
     *
     * Default implementation throws so existing fakes in tests keep compiling; the Room repository
     * overrides it.
     */
    suspend fun importStalker(
        request: StalkerImportRequest,
        onProgress: (XtreamImportStage) -> Unit = {},
    ): XtreamImportResult = throw UnsupportedOperationException("Stalker import is unavailable.")

    suspend fun loadSeriesDetails(
        sourceId: String,
        providerSeriesId: String,
    ): SeriesDetails

    suspend fun loadMovieDetails(
        sourceId: String,
        providerMovieId: String,
    ): MovieDetails = throw UnsupportedOperationException("Movie details are unavailable.")

    /**
     * Resolves a short-lived playback target only after the user chooses an episode.
     *
     * [Episode] carries provider metadata, never a credential-bearing playback URL.
     */
    suspend fun resolveEpisode(episode: Episode): Channel
}

data class LiveProgram(
    val title: String,
    val description: String? = null,
    val startEpochSeconds: Long? = null,
    val endEpochSeconds: Long? = null,
)

data class LiveEpg(
    val now: LiveProgram? = null,
    val next: LiveProgram? = null,
    /**
     * Everything the provider returned, in order, including what is already over.
     *
     * The response has always carried the whole schedule — up to eight entries with start and end
     * times — and only "now" and "next" were kept, so the rest was fetched and thrown away. The
     * guide needs the list; the two fields above stay because the player's header uses them.
     */
    val schedule: List<LiveProgram> = emptyList(),
)

data class PlaylistImportResult(
    val sourceId: String,
    val importedChannelCount: Int,
    val importedCategoryCount: Int,
    val parserWarningCount: Int,
    val skippedChannelCount: Int,
)

data class CatalogPage(
    val items: List<Channel>,
    val offset: Int,
    val totalCount: Int,
    val nextCursor: CatalogCursor? = null,
) {
    val hasMore: Boolean
        get() = offset + items.size < totalCount
}

data class CatalogCursor(
    val sortOrder: Int?,
    val itemId: String?,
    val loadedItemCount: Int,
)

data class XtreamImportRequest(
    val displayName: String,
    val serverUrl: String,
    val username: String,
    val password: String,
) {
    override fun toString(): String =
        "XtreamImportRequest(" +
            "displayName=$displayName, serverUrl=<redacted>, " +
            "username=<redacted>, password=<redacted>)"
}

/**
 * A Stalker portal import.
 *
 * [macAddress] is the credential, so it is redacted in [toString] exactly as passwords are
 * elsewhere. [username] and [password] are optional because most portals gate on the MAC alone.
 */
data class StalkerImportRequest(
    val displayName: String,
    val portalUrl: String,
    val macAddress: String,
    val username: String? = null,
    val password: String? = null,
) {
    override fun toString(): String =
        "StalkerImportRequest(" +
            "displayName=$displayName, portalUrl=<redacted>, macAddress=<redacted>)"
}

data class XtreamImportResult(
    val sourceId: String,
    val liveCount: Int,
    val movieCount: Int,
    val seriesCount: Int,
    val categoryCount: Int,
    val skippedItemCount: Int,
) {
    val totalItemCount: Int
        get() = liveCount + movieCount + seriesCount
}

enum class XtreamImportStage {
    AUTHENTICATING,
    CATEGORIES,
    LIVE,
    MOVIES,
    SERIES,
    SAVING,
}

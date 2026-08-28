package com.lucasserafin94.iptvburo.desktop.data

import com.lucasserafin94.iptvburo.desktop.model.XtreamCatalogPage
import com.lucasserafin94.iptvburo.desktop.model.XtreamPlaybackTarget
import com.lucasserafin94.iptvburo.desktop.model.XtreamSessionSummary
import com.lucasserafin94.iptvburo.desktop.security.XtreamLoginInput
import com.lucasserafin94.iptvburo.domain.model.ContentIdentity
import com.lucasserafin94.iptvburo.domain.model.LibraryCandidate
import com.lucasserafin94.iptvburo.xtream.XtreamCatalogItem
import com.lucasserafin94.iptvburo.xtream.XtreamCategory
import com.lucasserafin94.iptvburo.xtream.XtreamContentType
import com.lucasserafin94.iptvburo.xtream.XtreamMovieDetails
import com.lucasserafin94.iptvburo.xtream.XtreamSeriesDetails
import com.lucasserafin94.iptvburo.xtream.XtreamShortEpg
import java.net.URI

/**
 * Which protocol the current subscription speaks, chosen per connection.
 *
 * The app holds one repository for its whole life, so a viewer with an Xtream account and a
 * Stalker portal could otherwise only ever use whichever was decided at startup. This delegates
 * instead: every call goes to whichever protocol the subscription now open actually uses.
 *
 * Deliberately dumb. It holds no catalogue and no credentials of its own — both live in the
 * delegate — so there is no second copy of a subscription's data to keep in step, and no second
 * place a password could be left behind.
 */
class SwitchingCatalogueRepository(
    private val xtream: CatalogueRepository = SessionXtreamRepository(),
    private val stalker: CatalogueRepository = StalkerCatalogueRepository(),
) : CatalogueRepository {
    /**
     * The protocol the open subscription speaks.
     *
     * Xtream until told otherwise, because that is what the great majority of subscriptions are
     * and what every existing install already holds.
     */
    @Volatile
    private var active: CatalogueRepository = xtream

    /**
     * The merging repository, when this app was started with one.
     *
     * Exposed so the startup can add the viewer's other subscriptions to it. Null in the ordinary
     * single-list case, which is the answer the caller wants: nothing to add.
     */
    val merging: MergedCatalogueRepository?
        get() = xtream as? MergedCatalogueRepository

    /**
     * Points the next connection at a portal rather than an Xtream server.
     *
     * Called by the source form before it connects. Anything already loaded is cleared first: a
     * catalogue from the previous subscription would otherwise stay in memory behind a session
     * that no longer owns it, and a search would return titles the new account cannot play.
     */
    fun useStalker() {
        if (active !== stalker) {
            active.clear()
            active = stalker
        }
    }

    /** The other direction, on the same terms. */
    fun useXtream() {
        if (active !== xtream) {
            active.clear()
            active = xtream
        }
    }

    /** Whether the open subscription is a portal. Read by the screen that has to say so. */
    val isStalker: Boolean
        get() = active === stalker

    override fun authenticateAndLoadInitial(
        input: XtreamLoginInput,
        onProgress: (fraction: Float, detail: SessionXtreamRepository.XtreamLoadStage) -> Unit,
    ): XtreamSessionSummary = active.authenticateAndLoadInitial(input, onProgress)

    override fun loadCatalog(
        contentType: XtreamContentType,
        forceRefresh: Boolean,
        onProgress: CatalogLoadListener?,
    ): XtreamSessionSummary = active.loadCatalog(contentType, forceRefresh, onProgress)

    override fun categories(contentType: XtreamContentType): List<XtreamCategory> =
        active.categories(contentType)

    override fun itemByProviderId(
        contentType: XtreamContentType,
        providerId: String,
    ): XtreamCatalogItem? = active.itemByProviderId(contentType, providerId)

    override fun itemByContentKey(
        contentType: XtreamContentType,
        contentKey: String,
    ): XtreamCatalogItem? = active.itemByContentKey(contentType, contentKey)

    override fun page(
        contentType: XtreamContentType,
        categoryId: String?,
        query: String,
        requestedPage: Int,
        pageSize: Int,
        releaseYear: Int?,
        minimumRating: Double?,
        allowedIdentities: Set<ContentIdentity>?,
        kidsMode: Boolean,
        lockedCategoryIds: Set<String>,
        collapseDuplicates: Boolean,
        allowedLocalIds: Set<String>?,
    ): XtreamCatalogPage =
        active.page(
            contentType = contentType,
            categoryId = categoryId,
            query = query,
            requestedPage = requestedPage,
            pageSize = pageSize,
            releaseYear = releaseYear,
            minimumRating = minimumRating,
            allowedIdentities = allowedIdentities,
            kidsMode = kidsMode,
            lockedCategoryIds = lockedCategoryIds,
            collapseDuplicates = collapseDuplicates,
            allowedLocalIds = allowedLocalIds,
        )

    override fun seriesDetails(seriesId: String): XtreamSeriesDetails = active.seriesDetails(seriesId)

    override fun search(query: String, limit: Int): List<XtreamCatalogItem> =
        active.search(query, limit)

    override fun findByTitles(normalisedTitles: Set<String>, limit: Int): List<XtreamCatalogItem> =
        active.findByTitles(normalisedTitles, limit)

    override fun releasesForYear(
        type: XtreamContentType,
        year: Int,
        limit: Int,
        kidsMode: Boolean,
        lockedCategoryIds: Set<String>,
        rotation: Int,
    ): List<XtreamCatalogItem> =
        active.releasesForYear(type, year, limit, kidsMode, lockedCategoryIds, rotation)

    override fun isAllowedForBrowsing(
        item: XtreamCatalogItem,
        kidsMode: Boolean,
        lockedCategoryIds: Set<String>,
    ): Boolean = active.isAllowedForBrowsing(item, kidsMode, lockedCategoryIds)

    override fun libraryMatchCandidates(
        kidsMode: Boolean,
        lockedCategoryIds: Set<String>,
        lockedCategoryIdsByContentType: Map<XtreamContentType, Set<String>>,
    ): List<LibraryCandidate> =
        active.libraryMatchCandidates(kidsMode, lockedCategoryIds, lockedCategoryIdsByContentType)

    override fun movieDetails(movieId: String): XtreamMovieDetails = active.movieDetails(movieId)

    override fun shortEpg(streamId: String): XtreamShortEpg = active.shortEpg(streamId)

    override fun buildConfirmedPlaybackUri(target: XtreamPlaybackTarget): URI =
        active.buildConfirmedPlaybackUri(target)

    override fun measureProviderTransfer(budgetMillis: Long) =
        active.measureProviderTransfer(budgetMillis)

    override fun measureProviderLatency(attempts: Int) = active.measureProviderLatency(attempts)

    override fun placeholderArtworkUrls(): Set<String> = active.placeholderArtworkUrls()

    override fun summary(): XtreamSessionSummary? = active.summary()

    override fun clearCatalogCache() = active.clearCatalogCache()

    override fun clearIncludingDiskCache() = active.clearIncludingDiskCache()

    /**
     * Ends the session, on both delegates.
     *
     * Both rather than only the active one: signing out has to leave nothing behind, and a
     * catalogue held by the protocol that is not currently selected is still somebody's
     * subscription sitting in memory.
     */
    override fun clear() {
        xtream.clear()
        stalker.clear()
        active = xtream
    }

    /** Never the delegates, which carry credentials in their own state. */
    override fun toString(): String = "SwitchingCatalogueRepository(stalker=$isStalker)"
}

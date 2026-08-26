package com.lucasserafin94.iptvburo.desktop.data

import com.lucasserafin94.iptvburo.desktop.model.XtreamCatalogPage
import com.lucasserafin94.iptvburo.desktop.model.XtreamPlaybackTarget
import com.lucasserafin94.iptvburo.desktop.model.XtreamSessionSummary
import com.lucasserafin94.iptvburo.desktop.security.XtreamLoginInput
import com.lucasserafin94.iptvburo.domain.model.LibraryCandidate
import com.lucasserafin94.iptvburo.domain.model.ContentIdentity
import com.lucasserafin94.iptvburo.xtream.XtreamCatalogItem
import com.lucasserafin94.iptvburo.xtream.XtreamCategory
import com.lucasserafin94.iptvburo.xtream.XtreamContentType
import com.lucasserafin94.iptvburo.xtream.XtreamMovieDetails
import com.lucasserafin94.iptvburo.xtream.XtreamSeriesDetails
import com.lucasserafin94.iptvburo.xtream.XtreamShortEpg
import java.net.URI

/**
 * What the app needs from a subscription, whichever protocol it speaks.
 *
 * The app talked to [SessionXtreamRepository] directly — a concrete class, named after one
 * protocol — so a Stalker subscription had nowhere to arrive. The client for it has existed and
 * passed its tests for a long time, and the television has shipped Stalker for longer still; the
 * desktop simply had no seam to plug it into.
 *
 * This is that seam, and it is deliberately the existing shape rather than a tidier one. Every
 * signature is lifted from the class that already implements it, defaults included, so extracting
 * the interface changes no behaviour and no call site. A cleaner contract designed in the abstract
 * would have meant rewriting 49 calls at the same time as introducing a second protocol, and
 * mixing those two is how a working app gets broken in the middle.
 *
 * The names still say "Xtream" in their types. Those are the shared catalogue models — a category,
 * an item, a page — which Stalker fills in just as well; renaming them is a separate change that
 * would touch far more than this.
 */
/**
 * The page size a caller gets when it does not ask for one.
 *
 * Repeated from [SessionXtreamRepository]'s own constant rather than shared, because that one is
 * private to the class and widening it to satisfy an interface default would be the tail wagging
 * the dog. A test pins the two together, so a change to either is caught rather than producing an
 * interface whose default quietly disagrees with its implementation.
 */
const val CATALOGUE_PAGE_SIZE = 80

/** As above, for search. */
const val CATALOGUE_SEARCH_LIMIT = 200

interface CatalogueRepository {
    fun authenticateAndLoadInitial(
        input: XtreamLoginInput,
        onProgress: (fraction: Float, detail: SessionXtreamRepository.XtreamLoadStage) -> Unit = { _, _ -> },
    ): XtreamSessionSummary

    fun loadCatalog(
        contentType: XtreamContentType,
        forceRefresh: Boolean = false,
        onProgress: CatalogLoadListener? = null,
    ): XtreamSessionSummary

    fun categories(contentType: XtreamContentType): List<XtreamCategory>

    fun itemByProviderId(contentType: XtreamContentType, providerId: String): XtreamCatalogItem?

    fun itemByContentKey(contentType: XtreamContentType, contentKey: String): XtreamCatalogItem?

    fun page(
        contentType: XtreamContentType,
        categoryId: String?,
        query: String,
        requestedPage: Int,
        pageSize: Int = CATALOGUE_PAGE_SIZE,
        releaseYear: Int? = null,
        minimumRating: Double? = null,
        allowedIdentities: Set<ContentIdentity>? = null,
        kidsMode: Boolean = false,
        lockedCategoryIds: Set<String> = emptySet(),
        collapseDuplicates: Boolean = false,
        allowedLocalIds: Set<String>? = null,
    ): XtreamCatalogPage

    fun seriesDetails(seriesId: String): XtreamSeriesDetails

    fun search(
        query: String,
        limit: Int = CATALOGUE_SEARCH_LIMIT,
    ): List<XtreamCatalogItem>

    fun findByTitles(
        normalisedTitles: Set<String>,
        limit: Int,
    ): List<XtreamCatalogItem>

    fun releasesForYear(
        type: XtreamContentType,
        year: Int,
        limit: Int,
        kidsMode: Boolean,
        lockedCategoryIds: Set<String> = emptySet(),
        rotation: Int = 0,
    ): List<XtreamCatalogItem>

    fun isAllowedForBrowsing(
        item: XtreamCatalogItem,
        kidsMode: Boolean,
        lockedCategoryIds: Set<String>,
    ): Boolean

    fun libraryMatchCandidates(
        kidsMode: Boolean = false,
        lockedCategoryIds: Set<String> = emptySet(),
        lockedCategoryIdsByContentType: Map<XtreamContentType, Set<String>> = emptyMap(),
    ): List<LibraryCandidate>

    fun movieDetails(movieId: String): XtreamMovieDetails

    fun shortEpg(streamId: String): XtreamShortEpg

    /**
     * The address to play, resolved as late as possible.
     *
     * Named "confirmed" because it carries the account's credentials: it is built at the moment
     * of playback and never stored, so it cannot reach a log, a crash dump or a recomposition
     * snapshot. Any implementation of this interface has to keep that promise.
     */
    fun buildConfirmedPlaybackUri(target: XtreamPlaybackTarget): URI

    fun summary(): XtreamSessionSummary?

    fun clearCatalogCache()

    fun clearIncludingDiskCache()

    fun clear()
}

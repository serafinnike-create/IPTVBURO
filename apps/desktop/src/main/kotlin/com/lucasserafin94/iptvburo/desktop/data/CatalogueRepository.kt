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
/**
 * How long a diagnostic transfer may run.
 *
 * Long enough to outlast the local buffer and measure the network, short enough that somebody
 * waiting on the screen does not think it has hung. The test stops at the budget and reports what
 * it read, rather than waiting for a fixed size that a slow line would never finish.
 */
const val DIAGNOSTIC_BUDGET_MILLIS = 6_000L

/** Enough round trips to see jitter and loss without making the screen wait. */
const val DIAGNOSTIC_PING_ATTEMPTS = 8

/**
 * Bytes moved and the time they took, with no trace of where they came from.
 *
 * The address carried the account's credentials, so it stays inside the repository and only this
 * reaches the screen.
 */
data class TransferSample(
    val bytes: Long,
    val milliseconds: Long,
)

/**
 * Round trips to the provider: how long they took, and how many never answered.
 *
 * Failures are counted rather than thrown. A connection dropping one request in ten is exactly what
 * the viewer needs told, and an exception would lose that in favour of "the test failed".
 */
data class LatencySample(
    val samplesMillis: List<Int>,
    val attempted: Int,
) {
    /** The middle sample, which one slow outlier cannot drag the way an average can. */
    val medianMillis: Int?
        get() = samplesMillis.sorted().takeIf { it.isNotEmpty() }?.let { it[it.size / 2] }

    /** How many round trips never came back, as a percentage of those tried. */
    val lossPercent: Double
        get() = if (attempted <= 0) 0.0 else (attempted - samplesMillis.size) * 100.0 / attempted
}

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

    /**
     * Measures the connection to the provider, without exposing where it went.
     *
     * The diagnostics screen must not receive a URL: it would be a credentialed address travelling
     * into UI state, where a recomposition snapshot or a crash dump could keep it. So the request
     * is made in here, and only the reading comes back.
     *
     * Returns the bytes read and the milliseconds they took, or null when there is no session to
     * measure. The caller decides what that means — see
     * [com.lucasserafin94.iptvburo.domain.model.ConnectionDiagnostics].
     */
    fun measureProviderTransfer(budgetMillis: Long = DIAGNOSTIC_BUDGET_MILLIS): TransferSample? = null

    /**
     * Round-trip time to the provider, in milliseconds, across [attempts] tries.
     *
     * Separate from the transfer because latency and throughput fail independently: a fast line
     * with terrible latency still stalls on every channel change, and a viewer told only their
     * speed would conclude the app is at fault.
     */
    fun measureProviderLatency(attempts: Int = DIAGNOSTIC_PING_ATTEMPTS): LatencySample? = null

    /**
     * Cover addresses this subscription hands out for thousands of titles at once.
     *
     * A provider that files a whole category under one generic card leaves every row technically
     * covered, so nothing downstream reaches its fallback and the grid draws the same picture
     * hundreds of times. Recognised by how many titles share an address — see
     * [com.lucasserafin94.iptvburo.domain.model.PlaceholderArtwork].
     *
     * Empty for a subscription that gives each title its own cover, which is most of them.
     */
    fun placeholderArtworkUrls(): Set<String> = emptySet()

    /**
     * The same title from a different subscription, or null when there is no other.
     *
     * A stream that fails is exactly when the other list's copy is worth having — half the value of
     * owning a second subscription, and the viewer should never have to know it happened.
     *
     * @param exclude how many subscriptions to skip, so a second failure moves on again rather than
     *   offering the same dead stream for ever.
     */
    fun buildAlternativePlaybackUri(
        target: XtreamPlaybackTarget,
        exclude: Int,
    ): URI? = null

    fun summary(): XtreamSessionSummary?

    fun clearCatalogCache()

    fun clearIncludingDiskCache()

    fun clear()
}

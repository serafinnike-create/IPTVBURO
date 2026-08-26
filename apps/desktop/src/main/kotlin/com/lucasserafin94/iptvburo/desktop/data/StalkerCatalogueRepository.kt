package com.lucasserafin94.iptvburo.desktop.data

import com.lucasserafin94.iptvburo.desktop.model.XtreamCatalogPage
import com.lucasserafin94.iptvburo.desktop.model.XtreamPlaybackTarget
import com.lucasserafin94.iptvburo.desktop.model.XtreamSessionSummary
import com.lucasserafin94.iptvburo.desktop.security.XtreamLoginInput
import com.lucasserafin94.iptvburo.domain.model.ContentIdentity
import com.lucasserafin94.iptvburo.domain.model.LibraryCandidate
import com.lucasserafin94.iptvburo.domain.model.MatchKind
import com.lucasserafin94.iptvburo.stalker.StalkerCatalogItem
import com.lucasserafin94.iptvburo.stalker.StalkerClient
import com.lucasserafin94.iptvburo.stalker.StalkerContentType
import com.lucasserafin94.iptvburo.stalker.StalkerCredentials
import com.lucasserafin94.iptvburo.stalker.StalkerSession
import com.lucasserafin94.iptvburo.xtream.XtreamAccount
import com.lucasserafin94.iptvburo.xtream.XtreamCatalogItem
import com.lucasserafin94.iptvburo.xtream.XtreamCategory
import com.lucasserafin94.iptvburo.xtream.XtreamContentType
import com.lucasserafin94.iptvburo.xtream.XtreamEpgProgram
import com.lucasserafin94.iptvburo.xtream.XtreamMovieDetails
import com.lucasserafin94.iptvburo.xtream.XtreamSeriesDetails
import com.lucasserafin94.iptvburo.xtream.XtreamShortEpg
import java.net.URI
import java.util.Locale

/**
 * A Stalker/Ministra subscription, answering the same questions an Xtream one does.
 *
 * The portal speaks a different protocol — a MAC address instead of a username, a handshake token
 * instead of a query string, and an opaque command per item instead of a predictable URL — but the
 * app upstream neither knows nor needs to. Everything here converts between the portal's shapes
 * and the catalogue models the rest of the desktop already reads.
 *
 * ## The command, and why it is never written down
 *
 * A Stalker item does not carry a playable address. It carries a command like
 * `ffmpeg http://localhost/ch/123_`, which the portal turns into a real URL only when asked, and
 * that URL is short-lived and single-use. So the command is held in memory beside the catalogue
 * and resolved at the moment of playback — the same promise [CatalogueRepository] makes about
 * Xtream's credential-bearing URLs, kept the same way.
 *
 * ## What this deliberately does not do
 *
 * Stalker portals expose no episode tree and no programme guide the way Xtream does. Those methods
 * answer honestly with nothing rather than inventing a shape: a series that reports zero episodes
 * is visibly empty, while one that reports fabricated episodes is a screen full of titles that
 * refuse to play.
 */
class StalkerCatalogueRepository(
    private val client: StalkerClient = StalkerClient(),
) : CatalogueRepository {
    private val lock = Any()

    private var credentials: StalkerCredentials? = null
    private var session: StalkerSession? = null
    private var sourceId: String = ""

    /** The catalogue, per content type, as the portal reported it. */
    private val catalogue = mutableMapOf<XtreamContentType, List<XtreamCatalogItem>>()
    private val categoriesByType = mutableMapOf<XtreamContentType, List<XtreamCategory>>()

    /**
     * The portal command for each item, held only in memory.
     *
     * Keyed by content type and provider id, which is what a playback target carries. Never
     * persisted and never logged: it is the portal's own handle on a stream, and writing it to
     * disk would leave a durable pointer at somebody's subscription.
     */
    private val commands = mutableMapOf<String, String>()

    override fun authenticateAndLoadInitial(
        input: XtreamLoginInput,
        onProgress: (fraction: Float, detail: SessionXtreamRepository.XtreamLoadStage) -> Unit,
    ): XtreamSessionSummary {
        // The login form collects a server, a username and a password. A portal wants an address
        // and a MAC, so the MAC travels in the username field — which is what the Android and
        // television forms already do, and why this reads them in that order.
        val portal = input.copyServer().concatToString()
        val mac = input.copyUsername().concatToString()
        input.clear()

        onProgress(0.05f, SessionXtreamRepository.XtreamLoadStage.Authenticating)
        val credential = StalkerCredentials(portalUrl = portal, macAddress = mac)
        val opened = client.handshake(credential)
        val account = client.account(credential, opened)

        synchronized(lock) {
            credentials = credential
            session = opened
            // Derived from the portal and the MAC so the same subscription keeps its stored
            // favourites and progress across sessions. Hashed rather than stored raw: this id
            // reaches preferences, and a MAC there would be a device identifier at rest.
            sourceId = "stalker:" + (portal + "|" + mac).hashCode().toUInt().toString(16)
            catalogue.clear()
            categoriesByType.clear()
            commands.clear()
        }

        XtreamContentType.entries.forEachIndexed { index, contentType ->
            onProgress(
                0.2f + 0.8f * index / XtreamContentType.entries.size,
                SessionXtreamRepository.XtreamLoadStage.Categories(contentType),
            )
            loadCatalog(contentType)
        }

        return summary()
            ?: XtreamSessionSummary(
                sourceId = sourceId,
                account = account.toXtreamAccount(),
                loadedItemCount = 0,
                loadedContentTypes = emptySet(),
            )
    }

    override fun loadCatalog(
        contentType: XtreamContentType,
        forceRefresh: Boolean,
        onProgress: CatalogLoadListener?,
    ): XtreamSessionSummary {
        val credential = requireCredentials()
        val opened = requireSession()
        val stalkerType = contentType.toStalker()

        if (!forceRefresh && synchronized(lock) { catalogue.containsKey(contentType) }) {
            return summary() ?: emptySummary()
        }

        val categories = client.categories(credential, opened, stalkerType)
        val collected = mutableListOf<XtreamCatalogItem>()

        // Page by page, in order, stopping when the portal stops giving. A portal reports its own
        // total, but not reliably — several return a total larger than they will actually serve —
        // so the empty page is what ends the walk rather than the arithmetic.
        var pageNumber = 1
        while (pageNumber <= MAX_PAGES) {
            val page = client.page(credential, opened, stalkerType, categoryId = null, page = pageNumber)
            if (page.items.isEmpty()) break
            page.items.forEach { item ->
                collected += item.toXtreamItem(contentType)
                item.command?.let { command ->
                    synchronized(lock) { commands[commandKey(contentType, item.providerId)] = command }
                }
            }
            onProgress?.invoke(CatalogLoadProgress(collected.size, System.currentTimeMillis()))
            pageNumber += 1
        }

        synchronized(lock) {
            catalogue[contentType] = collected
            categoriesByType[contentType] =
                categories.map { category ->
                    XtreamCategory(
                        providerId = category.providerId,
                        name = category.name,
                        contentType = contentType,
                    )
                }
        }
        return summary() ?: emptySummary()
    }

    override fun categories(contentType: XtreamContentType): List<XtreamCategory> =
        synchronized(lock) { categoriesByType[contentType].orEmpty() }

    override fun itemByProviderId(
        contentType: XtreamContentType,
        providerId: String,
    ): XtreamCatalogItem? =
        synchronized(lock) { catalogue[contentType]?.firstOrNull { it.providerId == providerId } }

    override fun itemByContentKey(
        contentType: XtreamContentType,
        contentKey: String,
    ): XtreamCatalogItem? =
        synchronized(lock) {
            catalogue[contentType]?.firstOrNull { it.contentIdentity().key == contentKey }
        }

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
    ): XtreamCatalogPage {
        val all = synchronized(lock) { catalogue[contentType].orEmpty() }
        val needle = query.trim().lowercase(Locale.ROOT)

        var matches =
            all.filter { item ->
                if (categoryId != null && categoryId !in item.categoryIds) return@filter false
                if (needle.isNotEmpty() && !item.name.lowercase(Locale.ROOT).contains(needle)) {
                    return@filter false
                }
                if (releaseYear != null && item.year != releaseYear) return@filter false
                if (minimumRating != null && (item.rating ?: 0.0) < minimumRating) return@filter false
                if (allowedIdentities != null && item.contentIdentity() !in allowedIdentities) {
                    return@filter false
                }
                if (allowedLocalIds != null && item.contentIdentity().key !in allowedLocalIds) {
                    return@filter false
                }
                isAllowedForBrowsing(item, kidsMode, lockedCategoryIds)
            }

        if (collapseDuplicates) {
            // The first copy of each title wins, and the portal's own order decides which that is,
            // so a page turn does not reshuffle what the viewer just looked at.
            matches = matches.distinctBy { it.name.lowercase(Locale.ROOT) }
        }

        val total = matches.size
        val safePageSize = pageSize.coerceAtLeast(1)
        val lastPage = if (total == 0) 0 else (total - 1) / safePageSize
        val pageIndex = requestedPage.coerceIn(0, maxOf(lastPage, 0))
        val from = pageIndex * safePageSize
        return XtreamCatalogPage(
            items = if (from >= total) emptyList() else matches.subList(from, minOf(from + safePageSize, total)),
            pageIndex = pageIndex,
            pageSize = safePageSize,
            totalMatches = total,
        )
    }

    /**
     * Portals do not publish an episode tree, so a series reports none.
     *
     * Answering with nothing is the honest result: a series that shows zero episodes is visibly
     * empty, while one that shows invented episodes is a screen of titles that refuse to play.
     */
    override fun seriesDetails(seriesId: String): XtreamSeriesDetails {
        val item = itemByProviderId(XtreamContentType.SERIES, seriesId)
        return XtreamSeriesDetails(
            providerId = seriesId,
            title = item?.name.orEmpty(),
            plot = null,
            artworkUrl = item?.artworkUrl,
            backdropUrls = emptyList(),
            episodes = emptyList(),
            rating = item?.rating,
        )
    }

    override fun search(query: String, limit: Int): List<XtreamCatalogItem> {
        val needle = query.trim().lowercase(Locale.ROOT)
        if (needle.length < MIN_SEARCH_QUERY) return emptyList()
        return synchronized(lock) { catalogue.values.flatten() }
            .asSequence()
            .filter { it.name.lowercase(Locale.ROOT).contains(needle) }
            .take(limit)
            .toList()
    }

    override fun findByTitles(normalisedTitles: Set<String>, limit: Int): List<XtreamCatalogItem> {
        if (normalisedTitles.isEmpty()) return emptyList()
        return synchronized(lock) { catalogue.values.flatten() }
            .asSequence()
            .filter { item -> item.name.lowercase(Locale.ROOT).trim() in normalisedTitles }
            .take(limit)
            .toList()
    }

    override fun releasesForYear(
        type: XtreamContentType,
        year: Int,
        limit: Int,
        kidsMode: Boolean,
        lockedCategoryIds: Set<String>,
        rotation: Int,
    ): List<XtreamCatalogItem> {
        val matching =
            synchronized(lock) { catalogue[type].orEmpty() }
                .filter { it.year == year && isAllowedForBrowsing(it, kidsMode, lockedCategoryIds) }
        if (matching.isEmpty()) return emptyList()
        // Rotated rather than re-sorted, so the shelf shows something different each day without
        // the order jumping about within a single session.
        val offset = if (matching.isEmpty()) 0 else (rotation % matching.size + matching.size) % matching.size
        return (matching.drop(offset) + matching.take(offset)).take(limit)
    }

    override fun isAllowedForBrowsing(
        item: XtreamCatalogItem,
        kidsMode: Boolean,
        lockedCategoryIds: Set<String>,
    ): Boolean {
        if (item.categoryIds.any { it in lockedCategoryIds }) return false
        if (!kidsMode) return true
        // In Kids mode a category has to be known and permitted, so an item filed nowhere stays
        // out. Anything else lets an unfiled adult title through the one mode meant to stop it.
        val names = synchronized(lock) { categoriesByType[item.contentType].orEmpty() }
        val allowed =
            names
                .filter { category -> category.providerId in item.categoryIds }
                .map { it.name.lowercase(Locale.ROOT) }
        return allowed.isNotEmpty() && allowed.none { name -> KIDS_BLOCKED.any { word -> name.contains(word) } }
    }

    override fun libraryMatchCandidates(
        kidsMode: Boolean,
        lockedCategoryIds: Set<String>,
        lockedCategoryIdsByContentType: Map<XtreamContentType, Set<String>>,
    ): List<LibraryCandidate> =
        synchronized(lock) { catalogue.toMap() }
            .flatMap { (contentType, items) ->
                val locked = lockedCategoryIdsByContentType[contentType] ?: lockedCategoryIds
                items
                    .filter { isAllowedForBrowsing(it, kidsMode, locked) }
                    .map { item ->
                        LibraryCandidate(
                            localContentId = item.contentIdentity().key,
                            title = item.name,
                            year = item.year,
                            kind =
                                if (contentType == XtreamContentType.SERIES) {
                                    MatchKind.SERIES
                                } else {
                                    MatchKind.MOVIE
                                },
                        )
                    }
            }

    /** Portals carry no separate film record; what the catalogue holds is all there is. */
    override fun movieDetails(movieId: String): XtreamMovieDetails {
        val item = itemByProviderId(XtreamContentType.MOVIE, movieId)
        return XtreamMovieDetails(
            providerId = movieId,
            title = item?.name.orEmpty(),
            plot = null,
            cast = null,
            director = null,
            genre = null,
            duration = null,
            releaseDate = item?.year?.toString(),
            country = null,
            rating = item?.rating,
            artworkUrl = item?.artworkUrl,
            backdropUrls = emptyList(),
            youtubeTrailerId = null,
            containerExtension = null,
        )
    }

    /** Portals expose no guide in this API, so the channel reports none rather than a fake one. */
    override fun shortEpg(streamId: String): XtreamShortEpg =
        XtreamShortEpg(programs = emptyList<XtreamEpgProgram>(), skippedProgramCount = 0)

    /**
     * Asks the portal to turn this item's command into a playable address.
     *
     * Resolved here and never before: the portal issues a short-lived, single-use URL, so one
     * fetched early is usually dead by the time somebody presses play. The command it is built
     * from lives only in memory for the same reason the Xtream URL is built late — it is a handle
     * on somebody's subscription.
     */
    override fun buildConfirmedPlaybackUri(target: XtreamPlaybackTarget): URI {
        val credential = requireCredentials()
        val opened = requireSession()
        val (contentType, providerId) =
            when (target) {
                is XtreamPlaybackTarget.CatalogItem -> target.contentType to target.providerId
                // A portal has no episode addressing and no timeshift endpoint. Refused rather
                // than approximated: a plausible-looking URL that cannot play is worse than a
                // button that says it cannot.
                is XtreamPlaybackTarget.Episode ->
                    error("A Stalker portal does not address episodes individually.")
                is XtreamPlaybackTarget.CatchUp ->
                    error("A Stalker portal does not offer catch-up through this API.")
            }

        val command =
            synchronized(lock) { commands[commandKey(contentType, providerId)] }
                ?: error("No portal command is held for this item.")
        val item =
            StalkerCatalogItem(
                providerId = providerId,
                name = "",
                contentType = contentType.toStalker(),
                categoryId = null,
                artworkUrl = null,
                year = null,
                rating = null,
                command = command,
            )
        return URI(client.resolvePlaybackUrl(credential, opened, item))
    }

    override fun summary(): XtreamSessionSummary? =
        synchronized(lock) {
            if (credentials == null) return@synchronized null
            XtreamSessionSummary(
                sourceId = sourceId,
                account =
                    XtreamAccount(
                        authenticated = true,
                        status = null,
                        isTrial = null,
                        activeConnections = null,
                        maximumConnections = null,
                        allowedOutputFormats = emptySet(),
                    ),
                loadedItemCount = catalogue.values.sumOf { it.size },
                loadedContentTypes = catalogue.keys.toSet(),
            )
        }

    override fun clearCatalogCache() {
        synchronized(lock) {
            catalogue.clear()
            categoriesByType.clear()
            commands.clear()
        }
    }

    /** A portal catalogue is never written to disk, so this is the same as clearing memory. */
    override fun clearIncludingDiskCache() = clearCatalogCache()

    override fun clear() {
        synchronized(lock) {
            credentials = null
            session = null
            sourceId = ""
            catalogue.clear()
            categoriesByType.clear()
            commands.clear()
        }
    }

    private fun requireCredentials(): StalkerCredentials =
        synchronized(lock) { credentials } ?: error("No portal session is open.")

    private fun requireSession(): StalkerSession =
        synchronized(lock) { session } ?: error("No portal session is open.")

    private fun emptySummary(): XtreamSessionSummary =
        XtreamSessionSummary(
            sourceId = sourceId,
            account =
                XtreamAccount(
                    authenticated = credentials != null,
                    status = null,
                    isTrial = null,
                    activeConnections = null,
                    maximumConnections = null,
                    allowedOutputFormats = emptySet(),
                ),
            loadedItemCount = 0,
            loadedContentTypes = emptySet(),
        )

    /** Never the credentials, the token or a command. */
    override fun toString(): String = "StalkerCatalogueRepository(open=${credentials != null})"

    private companion object {
        /**
         * A ceiling on the walk, so a portal that never returns an empty page cannot spin forever.
         *
         * Portals have been seen repeating their last page indefinitely; without this the load
         * would never finish and the splash would sit at the same number for ever.
         */
        const val MAX_PAGES = 400

        const val MIN_SEARCH_QUERY = 2

        /** Category words a Kids profile must not see. Matched on the category, never a title. */
        val KIDS_BLOCKED =
            setOf("adulto", "adult", "xxx", "porn", "erotic", "erotico", "+18", "18+")
    }
}

private fun XtreamContentType.toStalker(): StalkerContentType =
    when (this) {
        XtreamContentType.LIVE -> StalkerContentType.LIVE
        XtreamContentType.MOVIE -> StalkerContentType.MOVIE
        XtreamContentType.SERIES -> StalkerContentType.SERIES
    }

private fun commandKey(contentType: XtreamContentType, providerId: String): String =
    "$contentType:$providerId"

private fun StalkerCatalogItem.toXtreamItem(contentType: XtreamContentType): XtreamCatalogItem =
    XtreamCatalogItem(
        providerId = providerId,
        name = name,
        contentType = contentType,
        // A portal files an item under one category; the catalogue models many, so this is a list
        // of one rather than a different shape for Stalker.
        categoryIds = listOfNotNull(categoryId),
        containerExtension = null,
        artworkUrl = artworkUrl,
        year = year,
        rating = rating,
        addedAtEpochSeconds = null,
    )

private fun com.lucasserafin94.iptvburo.stalker.StalkerAccount.toXtreamAccount(): XtreamAccount =
    XtreamAccount(
        authenticated = authenticated,
        status = if (blocked) "blocked" else tariffPlan,
        isTrial = null,
        activeConnections = null,
        maximumConnections = null,
        allowedOutputFormats = emptySet(),
    )

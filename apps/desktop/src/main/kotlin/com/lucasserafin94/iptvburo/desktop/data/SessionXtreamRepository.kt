package com.lucasserafin94.iptvburo.desktop.data

import com.lucasserafin94.iptvburo.desktop.model.XtreamCatalogPage
import com.lucasserafin94.iptvburo.desktop.model.XtreamPlaybackTarget
import com.lucasserafin94.iptvburo.desktop.model.XtreamSessionSummary
import com.lucasserafin94.iptvburo.desktop.security.XtreamLoginInput
import com.lucasserafin94.iptvburo.domain.model.ContentIdentity
import com.lucasserafin94.iptvburo.domain.model.FamilyContentPolicy
import com.lucasserafin94.iptvburo.domain.model.LibraryCandidate
import com.lucasserafin94.iptvburo.domain.model.MatchKind
import com.lucasserafin94.iptvburo.domain.model.shelfDeduplicationKey
import com.lucasserafin94.iptvburo.xtream.XtreamAccount
import com.lucasserafin94.iptvburo.xtream.XtreamCatalogItem
import com.lucasserafin94.iptvburo.xtream.XtreamCategory
import com.lucasserafin94.iptvburo.xtream.XtreamClient
import com.lucasserafin94.iptvburo.xtream.XtreamContentType
import com.lucasserafin94.iptvburo.xtream.XtreamCredentials
import com.lucasserafin94.iptvburo.xtream.XtreamClientException
import com.lucasserafin94.iptvburo.xtream.XtreamFailureReason
import com.lucasserafin94.iptvburo.xtream.XtreamMovieDetails
import com.lucasserafin94.iptvburo.xtream.XtreamShortEpg
import com.lucasserafin94.iptvburo.xtream.XtreamSeriesDetails
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Arrays
import java.util.EnumMap

/**
 * Session-only Xtream storage.
 *
 * Credentials live in wipeable arrays and are converted to the shared client's immutable model
 * only for the duration of a single call. No URL, credential or catalog is written to disk.
 */
class SessionXtreamRepository(
    private val client: XtreamClient = XtreamClient(),
) {
    private val lock = Any()
    private var generation = 0L
    private var credentialVault: CredentialVault? = null
    private var account: XtreamAccount? = null
    private var sourceId: String? = null
    private val categories = EnumMap<XtreamContentType, List<XtreamCategory>>(XtreamContentType::class.java)
    private val catalogs = EnumMap<XtreamContentType, CompactXtreamCatalog>(XtreamContentType::class.java)

    /**
     * One lock per content type, so a fetch of one type never blocks a fetch of another.
     *
     * Deliberately never cleared by [clear] or [clearCatalogCache]: these identify a *type*, not a
     * session, and swapping them mid-flight would let two fetches of the same type run in parallel
     * again - the exact duplicate download this guards against.
     */
    private val fetchLocks = EnumMap<XtreamContentType, Any>(XtreamContentType::class.java)

    /**
     * The catalogue kept between launches.
     *
     * A returning user waited for the whole list to be downloaded and parsed again before anything
     * appeared — for a catalogue that changes over days. Measured on a real-sized list: 41,717
     * items read back from disk in 132 ms.
     */
    private val diskCache = CatalogDiskCache()

    /**
     * What the initial load is doing right now.
     *
     * A type rather than a string so the wording stays in the translation tables and out of the
     * data layer — the repository knows *what* it is fetching, not how to say it in four languages.
     */
    sealed interface XtreamLoadStage {
        data object Authenticating : XtreamLoadStage

        data class Categories(val contentType: XtreamContentType) : XtreamLoadStage

        data object Channels : XtreamLoadStage
    }

    /**
     * Reports what the initial load is doing, so a slow provider looks like progress.
     *
     * This is four network round trips — authentication, then categories for live, films and
     * series, then the live catalogue itself — and on a slow provider each takes seconds. Reported
     * as it goes rather than left silent: a bar parked at one message reads as a hang, and the user
     * has no way to tell a slow provider from a broken app.
     *
     * The callback receives a fraction of *this* phase, 0..1, not of overall startup — the caller
     * knows where this phase sits in the whole sequence and scales accordingly.
     */
    fun authenticateAndLoadInitial(
        input: XtreamLoginInput,
        onProgress: (fraction: Float, detail: XtreamLoadStage) -> Unit = { _, _ -> },
    ): XtreamSessionSummary {
        val nextVault =
            try {
                CredentialVault(
                    server = input.copyServer(),
                    username = input.copyUsername(),
                    password = input.copyPassword(),
                )
            } finally {
                input.clear()
            }
        clear()

        val currentGeneration =
            synchronized(lock) {
                generation += 1
                credentialVault = nextVault
                sourceId = nextVault.stableSourceId()
                generation
            }

        return try {
            onProgress(0f, XtreamLoadStage.Authenticating)
            val authenticatedAccount = nextVault.use(client::authenticate)

            // Weighted by what actually costs time rather than by step count: authentication is one
            // small request, the category lists are three, and the live catalogue is by far the
            // largest payload of the four.
            val types = XtreamContentType.entries
            val loadedCategories =
                types.mapIndexed { index, type ->
                    onProgress(0.15f + 0.15f * index / types.size, XtreamLoadStage.Categories(type))
                    type to loadCategoriesAdaptively(nextVault, type)
                }.toMap()

            onProgress(0.35f, XtreamLoadStage.Channels)
            // The live catalogue is the largest single payload of the sign-in, and on a returning
            // launch it is almost always identical to the copy already on disk. Reading that
            // instead is the difference between a wait measured in seconds and one measured in
            // milliseconds — 41,717 items come back in about 130 ms.
            val fingerprint = synchronized(lock) { sourceId }
            val cachedLive = fingerprint?.let { diskCache.read(XtreamContentType.LIVE, it) }
            val liveCatalog = cachedLive?.catalog ?: loadCatalogItems(nextVault, XtreamContentType.LIVE)
            onProgress(1f, XtreamLoadStage.Channels)
            synchronized(lock) {
                checkGeneration(currentGeneration)
                account = authenticatedAccount
                categories.putAll(loadedCategories)
                catalogs[XtreamContentType.LIVE] = liveCatalog
                summaryLocked()
            }.also {
                // Only when it came from the provider: rewriting a copy that was just read from
                // disk would refresh its timestamp and keep a stale catalogue alive for ever.
                if (cachedLive == null && fingerprint != null) {
                    diskCache.write(
                        contentType = XtreamContentType.LIVE,
                        accountFingerprint = fingerprint,
                        catalog = liveCatalog,
                        categories = loadedCategories[XtreamContentType.LIVE].orEmpty(),
                    )
                }
            }
        } catch (error: Throwable) {
            clearIfGeneration(currentGeneration)
            throw error
        }
    }

    /**
     * Fetches one content type, at most once at a time.
     *
     * Serialised per type on [fetchLocks] rather than on [lock], because the fetch itself streams a
     * whole catalogue over the network and holding the session lock for that would block every
     * page(), categories() and summary() call on the UI thread for its duration.
     *
     * The double check around the fetch lock is what stops a duplicate download: the Home effect
     * calls this for MOVIE and SERIES while the user clicking a content tab calls it for the same
     * type, and the two used to miss the cache together and stream a 30,000-item catalogue twice.
     */
    /**
     * Loads a catalogue, from disk when there is a fresh copy and from the provider otherwise.
     *
     * @param forceRefresh bypasses the disk cache. This is what "Atualizar listas" does: the user
     *   is explicitly asking for what the provider has now, and answering from a file would make
     *   that button appear broken.
     */
    fun loadCatalog(
        contentType: XtreamContentType,
        forceRefresh: Boolean = false,
    ): XtreamSessionSummary {
        synchronized(lock) {
            catalogs[contentType]?.let { return summaryLocked() }
        }
        synchronized(fetchLockFor(contentType)) {
            // Re-checked inside the fetch lock: the download that beat us here has published its
            // result by now, so the loser answers from memory instead of repeating the work.
            synchronized(lock) {
                catalogs[contentType]?.let { return summaryLocked() }
            }
            val currentGeneration: Long
            val vault: CredentialVault
            synchronized(lock) {
                currentGeneration = generation
                vault = requireNotNull(credentialVault) { "No Xtream session is active." }
            }

            // Disk before network. A provider's catalogue changes over days, and re-downloading
            // and re-parsing 41,717 items is most of the wait a returning user sits through — for
            // a list that is almost always identical to the one they saw an hour ago.
            //
            // Skipped when the caller asked for fresh data, which is what Atualizar listas does.
            // The same id the rest of the app uses to tell one subscription from another: a salted
            // hash of server and username, never the credentials themselves.
            val fingerprint = synchronized(lock) { sourceId }
            if (!forceRefresh && fingerprint != null) {
                diskCache.read(contentType, fingerprint)?.let { cached ->
                    return synchronized(lock) {
                        checkGeneration(currentGeneration)
                        catalogs[contentType] = cached.catalog
                        // Categories travel with the catalogue: they are fetched in the same round
                        // and a catalogue without them shows every title under no category at all.
                        if (cached.categories.isNotEmpty()) categories[contentType] = cached.categories
                        summaryLocked()
                    }
                }
            }

            val loaded = loadCatalogItems(vault, contentType)
            return synchronized(lock) {
                checkGeneration(currentGeneration)
                catalogs[contentType] = loaded
                summaryLocked()
            }.also {
                // Written after publishing, so a slow disk never delays the screen. The categories
                // are read back out of memory rather than passed in, since the fetch above may
                // have refreshed them.
                fingerprint?.let { account ->
                    diskCache.write(
                        contentType = contentType,
                        accountFingerprint = account,
                        catalog = loaded,
                        categories = synchronized(lock) { categories[contentType].orEmpty() },
                    )
                }
            }
        }
    }

    private fun fetchLockFor(contentType: XtreamContentType): Any =
        synchronized(lock) { fetchLocks.getOrPut(contentType) { Any() } }

    fun categories(contentType: XtreamContentType): List<XtreamCategory> =
        synchronized(lock) {
            categories[contentType].orEmpty()
        }

    fun itemByProviderId(contentType: XtreamContentType, providerId: String): XtreamCatalogItem? =
        synchronized(lock) { catalogs[contentType]?.itemByProviderId(providerId) }

    /**
     * The item whose content identity matches [contentKey].
     *
     * Progress and favourites are recorded by content key rather than provider id, because the id
     * is per-playlist numbering: the same film has a different one in another list.
     */
    fun itemByContentKey(contentType: XtreamContentType, contentKey: String): XtreamCatalogItem? {
        val catalog = synchronized(lock) { catalogs[contentType] } ?: return null
        // Indexed rather than scanned. This used to walk the catalogue building a full
        // XtreamCatalogItem per row just to read its identity — 41,698 objects per lookup on a real
        // list — and the history screen makes two hundred of these, on every keystroke in its
        // search box.
        //
        // Under the same lock as the lookup above: the index is built lazily inside the catalogue,
        // so two threads calling this at once would otherwise race on building it.
        val index = synchronized(lock) { catalog.indexOfContentKey(contentKey) }
        return index.takeIf { it >= 0 }?.let(catalog::itemAt)
    }

    /**
     * Returns one small page without allocating a complete filtered copy of a large catalog.
     */
    fun page(
        contentType: XtreamContentType,
        categoryId: String?,
        query: String,
        requestedPage: Int,
        pageSize: Int = DEFAULT_PAGE_SIZE,
        releaseYear: Int? = null,
        minimumRating: Double? = null,
        allowedIdentities: Set<ContentIdentity>? = null,
        kidsMode: Boolean = false,
        /**
         * Categories behind the PIN that has not been entered this session.
         *
         * Filtered here rather than at the category rail because the rail is not the only way in:
         * with no category selected the catalogue lists everything, and a search matches across all
         * of them. A lock that only guarded the rail left every locked title one search away.
         */
        lockedCategoryIds: Set<String> = emptySet(),
    ): XtreamCatalogPage {
        require(pageSize in 1..MAX_PAGE_SIZE) { "Invalid page size." }
        val catalogItems =
            synchronized(lock) {
                catalogs[contentType] ?: CompactXtreamCatalog(contentType)
            }
        val normalizedQuery = query.trim().replace(WHITESPACE, " ")
        val safeRequestedPage = requestedPage.coerceAtLeast(0)
        val requestedStart = safeRequestedPage * pageSize
        val pageItems = ArrayList<XtreamCatalogItem>(pageSize)
        var totalMatches = 0
        val categoryNames = synchronized(lock) { categories[contentType].orEmpty().associate { it.providerId to it.name } }

        repeat(catalogItems.size) { index ->
            if (
                catalogItems.matches(
                    index,
                    categoryId,
                    normalizedQuery,
                    releaseYear,
                    minimumRating,
                    allowedIdentities,
                )
            ) {
                // The exclusions are answered from the columns, before any object is built.
                // `itemAt` allocates a whole XtreamCatalogItem — decoding category ids, composing
                // an artwork URL — and building one only to discard it, for every row of a 41,698
                // item catalogue, is most of the cost of turning a page.
                val rowCategoryIds = catalogItems.categoryIdsAt(index)
                val allowedForKids =
                    !kidsMode || FamilyContentPolicy.isAllowedForKids(
                        catalogItems.nameAt(index),
                        rowCategoryIds.map(categoryNames::get),
                    )
                if (!allowedForKids) return@repeat
                // Any locked category is enough to hide the title: an item carried by both a locked
                // and an open category is still the locked one's content, and showing it would make
                // the lock trivial to work around.
                if (lockedCategoryIds.isNotEmpty() && rowCategoryIds.any(lockedCategoryIds::contains)) {
                    return@repeat
                }
                // Built only for the rows that actually appear on this page — eighty of them,
                // not every match in the catalogue.
                if (totalMatches in requestedStart until requestedStart + pageSize) {
                    pageItems += catalogItems.itemAt(index)
                }
                totalMatches += 1
            }
        }

        val pageCount =
            if (totalMatches == 0) {
                1
            } else {
                ((totalMatches - 1) / pageSize) + 1
            }
        if (safeRequestedPage >= pageCount && safeRequestedPage != 0) {
            return page(
                contentType = contentType,
                categoryId = categoryId,
                query = query,
                requestedPage = pageCount - 1,
                pageSize = pageSize,
                releaseYear = releaseYear,
                // Carried through: without it the clamp to the last page silently dropped the
                // rating filter and returned titles the user had just filtered out.
                minimumRating = minimumRating,
                allowedIdentities = allowedIdentities,
                kidsMode = kidsMode,
                // The clamped request is still the same protected browse operation. Dropping this
                // argument here made an out-of-range page recurse without the parental filter and
                // return a locked title from the provider's unfiltered last page.
                lockedCategoryIds = lockedCategoryIds,
            )
        }
        return XtreamCatalogPage(
            items = pageItems,
            pageIndex = safeRequestedPage,
            pageSize = pageSize,
            totalMatches = totalMatches,
        )
    }

    fun seriesDetails(seriesId: String): XtreamSeriesDetails =
        withCredentials { credentials ->
            client.seriesDetails(credentials, seriesId)
        }

    /**
     * Films whose cast includes [personName].
     *
     * The provider only exposes the cast inside per-film details, so this costs one request per
     * candidate. Sweeping a 30,000-item catalogue that way would take many minutes and hammer the
     * provider, so the search is bounded: it walks the movie catalogue in catalogue order and stops
     * at [limit] matches or once [MAX_CAST_LOOKUPS] films have been inspected. That trades
     * completeness for a page that appears in seconds, which is the right trade for a filmography.
     */
    fun findByCastMember(
        personName: String,
        limit: Int,
    ): List<XtreamCatalogItem> {
        val needle = personName.trim().lowercase()
        if (needle.isBlank()) return emptyList()
        val catalog = synchronized(lock) { catalogs[XtreamContentType.MOVIE] } ?: return emptyList()

        val matches = ArrayList<XtreamCatalogItem>(limit)
        var inspected = 0
        for (index in 0 until catalog.size) {
            if (matches.size >= limit || inspected >= MAX_CAST_LOOKUPS) break
            val item = catalog.itemAt(index)
            inspected += 1
            val cast =
                runCatching { movieDetails(item.providerId).cast }
                    .getOrNull()
                    ?.lowercase()
                    ?: continue
            if (cast.contains(needle)) matches += item
        }
        return matches
    }

    /**
     * Every film and series in the loaded catalogue, as matching candidates.
     *
     * The whole catalogue, not the page the user is looking at. "Is this film already in my list?"
     * has to be asked of all 40-odd thousand items — checking only the current page answers no
     * almost every time, which is worse than not asking, because the user reads it as a fact.
     *
     * Live channels are left out: a channel is a stream, not a work, and matching one against a
     * film would only ever produce noise.
     */
    /**
     * This year's releases across the whole catalogue, newest first.
     *
     * Scans every item rather than a page: what arrived this year is scattered through forty
     * thousand entries, and a page-based answer would show whichever slice happened to be first.
     *
     * Filtered to [year] alone. A provider adds titles constantly, most of them older films being
     * back-filled, so "recently added" and "new" are different questions — and the one the user is
     * asking is what came out this year.
     */
    fun releasesForYear(
        type: XtreamContentType,
        year: Int,
        limit: Int,
        kidsMode: Boolean,
        lockedCategoryIds: Set<String> = emptySet(),
    ): List<XtreamCatalogItem> {
        if (synchronized(lock) { catalogs[type] } == null) {
            runCatching { loadCatalog(type) }
        }
        val catalog = synchronized(lock) { catalogs[type] } ?: return emptyList()

        val categoryNames = synchronized(lock) { categories[type].orEmpty().associate { it.providerId to it.name } }

        val matches = ArrayList<XtreamCatalogItem>(limit * 4)
        for (index in 0 until catalog.size) {
            val item = catalog.itemAt(index)
            if (item.year != year) continue
            if (item.categoryIds.any(lockedCategoryIds::contains)) continue
            if (kidsMode &&
                !FamilyContentPolicy.isAllowedForKids(item.name, item.categoryIds.map(categoryNames::get))
            ) {
                continue
            }
            matches += item
        }

        // Best first within the year: a provider's newest entries arrive unordered, and rating is
        // the only signal available for what is worth surfacing.
        return matches
            .sortedByDescending { item -> item.rating ?: 0.0 }
            // The shelf key, not the matching one. Stripping quality words was not enough: the
            // copies that still reached the screen differed by a channel prefix, a pipe-separated
            // tag, a bracketed language marker or a trailing single letter — none of which the
            // conservative matcher removes, and all of which are the same film.
            .distinctBy { item -> item.name.shelfDeduplicationKey() }
            .take(limit)
    }

    /**
     * Applies the same parental policy used by [page] to an item resolved outside paging.
     *
     * Continue-watching and history resolve persisted identities directly, so they cannot rely on
     * a page having filtered the item first. Keeping this check in the repository also gives those
     * Home rows the category names needed by the conservative Kids policy.
     */
    fun isAllowedForBrowsing(
        item: XtreamCatalogItem,
        kidsMode: Boolean,
        lockedCategoryIds: Set<String>,
    ): Boolean {
        if (item.categoryIds.any(lockedCategoryIds::contains)) return false
        if (!kidsMode) return true
        val categoryNames =
            synchronized(lock) {
                categories[item.contentType].orEmpty().associate { category -> category.providerId to category.name }
            }
        return FamilyContentPolicy.isAllowedForKids(item.name, item.categoryIds.map(categoryNames::get))
    }

    fun libraryMatchCandidates(
        kidsMode: Boolean = false,
        lockedCategoryIds: Set<String> = emptySet(),
        lockedCategoryIdsByContentType: Map<XtreamContentType, Set<String>> = emptyMap(),
    ): List<LibraryCandidate> {
        // Fetched if they are not in memory yet. Assinaturas can be the first screen a user opens,
        // and the film and series catalogues are only loaded when their own sections are visited —
        // so this used to run against an empty map and answer "you own nothing", silently, for
        // every title. loadCatalog is a no-op when the catalogue is already there.
        listOf(XtreamContentType.MOVIE, XtreamContentType.SERIES).forEach { type ->
            if (synchronized(lock) { catalogs[type] } == null) {
                runCatching { loadCatalog(type) }
            }
        }

        val loaded =
            synchronized(lock) {
                listOf(XtreamContentType.MOVIE, XtreamContentType.SERIES)
                    .mapNotNull { type -> catalogs[type]?.let { type to it } }
            }
        if (loaded.isEmpty()) return emptyList()

        // Sized up front: this runs on every title opened in Assinaturas, and growing an ArrayList
        // through forty thousand appends is measurable.
        val candidates = ArrayList<LibraryCandidate>(loaded.sumOf { (_, catalog) -> catalog.size })
        loaded.forEach { (contentType, catalog) ->
            val kind = if (contentType == XtreamContentType.SERIES) MatchKind.SERIES else MatchKind.MOVIE
            val lockedForType = lockedCategoryIdsByContentType[contentType] ?: lockedCategoryIds
            val categoryNames =
                synchronized(lock) {
                    categories[contentType].orEmpty().associate { category -> category.providerId to category.name }
                }
            for (index in 0 until catalog.size) {
                val item = catalog.itemAt(index)
                if (item.categoryIds.any(lockedForType::contains)) continue
                if (kidsMode &&
                    !FamilyContentPolicy.isAllowedForKids(item.name, item.categoryIds.map(categoryNames::get))
                ) {
                    continue
                }
                candidates +=
                    LibraryCandidate(
                        // Prefixed with the content type: provider ids are numbered per catalogue,
                        // so film 42 and series 42 both exist and a bare id cannot say which was
                        // matched. The caller splits this back apart to reopen the item.
                        localContentId = "${contentType.name}:${item.providerId}",
                        title = item.name,
                        year = item.year,
                        kind = kind,
                    )
            }
        }
        return candidates
    }

    fun movieDetails(movieId: String): XtreamMovieDetails =
        withCredentials { credentials ->
            client.movieDetails(credentials, movieId)
        }

    fun shortEpg(streamId: String): XtreamShortEpg =
        withCredentials { credentials ->
            client.shortEpg(credentials, streamId)
        }

    /**
     * Constructs the credential-bearing URI at the last possible moment.
     *
     * Callers must invoke this only after an explicit user confirmation and immediately hand the
     * result to the OS. The repository never retains the returned URI.
     */
    fun buildConfirmedPlaybackUri(target: XtreamPlaybackTarget): URI =
        withCredentials { credentials ->
            when (target) {
                is XtreamPlaybackTarget.CatalogItem ->
                    client
                        .buildPlaybackUrl(
                            credentials = credentials,
                            contentType = target.contentType,
                            providerId = target.providerId,
                            containerExtension =
                                target.containerExtension
                                    ?: target.contentType
                                        .takeIf { type -> type == XtreamContentType.LIVE }
                                        ?.let { preferredLiveContainerExtension() },
                        ).toUri()
                is XtreamPlaybackTarget.Episode ->
                    client
                        .buildEpisodePlaybackUrl(
                            credentials = credentials,
                            episode = target.episode,
                        ).toUri()
            }
        }

    private fun preferredLiveContainerExtension(): String? {
        val allowedFormats =
            synchronized(lock) {
                account?.allowedOutputFormats.orEmpty()
            }
        return when {
            "ts" in allowedFormats -> "ts"
            "m3u8" in allowedFormats -> "m3u8"
            else -> null
        }
    }

    fun summary(): XtreamSessionSummary? =
        synchronized(lock) {
            if (credentialVault == null || account == null || sourceId == null) {
                null
            } else {
                summaryLocked()
            }
        }

    /**
     * Drops the cached lists while keeping the session.
     *
     * Unlike [clear] the credentials survive, so the next [loadCatalog] re-fetches from the provider
     * instead of failing with no active session. The generation is deliberately not bumped: nothing
     * is being invalidated, only re-read.
     */
    /**
     * Drops the in-memory catalogues so the next [loadCatalog] fetches again.
     *
     * The disk copy goes too. It exists to answer "what did the provider have recently", and a
     * caller asking for a refresh is asking precisely for what it has *now* — leaving the file in
     * place would have the next load answer from it and make the refresh a no-op. A test caught
     * that before it shipped.
     */
    fun clearCatalogCache() {
        synchronized(lock) {
            // Only the item catalogues. Categories are fetched during connect, not by loadCatalog,
            // so clearing them here emptied the category rail down to "Todas" with no way to
            // repopulate it until the user signed in again.
            catalogs.clear()
        }
        diskCache.clear()
    }

    /**
     * Forgets the session **and** the catalogue kept on disk.
     *
     * Separate from [clear], which runs on every authentication — including the one that restores a
     * saved session at startup, where deleting the cache would defeat the whole point of having
     * one. This is for signing out: leaving a subscription's entire catalogue on disk after the
     * user has deliberately disconnected would be a surprise.
     */
    fun clearIncludingDiskCache() {
        clear()
        diskCache.clear()
    }

    fun clear() {
        val oldVault =
            synchronized(lock) {
                generation += 1
                val previous = credentialVault
                credentialVault = null
                account = null
                sourceId = null
                categories.clear()
                catalogs.clear()
                previous
            }
        oldVault?.clear()
    }

    private fun <T> withCredentials(block: (XtreamCredentials) -> T): T {
        val vault =
            synchronized(lock) {
                requireNotNull(credentialVault) { "No Xtream session is active." }
            }
        return vault.use(block)
    }

    /**
     * Older panels sometimes omit one category endpoint while the remaining sections work.
     * Unsupported or malformed optional category lists become an unfiltered catalog instead of
     * rejecting an otherwise valid account. Network and authentication failures remain fatal.
     */
    private fun loadCategoriesAdaptively(
        vault: CredentialVault,
        contentType: XtreamContentType,
    ): List<XtreamCategory> =
        try {
            vault.use { credentials ->
                client.categories(credentials, contentType).items
            }
        } catch (error: XtreamClientException) {
            when (error.reason) {
                XtreamFailureReason.HTTP,
                XtreamFailureReason.INVALID_RESPONSE,
                XtreamFailureReason.RESPONSE_TOO_LARGE,
                -> emptyList()
                XtreamFailureReason.INVALID_SERVER,
                XtreamFailureReason.NETWORK,
                XtreamFailureReason.AUTHENTICATION,
                -> throw error
            }
        }

    private fun checkGeneration(expected: Long) {
        check(expected == generation && credentialVault != null) {
            "The Xtream session was cleared while the operation was running."
        }
    }

    private fun clearIfGeneration(expected: Long) {
        val shouldClear =
            synchronized(lock) {
                expected == generation
            }
        if (shouldClear) clear()
    }

    private fun summaryLocked(): XtreamSessionSummary =
        XtreamSessionSummary(
            sourceId = requireNotNull(sourceId),
            account = requireNotNull(account),
            loadedItemCount = catalogs.values.sumOf(CompactXtreamCatalog::size),
            loadedContentTypes = catalogs.keys.toSet(),
        )

    private fun loadCatalogItems(
        vault: CredentialVault,
        contentType: XtreamContentType,
    ): CompactXtreamCatalog {
        val items = CompactXtreamCatalog(contentType)
        vault.use { credentials ->
            client.streamCatalog(credentials, contentType) { item ->
                items.add(item)
            }
        }
        return items
    }

    private class CredentialVault(
        server: CharArray,
        username: CharArray,
        password: CharArray,
    ) {
        private val lock = Any()
        private var serverChars = server
        private var usernameChars = username
        private var passwordChars = password
        private var cleared = false

        fun <T> use(block: (XtreamCredentials) -> T): T {
            val credentials =
                synchronized(lock) {
                    check(!cleared) { "The Xtream credential session was cleared." }
                    XtreamCredentials(
                        serverUrl = serverChars.concatToString(),
                        username = usernameChars.concatToString(),
                        password = passwordChars.concatToString(),
                    )
                }
            return block(credentials)
        }

        fun stableSourceId(): String = synchronized(lock) {
            check(!cleared) { "The Xtream credential session was cleared." }
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(serverChars.concatToString().trim().lowercase().toByteArray(StandardCharsets.UTF_8))
            digest.update(0)
            digest.update(usernameChars.concatToString().toByteArray(StandardCharsets.UTF_8))
            "xtream-" + digest.digest().take(16).joinToString("") { "%02x".format(it) }
        }

        fun clear() {
            synchronized(lock) {
                Arrays.fill(serverChars, ZERO_CHAR)
                Arrays.fill(usernameChars, ZERO_CHAR)
                Arrays.fill(passwordChars, ZERO_CHAR)
                serverChars = CharArray(0)
                usernameChars = CharArray(0)
                passwordChars = CharArray(0)
                cleared = true
            }
        }

        override fun toString(): String = "CredentialVault(<redacted>)"
    }

    private companion object {
        /** Bounds a cast sweep so a filmography cannot take minutes or flood the provider. */
        const val MAX_CAST_LOOKUPS = 400

        const val DEFAULT_PAGE_SIZE = 80
        const val MAX_PAGE_SIZE = 200
        const val ZERO_CHAR = '\u0000'
        val WHITESPACE = Regex("\\s+")

    }
}

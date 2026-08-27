package com.lucasserafin94.iptvburo.desktop.data

import com.lucasserafin94.iptvburo.desktop.model.XtreamCatalogPage
import com.lucasserafin94.iptvburo.desktop.model.XtreamPlaybackTarget
import com.lucasserafin94.iptvburo.desktop.model.XtreamSessionSummary
import com.lucasserafin94.iptvburo.desktop.security.XtreamLoginInput
import com.lucasserafin94.iptvburo.domain.model.ContentIdentity
import com.lucasserafin94.iptvburo.domain.model.FamilyContentPolicy
import com.lucasserafin94.iptvburo.domain.model.LibraryCandidate
import com.lucasserafin94.iptvburo.domain.model.PlaceholderArtwork
import com.lucasserafin94.iptvburo.domain.model.MatchKind
import com.lucasserafin94.iptvburo.domain.model.normalisedForMatching
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
) : CatalogueRepository {
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
     * Covers this provider reuses across thousands of titles, computed once per catalogue.
     *
     * Per catalogue rather than per card: the grid draws hundreds of cards and counting forty
     * thousand rows for each would be the whole catalogue walked per frame. Recomputed when a
     * catalogue is replaced, and cleared with everything else on sign-out.
     */
    private val placeholders = mutableSetOf<String>()

    private var placeholdersComputedFor = -1

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
    override fun authenticateAndLoadInitial(
        input: XtreamLoginInput,
        onProgress: (fraction: Float, detail: XtreamLoadStage) -> Unit,
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
                refreshPlaceholdersLocked()
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
    override fun loadCatalog(
        contentType: XtreamContentType,
        forceRefresh: Boolean,
        /**
         * Reports items as they are parsed, so the splash can move while this runs.
         *
         * Ignored on the disk-cache path below, which returns in milliseconds — there is nothing to
         * report and a caller that saw progress events for it would show a bar flashing past.
         */
        onProgress: CatalogLoadListener?,
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
                        refreshPlaceholdersLocked()
                        summaryLocked()
                    }
                }
            }

            val loaded = loadCatalogItems(vault, contentType, onProgress)
            return synchronized(lock) {
                checkGeneration(currentGeneration)
                catalogs[contentType] = loaded
                refreshPlaceholdersLocked()
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

    override fun categories(contentType: XtreamContentType): List<XtreamCategory> =
        synchronized(lock) {
            categories[contentType].orEmpty()
        }

    override fun itemByProviderId(contentType: XtreamContentType, providerId: String): XtreamCatalogItem? =
        synchronized(lock) { catalogs[contentType]?.itemByProviderId(providerId) }

    /**
     * The item whose content identity matches [contentKey].
     *
     * Progress and favourites are recorded by content key rather than provider id, because the id
     * is per-playlist numbering: the same film has a different one in another list.
     */
    override fun itemByContentKey(contentType: XtreamContentType, contentKey: String): XtreamCatalogItem? {
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
        /**
         * Categories behind the PIN that has not been entered this session.
         *
         * Filtered here rather than at the category rail because the rail is not the only way in:
         * with no category selected the catalogue lists everything, and a search matches across all
         * of them. A lock that only guarded the rail left every locked title one search away.
         */
        lockedCategoryIds: Set<String>,
        /**
         * Collapses a provider's repeated copies of one film into a single card.
         *
         * Providers carry the same title several times over — one per quality, per dubbing, per
         * channel prefix — and the catalogue listed every one. Reported plainly: "em filmes todos,
         * aparece vários filmes duplicados". On a real list that is most of a screen spent on three
         * copies of the same film.
         *
         * This was deliberate once, on the reasoning that the copies *are* the choice of quality and
         * collapsing them takes that choice away. True, but the choice was never presented as one —
         * it was four identical-looking cards with no indication of what distinguished them, which
         * is not a choice a user can make. The setting lets somebody who wants every copy have them.
         *
         * Dedup keeps the first copy of each title, and the catalogue's own order decides which that
         * is, so the result is stable from one page turn to the next.
         */
        collapseDuplicates: Boolean,
        /**
         * Restricts the page to these `localContentId` values, or null for no restriction.
         *
         * Used by the Serviço filter, whose set comes from TMDb rather than from the playlist: a list
         * that files its films by genre records nothing about which service carries them, so the only
         * way to offer that filter is to bring the answer from outside and match on identity.
         *
         * Null rather than an empty set for "no filter". An empty set is a real answer — the service
         * carries nothing this library holds — and must produce an empty page rather than everything.
         */
        allowedLocalIds: Set<String>?,
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

        /**
         * Titles already listed, for the favourites screen only.
         *
         * Allocated whatever the mode, because a null here would need a branch at every use; it
         * stays empty and costs nothing when the identity filter is not in play.
         */
        val seenIdentities = HashSet<ContentIdentity>()
        /**
         * Shelf keys already listed, when duplicate copies are being collapsed.
         *
         * Separate from [seenIdentities], which is keyed on kind/title/year for the favourites
         * screen. This one uses the shelf key, which also strips the channel prefixes, bracketed
         * language tags and quality words a provider uses to distinguish its copies — the decorations
         * that made four cards out of one film.
         */
        val seenShelfKeys = if (collapseDuplicates) HashSet<String>() else null
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
                // Decoded only when something actually asks for it.
                //
                // `categoryIdsAt` splits the encoded column into a fresh list, and this ran for
                // every row that survived the filters — on a 41,698-item catalogue, on every page
                // turn, on every keystroke in the search box. Neither reader needs it in the
                // ordinary case: most profiles are not Kids, and most sessions lock no category, so
                // the list was built and thrown away for nearly every row.
                val needsCategories = kidsMode || lockedCategoryIds.isNotEmpty()
                val rowCategoryIds =
                    if (needsCategories) catalogItems.categoryIdsAt(index) else emptyList()
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
                // One row per title when listing favourites.
                //
                // A favourite is keyed on what the content *is* — kind, title, year — so that it
                // survives replacing the playlist. Providers commonly carry the same film several
                // times over for different qualities, and every one of those copies matches the one
                // key: marking a film once made it appear four times on the favourites screen,
                // which reads as the app saving it repeatedly.
                //
                // Only here. In the catalogue proper those copies are the choice of quality, and
                // collapsing them would take away the ability to pick one.
                if (allowedIdentities != null && !seenIdentities.add(catalogItems.identityAt(index))) {
                    return@repeat
                }
                // The service filter, when one is active.
                //
                // Composed from the type and the provider id, which is the same "MOVIE:1234" form the
                // index was built with. Placed after the column filters and before `itemAt`, so a row
                // excluded here still costs no object.
                if (allowedLocalIds != null &&
                    "$contentType:${catalogItems.providerIdAt(index)}" !in allowedLocalIds
                ) {
                    return@repeat
                }
                // One card per film, when the setting asks for it.
                //
                // Read from the name column rather than from a built item: this runs for every row
                // of a 41,698-item catalogue, and `itemAt` allocates.
                if (seenShelfKeys != null && !seenShelfKeys.add(catalogItems.nameAt(index).shelfDeduplicationKey())) {
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
                // For the same reason as the two above: a clamped page that forgot this would list
                // the duplicate copies the user had asked to collapse.
                collapseDuplicates = collapseDuplicates,
                allowedLocalIds = allowedLocalIds,
            )
        }
        return XtreamCatalogPage(
            items = pageItems,
            pageIndex = safeRequestedPage,
            pageSize = pageSize,
            totalMatches = totalMatches,
        )
    }

    override fun seriesDetails(seriesId: String): XtreamSeriesDetails =
        withCredentials { credentials ->
            client.seriesDetails(credentials, seriesId)
        }

    /**
     * Everything in the loaded catalogues whose name matches [query], across all three kinds.
     *
     * The point of a search tab: one box that finds a film, a series or a live channel without the
     * user first choosing which of the three they are in. The per-screen filters cannot do that —
     * they narrow whatever catalogue is already open, so looking for a film while browsing live
     * channels finds nothing.
     *
     * Films and series come before live channels because someone typing a name almost always wants
     * a title, and a provider carrying three hundred channels with a matching word would otherwise
     * bury the one film they meant. Within a kind the catalogue's own order is kept, which is the
     * order they see everywhere else in the app.
     *
     * Reads only what is already in memory — no request leaves the machine, so this works while
     * offline and cannot leak the query to the provider.
     */
    override fun search(
        query: String,
        limit: Int,
    ): List<XtreamCatalogItem> {
        // Normalised, not raw. `contains` compares code points, so on a Portuguese catalogue
        // "chefao" found nothing while "Chefão" sat right there. The same normaliser the library
        // matcher uses strips accents and provider decoration alike, which also means "duna 4k"
        // finds "Duna".
        val needle = query.trim().normalisedForMatching()
        // Two characters is the shortest search worth running. One letter matches most of a
        // catalogue, which is slow to walk and useless to read.
        if (needle.length < MIN_SEARCH_QUERY) return emptyList()

        val matches = ArrayList<XtreamCatalogItem>(limit)
        // The order of this list is the order of the results.
        for (contentType in SEARCH_ORDER) {
            if (matches.size >= limit) break
            val catalog = synchronized(lock) { catalogs[contentType] } ?: continue
            for (index in 0 until catalog.size) {
                if (matches.size >= limit) break
                val item = catalog.itemAt(index)
                if (item.name.normalisedForMatching().contains(needle)) matches += item
            }
        }
        return matches
    }

    /**
     * Every film and series in the catalogue whose title is one of [normalisedTitles].
     *
     * This replaces asking the provider for each film's cast in turn, which is one request per
     * film: against forty thousand it could only ever be capped, and a cap meant the answer was
     * "the first four hundred rows" rather than "this actor's work".
     *
     * The catalogue is already in memory and the names are already normalised, so this is a set
     * lookup per row — the same normalisation ServiceTitleIndex uses, so "Tropa de Elite 4K [DUB]"
     * and "Tropa de Elite" are one title.
     *
     * Both catalogues, because an actor's credits mix films and series.
     */
    override fun findByTitles(
        normalisedTitles: Set<String>,
        limit: Int,
    ): List<XtreamCatalogItem> {
        if (normalisedTitles.isEmpty()) return emptyList()
        val loaded =
            synchronized(lock) {
                listOf(XtreamContentType.MOVIE, XtreamContentType.SERIES)
                    .mapNotNull { type -> catalogs[type] }
            }
        val matches = ArrayList<XtreamCatalogItem>(limit)
        loaded.forEach { catalog ->
            for (index in 0 until catalog.size) {
                if (matches.size >= limit) return matches
                // The name first, which is a field read; the item is only built once it matches,
                // because itemAt allocates and this runs over the whole catalogue.
                if (catalog.nameAt(index).normalisedForMatching() in normalisedTitles) {
                    matches += catalog.itemAt(index)
                }
            }
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
    override fun releasesForYear(
        type: XtreamContentType,
        year: Int,
        limit: Int,
        kidsMode: Boolean,
        lockedCategoryIds: Set<String>,
        /**
         * Which day's selection to show, as a rotation offset.
         *
         * Reported as the shelf being frozen: "faz 4 dias que os lançamentos são sempre o mesmo".
         * It was — the shelf sorted by rating and took the first eighteen, with nothing tied to the
         * date, so the best-rated releases of the year were the answer every single day. Every card
         * read ★5.0, which is the symptom: those really were the top of the list.
         *
         * The rest of the Home already rotates on a date-derived seed. This brings the shelf into
         * line without giving up the curation: the pool stays ordered by rating, and the day picks
         * a window into it, so a good film further down the list eventually gets its turn on screen.
         *
         * Zero keeps the old behaviour, which is what callers that want a stable "best of the year"
         * list — a full catalogue view rather than a Home shelf — should pass.
         */
        rotation: Int,
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
        val ranked =
            matches
                .sortedByDescending { item -> item.rating ?: 0.0 }
                // The shelf key, not the matching one. Stripping quality words was not enough: the
                // copies that still reached the screen differed by a channel prefix, a
                // pipe-separated tag, a bracketed language marker or a trailing single letter —
                // none of which the conservative matcher removes, and all of which are the same
                // film.
                .distinctBy { item -> item.name.shelfDeduplicationKey() }

        if (rotation == 0 || ranked.size <= limit) return ranked.take(limit)

        // Rotate within the well-rated part of the year rather than across all of it.
        //
        // The whole point of the shelf is that what it offers is worth watching, so the rotation is
        // bounded to a pool of the best entries instead of walking down into the unrated tail. Four
        // shelves' worth is enough that a week of viewing does not repeat, while everything shown
        // still comes from the top of the ranking.
        val pool = ranked.take((limit * 4).coerceAtMost(ranked.size))
        val offset = Math.floorMod(rotation, pool.size)
        // Wraps rather than truncating at the end of the pool: taking a window from `offset` alone
        // would hand back a short shelf on most days, and a Home row that changes length as well as
        // content reads as a loading fault.
        return List(limit.coerceAtMost(pool.size)) { index -> pool[(offset + index) % pool.size] }
    }

    /**
     * Applies the same parental policy used by [page] to an item resolved outside paging.
     *
     * Continue-watching and history resolve persisted identities directly, so they cannot rely on
     * a page having filtered the item first. Keeping this check in the repository also gives those
     * Home rows the category names needed by the conservative Kids policy.
     */
    override fun isAllowedForBrowsing(
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

    override fun libraryMatchCandidates(
        kidsMode: Boolean,
        lockedCategoryIds: Set<String>,
        lockedCategoryIdsByContentType: Map<XtreamContentType, Set<String>>,
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

    override fun movieDetails(movieId: String): XtreamMovieDetails =
        withCredentials { credentials ->
            client.movieDetails(credentials, movieId)
        }

    override fun shortEpg(streamId: String): XtreamShortEpg =
        withCredentials { credentials ->
            client.shortEpg(credentials, streamId)
        }

    /**
     * Constructs the credential-bearing URI at the last possible moment.
     *
     * Callers must invoke this only after an explicit user confirmation and immediately hand the
     * result to the OS. The repository never retains the returned URI.
     */
    override fun buildConfirmedPlaybackUri(target: XtreamPlaybackTarget): URI =
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
                is XtreamPlaybackTarget.CatchUp ->
                    client
                        .buildTimeshiftUrl(
                            credentials = credentials,
                            providerId = target.providerId,
                            startLocal = target.startLocal,
                            durationMinutes = target.durationMinutes,
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

    override fun placeholderArtworkUrls(): Set<String> =
        synchronized(lock) {
            // Recomputed only when the loaded catalogues have changed, which the count identifies
            // well enough: a catalogue is replaced whole, never edited row by row.
            refreshPlaceholdersLocked()
            placeholders.toSet()
        }

    /**
     * Recomputes the placeholder set when the loaded catalogues have changed, and tells each
     * catalogue what it found so [CompactXtreamCatalog.itemAt] stops handing the address out.
     *
     * The row count identifies a change well enough: a catalogue is replaced whole on load, never
     * edited row by row. Call with [lock] held.
     */
    /**
     * Drops the detected placeholders along with the catalogues they were counted from.
     *
     * The memo has to go too. It records a row count, and an empty catalogue counts zero — so
     * leaving it set would make the next load of an equally sized catalogue look unchanged and
     * skip the recount entirely. Call with [lock] held.
     */
    private fun forgetPlaceholdersLocked() {
        placeholders.clear()
        placeholdersComputedFor = -1
    }

    private fun refreshPlaceholdersLocked() {
        val loaded = catalogs.values.sumOf { catalog -> catalog.size }
        if (loaded == placeholdersComputedFor) return
        placeholders.clear()
        // Counted across all content types together: one provider stamp is reused for films and
        // series alike, and a type on its own may not reach the threshold.
        placeholders +=
            PlaceholderArtwork.detect(
                catalogs.values.asSequence().flatMap { catalog -> catalog.artworkUrls() },
            )
        placeholdersComputedFor = loaded
        catalogs.values.forEach { catalog -> catalog.markPlaceholderArtwork(placeholders) }

    }

    override fun summary(): XtreamSessionSummary? =
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
    override fun clearCatalogCache() {
        synchronized(lock) {
            // Only the item catalogues. Categories are fetched during connect, not by loadCatalog,
            // so clearing them here emptied the category rail down to "Todas" with no way to
            // repopulate it until the user signed in again.
            catalogs.clear()
            forgetPlaceholdersLocked()
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
    override fun clearIncludingDiskCache() {
        clear()
        diskCache.clear()
    }

    override fun clear() {
        val oldVault =
            synchronized(lock) {
                generation += 1
                val previous = credentialVault
                credentialVault = null
                account = null
                sourceId = null
                categories.clear()
                catalogs.clear()
                forgetPlaceholdersLocked()
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
        onProgress: CatalogLoadListener? = null,
    ): CompactXtreamCatalog {
        val items = CompactXtreamCatalog(contentType)
        vault.use { credentials ->
            if (onProgress == null) {
                client.streamCatalog(credentials, contentType) { item -> items.add(item) }
            } else {
                // Counted here rather than read off `items.size`, which is the same number but
                // reached through a synchronized accessor on the hot path of a 41,698-item parse.
                var seen = 0
                client.streamCatalog(credentials, contentType) { item ->
                    items.add(item)
                    seen += 1
                    // Throttled: an event per item costs more than the parse it describes.
                    if (seen % CATALOG_PROGRESS_ITEM_INTERVAL == 0) {
                        onProgress(CatalogLoadProgress(seen, System.currentTimeMillis()))
                    }
                }
                // The tail, so the last partial batch is not silently dropped and the screen does
                // not finish showing a count lower than what was actually loaded.
                onProgress(CatalogLoadProgress(seen, System.currentTimeMillis()))
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

        /**
         * How many results a search returns.
         *
         * Enough to be sure the right title is in there, few enough that the screen is readable and
         * the walk stops early on a catalogue of forty thousand.
         */
        const val DEFAULT_SEARCH_LIMIT = 200

        /** Shortest query worth walking a catalogue for. */
        const val MIN_SEARCH_QUERY = 2

        /**
         * Titles first, channels last — see [search]. Declared here so the order that decides the
         * results is one list rather than a sort spread through the loop.
         */
        val SEARCH_ORDER =
            listOf(XtreamContentType.MOVIE, XtreamContentType.SERIES, XtreamContentType.LIVE)

        const val DEFAULT_PAGE_SIZE = 80
        const val MAX_PAGE_SIZE = 200
        const val ZERO_CHAR = '\u0000'
        val WHITESPACE = Regex("\\s+")

    }
}

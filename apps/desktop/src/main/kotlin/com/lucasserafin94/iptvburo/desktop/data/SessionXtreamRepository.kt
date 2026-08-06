package com.lucasserafin94.iptvburo.desktop.data

import com.lucasserafin94.iptvburo.desktop.model.XtreamCatalogPage
import com.lucasserafin94.iptvburo.desktop.model.XtreamPlaybackTarget
import com.lucasserafin94.iptvburo.desktop.model.XtreamSessionSummary
import com.lucasserafin94.iptvburo.desktop.security.XtreamLoginInput
import com.lucasserafin94.iptvburo.domain.model.ContentIdentity
import com.lucasserafin94.iptvburo.domain.model.FamilyContentPolicy
import com.lucasserafin94.iptvburo.domain.model.LibraryCandidate
import com.lucasserafin94.iptvburo.domain.model.MatchKind
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
            val liveCatalog = loadCatalogItems(nextVault, XtreamContentType.LIVE)
            onProgress(1f, XtreamLoadStage.Channels)
            synchronized(lock) {
                checkGeneration(currentGeneration)
                account = authenticatedAccount
                categories.putAll(loadedCategories)
                catalogs[XtreamContentType.LIVE] = liveCatalog
                summaryLocked()
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
    fun loadCatalog(contentType: XtreamContentType): XtreamSessionSummary {
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
            val loaded = loadCatalogItems(vault, contentType)
            return synchronized(lock) {
                checkGeneration(currentGeneration)
                catalogs[contentType] = loaded
                summaryLocked()
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
        repeat(catalog.size) { index ->
            if (catalog.identityAt(index).key == contentKey) return catalog.itemAt(index)
        }
        return null
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
                val item = catalogItems.itemAt(index)
                val allowedForKids =
                    !kidsMode || FamilyContentPolicy.isAllowedForKids(
                        item.name,
                        item.categoryIds.map(categoryNames::get),
                    )
                if (!allowedForKids) return@repeat
                if (totalMatches in requestedStart until requestedStart + pageSize) {
                    pageItems += item
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
    fun libraryMatchCandidates(): List<LibraryCandidate> {
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
            for (index in 0 until catalog.size) {
                val item = catalog.itemAt(index)
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
    fun clearCatalogCache() {
        synchronized(lock) {
            // Only the item catalogues. Categories are fetched during connect, not by loadCatalog,
            // so clearing them here emptied the category rail down to "Todas" with no way to
            // repopulate it until the user signed in again.
            catalogs.clear()
        }
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

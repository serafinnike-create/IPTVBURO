package com.lucasserafin94.iptvburo.desktop.data

import com.lucasserafin94.iptvburo.desktop.model.XtreamCatalogPage
import com.lucasserafin94.iptvburo.desktop.model.XtreamPlaybackTarget
import com.lucasserafin94.iptvburo.desktop.model.XtreamSessionSummary
import com.lucasserafin94.iptvburo.desktop.security.XtreamLoginInput
import com.lucasserafin94.iptvburo.xtream.XtreamAccount
import com.lucasserafin94.iptvburo.xtream.XtreamCatalogItem
import com.lucasserafin94.iptvburo.xtream.XtreamCategory
import com.lucasserafin94.iptvburo.xtream.XtreamClient
import com.lucasserafin94.iptvburo.xtream.XtreamContentType
import com.lucasserafin94.iptvburo.xtream.XtreamCredentials
import com.lucasserafin94.iptvburo.xtream.XtreamClientException
import com.lucasserafin94.iptvburo.xtream.XtreamFailureReason
import com.lucasserafin94.iptvburo.xtream.XtreamMovieDetails
import com.lucasserafin94.iptvburo.xtream.XtreamSeriesDetails
import java.net.URI
import java.util.Arrays
import java.util.EnumMap
import java.util.UUID

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

    fun authenticateAndLoadInitial(input: XtreamLoginInput): XtreamSessionSummary {
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
                sourceId = UUID.randomUUID().toString()
                generation
            }

        return try {
            val authenticatedAccount = nextVault.use(client::authenticate)
            val loadedCategories =
                XtreamContentType.entries.associateWith { type ->
                    loadCategoriesAdaptively(nextVault, type)
                }
            val liveCatalog = loadCatalogItems(nextVault, XtreamContentType.LIVE)
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

    fun loadCatalog(contentType: XtreamContentType): XtreamSessionSummary {
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

    fun categories(contentType: XtreamContentType): List<XtreamCategory> =
        synchronized(lock) {
            categories[contentType].orEmpty()
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
        allowedProviderIds: Set<String>? = null,
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

        repeat(catalogItems.size) { index ->
            if (catalogItems.matches(index, categoryId, normalizedQuery, releaseYear, allowedProviderIds)) {
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
                allowedProviderIds = allowedProviderIds,
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

    fun movieDetails(movieId: String): XtreamMovieDetails =
        withCredentials { credentials ->
            client.movieDetails(credentials, movieId)
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
        const val DEFAULT_PAGE_SIZE = 80
        const val MAX_PAGE_SIZE = 200
        const val ZERO_CHAR = '\u0000'
        val WHITESPACE = Regex("\\s+")

    }
}

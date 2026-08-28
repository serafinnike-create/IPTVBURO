package com.lucasserafin94.iptvburo.desktop.data

import com.lucasserafin94.iptvburo.desktop.model.XtreamCatalogPage
import com.lucasserafin94.iptvburo.desktop.model.XtreamPlaybackTarget
import com.lucasserafin94.iptvburo.desktop.model.XtreamSessionSummary
import com.lucasserafin94.iptvburo.desktop.security.XtreamLoginInput
import com.lucasserafin94.iptvburo.domain.model.ContentIdentity
import com.lucasserafin94.iptvburo.domain.model.LibraryCandidate
import com.lucasserafin94.iptvburo.domain.model.MergedSources
import com.lucasserafin94.iptvburo.domain.model.shelfDeduplicationKey
import com.lucasserafin94.iptvburo.xtream.XtreamCatalogItem
import com.lucasserafin94.iptvburo.xtream.XtreamCategory
import com.lucasserafin94.iptvburo.xtream.XtreamContentType
import com.lucasserafin94.iptvburo.xtream.XtreamMovieDetails
import com.lucasserafin94.iptvburo.xtream.XtreamSeriesDetails
import com.lucasserafin94.iptvburo.xtream.XtreamShortEpg
import java.net.URI

/**
 * Several subscriptions browsed as one catalogue.
 *
 * Somebody who buys a second list to fill the gaps in the first ends up switching between them to
 * find which has the film they want. With this on, both arrive as one library: everything from
 * both, each title once, the biggest list leading.
 *
 * ## Why a separate repository rather than a flag on the existing one
 *
 * [SessionXtreamRepository] is built around exactly one credential vault and one set of catalogues,
 * and it is right that it is: that single-session shape is what keeps a password from outliving the
 * subscription it belongs to. Teaching it to hold ten would mean threading a source id through
 * every one of its methods and every one of its locks.
 *
 * So this holds several of them instead, and merges what they return. Each delegate stays exactly
 * as careful with its own credentials as it was alone.
 *
 * ## What a dead subscription does
 *
 * Nothing, to the others. A list whose server is unreachable is recorded in [failedSources] and the
 * rest still load — one dead list blanking a working library would be far worse than the problem
 * this solves.
 */
class MergedCatalogueRepository(
    /**
     * Builds a repository for one subscription.
     *
     * Injected so a test can supply fakes: what is worth exercising here is the merging and the
     * paging over it, not a second copy of what the session repository already proves.
     */
    private val newDelegate: () -> CatalogueRepository = { SessionXtreamRepository() },
) : CatalogueRepository {
    /** One subscription, its delegate, and whether it is currently usable. */
    private class Member(
        val sourceId: String,
        val label: String,
        val repository: CatalogueRepository,
        var failure: String? = null,
    )

    private val lock = Any()
    private val members = mutableListOf<Member>()

    /**
     * The subscription every non-catalogue call goes to.
     *
     * Playback, details and the EPG are about one title, which came from one list. The catalogue is
     * the only thing that is genuinely several lists at once.
     */
    private val primary: CatalogueRepository?
        get() = synchronized(lock) { members.firstOrNull { it.failure == null }?.repository }

    /** Which lists are down, by label, for the screen to name. */
    val failedSources: List<String>
        get() = synchronized(lock) { members.filter { it.failure != null }.map { it.label } }

    /** Whether more than one subscription is actually contributing. */
    val isMerging: Boolean
        get() = synchronized(lock) { members.count { it.failure == null } > 1 }

    /**
     * Adds a subscription to the merge and loads it.
     *
     * Failures are recorded rather than thrown: the viewer needs to know which of their lists is
     * down, and an exception would replace that with a failure of the whole load.
     *
     * @return the label of the source, or null when the cap is already reached.
     */
    fun addSource(
        sourceId: String,
        label: String,
        input: XtreamLoginInput,
    ): String? {
        val alreadyFull =
            synchronized(lock) { members.size >= MergedSources.MAXIMUM_SOURCES }
        if (alreadyFull) {
            input.clear()
            return null
        }

        val repository = newDelegate()
        val member = Member(sourceId = sourceId, label = label, repository = repository)
        val failure =
            runCatching { repository.authenticateAndLoadInitial(input) }
                .exceptionOrNull()
                ?.let { error -> error::class.simpleName ?: "failed" }
        member.failure = failure
        synchronized(lock) { members += member }
        return label
    }

    /**
     * Loads one content type from every subscription.
     *
     * Sequential rather than parallel: each delegate holds its own lock and its own HTTP client,
     * and ten catalogues fetched at once against a modest connection is how a load turns into a
     * timeout. The screen shows progress meanwhile.
     */
    override fun loadCatalog(
        contentType: XtreamContentType,
        forceRefresh: Boolean,
        onProgress: CatalogLoadListener?,
    ): XtreamSessionSummary {
        val working = synchronized(lock) { members.filter { it.failure == null }.toList() }
        working.forEach { member ->
            runCatching { member.repository.loadCatalog(contentType, forceRefresh, onProgress) }
                .onFailure { error ->
                    // Recorded and skipped. The catalogue this list would have contributed is
                    // simply missing; everything else still loads.
                    synchronized(lock) {
                        member.failure = error::class.simpleName ?: "failed"
                    }
                }
        }
        return summary() ?: error("No subscription is loaded.")
    }

    /**
     * A page of the merged catalogue.
     *
     * Merging happens before paging, which is the whole difficulty: page three of a merged
     * catalogue is not page three of any one subscription. So each source is asked for everything
     * matching, the results are merged, and the page is cut from that.
     *
     * That is affordable because each delegate already filters in its own compact arena and only
     * builds objects for rows that match — the expensive part was never the paging.
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
        lockedCategoryIds: Set<String>,
        collapseDuplicates: Boolean,
        allowedLocalIds: Set<String>?,
    ): XtreamCatalogPage {
        val working = synchronized(lock) { members.filter { it.failure == null }.toList() }
        if (working.size <= 1) {
            // One subscription merges to itself, so the whole pass is skipped: walking a catalogue
            // to merge it with nothing is a visible pause for no result.
            val single = working.firstOrNull()?.repository
                ?: return XtreamCatalogPage(emptyList(), requestedPage, pageSize, 0)
            return single.page(
                contentType, categoryId, query, requestedPage, pageSize, releaseYear,
                minimumRating, allowedIdentities, kidsMode, lockedCategoryIds,
                collapseDuplicates, allowedLocalIds,
            )
        }

        val contributions =
            working.map { member ->
                // Everything matching, not one page of it: the merge decides what page three is.
                val all =
                    runCatching {
                        member.repository.page(
                            contentType, categoryId, query, requestedPage = 0,
                            pageSize = MERGE_SCAN_LIMIT, releaseYear = releaseYear,
                            minimumRating = minimumRating, allowedIdentities = allowedIdentities,
                            kidsMode = kidsMode, lockedCategoryIds = lockedCategoryIds,
                            collapseDuplicates = collapseDuplicates, allowedLocalIds = allowedLocalIds,
                        ).items
                    }.getOrDefault(emptyList())
                MergedSources.Contribution(
                    sourceId = member.sourceId,
                    label = member.label,
                    items = all,
                )
            }

        val merged =
            MergedSources.merge(contributions) { item -> item.name.shelfDeduplicationKey() }
        val start = (requestedPage * pageSize).coerceAtLeast(0)
        val slice = merged.items.drop(start).take(pageSize)
        return XtreamCatalogPage(
            items = slice,
            pageIndex = requestedPage,
            pageSize = pageSize,
            totalMatches = merged.items.size,
        )
    }

    /**
     * Searched across every subscription, merged the same way.
     *
     * A search that only reached one list would be the sharpest form of the original problem: the
     * viewer typing a name and being told it is not there when they own it.
     */
    override fun search(
        query: String,
        limit: Int,
    ): List<XtreamCatalogItem> {
        val working = synchronized(lock) { members.filter { it.failure == null }.toList() }
        if (working.size <= 1) return working.firstOrNull()?.repository?.search(query, limit).orEmpty()

        val contributions =
            working.map { member ->
                MergedSources.Contribution(
                    sourceId = member.sourceId,
                    label = member.label,
                    items = runCatching { member.repository.search(query, limit) }.getOrDefault(emptyList()),
                )
            }
        return MergedSources
            .merge(contributions) { item -> item.name.shelfDeduplicationKey() }
            .items
            .take(limit)
    }

    /** Categories from every subscription, one entry per name. */
    override fun categories(contentType: XtreamContentType): List<XtreamCategory> {
        val working = synchronized(lock) { members.filter { it.failure == null }.toList() }
        if (working.size <= 1) return working.firstOrNull()?.repository?.categories(contentType).orEmpty()

        // By name, not by provider id: two subscriptions both carrying "Filmes | Ação" number that
        // category differently, and showing it twice would be the duplication this exists to stop.
        val seen = mutableSetOf<String>()
        return working.flatMap { member ->
            runCatching { member.repository.categories(contentType) }.getOrDefault(emptyList())
        }.filter { category -> seen.add(category.name.trim().lowercase()) }
    }

    /**
     * The first subscription that holds this title.
     *
     * Asked of each in turn rather than of the primary alone: a title contributed by the second
     * list is not in the first, and looking only there would fail to open something the grid had
     * just shown.
     */
    override fun itemByProviderId(
        contentType: XtreamContentType,
        providerId: String,
    ): XtreamCatalogItem? =
        eachWorking { it.itemByProviderId(contentType, providerId) }

    override fun itemByContentKey(
        contentType: XtreamContentType,
        contentKey: String,
    ): XtreamCatalogItem? = eachWorking { it.itemByContentKey(contentType, contentKey) }

    override fun findByTitles(
        normalisedTitles: Set<String>,
        limit: Int,
    ): List<XtreamCatalogItem> {
        val working = synchronized(lock) { members.filter { it.failure == null }.toList() }
        val contributions =
            working.map { member ->
                MergedSources.Contribution(
                    sourceId = member.sourceId,
                    label = member.label,
                    items =
                        runCatching { member.repository.findByTitles(normalisedTitles, limit) }
                            .getOrDefault(emptyList()),
                )
            }
        return MergedSources
            .merge(contributions) { item -> item.name.shelfDeduplicationKey() }
            .items
            .take(limit)
    }

    override fun releasesForYear(
        type: XtreamContentType,
        year: Int,
        limit: Int,
        kidsMode: Boolean,
        lockedCategoryIds: Set<String>,
        rotation: Int,
    ): List<XtreamCatalogItem> {
        val working = synchronized(lock) { members.filter { it.failure == null }.toList() }
        val contributions =
            working.map { member ->
                MergedSources.Contribution(
                    sourceId = member.sourceId,
                    label = member.label,
                    items =
                        runCatching {
                            member.repository.releasesForYear(
                                type, year, limit, kidsMode, lockedCategoryIds, rotation,
                            )
                        }.getOrDefault(emptyList()),
                )
            }
        return MergedSources
            .merge(contributions) { item -> item.name.shelfDeduplicationKey() }
            .items
            .take(limit)
    }

    override fun libraryMatchCandidates(
        kidsMode: Boolean,
        lockedCategoryIds: Set<String>,
        lockedCategoryIdsByContentType: Map<XtreamContentType, Set<String>>,
    ): List<LibraryCandidate> {
        val working = synchronized(lock) { members.filter { it.failure == null }.toList() }
        return working.flatMap { member ->
            runCatching {
                member.repository.libraryMatchCandidates(
                    kidsMode, lockedCategoryIds, lockedCategoryIdsByContentType,
                )
            }.getOrDefault(emptyList())
        }
    }

    /**
     * The total across every working subscription.
     *
     * The count the header shows, so it has to be the merged one — reporting the primary's alone
     * would tell somebody with two lists that they have half of what they can see.
     */
    override fun summary(): XtreamSessionSummary? {
        val working = synchronized(lock) { members.filter { it.failure == null }.toList() }
        val summaries = working.mapNotNull { it.repository.summary() }
        val first = summaries.firstOrNull() ?: return null
        if (summaries.size == 1) return first
        return first.copy(
            loadedItemCount = summaries.sumOf { it.loadedItemCount },
            loadedContentTypes = summaries.flatMap { it.loadedContentTypes }.toSet(),
        )
    }

    override fun placeholderArtworkUrls(): Set<String> {
        val working = synchronized(lock) { members.filter { it.failure == null }.toList() }
        return working.flatMap { it.repository.placeholderArtworkUrls() }.toSet()
    }

    // Everything below is about one title, which came from one list, so it asks each in turn and
    // takes the first real answer. The catalogue is the only thing that is several lists at once.

    override fun authenticateAndLoadInitial(
        input: XtreamLoginInput,
        onProgress: (fraction: Float, detail: SessionXtreamRepository.XtreamLoadStage) -> Unit,
    ): XtreamSessionSummary {
        // The first subscription, added the ordinary way. Later ones come through addSource.
        val repository = newDelegate()
        val summary = repository.authenticateAndLoadInitial(input, onProgress)
        synchronized(lock) {
            members.clear()
            members += Member(sourceId = "primary", label = "", repository = repository)
        }
        return summary
    }

    override fun seriesDetails(seriesId: String): XtreamSeriesDetails =
        requireNotNull(eachWorking { runCatching { it.seriesDetails(seriesId) }.getOrNull() }) {
            "No subscription could load these series details."
        }

    override fun movieDetails(movieId: String): XtreamMovieDetails =
        requireNotNull(eachWorking { runCatching { it.movieDetails(movieId) }.getOrNull() }) {
            "No subscription could load these movie details."
        }

    override fun shortEpg(streamId: String): XtreamShortEpg =
        eachWorking { runCatching { it.shortEpg(streamId) }.getOrNull() } ?: XtreamShortEpg(programs = emptyList(), skippedProgramCount = 0)

    override fun isAllowedForBrowsing(
        item: XtreamCatalogItem,
        kidsMode: Boolean,
        lockedCategoryIds: Set<String>,
    ): Boolean = primary?.isAllowedForBrowsing(item, kidsMode, lockedCategoryIds) ?: true

    /**
     * Playback, from whichever subscription actually has the title.
     *
     * Each in turn rather than the primary alone: a title the second list contributed is not in the
     * first, and building an address there would produce a stream the account cannot play.
     */
    override fun buildConfirmedPlaybackUri(target: XtreamPlaybackTarget): URI =
        requireNotNull(
            eachWorking { runCatching { it.buildConfirmedPlaybackUri(target) }.getOrNull() },
        ) { "No subscription could resolve this stream." }

    override fun measureProviderTransfer(budgetMillis: Long) =
        primary?.measureProviderTransfer(budgetMillis)

    override fun measureProviderLatency(attempts: Int) = primary?.measureProviderLatency(attempts)

    override fun clearCatalogCache() = eachMember { it.clearCatalogCache() }

    override fun clearIncludingDiskCache() = eachMember { it.clearIncludingDiskCache() }

    override fun clear() {
        eachMember { it.clear() }
        synchronized(lock) { members.clear() }
    }

    /** The first non-null answer from a working subscription. */
    private fun <T> eachWorking(block: (CatalogueRepository) -> T?): T? {
        val working = synchronized(lock) { members.filter { it.failure == null }.toList() }
        working.forEach { member ->
            block(member.repository)?.let { return it }
        }
        return null
    }

    private fun eachMember(block: (CatalogueRepository) -> Unit) {
        val all = synchronized(lock) { members.toList() }
        all.forEach { member -> runCatching { block(member.repository) } }
    }

    private companion object {
        /**
         * How many matching rows one subscription may contribute to a merged page.
         *
         * The merge needs everything that matches, not one page of it, because it decides what
         * page three contains. This bounds that: a filter matching a whole forty-thousand-title
         * catalogue would otherwise build forty thousand objects to show eighty of them.
         *
         * Generous enough that no real category reaches it, and the paging above stays correct up
         * to this many merged results.
         */
        const val MERGE_SCAN_LIMIT = 5_000
    }
}

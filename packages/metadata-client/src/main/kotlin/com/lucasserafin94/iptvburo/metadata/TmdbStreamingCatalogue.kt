package com.lucasserafin94.iptvburo.metadata

import com.lucasserafin94.iptvburo.domain.model.ExternalContentId
import com.lucasserafin94.iptvburo.domain.model.ExternalTitle
import com.lucasserafin94.iptvburo.domain.model.ExternalTitleDetails
import com.lucasserafin94.iptvburo.domain.model.ExternalTitleKind
import com.lucasserafin94.iptvburo.domain.model.StreamingProvider

/** Result of building the service shelves, preserving the difference between empty and failed. */
sealed interface TmdbShelfLoadResult {
    data class Loaded(val shelves: List<TmdbServiceShelf>) : TmdbShelfLoadResult

    /**
     * The request failed, so the empty result says nothing about the catalogue.
     *
     * [keyRejected] separates the two failures a user can be told apart: TMDb refusing the key
     * (401/403) and everything else. They need opposite actions — fix the key in Options, or check
     * the connection — and a single "unavailable" sent people to the wrong one.
     *
     * A data class with a default rather than two objects, so existing `is Unavailable` checks and
     * every caller that does not care about the reason keep working unchanged.
     */
    data class Unavailable(val keyRejected: Boolean = false) : TmdbShelfLoadResult
}

/**
 * The real storefront: what every streaming service in a region currently carries.
 *
 * This is the "video rental shop" view. Each service gets a shelf of posters; opening one shows
 * where that film can be watched and hands the user to the official service. **Nothing is ever
 * played here** — BURO holds no rights to these films and does not pretend to.
 *
 * Which services appear is asked of TMDb rather than hard-coded, so a region gets its own services
 * — the list in Brazil is not the list in Germany, and a fixed list would be wrong everywhere but
 * one country.
 *
 * Every call is blocking. Callers run it off the UI thread; there is no coroutine machinery here
 * because [TmdbClient] is a plain synchronous client and wrapping it twice would hide where the
 * work actually happens.
 */
class TmdbStreamingCatalogue(
    private val client: TmdbClient,
    private val region: String,
    /** How many services get a shelf. The rest exist but would be a wall of near-empty rows. */
    private val maxServices: Int = DEFAULT_MAX_SERVICES,
    private val titlesPerService: Int = DEFAULT_TITLES_PER_SERVICE,
) {
    /**
     * One shelf per service, each holding what that service currently carries.
     *
     * Services with nothing to show are dropped rather than rendered as an empty heading. This
     * compatibility helper keeps returning an empty list for both a genuinely empty catalogue and
     * a failed request. UI callers that need to offer a retry use [loadShelves] instead.
     */
    fun shelves(kind: TmdbDiscoverKind = TmdbDiscoverKind.MOVIES): List<TmdbServiceShelf> =
        when (val result = loadShelves(kind)) {
            is TmdbShelfLoadResult.Loaded -> result.shelves
            is TmdbShelfLoadResult.Unavailable -> emptyList()
        }

    /**
     * Builds shelves while preserving whether an empty answer was genuine or caused by failed calls.
     * Partial results remain useful and are shown; only an empty result with at least one failed
     * request becomes [TmdbShelfLoadResult.Unavailable].
     */
    fun loadShelves(kind: TmdbDiscoverKind = TmdbDiscoverKind.MOVIES): TmdbShelfLoadResult {
        val diagnosed = client.withRequestDiagnostics { buildShelves(kind) }
        return if (diagnosed.value.isEmpty() && diagnosed.failureCount > 0) {
            TmdbShelfLoadResult.Unavailable(keyRejected = diagnosed.keyRejected)
        } else {
            TmdbShelfLoadResult.Loaded(diagnosed.value)
        }
    }

    /**
     * Everything one service carries, for the "Ver mais" page behind a shelf.
     *
     * A shelf shows [titlesPerService] titles because that is what fits on a rail, and reaching its
     * end is where the question "what else is on Netflix?" gets asked. This answers it by walking
     * the pages after the first rather than by widening every shelf, which would make the whole
     * screen slower to fill for a question most visits never ask.
     *
     * Stops early on an empty page: TMDb answers a page past the end with no results, and there is
     * no total to compare against without asking for it separately.
     */
    fun allTitlesOnService(
        tmdbProviderId: Int,
        kind: TmdbDiscoverKind = TmdbDiscoverKind.MOVIES,
        maxTitles: Int = DEFAULT_EXPANDED_TITLES,
    ): List<ExternalTitle> {
        val collected = mutableListOf<TmdbDiscoveredTitle>()
        var page = 1
        while (collected.size < maxTitles && page <= MAX_EXPANDED_PAGES) {
            val batch = client.titlesOnProvider(tmdbProviderId, region, PAGE_SIZE, kind, page)
            if (batch.isEmpty()) break
            collected += batch
            page += 1
        }
        return collected
            .distinctBy { discovered -> discovered.id }
            .take(maxTitles)
            .map { discovered -> discovered.toExternalTitle() }
    }

    private fun buildShelves(kind: TmdbDiscoverKind): List<TmdbServiceShelf> {
        // "Coming soon" cannot be grouped by service, because the answer is the set of films no
        // service carries yet. Handled separately rather than forced into the per-provider shape.
        if (kind == TmdbDiscoverKind.UPCOMING) return comingToStreamingShelf()

        // The series directory for the series shelves. Asking the film directory for them wasted
        // several of the twelve slots on shops that carry no series, and a service with nothing is
        // dropped below — which is why Séries and Esta semana came up empty.
        val forSeries = kind == TmdbDiscoverKind.SERIES || kind == TmdbDiscoverKind.THIS_WEEK
        val services = client.watchProviderDirectory(region, forSeries).take(maxServices)
        if (services.isEmpty()) return emptyList()

        return services.mapNotNull { service ->
            val titles = client.titlesOnProvider(service.providerId, region, titlesPerService, kind)
            if (titles.isEmpty()) {
                null
            } else {
                TmdbServiceShelf(
                    provider = StreamingProvider.of(TmdbStreamingDiscovery.slugFor(service.name), service.name),
                    tmdbProviderId = service.providerId,
                    titles = titles.map { discovered -> discovered.toExternalTitle() },
                )
            }
        }
    }

    /**
     * Films in cinemas now that have not reached any subscription service yet.
     *
     * One shelf, not one per service, because that is the shape of the question: these titles are
     * on their way *into* the catalogues, so by definition none of them belongs to a service today.
     * The previous implementation asked each provider for films dated in the future and was
     * answered with nothing — Netflix 1, Prime 0, Disney 0, Apple 0 — which is why the tab was
     * empty rather than merely short.
     *
     * Availability is checked per title because TMDb's discover endpoint cannot express "carried by
     * nobody". That costs one request per candidate, so the candidate list is deliberately short
     * and the shelf stops as soon as it is full.
     */
    private fun comingToStreamingShelf(): List<TmdbServiceShelf> {
        val candidates = client.recentTheatricalReleases(region, limit = UPCOMING_CANDIDATES)
        if (candidates.isEmpty()) return emptyList()

        val notYetStreaming =
            candidates
                .asSequence()
                .filter { candidate -> client.subscriptionProviderNames(candidate.id, region).isEmpty() }
                .take(titlesPerService)
                .toList()

        if (notYetStreaming.isEmpty()) return emptyList()
        return listOf(
            TmdbServiceShelf(
                // No real service owns this shelf, so it carries the catalogue's own identity
                // rather than borrowing a company's name for titles it does not have.
                provider = StreamingProvider.of(COMING_SOON_SLUG, COMING_SOON_SLUG),
                tmdbProviderId = null,
                titles = notYetStreaming.map { discovered -> discovered.toExternalTitle() },
            ),
        )
    }

    /**
     * Everywhere [title] can be watched, ranked by the caller.
     *
     * Null when TMDb has nothing to say — which is not the same as "unavailable", and callers must
     * not render it that way. An empty offer list from a title TMDb *does* know is a different and
     * genuine answer.
     */
    /**
     * Artwork, synopsis, cast and trailer for [title].
     *
     * Separate from [detailsFor], which answers where it can be watched. Different questions with
     * different failure modes: availability can be unknown while the synopsis is well known, and a
     * screen that lost the whole page because one call failed would be worse than one drawing what
     * it has.
     */
    fun pageFor(title: ExternalTitle): TmdbTitleDetails? {
        val tmdbId = title.id.value.toIntOrNull() ?: return null
        return client.titleDetails(tmdbId, isSeries = title.kind == ExternalTitleKind.SERIES)
    }

    fun detailsFor(title: ExternalTitle): ExternalTitleDetails? {
        // The id, not the title text. This threw away an exact identifier and searched for the name
        // instead, which found the wrong film for a translated title and nothing at all for a
        // series — pageFor() beside it has always used the id correctly.
        val tmdbId = title.id.value.toIntOrNull() ?: return null
        val listing =
            client.watchProviders(
                tmdbId = tmdbId,
                region = region,
                isSeries = title.kind == ExternalTitleKind.SERIES,
            ) ?: return null
        return ExternalTitleDetails(
            title = title,
            offers = TmdbStreamingDiscovery.offersFrom(listing, title.title),
        )
    }

    companion object {
        /**
         * Region used until the user says otherwise.
         *
         * Brazil because that is where this build's users are. It is a default, not an assumption
         * the code relies on: every call takes the region explicitly, and the setting changes it.
         */
        const val DEFAULT_REGION: String = "BR"

        /**
         * Enough services to feel like a full storefront without turning the screen into a list of
         * one-title shelves. TMDb returns well over a hundred for Brazil, most of them niche.
         */
        const val DEFAULT_MAX_SERVICES: Int = 12

        const val DEFAULT_TITLES_PER_SERVICE: Int = 20

        /** One TMDb discover page. Asking for less would waste the rest of a page already fetched. */
        const val PAGE_SIZE: Int = 20

        /**
         * How many titles the expanded view of one service holds.
         *
         * A grid of a hundred is a real catalogue to browse and still one screenful of requests —
         * five pages. Beyond this the page becomes a scroll nobody finishes, and each extra page is
         * another round trip the viewer waits through.
         */
        const val DEFAULT_EXPANDED_TITLES: Int = 100

        /** Bounds the walk, so an endpoint that never returns an empty page cannot loop for ever. */
        const val MAX_EXPANDED_PAGES: Int = 8

        /**
         * How many recent releases to test for availability when building "coming soon".
         *
         * Each candidate costs one watch-providers request, so this bounds the work rather than
         * walking every film in cinemas. Comfortably more than a shelf holds, because many recent
         * releases have already reached a service and are filtered out.
         */
        const val UPCOMING_CANDIDATES: Int = 40

        /** Identifies the shelf that belongs to no service. Not a company name. */
        const val COMING_SOON_SLUG: String = "coming-soon"

        /** Regions the app has translations for, offered in the settings picker. */
        val SUPPORTED_REGIONS: List<String> = listOf("BR", "PT", "US", "DE", "IT")
    }
}

/** One service's shelf of covers. */
data class TmdbServiceShelf(
    val provider: StreamingProvider,
    /**
     * TMDb's own numeric id, kept so the shelf can be refreshed without another directory lookup.
     *
     * Null for a shelf that is not a real service — "coming to streaming" is a set of films no
     * provider carries yet, so there is no id to refresh it by.
     */
    val tmdbProviderId: Int?,
    val titles: List<ExternalTitle>,
)

/**
 * A discovered film as the domain sees it.
 *
 * `isDemo` is false: this is a real catalogue answer, so the DEMO badge must not appear on it. The
 * namespace records where the id came from, which is what lets a title be matched against the
 * user's own library later.
 */
private fun TmdbDiscoveredTitle.toExternalTitle(): ExternalTitle =
    ExternalTitle(
        // Namespaced by type: TMDb numbers films and series separately, so id 42 is two different
        // works and a bare id would match the wrong one against the user's library.
        id = ExternalContentId(if (isSeries) TMDB_SERIES_NAMESPACE else TMDB_NAMESPACE, id.toString()),
        title = title,
        kind = if (isSeries) ExternalTitleKind.SERIES else ExternalTitleKind.MOVIE,
        year = year,
        releaseDate = releaseDate,
        posterUrl = posterUrl,
        isDemo = false,
    )

const val TMDB_NAMESPACE: String = "tmdb"

/** Series are numbered separately from films, so they get their own namespace. */
const val TMDB_SERIES_NAMESPACE: String = "tmdb-tv"

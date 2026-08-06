package com.lucasserafin94.iptvburo.desktop.data

import com.lucasserafin94.iptvburo.domain.model.ExternalContentId
import com.lucasserafin94.iptvburo.domain.model.ExternalTitle
import com.lucasserafin94.iptvburo.domain.model.ExternalTitleDetails
import com.lucasserafin94.iptvburo.domain.model.ExternalTitleKind
import com.lucasserafin94.iptvburo.domain.model.StreamingProvider
import com.lucasserafin94.iptvburo.metadata.TmdbClient
import com.lucasserafin94.iptvburo.metadata.TmdbDiscoverKind
import com.lucasserafin94.iptvburo.metadata.TmdbDiscoveredTitle

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
     * Services with nothing to show are dropped rather than rendered as an empty heading. An empty
     * result overall means the region has nothing, the key is unset, or TMDb could not be reached —
     * all three look the same to the caller, and all three mean "show nothing" rather than an error
     * the user must dismiss.
     */
    fun shelves(kind: TmdbDiscoverKind = TmdbDiscoverKind.MOVIES): List<TmdbServiceShelf> {
        val services = client.watchProviderDirectory(region).take(maxServices)
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
     * Everywhere [title] can be watched, ranked by the caller.
     *
     * Null when TMDb has nothing to say — which is not the same as "unavailable", and callers must
     * not render it that way. An empty offer list from a title TMDb *does* know is a different and
     * genuine answer.
     */
    fun detailsFor(title: ExternalTitle): ExternalTitleDetails? {
        val listing = client.watchProviders(title.title, title.year, region) ?: return null
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

        /** Regions the app has translations for, offered in the settings picker. */
        val SUPPORTED_REGIONS: List<String> = listOf("BR", "PT", "US", "DE", "IT")
    }
}

/** One service's shelf of covers. */
data class TmdbServiceShelf(
    val provider: StreamingProvider,
    /** TMDb's own numeric id, kept so the shelf can be refreshed without another directory lookup. */
    val tmdbProviderId: Int,
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
        posterUrl = posterUrl,
        isDemo = false,
    )

const val TMDB_NAMESPACE: String = "tmdb"

/** Series are numbered separately from films, so they get their own namespace. */
const val TMDB_SERIES_NAMESPACE: String = "tmdb-tv"

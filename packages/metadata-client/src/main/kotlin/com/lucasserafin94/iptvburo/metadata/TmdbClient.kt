package com.lucasserafin94.iptvburo.metadata

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.time.Duration
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * People and artwork from The Movie Database.
 *
 * The IPTV provider sends the cast as a single comma-separated string — names and nothing else — so
 * a photo or a real filmography can only come from outside. TMDb is the usual source and is free
 * for personal use, but it requires the user's own API key: the app ships without one, and every
 * call is a no-op until one is supplied.
 *
 * Nothing here touches the playlist or its credentials. The only thing sent is a title or a person's
 * name, which the user is already looking at on screen.
 */
class TmdbClient(
    private val apiKey: String?,
    private val client: OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(10))
            .readTimeout(Duration.ofSeconds(15))
            .build(),
    private val baseUrl: HttpUrl = DEFAULT_BASE_URL.toHttpUrl(),
    private val imageBaseUrl: String = DEFAULT_IMAGE_BASE_URL,
    private val language: String = "pt-BR",
) {
    /** Whether metadata lookups can run at all. */
    val isConfigured: Boolean
        get() = !apiKey.isNullOrBlank()

    /**
     * The person TMDb knows by [name], or null when there is no confident match.
     *
     * Only the first result is taken. Name searches are ambiguous and picking further down the list
     * would confidently show the wrong person's face, which is worse than showing none.
     */
    fun findPerson(name: String): TmdbPerson? {
        val key = apiKey?.takeIf(String::isNotBlank) ?: return null
        if (name.isBlank()) return null

        val url =
            baseUrl.newBuilder()
                .addPathSegments("search/person")
                .addQueryParameter("api_key", key)
                .addQueryParameter("query", name.trim())
                .addQueryParameter("language", language)
                .addQueryParameter("include_adult", "false")
                .build()

        val root = get(url) ?: return null
        val first =
            root.getAsJsonArray("results")
                ?.firstOrNull()
                ?.takeIf { it.isJsonObject }
                ?.asJsonObject
                ?: return null

        val id = first.int("id") ?: return null
        return TmdbPerson(
            id = id,
            name = first.string("name") ?: name,
            profileImageUrl = first.string("profile_path")?.let { path -> "$imageBaseUrl/w342$path" },
            knownFor = first.string("known_for_department"),
        )
    }

    /**
     * Everything [personId] is credited in, most popular first.
     *
     * Ordered by popularity rather than by date because the point is recognition: a viewer scanning
     * an actor's page is looking for the title they already know.
     */
    fun filmography(personId: Int, limit: Int = 24): List<TmdbCredit> {
        val key = apiKey?.takeIf(String::isNotBlank) ?: return emptyList()
        require(limit in 1..100)

        val url =
            baseUrl.newBuilder()
                .addPathSegments("person/$personId/combined_credits")
                .addQueryParameter("api_key", key)
                .addQueryParameter("language", language)
                .build()

        val root = get(url) ?: return emptyList()
        val cast: JsonArray = root.getAsJsonArray("cast") ?: return emptyList()
        return cast
            .mapNotNull { element: JsonElement ->
                val credit = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                val title = credit.string("title") ?: credit.string("name") ?: return@mapNotNull null
                TmdbCredit(
                    title = title,
                    year = (credit.string("release_date") ?: credit.string("first_air_date"))
                        ?.take(4)
                        ?.toIntOrNull(),
                    posterUrl = credit.string("poster_path")?.let { path -> "$imageBaseUrl/w185$path" },
                    character = credit.string("character")?.takeIf(String::isNotBlank),
                    popularity = credit.double("popularity") ?: 0.0,
                )
            }.sortedByDescending(TmdbCredit::popularity)
            .distinctBy { credit -> credit.title.lowercase() }
            .take(limit)
    }

    /**
     * The YouTube id of a trailer for [title], or null when there is none.
     *
     * Most providers leave the trailer field empty even for films that plainly have one, so this is
     * the difference between a Trailer button existing and not. The title is searched rather than
     * matched on an id because the playlist carries no external identifier at all.
     *
     * Prefers the user's language and falls back to whatever exists: a trailer in the wrong language
     * is better than none.
     */
    fun findTrailer(title: String, year: Int?): String? {
        val key = apiKey?.takeIf(String::isNotBlank) ?: return null
        if (title.isBlank()) return null

        val searchUrl =
            baseUrl.newBuilder()
                .addPathSegments("search/movie")
                .addQueryParameter("api_key", key)
                .addQueryParameter("query", title.trim())
                .addQueryParameter("language", language)
                .addQueryParameter("include_adult", "false")
                .apply { year?.let { addQueryParameter("year", it.toString()) } }
                .build()

        val movieId =
            get(searchUrl)
                ?.getAsJsonArray("results")
                ?.firstOrNull()
                ?.takeIf { it.isJsonObject }
                ?.asJsonObject
                ?.int("id")
                ?: return null

        // Language first, then anything: TMDb returns an empty list rather than falling back itself.
        return trailerFor(movieId, key, language) ?: trailerFor(movieId, key, null)
    }

    private fun trailerFor(movieId: Int, key: String, forLanguage: String?): String? {
        val url =
            baseUrl.newBuilder()
                .addPathSegments("movie/$movieId/videos")
                .addQueryParameter("api_key", key)
                .apply { forLanguage?.let { addQueryParameter("language", it) } }
                .build()

        val videos = get(url)?.getAsJsonArray("results") ?: return null
        return videos
            .mapNotNull { element -> element.takeIf { it.isJsonObject }?.asJsonObject }
            .filter { video -> video.string("site").equals("YouTube", ignoreCase = true) }
            // A trailer, not a clip or a behind-the-scenes reel, which the same endpoint returns.
            .sortedByDescending { video -> if (video.string("type") == "Trailer") 1 else 0 }
            .firstOrNull { video -> video.string("type") in TRAILER_TYPES }
            ?.string("key")
    }

    /**
     * Biography and birth details for a person's page.
     *
     * Separate from [findPerson] because the search endpoint does not return them and most screens
     * never need them.
     */
    fun personDetails(personId: Int): TmdbPersonDetails? {
        val key = apiKey?.takeIf(String::isNotBlank) ?: return null

        val url =
            baseUrl.newBuilder()
                .addPathSegments("person/$personId")
                .addQueryParameter("api_key", key)
                .addQueryParameter("language", language)
                .build()

        val root = get(url) ?: return null
        return TmdbPersonDetails(
            biography = root.string("biography")?.takeIf(String::isNotBlank),
            birthday = root.string("birthday"),
            placeOfBirth = root.string("place_of_birth"),
        )
    }

    /**
     * Which services carry [title], in [region].
     *
     * This is TMDb relaying JustWatch's data, and it comes with two conditions that are not
     * negotiable: **the data must be attributed to JustWatch on every item that shows it**, and
     * access is revoked for use that does not comply. See [WATCH_PROVIDER_ATTRIBUTION].
     *
     * What it gives: which services have the title, split into subscription, ad-funded, free, rent
     * and buy. What it does not give, at all: **prices**. A rental entry names the service and
     * nothing more. Any "cheapest" claim built on this would have to be invented, so none is made.
     *
     * Returns null when nothing is known — an unconfigured key, a title TMDb cannot find, or a
     * region with no listings. Callers treat that as "we cannot say", never as "not available".
     */
    fun watchProviders(
        title: String,
        year: Int?,
        region: String,
    ): TmdbWatchProviders? {
        val key = apiKey?.takeIf(String::isNotBlank) ?: return null
        if (title.isBlank() || region.isBlank()) return null

        val movieId = findMovieId(title, year, key) ?: return null

        // One call carries every region; the response is sliced locally rather than asking per
        // region, which is both fewer requests and what TMDb's own docs describe.
        val url =
            baseUrl.newBuilder()
                .addPathSegments("movie/$movieId/watch/providers")
                .addQueryParameter("api_key", key)
                .build()

        val forRegion =
            get(url)
                ?.getAsJsonObject("results")
                ?.getAsJsonObject(region.trim().uppercase())
                ?: return null

        val entries = { bucket: String ->
            forRegion
                .getAsJsonArray(bucket)
                // The key is absent, not empty, when a bucket has nothing. Never assume it exists.
                ?.mapNotNull { element -> element.takeIf { it.isJsonObject }?.asJsonObject }
                ?.mapNotNull { entry ->
                    val id = entry.int("provider_id") ?: return@mapNotNull null
                    val name = entry.string("provider_name")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                    // logo_path is deliberately not read. TMDb serving those bytes is not a licence
                    // to redistribute another company's mark, so providers stay text-only.
                    TmdbWatchProvider(providerId = id, name = name)
                }.orEmpty()
        }

        val subscription = entries("flatrate")
        val ads = entries("ads")
        val free = entries("free")
        val rent = entries("rent")
        val buy = entries("buy")

        if (subscription.isEmpty() && ads.isEmpty() && free.isEmpty() && rent.isEmpty() && buy.isEmpty()) {
            return null
        }

        return TmdbWatchProviders(
            region = region.trim().uppercase(),
            subscription = subscription,
            withAds = ads,
            free = free,
            rent = rent,
            buy = buy,
            // TMDb's own page for the title. It is not a deep link into any service — TMDb states
            // plainly that it does not provide those — so it is a fallback, not the destination.
            tmdbWatchPageUrl = forRegion.string("link"),
        )
    }

    /**
     * What is currently on [providerId] in [region] — the shelf behind "browse by service".
     *
     * Uses TMDb's discovery endpoint filtered by watch provider, which is the closest thing to
     * "what is on Netflix right now" the API offers. Sorted by popularity, so the shelf reads like
     * a storefront rather than an alphabetical index.
     *
     * [providerId] is TMDb's own numeric provider id, not the app's slug: this is their filter and
     * it only understands their numbering.
     *
     * Posters are included. Unlike the services' brand logos, a film's own poster art is served by
     * TMDb for exactly this purpose, and the app already shows the same images for cast and
     * catalogue. Empty rather than null when nothing is found — an empty shelf is simply dropped.
     */
    fun titlesOnProvider(
        providerId: Int,
        region: String,
        limit: Int = 20,
        kind: TmdbDiscoverKind = TmdbDiscoverKind.MOVIES,
    ): List<TmdbDiscoveredTitle> {
        val key = apiKey?.takeIf(String::isNotBlank) ?: return emptyList()
        if (region.isBlank()) return emptyList()

        // Series live under a different path and name their fields differently — `name` and
        // `first_air_date` where films use `title` and `release_date`.
        val isSeries = kind == TmdbDiscoverKind.SERIES || kind == TmdbDiscoverKind.THIS_WEEK
        val today = java.time.LocalDate.now()

        val url =
            baseUrl.newBuilder()
                .addPathSegments(if (isSeries) "discover/tv" else "discover/movie")
                .addQueryParameter("api_key", key)
                .addQueryParameter("language", language)
                .addQueryParameter("with_watch_providers", providerId.toString())
                // Required alongside with_watch_providers; TMDb ignores the filter without it.
                .addQueryParameter("watch_region", region.trim().uppercase())
                .addQueryParameter("include_adult", "false")
                .apply {
                    when (kind) {
                        TmdbDiscoverKind.MOVIES -> {
                            addQueryParameter("sort_by", "primary_release_date.desc")
                            // Without an upper bound the list fills with titles dated years ahead
                            // that nobody can watch yet — sorted newest first, they crowd out
                            // everything real.
                            addQueryParameter("primary_release_date.lte", today.toString())
                            // And a lower bound, or a single mis-dated entry from 1900 anchors it.
                            addQueryParameter("vote_count.gte", "10")
                        }
                        TmdbDiscoverKind.SERIES -> {
                            addQueryParameter("sort_by", "first_air_date.desc")
                            addQueryParameter("first_air_date.lte", today.toString())
                            addQueryParameter("vote_count.gte", "10")
                        }
                        // What actually aired in the last week, series or film.
                        //
                        // `first_air_date` is when a *series* premiered, not when its latest episode
                        // went out — so a show releasing weekly since 2023 sorts to the bottom of a
                        // "newest" shelf even while it is the thing people are waiting for. TMDb's
                        // air-date window is the field that answers "what is new this week".
                        TmdbDiscoverKind.THIS_WEEK -> {
                            addQueryParameter("air_date.gte", today.minusDays(7).toString())
                            addQueryParameter("air_date.lte", today.toString())
                            // Popularity within the week, not date: everything here aired in the
                            // same seven days, so what people are actually watching is the more
                            // useful order.
                            addQueryParameter("sort_by", "popularity.desc")
                        }
                        TmdbDiscoverKind.UPCOMING -> {
                            // Dated after today, soonest first, so the shelf reads as a calendar.
                            addQueryParameter("sort_by", "primary_release_date.asc")
                            addQueryParameter("primary_release_date.gte", today.plusDays(1).toString())
                        }
                    }
                }.build()

        return get(url)
            ?.getAsJsonArray("results")
            ?.mapNotNull { element -> element.takeIf { it.isJsonObject }?.asJsonObject }
            ?.mapNotNull { result ->
                val id = result.int("id") ?: return@mapNotNull null
                val title = result.string(if (isSeries) "name" else "title") ?: return@mapNotNull null
                TmdbDiscoveredTitle(
                    id = id,
                    title = title,
                    year =
                        result
                            .string(if (isSeries) "first_air_date" else "release_date")
                            ?.take(4)
                            ?.toIntOrNull(),
                    // w342 is the poster width the catalogue grid already uses: large enough for a
                    // card, small enough not to stall a shelf of twenty.
                    posterUrl = result.string("poster_path")?.let { path -> "$imageBaseUrl/w342$path" },
                    overview = result.string("overview"),
                    rating = result.double("vote_average"),
                    isSeries = isSeries,
                    releaseDate = result.string(if (isSeries) "first_air_date" else "release_date"),
                )
            }?.take(limit)
            .orEmpty()
    }

    /**
     * Which services can be browsed in [region], as TMDb's own ids and names.
     *
     * Used to decide which shelves exist rather than hard-coding a list that would be wrong outside
     * one country. Ordered by TMDb's `display_priority`, which is their view of what a user in that
     * region expects to see first.
     */
    fun watchProviderDirectory(region: String): List<TmdbWatchProvider> {
        val key = apiKey?.takeIf(String::isNotBlank) ?: return emptyList()
        if (region.isBlank()) return emptyList()

        val url =
            baseUrl.newBuilder()
                .addPathSegments("watch/providers/movie")
                .addQueryParameter("api_key", key)
                .addQueryParameter("language", language)
                .addQueryParameter("watch_region", region.trim().uppercase())
                .build()

        return get(url)
            ?.getAsJsonArray("results")
            ?.mapNotNull { element -> element.takeIf { it.isJsonObject }?.asJsonObject }
            ?.sortedBy { entry -> entry.int("display_priority") ?: Int.MAX_VALUE }
            ?.mapNotNull { entry ->
                val id = entry.int("provider_id") ?: return@mapNotNull null
                val name = entry.string("provider_name") ?: return@mapNotNull null
                TmdbWatchProvider(providerId = id, name = name)
            }.orEmpty()
    }

    /** The TMDb id for a title, or null when the search finds nothing. */
    private fun findMovieId(
        title: String,
        year: Int?,
        key: String,
    ): Int? {
        val searchUrl =
            baseUrl.newBuilder()
                .addPathSegments("search/movie")
                .addQueryParameter("api_key", key)
                .addQueryParameter("query", title.trim())
                .addQueryParameter("language", language)
                .addQueryParameter("include_adult", "false")
                .apply { year?.let { addQueryParameter("year", it.toString()) } }
                .build()

        return get(searchUrl)
            ?.getAsJsonArray("results")
            ?.firstOrNull()
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
            ?.int("id")
    }

    /**
     * Runs the request, returning null on any failure.
     *
     * Metadata is an enhancement: a rate limit, an expired key or an offline machine must leave the
     * page showing what the provider gave, never an error the user has to dismiss.
     */
    private fun get(url: HttpUrl): JsonObject? =
        runCatching {
            val request =
                Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                JsonParser.parseString(response.body?.string() ?: return null).asJsonObject
            }
        }.getOrNull()

    /** The API key is a secret; it must never reach a log or a crash report. */
    override fun toString(): String = "TmdbClient(configured=$isConfigured)"

    private companion object {
        const val DEFAULT_BASE_URL = "https://api.themoviedb.org/3/"
        const val DEFAULT_IMAGE_BASE_URL = "https://image.tmdb.org/t/p"

        /** A teaser is still worth showing when no full trailer was published. */
        val TRAILER_TYPES = setOf("Trailer", "Teaser")
    }
}

data class TmdbPerson(
    val id: Int,
    val name: String,
    val profileImageUrl: String?,
    val knownFor: String?,
)

data class TmdbCredit(
    val title: String,
    val year: Int?,
    val posterUrl: String?,
    val character: String?,
    val popularity: Double,
)

data class TmdbPersonDetails(
    val biography: String?,
    val birthday: String?,
    val placeOfBirth: String?,
)

/**
 * A film found while browsing a service's shelf.
 *
 * [posterUrl] is the film's own poster art, which TMDb serves for display and the app already uses
 * elsewhere. This is not the same as a service's brand logo, which is that company's mark and is
 * deliberately never fetched.
 */
data class TmdbDiscoveredTitle(
    val id: Int,
    val title: String,
    val year: Int?,
    val posterUrl: String?,
    val overview: String?,
    val rating: Double?,
    val isSeries: Boolean = false,
    /** ISO date, when known. Kept whole so an upcoming shelf can show the day, not just the year. */
    val releaseDate: String? = null,
)

/**
 * What a shelf is showing.
 *
 * [UPCOMING] carries a caveat worth knowing: TMDb dates a release, but **does not say which service
 * will carry it** before it arrives. So an upcoming shelf under a provider's name is "coming out
 * soon", not "coming to this service" — the app must not word it as the latter.
 */
enum class TmdbDiscoverKind {
    MOVIES,
    SERIES,
    UPCOMING,

    /**
     * What went out in the last seven days.
     *
     * Queries the *series* endpoint, because weekly episodes are the thing this answers: a show
     * airing every Friday since 2023 is invisible on a shelf sorted by premiere date, which is
     * exactly the case that made this necessary.
     */
    THIS_WEEK,
}

/**
 * One service that carries a title.
 *
 * There is no logo field, deliberately. TMDb serves a `logo_path` for each of these, but serving
 * the bytes is not a licence to redistribute another company's mark — those belong to the services
 * themselves. Text-only is both the safe reading and what the product's own rules require.
 */
data class TmdbWatchProvider(
    val providerId: Int,
    val name: String,
)

/**
 * Where a title can be watched in one region, as reported by TMDb from JustWatch.
 *
 * The buckets mirror the API's own. Two of them are worth reading carefully:
 *
 * - [free] is TMDb's "no payment required", which is not the same claim as ad-funded. Collapsing it
 *   into [withAds] would tell the user to expect adverts that may not exist, so the two stay apart
 *   here and the caller decides.
 * - [rent] and [buy] carry **no prices**. The API does not return them in any form. A "cheapest"
 *   label on this data would be invented, and none is produced.
 */
data class TmdbWatchProviders(
    val region: String,
    val subscription: List<TmdbWatchProvider> = emptyList(),
    val withAds: List<TmdbWatchProvider> = emptyList(),
    val free: List<TmdbWatchProvider> = emptyList(),
    val rent: List<TmdbWatchProvider> = emptyList(),
    val buy: List<TmdbWatchProvider> = emptyList(),
    /** TMDb's page for the title. A fallback destination, never a deep link into a service. */
    val tmdbWatchPageUrl: String? = null,
) {
    val isEmpty: Boolean
        get() = subscription.isEmpty() && withAds.isEmpty() && free.isEmpty() && rent.isEmpty() && buy.isEmpty()
}

/**
 * The attribution that must appear on every item showing this data.
 *
 * JustWatch's terms require the source to be credited *per media item* — a line in an About screen
 * is explicitly not enough — and state that access is revoked where usage does not comply. It is a
 * constant rather than a translated string because it names a company and must read identically in
 * every language.
 */
const val WATCH_PROVIDER_ATTRIBUTION: String = "Streaming data provided by JustWatch"

private fun JsonObject.string(name: String): String? =
    get(name)?.takeUnless { it.isJsonNull }?.asString?.takeIf(String::isNotBlank)

private fun JsonObject.int(name: String): Int? =
    get(name)?.takeUnless { it.isJsonNull }?.asInt

private fun JsonObject.double(name: String): Double? =
    get(name)?.takeUnless { it.isJsonNull }?.asDouble

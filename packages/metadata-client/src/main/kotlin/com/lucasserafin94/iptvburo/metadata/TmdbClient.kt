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

/** Value produced by a group of synchronous TMDb calls and how many of those calls failed. */
internal data class TmdbRequestDiagnostics<T>(
    val value: T,
    val failureCount: Int,
    /**
     * Whether TMDb answered at least one of those calls by rejecting the key.
     *
     * Kept separate from the count because it is the one failure the user can actually act on, and
     * telling them to check their connection when the connection is fine sends them looking in the
     * wrong place — which is exactly what happened in BUG-021.
     *
     * A boolean rather than the status or the response body: TMDb takes the key as a query
     * parameter, so anything richer risks carrying it out of this class.
     */
    val keyRejected: Boolean = false,
)

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
    /**
     * Failures observed by the current blocking catalogue operation.
     *
     * A ThreadLocal keeps simultaneous details and shelf requests independent. The public metadata
     * helpers still return null/empty as before; a catalogue build can additionally ask whether an
     * empty result was caused by the network, HTTP status or malformed JSON.
     */
    private val requestFailures = ThreadLocal.withInitial { 0 }

    /** Set when TMDb rejected the key during the current diagnosed operation. See [keyRejected]. */
    private val requestKeyRejected = ThreadLocal.withInitial { false }

    internal fun <T> withRequestDiagnostics(block: () -> T): TmdbRequestDiagnostics<T> {
        val previousFailures = requestFailures.get()
        val previousRejected = requestKeyRejected.get()
        requestFailures.set(0)
        requestKeyRejected.set(false)
        return try {
            val value = block()
            TmdbRequestDiagnostics(
                value = value,
                failureCount = requestFailures.get(),
                keyRejected = requestKeyRejected.get(),
            )
        } finally {
            requestFailures.set(previousFailures)
            requestKeyRejected.set(previousRejected)
        }
    }

    private fun recordRequestFailure(keyRejected: Boolean = false) {
        requestFailures.set(requestFailures.get() + 1)
        if (keyRejected) requestKeyRejected.set(true)
    }

    /** Whether metadata lookups can run at all. */
    val isConfigured: Boolean
        get() = !apiKey.isNullOrBlank()

    /**
     * Whether the configured credential is a v4 Read Access Token rather than a v3 API key.
     *
     * TMDb hands out two things from the same settings page, and people quite reasonably copy
     * whichever they land on. They are not interchangeable: a v3 key goes in the `api_key` query
     * parameter, while a v4 token is a JWT and must travel as `Authorization: Bearer`. Sent the
     * wrong way, a perfectly valid token is answered with 401 — which is exactly what happened on a
     * real install, and looked to the user like their key was bad.
     *
     * Detected by shape rather than by asking the user which one they pasted. A JWT is three
     * base64url segments separated by dots and always begins `eyJ`, which no v3 key does: those are
     * 32 hexadecimal characters.
     */
    private val usesBearerToken: Boolean
        get() {
            val key = apiKey?.trim().orEmpty()
            return key.startsWith("eyJ") && key.count { it == '.' } == 2
        }

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
                    // The catalogue's own id and kind, which the response has always carried and
                    // nothing here read. Without them a credit was only a title, and clicking one
                    // could do nothing but search the user's playlist for a matching name — so a
                    // film the playlist did not have led nowhere at all, when the whole point of
                    // the subscriptions screen is to say where it *can* be watched.
                    // Null rather than a reason to discard the credit.
                    //
                    // A row without an id can still be shown and still matched against the user's
                    // own playlist by name; dropping it would silently remove a film from an
                    // actor's filmography because of a field that only affects one of the two ways
                    // it can be opened.
                    id = credit.int("id"),
                    isSeries = credit.string("media_type") == "tv",
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

    /**
     * The audience score for a film, found by the same search the trailer lookup runs.
     *
     * Two figures rather than one, because a score without a count is not a claim anybody can weigh:
     * 8.0 from twelve viewers and 8.0 from ninety thousand look identical and mean different things.
     * The details screen shows both.
     *
     * Deliberately a separate call from [findTrailer] rather than a widened return type: most
     * screens want one or the other, and a title with no trailer still has a score worth showing.
     */
    fun findAudienceScore(title: String, year: Int?): TmdbAudienceScore? {
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

        val match =
            get(searchUrl)
                ?.getAsJsonArray("results")
                ?.firstOrNull()
                ?.takeIf { it.isJsonObject }
                ?.asJsonObject
                ?: return null

        val average = match.double("vote_average") ?: return null
        // A score nobody voted on is not a score. TMDb returns 0.0 with a count of zero for titles
        // it holds but nobody has rated, and showing "0%" would read as a verdict rather than as
        // an absence of one.
        val votes = match.int("vote_count") ?: 0
        if (votes <= 0) return null

        // The id travels with the score so a caller wanting the platform logos does not have to
        // run the same search again to find it.
        return TmdbAudienceScore(
            average = average,
            voteCount = votes,
            tmdbId = match.int("id"),
        )
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
    /**
     * Where [tmdbId] can be watched, taking the id rather than searching for it.
     *
     * This used to take a title and a year and run a `search/movie` to recover an id the caller
     * already held, which failed in two ways at once. A translated or differently punctuated title
     * matched the wrong film or nothing; and because the path was hardcoded to `movie/`, a *series*
     * was looked up among films and could never be found at all. Either way the offer list came
     * back empty and the screen reported the title as unavailable — the "only on TV Guru" that
     * users reported.
     */
    fun watchProviders(
        tmdbId: Int,
        region: String,
        isSeries: Boolean = false,
    ): TmdbWatchProviders? {
        val key = apiKey?.takeIf(String::isNotBlank) ?: return null
        if (region.isBlank()) return null

        // One call carries every region; the response is sliced locally rather than asking per
        // region, which is both fewer requests and what TMDb's own docs describe.
        val url =
            baseUrl.newBuilder()
                .addPathSegments(if (isSeries) "tv/$tmdbId/watch/providers" else "movie/$tmdbId/watch/providers")
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
                    TmdbWatchProvider(
                        providerId = id,
                        name = name,
                        // w92 is the smallest ladder TMDb publishes for these and is more than a
                        // row of marks needs; asking for a larger one would download several times
                        // the bytes to draw the same twenty pixels.
                        logoUrl = entry.string("logo_path")?.let { path -> "$imageBaseUrl/w92$path" },
                    )
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
    /**
     * Films already in cinemas that no streaming service carries yet.
     *
     * This is what "Em breve" is actually asking: which titles are on their way *into* the
     * catalogues of Netflix, Prime and the rest. It deliberately does not filter by provider,
     * because the answer is the set of films that belong to *no* provider — a film that has not
     * reached streaming is on nobody's shelf, which is precisely why the old query returned
     * nothing. Measured before changing it: Netflix 1 result, Prime 0, Disney 0, Apple 0, even
     * with the window widened to a year.
     *
     * Recent theatrical releases only. Something released years ago and still absent from every
     * service is not "coming soon", it is a film nobody licensed.
     *
     * The result still has to be filtered by the caller against each title's watch providers —
     * TMDb cannot express "has no provider" as a discover parameter, so availability is checked
     * per title afterwards.
     */
    fun recentTheatricalReleases(
        region: String,
        limit: Int = 20,
        monthsBack: Long = 6,
    ): List<TmdbDiscoveredTitle> {
        val key = apiKey?.takeIf(String::isNotBlank) ?: return emptyList()
        if (region.isBlank()) return emptyList()
        val today = java.time.LocalDate.now()

        val url =
            baseUrl.newBuilder()
                .addPathSegments("discover/movie")
                .addQueryParameter("api_key", key)
                .addQueryParameter("language", language)
                .addQueryParameter("region", region.trim().uppercase())
                .addQueryParameter("include_adult", "false")
                // Popularity, not date: this is a shelf of films people are waiting for, and the
                // most anticipated release is more useful at the front than the most recent.
                .addQueryParameter("sort_by", "popularity.desc")
                .addQueryParameter("primary_release_date.gte", today.minusMonths(monthsBack).toString())
                .addQueryParameter("primary_release_date.lte", today.toString())
                // Theatrical only. Without this the list fills with direct-to-video titles that
                // were never going to appear on a service anyway.
                .addQueryParameter("with_release_type", "3")
                // Enough votes to be a real film rather than a catalogue artefact.
                .addQueryParameter("vote_count.gte", "20")
                .build()

        return get(url).parseDiscoveredTitles(isSeries = false).take(limit)
    }

    /**
     * Where [tmdbId] can currently be watched on a subscription in [region].
     *
     * Only `flatrate` counts. A film available to rent or buy has not "arrived on streaming" in the
     * sense anyone means when they ask where to watch something they already pay for.
     */
    fun subscriptionProviderNames(
        tmdbId: Int,
        region: String,
    ): List<String> {
        val key = apiKey?.takeIf(String::isNotBlank) ?: return emptyList()
        val url =
            baseUrl.newBuilder()
                .addPathSegments("movie/$tmdbId/watch/providers")
                .addQueryParameter("api_key", key)
                .build()

        return get(url)
            ?.getAsJsonObject("results")
            ?.getAsJsonObject(region.trim().uppercase())
            ?.getAsJsonArray("flatrate")
            ?.mapNotNull { element -> element.takeIf { it.isJsonObject }?.asJsonObject }
            ?.mapNotNull { entry -> entry.string("provider_name") }
            .orEmpty()
    }

    fun titlesOnProvider(
        providerId: Int,
        region: String,
        limit: Int = 20,
        kind: TmdbDiscoverKind = TmdbDiscoverKind.MOVIES,
        /**
         * Which page of results to ask for, one-based.
         *
         * A TMDb discover page holds twenty titles, which is exactly a shelf, so the shelves have
         * never needed this. "Ver mais" does: it asks for the pages after the first to build the
         * full list for one service, and without a page number every request would return the same
         * twenty titles the shelf is already showing.
         */
        page: Int = 1,
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
                // Coerced rather than trusted: TMDb rejects a page below 1 and caps at 500, and a
                // caller computing this from a scroll position can easily produce either.
                .addQueryParameter("page", page.coerceIn(1, MAX_DISCOVER_PAGE).toString())
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

        return get(url).parseDiscoveredTitles(isSeries).take(limit)
    }

    /**
     * Reads a `discover` response into titles.
     *
     * Shared by every discover query rather than repeated per call site: films and series name the
     * same fields differently (`title`/`name`, `release_date`/`first_air_date`), and that mapping
     * is exactly the kind of thing that drifts when it is written out twice.
     */
    private fun com.google.gson.JsonObject?.parseDiscoveredTitles(isSeries: Boolean): List<TmdbDiscoveredTitle> =
        this
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
            }.orEmpty()

    /**
     * Which services can be browsed in [region], as TMDb's own ids and names.
     *
     * Used to decide which shelves exist rather than hard-coding a list that would be wrong outside
     * one country. Ordered by TMDb's `display_priority`, which is their view of what a user in that
     * region expects to see first.
     */
    /**
     * The services worth showing in [region], for the kind of thing being listed.
     *
     * [forSeries] picks the matching TMDb directory, and it is not a detail. This always asked for
     * the *film* directory, whose top slots in Brazil include Google Play Movies and the Apple TV
     * Store — transactional film shops that carry no series at all. Building the Séries shelves
     * from that list spent several of the twelve slots on services that return nothing, and every
     * empty service is dropped, so the screen came up blank.
     *
     * Measured against TMDb rather than assumed: of the first eight entries in the film directory,
     * providers 3 and 2 return 0 series and two more return fewer than five, while the series
     * directory puts JustWatch TV and Plex in those positions instead.
     */
    fun watchProviderDirectory(
        region: String,
        forSeries: Boolean = false,
    ): List<TmdbWatchProvider> {
        val key = apiKey?.takeIf(String::isNotBlank) ?: return emptyList()
        if (region.isBlank()) return emptyList()

        val url =
            baseUrl.newBuilder()
                .addPathSegments(if (forSeries) "watch/providers/tv" else "watch/providers/movie")
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
                TmdbWatchProvider(
                    providerId = id,
                    name = name,
                    // The directory carries marks just as the per-title listings do. Dropping it
                    // here left every shelf heading without one while the film page had it.
                    logoUrl = entry.string("logo_path")?.let { path -> "$imageBaseUrl/w92$path" },
                )
            }.orEmpty()
    }

    /** The TMDb id for a title, or null when the search finds nothing. */
    /**
     * Everything the "where to watch" screen shows about a title: artwork, synopsis, cast, trailer.
     *
     * One request rather than four. TMDb's `append_to_response` folds credits and videos into the
     * details payload, and a screen firing four calls per title would be slow enough to notice on a
     * shelf the user is browsing quickly.
     */
    fun titleDetails(
        tmdbId: Int,
        isSeries: Boolean = false,
    ): TmdbTitleDetails? {
        val key = apiKey?.takeIf(String::isNotBlank) ?: return null

        val url =
            baseUrl.newBuilder()
                .addPathSegments(if (isSeries) "tv/$tmdbId" else "movie/$tmdbId")
                .addQueryParameter("api_key", key)
                .addQueryParameter("language", language)
                // external_ids carries the IMDb id, which is how the critics' scores are looked up
                // afterwards. Appended rather than fetched separately because it costs nothing
                // here: a series does not carry imdb_id at the top level the way a film does.
                .addQueryParameter("append_to_response", "credits,videos,external_ids")
                .build()

        val root = get(url) ?: return null

        val cast =
            root
                .getAsJsonObject("credits")
                ?.getAsJsonArray("cast")
                ?.mapNotNull { element -> element.takeIf { it.isJsonObject }?.asJsonObject }
                ?.take(MAX_CAST)
                ?.mapNotNull { member ->
                    val name = member.string("name") ?: return@mapNotNull null
                    TmdbCastMember(
                        name = name,
                        character = member.string("character"),
                        photoUrl = member.string("profile_path")?.let { path -> "$imageBaseUrl/w185$path" },
                    )
                }.orEmpty()

        val trailerKey =
            root
                .getAsJsonObject("videos")
                ?.getAsJsonArray("results")
                ?.mapNotNull { element -> element.takeIf { it.isJsonObject }?.asJsonObject }
                ?.filter { video -> video.string("site").equals("YouTube", ignoreCase = true) }
                // A full trailer before a teaser; the endpoint returns them mixed together.
                ?.sortedByDescending { video -> if (video.string("type") == "Trailer") 1 else 0 }
                ?.firstOrNull { video -> video.string("type") in TRAILER_TYPES }
                ?.string("key")

        return TmdbTitleDetails(
            title = root.string(if (isSeries) "name" else "title").orEmpty(),
            overview = root.string("overview"),
            // The wide image behind the page, not the poster: a backdrop is 16:9 and made for this.
            backdropUrl = root.string("backdrop_path")?.let { path -> "$imageBaseUrl/w1280$path" },
            posterUrl = root.string("poster_path")?.let { path -> "$imageBaseUrl/w342$path" },
            year = root.string(if (isSeries) "first_air_date" else "release_date")?.take(4)?.toIntOrNull(),
            rating = root.double("vote_average"),
            voteCount = root.int("vote_count"),
            runtimeMinutes =
                if (isSeries) {
                    root.getAsJsonArray("episode_run_time")?.firstOrNull()?.asInt
                } else {
                    root.int("runtime")
                },
            genres =
                root
                    .getAsJsonArray("genres")
                    ?.mapNotNull { element -> element.takeIf { it.isJsonObject }?.asJsonObject?.string("name") }
                    .orEmpty(),
            cast = cast,
            youtubeTrailerId = trailerKey,
            // A film reports it at the top level; a series only inside external_ids. Both are read
            // rather than branching on isSeries, since TMDb has been inconsistent about this before
            // and an absent id simply means no critics' scores are shown.
            imdbId =
                root.string("imdb_id")
                    ?: root.getAsJsonObject("external_ids")?.string("imdb_id"),
        )
    }

    // findMovieId is gone. It existed only to recover an id that watchProviders' caller already
    // held, and searching by title was what made a translated name resolve to the wrong film and a
    // series resolve to nothing.

    /**
     * Runs the request, returning null on any failure.
     *
     * Metadata is an enhancement: a rate limit, an expired key or an offline machine must leave the
     * page showing what the provider gave, never an error the user has to dismiss.
     */
    private fun get(url: HttpUrl): JsonObject? =
        try {
            // A v4 token travels in the header, and the `api_key` parameter every caller added is
            // removed rather than left alongside it: TMDb rejects the request on the bad parameter
            // even when the header would have been accepted.
            //
            // Done here, in the one place every request passes through, so the fifteen call sites
            // that build URLs stay as they are and none of them can forget.
            val effectiveUrl =
                if (usesBearerToken) url.newBuilder().removeAllQueryParameters("api_key").build() else url
            val request =
                Request.Builder()
                    .url(effectiveUrl)
                    .header("Accept", "application/json")
                    .apply {
                        if (usesBearerToken) header("Authorization", "Bearer ${apiKey.orEmpty().trim()}")
                    }
                    .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    // 401 is TMDb's answer for an invalid or unconfirmed key, 403 for a suspended
                    // one. Both mean "fix the key", which is worth telling the user apart from
                    // every other failure — the status alone is recorded, never the URL or body.
                    recordRequestFailure(keyRejected = response.code == 401 || response.code == 403)
                    return@use null
                }
                // Checked rather than cast. `asJsonObject` throws ClassCastException on a JSON
                // array; every caller here is written to handle null instead. TMDb answers some
                // errors with an array, so malformed/unexpected bodies are also recorded as a
                // failed request for the catalogue-level retry state.
                val parsed =
                    runCatching {
                        JsonParser.parseString(response.body.string()).takeIf { it.isJsonObject }?.asJsonObject
                    }.getOrNull()
                if (parsed == null) recordRequestFailure()
                parsed
            }
        } catch (_: Exception) {
            recordRequestFailure()
            null
        }

    /** The API key is a secret; it must never reach a log or a crash report. */
    override fun toString(): String = "TmdbClient(configured=$isConfigured)"

    private companion object {
        const val DEFAULT_BASE_URL = "https://api.themoviedb.org/3/"
        const val DEFAULT_IMAGE_BASE_URL = "https://image.tmdb.org/t/p"

        /** A teaser is still worth showing when no full trailer was published. */
        val TRAILER_TYPES = setOf("Trailer", "Teaser")

        /** Enough cast to fill a strip. TMDb returns the whole billed and unbilled list. */
        const val MAX_CAST = 12

        /** TMDb refuses a discover page beyond this, so asking for one is a wasted request. */
        const val MAX_DISCOVER_PAGE = 500
    }
}

data class TmdbPerson(
    val id: Int,
    val name: String,
    val profileImageUrl: String?,
    val knownFor: String?,
)

data class TmdbCredit(
    /**
     * TMDb's own id, so a credit can be looked up rather than only matched by name.
     *
     * Null when the response omitted it. Such a credit is still listed and can still be matched
     * against the user's playlist by title; only the Assinaturas route needs the id.
     */
    val id: Int?,
    /** Films and series are numbered separately, so the kind is needed to resolve the id. */
    val isSeries: Boolean,
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

/** One credited performer, with the photo the cast strip shows. */
data class TmdbCastMember(
    val name: String,
    val character: String?,
    val photoUrl: String?,
)

/**
 * The page shown above the "where to watch" list.
 *
 * Every field but the title is optional: TMDb's coverage is uneven, and a screen that only rendered
 * when everything was present would show nothing for a great many real films. Each piece is drawn
 * when it exists and omitted when it does not.
 */
/**
 * What an audience thought of a title, as TMDb reports it.
 *
 * The count travels with the average because it is what makes the average mean something; a screen
 * that showed one without the other would be presenting a dozen opinions as though they were a
 * consensus.
 */
data class TmdbAudienceScore(
    val average: Double,
    val voteCount: Int,
    /** TMDb's own id for the matched title, for follow-up lookups such as the watch providers. */
    val tmdbId: Int? = null,
)

data class TmdbTitleDetails(
    val title: String,
    val overview: String?,
    val backdropUrl: String?,
    val posterUrl: String?,
    val year: Int?,
    val rating: Double?,
    /**
     * How many people voted for [rating], which is what makes the score mean anything.
     *
     * An 8.0 from twelve viewers and an 8.0 from ninety thousand are different claims, and the
     * details screen shows both figures rather than presenting the first as though it were the
     * second.
     */
    val voteCount: Int? = null,
    val runtimeMinutes: Int?,
    val genres: List<String> = emptyList(),
    val cast: List<TmdbCastMember> = emptyList(),
    val youtubeTrailerId: String? = null,
    /**
     * The IMDb id, when TMDb knows one.
     *
     * The join key to the critics' scores. IMDb ids are stable and unambiguous, which matching by
     * title and year is not: two films share a name and a year often enough that a title match
     * would eventually put another film's Tomatometer on this page.
     */
    val imdbId: String? = null,
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
 * [logoUrl] carries the service's own mark, at the product owner's explicit instruction — the rule
 * that kept these text-only was reversed deliberately, not overlooked, and GDD 9 section 10 was
 * updated to match rather than left contradicting the app. The marks belong to the services; this
 * shows them to identify where a title can be watched and nothing more.
 *
 * Null when TMDb has no image, which is ordinary: the name still identifies the service, so a
 * missing logo costs nothing.
 */
data class TmdbWatchProvider(
    val providerId: Int,
    val name: String,
    val logoUrl: String? = null,
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

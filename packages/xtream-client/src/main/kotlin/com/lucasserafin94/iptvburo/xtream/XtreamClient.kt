package com.lucasserafin94.iptvburo.xtream

import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.TimeUnit
import com.lucasserafin94.iptvburo.playlist.XmltvParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class XtreamClient(
    private val httpClient: OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(DEFAULT_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(DEFAULT_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(DEFAULT_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            // readTimeout resets on every byte received, so it never fires against a server (hostile
            // or merely congested) that paces its response just under that interval — dynamic testing
            // against a mock server trickling one byte every two seconds confirmed this hangs the
            // calling thread indefinitely with only readTimeout set. callTimeout bounds the whole
            // call's wall-clock duration regardless of pacing, which is the property actually needed
            // here. Kept generous: it must comfortably outlast a large catalog on a slow connection,
            // not just a login handshake.
            .callTimeout(DEFAULT_CALL_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(true)
            .build(),
    private val userAgent: String = DEFAULT_USER_AGENT,
    private val maximumResponseBytes: Int = DEFAULT_MAXIMUM_RESPONSE_BYTES,
    private val maximumBufferedBytes: Int = DEFAULT_MAXIMUM_BUFFERED_BYTES,
    private val maximumTransientRetries: Int = DEFAULT_MAXIMUM_TRANSIENT_RETRIES,
    private val retryDelayMillis: Long = DEFAULT_RETRY_DELAY_MILLIS,
    /**
     * Told how fast the catalogue is arriving, in bytes per second, or null when it cannot say.
     *
     * A long download with no figure on screen cannot be told apart from a stuck one, and that is
     * the question a viewer actually has. Called as the body is read, so a caller that draws this
     * must decide for itself how often to publish it — a screen updated per block is a screen
     * recomposed hundreds of times a second.
     *
     * Off by default: existing callers keep the behaviour they had.
     */
    private val onDownloadRate: (Long?) -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
) {
    init {
        require(userAgent.isNotBlank()) { "userAgent cannot be blank" }
        require(maximumResponseBytes > 0) { "maximumResponseBytes must be positive" }
        require(maximumBufferedBytes > 0) { "maximumBufferedBytes must be positive" }
        // A buffered body is held whole; a streamed one is not. Allowing the buffered ceiling to
        // exceed the streamed one would invert the safety this split exists to provide.
        require(maximumBufferedBytes <= maximumResponseBytes) {
            "maximumBufferedBytes cannot exceed maximumResponseBytes"
        }
        require(maximumTransientRetries in 0..MAXIMUM_TRANSIENT_RETRIES) { "maximumTransientRetries is outside the safe range" }
        require(retryDelayMillis in 0..MAXIMUM_RETRY_DELAY_MILLIS) { "retryDelayMillis is outside the safe range" }
    }

    fun authenticate(credentials: XtreamCredentials): XtreamAccount {
        val root = request(credentials).asObject("account")
        val userInfo = root.objectOrNull("user_info")
            ?: throw invalidResponse("The account response is missing user information.")
        val authenticated = userInfo.flexibleBoolean("auth") ?: false
        if (!authenticated) {
            throw XtreamClientException(
                XtreamFailureReason.AUTHENTICATION,
                "The Xtream server rejected the account.",
            )
        }

        return XtreamAccount(
            authenticated = true,
            status = userInfo.stringOrNull("status"),
            isTrial = userInfo.flexibleBoolean("is_trial"),
            activeConnections = userInfo.flexibleInt("active_cons"),
            maximumConnections = userInfo.flexibleInt("max_connections"),
            allowedOutputFormats =
                userInfo.arrayOrNull("allowed_output_formats")
                    ?.mapNotNull { it.primitiveContentOrNull() }
                    ?.map(String::lowercase)
                    ?.toSet()
                    .orEmpty(),
        )
    }

    fun categories(
        credentials: XtreamCredentials,
        contentType: XtreamContentType,
    ): XtreamCollection<XtreamCategory> {
        val action =
            when (contentType) {
                XtreamContentType.LIVE -> "get_live_categories"
                XtreamContentType.MOVIE -> "get_vod_categories"
                XtreamContentType.SERIES -> "get_series_categories"
            }
        // Streamed, like the catalogue. The buffered path this used to take held the whole
        // response as bytes, then as a String, then as a JsonElement tree — under a 512 MiB
        // ceiling — which on a large provider is a very large allocation on the IO dispatcher
        // during startup, and was reported as a freeze followed by an "incompatible catalogue".
        val items = mutableListOf<XtreamCategory>()
        val summary =
            requestCatalogStream(credentials, action) { input ->
                XtreamCategoryStreamParser(
                    contentType = contentType,
                    maximumItems = MAXIMUM_CATEGORY_ITEMS,
                ).parse(input, items::add)
            }
        return XtreamCollection(items, summary.skippedItemCount)
    }

    fun catalog(
        credentials: XtreamCredentials,
        contentType: XtreamContentType,
    ): XtreamCollection<XtreamCatalogItem> {
        val items = mutableListOf<XtreamCatalogItem>()
        val summary = streamCatalog(credentials, contentType, items::add)
        return XtreamCollection(items, summary.skippedItemCount)
    }

    /**
     * Streams a provider catalog without first allocating its byte array, JSON tree and result
     * list. Production imports should use this API; [catalog] remains a convenience for small
     * callers and compatibility tests.
     */
    fun streamCatalog(
        credentials: XtreamCredentials,
        contentType: XtreamContentType,
        onItem: (XtreamCatalogItem) -> Unit,
    ): XtreamStreamSummary {
        val action =
            when (contentType) {
                XtreamContentType.LIVE -> "get_live_streams"
                XtreamContentType.MOVIE -> "get_vod_streams"
                XtreamContentType.SERIES -> "get_series"
            }
        return requestCatalogStream(credentials, action) { input ->
            XtreamCatalogStreamParser(
                contentType = contentType,
                maximumItems = DEFAULT_MAXIMUM_CATALOG_ITEMS,
                sanitizeArtwork = { value -> value.sanitizeArtworkUrl(credentials) },
            ).parse(input, onItem)
        }
    }

    fun seriesDetails(
        credentials: XtreamCredentials,
        seriesId: String,
    ): XtreamSeriesDetails {
        require(seriesId.isNotBlank()) { "seriesId cannot be blank" }
        val root =
            request(
                credentials = credentials,
                action = "get_series_info",
                additionalQuery = mapOf("series_id" to seriesId),
            ).asObject("get_series_info")
        val info = root.objectOrNull("info") ?: JsonObject(emptyMap())
        val episodes = root["episodes"].parseEpisodes(credentials)

        return XtreamSeriesDetails(
            providerId = seriesId,
            title =
                info.stringOrNull("name")
                    ?: info.stringOrNull("title")
                    ?: "Series",
            plot = info.stringOrNull("plot"),
            artworkUrl = info.stringOrNull("cover")?.sanitizeArtworkUrl(credentials),
            backdropUrls =
                info.arrayOrNull("backdrop_path")
                    ?.mapNotNull { it.primitiveContentOrNull() }
                    ?.mapNotNull { it.sanitizeArtworkUrl(credentials) }
                    .orEmpty(),
            episodes = episodes,
            cast = info.firstString("cast", "actors"),
            director = info.stringOrNull("director"),
            genre = info.stringOrNull("genre"),
            releaseDate = info.firstString("release_date", "releaseDate", "year"),
            rating = info.flexibleDouble("rating"),
            youtubeTrailerId = info.stringOrNull("youtube_trailer")?.sanitizeYouTubeReference(),
        )
    }

    fun movieDetails(
        credentials: XtreamCredentials,
        movieId: String,
    ): XtreamMovieDetails {
        require(movieId.isNotBlank()) { "movieId cannot be blank" }
        val root =
            request(
                credentials = credentials,
                action = "get_vod_info",
                additionalQuery = mapOf("vod_id" to movieId),
            ).asObject("get_vod_info")
        val info = root.objectOrNull("info") ?: JsonObject(emptyMap())
        val movieData = root.objectOrNull("movie_data") ?: JsonObject(emptyMap())

        return XtreamMovieDetails(
            providerId = movieId,
            title =
                info.firstString("name", "title", "o_name")
                    ?: movieData.firstString("name", "title")
                    ?: "Filme",
            plot = info.firstString("plot", "description"),
            cast = info.firstString("cast", "actors"),
            director = info.stringOrNull("director"),
            genre = info.stringOrNull("genre"),
            duration = info.firstString("duration", "episode_run_time"),
            releaseDate = info.firstString("release_date", "releasedate"),
            country = info.stringOrNull("country"),
            rating = info.flexibleDouble("rating"),
            artworkUrl =
                info.firstString("movie_image", "cover_big", "cover")
                    ?.sanitizeArtworkUrl(credentials),
            backdropUrls = info.sanitizedArtworkList("backdrop_path", credentials),
            youtubeTrailerId = info.stringOrNull("youtube_trailer")?.sanitizeYouTubeReference(),
            containerExtension = movieData.stringOrNull("container_extension")?.sanitizeExtension(),
        )
    }

    fun shortEpg(
        credentials: XtreamCredentials,
        streamId: String,
        limit: Int = DEFAULT_SHORT_EPG_LIMIT,
    ): XtreamShortEpg {
        require(streamId.isNotBlank()) { "streamId cannot be blank" }
        require(limit in 1..MAXIMUM_SHORT_EPG_LIMIT) { "limit must be between 1 and $MAXIMUM_SHORT_EPG_LIMIT" }
        val root =
            request(
                credentials = credentials,
                action = "get_short_epg",
                additionalQuery = mapOf("stream_id" to streamId, "limit" to limit.toString()),
            )
        val listings =
            when (root) {
                is JsonObject -> root.arrayOrNull("epg_listings").orEmpty()
                is JsonArray -> root
                else -> emptyList()
            }
        var skipped = 0
        val programs =
            listings.take(MAXIMUM_SHORT_EPG_LIMIT).mapNotNull { element ->
                val value = element as? JsonObject
                val title = value?.firstString("title", "name")?.decodeProviderText()
                if (title.isNullOrBlank()) {
                    skipped += 1
                    null
                } else {
                    val start = value.flexibleLong("start_timestamp") ?: value.flexibleLong("start")
                    val end = value.flexibleLong("stop_timestamp") ?: value.flexibleLong("end_timestamp")
                        ?: value.flexibleLong("stop")
                    XtreamEpgProgram(
                        title = title.take(MAXIMUM_EPG_TEXT_LENGTH),
                        description =
                            value.firstString("description", "desc")
                                ?.decodeProviderText()
                                ?.take(MAXIMUM_EPG_DESCRIPTION_LENGTH),
                        startEpochSeconds = start?.takeIf(::isPlausibleEpochSeconds),
                        endEpochSeconds = end?.takeIf(::isPlausibleEpochSeconds),
                    )
                }
            }
        return XtreamShortEpg(programs, skipped + (listings.size - MAXIMUM_SHORT_EPG_LIMIT).coerceAtLeast(0))
    }

    /**
     * The provider's full multi-day guide, from `xmltv.php` — the same file TiviMate and IPTV
     * Smarters read, and the reason [shortEpg] alone cannot show more than the next few hours:
     * `get_short_epg` is capped by the provider itself regardless of the `limit` requested, while
     * `xmltv.php` carries whatever window the panel was configured to publish, often several days.
     *
     * Returned as programmes by channel id (lower-cased, matching how a caller should look them
     * up) rather than as a single flat list: a guide this size is only ever consulted for one
     * channel at a time, and building the index once here is what makes that lookup free instead
     * of a linear scan repeated per channel opened.
     *
     * Failure is silence — an empty map, never a thrown exception. The guide is a bonus a provider
     * may not even publish, and a panel that is slow or unreachable for it must not be confused
     * with one that is down for anything else the app actually depends on.
     */
    fun xmltvGuide(credentials: XtreamCredentials): Map<String, List<XtreamEpgProgram>> {
        if (credentials.username.isBlank() || credentials.password.isBlank()) return emptyMap()
        return runCatching {
            val endpoint = XtreamEndpointParser.parse(credentials.serverUrl)
            val url =
                endpoint.baseUrl
                    .newBuilder()
                    .addPathSegment("xmltv.php")
                    .addQueryParameter("username", credentials.username)
                    .addQueryParameter("password", credentials.password)
                    .build()
            val request =
                Request.Builder()
                    .url(url)
                    .header("User-Agent", userAgent)
                    // The parser sniffs gzip from the first bytes itself, so the header is asked
                    // for explicitly rather than left to OkHttp's transparent (and then hidden)
                    // decompression — the same reasoning the desktop guide fetch already applies.
                    .header("Accept-Encoding", "gzip")
                    .get()
                    .build()
            // A dedicated client for this one call rather than the shared one: a guide can run to
            // tens of megabytes on a slow host, and the 60-second read timeout every other Xtream
            // call uses would abort a perfectly healthy download partway through. newBuilder()
            // keeps every other setting — the redirect and retry policy this class was built
            // with — and changes only the timeouts, including callTimeout: the shared client's
            // bound exists for ordinary calls and would otherwise cut off a guide the read timeout
            // alone was just widened to allow.
            val guideClient = httpClient.newBuilder()
                .readTimeout(XMLTV_READ_TIMEOUT_MINUTES, TimeUnit.MINUTES)
                .callTimeout(XMLTV_READ_TIMEOUT_MINUTES, TimeUnit.MINUTES)
                .build()
            guideClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching emptyMap()
                val declaredLength = response.body.contentLength()
                if (declaredLength > maximumResponseBytes) return@runCatching emptyMap()
                val bounded = MaximumBytesInputStream(response.body.byteStream(), maximumResponseBytes.toLong())
                val collected = HashMap<String, MutableList<XtreamEpgProgram>>()
                // Errors from the parse itself (the byte ceiling above, a malformed tail, the
                // connection dropping mid-stream) are swallowed here rather than by the outer
                // runCatching, so whatever was already collected survives instead of being thrown
                // away — a guide truncated at the ceiling is still a guide, and discarding it would
                // turn "a very large file" into "no schedule at all" for no benefit.
                runCatching {
                    XmltvParser.parse(bounded) { programme ->
                        collected
                            .getOrPut(programme.channelId.trim().lowercase()) { mutableListOf() }
                            .add(
                                XtreamEpgProgram(
                                    title = programme.title.take(MAXIMUM_EPG_TEXT_LENGTH),
                                    description = programme.description?.take(MAXIMUM_EPG_DESCRIPTION_LENGTH),
                                    startEpochSeconds = programme.startEpochSeconds,
                                    endEpochSeconds = programme.endEpochSeconds,
                                ),
                            )
                    }
                }
                collected
            }
        }.getOrDefault(emptyMap())
    }

    fun buildPlaybackUrl(
        credentials: XtreamCredentials,
        contentType: XtreamContentType,
        providerId: String,
        containerExtension: String? = null,
    ): HttpUrl {
        require(providerId.isNotBlank()) { "providerId cannot be blank" }
        if (contentType == XtreamContentType.SERIES) {
            throw IllegalArgumentException("A series container is not directly playable; use an episode.")
        }
        val endpoint = XtreamEndpointParser.parse(credentials.serverUrl)
        val folder =
            when (contentType) {
                XtreamContentType.LIVE -> "live"
                XtreamContentType.MOVIE -> "movie"
                XtreamContentType.SERIES -> error("Handled above")
            }
        val extension =
            containerExtension?.sanitizeExtension()
                ?: if (contentType == XtreamContentType.LIVE) "ts" else "mp4"
        return endpoint.baseUrl
            .newBuilder()
            .addPathSegment(folder)
            .addPathSegment(credentials.username)
            .addPathSegment(credentials.password)
            .addPathSegment("$providerId.$extension")
            .build()
    }

    /**
     * A past programme from a channel's catch-up recorder.
     *
     * Xtream's timeshift path is `/timeshift/user/pass/<minutes>/<start>/<id>.ts`, where the start
     * is the provider's own local time in `YYYY-MM-DD:HH-MM` — not UTC, and not an epoch. The
     * caller therefore has to hand over an already-formatted local start, because only it knows
     * which zone the provider reports its guide in.
     *
     * The duration is in minutes and is the length of the programme rather than the size of the
     * archive: asking for more than was recorded returns whatever exists, but asking for a whole
     * day would have the server open a file measured in gigabytes.
     */
    fun buildTimeshiftUrl(
        credentials: XtreamCredentials,
        providerId: String,
        startLocal: String,
        durationMinutes: Int,
    ): HttpUrl {
        require(providerId.isNotBlank()) { "providerId cannot be blank" }
        require(durationMinutes in 1..MAX_TIMESHIFT_MINUTES) { "durationMinutes is outside the safe range" }
        // The shape is fixed, so anything else is either a caller's bug or an attempt to inject a
        // path segment. Validated rather than escaped, because there is no legitimate variation.
        require(TIMESHIFT_START.matches(startLocal)) { "startLocal must be YYYY-MM-DD:HH-MM" }
        val endpoint = XtreamEndpointParser.parse(credentials.serverUrl)
        return endpoint.baseUrl
            .newBuilder()
            .addPathSegment("timeshift")
            .addPathSegment(credentials.username)
            .addPathSegment(credentials.password)
            .addPathSegment(durationMinutes.toString())
            .addPathSegment(startLocal)
            .addPathSegment("$providerId.ts")
            .build()
    }

    fun buildEpisodePlaybackUrl(
        credentials: XtreamCredentials,
        episode: XtreamEpisode,
    ): HttpUrl {
        val endpoint = XtreamEndpointParser.parse(credentials.serverUrl)
        val extension = episode.containerExtension?.sanitizeExtension() ?: "mp4"
        return endpoint.baseUrl
            .newBuilder()
            .addPathSegment("series")
            .addPathSegment(credentials.username)
            .addPathSegment(credentials.password)
            .addPathSegment("${episode.providerId}.$extension")
            .build()
    }

    /**
     * Reads from the provider for up to [budgetMillis], reporting bytes moved and time taken.
     *
     * Measures the connection the viewer's television actually depends on, which is the question
     * behind "it keeps freezing": a general speed test says the Internet is fine while the provider
     * is the slow part, and the viewer is left blaming the app.
     *
     * Uses the catalogue endpoint rather than a stream. A stream would consume one of the account's
     * simultaneous connections — on a two-connection plan, running diagnostics would stop the
     * television in the other room.
     *
     * Stops at the budget and reports what it read rather than waiting for a fixed size, so a slow
     * line finishes the test instead of hanging it. Returns null when nothing could be read.
     */
    fun measureTransfer(
        credentials: XtreamCredentials,
        budgetMillis: Long,
    ): Pair<Long, Long>? {
        val endpoint = XtreamEndpointParser.parse(credentials.serverUrl)
        val url =
            endpoint.baseUrl
                .newBuilder()
                .addPathSegment("player_api.php")
                .addQueryParameter("username", credentials.username)
                .addQueryParameter("password", credentials.password)
                .addQueryParameter("action", "get_vod_streams")
                .build()
        val request =
            Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .get()
                .build()

        // The shared client bounds a whole call in minutes, which is right for a catalogue fetch
        // and wrong here: a provider slow to answer would leave the screen waiting long after the
        // budget, showing a spinner that looks like the app hung.
        val probe =
            httpClient
                .newBuilder()
                .callTimeout(budgetMillis * 2, TimeUnit.MILLISECONDS)
                .readTimeout(budgetMillis, TimeUnit.MILLISECONDS)
                .build()

        return runCatching {
            val started = System.nanoTime()
            probe.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                var read = 0L
                val buffer = ByteArray(BUFFER_BYTES)
                val stream = response.body.byteStream()
                // Read and discard. Nothing is parsed and nothing is kept: this is a stopwatch on
                // the connection, and holding a catalogue here would cost memory for no reading.
                while (true) {
                    val elapsed = (System.nanoTime() - started) / 1_000_000
                    if (elapsed >= budgetMillis) break
                    val count = stream.read(buffer)
                    if (count <= 0) break
                    read += count
                }
                val took = (System.nanoTime() - started) / 1_000_000
                if (read <= 0L) null else read to took
            }
        }.getOrNull()
    }

    /**
     * Round-trip times to the provider, in milliseconds, one per successful attempt.
     *
     * The returned list is shorter than [attempts] when requests failed, and that gap is the
     * measurement: a connection losing one request in ten is exactly what a viewer needs told, and
     * throwing would replace that with "the test failed".
     *
     * A HEAD request, so the reading is the round trip rather than the size of an answer.
     */
    fun measureLatency(
        credentials: XtreamCredentials,
        attempts: Int,
    ): List<Int> {
        val endpoint = XtreamEndpointParser.parse(credentials.serverUrl)
        val url =
            endpoint.baseUrl
                .newBuilder()
                .addPathSegment("player_api.php")
                .addQueryParameter("username", credentials.username)
                .addQueryParameter("password", credentials.password)
                .build()
        val probe = httpClient.newBuilder().callTimeout(3, TimeUnit.SECONDS).build()

        // HEAD first, GET as the fallback. Plenty of panels answer 405 or 501 to a HEAD they have
        // never been asked for, and treating that as loss would report a perfectly healthy
        // connection as dropping every request.
        var useHead = true
        return (1..attempts).mapNotNull {
            fun attempt(head: Boolean): Int? =
                runCatching {
                    val builder =
                        Request.Builder()
                            .url(url)
                            .header("User-Agent", userAgent)
                    val request = if (head) builder.head().build() else builder.get().build()
                    val started = System.nanoTime()
                    probe.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@runCatching null
                        // Drained so the connection can be reused; an unread body would make the
                        // next round trip pay for a new one and inflate the reading.
                        response.body.bytes()
                        ((System.nanoTime() - started) / 1_000_000).toInt()
                    }
                }.getOrNull()

            attempt(useHead) ?: if (useHead) {
                // Remembered, so the remaining attempts do not each pay for a refused HEAD.
                useHead = false
                attempt(false)
            } else {
                null
            }
        }
    }

    private fun request(
        credentials: XtreamCredentials,
        action: String? = null,
        additionalQuery: Map<String, String> = emptyMap(),
    ): JsonElement {
        val endpoint = XtreamEndpointParser.parse(credentials.serverUrl)
        if (credentials.username.isBlank() || credentials.password.isBlank()) {
            throw XtreamClientException(
                XtreamFailureReason.AUTHENTICATION,
                "The Xtream username and password are required.",
            )
        }
        val urlBuilder =
            endpoint.baseUrl
                .newBuilder()
                .addPathSegment("player_api.php")
                .addQueryParameter("username", credentials.username)
                .addQueryParameter("password", credentials.password)
        action?.let { urlBuilder.addQueryParameter("action", it) }
        additionalQuery.forEach(urlBuilder::addQueryParameter)

        val request =
            Request.Builder()
                .url(urlBuilder.build())
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .get()
                .build()
        val response = executeWithTransientRetry(request)
        response.use {
            if (!response.isSuccessful) {
                throw XtreamClientException(
                    XtreamFailureReason.HTTP,
                    "The Xtream server returned HTTP ${response.code}.",
                )
            }
            // The buffered ceiling, not the streamed one: everything below holds the whole body in
            // memory at once, so this is where an oversized response becomes an OutOfMemoryError.
            val declaredLength = response.body.contentLength()
            if (declaredLength > maximumBufferedBytes) {
                throw XtreamClientException(
                    XtreamFailureReason.RESPONSE_TOO_LARGE,
                    "The Xtream response exceeded the configured safety limit.",
                )
            }
            val bytes =
                measuring(response.body.byteStream()).use { input ->
                    input.readAtMost(maximumBufferedBytes + 1)
                }
            if (bytes.size > maximumBufferedBytes) {
                throw XtreamClientException(
                    XtreamFailureReason.RESPONSE_TOO_LARGE,
                    "The Xtream response exceeded the configured safety limit.",
                )
            }
            val text =
                try {
                    STRICT_UTF8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(java.nio.ByteBuffer.wrap(bytes))
                        .toString()
                } catch (error: Exception) {
                    throw invalidResponse("The Xtream response is not valid UTF-8.")
                }
            if (text.trimStart().startsWith("<", ignoreCase = false)) {
                throw invalidResponse("The Xtream server returned HTML instead of JSON.")
            }
            return try {
                JSON.parseToJsonElement(text)
            } catch (error: Exception) {
                throw invalidResponse("The Xtream response is not valid JSON.")
            }
        }
    }

    private fun <T> requestCatalogStream(
        credentials: XtreamCredentials,
        action: String,
        consume: (InputStream) -> T,
    ): T {
        val endpoint = XtreamEndpointParser.parse(credentials.serverUrl)
        if (credentials.username.isBlank() || credentials.password.isBlank()) {
            throw XtreamClientException(
                XtreamFailureReason.AUTHENTICATION,
                "The Xtream username and password are required.",
            )
        }
        val url =
            endpoint.baseUrl
                .newBuilder()
                .addPathSegment("player_api.php")
                .addQueryParameter("username", credentials.username)
                .addQueryParameter("password", credentials.password)
                .addQueryParameter("action", action)
                .build()
        val request =
            Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .get()
                .build()
        val response = executeWithTransientRetry(request)

        response.use {
            if (!response.isSuccessful) {
                throw XtreamClientException(
                    XtreamFailureReason.HTTP,
                    "The Xtream server returned HTTP ${response.code}.",
                )
            }
            val declaredLength = response.body.contentLength()
            if (declaredLength > maximumResponseBytes) {
                throw XtreamClientException(
                    XtreamFailureReason.RESPONSE_TOO_LARGE,
                    "The Xtream response exceeded the configured safety limit.",
                )
            }
            val bounded =
                MaximumBytesInputStream(
                    measuring(response.body.byteStream()),
                    maximumResponseBytes.toLong(),
                )
            return try {
                consume(bounded)
            } catch (error: CatalogByteLimitExceededException) {
                throw XtreamClientException(
                    XtreamFailureReason.RESPONSE_TOO_LARGE,
                    "The Xtream response exceeded the configured safety limit.",
                    error,
                )
            } catch (error: XtreamClientException) {
                throw error
            } catch (error: IOException) {
                throw XtreamClientException(
                    XtreamFailureReason.INVALID_RESPONSE,
                    "The Xtream catalog response could not be decoded safely.",
                    error,
                )
            } catch (error: IllegalStateException) {
                throw XtreamClientException(
                    XtreamFailureReason.INVALID_RESPONSE,
                    "The Xtream catalog response is not valid JSON.",
                    error,
                )
            }
        }
    }

    private fun JsonElement?.parseEpisodes(
        credentials: XtreamCredentials,
    ): List<XtreamEpisode> {
        val episodeObjects =
            when (this) {
                is JsonObject ->
                    entries.flatMap { (seasonKey, value) ->
                        (value as? JsonArray)
                            ?.mapNotNull { episode ->
                                (episode as? JsonObject)?.let { it to seasonKey.toIntOrNull() }
                            }
                            .orEmpty()
                    }
                is JsonArray -> mapNotNull { episode ->
                    (episode as? JsonObject)?.let { it to null }
                }
                else -> emptyList()
            }
        return episodeObjects.mapNotNull { (episode, seasonFromMap) ->
            val id = episode.stringOrNull("id")
            val title = episode.stringOrNull("title")
            val season = episode.flexibleInt("season") ?: seasonFromMap
            if (id.isNullOrBlank() || title.isNullOrBlank() || season == null) {
                null
            } else {
                XtreamEpisode(
                    providerId = id,
                    title = title,
                    seasonNumber = season,
                    episodeNumber = episode.flexibleInt("episode_num"),
                    containerExtension =
                        episode.stringOrNull("container_extension")?.sanitizeExtension(),
                    artworkUrl =
                        episode.objectOrNull("info")
                            ?.stringOrNull("movie_image")
                            ?.sanitizeArtworkUrl(credentials),
                )
            }
        }
    }

    private fun JsonElement.asObject(endpoint: String): JsonObject =
        this as? JsonObject
            ?: throw invalidResponse("The $endpoint response must be a JSON object.")

    private fun JsonElement.asArray(endpoint: String): List<JsonElement> =
        when (this) {
            is JsonArray -> this
            JsonNull -> emptyList()
            is JsonObject ->
                if (keys.all { it.toIntOrNull() != null }) {
                    values.toList()
                } else {
                    throw invalidResponse("The $endpoint response must be a JSON array.")
                }
            is JsonPrimitive ->
                if (booleanOrNull == false || contentOrNull == "0") {
                    emptyList()
                } else {
                    throw invalidResponse("The $endpoint response must be a JSON array.")
                }
        }

    private fun JsonObject.stringOrNull(key: String): String? =
        (get(key) as? JsonPrimitive)
            ?.contentOrNull
            ?.trim()
            ?.takeIf(String::isNotEmpty)

    private fun JsonObject.firstString(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key -> stringOrNull(key) }

    private fun JsonObject.sanitizedArtworkList(
        key: String,
        credentials: XtreamCredentials,
    ): List<String> =
        when (val value = get(key)) {
            is JsonArray -> value.mapNotNull { it.primitiveContentOrNull()?.sanitizeArtworkUrl(credentials) }
            is JsonPrimitive -> value.contentOrNull?.sanitizeArtworkUrl(credentials)?.let(::listOf).orEmpty()
            else -> emptyList()
        }

    private fun JsonObject.objectOrNull(key: String): JsonObject? = get(key) as? JsonObject

    private fun JsonObject.arrayOrNull(key: String): JsonArray? = get(key) as? JsonArray

    private fun JsonObject.flexibleInt(key: String): Int? =
        (get(key) as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.toIntOrNull() }

    private fun JsonObject.flexibleLong(key: String): Long? =
        (get(key) as? JsonPrimitive)?.let { it.longOrNull ?: it.contentOrNull?.toLongOrNull() }

    private fun JsonObject.flexibleDouble(key: String): Double? =
        (get(key) as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() }

    private fun JsonObject.flexibleBoolean(key: String): Boolean? =
        (get(key) as? JsonPrimitive)?.let { primitive ->
            primitive.booleanOrNull
                ?: when (primitive.contentOrNull?.trim()?.lowercase()) {
                    "1", "true", "yes" -> true
                    "0", "false", "no" -> false
                    else -> null
                }
        }

    private fun JsonObject.categoryIds(): List<String> =
        buildList {
            stringOrNull("category_id")?.let(::add)
            arrayOrNull("category_ids")
                ?.mapNotNull { it.primitiveContentOrNull() }
                ?.forEach(::add)
            stringOrNull("category_ids")
                ?.parseProviderIdList()
                ?.forEach(::add)
        }.distinct()

    private fun String.parseProviderIdList(): List<String> =
        trim()
            .removePrefix("[")
            .removeSuffix("]")
            .split(',')
            .map { value -> value.trim().trim('"', '\'') }
            .filter { value -> value.isNotEmpty() && value.length <= MAXIMUM_PROVIDER_ID_LENGTH }

    private fun JsonElement.primitiveContentOrNull(): String? =
        (this as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)

    /**
     * Wraps a body so its bytes are counted, and reports the rate while they arrive.
     *
     * The rate is recomputed on every block but handed out at most once per
     * [RATE_PUBLISH_INTERVAL_MILLIS], because the caller draws it: publishing per block would put
     * a state write, and a recomposition, behind every thirty-two kilobytes.
     */
    private fun measuring(source: InputStream): InputStream {
        val rate = DownloadRate()
        var lastPublishedAt = 0L
        return CountingInputStream(source, rate) {
            val now = clock()
            if (now - lastPublishedAt >= RATE_PUBLISH_INTERVAL_MILLIS) {
                lastPublishedAt = now
                onDownloadRate(rate.bytesPerSecond(now))
            }
            now
        }
    }

    private fun InputStream.readAtMost(maximumBytes: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(maximumBytes, READ_BUFFER_SIZE))
        val buffer = ByteArray(READ_BUFFER_SIZE)
        var remaining = maximumBytes
        while (remaining > 0) {
            val read = read(buffer, 0, minOf(buffer.size, remaining))
            if (read < 0) break
            if (read == 0) {
                val singleByte = read()
                if (singleByte < 0) break
                output.write(singleByte)
                remaining -= 1
            } else {
                output.write(buffer, 0, read)
                remaining -= read
            }
        }
        buffer.fill(0)
        return output.toByteArray()
    }

    private fun String.sanitizeExtension(): String? =
        trim()
            .removePrefix(".")
            .lowercase()
            .takeIf { it.matches(EXTENSION_PATTERN) }

    private fun String.sanitizeArtworkUrl(credentials: XtreamCredentials): String? {
        if (length > MAXIMUM_ARTWORK_URL_LENGTH) return null
        val parsed = trim().toHttpUrlOrNull() ?: return null
        if (parsed.scheme !in ALLOWED_ARTWORK_SCHEMES) return null
        if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) return null
        if (parsed.query != null) return null
        if (
            parsed.pathSegments.any { segment ->
                segment == credentials.username || segment == credentials.password
            }
        ) {
            return null
        }
        return parsed.newBuilder().fragment(null).build().toString()
    }

    private fun String.sanitizeYouTubeReference(): String? {
        val candidate = trim().takeIf { it.length in 6..512 } ?: return null
        if (candidate.matches(YOUTUBE_ID_PATTERN)) return candidate
        val parsed = candidate.toHttpUrlOrNull() ?: return null
        val host = parsed.host.lowercase()
        val id =
            when {
                host == "youtu.be" -> parsed.pathSegments.firstOrNull()
                host == "youtube.com" || host.endsWith(".youtube.com") ->
                    parsed.queryParameter("v")
                        ?: parsed.pathSegments
                            .zipWithNext()
                            .firstOrNull { (segment, _) -> segment == "embed" || segment == "shorts" }
                            ?.second
                else -> null
            }
        return id?.takeIf { it.matches(YOUTUBE_ID_PATTERN) }
    }

    /**
     * Retries only failures that are normally transient, with a strict operation budget.
     * Authentication, malformed responses and ordinary 4xx responses are never retried.
     */
    private fun executeWithTransientRetry(request: Request): Response {
        var attempt = 0
        var lastNetworkError: IOException? = null
        while (attempt <= maximumTransientRetries) {
            val response =
                try {
                    httpClient.newCall(request).execute()
                } catch (error: IOException) {
                    lastNetworkError = error
                    null
                }
            if (response != null && (!response.code.isTransientHttpCode() || attempt == maximumTransientRetries)) {
                return response
            }
            response?.close()
            if (attempt == maximumTransientRetries) break
            val delay = (retryDelayMillis * (attempt + 1L)).coerceAtMost(MAXIMUM_RETRY_DELAY_MILLIS)
            if (delay > 0) {
                try {
                    Thread.sleep(delay)
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw XtreamClientException(
                        XtreamFailureReason.NETWORK,
                        "The Xtream request was interrupted.",
                        interrupted,
                    )
                }
            }
            attempt += 1
        }
        throw XtreamClientException(
            XtreamFailureReason.NETWORK,
            "The Xtream server could not be reached.",
            lastNetworkError,
        )
    }

    private fun Int.isTransientHttpCode(): Boolean = this == 408 || this == 429 || this in 500..504

    private fun String.decodeProviderText(): String {
        val clean = trim()
        if (clean.isEmpty() || clean.length > MAXIMUM_ENCODED_EPG_TEXT_LENGTH) return clean
        if (!clean.matches(BASE64_TEXT_PATTERN) || clean.length % 4 != 0) return clean
        return runCatching {
            val bytes = Base64.getDecoder().decode(clean)
            val decoded = STRICT_UTF8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(bytes))
                .toString()
                .trim()
            decoded.takeIf { value -> value.isNotBlank() && value.none(Char::isISOControl) } ?: clean
        }.getOrDefault(clean)
    }

    private fun isPlausibleEpochSeconds(value: Long): Boolean = value in MINIMUM_EPG_EPOCH_SECONDS..MAXIMUM_EPG_EPOCH_SECONDS

    private fun invalidResponse(message: String): XtreamClientException =
        XtreamClientException(XtreamFailureReason.INVALID_RESPONSE, message)

    private companion object {
        /**
         * Read size for the throughput probe.
         *
         * Big enough that the loop is not the bottleneck on a fast line, small enough that the
         * budget check between reads still stops the test roughly on time.
         */
        const val BUFFER_BYTES = 64 * 1024

        /**
         * The longest single catch-up request: twelve hours.
         *
         * Long enough for any programme, including a match that runs over, and short enough that a
         * mistaken value cannot ask the provider to open a file measured in days.
         */
        const val MAX_TIMESHIFT_MINUTES = 720

        /** `YYYY-MM-DD:HH-MM`, which is the only shape the timeshift path accepts. */
        val TIMESHIFT_START = Regex("""\d{4}-\d{2}-\d{2}:\d{2}-\d{2}""")

        const val DEFAULT_USER_AGENT = "IPTV BURO/0.2"

        /**
         * Ceiling for a *streamed* response, which is never held whole.
         *
         * The parser reads it item by item and the byte counter only stops a body that would not
         * end, so a large number here costs nothing in memory.
         */
        const val DEFAULT_MAXIMUM_RESPONSE_BYTES = 512 * 1024 * 1024

        /**
         * Ceiling for a *buffered* response, which is held whole — three times over.
         *
         * `request` reads the body into a ByteArray, decodes that into a String, and parses that
         * into a JsonElement tree, all alive at once. Against a 768 MB heap the streamed ceiling
         * was a loaded gun: one oversized body could take the whole heap and kill the app with an
         * OutOfMemoryError, which is exactly the freeze-then-fail a user reported while their
         * catalogue was loading.
         *
         * The endpoints that still buffer are the small ones — the account handshake, series and
         * film details, the short EPG. None is anywhere near this; a provider that sends more than
         * 32 MB for one of them is malfunctioning, and refusing it is the correct answer.
         */
        const val DEFAULT_MAXIMUM_BUFFERED_BYTES = 32 * 1024 * 1024
        const val DEFAULT_MAXIMUM_CATALOG_ITEMS = 1_000_000

        /**
         * Bound on the category list, which is a different order of magnitude from the catalogue.
         *
         * A provider with tens of thousands of films still groups them into hundreds of categories,
         * so this is generous while keeping the list to something the sidebar can hold.
         */
        const val MAXIMUM_CATEGORY_ITEMS = 50_000
        const val DEFAULT_MAXIMUM_TRANSIENT_RETRIES = 1
        const val MAXIMUM_TRANSIENT_RETRIES = 2
        const val DEFAULT_RETRY_DELAY_MILLIS = 250L
        const val MAXIMUM_RETRY_DELAY_MILLIS = 2_000L
        const val DEFAULT_SHORT_EPG_LIMIT = 8
        const val MAXIMUM_SHORT_EPG_LIMIT = 50
        const val MAXIMUM_EPG_TEXT_LENGTH = 240
        const val MAXIMUM_EPG_DESCRIPTION_LENGTH = 2_000
        const val MAXIMUM_ENCODED_EPG_TEXT_LENGTH = 16_384
        const val MINIMUM_EPG_EPOCH_SECONDS = 946_684_800L
        const val MAXIMUM_EPG_EPOCH_SECONDS = 4_102_444_800L
        const val DEFAULT_CONNECT_TIMEOUT_SECONDS = 15L
        const val DEFAULT_READ_TIMEOUT_SECONDS = 60L
        const val DEFAULT_WRITE_TIMEOUT_SECONDS = 30L

        /**
         * Bounds the whole call, unlike [DEFAULT_READ_TIMEOUT_SECONDS] which resets on every byte
         * received and so never fires against a server pacing its response just under that
         * interval. Wide enough to comfortably outlast a large catalogue arriving slowly, not tuned
         * to ordinary calls — those fail fast on their own well before this ever matters.
         */
        const val DEFAULT_CALL_TIMEOUT_MINUTES = 5L

        /** How long xmltvGuide() alone waits per read, matching the desktop guide fetch's own. */
        const val XMLTV_READ_TIMEOUT_MINUTES = 3L
        const val READ_BUFFER_SIZE = 32 * 1024

        /**
         * How often the rate reaches the caller. Four times a second reads as live without
         * putting a UI state write behind every block read.
         */
        private const val RATE_PUBLISH_INTERVAL_MILLIS = 250L
        const val MAXIMUM_ARTWORK_URL_LENGTH = 8 * 1024
        const val MAXIMUM_PROVIDER_ID_LENGTH = 256
        val JSON = Json { ignoreUnknownKeys = true }
        val STRICT_UTF8 = StandardCharsets.UTF_8
        val EXTENSION_PATTERN = Regex("[a-z0-9]{1,10}")
        val ALLOWED_ARTWORK_SCHEMES = setOf("http", "https")
        val YOUTUBE_ID_PATTERN = Regex("[A-Za-z0-9_-]{6,32}")
        val BASE64_TEXT_PATTERN = Regex("[A-Za-z0-9+/]+={0,2}")
    }
}

private class MaximumBytesInputStream(
    delegate: InputStream,
    private val maximumBytes: Long,
) : FilterInputStream(delegate) {
    private var bytesRead = 0L

    override fun read(): Int {
        if (bytesRead >= maximumBytes) return probeOverflow()
        val value = super.read()
        if (value >= 0) bytesRead += 1
        return value
    }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        if (length == 0) return 0
        if (bytesRead >= maximumBytes) return probeOverflow()
        val allowed = minOf(length.toLong(), maximumBytes - bytesRead).toInt()
        val count = super.read(buffer, offset, allowed)
        if (count > 0) bytesRead += count
        return count
    }

    private fun probeOverflow(): Int {
        val value = super.read()
        if (value == -1) return -1
        throw CatalogByteLimitExceededException()
    }
}

private class CatalogByteLimitExceededException : IOException()

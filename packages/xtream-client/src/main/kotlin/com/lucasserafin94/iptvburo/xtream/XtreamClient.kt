package com.lucasserafin94.iptvburo.xtream

import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.TimeUnit
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
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(true)
            .build(),
    private val userAgent: String = DEFAULT_USER_AGENT,
    private val maximumResponseBytes: Int = DEFAULT_MAXIMUM_RESPONSE_BYTES,
    private val maximumTransientRetries: Int = DEFAULT_MAXIMUM_TRANSIENT_RETRIES,
    private val retryDelayMillis: Long = DEFAULT_RETRY_DELAY_MILLIS,
) {
    init {
        require(userAgent.isNotBlank()) { "userAgent cannot be blank" }
        require(maximumResponseBytes > 0) { "maximumResponseBytes must be positive" }
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
        val array = request(credentials, action).asArray(action)
        var skipped = 0
        val items =
            array.mapNotNull { element ->
                val objectValue = element as? JsonObject
                val id = objectValue?.stringOrNull("category_id")
                val name = objectValue?.stringOrNull("category_name")
                if (id.isNullOrBlank() || name.isNullOrBlank()) {
                    skipped += 1
                    null
                } else {
                    XtreamCategory(id, name, contentType)
                }
            }
        return XtreamCollection(items, skipped)
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
            val declaredLength = response.body.contentLength()
            if (declaredLength > maximumResponseBytes) {
                throw XtreamClientException(
                    XtreamFailureReason.RESPONSE_TOO_LARGE,
                    "The Xtream response exceeded the configured safety limit.",
                )
            }
            val bytes =
                response.body.byteStream().use { input ->
                    input.readAtMost(maximumResponseBytes + 1)
                }
            if (bytes.size > maximumResponseBytes) {
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
            val bounded = MaximumBytesInputStream(response.body.byteStream(), maximumResponseBytes.toLong())
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
        const val DEFAULT_USER_AGENT = "IPTV BURO/0.2"
        const val DEFAULT_MAXIMUM_RESPONSE_BYTES = 512 * 1024 * 1024
        const val DEFAULT_MAXIMUM_CATALOG_ITEMS = 1_000_000
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
        const val READ_BUFFER_SIZE = 32 * 1024
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

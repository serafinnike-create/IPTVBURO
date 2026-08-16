package com.lucasserafin94.iptvburo.metadata

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.time.Duration
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * What the critics said, from OMDb.
 *
 * TMDb publishes one score: its own users' average. The phone apps people compare this one against
 * show a row of three — the Tomatometer, IMDb and Metacritic — and those come from three companies
 * that each compute them differently. Rotten Tomatoes has no free API; OMDb republishes the figure
 * alongside the other two, keyed by IMDb id, which is why it is the source used here.
 *
 * A separate client rather than another method on [TmdbClient]: different host, different key,
 * different failure modes, and a rate limit that must not be able to take the TMDb calls down with
 * it. The app works without this entirely — no key means no critics' row, and every other part of
 * the details screen is unaffected.
 *
 * ## What is sent
 *
 * One IMDb id, which identifies a public film. Nothing about the user, their playlist or their
 * source is included in any request, and the key never appears in a log line or in [toString].
 */
class CriticScoresClient(
    private val apiKey: String?,
    private val client: OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(8))
            .readTimeout(Duration.ofSeconds(10))
            .build(),
    private val baseUrl: HttpUrl = DEFAULT_BASE_URL.toHttpUrl(),
) {
    /** Whether lookups can run at all. */
    val isConfigured: Boolean
        get() = !apiKey.isNullOrBlank()

    /**
     * The critics' scores for [imdbId], or null when there are none to show.
     *
     * Null rather than an empty object for the same reason the rest of this package returns null on
     * failure: a ratings row that cannot be filled should be absent, not a panel of dashes.
     */
    fun scoresFor(imdbId: String): CriticScores? {
        val key = apiKey?.takeIf(String::isNotBlank) ?: return null
        val id = imdbId.trim()
        // OMDb answers a malformed id with a 200 and an error body, so the shape is checked here
        // rather than spending a request to be told no.
        if (!IMDB_ID.matches(id)) return null

        val url =
            baseUrl.newBuilder()
                .addQueryParameter("i", id)
                .addQueryParameter("apikey", key)
                .build()

        val root = get(url) ?: return null
        // OMDb reports failure in the body with HTTP 200: {"Response":"False","Error":"..."}.
        if (!root.string("Response").equals("True", ignoreCase = true)) return null

        val ratings =
            root.getAsJsonArray("Ratings")
                ?.mapNotNull { element -> element.takeIf { it.isJsonObject }?.asJsonObject }
                .orEmpty()

        fun percentFrom(source: String): Int? =
            ratings
                .firstOrNull { entry -> entry.string("Source") == source }
                ?.string("Value")
                ?.let(::parsePercent)

        val scores =
            CriticScores(
                // "83%"
                tomatometer = percentFrom(SOURCE_ROTTEN_TOMATOES),
                // "73/100"
                metascore = percentFrom(SOURCE_METACRITIC),
                // "8.7/10", kept out of ten because that is how IMDb is read everywhere.
                imdbRating =
                    ratings
                        .firstOrNull { entry -> entry.string("Source") == SOURCE_IMDB }
                        ?.string("Value")
                        ?.substringBefore('/')
                        ?.replace(',', '.')
                        ?.toDoubleOrNull(),
            )

        return scores.takeIf { it.hasAny }
    }

    /**
     * "83%" or "73/100" as a whole percentage.
     *
     * Both forms appear in the same array, from different sources, and both mean the same thing to
     * a reader. Anything else is discarded rather than guessed at.
     */
    private fun parsePercent(value: String): Int? {
        val trimmed = value.trim()
        return when {
            trimmed.endsWith("%") -> trimmed.dropLast(1).trim().toIntOrNull()
            trimmed.contains("/100") -> trimmed.substringBefore('/').trim().toIntOrNull()
            else -> null
        }?.takeIf { percent -> percent in 0..100 }
    }

    /**
     * Runs the request, returning null on any failure.
     *
     * Identical in spirit to the TMDb client's: this is an enhancement to a page that is already
     * showing the user their film, so a rate limit or an offline machine must cost the row and
     * nothing else. No response body or URL is logged — the URL carries the key.
     */
    private fun get(url: HttpUrl): JsonObject? =
        try {
            val request =
                Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    null
                } else {
                    response.body?.string()
                        ?.let { body -> JsonParser.parseString(body) }
                        ?.takeIf { parsed -> parsed.isJsonObject }
                        ?.asJsonObject
                }
            }
        } catch (_: Exception) {
            null
        }

    /** Never let the key reach a log line or a crash report. */
    override fun toString(): String = "CriticScoresClient(configured=$isConfigured)"

    private fun JsonObject.string(name: String): String? =
        get(name)?.takeIf { element -> element.isJsonPrimitive }?.asString?.takeIf(String::isNotBlank)

    companion object {
        private const val DEFAULT_BASE_URL = "https://www.omdbapi.com/"

        /** `tt` followed by at least seven digits. */
        private val IMDB_ID = Regex("""^tt\d{7,}$""")

        private const val SOURCE_ROTTEN_TOMATOES = "Rotten Tomatoes"
        private const val SOURCE_METACRITIC = "Metacritic"
        private const val SOURCE_IMDB = "Internet Movie Database"
    }
}

/**
 * The critics' verdicts on one title.
 *
 * Each is nullable on its own: OMDb frequently has an IMDb rating for a film with no Tomatometer,
 * and showing a gap where one is missing is honest in a way that substituting another company's
 * number would not be.
 */
data class CriticScores(
    /** Rotten Tomatoes' Tomatometer, as a whole percentage. */
    val tomatometer: Int? = null,
    /** Metacritic's Metascore, normalised to a percentage from its own out-of-100 scale. */
    val metascore: Int? = null,
    /** IMDb's average, out of ten, which is how IMDb itself presents it. */
    val imdbRating: Double? = null,
) {
    /** Whether there is anything at all worth drawing a row for. */
    val hasAny: Boolean
        get() = tomatometer != null || metascore != null || imdbRating != null
}

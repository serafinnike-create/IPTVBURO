package com.lucasserafin94.iptvburo.metadata

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.time.Duration
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Cover art for a catalogue TMDb will not answer for.
 *
 * TMDb returns nothing for these titles — this app asks it not to, and TMDb's own guidance is that
 * applications should not fetch them — so an adult category arrives with no artwork at all and
 * every row draws its title on a tinted card. This is the only source that fills it.
 *
 * ## The key is the viewer's own, always
 *
 * No key ships with the app, and none can. An installer is a file anybody unpacks, so a key inside
 * one is a published key: the account suspended when somebody abuses it would be the account that
 * issued it, and every viewer would lose their artwork at once. The same reasoning already governs
 * the TMDb key here — the build has a task that refuses to package one.
 *
 * Absent by default. Without a key this answers nothing and the rows keep the title fallback they
 * have now, which is a working screen rather than a broken one.
 *
 * ## What is sent
 *
 * A title, and nothing else. No identifier for the viewer, their playlist or their provider goes
 * into any request, and the key never reaches a log line or [toString].
 */
class AdultArtworkClient(
    private val apiKey: String?,
    private val client: OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(8))
            .readTimeout(Duration.ofSeconds(10))
            .build(),
    private val baseUrl: HttpUrl = DEFAULT_BASE_URL.toHttpUrl(),
) {
    /** Whether lookups can run at all. Without a key, nothing here does anything. */
    val isConfigured: Boolean
        get() = !apiKey.isNullOrBlank()

    /**
     * A poster for [title], or null when there is none to show.
     *
     * Null rather than a placeholder URL: the caller already draws a readable card for a title with
     * no artwork, and a broken image is worse than the card it would replace.
     */
    fun posterFor(title: String): String? {
        val key = apiKey?.takeIf(String::isNotBlank) ?: return null
        val cleaned = title.trim().take(MAX_TITLE)
        if (cleaned.length < MIN_TITLE) return null

        val url =
            baseUrl.newBuilder()
                .addPathSegment("movies")
                .addQueryParameter("parse", cleaned)
                .build()

        val root = get(url, key) ?: return null
        val first =
            root.getAsJsonArray("data")
                ?.firstOrNull { element -> element.isJsonObject }
                ?.asJsonObject
                ?: return null

        // Read defensively across the names this API is known to use for an image. A response
        // shape that changes should cost the artwork, never an exception on a details screen.
        return POSTER_FIELDS.firstNotNullOfOrNull { field -> first.imageUrl(field) }
    }

    /**
     * An image URL from [field], which may be a string or an object holding one.
     *
     * Both shapes appear in this API depending on the endpoint, and guessing one would mean the
     * artwork silently never arriving for half the catalogue.
     */
    private fun JsonObject.imageUrl(field: String): String? {
        val element = get(field) ?: return null
        val direct = element.takeIf { it.isJsonPrimitive }?.asString
        if (direct != null) return direct.takeIf(::looksLikeHttpUrl)
        val nested = element.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        return NESTED_IMAGE_FIELDS
            .firstNotNullOfOrNull { name ->
                nested.get(name)?.takeIf { it.isJsonPrimitive }?.asString
            }?.takeIf(::looksLikeHttpUrl)
    }

    /**
     * Whether this is an address the app can actually load.
     *
     * Checked because the value becomes an image request: anything that is not plain http or https
     * is a malformed response rather than a picture, and the loader should never be handed one.
     */
    private fun looksLikeHttpUrl(value: String): Boolean =
        value.startsWith("https://", ignoreCase = true) || value.startsWith("http://", ignoreCase = true)

    private fun get(url: HttpUrl, key: String): JsonObject? =
        runCatching {
            val request =
                Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $key")
                    .header("Accept", "application/json")
                    .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                JsonParser.parseString(response.body.string()).asJsonObject
            }
        }.getOrNull()

    /** Never the key, and never the address, which carries a searched title. */
    override fun toString(): String = "AdultArtworkClient(configured=$isConfigured)"

    private companion object {
        const val DEFAULT_BASE_URL = "https://api.theporndb.net/"

        /** Short enough to be a mis-parse rather than a title worth a request. */
        const val MIN_TITLE = 3

        /** Provider titles run long with quality and channel prefixes; the search needs none of it. */
        const val MAX_TITLE = 120

        val POSTER_FIELDS = listOf("poster", "image", "cover", "background")
        val NESTED_IMAGE_FIELDS = listOf("url", "full", "large", "medium")
    }
}

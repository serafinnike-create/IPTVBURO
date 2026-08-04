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

private fun JsonObject.string(name: String): String? =
    get(name)?.takeUnless { it.isJsonNull }?.asString?.takeIf(String::isNotBlank)

private fun JsonObject.int(name: String): Int? =
    get(name)?.takeUnless { it.isJsonNull }?.asInt

private fun JsonObject.double(name: String): Double? =
    get(name)?.takeUnless { it.isJsonNull }?.asDouble

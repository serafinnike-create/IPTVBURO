package com.lucasserafin94.iptvburo.xtream

import okhttp3.HttpUrl

data class XtreamCredentials(
    val serverUrl: String,
    val username: String,
    val password: String,
) {
    override fun toString(): String =
        "XtreamCredentials(serverUrl=<redacted>, username=<redacted>, password=<redacted>)"
}

data class XtreamAccount(
    val authenticated: Boolean,
    val status: String?,
    val isTrial: Boolean?,
    val activeConnections: Int?,
    val maximumConnections: Int?,
    val allowedOutputFormats: Set<String>,
)

enum class XtreamContentType {
    LIVE,
    MOVIE,
    SERIES,
}

data class XtreamCategory(
    val providerId: String,
    val name: String,
    val contentType: XtreamContentType,
)

data class XtreamCatalogItem(
    val providerId: String,
    val name: String,
    val contentType: XtreamContentType,
    val categoryIds: List<String>,
    val containerExtension: String?,
    val artworkUrl: String?,
    val year: Int?,
    val rating: Double?,
    val addedAtEpochSeconds: Long?,
) {
    override fun toString(): String =
        "XtreamCatalogItem(" +
            "providerId=$providerId, " +
            "name=$name, " +
            "contentType=$contentType, " +
            "categoryIds=$categoryIds, " +
            "containerExtension=$containerExtension, " +
            "artworkUrl=${if (artworkUrl == null) "null" else "<redacted>"}, " +
            "year=$year, rating=$rating, addedAtEpochSeconds=$addedAtEpochSeconds" +
            ")"
}

data class XtreamEpisode(
    val providerId: String,
    val title: String,
    val seasonNumber: Int,
    val episodeNumber: Int?,
    val containerExtension: String?,
    val artworkUrl: String?,
) {
    override fun toString(): String =
        "XtreamEpisode(" +
            "providerId=$providerId, title=$title, seasonNumber=$seasonNumber, " +
            "episodeNumber=$episodeNumber, containerExtension=$containerExtension, " +
            "artworkUrl=${if (artworkUrl == null) "null" else "<redacted>"})"
}

data class XtreamSeriesDetails(
    val providerId: String,
    val title: String,
    val plot: String?,
    val artworkUrl: String?,
    val backdropUrls: List<String>,
    val episodes: List<XtreamEpisode>,
    val cast: String? = null,
    val director: String? = null,
    val genre: String? = null,
    val releaseDate: String? = null,
    val rating: Double? = null,
    val youtubeTrailerId: String? = null,
) {
    override fun toString(): String =
        "XtreamSeriesDetails(" +
            "providerId=$providerId, title=$title, plot=${if (plot == null) "null" else "<present>"}, " +
            "artworkUrl=${if (artworkUrl == null) "null" else "<redacted>"}, " +
            "backdropCount=${backdropUrls.size}, episodeCount=${episodes.size}, " +
            "cast=${if (cast == null) "null" else "<present>"}, " +
            "director=${if (director == null) "null" else "<present>"}, genre=$genre, " +
            "releaseDate=$releaseDate, rating=$rating, " +
            "youtubeTrailerId=${if (youtubeTrailerId == null) "null" else "<present>"})"
}

/** Rich metadata returned by Xtream's VOD details endpoint. */
data class XtreamMovieDetails(
    val providerId: String,
    val title: String,
    val plot: String?,
    val cast: String?,
    val director: String?,
    val genre: String?,
    val duration: String?,
    val releaseDate: String?,
    val country: String?,
    val rating: Double?,
    val artworkUrl: String?,
    val backdropUrls: List<String>,
    val youtubeTrailerId: String?,
    val containerExtension: String?,
) {
    override fun toString(): String =
        "XtreamMovieDetails(" +
            "providerId=$providerId, title=$title, " +
            "plot=${if (plot == null) "null" else "<present>"}, " +
            "cast=${if (cast == null) "null" else "<present>"}, " +
            "director=${if (director == null) "null" else "<present>"}, " +
            "genre=${if (genre == null) "null" else "<present>"}, " +
            "duration=$duration, releaseDate=$releaseDate, country=$country, rating=$rating, " +
            "artworkUrl=${if (artworkUrl == null) "null" else "<redacted>"}, " +
            "backdropCount=${backdropUrls.size}, " +
            "youtubeTrailerId=${if (youtubeTrailerId == null) "null" else "<present>"}, " +
            "containerExtension=$containerExtension)"
}

data class XtreamCollection<T>(
    val items: List<T>,
    val skippedItemCount: Int,
)

/** Result of a catalog response consumed without retaining the complete JSON tree. */
data class XtreamStreamSummary(
    val itemCount: Int,
    val skippedItemCount: Int,
)

/** A bounded now/next programme returned by Xtream's short EPG endpoint. */
data class XtreamEpgProgram(
    val title: String,
    val description: String?,
    val startEpochSeconds: Long?,
    val endEpochSeconds: Long?,
)

data class XtreamShortEpg(
    val programs: List<XtreamEpgProgram>,
    val skippedProgramCount: Int,
) {
    fun nowAndNext(nowEpochSeconds: Long): Pair<XtreamEpgProgram?, XtreamEpgProgram?> {
        val ordered = programs.sortedWith(compareBy<XtreamEpgProgram> { it.startEpochSeconds ?: Long.MAX_VALUE })
        val current = ordered.lastOrNull { program ->
            val start = program.startEpochSeconds ?: return@lastOrNull false
            val end = program.endEpochSeconds ?: return@lastOrNull false
            nowEpochSeconds in start until end
        }
        val next = ordered.firstOrNull { program ->
            val start = program.startEpochSeconds ?: return@firstOrNull false
            start > (current?.startEpochSeconds ?: nowEpochSeconds)
        }
        return current to next
    }
}

class XtreamEndpoint internal constructor(
    val baseUrl: HttpUrl,
) {
    override fun toString(): String = "XtreamEndpoint(baseUrl=<redacted>)"
}

class XtreamClientException(
    val reason: XtreamFailureReason,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

enum class XtreamFailureReason {
    INVALID_SERVER,
    NETWORK,
    HTTP,
    RESPONSE_TOO_LARGE,
    INVALID_RESPONSE,
    AUTHENTICATION,
}

package com.lucasserafin94.iptvburo.domain.model

/**
 * A user-managed playlist source.
 *
 * Connection details intentionally do not belong to this model. Platform storage is responsible
 * for keeping sensitive source configuration encrypted.
 */
data class Source(
    val id: String,
    val name: String,
    val type: SourceType,
    val createdAtEpochMillis: Long = 0L,
    val updatedAtEpochMillis: Long? = null,
    val channelCount: Int = 0,
)

enum class SourceType {
    LOCAL_M3U,
    REMOTE_M3U,
    XTREAM,

    /**
     * Stalker/Ministra portal, sold as "MAC + portal".
     *
     * Authenticates the device rather than a person: the MAC address is the credential, so there
     * is no username or password to store for most subscriptions.
     */
    STALKER,
}

enum class CatalogContentType {
    LIVE,
    MOVIE,
    SERIES,
    EPISODE,
    UNKNOWN,
}

data class Category(
    val id: String,
    val sourceId: String,
    val name: String,
    val sortOrder: Int = 0,
    val contentType: CatalogContentType = CatalogContentType.UNKNOWN,
    val providerCategoryId: String? = null,
)

/**
 * A playable catalog item.
 *
 * [streamUri] and the values in [requestHeaders] are sensitive. The custom [toString] prevents
 * accidental disclosure when the model is passed to a logger or an exception message.
 */
data class Channel(
    val id: String,
    val sourceId: String,
    val name: String,
    val streamUri: String,
    val categoryId: String? = null,
    val tvgId: String? = null,
    val tvgName: String? = null,
    val logoUri: String? = null,
    val requestHeaders: Map<String, String> = emptyMap(),
    val contentType: CatalogContentType = CatalogContentType.UNKNOWN,
    val providerItemId: String? = null,
    val containerExtension: String? = null,
    val year: Int? = null,
    val rating: Double? = null,
) {
    override fun toString(): String =
        "Channel(" +
            "id=$id, " +
            "sourceId=$sourceId, " +
            "name=$name, " +
            "streamUri=<redacted>, " +
            "categoryId=$categoryId, " +
            "tvgId=$tvgId, " +
            "tvgName=$tvgName, " +
            "logoUri=${if (logoUri == null) "null" else "<redacted>"}, " +
            "requestHeaderNames=${requestHeaders.keys.sorted()}, " +
            "contentType=$contentType, providerItemId=$providerItemId, " +
            "containerExtension=$containerExtension, year=$year, rating=$rating" +
            ")"
}

data class SeriesDetails(
    val sourceId: String,
    val providerSeriesId: String,
    val title: String,
    val plot: String?,
    val artworkUri: String?,
    val episodes: List<Episode>,
    val backdropUris: List<String> = emptyList(),
    val cast: String? = null,
    val director: String? = null,
    val genre: String? = null,
    val releaseDate: String? = null,
    val rating: Double? = null,
    val youtubeTrailerId: String? = null,
) {
    override fun toString(): String =
        "SeriesDetails(" +
            "sourceId=$sourceId, providerSeriesId=$providerSeriesId, title=$title, " +
            "plot=${if (plot == null) "null" else "<present>"}, " +
            "artworkUri=${if (artworkUri == null) "null" else "<redacted>"}, " +
            "backdropCount=${backdropUris.size}, episodeCount=${episodes.size}, " +
            "cast=${if (cast == null) "null" else "<present>"}, " +
            "director=${if (director == null) "null" else "<present>"}, " +
            "youtubeTrailerId=${if (youtubeTrailerId == null) "null" else "<present>"})"
}

data class MovieDetails(
    val sourceId: String,
    val providerMovieId: String,
    val title: String,
    val plot: String?,
    val cast: String?,
    val director: String?,
    val genre: String?,
    val duration: String?,
    val releaseDate: String?,
    val country: String?,
    val rating: Double?,
    /** How many people voted for [rating]. Absent when the source does not say. */
    val voteCount: Int? = null,
    val artworkUri: String?,
    val backdropUris: List<String>,
    val youtubeTrailerId: String?,
) {
    override fun toString(): String =
        "MovieDetails(" +
            "sourceId=$sourceId, providerMovieId=$providerMovieId, title=$title, " +
            "plot=${if (plot == null) "null" else "<present>"}, " +
            "cast=${if (cast == null) "null" else "<present>"}, " +
            "director=${if (director == null) "null" else "<present>"}, " +
            "artworkUri=${if (artworkUri == null) "null" else "<redacted>"}, " +
            "backdropCount=${backdropUris.size}, " +
            "youtubeTrailerId=${if (youtubeTrailerId == null) "null" else "<present>"})"
}

data class Episode(
    val id: String,
    val sourceId: String,
    val providerEpisodeId: String,
    val title: String,
    val seasonNumber: Int,
    val episodeNumber: Int?,
    val artworkUri: String?,
    val containerExtension: String?,
) {
    override fun toString(): String =
        "Episode(" +
            "id=$id, sourceId=$sourceId, providerEpisodeId=$providerEpisodeId, " +
            "title=$title, seasonNumber=$seasonNumber, episodeNumber=$episodeNumber, " +
            "artworkUri=${if (artworkUri == null) "null" else "<redacted>"}, " +
            "containerExtension=$containerExtension)"
}

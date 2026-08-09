package com.lucasserafin94.iptvburo.domain.model

/** Universal media taxonomy. Legacy video enums remain available during the migration. */
enum class MediaKind {
    LIVE_TV,
    MOVIE,
    SERIES,
    VIDEO_EPISODE,
    MUSIC_TRACK,
    ALBUM,
    ARTIST,
    AUDIO_PLAYLIST,
    RADIO_STATION,
    PODCAST_SHOW,
    PODCAST_EPISODE,
    AUDIOBOOK,
    AUDIOBOOK_CHAPTER,
    PHOTO,
    GAME_STREAM,
    UNKNOWN,
}

fun CatalogContentType.toMediaKind(): MediaKind =
    when (this) {
        CatalogContentType.LIVE -> MediaKind.LIVE_TV
        CatalogContentType.MOVIE -> MediaKind.MOVIE
        CatalogContentType.SERIES -> MediaKind.SERIES
        CatalogContentType.EPISODE -> MediaKind.VIDEO_EPISODE
        CatalogContentType.UNKNOWN -> MediaKind.UNKNOWN
    }

/** Audio and future media never masquerade as a legacy video type. */
fun MediaKind.toLegacyCatalogContentType(): CatalogContentType =
    when (this) {
        MediaKind.LIVE_TV -> CatalogContentType.LIVE
        MediaKind.MOVIE -> CatalogContentType.MOVIE
        MediaKind.SERIES -> CatalogContentType.SERIES
        MediaKind.VIDEO_EPISODE -> CatalogContentType.EPISODE
        else -> CatalogContentType.UNKNOWN
    }

fun ContentKind.toMediaKind(): MediaKind =
    when (this) {
        ContentKind.LIVE -> MediaKind.LIVE_TV
        ContentKind.MOVIE -> MediaKind.MOVIE
        ContentKind.SERIES -> MediaKind.SERIES
        ContentKind.EPISODE -> MediaKind.VIDEO_EPISODE
        ContentKind.UNKNOWN -> MediaKind.UNKNOWN
    }

fun MediaKind.toLegacyContentKind(): ContentKind =
    when (this) {
        MediaKind.LIVE_TV -> ContentKind.LIVE
        MediaKind.MOVIE -> ContentKind.MOVIE
        MediaKind.SERIES -> ContentKind.SERIES
        MediaKind.VIDEO_EPISODE -> ContentKind.EPISODE
        else -> ContentKind.UNKNOWN
    }

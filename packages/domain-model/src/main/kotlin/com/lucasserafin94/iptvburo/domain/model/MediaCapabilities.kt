package com.lucasserafin94.iptvburo.domain.model

/** Conservative media-level capabilities. An omitted flag always means unavailable. */
data class MediaCapabilities(
    val playable: Boolean = false,
    val live: Boolean = false,
    val seekable: Boolean = false,
    val downloadable: Boolean = false,
    val backgroundPlayback: Boolean = false,
    val gapless: Boolean = false,
    val crossfade: Boolean = false,
    val replayGain: Boolean = false,
    val lyrics: Boolean = false,
    val chapters: Boolean = false,
    val multipleAudioTracks: Boolean = false,
    val subtitles: Boolean = false,
    val pictureInPicture: Boolean = false,
    val multiview: Boolean = false,
) {
    /** What remains available when both the source and platform must support an action. */
    infix fun intersect(other: MediaCapabilities): MediaCapabilities =
        MediaCapabilities(
            playable = playable && other.playable,
            live = live && other.live,
            seekable = seekable && other.seekable,
            downloadable = downloadable && other.downloadable,
            backgroundPlayback = backgroundPlayback && other.backgroundPlayback,
            gapless = gapless && other.gapless,
            crossfade = crossfade && other.crossfade,
            replayGain = replayGain && other.replayGain,
            lyrics = lyrics && other.lyrics,
            chapters = chapters && other.chapters,
            multipleAudioTracks = multipleAudioTracks && other.multipleAudioTracks,
            subtitles = subtitles && other.subtitles,
            pictureInPicture = pictureInPicture && other.pictureInPicture,
            multiview = multiview && other.multiview,
        )
}

/** Capabilities declared by one configured source, before platform intersection. */
data class SourceCapabilities(
    val supportedKinds: Set<MediaKind> = emptySet(),
    val media: MediaCapabilities = MediaCapabilities(),
) {
    fun supports(kind: MediaKind): Boolean = kind in supportedKinds

    infix fun intersect(platform: MediaCapabilities): SourceCapabilities =
        copy(media = media intersect platform)
}

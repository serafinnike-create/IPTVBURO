package com.lucasserafin94.iptvburo.domain.model

/**
 * Capabilities observed from the active player timeline.
 *
 * These values describe what the source can do now; they must not be inferred only from a file
 * extension.
 */
data class PlaybackCapabilities(
    val playable: Boolean = false,
    val isLive: Boolean = false,
    val durationMillis: Long? = null,
    val seekCapability: SeekCapability = SeekCapability.UNKNOWN,
    val liveWindowDurationMillis: Long? = null,
    val supportsAlternateAudio: Boolean = false,
    val supportsSubtitles: Boolean = false,
    val downloadable: Boolean = false,
    val backgroundPlayback: Boolean = false,
    val gapless: Boolean = false,
    val crossfade: Boolean = false,
    val replayGain: Boolean = false,
    val lyrics: Boolean = false,
    val chapters: Boolean = false,
    val pictureInPicture: Boolean = false,
    val multiview: Boolean = false,
) {
    init {
        require(durationMillis == null || durationMillis >= 0) {
            "durationMillis must be null or non-negative"
        }
        require(liveWindowDurationMillis == null || liveWindowDurationMillis >= 0) {
            "liveWindowDurationMillis must be null or non-negative"
        }
    }

    val canSeek: Boolean
        get() = seekCapability.canSeek

    /** Intersection used when both the resolved media and the player must support an action. */
    infix fun intersect(other: PlaybackCapabilities): PlaybackCapabilities =
        PlaybackCapabilities(
            playable = playable && other.playable,
            isLive = isLive && other.isLive,
            durationMillis =
                durationMillis?.let { left -> other.durationMillis?.let { right -> minOf(left, right) } },
            seekCapability = seekCapability intersect other.seekCapability,
            liveWindowDurationMillis =
                liveWindowDurationMillis?.let { left ->
                    other.liveWindowDurationMillis?.let { right -> minOf(left, right) }
                },
            supportsAlternateAudio = supportsAlternateAudio && other.supportsAlternateAudio,
            supportsSubtitles = supportsSubtitles && other.supportsSubtitles,
            downloadable = downloadable && other.downloadable,
            backgroundPlayback = backgroundPlayback && other.backgroundPlayback,
            gapless = gapless && other.gapless,
            crossfade = crossfade && other.crossfade,
            replayGain = replayGain && other.replayGain,
            lyrics = lyrics && other.lyrics,
            chapters = chapters && other.chapters,
            pictureInPicture = pictureInPicture && other.pictureInPicture,
            multiview = multiview && other.multiview,
        )
}

enum class SeekCapability(
    val canSeek: Boolean,
) {
    UNKNOWN(false),
    PRECISE(true),
    APPROXIMATE(true),
    LIVE_WINDOW(true),
    NOT_SEEKABLE(false),
}

private infix fun SeekCapability.intersect(other: SeekCapability): SeekCapability =
    when {
        !canSeek || !other.canSeek -> SeekCapability.NOT_SEEKABLE
        this == SeekCapability.LIVE_WINDOW || other == SeekCapability.LIVE_WINDOW ->
            SeekCapability.LIVE_WINDOW
        this == SeekCapability.APPROXIMATE || other == SeekCapability.APPROXIMATE ->
            SeekCapability.APPROXIMATE
        else -> SeekCapability.PRECISE
    }

package com.lucasserafin94.iptvburo.domain.model

/**
 * Capabilities observed from the active player timeline.
 *
 * These values describe what the source can do now; they must not be inferred only from a file
 * extension.
 */
data class PlaybackCapabilities(
    val isLive: Boolean = false,
    val durationMillis: Long? = null,
    val seekCapability: SeekCapability = SeekCapability.UNKNOWN,
    val liveWindowDurationMillis: Long? = null,
    val supportsAlternateAudio: Boolean = false,
    val supportsSubtitles: Boolean = false,
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

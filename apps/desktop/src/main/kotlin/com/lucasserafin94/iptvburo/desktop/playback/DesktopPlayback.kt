package com.lucasserafin94.iptvburo.desktop.playback

import java.net.URI
import com.lucasserafin94.iptvburo.domain.model.PlaybackProgressIdentity

data class DesktopPlaybackRequest(
    val title: String,
    val uri: URI,
    val progressIdentity: PlaybackProgressIdentity? = null,
    val startPositionMillis: Long = 0L,
) {
    override fun toString(): String = "DesktopPlaybackRequest(title=$title, uri=<redacted>)"
}

data class DesktopPlaybackSnapshot(
    val loading: Boolean = true,
    val ready: Boolean = false,
    val playing: Boolean = false,
    val positionMillis: Double = 0.0,
    val durationMillis: Double = 0.0,
    val volume: Double = 0.8,
    val playbackRate: Double = 1.0,
    val engineName: String = "Windows",
    val ended: Boolean = false,
    val errorMessage: String? = null,
)

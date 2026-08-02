package com.lucasserafin94.iptvburo.desktop.playback

import java.net.URI

data class DesktopPlaybackRequest(
    val title: String,
    val uri: URI,
) {
    override fun toString(): String = "DesktopPlaybackRequest(title=$title, uri=<redacted>)"
}

data class DesktopPlaybackSnapshot(
    val ready: Boolean = false,
    val playing: Boolean = false,
    val positionMillis: Double = 0.0,
    val durationMillis: Double = 0.0,
    val volume: Double = 0.8,
    val errorMessage: String? = null,
)

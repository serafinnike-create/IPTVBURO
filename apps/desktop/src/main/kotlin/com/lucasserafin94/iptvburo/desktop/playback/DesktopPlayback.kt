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
    /** Audio tracks the current title carries, as the provider supplied them. */
    val audioTracks: List<MediaTrack> = emptyList(),
    /** Subtitle tracks, plus a synthetic "off" entry so they can be turned back off. */
    val subtitleTracks: List<MediaTrack> = emptyList(),
    val activeAudioTrackId: Int? = null,
    val activeSubtitleTrackId: Int? = null,
)

/**
 * One selectable track inside the playing title.
 *
 * [id] is VLC's own track id, which is what the control interface expects back. [label] is whatever
 * the file names it — usually a language, sometimes nothing useful, which is why it falls back to a
 * numbered name rather than showing an empty row.
 */
data class MediaTrack(
    val id: Int,
    val label: String,
)

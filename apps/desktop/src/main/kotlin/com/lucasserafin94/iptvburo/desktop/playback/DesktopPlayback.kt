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
    /**
     * Picture brightness, with 1.0 as the source's own.
     *
     * Not persisted: it corrects a dark scene or a bright room, both of which are about this
     * viewing rather than a preference, and a remembered value would silently alter every film
     * afterwards.
     */
    val brightness: Double = 1.0,
    val aspectRatio: PlaybackAspectRatio = PlaybackAspectRatio.DEFAULT,
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

/**
 * How the picture is fitted to the window.
 *
 * [DEFAULT] is the film as shot, letterboxed where it does not match the window — the right answer
 * almost always, and the reason it is the default. The rest exist for the cases it is not: a
 * provider that encoded the wrong flag, or a viewer who would rather lose the edges than see bars.
 *
 * [vlcValue] is what VLC's `aspectratio` command expects; empty means "leave it alone".
 */
enum class PlaybackAspectRatio(
    val label: String,
    val vlcValue: String,
) {
    DEFAULT("Padrão", ""),

    /** Crops to fill the window. Trims the edges rather than stretching faces. */
    FILL("Preencher", ""),

    RATIO_16_9("16:9", "16:9"),
    RATIO_16_10("16:10", "16:10"),
    RATIO_4_3("4:3", "4:3"),
    RATIO_3_2("3:2", "3:2"),
}

/**
 * How subtitles are drawn, when a title carries them.
 *
 * VLC builds its text renderer with the video output, so these apply to the next title played
 * rather than the one on screen. The UI says so; a setting that silently does nothing until the
 * next film would read as broken.
 */
data class SubtitleStyle(
    val size: SubtitleSize = SubtitleSize.MEDIUM,
    val textColour: SubtitleColour = SubtitleColour.WHITE,
    /** A dark box behind the text — the difference between readable and not over a bright scene. */
    val background: Boolean = true,
) {
    /** VLC's relative font size. Counter-intuitively, a *smaller* number is a larger subtitle. */
    val vlcRelativeSize: Int
        get() = size.vlcRelativeSize
}

enum class SubtitleSize(
    val label: String,
    val vlcRelativeSize: Int,
) {
    SMALL("Pequeno", 20),
    MEDIUM("Médio", 16),
    LARGE("Grande", 12),
    HUGE("Muito grande", 10),
}

/**
 * Subtitle colours, as VLC's integer values.
 *
 * A short list rather than a full picker: these are the colours that stay legible over film, and a
 * free choice mostly produces subtitles nobody can read.
 */
enum class SubtitleColour(
    val label: String,
    val vlcValue: Int,
) {
    WHITE("Branco", 0xFFFFFF),
    YELLOW("Amarelo", 0xFFFF00),
    GREY("Cinza", 0xC0C0C0),
    GREEN("Verde", 0x00FF00),
    CYAN("Ciano", 0x00FFFF),
}

package com.lucasserafin94.iptvburo.domain.model

/**
 * How subtitles should look, independent of the player drawing them.
 *
 * Windows draws these through VLC and Android through Media3's `SubtitleView`, which take entirely
 * different values — a font scale here, an integer colour there. What both platforms agree on is
 * what the *user* chose, so that is what lives here; each side maps it to its own renderer.
 *
 * The choices are deliberately short. A free colour picker and a continuous size slider mostly
 * produce subtitles nobody can read over film, and the point of this setting is legibility.
 */
data class SubtitlePresentation(
    val size: SubtitleTextSize = SubtitleTextSize.MEDIUM,
    val colour: SubtitleTextColour = SubtitleTextColour.WHITE,
    /**
     * A dark box behind the text.
     *
     * On by default: it is the difference between readable and not over a bright scene, and a
     * viewer who does not want it can turn it off, whereas one who needs it may not think to look.
     */
    val background: Boolean = true,
)

/**
 * Subtitle sizes, as a multiplier of the player's own default.
 *
 * A scale rather than a point size, because the same subtitle has to work on a phone held at arm's
 * length and a television across a room; the player already knows its own baseline for that.
 */
enum class SubtitleTextSize(
    /** Stable across releases: it is written to preferences. */
    val id: String,
    val scale: Float,
) {
    SMALL("small", 0.75f),
    MEDIUM("medium", 1.0f),
    LARGE("large", 1.35f),
    HUGE("huge", 1.7f),
    ;

    companion object {
        /** Falls back to [MEDIUM] for an id written by a version that knew a size this one does not. */
        fun fromId(id: String?): SubtitleTextSize = entries.firstOrNull { it.id == id } ?: MEDIUM
    }
}

/** Subtitle colours that stay legible over film, as 24-bit RGB. */
enum class SubtitleTextColour(
    val id: String,
    val rgb: Int,
) {
    WHITE("white", 0xFFFFFF),
    YELLOW("yellow", 0xFFFF00),
    GREY("grey", 0xC0C0C0),
    GREEN("green", 0x00FF00),
    CYAN("cyan", 0x00FFFF),
    ;

    companion object {
        fun fromId(id: String?): SubtitleTextColour = entries.firstOrNull { it.id == id } ?: WHITE
    }
}

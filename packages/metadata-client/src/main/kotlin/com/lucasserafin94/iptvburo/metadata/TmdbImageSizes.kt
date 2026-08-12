package com.lucasserafin94.iptvburo.metadata

/**
 * Picks the TMDb image width to ask for, given how many real pixels the screen will use.
 *
 * TMDb serves a fixed ladder of widths and encodes the choice in the path — `/t/p/w342/abc.jpg`.
 * The app used to bake a width in when the URL was built, far from any knowledge of the display, so
 * every screen got sizes chosen for 1080p. Measured on a 4K panel at 200% scale:
 *
 * | element  | asked for | drawn at | result          |
 * | -------- | --------- | -------- | --------------- |
 * | poster   | w342      | 496 px   | 1.45x upscale   |
 * | backdrop | w1280     | 3840 px  | 3x upscale      |
 * | cast     | w185      | 370 px   | 2x upscale      |
 *
 * Upscaling is what makes a poster wall look soft and a backdrop look blocky. The backdrop is the
 * worst of the three because a Ken Burns transform magnifies it further.
 *
 * The obvious fix — ask for `original` everywhere — is a trap. A shelf of twenty posters at full
 * resolution is tens of megabytes against a 768 MB heap, which is the same memory pressure that has
 * already frozen this app once. So the width is chosen *proportionally*: a 1080p machine keeps
 * asking for the small images it was always right to ask for.
 */
object TmdbImageSizes {
    /** Widths TMDb publishes for posters and stills, smallest first. */
    private val POSTER_LADDER = intArrayOf(92, 154, 185, 342, 500, 780)

    /** Widths TMDb publishes for backdrops. */
    private val BACKDROP_LADDER = intArrayOf(300, 780, 1280)

    /**
     * Rewrites the width segment of a TMDb image URL to suit [targetWidthPx].
     *
     * Returns [url] unchanged when it is not a TMDb image path, so a provider's own artwork — which
     * has no size ladder and must not be touched — passes straight through.
     *
     * The next width *at or above* the target is chosen, never one below: a slightly larger image
     * scaled down is sharp, while a smaller one scaled up is exactly the fault being fixed. Above
     * the ladder's top the largest available is used, because there is nothing better to ask for.
     */
    fun resizedForWidth(
        url: String,
        targetWidthPx: Int,
        isBackdrop: Boolean = false,
    ): String {
        if (targetWidthPx <= 0) return url
        val match = TMDB_WIDTH_SEGMENT.find(url) ?: return url
        val ladder = if (isBackdrop) BACKDROP_LADDER else POSTER_LADDER
        val chosen = ladder.firstOrNull { candidate -> candidate >= targetWidthPx } ?: ladder.last()

        // Left alone when the URL already asks for the right width, so an unchanged string is
        // returned identically and Coil's cache key does not move for no reason.
        val current = match.groupValues[1].toIntOrNull()
        if (current == chosen) return url
        return url.replaceRange(match.range, "/w$chosen/")
    }

    /**
     * The `/wNNN/` segment of a TMDb image path.
     *
     * Anchored on the slashes so it cannot match a width-like number inside a filename, and only
     * `w` sizes are rewritten — `original` is already the largest there is, and `h632` is a height
     * whose ladder is a different one.
     */
    private val TMDB_WIDTH_SEGMENT = Regex("""/w(\d{2,4})/""")
}

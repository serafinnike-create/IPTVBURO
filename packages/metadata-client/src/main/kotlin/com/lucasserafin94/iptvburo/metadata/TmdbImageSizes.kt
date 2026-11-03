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
 * Above the top of a ladder the request goes to `original`, which for a backdrop is usually the
 * 3840×2160 master: the home banner and a title's background fill a 2560- or 3840-pixel window,
 * and capping them at `w1280` stretched every one of them two or three times over. That is the
 * only place it happens — a slot wider than 1280 real pixels — and the image is still decoded at
 * the slot's size, so the cost is the download, not the heap.
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
     * The width both ladders share, for an image whose kind cannot be told.
     *
     * Asking a backdrop for a poster-only width — or the reverse — returns nothing at all, so a URL
     * that says neither (`w780`, `original`) only ever moves between this and `original`.
     */
    private val SHARED_LADDER = intArrayOf(780)

    /** Widths that exist on one ladder only, and so say which kind of image a URL is. */
    private val POSTER_ONLY = setOf(92, 154, 185, 342, 500)
    private val BACKDROP_ONLY = setOf(300, 1280)

    /**
     * Rewrites the size segment of a TMDb image URL to suit [targetWidthPx].
     *
     * Returns [url] unchanged when it is not a TMDb image path, so a provider's own artwork — which
     * has no size ladder and must not be touched — passes straight through.
     *
     * The next width *at or above* the target is chosen, never one below: a slightly larger image
     * scaled down is sharp, while a smaller one scaled up is exactly the fault being fixed. Above
     * the ladder's top, `original`.
     *
     * [isBackdrop] is a hint; the URL usually says itself. `w1280` and `w300` exist only for
     * backdrops, `w342` and the rest below 780 only for posters, and the cropped sizes providers
     * send (`w600_and_h900_bestv2`, `w1920_and_h800_multi_faces`) are tall or wide by their own
     * numbers. Only one screen ever passed the hint, so the home banner, the loading wall and every
     * title's background were sized as posters and stopped at 780 pixels across the whole window.
     */
    fun resizedForWidth(
        url: String,
        targetWidthPx: Int,
        isBackdrop: Boolean = false,
    ): String {
        if (targetWidthPx <= 0) return url
        val match = TMDB_SIZE_SEGMENT.find(url) ?: return url
        val segment = match.groupValues[1]
        val width = LEADING_WIDTH.find(segment)?.groupValues?.get(1)?.toIntOrNull()
        val height = CROP_HEIGHT.find(segment)?.groupValues?.get(1)?.toIntOrNull()
        // `h632` and anything else that is not a width: a different ladder, left alone.
        if (segment != ORIGINAL && width == null) return url

        val backdrop =
            isBackdrop || width in BACKDROP_ONLY || (width != null && height != null && width > height)
        val poster =
            !backdrop && (width in POSTER_ONLY || (width != null && height != null && height > width))
        val ladder =
            when {
                backdrop -> BACKDROP_LADDER
                poster -> POSTER_LADDER
                else -> SHARED_LADDER
            }
        val chosen = ladder.firstOrNull { candidate -> candidate >= targetWidthPx }
        val replacement = if (chosen == null) ORIGINAL else "w$chosen"

        // Left alone when the URL already asks for the right size, so an unchanged string is
        // returned identically and Coil's cache key does not move for no reason.
        if (replacement == segment) return url
        return url.replaceRange(match.groups[1]!!.range, replacement)
    }

    private const val ORIGINAL = "original"

    /**
     * The size segment of a TMDb image path: `/t/p/<size>/`.
     *
     * Anchored on TMDb's image host, so a provider's own URL that happens to contain `/w500/` is
     * never rewritten into a path that server does not have.
     */
    private val TMDB_SIZE_SEGMENT = Regex("""image\.tmdb\.org/t/p/([A-Za-z0-9_]+)/""")

    private val LEADING_WIDTH = Regex("""^w(\d{2,4})""")

    private val CROP_HEIGHT = Regex("""_and_h(\d{2,4})""")
}

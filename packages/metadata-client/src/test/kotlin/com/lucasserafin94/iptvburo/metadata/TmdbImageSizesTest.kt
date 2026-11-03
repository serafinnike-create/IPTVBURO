package com.lucasserafin94.iptvburo.metadata

import kotlin.test.Test
import kotlin.test.assertEquals

class TmdbImageSizesTest {
    private val poster = "https://image.tmdb.org/t/p/w342/abc123.jpg"
    private val backdrop = "https://image.tmdb.org/t/p/w1280/xyz789.jpg"

    /**
     * The fault this exists to fix: a 248 dp poster on a 4K panel at 200% needs 496 real pixels and
     * was being handed a 342-wide image, so a wall of posters looked soft.
     */
    @Test
    fun `a 4K poster asks for a width above what it draws`() {
        assertEquals(
            "https://image.tmdb.org/t/p/w500/abc123.jpg",
            TmdbImageSizes.resizedForWidth(poster, targetWidthPx = 496),
        )
    }

    /**
     * And a 1080p machine keeps asking for the small image.
     *
     * This is the half that stops the fix becoming a memory problem: asking for `original`
     * everywhere would put tens of megabytes per shelf against a 768 MB heap.
     */
    @Test
    fun `a 1080p poster is not upgraded needlessly`() {
        assertEquals(poster, TmdbImageSizes.resizedForWidth(poster, targetWidthPx = 248))
    }

    /** Never a width below the target: scaling up is the fault, scaling down is sharp. */
    @Test
    fun `the chosen width is never smaller than the target`() {
        listOf(100, 200, 400, 700).forEach { target ->
            val chosen =
                Regex("""/w(\d+)/""")
                    .find(TmdbImageSizes.resizedForWidth(poster, target))!!
                    .groupValues[1]
                    .toInt()
            assertEquals(true, chosen >= target, "$target got $chosen")
        }
    }

    /** Beyond the ladder the master file is the only thing sharper, so `original` is asked for. */
    @Test
    fun `a target beyond the ladder takes the original`() {
        assertEquals(
            "https://image.tmdb.org/t/p/original/abc123.jpg",
            TmdbImageSizes.resizedForWidth(poster, targetWidthPx = 4000),
        )
    }

    /**
     * The fault reported as "low quality" backgrounds: a banner 2560 pixels wide was handed the
     * 1280 backdrop, or — with no hint passed — a 780 poster width, and stretched over the window.
     */
    @Test
    fun `a full-width backdrop takes the original without being told it is one`() {
        assertEquals(
            "https://image.tmdb.org/t/p/original/xyz789.jpg",
            TmdbImageSizes.resizedForWidth(backdrop, targetWidthPx = 2560),
        )
    }

    /** A w1280 URL is a backdrop by its own width, so a small slot steps down its own ladder. */
    @Test
    fun `a backdrop is recognised from its url`() {
        assertEquals(
            "https://image.tmdb.org/t/p/w300/xyz789.jpg",
            TmdbImageSizes.resizedForWidth(backdrop, targetWidthPx = 280),
        )
    }

    /** Providers often send TMDb's cropped sizes; they are read by their own proportions. */
    @Test
    fun `cropped provider sizes are understood`() {
        val tall = "https://image.tmdb.org/t/p/w600_and_h900_bestv2/abc123.jpg"
        val wide = "https://image.tmdb.org/t/p/w1920_and_h800_multi_faces/xyz789.jpg"

        assertEquals(
            "https://image.tmdb.org/t/p/w342/abc123.jpg",
            TmdbImageSizes.resizedForWidth(tall, targetWidthPx = 300),
        )
        assertEquals(
            "https://image.tmdb.org/t/p/original/xyz789.jpg",
            TmdbImageSizes.resizedForWidth(wide, targetWidthPx = 3000),
        )
    }

    /** An image whose kind cannot be told only moves to the width both ladders have. */
    @Test
    fun `an original url in a small slot comes down to the shared width`() {
        assertEquals(
            "https://image.tmdb.org/t/p/w780/abc123.jpg",
            TmdbImageSizes.resizedForWidth("https://image.tmdb.org/t/p/original/abc123.jpg", targetWidthPx = 400),
        )
    }

    /** A provider path that merely looks like a TMDb size is not TMDb's, and is not rewritten. */
    @Test
    fun `a provider url with a width-like segment is left alone`() {
        val providerArt = "http://provider.example:8080/w500/12345.jpg"

        assertEquals(providerArt, TmdbImageSizes.resizedForWidth(providerArt, targetWidthPx = 900))
    }

    /** Backdrops have their own ladder; w500 is not one of its rungs. */
    @Test
    fun `a backdrop uses the backdrop ladder`() {
        assertEquals(
            "https://image.tmdb.org/t/p/w1280/xyz789.jpg",
            TmdbImageSizes.resizedForWidth(backdrop, targetWidthPx = 900, isBackdrop = true),
        )
    }

    /**
     * A provider's own artwork must pass through untouched.
     *
     * It has no size ladder, and rewriting a path segment in a subscriber's URL would at best 404
     * and at worst point somewhere unintended.
     */
    @Test
    fun `a non-TMDb url is returned unchanged`() {
        val providerArt = "http://provider.example:8080/images/movie/12345.jpg"

        assertEquals(providerArt, TmdbImageSizes.resizedForWidth(providerArt, targetWidthPx = 500))
    }

    /** `original` is already the largest there is and has no width to rewrite. */
    @Test
    fun `an original url is left alone`() {
        val original = "https://image.tmdb.org/t/p/original/abc123.jpg"

        assertEquals(original, TmdbImageSizes.resizedForWidth(original, targetWidthPx = 4000))
    }

    /** An unmeasured layout must not force a size decision on incomplete information. */
    @Test
    fun `a zero target changes nothing`() {
        assertEquals(poster, TmdbImageSizes.resizedForWidth(poster, targetWidthPx = 0))
    }

    /** The same URL back, identically, so Coil's cache key does not move for no reason. */
    @Test
    fun `an already correct width is returned unchanged`() {
        assertEquals(poster, TmdbImageSizes.resizedForWidth(poster, targetWidthPx = 342))
    }
}

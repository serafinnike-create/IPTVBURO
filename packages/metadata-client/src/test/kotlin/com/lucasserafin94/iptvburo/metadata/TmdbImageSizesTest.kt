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

    /** Beyond the ladder there is nothing better to ask for, so the largest is used. */
    @Test
    fun `a target beyond the ladder takes the largest available`() {
        assertEquals(
            "https://image.tmdb.org/t/p/w780/abc123.jpg",
            TmdbImageSizes.resizedForWidth(poster, targetWidthPx = 4000),
        )
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

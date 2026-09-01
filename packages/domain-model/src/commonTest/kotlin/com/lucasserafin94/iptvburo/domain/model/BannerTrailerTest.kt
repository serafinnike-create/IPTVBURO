package com.lucasserafin94.iptvburo.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * When the home banner may play a trailer.
 *
 * The banner is the first thing anybody sees, so a trailer that fails there is worse than no
 * trailer: artwork that never moves looks deliberate, while a black rectangle on the opening screen
 * looks like a broken app. Every case here is a reason to fall back to the poster.
 */
class BannerTrailerTest {
    private val now = 1_700_000_000L
    private val good = "dQw4w9WgXcQ"

    // -------------------------------------------------------------------------------------------
    // The id itself
    // -------------------------------------------------------------------------------------------

    @Test
    fun `a real video id is playable`() {
        assertTrue(BannerTrailer.isPlayableId(good))
        assertTrue(BannerTrailer.isPlayableId("_-aBcDeFgHi"))
    }

    /**
     * Anything else is refused before it reaches a player.
     *
     * The id is pasted into a URL, and a malformed one produces an error frame on the opening
     * screen — the exact thing this exists to prevent.
     */
    @Test
    fun `anything not shaped like an id is refused`() {
        listOf(
            null,
            "",
            "   ",
            "tooshort",
            "waaaaaytoolongforanid",
            "has spaces!",
            "abc/def+ghi",
            "https://youtu.be/dQw4w9WgXcQ",
        ).forEach { candidate ->
            assertFalse(BannerTrailer.isPlayableId(candidate), "aceitou $candidate")
        }
    }

    // -------------------------------------------------------------------------------------------
    // Whether to play
    // -------------------------------------------------------------------------------------------

    @Test
    fun `a good id with no history plays`() {
        assertTrue(BannerTrailer.shouldPlay(good, failedAtEpochSeconds = null, nowEpochSeconds = now))
    }

    /**
     * A trailer that failed is not tried again for a day.
     *
     * A video that was pulled, made private or region-locked stays that way, and retrying on every
     * rotation costs the viewer a wait each time for the same answer.
     */
    @Test
    fun `a trailer that just failed is not tried again`() {
        assertFalse(
            BannerTrailer.shouldPlay(
                good,
                failedAtEpochSeconds = now - 60,
                nowEpochSeconds = now,
            ),
        )
    }

    /** But an old failure is worth one more attempt: a video can come back. */
    @Test
    fun `an old failure is forgotten`() {
        assertTrue(
            BannerTrailer.shouldPlay(
                good,
                failedAtEpochSeconds = now - BannerTrailer.FAILURE_MEMORY_SECONDS - 1,
                nowEpochSeconds = now,
            ),
        )
    }

    /**
     * A timestamp from the future is expired, not fresh.
     *
     * A clock corrected backwards would otherwise suppress a working trailer indefinitely.
     */
    @Test
    fun `a failure recorded in the future is not believed`() {
        assertFalse(BannerTrailer.hasRecentlyFailed(now + 5_000, now))
        assertTrue(BannerTrailer.shouldPlay(good, failedAtEpochSeconds = now + 5_000, nowEpochSeconds = now))
    }

    /** A trailer must never talk over something the viewer chose. */
    @Test
    fun `nothing plays over the viewer's own playback`() {
        assertFalse(
            BannerTrailer.shouldPlay(
                good,
                failedAtEpochSeconds = null,
                nowEpochSeconds = now,
                somethingElseIsPlaying = true,
            ),
        )
    }

    /**
     * And scrolling away stops it.
     *
     * Muting and continuing is not a compromise worth making: the viewer moved away from the
     * banner, so it is not what they are looking at.
     */
    @Test
    fun `scrolling away stops the trailer`() {
        assertFalse(
            BannerTrailer.shouldPlay(
                good,
                failedAtEpochSeconds = null,
                nowEpochSeconds = now,
                viewerIsScrolling = true,
            ),
        )
    }

    // -------------------------------------------------------------------------------------------
    // Remembering failures
    // -------------------------------------------------------------------------------------------

    @Test
    fun `expired failures are pruned and recent ones kept`() {
        val failures =
            mapOf(
                "recente" to now - 60,
                "antiga" to now - BannerTrailer.FAILURE_MEMORY_SECONDS - 1,
                "futura" to now + 5_000,
            )

        val kept = BannerTrailer.pruneFailures(failures, now)

        assertEquals(setOf("recente"), kept.keys)
    }

    /** The settle delay is long enough that a rotation does not start a video per title. */
    @Test
    fun `the banner waits before it starts`() {
        assertEquals(
            3_000L,
            BannerTrailer.SETTLE_MILLIS,
            "o trailer nao respeita os tres segundos de leitura do banner",
        )
    }

    /**
     * The copy never reaches under the trailer.
     *
     * The synopsis was reported cut off twice: once because the column had the whole banner and the
     * video was drawn over its last line, and again at a narrow window, where a fixed cap could not
     * track a video measured as a fraction. So the answer is arithmetic, not a constant.
     */
    @Test
    fun `the copy stops before the trailer starts`() {
        // Every width from a small laptop to a 4K panel, with the gutter each one uses.
        listOf(1_000f to 24f, 1_280f to 24f, 1_600f to 48f, 1_920f to 48f, 3_840f to 48f)
            .forEach { (banner, gutter) ->
                val copy = BannerTrailer.copyWidthBesideTrailer(banner, gutter)
                val trailerStarts = banner * (1f - BannerTrailer.TRAILER_WIDTH_FRACTION)

                assertTrue(
                    copy + gutter <= trailerStarts,
                    "a ${banner}px a sinopse passa por baixo do trailer: $copy",
                )
            }
    }

    /** And it is never squeezed into a column one word wide. */
    @Test
    fun `the copy is never squeezed to nothing`() {
        // A window far narrower than the app is meant for: the remainder would go to almost zero.
        val copy = BannerTrailer.copyWidthBesideTrailer(bannerWidth = 600f, gutter = 48f)

        assertEquals(
            BannerTrailer.COPY_MIN_WIDTH,
            copy,
            "a coluna foi espremida ate uma letra por linha, outra vez",
        )
    }

    /** A wider banner gives the copy more room, not less. */
    @Test
    fun `a wider banner widens the copy`() {
        val narrow = BannerTrailer.copyWidthBesideTrailer(1_280f, 24f)
        val wide = BannerTrailer.copyWidthBesideTrailer(1_920f, 24f)

        assertTrue(wide > narrow, "alargar a janela nao deu mais espaco ao texto: $narrow -> $wide")
    }
}

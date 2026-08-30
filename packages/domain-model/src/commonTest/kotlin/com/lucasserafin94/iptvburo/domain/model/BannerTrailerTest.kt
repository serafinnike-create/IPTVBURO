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
        assertTrue(
            BannerTrailer.SETTLE_MILLIS >= 1_000L,
            "o banner comeca depressa demais e abre um video por rotacao",
        )
    }
}

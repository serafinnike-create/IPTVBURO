package com.lucasserafin94.iptvburo.desktop.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The stop grace must grow with the buffer, at every size.
 *
 * It did not. `networkCachingMillis + STOP_GRACE_HEADROOM_MILLIS` added two Ints and converted to
 * Long afterwards, so a large buffer overflowed to a negative number and `coerceAtLeast` then
 * raised it to the four-second floor. The most heavily buffered player would have received the
 * *shortest* grace — reconnecting streams that were still refilling, which is the exact flicker the
 * grace exists to prevent.
 *
 * Not reachable from the app's own two call sites (1500ms and 5000ms), which is why it never showed
 * up. The constructor now refuses an out-of-range buffer as well, so it cannot become reachable
 * without someone changing both.
 */
class StopGraceOverflowTest {
    @Test
    fun `the grace never collapses to the floor for a large buffer`() {
        val grace = stopGraceFor(Int.MAX_VALUE)
        assertTrue(
            grace > STOP_GRACE_DWELL_MILLIS,
            "A huge buffer must not produce the smallest grace; got ${grace}ms.",
        )
        assertEquals(Int.MAX_VALUE.toLong() + STOP_GRACE_HEADROOM_MILLIS, grace)
    }

    @Test
    fun `the grace rises with the buffer`() {
        val sizes = listOf(0, 1_500, 5_000, 60_000, 1_000_000, Int.MAX_VALUE)
        val graces = sizes.map(::stopGraceFor)
        assertEquals(
            graces.sorted(),
            graces,
            "A bigger buffer needs at least as long to refill, so its grace cannot be shorter.",
        )
    }

    @Test
    fun `the floor still applies to a player with no buffering`() {
        assertTrue(stopGraceFor(0) >= STOP_GRACE_DWELL_MILLIS)
        // Negative is nonsense but must stay safe rather than arithmetically surprising.
        assertEquals(STOP_GRACE_DWELL_MILLIS, stopGraceFor(Int.MIN_VALUE))
    }

    @Test
    fun `a player refuses a buffer outside the safe range`() {
        assertFailsWith<IllegalArgumentException> {
            VlcDesktopPlayer(networkCachingMillis = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            VlcDesktopPlayer(networkCachingMillis = Int.MAX_VALUE)
        }
    }

    @Test
    fun `the sizes the app actually uses are accepted`() {
        // Both real call sites, so the guard cannot be tightened past what the app needs.
        VlcDesktopPlayer(networkCachingMillis = DEFAULT_NETWORK_CACHING_MILLIS).dispose()
        VlcDesktopPlayer(networkCachingMillis = MULTIVIEW_NETWORK_CACHING_MILLIS).dispose()
    }
}

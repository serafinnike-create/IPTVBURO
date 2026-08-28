package com.lucasserafin94.iptvburo.desktop.playback

import com.lucasserafin94.iptvburo.domain.model.PlaybackBuffering
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

    /**
     * A film buffers two minutes, and its grace has to clear that.
     *
     * The whole point of reading far ahead is that a connection which drops and comes back never
     * reaches the picture. A grace shorter than the buffer would undo it: the player would be
     * forcibly reconnected part-way through an ordinary refill, which is the flicker this
     * mechanism exists to prevent.
     */
    @Test
    fun `a film's two-minute buffer gets a grace long enough to refill it`() {
        val grace = stopGraceFor(PlaybackBuffering.ON_DEMAND_MILLIS)

        assertTrue(
            grace > PlaybackBuffering.ON_DEMAND_MILLIS,
            "a tolerancia ($grace ms) e menor do que o buffer que tem de encher",
        )
    }

    /**
     * And a live channel keeps the short grace its small buffer needs.
     *
     * Both graces carry the same fixed headroom, so they can never be worlds apart — what matters
     * is that a channel is reconnected in tens of seconds while a film is given minutes. Half a
     * minute is the line: beyond that a viewer stops waiting and presses something.
     */
    @Test
    fun `a live channel is not given a film's patience`() {
        val live = stopGraceFor(PlaybackBuffering.LIVE_MILLIS)

        assertTrue(live < 30_000, "um canal ao vivo esperaria $live ms antes de reconectar")
        assertTrue(
            stopGraceFor(PlaybackBuffering.ON_DEMAND_MILLIS) > live * 4,
            "um filme nao esta a receber muito mais paciencia do que um canal",
        )
    }
}

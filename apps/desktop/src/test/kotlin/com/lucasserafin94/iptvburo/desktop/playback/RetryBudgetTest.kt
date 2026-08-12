package com.lucasserafin94.iptvburo.desktop.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * When a player earns its restart attempts back.
 *
 * This is the rule behind the longest-running multiview complaint: four channels started, played for
 * a few seconds, then went black one at a time until a single tile was left, and entering full
 * screen took even that one out.
 *
 * The budget was cumulative over the player's whole life. Four decoders competing means every tile
 * stumbles sooner or later, and each stumble spent one of three attempts that were never returned —
 * so the grid decayed to whichever channel had been luckiest. Full screen finished it: resizing the
 * window rebuilds every swap chain at once, and by then there was nothing left to spend.
 */
class RetryBudgetTest {
    @Test
    fun `a stream that has settled gets its attempts back`() {
        assertTrue(
            shouldRestoreRetryBudget(
                playing = true,
                retriesUsed = 2,
                steadyForMillis = STEADY_PLAYBACK_DWELL_MILLIS + 1,
            ),
            "a channel playing steadily has recovered, and must be able to recover again later",
        )
    }

    /**
     * A stream bouncing between playing and stopped is not recovering.
     *
     * Restoring on the first `playing` poll would let it retry against the provider for ever, which
     * is worse for the customer than a tile that stays down and worse for the provider than either.
     */
    @Test
    fun `a brief flicker of playback does not restore the budget`() {
        assertFalse(
            shouldRestoreRetryBudget(
                playing = true,
                retriesUsed = 3,
                steadyForMillis = 2_000,
            ),
        )
    }

    @Test
    fun `nothing is restored while playback is down`() {
        assertFalse(
            shouldRestoreRetryBudget(
                playing = false,
                retriesUsed = 3,
                steadyForMillis = 0,
            ),
        )
    }

    /**
     * A player that never stumbled does not need the write.
     *
     * Purely a matter of not touching shared state on every poll of every tile — four players
     * polling twice a second is enough traffic to keep pointless work out of.
     */
    @Test
    fun `an untroubled player needs no restoring`() {
        assertFalse(
            shouldRestoreRetryBudget(
                playing = true,
                retriesUsed = 0,
                steadyForMillis = STEADY_PLAYBACK_DWELL_MILLIS * 10,
            ),
        )
    }

    /**
     * The dwell is a real wait, not a token one.
     *
     * Pinned because shortening it silently would bring back the flapping case, and that failure
     * shows up as load on the provider rather than as anything visible in the app.
     */
    @Test
    fun `the dwell is long enough to mean something`() {
        assertTrue(
            STEADY_PLAYBACK_DWELL_MILLIS >= 15_000,
            "a dwell under fifteen seconds does not distinguish recovery from a bounce",
        )
    }

    /**
     * A momentary stop is not a drop.
     *
     * This is the rule behind the reported flicker. A live stream reports `stopped` for a poll or
     * two over ordinary events and recovers by itself; reconnecting at the first such report tore
     * down a video output that was about to come back, and rebuilding it is the black flash itself.
     * The log showed the loop plainly — `playing`, `stopped`, `retrying (1/3)`, `playing`, over and
     * over, the count never rising because the stream kept recovering.
     */
    @Test
    fun `a brief stop is ignored rather than reconnected`() {
        listOf(0L, 500L, 1_000L, 2_000L).forEach { millis ->
            assertFalse(
                shouldTreatStopAsDrop(millis),
                "a stop lasting ${millis}ms is a transient, and reconnecting on it is the flicker",
            )
        }
    }

    @Test
    fun `a stop that lasts is treated as a drop`() {
        // Derived from the grace rather than from the floor: the grace now depends on how much the
        // player buffers, and a test pinned to the bare floor silently stopped exercising the rule.
        assertTrue(shouldTreatStopAsDrop(stopGraceFor(DEFAULT_NETWORK_CACHING_MILLIS) + 1))
    }

    /**
     * The grace is comfortably longer than the poll interval.
     *
     * pollState runs every 500ms. A grace of one or two polls would still fire on the transients it
     * exists to ignore, so the margin is what makes the rule work at all — and shrinking it silently
     * would bring the flicker back with every test still green.
     */
    @Test
    fun `the grace is well clear of the poll interval`() {
        assertTrue(
            STOP_GRACE_DWELL_MILLIS >= 2_000,
            "a grace under four polls does not survive an ordinary buffer refill",
        )
    }

    /**
     * The grace outlasts the buffer it is guarding.
     *
     * A tile holding five seconds of stream can sit at `stopped` for most of that while it refills.
     * A grace shorter than the buffer would fire in the middle of an ordinary recovery — tearing
     * down a video output that was already coming back, which is the flicker itself.
     */
    @Test
    fun `the grace always outlasts the buffer`() {
        listOf(
            DEFAULT_NETWORK_CACHING_MILLIS,
            MULTIVIEW_NETWORK_CACHING_MILLIS,
            0,
            20_000,
        ).forEach { buffer ->
            assertTrue(
                stopGraceFor(buffer) > buffer,
                "a grace of ${stopGraceFor(buffer)}ms does not clear a ${buffer}ms buffer",
            )
        }
    }

    /**
     * A multiview tile tolerates a longer stall than a single title.
     *
     * Four streams sharing one connection stumble more often and for longer, and the log showed
     * exactly that: every tile falling to `stopped` and recovering seconds later, repeatedly.
     */
    @Test
    fun `a multiview tile is given more time than a single title`() {
        assertTrue(
            stopGraceFor(MULTIVIEW_NETWORK_CACHING_MILLIS) >
                stopGraceFor(DEFAULT_NETWORK_CACHING_MILLIS),
        )
    }

    /** A stall within the buffer's own refill window is never treated as a drop. */
    @Test
    fun `a stall shorter than the buffer is ignored`() {
        assertFalse(
            shouldTreatStopAsDrop(
                unhealthyForMillis = MULTIVIEW_NETWORK_CACHING_MILLIS.toLong(),
                networkCachingMillis = MULTIVIEW_NETWORK_CACHING_MILLIS,
            ),
            "a tile refilling its own buffer has not dropped",
        )
    }

    /** A stall well past the buffer is a real drop and must be reconnected. */
    @Test
    fun `a stall well past the buffer is a drop`() {
        assertTrue(
            shouldTreatStopAsDrop(
                unhealthyForMillis = stopGraceFor(MULTIVIEW_NETWORK_CACHING_MILLIS) + 1,
                networkCachingMillis = MULTIVIEW_NETWORK_CACHING_MILLIS,
            ),
        )
    }

    /**
     * A live channel is never given up on.
     *
     * This is the fault the log caught on the fourth tile: it stopped and recovered four times in
     * under a minute, never reaching the thirty seconds of steady playback that would have returned
     * its budget, and then gave up for good — `gave up after 3 retries` — while the stream itself
     * was perfectly alive. A channel carries no end, so reopening is always the right answer; the
     * spacing, not a cap, is what keeps that from becoming a flood.
     */
    @Test
    fun `a live channel may always reconnect`() {
        listOf(0, 3, 10, 500).forEach { failures ->
            assertTrue(
                mayReconnect(liveStream = true, consecutiveFailures = failures),
                "a live channel must still try after $failures failures — its stream may be back",
            )
        }
    }

    /**
     * A film is the opposite case.
     *
     * One that reaches its end has ended, and reopening it would replay it from the beginning, so a
     * title with a declared length keeps a strict, small allowance.
     */
    @Test
    fun `a film gives up after a few attempts`() {
        assertTrue(mayReconnect(liveStream = false, consecutiveFailures = 0))
        assertFalse(mayReconnect(liveStream = false, consecutiveFailures = MAX_FILM_RECONNECTS))
    }

    /**
     * Attempts spread out rather than repeating at the same rate.
     *
     * Unlimited retries protect the viewer; the backoff protects the provider. A channel that is
     * genuinely off air must settle into a quiet retry rather than a request every few seconds for
     * the rest of the evening.
     */
    @Test
    fun `the wait between attempts grows and then levels off`() {
        val delays = (0..8).map(::reconnectBackoffMillis)

        assertEquals(
            FIRST_RECONNECT_DELAY_MILLIS,
            delays.first(),
            "the first reconnection is prompt; a viewer is watching",
        )
        delays.zipWithNext().forEach { (earlier, later) ->
            assertTrue(later >= earlier, "the wait never shrinks: $delays")
        }
        assertTrue(
            delays.last() <= MAX_RECONNECT_DELAY_MILLIS,
            "the wait is capped so a channel that comes back is picked up within the minute",
        )
        assertEquals(
            MAX_RECONNECT_DELAY_MILLIS,
            delays.last(),
            "a long outage settles at the cap rather than growing without bound",
        )
    }

    /** Nonsense input must not produce a negative or absurd wait. */
    @Test
    fun `the backoff is sane at the edges`() {
        assertTrue(reconnectBackoffMillis(-5) >= FIRST_RECONNECT_DELAY_MILLIS)
        assertTrue(reconnectBackoffMillis(Int.MAX_VALUE) <= MAX_RECONNECT_DELAY_MILLIS)
    }
}

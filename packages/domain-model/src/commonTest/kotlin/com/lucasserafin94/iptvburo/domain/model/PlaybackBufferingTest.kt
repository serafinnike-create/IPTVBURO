package com.lucasserafin94.iptvburo.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * How far ahead of the picture the player reads.
 *
 * The distinction between a film and a live channel is the whole design here, so these mostly guard
 * that it survives: one number applied to both would either make live unwatchable or leave films as
 * fragile as they were.
 */
class PlaybackBufferingTest {
    /** The figure asked for: a film reads two minutes ahead. */
    @Test
    fun `a film reads two minutes ahead`() {
        assertEquals(120_000, PlaybackBuffering.millisFor(isLive = false))
    }

    /**
     * A live channel has no ahead to read.
     *
     * What has not been broadcast cannot be fetched early, so a large buffer buys nothing there and
     * costs a later start and a picture that sits behind — a neighbour's shout arriving before the
     * goal.
     */
    @Test
    fun `a live channel keeps its small reservoir`() {
        assertEquals(1_500, PlaybackBuffering.millisFor(isLive = true))
    }

    /** The two must never converge, whatever else changes. */
    @Test
    fun `a film reads far further ahead than a live channel`() {
        assertTrue(
            PlaybackBuffering.millisFor(isLive = false) >
                PlaybackBuffering.millisFor(isLive = true) * 10,
            "a diferenca entre filme e ao vivo desapareceu",
        )
    }

    /**
     * Several channels on one connection need more than one does, and still nothing like a film's.
     */
    @Test
    fun `multiview sits between the two`() {
        val multiview = PlaybackBuffering.millisFor(isLive = true, isMultiview = true)

        assertTrue(multiview > PlaybackBuffering.millisFor(isLive = true))
        assertTrue(multiview < PlaybackBuffering.millisFor(isLive = false))
    }

    /** Multiview is about sharing a connection, so it wins even for a film in a tile. */
    @Test
    fun `multiview decides the buffer whatever the content is`() {
        assertEquals(
            PlaybackBuffering.millisFor(isLive = true, isMultiview = true),
            PlaybackBuffering.millisFor(isLive = false, isMultiview = true),
        )
    }

    /**
     * A buffer beyond the limit is not a more robust player: it is a player that appears not to
     * start.
     */
    @Test
    fun `the limit leaves room for a film and refuses the absurd`() {
        assertTrue(PlaybackBuffering.isWithinLimit(PlaybackBuffering.ON_DEMAND_MILLIS))
        assertTrue(PlaybackBuffering.isWithinLimit(PlaybackBuffering.MAXIMUM_MILLIS))
        assertFalse(PlaybackBuffering.isWithinLimit(PlaybackBuffering.MAXIMUM_MILLIS + 1))
        assertFalse(PlaybackBuffering.isWithinLimit(-1))
    }
}

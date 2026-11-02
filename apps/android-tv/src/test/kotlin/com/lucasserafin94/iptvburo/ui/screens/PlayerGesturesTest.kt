package com.lucasserafin94.iptvburo.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerGesturesTest {
    @Test
    fun `double tap zones are the outer thirds and the middle`() {
        assertEquals(SeekZone.BACK, seekZoneFor(x = 50f, width = 900f))
        assertEquals(SeekZone.MIDDLE, seekZoneFor(x = 450f, width = 900f))
        assertEquals(SeekZone.FORWARD, seekZoneFor(x = 850f, width = 900f))
        assertEquals(SeekZone.MIDDLE, seekZoneFor(x = 10f, width = 0f))
    }

    @Test
    fun `brightness is on the left half and volume on the right`() {
        assertEquals(LevelSide.BRIGHTNESS, levelSideFor(x = 100f, width = 900f))
        assertEquals(LevelSide.VOLUME, levelSideFor(x = 600f, width = 900f))
    }

    @Test
    fun `a swipe moves the level and stops at the ends`() {
        assertEquals(0.64f, adjustedLevel(current = 0.5f, upwardFraction = 0.1f), 0.0001f)
        assertEquals(1f, adjustedLevel(current = 0.9f, upwardFraction = 0.5f), 0.0001f)
        assertEquals(0f, adjustedLevel(current = 0.1f, upwardFraction = -0.5f), 0.0001f)
    }

    @Test
    fun `repeated taps in one direction add up and a reversal starts over`() {
        val first = accumulatedSeekSeconds(previous = null, zone = SeekZone.FORWARD)
        val second = accumulatedSeekSeconds(previous = first, zone = SeekZone.FORWARD)
        val reversed = accumulatedSeekSeconds(previous = second, zone = SeekZone.BACK)

        assertEquals(10, first)
        assertEquals(20, second)
        assertEquals(-10, reversed)
        assertEquals(0, accumulatedSeekSeconds(previous = 30, zone = SeekZone.MIDDLE))
    }
}

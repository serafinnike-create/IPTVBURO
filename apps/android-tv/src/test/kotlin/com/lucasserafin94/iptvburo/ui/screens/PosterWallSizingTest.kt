package com.lucasserafin94.iptvburo.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How much of the loading screen's poster wall is actually drawn.
 *
 * The wall repeats a 512 dp cycle of four covers. It used to repeat it six times whatever the
 * screen, which is 3072 dp of strip — sized for a 4K television and drawn in full on a 360 dp
 * phone. With seven rows that came to 168 `AsyncImage`s, and the cost was not theoretical: on a
 * real phone the wall issued 168 concurrent image requests, 84 were cancelled when the boot screen
 * ended, and the first decode landed 6.6 seconds in — longer than the boot screen exists. The
 * covers never appeared, so the wall showed its bundled placeholders and looked like it had failed.
 */
class PosterWallSizingTest {
    @Test
    fun `a phone draws only what it can show`() {
        // The device this was measured on: 720x1640 at density 2.0.
        assertEquals(2, posterCyclesFor(360f))
    }

    @Test
    fun `a tablet draws more than a phone and less than a television`() {
        val tablet = posterCyclesFor(800f)

        assertTrue("tablet was $tablet", tablet in 3..4)
    }

    @Test
    fun `a television still gets a full wall`() {
        assertEquals(6, posterCyclesFor(1920f))
    }

    @Test
    fun `nothing wider than 4K asks for more than the ceiling`() {
        assertEquals(6, posterCyclesFor(3840f))
    }

    /**
     * A cycle covers the screen and a second one supplies what slides in behind it. Fewer than two
     * would leave the strip's trailing edge visible as it scrolls.
     */
    @Test
    fun `even the narrowest screen keeps a cycle in reserve`() {
        assertEquals(2, posterCyclesFor(1f))
    }

    /**
     * Growing the screen must never shrink the wall, or some width in between would leave a gap.
     */
    @Test
    fun `wider screens never draw fewer cycles`() {
        val widths = listOf(1f, 320f, 360f, 411f, 600f, 800f, 1280f, 1920f, 2560f, 3840f)

        widths.zipWithNext().forEach { (narrower, wider) ->
            assertTrue(
                "$wider drew fewer cycles than $narrower",
                posterCyclesFor(wider) >= posterCyclesFor(narrower),
            )
        }
    }
}

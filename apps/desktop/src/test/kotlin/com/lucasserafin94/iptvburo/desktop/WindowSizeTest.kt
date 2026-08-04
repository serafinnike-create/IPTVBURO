package com.lucasserafin94.iptvburo.desktop

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The window must never open taller than the screen can show.
 *
 * On a 1536x864 laptop the fixed 1380x860 default put the lower edge under the taskbar. The last
 * rail was drawn off screen, so the page looked like it refused to scroll when in fact the part
 * that scrolls was never visible.
 */
class WindowSizeTest {
    @Test
    fun `shrinks to fit a short laptop screen`() {
        val size = fitToScreen(usableWidth = 1_536, usableHeight = 816)

        assertTrue(size.height.value <= 816f, "was ${size.height}")
        assertTrue(size.width.value <= 1_536f, "was ${size.width}")
    }

    @Test
    fun `keeps the preferred size on a large screen`() {
        val size = fitToScreen(usableWidth = 2_560, usableHeight = 1_400)

        assertTrue(size.width.value == 1_380f, "was ${size.width}")
        assertTrue(size.height.value == 860f, "was ${size.height}")
    }

    /** A tiny screen must still yield a usable window rather than collapsing to nothing. */
    @Test
    fun `never shrinks below the usable minimum`() {
        val size = fitToScreen(usableWidth = 640, usableHeight = 400)

        assertTrue(size.width.value >= 900f, "was ${size.width}")
        assertTrue(size.height.value >= 560f, "was ${size.height}")
    }

    @Test
    fun `unknown screen bounds fall back to the preferred size`() {
        val size = fitToScreen(usableWidth = 0, usableHeight = 0)

        assertTrue(size.width.value == 1_380f, "was ${size.width}")
        assertTrue(size.height.value == 860f, "was ${size.height}")
    }
}

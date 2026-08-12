package com.lucasserafin94.iptvburo.desktop.playback

import java.awt.event.KeyEvent
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Getting out of full screen multiview.
 *
 * Borderless full screen hides every window control, and the AWT video canvases take keyboard focus
 * as soon as the pointer crosses them — so Compose sees no key events and there is nothing on screen
 * to click. The way out has to be the keyboard, and it has to work on the first key tried.
 */
class MultiviewFullScreenKeyTest {
    @Test
    fun `F11 enters full screen while other keys remain free in windowed mode`() {
        assertTrue(multiviewKeyTogglesFullScreen(KeyEvent.VK_F11, isFullScreen = false))
        assertFalse(multiviewKeyTogglesFullScreen(KeyEvent.VK_ESCAPE, isFullScreen = false))
        assertFalse(multiviewKeyTogglesFullScreen(KeyEvent.VK_SPACE, isFullScreen = false))
    }

    @Test
    fun `any ordinary key leaves full screen`() {
        val tried =
            listOf(
                KeyEvent.VK_ESCAPE,
                KeyEvent.VK_SPACE,
                KeyEvent.VK_BACK_SPACE,
                KeyEvent.VK_ENTER,
                KeyEvent.VK_Q,
                KeyEvent.VK_5,
                KeyEvent.VK_F11,
                KeyEvent.VK_LEFT,
            )

        tried.forEach { key ->
            assertTrue(
                multiviewKeyLeavesFullScreen(key, isFullScreen = true),
                "key ${KeyEvent.getKeyText(key)} must return the window to normal",
            )
        }
    }

    /**
     * A modifier is pressed on the way to a shortcut, not as a request to leave.
     *
     * Alt+Tab is the case that matters: dropping out of full screen the instant Alt goes down would
     * resize the window under somebody who was only switching away from it.
     */
    @Test
    fun `modifiers alone do not leave full screen`() {
        val modifiers =
            listOf(
                KeyEvent.VK_CONTROL,
                KeyEvent.VK_SHIFT,
                KeyEvent.VK_ALT,
                KeyEvent.VK_ALT_GRAPH,
                KeyEvent.VK_META,
                KeyEvent.VK_WINDOWS,
                KeyEvent.VK_CAPS_LOCK,
                KeyEvent.VK_NUM_LOCK,
            )

        modifiers.forEach { key ->
            assertFalse(
                multiviewKeyLeavesFullScreen(key, isFullScreen = true),
                "modifier ${KeyEvent.getKeyText(key)} is part of a shortcut, not an exit",
            )
        }
    }

    /**
     * Windowed, keys are left alone.
     *
     * Nothing is claimed that is not needed: the window controls are visible and reachable, so there
     * is no reason to swallow every keystroke and every reason to keep them free for later.
     */
    @Test
    fun `keys are untouched when the window is not full screen`() {
        assertFalse(multiviewKeyLeavesFullScreen(KeyEvent.VK_ESCAPE, isFullScreen = false))
        assertFalse(multiviewKeyLeavesFullScreen(KeyEvent.VK_SPACE, isFullScreen = false))
    }
}

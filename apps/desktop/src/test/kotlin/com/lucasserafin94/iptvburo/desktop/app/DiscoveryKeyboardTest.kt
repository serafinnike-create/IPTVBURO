package com.lucasserafin94.iptvburo.desktop.app

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Deciding on a card without leaving the keyboard.
 *
 * The screen already offers two ways — the buttons and the swipe — and this is a third for
 * somebody whose hands are on the keys. The directions match the swipe deliberately: right keeps,
 * left skips, so the two never disagree about which way means what.
 */
class DiscoveryKeyboardTest {
    private fun read(relative: String): String = Path.of(relative).readText()

    private val screen = read("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/DiscoveryScreen.kt")
    private val app = read("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/DesktopApp.kt")

    @Test
    fun `the arrows do what the buttons do`() {
        assertTrue(
            screen.contains("Key.DirectionLeft ->") && screen.contains("DiscoveryVerdict.SKIPPED"),
            "left skips",
        )
        assertTrue(
            screen.contains("Key.DirectionRight ->") && screen.contains("DiscoveryVerdict.KEPT"),
            "right keeps",
        )
        assertTrue(
            screen.contains("Key.DirectionUp ->") && screen.contains("onOpenDetails(card)"),
            "up opens the details",
        )
    }

    @Test
    fun `left and right agree with the swipe`() {
        // Two ways to do the same thing that disagree about direction would be worse than one.
        val swipeKeeps = screen.indexOf("dragX > DECISION_THRESHOLD -> onDecide(DiscoveryVerdict.KEPT)")
        assertTrue(swipeKeeps >= 0, "the swipe still keeps on a rightward drag")
    }

    @Test
    fun `the screen takes focus, or the keys never arrive`() {
        // The handler attaches happily to a component that never receives a key, which looks
        // exactly like the keys not having been wired at all.
        assertTrue(screen.contains(".focusRequester(focusRequester)"))
        assertTrue(screen.contains(".focusable()"))
        assertTrue(screen.contains("focusRequester.requestFocus()"))
    }

    @Test
    fun `an empty deck decides nothing`() {
        // A key press with no card would otherwise reach onDecide with nothing to decide on.
        assertTrue(screen.contains("val card = top ?: return@onPreviewKeyEvent false"))
    }

    @Test
    fun `down closes the details page from wherever it is showing`() {
        // It cannot live on the discovery screen: by the time there is a page to close, that
        // screen is no longer the one on display.
        assertTrue(
            app.contains("if (event.key == Key.DirectionDown && appState.closeOpenedTitle())"),
            "the window's own handler owns this key",
        )
    }

    @Test
    fun `down with nothing open still scrolls`() {
        // closeOpenedTitle returns false when there was nothing to close, and the key is only
        // swallowed on true — otherwise a down press would stop the list moving.
        val handler = app.substringAfter("if (event.key == Key.DirectionDown").substringBefore("\n")
        assertTrue(handler.contains("appState.closeOpenedTitle()"), "the result decides")
    }
}

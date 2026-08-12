package com.lucasserafin94.iptvburo.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * How many tiles a subscription can actually sustain.
 *
 * Providers cap simultaneous connections per account, and going over the cap produces no error at
 * all — the provider simply stops sending on the older streams. From inside the app that looks
 * exactly like tiles going black for no reason, and it took a timestamped log to see what was
 * happening: four channels from four different broadcasters started, two kept playing, and two
 * ended after about five seconds each with "the source stopped sending".
 *
 * The number has always been available. `max_connections` is read from the provider's own account
 * response and was stored and never used, while the UI offered four slots to an account that could
 * sustain two.
 */
class MultiviewCapacityTest {
    /**
     * The rule itself, matching [DesktopAppState.multiviewCapacity].
     *
     * The production function is exercised directly. Duplicating its arithmetic in the test let
     * the test keep passing while the real capacity calculation ignored active connections.
     */
    @Test
    fun `a two-connection account is offered two tiles`() {
        assertEquals(2, availableMultiviewConnections(maximumConnections = 2, activeConnections = 0))
    }

    /**
     * A generous account still stops at the app's own cap.
     *
     * Beyond four, each tile is too small to follow and the machine runs four decoders for pictures
     * nobody can read — a limit that is about the screen, not the subscription.
     */
    @Test
    fun `a generous account is still capped by what the screen can show`() {
        assertEquals(4, availableMultiviewConnections(maximumConnections = 8, activeConnections = 0))
        assertEquals(4, availableMultiviewConnections(maximumConnections = 100, activeConnections = 0))
    }

    /**
     * An unknown limit means the app's own cap, not a cautious guess.
     *
     * Not every provider reports `max_connections`, and guessing low would take away a feature the
     * customer has paid for. Guessing high costs a black tile and a clear log line; guessing low
     * costs them multiview entirely.
     */
    @Test
    fun `an unknown limit falls back to the app's own cap`() {
        assertEquals(4, availableMultiviewConnections(maximumConnections = null, activeConnections = null))
    }

    @Test
    fun `connections already active on the account are not offered as tiles`() {
        assertEquals(2, availableMultiviewConnections(maximumConnections = 4, activeConnections = 2))
        assertEquals(1, availableMultiviewConnections(maximumConnections = 4, activeConnections = 3))
    }

    @Test
    fun `a stale fully-used count still leaves one recovery tile`() {
        assertEquals(1, availableMultiviewConnections(maximumConnections = 4, activeConnections = 4))
        assertEquals(1, availableMultiviewConnections(maximumConnections = 2, activeConnections = 99))
    }

    /**
     * A nonsensical limit never produces a grid that cannot hold anything.
     *
     * Providers do report zero and negative values. A capacity of zero would make the multiview
     * button dead with no explanation, which is worse than one tile that works.
     */
    @Test
    fun `a nonsensical limit still allows one tile`() {
        assertEquals(1, availableMultiviewConnections(maximumConnections = 0, activeConnections = 0))
        assertEquals(1, availableMultiviewConnections(maximumConnections = -3, activeConnections = 0))
    }

    /**
     * The message names the real number in every language the app ships.
     *
     * "Máximo de 4 canais" shown to somebody whose account allows two is worse than no message: it
     * tells them the app is broken rather than that their plan is the limit.
     */
    @Test
    fun `the limit message carries the number in every language`() {
        com.lucasserafin94.iptvburo.desktop.user.DesktopLanguage.entries.forEach { language ->
            val message =
                com.lucasserafin94.iptvburo.desktop.ui.DesktopStrings
                    .of(language)
                    .settingsText
                    .multiviewFull

            assertTrue(
                message.contains("%d"),
                "the $language message must take the real limit, not state a fixed four: $message",
            )
            assertTrue(
                "2" in message.format(2),
                "formatting must put the number in: ${message.format(2)}",
            )
            assertTrue(
                "4" !in message.format(2),
                "a message that still says four contradicts the limit it is explaining: " +
                    message.format(2),
            )
        }
    }
}

package com.lucasserafin94.iptvburo.desktop.playback

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Why brightness is applied at startup rather than while a title plays.
 *
 * VLC's HTTP control interface accepts a fixed list of commands, and its own README in the bundled
 * runtime names them: volume, seek, rate, the track selectors, the playlist verbs. `brightness` and
 * `adjust` are not among them. The player sent both anyway — the socket accepted them, the engine
 * ignored them, and the slider moved while the picture never changed. From the outside that is
 * indistinguishable from a broken control, and it was reported as one.
 *
 * The adjust filter is built with the video chain, so the only place the value can take effect is
 * the command line of the next title. That is the same limitation the subtitle appearance settings
 * carry, and it is handled the same way.
 */
class BrightnessControlTest {
    private val source: String =
        Files.readString(
            Path.of(
                "src/main/kotlin/com/lucasserafin94/iptvburo/desktop/playback/VlcDesktopPlayer.kt",
            ),
        )

    /**
     * The commands the bundled VLC actually supports, read from the runtime rather than assumed.
     *
     * Returns null when the runtime is not present — a build machine without the unpacked VLC must
     * skip rather than fail, since the absence proves nothing about the code.
     */
    private fun supportedCommands(): Set<String>? {
        val readme =
            Path.of(
                "build/compose/binaries/main/app/IPTVBURO/app/resources/vlc/lua/http/requests/README.txt",
            )
        if (!Files.isRegularFile(readme)) return null
        return Regex("\\?command=([a-z_]+)")
            .findAll(Files.readString(readme))
            .map { match -> match.groupValues[1] }
            .toSet()
    }

    @Test
    fun `the engine has no brightness command, which is why it is set at startup`() {
        val commands = supportedCommands() ?: return

        assertFalse(
            "brightness" in commands,
            "if VLC gained a brightness command, the deferred approach could be revisited",
        )
        assertFalse("adjust" in commands)
        // The control that does exist, so a failure above is read as "the list changed" rather than
        // "the file was not the one expected".
        assertTrue("volume" in commands, "volume is a real command and is sent live")
    }

    /**
     * No command is sent for brightness at all.
     *
     * The previous implementation issued two that the engine discards. Sending a command that is
     * known not to exist is worse than doing nothing: it makes the control look wired up.
     */
    @Test
    fun `no brightness command is issued to the control interface`() {
        val setter = source.substringAfter("fun setBrightness(").substringBefore("\n    fun ")

        assertFalse(
            "executeCommand" in setter,
            "brightness must not be sent over an interface that ignores it: $setter",
        )
    }

    /**
     * The value reaches the place it can work: the startup command line.
     *
     * Hardcoding 1.0 there is what made the slider permanently decorative, so the argument must
     * carry the remembered value.
     */
    @Test
    fun `the remembered brightness is passed when a title starts`() {
        assertTrue(
            "\"--brightness=\$pendingBrightness\"" in source,
            "the adjust filter is built at startup, and that is the only place this can apply",
        )
        assertFalse(
            "\"--brightness=1.0\"" in source,
            "a fixed value here ignores whatever the viewer chose",
        )
    }

    /**
     * The setting outlives the title it was chosen during.
     *
     * Somebody who darkens a film at night expects the next one to open the same way; a per-player
     * field would reset to 1.0 with every title and make the control feel random.
     */
    @Test
    fun `the brightness persists across players`() {
        val original = pendingBrightness
        try {
            VlcDesktopPlayer().setBrightness(0.7)

            assertEquals(
                0.7,
                pendingBrightness,
                "the choice must survive into the next title, not vanish with this player",
            )
            assertEquals(0.7, VlcDesktopPlayer().snapshot().brightness)
        } finally {
            pendingBrightness = original
        }
    }

    /** Out-of-range values are clamped rather than passed to the engine as nonsense. */
    @Test
    fun `brightness stays within the filter's own bounds`() {
        val original = pendingBrightness
        try {
            VlcDesktopPlayer().setBrightness(99.0)
            assertTrue(pendingBrightness <= 2.0, "VLC's adjust filter accepts 0 to 2")

            VlcDesktopPlayer().setBrightness(-5.0)
            assertTrue(pendingBrightness >= 0.0)
        } finally {
            pendingBrightness = original
        }
    }
}

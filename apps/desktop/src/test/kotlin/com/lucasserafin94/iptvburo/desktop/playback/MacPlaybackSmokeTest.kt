package com.lucasserafin94.iptvburo.desktop.playback

import com.sun.jna.Platform
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The macOS player really draws pictures.
 *
 * Runs only on a Mac — the release workflow's macOS jobs — against the libVLC the build bundles. It
 * opens a still image, which VLC's image demuxer plays as video, through the same player class the
 * app uses, and waits for frames to reach the surface. Loading the library, finding the plugins,
 * the format negotiation and the frame copy all have to work for a single frame to arrive, so this
 * is the one check that the Mac build can show a film at all.
 */
class MacPlaybackSmokeTest {
    @Test
    fun `libVLC plays into the surface on macOS`() {
        if (!Platform.isMac()) return
        val bundled = File("build/generated/app-resources/macos")
        if (File(bundled, "vlc/lib/libvlc.dylib").exists()) {
            System.setProperty("compose.application.resources.dir", bundled.absolutePath)
        }
        assertNotNull(LibVlcRuntime.library(), "libVLC could not be loaded")

        val image = File("src/main/resources/brand/buro-mark-512.png").absoluteFile
        val player = LibVlcDesktopPlayer(networkCachingMillis = 300, logTag = "smoke")
        try {
            player.createComponent(
                request = DesktopPlaybackRequest(title = "smoke", uri = image.toURI()),
                onPointerActivity = {},
                onClick = {},
                onKey = { false },
                onDoubleClick = null,
                onWheel = {},
            )
            val deadline = System.currentTimeMillis() + 20_000
            while (player.framesPresented == 0L && System.currentTimeMillis() < deadline) {
                Thread.sleep(100)
            }
            assertTrue(player.framesPresented > 0, "no frame reached the surface")
            assertNull(player.snapshot().errorMessage)
        } finally {
            player.dispose()
        }
    }
}

package com.lucasserafin94.iptvburo.desktop.app

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Keeps the top-bar and Settings update buttons on the same complete flow. */
class UpdateButtonWiringTest {
    private val source =
        Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/DesktopApp.kt").readText()

    @Test
    fun `both update buttons use the shared check and download action`() {
        assertEquals(
            2,
            Regex("onUpdate\\s*=\\s*::checkAndDownloadUpdate").findAll(source).count(),
            "Both visible update buttons must use the same action.",
        )
        // Two checks: the buttons' one, and the quiet background one that only lights the header
        // chip — which itself calls the buttons' action. Anything more is a path that can diverge.
        assertEquals(
            2,
            Regex("releaseUpdater\\s*\\.check\\(").findAll(source).count(),
            "A duplicated check path can diverge and leave one button broken.",
        )
        // And one download: the background check must never install anything on its own.
        assertEquals(1, Regex("\\.downloadAndLaunch\\(").findAll(source).count())
        assertTrue(source.contains(".downloadAndLaunch(result.release)"))
    }
}

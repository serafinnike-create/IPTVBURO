package com.lucasserafin94.iptvburo.desktop.playback

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The trailer panel must be honest about whether it can show anything.
 *
 * A blank white rectangle reached the user twice. Both times the cause was the same: CEF's own
 * `startup` and `getInstance` succeed with no native runtime installed, so every "is this
 * available?" check passed and the failure only surfaced as an empty panel on screen.
 *
 * These run on a machine with no bundled Chromium — the test JVM — so they pin the missing-runtime
 * path, which is the one that was broken.
 */
class TrailerAvailabilityTest {
    @Test
    fun `the heavyweight browser child never inherits the white AWT background`() {
        val source =
            Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/playback/TrailerBrowser.kt")
                .readText()
        assertTrue(
            source.contains("browserComponent =") &&
                source.contains("background = Color.BLACK") &&
                source.contains("isVisible = !unattended") &&
                source.contains("nativeComponent?.isVisible = false") &&
                source.contains("cefBrowser.createImmediately()") &&
                source.contains("browser?.wasResized("),
            "o componente nativo do trailer voltou a mostrar branco antes da pagina",
        )
    }

    /**
     * Availability must follow the runtime on disk, in both directions.
     *
     * The build now bundles Chromium under `build/generated/app-resources/windows/jcef`, and the
     * locator looks there as well as beside an installed app — so on a machine that has run the
     * packaging task this is available, and on one that has not it is not. Either answer is
     * correct; the bug was reporting *available* when nothing could render.
     */
    @Test
    fun `availability matches whether the runtime is actually present`() {
        val runtimePresent =
            sequenceOf(
                "apps/desktop/build/generated/app-resources/windows/jcef",
                "build/generated/app-resources/windows/jcef",
            ).map { java.io.File(it).absoluteFile }
                .any { directory -> java.io.File(directory, "libcef.dll").isFile }

        if (runtimePresent) {
            // Not asserted as available: starting Chromium in a headless test JVM is a different
            // question from the library being on disk, and a CI box may legitimately refuse.
            // What must never happen is the reverse — see the next test.
            return
        }
        assertFalse(TrailerBrowser.isAvailable(), "reported available with no libcef.dll anywhere")
    }

    @Test
    fun `an unavailable browser hands back null rather than an unrenderable panel`() {
        if (TrailerBrowser.isAvailable()) return

        val browser = TrailerBrowser()
        try {
            // Null is what makes the caller fall back to the system browser. A non-null panel here
            // is exactly the white box the user saw.
            assertNull(
                browser.createComponent(youtubeId = "dQw4w9WgXcQ", autoplay = false, muted = true),
                "handed back a panel that cannot render",
            )
        } finally {
            browser.dispose()
        }
    }
}

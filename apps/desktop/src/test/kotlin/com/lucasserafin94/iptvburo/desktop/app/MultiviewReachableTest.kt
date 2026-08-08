package com.lucasserafin94.iptvburo.desktop.app

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Multiview is reachable, and its failures are visible.
 *
 * The feature was complete, tested, and did nothing when pressed — for three separate reasons, each
 * of which produced the same symptom: the screen simply did not change.
 *
 * 1. The capability manifest said `multiview: false`, so no button ever rendered.
 * 2. The single-title player composed *after* the overlay, drawing on top of it — pressing "watch
 *    together" while a channel was playing changed nothing visible.
 * 3. An overlay with no resolvable tiles returned without rendering anything at all.
 *
 * Each is pinned here, because a feature that silently does nothing is indistinguishable from one
 * that was never built.
 */
class MultiviewReachableTest {
    private val appSource: String =
        Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/DesktopApp.kt").readText()

    private val overlaySource: String =
        Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/playback/MultiviewOverlay.kt")
            .readText()

    private val stateSource: String =
        Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/DesktopAppState.kt").readText()

    private val manifest: String =
        Path.of("../../packages/platform-capabilities/windows-preview.json").readText()

    @Test
    fun `the capability manifest enables multiview`() {
        // The gate that hid the whole feature. Every button is conditional on this.
        assertTrue(
            Regex(""""multiview"\s*:\s*true""").containsMatchIn(manifest),
            "multiview is disabled in the manifest, so no button will render",
        )
    }

    /**
     * Opening multiview closes the single player.
     *
     * Without this the player draws on top and the overlay is invisible behind it — the button
     * appears to do nothing at all, which is exactly how this was reported.
     */
    @Test
    fun `opening multiview dismisses the single player`() {
        assertTrue(
            Regex("""multiviewOpen\)\s*\{\s*\n\s*if \(appState\.multiviewOpen\) activePlayback = null""")
                .containsMatchIn(appSource),
            "opening multiview must close the single-title player",
        )
    }

    /**
     * An overlay with nothing to show says so.
     *
     * `if (tiles.isEmpty()) return` rendered nothing, leaving the app looking untouched. Tiles are
     * dropped when a stream cannot be resolved, which is a real failure worth reporting rather than
     * hiding behind an unchanged screen.
     */
    @Test
    fun `an empty multiview explains itself instead of vanishing`() {
        assertTrue(
            overlaySource.contains("MultiviewUnavailable(onClose = onClose)"),
            "an empty overlay must render an explanation",
        )
        assertFalse(
            Regex("""if \(tiles\.isEmpty\(\)\) return\s*\n""").containsMatchIn(overlaySource),
            "the silent early return is what made this look broken",
        )
    }

    @Test
    fun `dropped tiles are logged`() {
        // When every channel drops, the overlay is empty and the cause is invisible. Only the
        // reason is logged, never a stream URL — those carry the subscription's credentials.
        assertTrue(stateSource.contains("multiview: channel no longer in catalogue"))
        assertTrue(stateSource.contains("multiview: could not resolve a stream"))

        Regex("""println\("multiview: [^"]*"\)""").findAll(stateSource).forEach { match ->
            assertFalse(match.value.contains("$"), "a multiview log must not interpolate anything")
        }
    }

    @Test
    fun `both entry points exist`() {
        val workspace =
            Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/XtreamWorkspace.kt")
                .readText()

        // Adding a channel, from its detail page.
        assertTrue(workspace.contains("onToggleMultiview"), "no way to add a channel")
        // And opening the grid, from the live toolbar once something is queued.
        assertTrue(workspace.contains("onOpenMultiview"), "no way to open the grid")
    }

    @Test
    fun `multiview is offered on live channels only`() {
        val workspace =
            Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/XtreamWorkspace.kt")
                .readText()

        // Four films playing at once is not a thing anybody wants, and four decoders is the
        // heaviest thing this app can be asked to do.
        assertTrue(
            workspace.contains("selectedType == XtreamContentType.LIVE && multiviewCount > 0"),
            "the open button must be live-only",
        )
        assertTrue(
            workspace.contains("item.contentType == XtreamContentType.LIVE && onToggleMultiview != null"),
            "the add button must be live-only",
        )
    }

    @Test
    fun `the cap is four tiles`() {
        assertTrue(overlaySource.contains("MULTIVIEW_MAX_TILES = 4"))
        assertTrue(
            stateSource.contains("MAX_MULTIVIEW_TILES"),
            "the state holder must enforce the same cap the overlay documents",
        )
    }

    /**
     * Exactly one tile carries sound.
     *
     * Four soundtracks at once is noise. It has to be done at the engine rather than in the UI: a
     * muted Compose surface still leaves four decoders pulling audio.
     */
    @Test
    fun `only one tile has audio`() {
        assertTrue(overlaySource.contains("controller.setVolume(if (hasAudio) 1.0 else 0.0)"))
        assertTrue(overlaySource.contains("onFocus = { audioIndex = index }"), "clicking moves sound")
    }
}

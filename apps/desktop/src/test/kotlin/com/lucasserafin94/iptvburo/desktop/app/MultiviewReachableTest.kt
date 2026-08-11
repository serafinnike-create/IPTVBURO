package com.lucasserafin94.iptvburo.desktop.app

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
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
            overlaySource.contains("MultiviewUnavailable(onClose = onClose"),
            "an empty overlay must render an explanation",
        )
        // And the right one of two. Telling somebody who queued nothing that "the channels did not
        // respond" is nonsense, and telling somebody whose channels failed to "choose channels
        // first" sends them to do something they already did.
        assertTrue(
            overlaySource.contains("nothingQueued = queuedCount == 0"),
            "the two empty cases must be told apart",
        )
        assertFalse(
            Regex("""if \(tiles\.isEmpty\(\)\) return\s*\n""").containsMatchIn(overlaySource),
            "the silent early return is what made this look broken",
        )
    }

    @Test
    fun `dropped tiles are logged`() {
        // When every channel drops, the overlay is empty and the cause is invisible.
        assertTrue(stateSource.contains("multiview: channel no longer in catalogue"))
        assertTrue(stateSource.contains("multiview: could not resolve a stream"))

        // Counts may be interpolated; anything that could carry an address may not. A stream URL in
        // a log the customer can read and send on hands over the subscription's credentials, which
        // is the one thing these lines must never do.
        val forbidden = listOf("uri", "url", "stream", "request", "providerId", "streamUri")
        Regex("""println\("multiview: [^"]*"\)""").findAll(stateSource).forEach { match ->
            forbidden.forEach { term ->
                assertFalse(
                    match.value.contains("$$term", ignoreCase = true),
                    "a multiview log interpolates $term: ${match.value}",
                )
            }
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
        //
        // The toolbar button is no longer gated on a count — it appears whenever the live tab is
        // open, because gating it meant nothing announced the feature until the user had already
        // found it elsewhere. Still live-only.
        assertTrue(
            workspace.contains("if (selectedType == XtreamContentType.LIVE) {"),
            "the open button must be live-only",
        )
        assertTrue(
            workspace.contains("item.contentType == XtreamContentType.LIVE"),
            "the add button must be live-only",
        )
    }

    /**
     * The feature announces itself.
     *
     * Every piece worked, and nobody could use it: the only way in was a button inside a channel's
     * detail page, and the toolbar chip that opens the grid appeared only once something had already
     * been queued. A user had to already know multiview existed in order to find it.
     */
    @Test
    fun `multiview is discoverable without opening a channel`() {
        val workspace =
            Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/XtreamWorkspace.kt")
                .readText()

        // On the card, in the grid.
        assertTrue(
            Regex("""XtreamCatalogCard\([\s\S]{0,1200}onToggleMultiview =""").containsMatchIn(workspace),
            "the grid card must offer adding to multiview",
        )
        // And named in the toolbar before anything is queued.
        assertTrue(workspace.contains("multiviewHint"), "the toolbar must say what this is")
    }

    /**
     * Opening with nothing queued explains rather than doing nothing.
     *
     * A button that does not respond teaches nothing. The overlay says channels must be added.
     */
    @Test
    fun `opening an empty multiview still opens`() {
        val body = stateSource.substringAfter("fun openMultiview()").substringBefore("\n    fun ")

        assertTrue(body.contains("multiviewOpen = true"), "openMultiview must open something")
        // The guard that made an empty press do nothing at all, teaching the user nothing.
        assertFalse(
            Regex("""if \([^)]*(isNotEmpty\(\)|queued > 0)[^)]*\) multiviewOpen = true""")
                .containsMatchIn(body),
            "opening must not be conditional on something already being queued",
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

    @Test
    fun `window controls remain reachable with four channel names`() {
        val bar = overlaySource.substringAfter("private fun MultiviewBar(")
        val controls = bar.indexOf("onClick = onToggleFullScreen")
        val channelNames = bar.indexOf("tiles.forEach")

        assertTrue(controls >= 0, "the multiview bar must expose a full-screen control")
        assertTrue(channelNames >= 0, "the multiview bar must expose the channel audio controls")
        assertTrue(
            controls < channelNames,
            "fixed window controls must be laid out before channel names can consume the width",
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
        assertTrue(
            overlaySource.contains("if (tile.providerId == audioProviderId) 1.0 else 0.0"),
            "sound must be set at the engine: a muted Compose surface still leaves four decoders " +
                "pulling audio and the user hears whichever wins",
        )
        assertTrue(
            overlaySource.contains("requestedAudioProviderId = providerId"),
            "clicking must move sound by stable channel identity",
        )
    }

    @Test
    fun `removing a displayed tile uses its identity rather than its filtered index`() {
        assertTrue(overlaySource.contains("onRemoveTile: (String) -> Unit"))
        assertFalse(
            appSource.contains("multiviewChannelIds.getOrNull(index)"),
            "an unresolvable queued channel shifts indexes and would remove the wrong visible tile",
        )
    }

    /**
     * The grid is one embedded surface, not one per tile.
     *
     * This is the fault that made multiview look broken for days. Each embedded AWT component is
     * composited on its own layer above the Compose scene, and several of them do not lay out
     * against one another: with two channels one played and the other was a black rectangle over
     * half the screen, and with four the number that worked varied between attempts.
     *
     * Every fix before this one addressed a real bug and none of them was that.
     */
    @Test
    fun `the grid uses a single embedded surface`() {
        val surface = Path
            .of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/playback/MultiviewSurface.kt")
            .readText()

        assertEquals(
            1,
            Regex("""SwingPanel\(""").findAll(overlaySource).count(),
            "more than one embedded panel is what caused the tiles to overlap",
        )
        assertTrue(surface.contains("GridLayout"), "AWT must own the arrangement")
    }

    /**
     * Adding a channel does not restart the ones already playing.
     *
     * Restarting a live stream costs several seconds and drops the viewer out of whatever they were
     * watching, so a fourth tile must not interrupt the other three.
     */
    @Test
    fun `existing players survive a rebuild`() {
        val surface = Path
            .of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/playback/MultiviewSurface.kt")
            .readText()

        assertTrue(
            surface.contains("mounted.getOrPut(tile.providerId)"),
            "a player already mounted must be reused rather than recreated",
        )
    }
}

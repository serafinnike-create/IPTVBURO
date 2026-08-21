package com.lucasserafin94.iptvburo.desktop.playback

import com.lucasserafin94.iptvburo.desktop.ui.DesktopStrings
import com.lucasserafin94.iptvburo.desktop.ui.ScreenStrings
import com.lucasserafin94.iptvburo.desktop.user.DesktopLanguage
import java.awt.Canvas
import java.awt.Color
import java.awt.Component
import java.awt.GridLayout
import javax.swing.JPanel

/**
 * One native surface holding every multiview tile.
 *
 * ## Why the grid is built in AWT rather than in Compose
 *
 * Each tile needs a real window handle: VLC is given `--drawable-hwnd` and draws into it directly.
 * The obvious arrangement — a Compose grid with a `SwingPanel` per cell — does not work. An embedded
 * AWT component is composited on its own layer above the Compose scene, and several of them do not
 * lay out against each other: the second panel covers the first instead of taking half the space.
 *
 * That is what produced the reported symptom exactly. With two channels, one played and the other
 * was a black rectangle over half the screen; with four, the number that worked varied. It looked
 * like players failing to start, and it was really panels overlapping.
 *
 * A single `SwingPanel` containing an AWT `GridLayout` avoids it entirely. One embedded surface,
 * whose children are laid out by AWT — which has arranged sibling components correctly for thirty
 * years. Compose draws the chrome above it and never has to share the layer.
 *
 * ## Why the tiles are bare canvases
 *
 * Compose cuts a hole in its scene for the one component given to `SwingPanel`, and no deeper. With
 * a wrapper `JPanel` per tile the video canvases were grandchildren inside that hole: Compose went
 * on painting its own surface over the region and every tile stayed black, while VLC reported
 * `playing` and the sound came through. That combination — audio fine, picture missing, four valid
 * window handles at the right sizes — is what identified it as compositing rather than decoding.
 *
 * Each canvas is therefore added to the grid directly, making it a direct child of the interop
 * component: the same arrangement the single-title player has always used.
 *
 * ## What this deliberately does not do
 *
 * No borders, no titles, no focus ring. Those are Compose's, drawn over this panel, because AWT
 * painting under a live video surface is unreliable and because the app's design system lives in
 * Compose. This owns nothing but the arrangement of the video rectangles.
 */
class MultiviewSurface {
    /**
     * The gaps between tiles are this panel's own background showing through.
     *
     * Near-black rather than white. A GridLayout's gaps are simply the container behind them, and
     * the default light panel background drew bright lines through the middle of the video — at four
     * pixels they read as a frame around each tile and pulled the eye away from what was playing.
     * A grid of moving pictures needs the seams to disappear.
     */
    private val panel = JPanel().apply { background = SEAM_COLOUR_AWT }

    /**
     * The players currently mounted, with the component each one draws into.
     *
     * The component is held alongside the player because it must be created exactly once. A player
     * latches internally on its first surface: handed a second canvas it never starts on it, and the
     * tile stays black for ever. Calling `createComponent` on every rebuild did precisely that — the
     * first tile kept working because its player and canvas still matched, and the newly added one
     * came up black.
     */
    private val mounted = LinkedHashMap<String, MountedTile>()

    /** The current key handler, read through by every mounted tile. See the note in [sync]. */
    private var keyHandler: (Int) -> Boolean = { false }

    private data class MountedTile(
        val player: VlcDesktopPlayer,
        val component: Canvas,
    )

    /**
     * The AWT component to hand to a single `SwingPanel`.
     *
     * One instance for the whole overlay. Handing out several would reintroduce the layering problem
     * this class exists to avoid.
     */
    fun component(): JPanel = panel

    /**
     * Brings the grid in line with [tiles].
     *
     * Players already mounted are kept rather than recreated: restarting a live stream costs several
     * seconds and drops the viewer out of whatever they were watching, so adding a fourth channel
     * must not interrupt the three already playing.
     *
     * @param onTileClicked receives the provider id of the tile that was pressed, for moving audio
     */
    fun sync(
        tiles: List<MultiviewTile>,
        onTileClicked: (String) -> Unit,
        /**
         * A key pressed while a tile holds the focus.
         *
         * Needed because the AWT canvases take keyboard focus the moment the pointer crosses them,
         * and from that point Compose sees no key events at all — so in full screen, where there is
         * no visible chrome to aim at, there was no way out but the mouse.
         *
         * @return true when the key was handled and should not reach VLC
         */
        onKey: (Int) -> Boolean = { false },
        /** The engine's failure messages, in the language the app is running in. */
        text: ScreenStrings =
            DesktopStrings.of(DesktopLanguage.PORTUGUESE_BRAZIL).shareStrings.screens,
    ) {
        // Stored rather than captured, because the components are created once and kept: a tile
        // mounted before full screen was entered would otherwise hold the old handler for ever, and
        // pressing a key over it would do nothing.
        keyHandler = onKey
        val wanted = tiles.map(MultiviewTile::providerId).toSet()

        // Gone first, so their windows are released before the layout is recomputed.
        mounted.keys.toList()
            .filterNot { id -> id in wanted }
            .forEach { id ->
                mounted.remove(id)?.let { entry ->
                    // Stop VLC before its drawable disappears. Only the tile that left is detached;
                    // every surviving canvas keeps the same native peer and therefore the same HWND.
                    entry.player.dispose()
                    panel.remove(entry.component)
                }
            }

        panel.layout = multiviewGridLayout(tiles.size)

        // Build the desired order from stable entries. The helper below moves existing children in
        // place instead of removing them, because detaching a heavyweight Canvas destroys its HWND.
        val orderedComponents = tiles.mapIndexed { index, tile ->
            // Created once per channel and kept. Existing components remain attached while a new
            // one starts beside them, so their players and native handles survive a grid rebuild.
            val entry =
                mounted.getOrPut(tile.providerId) {
                    // Four simultaneous software decoders can saturate the CPU and make healthy
                    // streams lose video one by one. Multiview therefore lets VLC select the
                    // available Windows hardware decoder; the single-title player keeps its
                    // compatibility-first software default.
                    val player =
                        VlcDesktopPlayer(
                            hardwareDecoding = VlcHardwareDecoding.AUTOMATIC,
                            startupDelayMillis = multiviewStartupDelay(index),
                            // A deeper buffer than a single title needs. Four streams share one
                            // connection, and the log showed each tile starving and recovering
                            // seconds later — which is what the blinking actually was.
                            networkCachingMillis = MULTIVIEW_NETWORK_CACHING_MILLIS,
                            // The tile's position, so four players can be told apart in the log.
                            // A number, never the channel — a provider id can carry an address.
                            logTag = "tile${index + 1}",
                            text = text,
                        )
                    MountedTile(
                        player = player,
                        component =
                            player.createCanvas(
                                request = tile.request,
                                onClick = { onTileClicked(tile.providerId) },
                                // Indirect, so the handler can change without recreating the
                                // component — which would restart the player behind it.
                                onKey = { code -> keyHandler(code) },
                            ),
                    )
                }
            entry.component
        }

        // Never remove a surviving canvas merely to reorder the grid. Container.remove/removeAll
        // calls removeNotify on heavyweight children and destroys their native peers. VLC was then
        // left drawing to the old --drawable-hwnd, which made every tile black after full screen or
        // a grid rebuild. setComponentZOrder moves a child inside the same parent without doing so.
        synchronizeMultiviewComponents(panel, orderedComponents)

        panel.revalidate()
        panel.repaint()
    }

    /** Which player owns a tile, so the overlay can mute every one but the focused tile. */
    fun playerFor(providerId: String): VlcDesktopPlayer? = mounted[providerId]?.player

    /** Shuts every player down. Called when the overlay closes. */
    fun dispose() {
        mounted.values.forEach { entry -> entry.player.dispose() }
        mounted.clear()
        panel.removeAll()
    }

    companion object {
        /**
         * The seam colour as Compose sees it.
         *
         * Both sides must agree, because `SwingPanel` assigns its `background` onto the AWT panel
         * rather than painting behind it: two separate constants would let the Compose side silently
         * overwrite the AWT one, which is exactly how the dividers came to be white.
         */
        val SEAM_COLOUR: androidx.compose.ui.graphics.Color =
            androidx.compose.ui.graphics.Color(0xFF222426)

        /**
         * Two pixels, not eight.
         *
         * Enough to see where one picture ends and the next begins, and no more. At eight the seams
         * were a visible grid drawn over the video; the job of a divider here is to be noticed only
         * when looked for.
         */
        const val GRID_GAP = 2

        /**
         * The same colour for AWT, derived rather than repeated.
         *
         * One step above the canvas: not pure black, which would make adjacent dark scenes bleed
         * into one another, and nowhere near white, which is what made the old dividers shout.
         */
        internal val SEAM_COLOUR_AWT: Color =
            Color(
                (SEAM_COLOUR.red * 255).toInt(),
                (SEAM_COLOUR.green * 255).toInt(),
                (SEAM_COLOUR.blue * 255).toInt(),
            )
    }
}

/**
 * The grid shape for [tileCount] tiles.
 *
 * Two columns from two tiles up: a 1x2 strip on a widescreen monitor wastes half the display on
 * letterboxing, and a 2x2 grid is how people actually arrange screens for sport. Three tiles leaves
 * the fourth cell empty rather than reflowing into a row of three narrow strips — the fourth channel
 * is usually seconds away.
 *
 * Rows are passed as zero deliberately. `GridLayout` treats rows as authoritative whenever both
 * arguments are non-zero and recomputes the columns from the number of children, ignoring the value
 * given; passing zero rows makes the column count the one that is honoured. That is documented
 * behaviour, and it is not visible from reading arithmetic that looks correct on its own.
 */
/**
 * Two pixels, not eight.
 *
 * Enough to see where one picture ends and the next begins, and no more. At eight the seams were a
 * visible grid drawn over the video; the job of a divider here is to be noticed only when looked for.
 */
private const val GRID_GAP = 2

internal fun multiviewGridLayout(tileCount: Int): GridLayout =
    GridLayout(0, if (tileCount <= 1) 1 else 2, GRID_GAP, GRID_GAP)

/** Staggers provider requests while keeping the complete four-tile start under three seconds. */
internal fun multiviewStartupDelay(index: Int): Long =
    index.coerceIn(0, MULTIVIEW_LAST_INDEX) * MULTIVIEW_START_INTERVAL_MILLIS

private const val MULTIVIEW_START_INTERVAL_MILLIS = 750L
private const val MULTIVIEW_LAST_INDEX = 3

/**
 * Makes [panel]'s children match [desired] without detaching components that are still wanted.
 *
 * Heavyweight AWT canvases own the native HWND passed to VLC. Removing and re-adding one is not a
 * harmless layout operation: it destroys that HWND. This helper deliberately removes only children
 * that disappeared and uses `setComponentZOrder` for survivors.
 */
internal fun synchronizeMultiviewComponents(
    panel: JPanel,
    desired: List<Component>,
) {
    val wanted = desired.toSet()
    panel.components
        .filterNot { component -> component in wanted }
        .forEach(panel::remove)

    desired.forEachIndexed { index, component ->
        when {
            component.parent !== panel -> panel.add(component, index)
            panel.getComponentZOrder(component) != index -> panel.setComponentZOrder(component, index)
        }
    }
}

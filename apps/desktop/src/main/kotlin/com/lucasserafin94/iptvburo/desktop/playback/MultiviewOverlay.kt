package com.lucasserafin94.iptvburo.desktop.playback

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.lucasserafin94.iptvburo.desktop.ui.strings
import com.lucasserafin94.iptvburo.desktop.ui.BuroColors
import com.lucasserafin94.iptvburo.desktop.ui.BuroInteractiveRow
import com.lucasserafin94.iptvburo.desktop.ui.BuroRadius
import com.lucasserafin94.iptvburo.desktop.ui.BuroSpacing

/** One occupied cell of the grid. */
data class MultiviewTile(
    /** The queued identity. Titles are not unique and failed tiles make list indexes unstable. */
    val providerId: String,
    val request: DesktopPlaybackRequest,
    val title: String,
)

/**
 * Watches up to four live channels at once.
 *
 * This is the feature no mainstream IPTV player does well, and it is why the desktop build carries
 * its own VLC process per tile rather than one shared engine: VLC's HTTP control interface is
 * per-process, so a shared instance could not be told to mute one stream and not another.
 *
 * **Exactly one tile has audio.** Four simultaneous soundtracks is noise, not a feature, so the
 * focused tile owns the audio and every other tile is muted at the engine. Clicking a tile moves
 * both the highlight and the sound.
 *
 * Tiles are keyed by their content key so that adding or removing a channel does not tear down and
 * restart the players that were already running — restarting a live stream costs seconds and drops
 * the user out of whatever they were watching.
 *
 * ## How it is reached
 *
 * Each live channel's detail page offers "add to multiview"; once anything is queued, a chip in the
 * live toolbar opens this. Both are gated on `multiviewSupported` in the platform capability
 * manifest, which is what kept the whole feature invisible for a while: the code was complete and
 * the manifest said `false`, so no button ever rendered and multiview looked unimplemented.
 */
@Composable
fun MultiviewOverlay(
    tiles: List<MultiviewTile>,
    onClose: () -> Unit,
    onRemoveTile: (String) -> Unit,
    /** How many channels the user queued, which may exceed [tiles] when some failed to resolve. */
    queuedCount: Int = tiles.size,
    /**
     * Whether the window is borderless full screen, and how to switch.
     *
     * Four matches at once is precisely when somebody wants the whole monitor, and the single-title
     * player has offered this since the beginning — its absence here read as an oversight rather
     * than a decision.
     */
    isFullScreen: Boolean = false,
    onToggleFullScreen: () -> Unit = {},
) {
    // An empty overlay says so rather than vanishing.
    //
    // Returning silently left the app looking exactly as it did before: the user pressed a button,
    // the screen did not change, and there was nothing to act on. Tiles are dropped when a stream
    // URL cannot be resolved, which is a real failure the customer needs told about — not one to
    // hide by rendering nothing.
    if (tiles.isEmpty()) {
        // Two different empties, and telling them apart matters. Nothing queued is a user who has
        // not learnt how yet and needs telling; queued but unplayable is a genuine failure. Showing
        // "the channels did not respond" to somebody who chose none would be nonsense.
        MultiviewUnavailable(onClose = onClose, nothingQueued = queuedCount == 0)
        return
    }
    var requestedAudioProviderId by remember { mutableStateOf<String?>(null) }

    // What each tile is actually doing, polled for the badge in the bar.
    //
    // A tile that is loading, a tile whose stream the provider closed, and a tile that is simply
    // dark all render as the same black rectangle. That is what made a grid coming up three-of-four
    // impossible to interpret from the screen alone — and it is a question the customer will ask
    // long before they ask for a log.
    var tileStates by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    val audioProviderId =
        requestedAudioProviderId?.takeIf { selected -> tiles.any { it.providerId == selected } }
            ?: tiles.first().providerId

    Box(modifier = Modifier.fillMaxSize().background(BuroColors.Canvas)) {
        Column(modifier = Modifier.fillMaxSize()) {
            MultiviewBar(
                tiles = tiles,
                audioProviderId = audioProviderId,
                onSelectAudio = { providerId -> requestedAudioProviderId = providerId },
                isFullScreen = isFullScreen,
                onToggleFullScreen = onToggleFullScreen,
                onClose = onClose,
                // Which tiles have a picture. Shown in the bar rather than over the video: the
                // tiles are an embedded AWT surface that composites above the Compose scene, so
                // anything drawn on top of them is simply not visible.
                playingByProvider = tileStates,
            )

            // One embedded surface for the whole grid, not one per tile.
            //
            // A SwingPanel per cell does not work: each embedded AWT component is composited on its
            // own layer above the Compose scene, and several of them do not lay out against one
            // another — the second covers the first instead of taking half the space. With two
            // channels one played and the other was a black rectangle over half the screen; with
            // four, the number that worked varied between attempts.
            //
            // AWT has arranged sibling components correctly for thirty years. Letting it own the
            // arrangement, inside a single embedded panel, removes the problem rather than working
            // around it.
            val surface = remember { MultiviewSurface() }
            // Read here, in composable scope; the effect below is not one.
            val screenText = strings.shareStrings.screens
            val currentIsFullScreen = rememberUpdatedState(isFullScreen)
            val currentToggleFullScreen = rememberUpdatedState(onToggleFullScreen)

            DisposableEffect(surface) {
                onDispose { surface.dispose() }
            }

            // Rebuilt whenever the set changes. Players already mounted are reused, so adding a
            // fourth channel does not restart the three that are playing.
            LaunchedEffect(surface, tiles.map(MultiviewTile::providerId)) {
                surface.sync(
                    tiles = tiles,
                    text = screenText,
                    onTileClicked = { providerId -> requestedAudioProviderId = providerId },
                    // Any key leaves full screen.
                    //
                    // Not just Escape: in full screen there is no window chrome and no visible way
                    // out, and somebody who reaches for the keyboard at that point is trying to get
                    // back — whichever key they happen to hit. Windowed, keys are left alone so they
                    // can mean something later.
                    onKey = { keyCode ->
                        if (multiviewKeyTogglesFullScreen(keyCode, currentIsFullScreen.value)) {
                            currentToggleFullScreen.value()
                            true
                        } else {
                            false
                        }
                    },
                )
            }

            // Exactly one tile carries sound. Driven at the engine because muting in Compose would
            // still leave four decoders pulling audio, and the user would hear whichever won.
            LaunchedEffect(surface, audioProviderId, tiles.size) {
                tiles.forEach { tile ->
                    surface.playerFor(tile.providerId)
                        ?.setVolume(if (tile.providerId == audioProviderId) 1.0 else 0.0)
                }
            }

            LaunchedEffect(surface, tiles.map(MultiviewTile::providerId)) {
                while (true) {
                    tileStates =
                        tiles.associate { tile ->
                            tile.providerId to
                                (surface.playerFor(tile.providerId)?.snapshot()?.playing ?: false)
                        }
                    kotlinx.coroutines.delay(TILE_STATE_POLL_MILLIS)
                }
            }

            // No padding around the surface, and the box painted behind it.
            //
            // Padding here does not tint the gap — it exposes whatever Compose draws underneath,
            // which is why a pale border framed the grid however dark the AWT panel was made. The
            // seams between tiles belong to the panel's own background; the edge belongs to this.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(BuroColors.Canvas),
            ) {
                SwingPanel(
                    // The seams are this, and nothing else.
                    //
                    // SwingPanel's `background` defaults to Color.White, and it does not merely sit
                    // behind the component — it assigns it, overwriting whatever the panel set for
                    // itself on every recomposition. Darkening the panel in its own constructor was
                    // therefore undone immediately, which is why the dividers stayed white through
                    // several attempts at fixing them somewhere else.
                    background = MultiviewSurface.SEAM_COLOUR,
                    factory = { surface.component() },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/**
 * Whether a key pressed over a tile should return the window to its normal size.
 *
 * Any key, not a chosen few. Borderless full screen hides the window controls and the app's own
 * chrome, so somebody who wants out has nothing to aim at; they reach for the keyboard and press
 * whatever comes first. Escape alone would leave anyone who tried Space or Backspace still trapped.
 *
 * Modifiers are the exception. Ctrl, Shift and Alt are pressed on the way to a shortcut rather than
 * as a request, and dropping out of full screen while somebody holds Alt for Alt+Tab would fight
 * them. Windowed, nothing is claimed at all — keys stay free to mean something later.
 */
internal fun multiviewKeyLeavesFullScreen(keyCode: Int, isFullScreen: Boolean): Boolean {
    if (!isFullScreen) return false
    return keyCode !in MODIFIER_KEYS
}

/** F11 also enters full screen, so the action remains reachable when the title bar is crowded. */
internal fun multiviewKeyTogglesFullScreen(keyCode: Int, isFullScreen: Boolean): Boolean =
    if (isFullScreen) {
        multiviewKeyLeavesFullScreen(keyCode, isFullScreen = true)
    } else {
        keyCode == java.awt.event.KeyEvent.VK_F11
    }

/**
 * How often the bar re-reads what each tile is doing.
 *
 * Twice a second: fast enough that the badge tracks a stall as it happens, slow enough that four
 * players are not interrogated on every frame.
 */
private const val TILE_STATE_POLL_MILLIS = 500L

private val MODIFIER_KEYS =
    setOf(
        java.awt.event.KeyEvent.VK_CONTROL,
        java.awt.event.KeyEvent.VK_SHIFT,
        java.awt.event.KeyEvent.VK_ALT,
        java.awt.event.KeyEvent.VK_ALT_GRAPH,
        java.awt.event.KeyEvent.VK_META,
        java.awt.event.KeyEvent.VK_WINDOWS,
        java.awt.event.KeyEvent.VK_CAPS_LOCK,
        java.awt.event.KeyEvent.VK_NUM_LOCK,
    )

@Composable
private fun MultiviewBar(
    tiles: List<MultiviewTile>,
    audioProviderId: String,
    onSelectAudio: (String) -> Unit,
    isFullScreen: Boolean,
    onToggleFullScreen: () -> Unit,
    onClose: () -> Unit,
    /** Which tiles currently have a picture, by provider id. Absent means not yet known. */
    playingByProvider: Map<String, Boolean> = emptyMap(),
) {
    val text = strings
    // Which channel takes the sound, as a menu rather than a label.
    //
    // The bar said "🔊 A&E FHD" and that was all it was — text. Pressing it did nothing, which is
    // exactly what somebody trying to change the audio would press first.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .background(BuroColors.Surface)
            .padding(horizontal = BuroSpacing.Lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "MULTIVIEW",
            color = BuroColors.Primary,
            style = MaterialTheme.typography.labelSmall,
        )
        Spacer(Modifier.width(BuroSpacing.Md))
        Text(
            text = "${tiles.size}",
            color = BuroColors.TextMuted,
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(Modifier.width(BuroSpacing.Md))

        BuroInteractiveRow(
            onClick = onToggleFullScreen,
            selected = false,
            shape = BuroRadius.Small,
            contentDescription =
                if (isFullScreen) text.settingsText.multiviewWindowed else text.settingsText.multiviewFullScreen,
        ) {
            Text(
                text = if (isFullScreen) "⤡" else "⛶",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                color = BuroColors.Text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
        }

        Spacer(Modifier.width(BuroSpacing.Sm))
        BuroInteractiveRow(
            onClick = onClose,
            selected = false,
            shape = BuroRadius.Small,
            contentDescription = strings.close,
        ) {
            Text(
                text = "✕",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                color = BuroColors.Text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(BuroSpacing.Md))

        // One button per channel, laid out in the bar — not a dropdown.
        //
        // A menu opened downwards from here lands on the video, and the video is an embedded AWT
        // surface: it composites above the Compose scene, so the menu was drawn and immediately
        // hidden behind it. The same DropdownMenu works in the single-title player only because its
        // controls sit at the bottom and it opens upwards into Compose space.
        //
        // Four buttons is the whole set — the cap is four tiles — so a menu was never buying much
        // anyway, and this way the choice is visible without a press. The one carrying sound is
        // marked, which also answers "which am I hearing?" at a glance.
        // Keep the window controls outside the flexible channel-name region. Four intrinsic title
        // widths previously pushed full-screen and close completely off the right edge.
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text.settingsText.multiviewAudioFrom,
                color = BuroColors.TextSubtle,
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(Modifier.width(BuroSpacing.Xs))

            tiles.forEach { tile ->
                val carriesAudio = tile.providerId == audioProviderId
                BuroInteractiveRow(
                    onClick = { onSelectAudio(tile.providerId) },
                    selected = carriesAudio,
                    modifier = Modifier.width(110.dp),
                    shape = BuroRadius.Small,
                    contentDescription = tile.title,
                ) { state ->
                    // A dot for a tile with no picture.
                    //
                    // Small and unobtrusive when everything is fine, and the difference between "the
                    // app is broken" and "that channel stopped sending" when it is not — which the
                    // customer cannot tell from a black rectangle, and neither could I.
                    val stalled = playingByProvider[tile.providerId] == false
                    Text(
                        text =
                            when {
                                carriesAudio -> "🔊  ${tile.title}"
                                stalled -> "•  ${tile.title}"
                                else -> tile.title
                            },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        color = when {
                            carriesAudio -> BuroColors.Primary
                            // Dimmed rather than red. A channel that stops sending for a few
                            // seconds is ordinary on live television, and an alarm colour every
                            // time would train the user to ignore it.
                            stalled -> BuroColors.TextSubtle
                            state.active -> BuroColors.Text
                            else -> BuroColors.TextMuted
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (carriesAudio) FontWeight.Bold else FontWeight.Normal,
                        // Four channel names have to share the bar with two buttons, and a long name
                        // must shorten rather than push the close button off the edge.
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false,
                    )
                }
                Spacer(Modifier.width(BuroSpacing.Xxs))
            }
        }

    }
}

/**
 * The bar alone, for tests that need to measure it without starting a video engine.
 *
 * The controls have gone missing twice — once behind four intrinsic channel widths that pushed them
 * off the right edge, and once because a build without the fix was installed. Reading the code
 * proved nothing either time; only composing it at a real width did.
 */
@Composable
internal fun MultiviewBarForTesting(
    tiles: List<MultiviewTile>,
    audioProviderId: String,
    isFullScreen: Boolean = false,
    onSelectAudio: (String) -> Unit = {},
    onToggleFullScreen: () -> Unit = {},
    onClose: () -> Unit = {},
) {
    MultiviewBar(
        tiles = tiles,
        audioProviderId = audioProviderId,
        onSelectAudio = onSelectAudio,
        isFullScreen = isFullScreen,
        onToggleFullScreen = onToggleFullScreen,
        onClose = onClose,
    )
}

/**
 * Shown when every queued channel failed to produce a playable stream.
 *
 * The alternative was rendering nothing, which is what made this look like a broken button: the user
 * pressed it, the screen stayed exactly as it was, and there was no way to tell whether the feature
 * had failed or never existed.
 */
@Composable
private fun MultiviewUnavailable(onClose: () -> Unit, nothingQueued: Boolean) {
    val text = strings
    Box(
        modifier = Modifier.fillMaxSize().background(BuroColors.Canvas),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (nothingQueued) text.settingsText.multiviewEmpty else text.settingsText.multiviewUnavailable,
                color = BuroColors.Text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(BuroSpacing.Xs))
            Text(
                text = if (nothingQueued) text.settingsText.multiviewEmptyHint else text.settingsText.multiviewUnavailableHint,
                color = BuroColors.TextMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(BuroSpacing.Lg))
            BuroInteractiveRow(
                onClick = onClose,
                selected = false,
                shape = BuroRadius.Small,
                contentDescription = text.close,
            ) {
                Text(
                    text = text.close,
                    modifier = Modifier.padding(horizontal = BuroSpacing.Lg, vertical = BuroSpacing.Xs),
                    color = BuroColors.Text,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

/** Multiview is a live-TV feature; a grid of paused films is not useful. */
const val MULTIVIEW_MAX_TILES = 4

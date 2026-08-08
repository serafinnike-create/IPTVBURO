package com.lucasserafin94.iptvburo.desktop.playback

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.draw.clip
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
    onRemoveTile: (Int) -> Unit,
) {
    // An empty overlay says so rather than vanishing.
    //
    // Returning silently left the app looking exactly as it did before: the user pressed a button,
    // the screen did not change, and there was nothing to act on. Tiles are dropped when a stream
    // URL cannot be resolved, which is a real failure the customer needs told about — not one to
    // hide by rendering nothing.
    if (tiles.isEmpty()) {
        MultiviewUnavailable(onClose = onClose)
        return
    }
    var audioIndex by remember(tiles.size) { mutableStateOf(0) }

    Box(modifier = Modifier.fillMaxSize().background(BuroColors.Canvas)) {
        Column(modifier = Modifier.fillMaxSize()) {
            MultiviewBar(
                count = tiles.size,
                audioTitle = tiles.getOrNull(audioIndex)?.title.orEmpty(),
                onClose = onClose,
            )

            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                // Two columns from two tiles up. A 1x2 strip on a wide monitor wastes half the
                // screen on letterboxing, and a 2x2 grid matches how sports viewers actually
                // arrange screens.
                val columns = if (tiles.size == 1) 1 else 2
                val rows = (tiles.size + columns - 1) / columns
                val gap = BuroSpacing.Xs

                Column(
                    modifier = Modifier.fillMaxSize().padding(gap),
                    verticalArrangement = Arrangement.spacedBy(gap),
                ) {
                    for (row in 0 until rows) {
                        Row(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(gap),
                        ) {
                            for (column in 0 until columns) {
                                val index = row * columns + column
                                if (index >= tiles.size) {
                                    // Keeps the last row aligned with the one above instead of
                                    // stretching a lone tile across the full width.
                                    Spacer(Modifier.weight(1f).fillMaxHeight())
                                    continue
                                }
                                val tile = tiles[index]
                                // Live channels carry no progress identity, so the title is the
                                // stable key here. It is what distinguishes one tile from another.
                                key(tile.request.progressIdentity?.contentId ?: tile.title) {
                                    MultiviewCell(
                                        tile = tile,
                                        hasAudio = index == audioIndex,
                                        modifier = Modifier.weight(1f).fillMaxHeight(),
                                        onFocus = { audioIndex = index },
                                        onRemove = { onRemoveTile(index) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MultiviewCell(
    tile: MultiviewTile,
    hasAudio: Boolean,
    modifier: Modifier,
    onFocus: () -> Unit,
    onRemove: () -> Unit,
) {
    val controller = remember(tile.request) { VlcDesktopPlayer() }

    // Volume is driven from the engine rather than the UI: muting in Compose would still leave four
    // decoders pulling audio, and the user would hear whichever one won.
    DisposableEffect(controller, hasAudio) {
        controller.setVolume(if (hasAudio) 1.0 else 0.0)
        onDispose { }
    }
    DisposableEffect(controller) {
        onDispose { controller.dispose() }
    }

    Box(
        modifier = modifier
            .clip(BuroRadius.Medium)
            .background(BuroColors.Surface)
            .border(
                width = if (hasAudio) 2.dp else 1.dp,
                color = if (hasAudio) BuroColors.Primary else BuroColors.BorderSoft,
                shape = BuroRadius.Medium,
            ),
    ) {
        SwingPanel(
            factory = { controller.createComponent(tile.request) },
            modifier = Modifier.fillMaxSize(),
        )

        // Overlay strip. Kept to a single row so it never covers meaningful picture.
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .background(BuroColors.Canvas.copy(alpha = 0.62f))
                .padding(horizontal = BuroSpacing.Sm, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (hasAudio) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(BuroColors.Primary))
                Spacer(Modifier.width(7.dp))
            }
            Text(
                text = tile.title,
                color = BuroColors.Text,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (!hasAudio) {
                BuroInteractiveRow(
                    onClick = onFocus,
                    selected = false,
                    shape = BuroRadius.Pill,
                    contentDescription = "Audio",
                ) {
                    Text(
                        text = "🔈",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Spacer(Modifier.width(4.dp))
            }
            BuroInteractiveRow(
                onClick = onRemove,
                selected = false,
                shape = BuroRadius.Pill,
                contentDescription = "Fechar",
            ) {
                Text(
                    text = "✕",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    color = BuroColors.TextMuted,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun MultiviewBar(
    count: Int,
    audioTitle: String,
    onClose: () -> Unit,
) {
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
            text = "$count",
            color = BuroColors.TextMuted,
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(Modifier.weight(1f))
        if (audioTitle.isNotBlank()) {
            Text(
                text = "🔊  $audioTitle",
                color = BuroColors.TextMuted,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(BuroSpacing.Md))
        }
        BuroInteractiveRow(
            onClick = onClose,
            selected = false,
            shape = BuroRadius.Small,
            contentDescription = "Fechar multiview",
        ) {
            Text(
                text = "✕",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                color = BuroColors.Text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * Shown when every queued channel failed to produce a playable stream.
 *
 * The alternative was rendering nothing, which is what made this look like a broken button: the user
 * pressed it, the screen stayed exactly as it was, and there was no way to tell whether the feature
 * had failed or never existed.
 */
@Composable
private fun MultiviewUnavailable(onClose: () -> Unit) {
    val text = strings
    Box(
        modifier = Modifier.fillMaxSize().background(BuroColors.Canvas),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = text.settingsText.multiviewUnavailable,
                color = BuroColors.Text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(BuroSpacing.Xs))
            Text(
                text = text.settingsText.multiviewUnavailableHint,
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

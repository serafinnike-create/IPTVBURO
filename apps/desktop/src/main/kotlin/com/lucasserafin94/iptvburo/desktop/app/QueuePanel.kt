package com.lucasserafin94.iptvburo.desktop.app

import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lucasserafin94.iptvburo.desktop.DesktopAppState
import com.lucasserafin94.iptvburo.desktop.playback.DesktopPlaybackRequest
import com.lucasserafin94.iptvburo.desktop.ui.BuroColors
import com.lucasserafin94.iptvburo.desktop.ui.BuroInteractiveRow
import com.lucasserafin94.iptvburo.desktop.ui.BuroRadius
import com.lucasserafin94.iptvburo.desktop.ui.BuroSpacing
import com.lucasserafin94.iptvburo.desktop.ui.strings
import com.lucasserafin94.iptvburo.domain.model.QueueEntry
import com.lucasserafin94.iptvburo.domain.model.QueueMediaKind

/**
 * The playback queue, as a panel beside the music library (GDD 8 §16).
 *
 * Shows what is playing, what follows, and the row controls for reordering and removing. The panel
 * is a view over [DesktopAppState.playbackQueue]; every action goes back through the state, which
 * owns the ordering rules and is where the identities are resolved to a URI. Nothing here ever sees
 * a stream URL.
 */
@Composable
fun QueuePanel(
    appState: DesktopAppState,
    onPlay: (DesktopPlaybackRequest) -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = strings
    val queue = appState.playbackQueue

    Column(
        modifier =
            modifier
                .fillMaxHeight()
                .width(320.dp)
                .background(BuroColors.Surface)
                .padding(vertical = BuroSpacing.Md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = BuroSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text.queueTitle,
                color = BuroColors.Text,
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.width(BuroSpacing.Xs))
            if (!queue.isEmpty) {
                Text(
                    text = "${queue.size} ${text.queueCount}",
                    color = BuroColors.TextSubtle,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.weight(1f))
            if (!queue.isEmpty) {
                QueueTextAction(label = text.queueClear, onClick = appState::clearQueue)
            }
            Spacer(Modifier.width(BuroSpacing.Xxs))
            QueueTextAction(
                label = "✕",
                contentDescription = text.queueClose,
                onClick = { appState.showQueuePanel(false) },
            )
        }
        Spacer(Modifier.height(BuroSpacing.Sm))
        HorizontalDivider(color = BuroColors.BorderSoft)

        if (queue.isEmpty) {
            Box(modifier = Modifier.fillMaxSize().padding(BuroSpacing.Lg), contentAlignment = Alignment.Center) {
                Text(
                    text = text.queueEmptyBody,
                    color = BuroColors.TextSubtle,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            return@Column
        }

        // Weighted, never fillMaxSize: an unweighted child of a Column is measured against
        // unbounded height, which lays the whole list out past the bottom of the panel and puts it
        // beyond the reach of any scroll. This has been a recurring defect on this screen.
        val listState = rememberLazyListState()
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding =
                    PaddingValues(horizontal = BuroSpacing.Sm, vertical = BuroSpacing.Xs),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(
                    items = queue.entries,
                    // The handle, not the media id: queueing the same track twice is legitimate,
                    // and duplicate keys make a reorder animate the wrong rows.
                    key = { _, entry -> entry.handle },
                ) { position, entry ->
                    if (position == queue.index) {
                        QueueSectionLabel(text.queueNowPlaying)
                    } else if (position == queue.index + 1) {
                        QueueSectionLabel(text.queueUpNext)
                    }
                    QueueRow(
                        entry = entry,
                        playing = position == queue.index,
                        canMoveUp = position > 0,
                        canMoveDown = position < queue.entries.lastIndex,
                        onPlay = { appState.playQueuePosition(position)?.let(onPlay) },
                        onMoveUp = { appState.moveQueuePosition(position, position - 1) },
                        onMoveDown = { appState.moveQueuePosition(position, position + 1) },
                        // Removing the playing row advances the queue, and the state returns the
                        // request for whatever took its place so the player follows it.
                        onRemove = { appState.removeQueuePosition(position)?.let(onPlay) },
                    )
                }
            }
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(listState),
                modifier =
                    Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(vertical = BuroSpacing.Xs),
                // Explicit colours: the default track is very nearly this canvas colour, which
                // makes a long queue look unscrollable even though it is not.
                style =
                    LocalScrollbarStyle.current.copy(
                        thickness = 8.dp,
                        unhoverColor = BuroColors.BorderSoft,
                        hoverColor = BuroColors.Primary,
                    ),
            )
        }
    }
}

/** "Tocando agora" / "A seguir", so the split between the two is visible without counting rows. */
@Composable
private fun QueueSectionLabel(label: String) {
    Text(
        text = label.uppercase(),
        modifier =
            Modifier.padding(
                start = BuroSpacing.Xs,
                top = BuroSpacing.Sm,
                bottom = BuroSpacing.Xxs,
            ),
        color = BuroColors.TextSubtle,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
    )
}

/**
 * One queued row.
 *
 * The move and remove controls appear on hover rather than sitting on every row: forty rows each
 * carrying three permanent buttons reads as noise, and the queue is meant to be scanned.
 */
@Composable
private fun QueueRow(
    entry: QueueEntry,
    playing: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onPlay: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    val text = strings
    BuroInteractiveRow(
        onClick = onPlay,
        selected = playing,
        modifier = Modifier.fillMaxWidth(),
        shape = BuroRadius.Small,
        contentDescription = entry.title,
    ) { state ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(BuroSpacing.Xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(28.dp)
                        .clip(BuroRadius.Small)
                        .background(BuroColors.SurfaceRaised),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    // A station is marked, because a live entry behaves differently from a track:
                    // it never ends, so nothing queued behind it would ever play.
                    text = if (entry.kind == QueueMediaKind.RADIO) "◉" else if (playing) "▶" else "♪",
                    color = if (playing) BuroColors.Primary else BuroColors.TextSubtle,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Spacer(Modifier.width(BuroSpacing.Xs))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    color = if (playing) BuroColors.Primary else BuroColors.Text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (playing) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                entry.subtitle?.let { subtitle ->
                    Text(
                        text = subtitle,
                        color = BuroColors.TextSubtle,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (state.active) {
                if (canMoveUp) {
                    QueueTextAction(label = "▲", contentDescription = text.queueMoveUp, onClick = onMoveUp)
                }
                if (canMoveDown) {
                    QueueTextAction(label = "▼", contentDescription = text.queueMoveDown, onClick = onMoveDown)
                }
                QueueTextAction(label = "✕", contentDescription = text.queueRemove, onClick = onRemove)
            }
        }
    }
}

/** A small text-only control, the shape used by the panel's header and row actions. */
@Composable
private fun QueueTextAction(
    label: String,
    onClick: () -> Unit,
    contentDescription: String = label,
) {
    BuroInteractiveRow(
        onClick = onClick,
        selected = false,
        shape = BuroRadius.Small,
        contentDescription = contentDescription,
    ) { state ->
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = BuroSpacing.Xs, vertical = BuroSpacing.Xxs),
            color = if (state.active) BuroColors.Primary else BuroColors.TextMuted,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

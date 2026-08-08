package com.lucasserafin94.iptvburo.desktop.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.layout.fillMaxHeight
import com.lucasserafin94.iptvburo.desktop.ui.arrowScrollableList
import com.lucasserafin94.iptvburo.desktop.ui.edgeScrollableVertically
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lucasserafin94.iptvburo.desktop.DesktopContinueWatchingEntry
import com.lucasserafin94.iptvburo.desktop.ui.BuroColors
import com.lucasserafin94.iptvburo.desktop.ui.BuroInteractiveRow
import com.lucasserafin94.iptvburo.desktop.ui.BuroRadius
import com.lucasserafin94.iptvburo.desktop.ui.BuroRemoteArtwork
import com.lucasserafin94.iptvburo.desktop.ui.BuroSpacing
import com.lucasserafin94.iptvburo.desktop.ui.editorialTitle
import com.lucasserafin94.iptvburo.desktop.ui.strings

/**
 * Everything started and not finished, with the choice the user actually wants at that moment.
 *
 * Resuming and starting over are offered side by side rather than behind a dialog: the app already
 * knows where playback stopped, so asking which one on every open would be a question with an
 * obvious default and an extra click.
 */
@Composable
fun ContinueWatchingWorkspace(
    entries: List<DesktopContinueWatchingEntry>,
    onResume: (DesktopContinueWatchingEntry) -> Unit,
    onRestart: (DesktopContinueWatchingEntry) -> Unit,
    onForget: (DesktopContinueWatchingEntry) -> Unit,
) {
    val text = strings
    Column(modifier = Modifier.fillMaxSize().padding(BuroSpacing.Lg)) {
        // The heading, the "clear everything" button and the configurable title all lived here to
        // serve the history screen, which now has its own gallery. Removed rather than left
        // defaulted: parameters no caller passes are dead weight that reads as a live option.
        Text(
            text = text.continueWatching,
            color = BuroColors.Text,
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(BuroSpacing.Md))

        if (entries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = text.continueEmptyTitle,
                        color = BuroColors.Text,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.height(BuroSpacing.Xs))
                    Text(
                        text = text.continueEmptyBody,
                        color = BuroColors.TextSubtle,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            return@Column
        }

        val listState = rememberLazyListState()
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .arrowScrollableList(listState)
                        .edgeScrollableVertically(listState),
                verticalArrangement = Arrangement.spacedBy(BuroSpacing.Sm),
            ) {
                items(entries, key = { entry -> entry.item.providerId }) { entry ->
                    ContinueRow(
                        entry = entry,
                        onResume = { onResume(entry) },
                        onRestart = { onRestart(entry) },
                        onForget = { onForget(entry) },
                    )
                }
            }
            // Visible, like every other long surface: the list scrolled, but with nothing on screen
            // saying so the rows past the fold looked like they did not exist.
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(listState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                style =
                    LocalScrollbarStyle.current.copy(
                        thickness = 10.dp,
                        unhoverColor = BuroColors.BorderSoft,
                        hoverColor = BuroColors.Primary,
                    ),
            )
        }
    }
}

@Composable
private fun ContinueRow(
    entry: DesktopContinueWatchingEntry,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onForget: () -> Unit,
) {
    val text = strings
    val duration = entry.progress.durationMs
    val fraction =
        if (duration > 0L) {
            (entry.progress.positionMs.toFloat() / duration).coerceIn(0f, 1f)
        } else {
            0f
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(BuroRadius.Medium)
                .background(BuroColors.Surface)
                .padding(BuroSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BuroRemoteArtwork(
            artworkUrl = entry.item.artworkUrl,
            contentDescription = entry.item.name,
            modifier =
                Modifier
                    .width(64.dp)
                    .aspectRatio(2f / 3f)
                    .clip(BuroRadius.Small)
                    .background(BuroColors.SurfaceRaised),
            contentScale = ContentScale.Crop,
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = entry.item.name.take(1).uppercase(),
                    color = BuroColors.TextSubtle,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        Spacer(Modifier.width(BuroSpacing.Md))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.item.name.editorialTitle(),
                color = BuroColors.Text,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            // The bar carries the same information as the label beside it, but at a glance: how
            // much is left is the reason this screen exists.
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(BuroColors.Canvas),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(fraction)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(BuroColors.Primary),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text =
                    if (duration > 0L) {
                        "${formatWatchTime(entry.progress.positionMs)} / " +
                            "${formatWatchTime(duration)}  ·  ${(fraction * 100).toInt()}%"
                    } else {
                        formatWatchTime(entry.progress.positionMs)
                    },
                color = BuroColors.TextSubtle,
                style = MaterialTheme.typography.labelMedium,
            )
        }

        Spacer(Modifier.width(BuroSpacing.Md))
        Button(
            onClick = onResume,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = BuroColors.Primary,
                    contentColor = BuroColors.OnPrimary,
                ),
            shape = BuroRadius.Small,
        ) {
            Text(text.resumeFrom, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        Spacer(Modifier.width(BuroSpacing.Xs))
        OutlinedButton(onClick = onRestart, shape = BuroRadius.Small) {
            Text(text.startOver, maxLines = 1)
        }
        TextButton(onClick = onForget) {
            Text(
                text = text.forgetProgress,
                color = BuroColors.TextSubtle,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
    }
}

private fun formatWatchTime(valueMillis: Long): String {
    val totalSeconds = (valueMillis.coerceAtLeast(0L)) / 1_000L
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lucasserafin94.iptvburo.desktop.DesktopContinueWatchingEntry
import com.lucasserafin94.iptvburo.domain.model.normalisedForMatching
import com.lucasserafin94.iptvburo.desktop.ui.BuroColors
import com.lucasserafin94.iptvburo.desktop.ui.BuroInteractiveRow
import com.lucasserafin94.iptvburo.desktop.ui.BuroRadius
import com.lucasserafin94.iptvburo.desktop.ui.BuroRemoteArtwork
import com.lucasserafin94.iptvburo.desktop.ui.BuroSpacing
import com.lucasserafin94.iptvburo.desktop.ui.editorialTitle
import com.lucasserafin94.iptvburo.desktop.ui.strings

/**
 * What has been watched, as covers rather than rows.
 *
 * History answers a different question from Continue watching: not "where was I?" but "have I seen
 * this?". That is a recognition task, and a wall of posters answers it in a glance where a list of
 * titles and progress bars has to be read line by line. Hundreds of entries also fit here, which is
 * the point at which the search box stops being decoration.
 *
 * Clicking a cover resumes it, because a title in this list is one the user has already chosen once
 * and the app knows where it stopped.
 */
@Composable
fun HistoryGallery(
    entries: List<DesktopContinueWatchingEntry>,
    onResume: (DesktopContinueWatchingEntry) -> Unit,
    onForget: (DesktopContinueWatchingEntry) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = strings
    var query by remember { mutableStateOf("") }

    // Titles normalised once per list, not once per keystroke. Normalising strips accents and
    // decoration with several passes over each string, and doing that for every entry on every
    // character typed is work the search does not need to repeat — the titles do not change while
    // the user is typing.
    val searchable =
        remember(entries) {
            entries.map { entry -> entry to entry.item.name.normalisedForMatching() }
        }
    val visible =
        remember(searchable, query) {
            val needle = query.trim().normalisedForMatching()
            if (needle.isBlank()) {
                entries
            } else {
                // Matched on the normalised form, not raw text: a Portuguese catalogue is full of
                // accents, and `contains` compares code points, so "chefao" found nothing while
                // "Chefão" sat on screen. The same normaliser the library matcher uses strips
                // accents and provider decoration alike, which also means "duna 4k" finds "Duna".
                searchable.filter { (_, name) -> name.contains(needle) }.map { (entry, _) -> entry }
            }
        }

    Column(modifier = modifier.fillMaxSize().padding(BuroSpacing.Lg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = text.settingsText.historyTitle,
                color = BuroColors.Text,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(BuroSpacing.Md))
            OutlinedTextField(
                value = query,
                onValueChange = { entered -> query = entered.take(MAX_QUERY_LENGTH) },
                modifier = Modifier.weight(1f).widthIn(max = 420.dp),
                singleLine = true,
                placeholder = { Text(text.search) },
            )
            Spacer(Modifier.width(BuroSpacing.Md))
            // Only when there is something to clear: a button that empties an empty list is noise.
            if (entries.isNotEmpty()) {
                TextButton(onClick = onClearAll) {
                    Text(
                        text = text.settingsText.historyClearAll,
                        color = BuroColors.TextMuted,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }

        Spacer(Modifier.height(BuroSpacing.Md))

        if (visible.isEmpty()) {
            Text(
                // Two different emptinesses. The second is phrased from the query itself rather
                // than from a new translated string: DesktopStrings is close to the JVM's 254
                // constructor-argument ceiling, which has already shipped an app that would not
                // start, and a quoted search term reads clearly in every language.
                text =
                    if (entries.isEmpty()) {
                        text.settingsText.historyEmpty
                    } else {
                        "${text.search}: \"${query.trim()}\" — 0"
                    },
                color = BuroColors.TextSubtle,
                style = MaterialTheme.typography.bodyMedium,
            )
            return@Column
        }

        val gridState = rememberLazyGridState()
        Box(modifier = Modifier.weight(1f)) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(end = BuroSpacing.Md, bottom = BuroSpacing.Lg),
                horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Md),
                verticalArrangement = Arrangement.spacedBy(BuroSpacing.Md),
            ) {
                // Type as well as id: provider numbering restarts per content type, so a film and
                // an episode can share one and the grid would drop a row as a duplicate key.
                items(
                    visible,
                    key = { entry -> "${entry.progress.identity.contentType}:${entry.progress.identity.contentId}" },
                ) { entry ->
                    HistoryCover(
                        entry = entry,
                        onResume = { onResume(entry) },
                        onForget = { onForget(entry) },
                    )
                }
            }
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(gridState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                style =
                    LocalScrollbarStyle.current.copy(
                        unhoverColor = BuroColors.BorderSoft,
                        hoverColor = BuroColors.Primary,
                    ),
            )
        }
    }
}

/**
 * One watched title: its poster, its name, and how far through it the user got.
 *
 * The progress bar stays even for a finished title, where it reads as a full line — that is the
 * fastest way to tell "seen it all" from "gave up halfway", which is most of what this screen is
 * consulted for.
 */
@Composable
private fun HistoryCover(
    entry: DesktopContinueWatchingEntry,
    onResume: () -> Unit,
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

    Column {
        BuroInteractiveRow(
            onClick = onResume,
            selected = false,
            shape = BuroRadius.Medium,
            contentDescription = entry.item.name,
        ) { _ ->
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(POSTER_RATIO).clip(BuroRadius.Medium)) {
                BuroRemoteArtwork(
                    artworkUrl = entry.item.artworkUrl,
                    contentDescription = entry.item.name,
                    modifier = Modifier.fillMaxSize().background(BuroColors.SurfaceRaised),
                    contentScale = ContentScale.Crop,
                ) {
                    // A provider that serves no poster is common, and an empty tile in a wall of
                    // covers is worse than a letter: the initial keeps the grid readable.
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = entry.item.name.take(1).uppercase(),
                            color = BuroColors.TextSubtle,
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                }
                // Drawn over the foot of the poster rather than beside it, so the covers stay on a
                // single grid line and the wall reads as a wall.
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(BuroColors.BorderSoft),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth(fraction)
                                .fillMaxHeight()
                                .background(BuroColors.Primary),
                    )
                }
            }
        }
        Spacer(Modifier.height(BuroSpacing.Xs))
        Text(
            text = entry.item.name.editorialTitle(),
            color = BuroColors.Text,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        TextButton(onClick = onForget, contentPadding = PaddingValues(0.dp)) {
            Text(
                text = text.forgetProgress,
                color = BuroColors.TextSubtle,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/** Posters are 2:3, which is what every provider and TMDb serve. */
private const val POSTER_RATIO = 2f / 3f

/** Long enough for any title, short enough that a paste cannot become a performance problem. */
private const val MAX_QUERY_LENGTH = 80

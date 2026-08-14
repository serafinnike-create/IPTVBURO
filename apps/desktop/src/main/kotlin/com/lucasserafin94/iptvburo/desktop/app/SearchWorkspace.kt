package com.lucasserafin94.iptvburo.desktop.app

import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lucasserafin94.iptvburo.desktop.ui.BuroColors
import com.lucasserafin94.iptvburo.desktop.ui.BuroInteractiveRow
import com.lucasserafin94.iptvburo.desktop.ui.BuroRemoteArtwork
import com.lucasserafin94.iptvburo.desktop.ui.BuroRadius
import com.lucasserafin94.iptvburo.desktop.ui.BuroSpacing
import com.lucasserafin94.iptvburo.desktop.ui.DesktopStrings
import com.lucasserafin94.iptvburo.desktop.ui.rememberRestoredGridState
import com.lucasserafin94.iptvburo.xtream.XtreamCatalogItem
import com.lucasserafin94.iptvburo.xtream.XtreamContentType

/**
 * One box that searches films, series and live channels at once.
 *
 * The app has always had search, but trapped inside each screen: a box in the catalogue, another in
 * the channel list. Both narrow whatever is already open, so looking for a film while browsing live
 * channels found nothing. This is the tab that answers "where is this title" without the user first
 * having to know which of the three it is.
 *
 * Results are ordered titles-first by the repository — someone typing a name almost always wants a
 * film or a series, and a provider carrying three hundred channels with a matching word would
 * otherwise bury the one they meant.
 */
@Composable
fun SearchWorkspace(
    query: String,
    results: List<XtreamCatalogItem>,
    onQueryChange: (String) -> Unit,
    onOpenItem: (XtreamCatalogItem) -> Unit,
    text: DesktopStrings,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(BuroSpacing.Lg)) {
        Text(
            text = text.search,
            color = BuroColors.Text,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(BuroSpacing.Md))

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
            singleLine = true,
            placeholder = { Text(text.searchCatalog) },
        )
        Spacer(Modifier.height(BuroSpacing.Md))

        val trimmed = query.trim()
        if (results.isEmpty()) {
            // Two different emptinesses, and saying "nothing found" before anyone has typed reads
            // as a broken search rather than an invitation.
            //
            // Phrased from the query itself rather than from new translated strings: DesktopStrings
            // sits at its enforced constructor-argument ceiling, and a quoted search term reads
            // clearly in every language this app ships.
            Text(
                text = if (trimmed.length < MIN_QUERY) text.searchCatalog else "${text.search}: \"$trimmed\" — 0",
                color = BuroColors.TextSubtle,
                style = MaterialTheme.typography.bodyMedium,
            )
            return@Column
        }

        val gridState = rememberRestoredGridState("search")
        Box(modifier = Modifier.weight(1f)) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(end = BuroSpacing.Md, bottom = BuroSpacing.Lg),
                horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Md),
                verticalArrangement = Arrangement.spacedBy(BuroSpacing.Md),
            ) {
                // Type as well as id: provider numbering restarts per content type, so a film and a
                // channel can share one and the grid would drop a row as a duplicate key.
                items(results, key = { item -> "${item.contentType}:${item.providerId}" }) { item ->
                    SearchResultCover(item = item, onOpen = { onOpenItem(item) })
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
 * One result: its artwork, its name, and what kind of thing it is.
 *
 * The kind is worth stating here in a way it is not elsewhere. Everywhere else in the app the
 * screen already says whether it is showing films or channels; these three arrive mixed, and
 * "Duna" the film and "Duna" the channel are different answers to the same search.
 */
@Composable
private fun SearchResultCover(
    item: XtreamCatalogItem,
    onOpen: () -> Unit,
) {
    Column {
        BuroInteractiveRow(
            onClick = onOpen,
            selected = false,
            shape = BuroRadius.Medium,
            contentDescription = item.name,
        ) { _ ->
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(POSTER_RATIO).clip(BuroRadius.Medium)) {
                BuroRemoteArtwork(
                    artworkUrl = item.artworkUrl,
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize().background(BuroColors.SurfaceRaised),
                    contentScale = ContentScale.Crop,
                ) {
                    // A provider that serves no poster is common — live channels rarely have one —
                    // and an empty tile in a wall of covers is worse than a letter.
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = item.name.take(1).uppercase(),
                            color = BuroColors.TextSubtle,
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(BuroSpacing.Xs))
        Text(
            text = item.name,
            color = BuroColors.Text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
        )
        Text(
            text = kindLabel(item.contentType),
            color = BuroColors.TextSubtle,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

private fun kindLabel(contentType: XtreamContentType): String =
    when (contentType) {
        XtreamContentType.MOVIE -> "Filme"
        XtreamContentType.SERIES -> "Série"
        XtreamContentType.LIVE -> "Ao vivo"
    }

/** The 2:3 of a film poster, which is what the rest of the app's grids use. */
private const val POSTER_RATIO = 2f / 3f

/** Mirrors the repository's own floor, so the screen and the search agree on what is too short. */
private const val MIN_QUERY = 2

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lucasserafin94.iptvburo.desktop.DesktopAppState
import com.lucasserafin94.iptvburo.desktop.MusicSection
import com.lucasserafin94.iptvburo.desktop.data.musicHomeShelves
import com.lucasserafin94.iptvburo.desktop.playback.DesktopPlaybackRequest
import com.lucasserafin94.iptvburo.desktop.ui.BuroColors
import com.lucasserafin94.iptvburo.desktop.ui.BuroInteractiveRow
import com.lucasserafin94.iptvburo.desktop.ui.BuroInteractiveSurface
import com.lucasserafin94.iptvburo.desktop.ui.BuroRadius
import com.lucasserafin94.iptvburo.desktop.ui.BuroRemoteArtwork
import com.lucasserafin94.iptvburo.desktop.ui.BuroSpacing
import com.lucasserafin94.iptvburo.desktop.ui.strings
import com.lucasserafin94.iptvburo.desktop.platform.chooseLocalPlaylist
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.lucasserafin94.iptvburo.domain.model.MusicArtist
import com.lucasserafin94.iptvburo.domain.model.MusicTrack

/**
 * The music side of the app: a small library of its own, fed by the user's optional M3U.
 *
 * Built from the same tokens as every other screen — [BuroColors], [BuroSpacing], [BuroRadius] and
 * the shared interactive surfaces — so it reads as part of the product rather than as a module
 * bolted on beside it. The video catalogue is untouched by everything here.
 */
@Composable
fun MusicWorkspace(
    appState: DesktopAppState,
    onPlay: (DesktopPlaybackRequest) -> Unit,
    ownerWindow: java.awt.Frame? = null,
) {
    val text = strings
    val scope = rememberCoroutineScope()
    val library = appState.musicLibrary

    // No playlist yet: explain what this section is and offer to add one here. The section used to
    // be hidden until a playlist existed, which made it undiscoverable - there was no way to learn
    // the app had music, and so no reason to go looking for where to add the list.
    if (!appState.hasMusicLibrary) {
        Box(modifier = Modifier.fillMaxSize().padding(BuroSpacing.Xl), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = text.musicEmptyTitle,
                    color = BuroColors.Text,
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(BuroSpacing.Sm))
                Text(
                    text = text.musicEmptyBody,
                    color = BuroColors.TextSubtle,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(BuroSpacing.Lg))
                Button(
                    onClick = {
                        chooseLocalPlaylist(ownerWindow)?.let { path ->
                            scope.launch { appState.attachMusicPlaylist(path) }
                        }
                    },
                    shape = BuroRadius.Small,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = BuroColors.Primary,
                            contentColor = BuroColors.OnPrimary,
                        ),
                ) {
                    Text(text.musicAddPlaylist, fontWeight = FontWeight.Bold)
                }
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        MusicSectionBar(
            selected = appState.musicSection,
            onSelect = appState::selectMusicSection,
            queueSize = appState.playbackQueue.size,
            queueOpen = appState.queuePanelVisible,
            onToggleQueue = appState::toggleQueuePanel,
        )
        // Weighted, never fillMaxSize: an unweighted Column child is measured against unbounded
        // height, which lays the whole section out past the bottom of the window and puts it beyond
        // the reach of any scroll.
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // The section itself is weighted so the queue panel takes its fixed width out of the
            // row and the library reflows into what remains, rather than being overlapped.
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                when (appState.musicSection) {
                    MusicSection.HOME -> {
                        val shelves =
                            remember(library, appState.musicPlayCounts) {
                                musicHomeShelves(library, appState.musicPlayCounts)
                            }
                        MusicHome(
                            newReleases = shelves.newReleases,
                            mostPlayed = shelves.mostPlayed,
                            // The shelf becomes the queue, so playback continues into the next
                            // track instead of stopping after one - the point of a queue existing.
                            onPlay = { track, context ->
                                appState.playMusicNow(track, context)?.let(onPlay)
                            },
                            onPlayNext = { track -> appState.queueMusicNext(track)?.let(onPlay) },
                            onAddToEnd = { track -> appState.queueMusicLast(track)?.let(onPlay) },
                        )
                    }
                    MusicSection.ARTISTS ->
                        MusicArtists(
                            artists = library.artists,
                            openArtist = appState.selectedMusicArtist,
                            tracksForArtist = appState::tracksForArtist,
                            onOpenArtist = appState::selectMusicArtist,
                            onPlay = { track, context ->
                                appState.playMusicNow(track, context)?.let(onPlay)
                            },
                            onPlayNext = { track -> appState.queueMusicNext(track)?.let(onPlay) },
                            onAddToEnd = { track -> appState.queueMusicLast(track)?.let(onPlay) },
                        )
                    MusicSection.RADIO ->
                        MusicTrackList(
                            tracks = library.radioStations,
                            emptyMessage = text.musicNoRadio,
                            // A station replaces the queue (GDD 8 section 16), which the state
                            // enforces; passing only the station keeps that explicit here too.
                            onPlay = { track, _ -> appState.playMusicNow(track)?.let(onPlay) },
                            onPlayNext = { track -> appState.playMusicNow(track)?.let(onPlay) },
                            onAddToEnd = { track -> appState.playMusicNow(track)?.let(onPlay) },
                            // Queueing a station is meaningless: it never ends, so nothing behind
                            // it could play. The row offers play only.
                            showQueueActions = false,
                        )
                    // Playlists live in their own file so the queue work and the playlist work do
                    // not contend for this one. See MusicPlaylistsSection.
                    MusicSection.PLAYLISTS ->
                        MusicPlaylistsSection(
                            appState = appState,
                            onPlay = onPlay,
                            ownerWindow = ownerWindow,
                        )
                    // Music downloads are not wired to the downloader yet, so this states the intent
                    // rather than showing an empty list that looks like a fault. See the report.
                    MusicSection.DOWNLOADS ->
                        MusicEmptyState(
                            title = text.downloads,
                            body = text.musicNoDownloads,
                        )
                }
            }
            if (appState.queuePanelVisible) {
                VerticalDivider(color = BuroColors.BorderSoft)
                QueuePanel(appState = appState, onPlay = onPlay)
            }
        }
    }
}

/** The workspace's own navigation, mirroring the sidebar's ordering. */
@Composable
private fun MusicSectionBar(
    selected: MusicSection,
    onSelect: (MusicSection) -> Unit,
    queueSize: Int,
    queueOpen: Boolean,
    onToggleQueue: () -> Unit,
) {
    val text = strings
    val labels =
        listOf(
            MusicSection.HOME to text.musicHome,
            MusicSection.ARTISTS to text.musicArtists,
            MusicSection.PLAYLISTS to text.musicPlaylists,
            MusicSection.RADIO to text.musicRadio,
            MusicSection.DOWNLOADS to text.downloads,
        )

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = BuroSpacing.Lg, vertical = BuroSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text.music,
            color = BuroColors.Text,
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.width(BuroSpacing.Lg))
        Row(
            modifier = Modifier.clip(BuroRadius.Pill).background(BuroColors.Surface).padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            labels.forEach { (section, label) ->
                val active = section == selected
                BuroInteractiveRow(
                    onClick = { onSelect(section) },
                    selected = active,
                    shape = BuroRadius.Pill,
                    contentDescription = label,
                ) { state ->
                    Text(
                        text = label,
                        modifier =
                            Modifier.padding(horizontal = BuroSpacing.Md, vertical = BuroSpacing.Xs),
                        color =
                            when {
                                active -> BuroColors.Primary
                                state.active -> BuroColors.Text
                                else -> BuroColors.TextMuted
                            },
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        // The queue toggle carries its own count, so the user can tell there is something queued
        // without opening the panel to find out.
        BuroInteractiveRow(
            onClick = onToggleQueue,
            selected = queueOpen,
            shape = BuroRadius.Pill,
            contentDescription = if (queueOpen) text.queueClose else text.queueOpen,
        ) { state ->
            Text(
                text = if (queueSize > 0) "${text.queueTitle} · $queueSize" else text.queueTitle,
                modifier = Modifier.padding(horizontal = BuroSpacing.Md, vertical = BuroSpacing.Xs),
                color =
                    when {
                        queueOpen -> BuroColors.Primary
                        state.active -> BuroColors.Text
                        else -> BuroColors.TextMuted
                    },
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/**
 * New releases and most played.
 *
 * Most played is omitted rather than shown empty when nothing has been listened to yet: an empty
 * rail under a heading that promises a ranking reads as a bug.
 */
@Composable
private fun MusicHome(
    newReleases: List<MusicTrack>,
    mostPlayed: List<MusicTrack>,
    onPlay: (MusicTrack, List<MusicTrack>) -> Unit,
    onPlayNext: (MusicTrack) -> Unit,
    onAddToEnd: (MusicTrack) -> Unit,
) {
    val text = strings
    if (newReleases.isEmpty() && mostPlayed.isEmpty()) {
        MusicEmptyState(title = text.musicEmptyTitle, body = text.musicEmptyBody)
        return
    }

    val listState = rememberLazyListState()
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = BuroSpacing.Xl),
            verticalArrangement = Arrangement.spacedBy(BuroSpacing.Lg),
        ) {
            if (newReleases.isNotEmpty()) {
                item(key = "new-releases") {
                    MusicShelf(
                        title = text.musicNewReleases,
                        tracks = newReleases,
                        onPlay = onPlay,
                        onPlayNext = onPlayNext,
                        onAddToEnd = onAddToEnd,
                    )
                }
            }
            if (mostPlayed.isNotEmpty()) {
                item(key = "most-played") {
                    MusicShelf(
                        title = text.musicMostPlayed,
                        tracks = mostPlayed,
                        onPlay = onPlay,
                        onPlayNext = onPlayNext,
                        onAddToEnd = onAddToEnd,
                    )
                }
            }
        }
        MusicScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd),
            adapter = rememberScrollbarAdapter(listState),
        )
    }
}

/** One horizontal rail of covers. */
@Composable
private fun MusicShelf(
    title: String,
    tracks: List<MusicTrack>,
    onPlay: (MusicTrack, List<MusicTrack>) -> Unit,
    onPlayNext: (MusicTrack) -> Unit,
    onAddToEnd: (MusicTrack) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = BuroSpacing.Lg),
            color = BuroColors.Text,
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(BuroSpacing.Sm))
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = BuroSpacing.Lg),
            horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Md),
        ) {
            items(tracks, key = MusicTrack::id) { track ->
                MusicCard(
                    track = track,
                    // The whole rail is the context, so playing the third cover continues into the
                    // fourth rather than falling silent.
                    onPlay = { onPlay(track, tracks) },
                    onPlayNext = { onPlayNext(track) },
                    onAddToEnd = { onAddToEnd(track) },
                )
            }
        }
    }
}

/** A square cover with its title beneath, the shape a music library is expected to have. */
@Composable
private fun MusicCard(
    track: MusicTrack,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToEnd: () -> Unit,
) {
    val text = strings
    BuroInteractiveSurface(
        onClick = onPlay,
        modifier = Modifier.width(164.dp),
        shape = BuroRadius.Medium,
        contentDescription = track.title,
    ) { cardState ->
        Column(modifier = Modifier.fillMaxWidth()) {
            BuroRemoteArtwork(
                artworkUrl = track.artworkUri,
                contentDescription = track.title,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        // Square, unlike the video poster's 2:3: album art is square, and cropping
                        // it to a poster shape cuts the middle out of every cover.
                        .aspectRatio(1f)
                        .clip(BuroRadius.Medium)
                        .background(BuroColors.SurfaceRaised),
                contentScale = ContentScale.Crop,
            ) {
                MusicArtworkFallback(label = track.title)
            }
            Spacer(Modifier.height(BuroSpacing.Xs))
            Text(
                text = track.title,
                modifier = Modifier.padding(horizontal = BuroSpacing.Xxs),
                color = BuroColors.Text,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artistOrUnknown,
                modifier = Modifier.padding(horizontal = BuroSpacing.Xxs, vertical = 2.dp),
                color = BuroColors.TextSubtle,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Queue actions only on hover: a rail of twenty covers each carrying two permanent
            // buttons is unreadable, and the primary action of a cover is still to play it.
            if (cardState.active) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Xxs),
                ) {
                    MusicQueueAction(label = "+ ${text.queuePlayNext}", onClick = onPlayNext)
                    MusicQueueAction(label = "↓", contentDescription = text.queueAddToEnd, onClick = onAddToEnd)
                }
            }
        }
    }
}

/**
 * The artist grid, and one artist's tracks once opened.
 *
 * Both live here rather than in separate sections because opening an artist is a step within
 * Artistas, not a destination of its own — the back control returns to the grid instead of leaving
 * the user to find their way through the section bar.
 */
@Composable
private fun MusicArtists(
    artists: List<MusicArtist>,
    openArtist: String?,
    tracksForArtist: (String) -> List<MusicTrack>,
    onOpenArtist: (String?) -> Unit,
    onPlay: (MusicTrack, List<MusicTrack>) -> Unit,
    onPlayNext: (MusicTrack) -> Unit,
    onAddToEnd: (MusicTrack) -> Unit,
) {
    val text = strings
    if (openArtist != null) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = BuroSpacing.Lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BuroInteractiveRow(
                    onClick = { onOpenArtist(null) },
                    selected = false,
                    shape = BuroRadius.Pill,
                    contentDescription = text.musicBackToArtists,
                ) { _ ->
                    Text(
                        text = "‹  ${text.musicBackToArtists}",
                        modifier =
                            Modifier.padding(horizontal = BuroSpacing.Sm, vertical = BuroSpacing.Xs),
                        color = BuroColors.TextMuted,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Spacer(Modifier.width(BuroSpacing.Md))
                Text(
                    text = openArtist,
                    color = BuroColors.Text,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(BuroSpacing.Sm))
            // Weighted so the track list owns the rest of the column and scrolls inside it.
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                MusicTrackList(
                    tracks = tracksForArtist(openArtist),
                    emptyMessage = text.musicNoArtists,
                    onPlay = onPlay,
                    onPlayNext = onPlayNext,
                    onAddToEnd = onAddToEnd,
                )
            }
        }
        return
    }

    if (artists.isEmpty()) {
        MusicEmptyState(title = text.musicArtists, body = text.musicNoArtists)
        return
    }

    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 168.dp),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(BuroSpacing.Lg),
            horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(BuroSpacing.Md),
        ) {
            items(artists, key = MusicArtist::name) { artist ->
                MusicArtistCard(artist = artist, onClick = { onOpenArtist(artist.name) })
            }
        }
        MusicScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd),
            adapter = rememberScrollbarAdapter(gridState),
        )
    }
}

/** A round cover, the convention that distinguishes an artist from an album at a glance. */
@Composable
private fun MusicArtistCard(
    artist: MusicArtist,
    onClick: () -> Unit,
) {
    BuroInteractiveSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = BuroRadius.Medium,
        contentDescription = artist.name,
    ) { _ ->
        Column(
            modifier = Modifier.fillMaxWidth().padding(BuroSpacing.Sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BuroRemoteArtwork(
                artworkUrl = artist.artworkUri,
                contentDescription = artist.name,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(CircleShape)
                        .background(BuroColors.SurfaceRaised),
                contentScale = ContentScale.Crop,
            ) {
                MusicArtworkFallback(label = artist.name, round = true)
            }
            Spacer(Modifier.height(BuroSpacing.Xs))
            Text(
                text = artist.name,
                color = BuroColors.Text,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${artist.trackCount} ${strings.musicTracks}",
                color = BuroColors.TextSubtle,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** A scrolling list of rows, used for radio stations and for one artist's tracks. */
@Composable
private fun MusicTrackList(
    tracks: List<MusicTrack>,
    emptyMessage: String,
    onPlay: (MusicTrack, List<MusicTrack>) -> Unit,
    onPlayNext: (MusicTrack) -> Unit,
    onAddToEnd: (MusicTrack) -> Unit,
    showQueueActions: Boolean = true,
) {
    if (tracks.isEmpty()) {
        MusicEmptyState(title = strings.music, body = emptyMessage)
        return
    }

    val listState = rememberLazyListState()
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = BuroSpacing.Lg, vertical = BuroSpacing.Xs),
            verticalArrangement = Arrangement.spacedBy(BuroSpacing.Xs),
        ) {
            items(tracks, key = MusicTrack::id) { track ->
                MusicTrackRow(
                    track = track,
                    // The whole list is the context: playing one track continues into the rest.
                    onPlay = { onPlay(track, tracks) },
                    onPlayNext = { onPlayNext(track) },
                    onAddToEnd = { onAddToEnd(track) },
                    showQueueActions = showQueueActions,
                )
            }
        }
        MusicScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd),
            adapter = rememberScrollbarAdapter(listState),
        )
    }
}

@Composable
private fun MusicTrackRow(
    track: MusicTrack,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToEnd: () -> Unit,
    showQueueActions: Boolean,
) {
    val text = strings
    BuroInteractiveRow(
        onClick = onPlay,
        selected = false,
        modifier = Modifier.fillMaxWidth(),
        shape = BuroRadius.Medium,
        contentDescription = track.title,
    ) { state ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(BuroSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BuroRemoteArtwork(
                artworkUrl = track.artworkUri,
                contentDescription = track.title,
                modifier =
                    Modifier
                        .size(48.dp)
                        .clip(BuroRadius.Small)
                        .background(BuroColors.SurfaceRaised),
                contentScale = ContentScale.Crop,
            ) {
                MusicArtworkFallback(label = track.title)
            }
            Spacer(Modifier.width(BuroSpacing.Sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    color = BuroColors.Text,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = track.genre ?: track.artistOrUnknown,
                    color = BuroColors.TextSubtle,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // The play affordance appears on hover rather than sitting on every row: a list of
            // fifty stations with fifty gold buttons reads as noise. The queue actions follow the
            // same rule, and are hidden entirely for radio, which cannot be queued behind anything.
            if (state.active && showQueueActions) {
                MusicQueueAction(label = "+ ${text.queuePlayNext}", onClick = onPlayNext)
                MusicQueueAction(label = "↓", contentDescription = text.queueAddToEnd, onClick = onAddToEnd)
            }
            Text(
                text = "▶",
                modifier = Modifier.padding(horizontal = BuroSpacing.Sm),
                color = if (state.active) BuroColors.Primary else BuroColors.TextSubtle,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

/**
 * A small "play next" / "add to end" control, shown on hover.
 *
 * Text rather than an icon: the two actions are easy to confuse as glyphs, and getting them the
 * wrong way round is exactly the mistake the queue makes visible.
 */
@Composable
private fun MusicQueueAction(
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
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/**
 * Cover stand-in for an entry whose artwork is missing or still loading.
 *
 * A music M3U carries `tvg-logo` far less reliably than a video one, so this is the common case
 * rather than the exception.
 */
@Composable
private fun MusicArtworkFallback(
    label: String,
    round: Boolean = false,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .clip(if (round) CircleShape else BuroRadius.Medium)
                .background(
                    Brush.linearGradient(
                        listOf(
                            BuroColors.Primary.copy(alpha = 0.22f),
                            BuroColors.Accent.copy(alpha = 0.10f),
                        ),
                    ),
                ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label.trim().take(1).uppercase().ifBlank { "♪" },
            color = BuroColors.Text,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

@Composable
private fun MusicEmptyState(
    title: String,
    body: String,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                color = BuroColors.Text,
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(BuroSpacing.Xs))
            Text(
                text = body,
                color = BuroColors.TextSubtle,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/**
 * Scrollbar with explicit colours.
 *
 * The default track is very nearly the canvas colour, which on this palette makes it invisible —
 * long lists then look unscrollable even though they are not.
 */
@Composable
private fun MusicScrollbar(
    modifier: Modifier,
    adapter: androidx.compose.foundation.v2.ScrollbarAdapter,
) {
    VerticalScrollbar(
        adapter = adapter,
        modifier = modifier.fillMaxHeight().padding(vertical = BuroSpacing.Xs),
        style =
            LocalScrollbarStyle.current.copy(
                thickness = 8.dp,
                unhoverColor = BuroColors.BorderSoft,
                hoverColor = BuroColors.Primary,
            ),
    )
}

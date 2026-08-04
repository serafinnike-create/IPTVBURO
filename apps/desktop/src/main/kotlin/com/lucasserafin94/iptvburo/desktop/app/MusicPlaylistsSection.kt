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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lucasserafin94.iptvburo.desktop.DesktopAppState
import com.lucasserafin94.iptvburo.desktop.SmartMusicPlaylist
import com.lucasserafin94.iptvburo.desktop.platform.chooseM3uDestination
import com.lucasserafin94.iptvburo.desktop.platform.chooseM3uFile
import com.lucasserafin94.iptvburo.desktop.playback.DesktopPlaybackRequest
import com.lucasserafin94.iptvburo.desktop.ui.BuroColors
import com.lucasserafin94.iptvburo.desktop.ui.BuroInteractiveRow
import com.lucasserafin94.iptvburo.desktop.ui.BuroRadius
import com.lucasserafin94.iptvburo.desktop.ui.BuroSpacing
import com.lucasserafin94.iptvburo.desktop.ui.DesktopStrings
import com.lucasserafin94.iptvburo.desktop.ui.strings
import com.lucasserafin94.iptvburo.domain.model.MusicPlaylist
import com.lucasserafin94.iptvburo.domain.model.MusicTrack
import com.lucasserafin94.iptvburo.domain.model.SmartPlaylistRule
import kotlinx.coroutines.launch

/**
 * The playlists section — GDD 8 section 17.
 *
 * Kept in its own file rather than added to [MusicWorkspace] because the playback queue is being
 * built in that file in parallel; separating them means neither change has to be unpicked from the
 * other.
 *
 * Two groups are shown. The user's own playlists can be created, renamed, duplicated, deleted,
 * reordered and exported. The smart playlists below them are computed from the library and the
 * profile's listening history, so they have no editing affordances at all — offering "remove track"
 * on a list that is recomputed on the next frame would be a lie.
 */
@Composable
fun MusicPlaylistsSection(
    appState: DesktopAppState,
    onPlay: (DesktopPlaybackRequest) -> Unit,
    ownerWindow: java.awt.Frame? = null,
) {
    val text = strings
    val openId = appState.selectedMusicPlaylistId

    // An export awaiting its warning takes over the section. The warning is required before the
    // file exists, so it must not be dismissible by simply navigating past it.
    appState.pendingMusicExport?.let { pending ->
        MusicExportWarningDialog(
            sensitiveCount = pending.warning.sensitiveUriCount,
            totalCount = pending.warning.totalTrackCount,
            onConfirm = appState::confirmMusicExport,
            onCancel = appState::cancelMusicExport,
        )
        return
    }

    if (openId != null) {
        val playlist = appState.musicPlaylists.firstOrNull { it.id == openId }
        if (playlist != null) {
            MusicPlaylistDetail(
                appState = appState,
                playlist = playlist,
                onPlay = onPlay,
                ownerWindow = ownerWindow,
            )
            return
        }
        // The playlist was deleted from under the open view; fall through to the index rather than
        // rendering an empty detail page.
    }

    MusicPlaylistIndex(appState = appState, onPlay = onPlay, ownerWindow = ownerWindow, text = text)
}

/** The list of the user's playlists, with the smart playlists beneath them. */
@Composable
private fun MusicPlaylistIndex(
    appState: DesktopAppState,
    onPlay: (DesktopPlaybackRequest) -> Unit,
    ownerWindow: java.awt.Frame?,
    text: DesktopStrings,
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val playlists = appState.musicPlaylists
    // Recomputed from state rather than remembered against it: the smart lists depend on the
    // library, the history and the favourites at once, and a stale key here would show yesterday's
    // "recently played".
    val smart = appState.smartMusicPlaylists

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = BuroSpacing.Lg, vertical = BuroSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Sm),
        ) {
            MusicPlaylistAction(label = text.musicPlaylistNew) {
                appState.createMusicPlaylist(text.musicPlaylistNewName)
            }
            MusicPlaylistAction(label = text.musicPlaylistImport) {
                chooseM3uFile(ownerWindow, text.musicPlaylistImport)?.let { path ->
                    scope.launch { appState.importMusicPlaylist(path) }
                }
            }
        }

        // Weighted, never fillMaxSize: an unweighted scrollable child in a Column is measured
        // against unbounded height, which lays it out past the bottom of the window and puts it
        // beyond the reach of any scroll.
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = BuroSpacing.Lg, vertical = BuroSpacing.Xs),
                verticalArrangement = Arrangement.spacedBy(BuroSpacing.Xs),
            ) {
                if (playlists.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            text = text.musicPlaylistsEmpty,
                            modifier = Modifier.padding(vertical = BuroSpacing.Md),
                            color = BuroColors.TextSubtle,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                items(playlists, key = MusicPlaylist::id) { playlist ->
                    MusicPlaylistRow(
                        playlist = playlist,
                        onOpen = { appState.selectMusicPlaylist(playlist.id) },
                        onRename = { name -> appState.renameMusicPlaylist(playlist.id, name) },
                        onDuplicate = {
                            appState.duplicateMusicPlaylist(
                                playlist.id,
                                "${playlist.name} (${text.musicPlaylistDuplicateSuffix})",
                            )
                        },
                        onDelete = { appState.deleteMusicPlaylist(playlist.id) },
                        text = text,
                    )
                }

                if (smart.isNotEmpty()) {
                    item(key = "smart-heading") {
                        Text(
                            text = text.musicSmartPlaylists,
                            modifier = Modifier.padding(top = BuroSpacing.Lg, bottom = BuroSpacing.Xs),
                            color = BuroColors.Text,
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                    items(smart, key = { smartPlaylistKey(it.rule) }) { entry ->
                        SmartPlaylistShelf(
                            title = smartPlaylistLabel(entry.rule, text),
                            tracks = entry.tracks,
                            onPlay = { track -> appState.prepareMusicPlayback(track)?.let(onPlay) },
                        )
                    }
                }
            }
            MusicPlaylistScrollbar(
                modifier = Modifier.align(Alignment.CenterEnd),
                adapter = rememberScrollbarAdapter(listState),
            )
        }
    }
}

/** One stored playlist, with its operations inline. */
@Composable
private fun MusicPlaylistRow(
    playlist: MusicPlaylist,
    onOpen: () -> Unit,
    onRename: (String) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    text: DesktopStrings,
) {
    var renaming by remember(playlist.id) { mutableStateOf(false) }

    BuroInteractiveRow(
        onClick = onOpen,
        selected = false,
        modifier = Modifier.fillMaxWidth(),
        shape = BuroRadius.Medium,
        contentDescription = playlist.name,
    ) { _ ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(BuroSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (renaming) {
                    MusicPlaylistNameField(
                        initial = playlist.name,
                        onCommit = { name ->
                            onRename(name)
                            renaming = false
                        },
                    )
                } else {
                    Text(
                        text = playlist.name,
                        color = BuroColors.Text,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "${playlist.trackCount} ${text.musicTracks}",
                    color = BuroColors.TextSubtle,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            MusicPlaylistAction(label = text.musicPlaylistRename) { renaming = true }
            MusicPlaylistAction(label = text.musicPlaylistDuplicate, onClick = onDuplicate)
            MusicPlaylistAction(label = text.musicPlaylistDelete, onClick = onDelete)
        }
    }
}

/** One open playlist: its tracks, in the user's order, with reorder and remove. */
@Composable
private fun MusicPlaylistDetail(
    appState: DesktopAppState,
    playlist: MusicPlaylist,
    onPlay: (DesktopPlaybackRequest) -> Unit,
    ownerWindow: java.awt.Frame?,
) {
    val text = strings
    val listState = rememberLazyListState()
    val tracks = appState.tracksForMusicPlaylist(playlist.id)

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = BuroSpacing.Lg, vertical = BuroSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Sm),
        ) {
            MusicPlaylistAction(label = "‹  ${text.musicPlaylistBack}") { appState.selectMusicPlaylist(null) }
            Text(
                text = playlist.name,
                modifier = Modifier.weight(1f),
                color = BuroColors.Text,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            MusicPlaylistAction(label = text.musicPlaylistExport) {
                val destination =
                    chooseM3uDestination(ownerWindow, text.musicPlaylistExport, "${playlist.name}.m3u")
                if (destination != null) {
                    // Only ever begins the export. The warning is raised from state, and the file
                    // is written by confirmMusicExport once the user has answered it.
                    appState.beginMusicExport(playlist.name, tracks, destination)
                }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (tracks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = text.musicPlaylistEmptyTracks,
                        color = BuroColors.TextSubtle,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = BuroSpacing.Lg, vertical = BuroSpacing.Xs),
                    verticalArrangement = Arrangement.spacedBy(BuroSpacing.Xs),
                ) {
                    itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                        MusicPlaylistTrackRow(
                            track = track,
                            canMoveUp = index > 0,
                            canMoveDown = index < tracks.lastIndex,
                            onPlay = { appState.prepareMusicPlayback(track)?.let(onPlay) },
                            onMoveUp = { appState.reorderMusicPlaylist(playlist.id, index, index - 1) },
                            onMoveDown = { appState.reorderMusicPlaylist(playlist.id, index, index + 1) },
                            onRemove = { appState.removeTrackFromMusicPlaylist(playlist.id, track.id) },
                            text = text,
                        )
                    }
                }
                MusicPlaylistScrollbar(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    adapter = rememberScrollbarAdapter(listState),
                )
            }
        }
    }
}

@Composable
private fun MusicPlaylistTrackRow(
    track: MusicTrack,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onPlay: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    text: DesktopStrings,
) {
    BuroInteractiveRow(
        onClick = onPlay,
        selected = false,
        modifier = Modifier.fillMaxWidth(),
        shape = BuroRadius.Medium,
        contentDescription = track.title,
    ) { _ ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(BuroSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    color = BuroColors.Text,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = track.artistOrUnknown,
                    color = BuroColors.TextSubtle,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Reorder by button rather than by drag: a drag inside a lazy list that also scrolls
            // needs a gesture arbiter to be reliable, and the buttons work identically from the
            // keyboard, which a drag does not.
            if (canMoveUp) MusicPlaylistAction(label = "▲", onClick = onMoveUp)
            if (canMoveDown) MusicPlaylistAction(label = "▼", onClick = onMoveDown)
            MusicPlaylistAction(label = text.musicPlaylistRemoveTrack, onClick = onRemove)
        }
    }
}

/** One smart playlist as a horizontal rail, matching the home shelves. */
@Composable
private fun SmartPlaylistShelf(
    title: String,
    tracks: List<MusicTrack>,
    onPlay: (MusicTrack) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = BuroSpacing.Xs)) {
        Text(
            text = title,
            color = BuroColors.Text,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(BuroSpacing.Xs))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Sm)) {
            items(tracks, key = MusicTrack::id) { track ->
                BuroInteractiveRow(
                    onClick = { onPlay(track) },
                    selected = false,
                    modifier = Modifier.width(220.dp),
                    shape = BuroRadius.Medium,
                    contentDescription = track.title,
                ) { _ ->
                    Column(modifier = Modifier.fillMaxWidth().padding(BuroSpacing.Sm)) {
                        Text(
                            text = track.title,
                            color = BuroColors.Text,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = track.artistOrUnknown,
                            color = BuroColors.TextSubtle,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The sensitive-URL warning required before an M3U export (GDD 8 section 17).
 *
 * States what would be disclosed and how many entries carry it, rather than a generic "are you
 * sure": the user cannot weigh the risk without knowing that the file can hand over their
 * subscription.
 */
@Composable
private fun MusicExportWarningDialog(
    sensitiveCount: Int,
    totalCount: Int,
    onConfirm: suspend () -> Boolean,
    onCancel: () -> Unit,
) {
    val text = strings
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize().padding(BuroSpacing.Xl), contentAlignment = Alignment.Center) {
        Column(
            modifier =
                Modifier
                    .width(520.dp)
                    .clip(BuroRadius.Medium)
                    .background(BuroColors.Surface)
                    .padding(BuroSpacing.Lg),
        ) {
            Text(
                text = text.musicExportWarningTitle,
                color = BuroColors.Text,
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(BuroSpacing.Sm))
            Text(
                text = text.musicExportWarningBody,
                color = BuroColors.TextSubtle,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(BuroSpacing.Sm))
            Text(
                text = "$sensitiveCount / $totalCount",
                color = BuroColors.Primary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(BuroSpacing.Lg))
            Row(horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Sm)) {
                MusicPlaylistAction(label = text.musicExportWarningCancel, onClick = onCancel)
                MusicPlaylistAction(label = text.musicExportWarningConfirm) {
                    scope.launch { onConfirm() }
                }
            }
        }
    }
}

/** An inline rename field, committing on Enter or on losing focus. */
@Composable
private fun MusicPlaylistNameField(
    initial: String,
    onCommit: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }

    BasicTextField(
        value = value,
        onValueChange = { value = it },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        textStyle =
            MaterialTheme.typography.titleMedium.copy(color = BuroColors.Text),
        cursorBrush = SolidColor(BuroColors.Primary),
        keyboardActions =
            androidx.compose.foundation.text.KeyboardActions(
                onDone = { onCommit(value) },
            ),
        keyboardOptions =
            androidx.compose.foundation.text.KeyboardOptions(
                imeAction = androidx.compose.ui.text.input.ImeAction.Done,
            ),
    )
}

/** A small text button, in the shape the rest of the music section uses. */
@Composable
private fun MusicPlaylistAction(
    label: String,
    onClick: () -> Unit,
) {
    BuroInteractiveRow(
        onClick = onClick,
        selected = false,
        shape = BuroRadius.Pill,
        contentDescription = label,
    ) { state ->
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = BuroSpacing.Sm, vertical = BuroSpacing.Xs),
            color = if (state.active) BuroColors.Primary else BuroColors.TextMuted,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/**
 * Scrollbar with explicit colours.
 *
 * The default track is very nearly the canvas colour on this palette, which makes long lists look
 * unscrollable even though they are not.
 */
@Composable
private fun MusicPlaylistScrollbar(
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

/**
 * A stable list key for a smart playlist.
 *
 * Derived from the rule rather than the label, because the label is translated and would change
 * the key — and therefore rebuild every row — on a language switch.
 */
private fun smartPlaylistKey(rule: SmartPlaylistRule): String =
    when (rule) {
        SmartPlaylistRule.Favourites -> "smart:favourites"
        SmartPlaylistRule.RecentlyPlayed -> "smart:recently-played"
        SmartPlaylistRule.MostPlayed -> "smart:most-played"
        SmartPlaylistRule.NeverPlayed -> "smart:never-played"
        SmartPlaylistRule.RecentlyAdded -> "smart:recently-added"
        is SmartPlaylistRule.ByGenre -> "smart:genre:${rule.genre}"
        is SmartPlaylistRule.ByDecade -> "smart:decade:${rule.startYear}"
    }

/**
 * The shelf title for a rule.
 *
 * Genre and decade are not translated: a genre is the playlist author's own word, and a decade
 * label is a number. Inventing translations for either would rename the user's own data.
 */
private fun smartPlaylistLabel(
    rule: SmartPlaylistRule,
    text: DesktopStrings,
): String =
    when (rule) {
        SmartPlaylistRule.Favourites -> text.musicSmartFavourites
        SmartPlaylistRule.RecentlyPlayed -> text.musicSmartRecentlyPlayed
        SmartPlaylistRule.MostPlayed -> text.musicSmartMostPlayed
        SmartPlaylistRule.NeverPlayed -> text.musicSmartNeverPlayed
        SmartPlaylistRule.RecentlyAdded -> text.musicSmartRecentlyAdded
        is SmartPlaylistRule.ByGenre -> rule.genre
        is SmartPlaylistRule.ByDecade -> "${rule.startYear}s"
    }

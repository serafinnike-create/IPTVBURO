package com.lucasserafin94.iptvburo.desktop.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lucasserafin94.iptvburo.desktop.DesktopAppState
import com.lucasserafin94.iptvburo.desktop.ui.BuroColors
import com.lucasserafin94.iptvburo.desktop.ui.BuroRadius
import com.lucasserafin94.iptvburo.desktop.ui.BuroSpacing
import com.lucasserafin94.iptvburo.desktop.ui.strings
import com.lucasserafin94.iptvburo.domain.model.MusicTidyProposal
import com.lucasserafin94.iptvburo.domain.model.MusicTrack
import kotlinx.coroutines.launch

/**
 * The music workshop: making sense of somebody else's M3U.
 *
 * ## What it is for
 *
 * A music playlist's display name is whatever the person who built it happened to have, which in
 * practice means filenames — `01 - Pink_Floyd_-_Time.mp3`, `[320kbps] Money (Official Video)`. The
 * parser reads those faithfully and produces an artist called "01" and a title ending in ".mp3",
 * because that is what the file says.
 *
 * This is where the user says what the file *meant*.
 *
 * ## Nothing is applied without being seen
 *
 * Every rule here is a guess. Guesses shown as a before-and-after list, applied in a batch the user
 * chose, and undoable in one press are a tool; the same guesses applied silently on import would be
 * an app that renames somebody's music without asking.
 *
 * ## The source file is never touched
 *
 * Corrections are an overlay stored per profile. The M3U belongs to the user and may be replaced at
 * any time — re-downloaded, regenerated — and rewriting it would both claim ownership of their file
 * and lose every correction the next time they refreshed it.
 */
@Composable
fun MusicWorkshopDialog(
    appState: DesktopAppState,
    onDismiss: () -> Unit,
) {
    val text = strings.settingsText
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(WorkshopTab.NAMES) }

    // Recomputed when a correction is applied, which is what `musicRevision` exists for: the library
    // is rebuilt from disk and every proposal is derived from it.
    val revision = appState.musicRevision
    val proposals = remember(revision) { appState.musicTidyProposals() }
    val duplicates = remember(revision) { appState.musicDuplicateGroups() }
    val sameAddress = remember(revision) { appState.musicSameAddressGroups() }
    val correctionCount = remember(revision) { appState.musicCorrectionCount() }

    Box(
        modifier = Modifier.fillMaxSize().background(BuroColors.Scrim),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 860.dp)
                .fillMaxWidth(0.8f)
                .heightIn(max = 720.dp)
                .fillMaxHeight(0.86f)
                .clip(BuroRadius.Large)
                .background(BuroColors.Canvas)
                .border(1.dp, BuroColors.Border, BuroRadius.Large)
                .padding(BuroSpacing.Lg),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = text.musicWorkshop,
                        color = BuroColors.Text,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        // The counts before the tabs, because they answer "is anything wrong?" —
                        // which is the question somebody opens this to ask.
                        text = text.musicWorkshopSummary
                            .format(appState.musicLibrary.songs.size, correctionCount),
                        color = BuroColors.TextSubtle,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text("✕", color = BuroColors.TextMuted, style = MaterialTheme.typography.titleMedium)
                }
            }

            Spacer(Modifier.height(BuroSpacing.Md))

            Row(horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Xs)) {
                WorkshopTabChip(
                    label = "${text.musicWorkshopNames} (${proposals.size})",
                    selected = tab == WorkshopTab.NAMES,
                    onClick = { tab = WorkshopTab.NAMES },
                )
                WorkshopTabChip(
                    label = "${text.musicWorkshopDuplicates} (${duplicates.size + sameAddress.size})",
                    selected = tab == WorkshopTab.DUPLICATES,
                    onClick = { tab = WorkshopTab.DUPLICATES },
                )
            }

            Spacer(Modifier.height(BuroSpacing.Md))

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    WorkshopTab.NAMES -> NamesTab(appState, proposals)
                    WorkshopTab.DUPLICATES -> DuplicatesTab(duplicates, sameAddress)
                }
            }

            if (correctionCount > 0) {
                Spacer(Modifier.height(BuroSpacing.Sm))
                // The way back, always available. A batch rename with no undo is a tool nobody
                // should press the first time.
                TextButton(onClick = { scope.launch { appState.undoAllMusicCorrections() } }) {
                    Text(
                        text = text.musicWorkshopUndoAll.format(correctionCount),
                        color = BuroColors.TextSubtle,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

private enum class WorkshopTab { NAMES, DUPLICATES }

/**
 * The proposed renames, before and after.
 *
 * Both halves shown together because the decision is a comparison: "Pink Floyd · Time" is obviously
 * right *next to* `01 - Pink_Floyd_-_Time.mp3` and merely plausible on its own.
 */
@Composable
private fun NamesTab(
    appState: DesktopAppState,
    proposals: List<MusicTidyProposal>,
) {
    val text = strings.settingsText
    // Applying a correction writes to disk and re-reads the playlist, so it is a suspending call:
    // doing that on the UI thread would stall the window on a large library.
    val scope = rememberCoroutineScope()

    if (proposals.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = text.musicWorkshopNothingToFix,
                color = BuroColors.TextSubtle,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }

    val byId = remember(appState.musicRevision) {
        appState.musicLibrary.tracks.associateBy(MusicTrack::id)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Button(
            onClick = { scope.launch { appState.applyMusicTidy(proposals) } },
            colors = ButtonDefaults.buttonColors(
                containerColor = BuroColors.Primary,
                contentColor = BuroColors.OnPrimary,
            ),
            shape = BuroRadius.Small,
        ) {
            Text(text.musicWorkshopApplyAll.format(proposals.size), fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(BuroSpacing.Sm))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(BuroSpacing.Xxs)) {
            items(proposals, key = MusicTidyProposal::trackId) { proposal ->
                val original = byId[proposal.trackId]
                ProposalRow(
                    before = original?.let { track ->
                        listOfNotNull(track.artist, track.title).joinToString(" · ")
                    }.orEmpty(),
                    after = listOfNotNull(proposal.artist, proposal.title).joinToString(" · "),
                    onApply = {
                        scope.launch {
                            appState.correctMusicTrack(
                                proposal.trackId,
                                proposal.title,
                                proposal.artist,
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ProposalRow(
    before: String,
    after: String,
    onApply: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(BuroRadius.Small)
            .background(BuroColors.Surface)
            .padding(horizontal = BuroSpacing.Sm, vertical = BuroSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = before,
                color = BuroColors.TextSubtle,
                style = MaterialTheme.typography.labelSmall,
                // Struck through: this is what the row *was*, and the strike says so faster than a
                // label would.
                textDecoration = TextDecoration.LineThrough,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = after,
                color = BuroColors.Text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = onApply) {
            Text(strings.settingsText.musicWorkshopApplyOne, color = BuroColors.Primary)
        }
    }
}

/**
 * Repeated tracks, in two groups that mean different things.
 *
 * Same address is a certainty: two entries pointing at one URL are the same entry twice. Same name
 * is a judgement, and a live version is not a duplicate of the studio version — so the two are never
 * merged into one list, and neither is removed automatically.
 */
@Composable
private fun DuplicatesTab(
    byName: List<List<MusicTrack>>,
    byAddress: List<List<MusicTrack>>,
) {
    val text = strings.settingsText

    if (byName.isEmpty() && byAddress.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = text.musicWorkshopNoDuplicates,
                color = BuroColors.TextSubtle,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(BuroSpacing.Xs)) {
        if (byAddress.isNotEmpty()) {
            item(key = "address-header") {
                Text(
                    text = text.musicWorkshopSameAddress,
                    color = BuroColors.Primary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            items(byAddress, key = { group -> "addr-${group.first().id}" }) { group ->
                DuplicateGroupRow(group)
            }
        }
        if (byName.isNotEmpty()) {
            item(key = "name-header") {
                Spacer(Modifier.height(BuroSpacing.Sm))
                Text(
                    text = text.musicWorkshopSameName,
                    color = BuroColors.Primary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            items(byName, key = { group -> "name-${group.first().id}" }) { group ->
                DuplicateGroupRow(group)
            }
        }
    }
}

@Composable
private fun DuplicateGroupRow(group: List<MusicTrack>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(BuroRadius.Small)
            .background(BuroColors.Surface)
            .padding(BuroSpacing.Sm),
    ) {
        Text(
            text = "${group.size}×  ${group.first().title}",
            color = BuroColors.Text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        group.forEach { track ->
            Text(
                // The differing names are the useful part: they say why the entries were separate.
                text = track.artistOrUnknown,
                color = BuroColors.TextSubtle,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun WorkshopTabChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick) {
        Text(
            text = label,
            color = if (selected) BuroColors.Primary else BuroColors.TextMuted,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

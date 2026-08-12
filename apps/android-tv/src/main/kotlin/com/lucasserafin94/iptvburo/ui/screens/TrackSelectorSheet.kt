package com.lucasserafin94.iptvburo.ui.screens

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import com.lucasserafin94.iptvburo.R
import com.lucasserafin94.iptvburo.ui.components.FocusSurface
import com.lucasserafin94.iptvburo.ui.theme.BuroAccent
import com.lucasserafin94.iptvburo.ui.theme.BuroCanvas
import com.lucasserafin94.iptvburo.ui.theme.BuroTextPrimary
import com.lucasserafin94.iptvburo.ui.theme.BuroTextSecondary
import java.util.Locale

/**
 * One selectable audio or subtitle track.
 *
 * [groupIndex] and [trackIndex] locate the track inside the player's own [Tracks] structure, which
 * is the only way to select it: the label is for the user and is not stable enough to key on.
 */
internal data class SelectableTrack(
    val label: String,
    val groupIndex: Int,
    val trackIndex: Int,
    val isSelected: Boolean,
)

/**
 * Choosing the audio track and the subtitles, when the stream carries more than one.
 *
 * Providers commonly ship a film with dubbed and original audio, and with subtitles the viewer has
 * to opt into. Until this existed the app played whatever the stream listed first and offered no way
 * to change it — a film in the wrong language could only be abandoned.
 *
 * The sheet shows what the stream actually contains: sections appear only where there is a real
 * choice, and a stream with one audio track and no subtitles produces no sheet at all rather than
 * an empty dialog implying the feature is broken.
 */
@Composable
@OptIn(UnstableApi::class)
internal fun TrackSelectorSheet(
    audioTracks: List<SelectableTrack>,
    subtitleTracks: List<SelectableTrack>,
    subtitlesDisabled: Boolean,
    onSelectAudio: (SelectableTrack) -> Unit,
    onSelectSubtitle: (SelectableTrack) -> Unit,
    onDisableSubtitles: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = 420.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(BuroCanvas)
                    .padding(20.dp),
        ) {
            LazyColumn(
                modifier = Modifier.heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (audioTracks.size > 1) {
                    item(key = "audio-title") {
                        SectionLabel(stringResource(R.string.player_audio_track))
                    }
                    items(audioTracks, key = { "a${it.groupIndex}:${it.trackIndex}" }) { track ->
                        TrackRow(
                            label = track.label,
                            selected = track.isSelected,
                            onClick = { onSelectAudio(track) },
                        )
                    }
                }

                if (subtitleTracks.isNotEmpty()) {
                    item(key = "subtitle-title") {
                        Spacer(Modifier.padding(top = 8.dp))
                        SectionLabel(stringResource(R.string.player_subtitles))
                    }
                    // Turning them off is a choice in the same list, not a separate control: it is
                    // the state most viewers return to, and burying it would be perverse.
                    item(key = "subtitle-off") {
                        TrackRow(
                            label = stringResource(R.string.player_subtitles_off),
                            selected = subtitlesDisabled,
                            onClick = onDisableSubtitles,
                        )
                    }
                    items(subtitleTracks, key = { "s${it.groupIndex}:${it.trackIndex}" }) { track ->
                        TrackRow(
                            label = track.label,
                            selected = track.isSelected && !subtitlesDisabled,
                            onClick = { onSelectSubtitle(track) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = BuroTextSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 6.dp),
    )
}

@Composable
private fun TrackRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FocusSurface(
        onClick = onClick,
        selected = selected,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = label,
            color = if (selected) BuroAccent else BuroTextPrimary,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
        )
    }
}

/**
 * The selectable tracks of one type that the player currently reports.
 *
 * Unsupported tracks are dropped: offering a codec the device cannot decode would produce silence
 * or a blank subtitle layer, which reads as the app being broken rather than the file being unusual.
 */
@OptIn(UnstableApi::class)
internal fun Tracks.selectableTracks(trackType: Int): List<SelectableTrack> =
    groups.withIndex()
        .filter { (_, group) -> group.type == trackType }
        .flatMap { (groupIndex, group) ->
            (0 until group.length)
                .filter { index -> group.isTrackSupported(index) }
                .map { index ->
                    SelectableTrack(
                        label = group.getTrackFormat(index).trackLabel(),
                        groupIndex = groupIndex,
                        trackIndex = index,
                        isSelected = group.isTrackSelected(index),
                    )
                }
        }

/**
 * A track's name as the viewer would recognise it.
 *
 * The language comes first because that is what someone is looking for; the stream's own label is
 * used only when it adds something the language does not already say. A track with neither falls
 * back to its index, which is unhelpful but honest — better than an empty row.
 */
@OptIn(UnstableApi::class)
private fun androidx.media3.common.Format.trackLabel(): String {
    val languageName =
        language
            ?.takeIf { it.isNotBlank() && it != C.LANGUAGE_UNDETERMINED }
            ?.let { tag ->
                runCatching { Locale.forLanguageTag(tag).displayLanguage }
                    .getOrNull()
                    ?.takeIf(String::isNotBlank)
                    ?.replaceFirstChar { it.uppercase() }
            }
    val streamLabel = label?.takeIf(String::isNotBlank)
    return when {
        languageName != null && streamLabel != null && !streamLabel.contains(languageName, true) ->
            "$languageName · $streamLabel"
        languageName != null -> languageName
        streamLabel != null -> streamLabel
        else -> language.orEmpty().ifBlank { "—" }
    }
}

/** Selects [track] of [trackType], replacing whatever was chosen for that type before. */
@OptIn(UnstableApi::class)
internal fun Player.selectTrack(
    tracks: Tracks,
    trackType: Int,
    track: SelectableTrack,
) {
    val group = tracks.groups.getOrNull(track.groupIndex) ?: return
    trackSelectionParameters =
        trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, track.trackIndex))
            // Selecting a track implies wanting that type on, which matters for subtitles: a
            // viewer who had turned them off and then picked one expects them to appear.
            .setTrackTypeDisabled(trackType, false)
            .build()
}

/** Turns subtitles off without forgetting which one was chosen, so re-enabling is one tap. */
@OptIn(UnstableApi::class)
internal fun Player.disableSubtitles() {
    trackSelectionParameters =
        trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
}

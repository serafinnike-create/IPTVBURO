package com.lucasserafin94.iptvburo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.lucasserafin94.iptvburo.domain.model.EpgEntry
import com.lucasserafin94.iptvburo.domain.model.LiveGuide
import com.lucasserafin94.iptvburo.ui.ChannelUi
import com.lucasserafin94.iptvburo.ui.LiveProgramUi
import com.lucasserafin94.iptvburo.ui.components.FocusSurface
import com.lucasserafin94.iptvburo.ui.theme.BuroGold
import com.lucasserafin94.iptvburo.ui.theme.BuroSurface
import com.lucasserafin94.iptvburo.ui.theme.BuroSurfaceRaised
import com.lucasserafin94.iptvburo.ui.theme.BuroTextSecondary
import com.lucasserafin94.iptvburo.ui.theme.BuroTextPrimary
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.delay

/**
 * The live guide on the television: channels on one side, what is on them on the other.
 *
 * The catalogue answers "what channels are there". This answers "what is on", which is the question
 * somebody has when they sit down with a remote — and it is why every satellite box has this screen.
 *
 * On a television the D-pad is how everything moves, so focus *is* selection here: landing on a row
 * with the remote shows that channel's schedule, with no separate press. That is the opposite of the
 * desktop, where the pointer crosses rows on its way elsewhere and following it fights the viewer.
 */
@Composable
internal fun LiveGuideScreen(
    channels: List<ChannelUi>,
    focusedChannelId: String?,
    scheduleFor: (String) -> List<LiveProgramUi>?,
    isLoading: (String) -> Boolean,
    onFocusChannel: (ChannelUi) -> Unit,
    onWatch: (ChannelUi) -> Unit,
    title: String,
    nowLabel: String,
    emptyLabel: String,
    watchLabel: String,
) {
    val focused = channels.firstOrNull { it.id == focusedChannelId } ?: channels.firstOrNull()

    Row(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Column(modifier = Modifier.width(380.dp).fillMaxHeight()) {
            Text(
                text = title,
                color = BuroTextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Keyed on the position as well as the id: a provider files one stream under two
                // categories and sends it twice, and with two subscriptions merged the same number
                // arrives from both. A duplicate key is a crash, not a cosmetic problem.
                itemsIndexed(channels, key = { index, channel -> "$index:${channel.id}" }) { _, channel ->
                    GuideChannelRow(
                        channel = channel,
                        selected = channel.id == focused?.id,
                        onNow =
                            scheduleFor(channel.id)
                                ?.let { entries ->
                                    LiveGuide.upcoming(entries.map(LiveProgramUi::toEntry), nowSeconds())
                                        .firstOrNull()
                                },
                        loading = isLoading(channel.id),
                        onFocus = { onFocusChannel(channel) },
                        onClick = { onWatch(channel) },
                    )
                }
            }
        }

        Spacer(Modifier.width(24.dp))

        if (focused == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(emptyLabel, color = BuroTextSecondary, fontSize = 16.sp)
            }
            return@Row
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = focused.name,
                color = BuroTextPrimary,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            FocusSurface(
                onClick = { onWatch(focused) },
                modifier = Modifier.width(280.dp),
            ) { _ ->
                Text(
                    text = "▶  $watchLabel",
                    color = BuroGold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                )
            }
            Spacer(Modifier.height(16.dp))

            val schedule = scheduleFor(focused.id)
            // The clock ticks so the bar moves and a finished programme leaves the top. Once a
            // minute is as often as any of it changes.
            var now by remember { mutableStateOf(nowSeconds()) }
            LaunchedEffect(Unit) {
                while (true) {
                    delay(60_000L)
                    now = nowSeconds()
                }
            }

            when {
                schedule == null ->
                    Text(text = "…", color = BuroTextSecondary, fontSize = 16.sp)

                else -> {
                    val upcoming = LiveGuide.upcoming(schedule.map(LiveProgramUi::toEntry), now)
                    if (upcoming.isEmpty()) {
                        Text(emptyLabel, color = BuroTextSecondary, fontSize = 16.sp)
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(upcoming) { _, program ->
                                GuideProgramRow(program = program, now = now, nowLabel = nowLabel)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One channel, with what is on it now under the name. */
@Composable
private fun GuideChannelRow(
    channel: ChannelUi,
    selected: Boolean,
    onNow: EpgEntry?,
    loading: Boolean,
    onFocus: () -> Unit,
    onClick: () -> Unit,
) {
    FocusSurface(
        onClick = onClick,
        selected = selected,
        modifier = Modifier.fillMaxWidth(),
    ) { isFocused ->
        // On a television the D-pad is how everything moves, so landing on a row *is* choosing it:
        // asking for a second press to see what is on would be the work the guide exists to remove.
        LaunchedEffect(isFocused) { if (isFocused) onFocus() }

        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                text = channel.name,
                color = if (selected) BuroGold else BuroTextPrimary,
                fontSize = 17.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Nothing rather than a placeholder while it is being fetched: "loading" on forty rows
            // at once is noise, and the row reads perfectly well without it.
            if (onNow != null) {
                Text(
                    text = onNow.title,
                    color = BuroTextSecondary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else if (loading) {
                Text(text = "…", color = BuroTextSecondary, fontSize = 14.sp)
            }
        }
    }
}

/** One programme: when it starts, what it is, and how far through it the viewer is. */
@Composable
private fun GuideProgramRow(
    program: EpgEntry,
    now: Long,
    nowLabel: String,
) {
    val onNow = LiveGuide.isOnNow(program, now)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    if (onNow) BuroSurfaceRaised else BuroSurface,
                    RoundedCornerShape(12.dp),
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = clockOf(program.startEpochSeconds),
                color = if (onNow) BuroGold else BuroTextSecondary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(72.dp),
            )
            Text(
                text = program.title,
                color = BuroTextPrimary,
                fontSize = 17.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (onNow) {
                Text(
                    text = nowLabel.uppercase(),
                    color = BuroGold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        // The bar only where the times allow it to mean something: a programme sent without a clock
        // gets none rather than one sitting at zero, which would claim it just began.
        LiveGuide.progressOf(program, now)?.takeIf { onNow }?.let { fraction ->
            Spacer(Modifier.height(8.dp))
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(BuroSurface, RoundedCornerShape(2.dp)),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(fraction)
                            .height(3.dp)
                            .background(BuroGold, RoundedCornerShape(2.dp)),
                )
            }
        }
        program.description?.takeIf { onNow && it.isNotBlank() }?.let { description ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = description,
                color = BuroTextSecondary,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The shared guide model's shape, so the arithmetic is the one every platform uses. */
private fun LiveProgramUi.toEntry(): EpgEntry =
    EpgEntry(
        title = title,
        description = description,
        startEpochSeconds = startEpochSeconds,
        endEpochSeconds = endEpochSeconds,
    )

private fun nowSeconds(): Long = System.currentTimeMillis() / 1_000L

/**
 * A start time as HH:MM in the machine's own zone, or a dash when the provider sent none.
 *
 * A dash rather than a made-up time: a guide inventing a clock is worse than one admitting the
 * provider did not say.
 */
private fun clockOf(startEpochSeconds: Long?): String {
    val seconds = startEpochSeconds ?: return "—"
    val local = LocalDateTime.ofInstant(Instant.ofEpochSecond(seconds), ZoneId.systemDefault())
    return "%02d:%02d".format(local.hour, local.minute)
}

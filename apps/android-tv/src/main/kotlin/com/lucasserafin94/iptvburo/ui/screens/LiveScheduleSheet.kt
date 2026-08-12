package com.lucasserafin94.iptvburo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text
import com.lucasserafin94.iptvburo.R
import com.lucasserafin94.iptvburo.ui.LiveProgramUi
import com.lucasserafin94.iptvburo.ui.components.FocusSurface
import com.lucasserafin94.iptvburo.ui.theme.BuroCanvas
import com.lucasserafin94.iptvburo.ui.theme.BuroGold
import com.lucasserafin94.iptvburo.ui.theme.BuroSurface
import com.lucasserafin94.iptvburo.ui.theme.BuroSurfaceRaised
import com.lucasserafin94.iptvburo.ui.theme.BuroTextPrimary
import com.lucasserafin94.iptvburo.ui.theme.BuroTextSecondary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * What is on this channel later today.
 *
 * The provider has always returned the schedule with each programme's start and end; only "now"
 * and "next" were kept, so the rest was fetched and discarded. This shows the whole list, which is
 * what other IPTV apps call the guide and what somebody deciding whether to keep watching wants.
 *
 * Read-only by design. Nothing here schedules a recording or a reminder — the app has no way to
 * honour either, and offering a button that quietly does nothing would be worse than not offering
 * it.
 */
@Composable
internal fun LiveScheduleSheet(
    channelName: String,
    schedule: List<LiveProgramUi>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
) {
    val zone = remember { ZoneId.systemDefault() }
    val formatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val nowSeconds = remember { System.currentTimeMillis() / 1_000L }

    // The programme on air now, so the list can mark it and open at it rather than at breakfast.
    val currentIndex =
        remember(schedule, nowSeconds) {
            schedule.indexOfFirst { programme ->
                val start = programme.startEpochSeconds
                val end = programme.endEpochSeconds
                start != null && end != null && nowSeconds in start until end
            }
        }
    val listState = rememberLazyListState()
    LaunchedEffect(currentIndex) {
        if (currentIndex > 0) listState.scrollToItem(currentIndex)
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .background(BuroSurfaceRaised, RoundedCornerShape(22.dp))
                .padding(20.dp),
        ) {
            Text(
                text = stringResource(R.string.live_schedule_title),
                color = BuroTextPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = channelName,
                color = BuroTextSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(14.dp))

            when {
                isLoading ->
                    Text(
                        text = stringResource(R.string.player_guide_loading),
                        color = BuroTextSecondary,
                        fontSize = 14.sp,
                    )

                // An empty guide is the provider's silence, not a failure of ours, and the wording
                // says which: plenty of playlists carry no EPG at all.
                schedule.isEmpty() ->
                    Text(
                        text = stringResource(R.string.live_schedule_empty),
                        color = BuroTextSecondary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )

                else ->
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        itemsIndexed(
                            items = schedule,
                            key = { index, programme -> "$index:${programme.startEpochSeconds}" },
                        ) { index, programme ->
                            ScheduleRow(
                                programme = programme,
                                isNow = index == currentIndex,
                                isPast =
                                    programme.endEpochSeconds?.let { it <= nowSeconds } == true,
                                time =
                                    programme.startEpochSeconds?.let { start ->
                                        Instant.ofEpochSecond(start)
                                            .atZone(zone)
                                            .format(formatter)
                                    },
                            )
                        }
                    }
            }

            Spacer(Modifier.height(14.dp))
            FocusSurface(
                onClick = onDismiss,
                shape = RoundedCornerShape(50),
                contentAlignment = Alignment.Center,
                modifier = Modifier.height(44.dp),
            ) {
                Text(
                    text = stringResource(R.string.common_close),
                    color = BuroTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }
    }
}

@Composable
private fun ScheduleRow(
    programme: LiveProgramUi,
    isNow: Boolean,
    isPast: Boolean,
    time: String?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isNow) BuroSurface else BuroCanvas.copy(alpha = 0f),
                RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Box(modifier = Modifier.width(52.dp)) {
            Text(
                // A programme with no start still gets a row — the title is worth having — but it
                // is never given an invented time.
                text = time ?: "—",
                color = if (isNow) BuroGold else BuroTextSecondary,
                fontSize = 13.sp,
                fontWeight = if (isNow) FontWeight.Bold else FontWeight.Normal,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = programme.title,
                // What has already finished is dimmed rather than hidden: it is what tells the
                // viewer where in the day they are.
                color =
                    when {
                        isNow -> BuroGold
                        isPast -> BuroTextSecondary.copy(alpha = 0.55f)
                        else -> BuroTextPrimary
                    },
                fontSize = 14.sp,
                fontWeight = if (isNow) FontWeight.Bold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            programme.description?.takeIf(String::isNotBlank)?.let { description ->
                Text(
                    text = description,
                    color = BuroTextSecondary.copy(alpha = if (isPast) 0.4f else 0.8f),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

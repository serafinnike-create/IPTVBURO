package com.lucasserafin94.iptvburo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucasserafin94.iptvburo.R
import com.lucasserafin94.iptvburo.domain.model.CacheBudget
import com.lucasserafin94.iptvburo.domain.model.CacheFillProgress
import com.lucasserafin94.iptvburo.domain.model.CacheFillState
import com.lucasserafin94.iptvburo.ui.designsystem.BuroButton
import com.lucasserafin94.iptvburo.ui.designsystem.BuroButtonStyle
import com.lucasserafin94.iptvburo.ui.theme.BuroGold
import com.lucasserafin94.iptvburo.ui.theme.BuroSurface
import com.lucasserafin94.iptvburo.ui.theme.BuroTextPrimary
import com.lucasserafin94.iptvburo.ui.theme.BuroTextSecondary

/**
 * How much artwork this device keeps, and what the fill is doing about it.
 *
 * The slider and the progress bar live in one card because they are one decision: choosing a size
 * is what starts a download, and somebody who has just moved the slider is exactly the person who
 * wants to know how long the result will take.
 */
@Composable
fun CacheSettingsCard(
    budget: CacheBudget,
    bytesUsed: Long,
    progress: CacheFillProgress,
    onChooseBudget: (Int) -> Unit,
    onStartFill: () -> Unit,
    onStopFill: () -> Unit,
    onRefreshFill: () -> Unit,
    onClearCache: () -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(BuroSurface)
            .padding(if (compact) 16.dp else 22.dp),
    ) {
        Text(
            text = stringResource(R.string.cache_settings_title),
            color = BuroTextPrimary,
            fontSize = if (compact) 17.sp else 19.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(if (compact) 4.dp else 6.dp))
        Text(
            text = stringResource(R.string.cache_settings_hint),
            color = BuroTextSecondary,
            fontSize = if (compact) 13.sp else 14.sp,
        )

        Spacer(Modifier.height(if (compact) 14.dp else 18.dp))
        CacheBudgetSlider(budget = budget, onChooseBudget = onChooseBudget)

        Spacer(Modifier.height(if (compact) 10.dp else 14.dp))
        Text(
            text = stringResource(R.string.cache_settings_usage, formatBytes(bytesUsed)),
            color = BuroTextSecondary,
            fontSize = 12.sp,
        )

        if (budget.isEnabled) {
            Spacer(Modifier.height(if (compact) 12.dp else 16.dp))
            CacheFillSection(
                progress = progress,
                onStartFill = onStartFill,
                onStopFill = onStopFill,
                onRefreshFill = onRefreshFill,
            )
        }

        if (bytesUsed > 0) {
            Spacer(Modifier.height(if (compact) 10.dp else 14.dp))
            BuroButton(onClick = onClearCache, style = BuroButtonStyle.Ghost) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Text(stringResource(R.string.cache_settings_clear))
            }
        }
    }
}

/**
 * The 0–64 GB choice.
 *
 * Local state while dragging, committed on release: writing the preference on every pixel would
 * rewrite the store dozens of times per gesture, and each write is what tells the app to reconsider
 * its cache.
 */
@Composable
private fun CacheBudgetSlider(
    budget: CacheBudget,
    onChooseBudget: (Int) -> Unit,
) {
    var dragging by remember { mutableFloatStateOf(budget.gigabytes.toFloat()) }
    // Re-seeded when the stored value changes underneath — after a first-run choice, say — so the
    // handle does not sit where the last gesture left it.
    val shown = remember(budget.gigabytes) { budget.gigabytes.toFloat() }
    val position = if (dragging != shown && dragging >= 0f) dragging else shown

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text =
                if (budget.isEnabled) {
                    stringResource(R.string.cache_settings_size, budget.gigabytes)
                } else {
                    stringResource(R.string.cache_settings_off)
                },
            color = if (budget.isEnabled) BuroGold else BuroTextSecondary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
    }
    Slider(
        value = position,
        onValueChange = { value -> dragging = value },
        onValueChangeFinished = { onChooseBudget(position.toInt()) },
        valueRange = 0f..CacheBudget.MAX_GIGABYTES.toFloat(),
        // One stop per gigabyte: the setting is expressed in whole gigabytes, and a continuous
        // slider would offer precision the stored value cannot keep.
        steps = CacheBudget.MAX_GIGABYTES - 1,
        colors = SliderDefaults.colors(
            thumbColor = BuroGold,
            activeTrackColor = BuroGold,
            inactiveTrackColor = BuroTextSecondary.copy(alpha = 0.3f),
            // The tick marks default to the Material accent, which is the purple this app does not
            // use. Made transparent rather than recoloured: sixty-four of them read as noise across
            // the track, and the figure above already says exactly which stop is chosen.
            activeTickColor = Color.Transparent,
            inactiveTickColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * The download: how far it has got, and the buttons that steer it.
 *
 * The percentage is drawn beside the bar rather than only under it, because "47%" is the thing
 * somebody glances at to decide whether to wait — the count of images is the detail behind it, and
 * the bar alone makes a long download look motionless.
 */
@Composable
private fun CacheFillSection(
    progress: CacheFillProgress,
    onStartFill: () -> Unit,
    onStopFill: () -> Unit,
    onRefreshFill: () -> Unit,
) {
    val paused = progress.state == CacheFillState.PAUSED
    // Drawn whenever there is a position worth showing, so pausing does not make the bar vanish and
    // take the viewer's sense of how far along they were with it.
    if (progress.isRunning || paused) {
        CacheFillBar(progress = progress, dimmed = paused)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.cache_fill_progress, progress.done, progress.total),
                color = BuroTextSecondary,
                fontSize = 12.sp,
            )
            // Absent rather than "0 MB/s" before an interval has been measured or once the fill
            // has paused: both mean "nothing to report", not "stopped moving".
            progress.bytesPerSecond?.takeIf { progress.isRunning }?.let { rate ->
                Text(
                    text = stringResource(R.string.cache_fill_rate, formatBytes(rate)),
                    color = BuroTextSecondary,
                    fontSize = 12.sp,
                )
            }
        }
    }

    if (progress.isRunning) {
        Spacer(Modifier.height(10.dp))
        BuroButton(onClick = onStopFill, style = BuroButtonStyle.Ghost) {
            Icon(Icons.Default.Pause, contentDescription = null)
            Text(stringResource(R.string.cache_fill_pause))
        }
        return
    }

    val label =
        when (progress.state) {
            CacheFillState.COMPLETE -> R.string.cache_fill_complete
            CacheFillState.PAUSED -> R.string.cache_fill_paused
            else -> R.string.cache_fill_idle
        }
    Spacer(Modifier.height(if (paused) 10.dp else 0.dp))
    Text(text = stringResource(label), color = BuroTextSecondary, fontSize = 12.sp)
    Spacer(Modifier.height(10.dp))

    // Resuming and re-checking are the same work — the fill skips what is already on disk — but
    // they answer different questions, so they are worded differently and shown where each applies.
    // "Retomar" continues an interrupted download; "Atualizar novamente" is for a list that has
    // gained titles since the last full pass.
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val primaryAction =
            if (progress.state == CacheFillState.COMPLETE) onRefreshFill else onStartFill
        BuroButton(onClick = primaryAction, style = BuroButtonStyle.Ghost) {
            Icon(
                if (progress.isResumable) Icons.Default.PlayArrow else Icons.Default.CloudDownload,
                contentDescription = null,
            )
            Text(
                stringResource(
                    when {
                        progress.isResumable -> R.string.cache_fill_resume
                        progress.state == CacheFillState.COMPLETE -> R.string.cache_fill_refresh
                        else -> R.string.cache_fill_start
                    },
                ),
            )
        }
        // Offered beside "Retomar" so somebody who paused long ago can start a fresh pass instead
        // of continuing an old one against a list that has since changed.
        if (progress.isResumable) {
            BuroButton(onClick = onRefreshFill, style = BuroButtonStyle.Ghost) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                // Held to one line: beside "Continuar" there is not room for two words to wrap, and
                // "Atualizar novament / e" broken across three lines is worse than a shorter label.
                Text(stringResource(R.string.cache_fill_refresh), maxLines = 1)
            }
        }
    }
}

/** The bar itself, dimmed while paused so a stopped download does not look like a running one. */
@Composable
private fun CacheFillBar(
    progress: CacheFillProgress,
    dimmed: Boolean,
) {
    val fraction = progress.fraction
    val colour = if (dimmed) BuroGold.copy(alpha = 0.55f) else BuroGold
    val track = BuroTextSecondary.copy(alpha = 0.25f)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.weight(1f)) {
            // A known total draws a bar that fills; an unknown one draws a bar that moves. A
            // determinate bar stuck at zero would look like a download that had stalled.
            if (fraction == null) {
                LinearProgressIndicator(
                    color = colour,
                    trackColor = track,
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                )
            } else {
                LinearProgressIndicator(
                    progress = { fraction },
                    color = colour,
                    trackColor = track,
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                )
            }
        }
        // Absent rather than "0%" while the total is unknown: the two mean different things.
        progress.percent?.let { percent ->
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.cache_fill_percent, percent),
                color = if (dimmed) BuroTextSecondary else BuroGold,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * A size somebody can read, in the unit that suits it.
 *
 * Whole megabytes below a gigabyte and one decimal above: "1.4 GB" is what a storage screen shows,
 * and "1434 MB" is the same fact told less clearly.
 */
internal fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val megabytes = bytes / (1024.0 * 1024.0)
    if (megabytes < 1024) return "${megabytes.toInt()} MB"
    val gigabytes = megabytes / 1024.0
    return "${(gigabytes * 10).toInt() / 10.0} GB"
}

/**
 * The offer made on first launch.
 *
 * Shown once, before anybody has browsed anything, because that is when the download is cheapest to
 * start and most useful to have finished. It explains the cost honestly — the first fill is slow on
 * a large list — and taking the offer is a choice, not a default: "Agora não" is a real answer that
 * leaves the app behaving exactly as it did before.
 */
@Composable
fun CacheFirstRunOffer(
    onAccept: (Int) -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var chosen by remember { mutableFloatStateOf(CacheBudget.DEFAULT_GIGABYTES.toFloat()) }

    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(BuroSurface)
                .padding(22.dp),
        ) {
            Text(
                text = stringResource(R.string.cache_offer_title),
                color = BuroTextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.cache_offer_body),
                color = BuroTextSecondary,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.cache_offer_warning),
                color = BuroTextSecondary,
                fontSize = 13.sp,
            )

            Spacer(Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.cache_settings_size, chosen.toInt()),
                color = BuroGold,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Slider(
                value = chosen,
                onValueChange = { value -> chosen = value },
                valueRange = 0f..CacheBudget.MAX_GIGABYTES.toFloat(),
                steps = CacheBudget.MAX_GIGABYTES - 1,
                colors = SliderDefaults.colors(
                    thumbColor = BuroGold,
                    activeTrackColor = BuroGold,
                    inactiveTrackColor = BuroTextSecondary.copy(alpha = 0.3f),
                    // Transparent for the reason given on the settings slider: sixty-four tick
                    // marks in the Material accent are both the wrong colour and unreadable.
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BuroButton(onClick = { onAccept(chosen.toInt()) }, style = BuroButtonStyle.Primary) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null)
                    Text(stringResource(R.string.cache_offer_accept))
                }
                BuroButton(onClick = onDecline, style = BuroButtonStyle.Ghost) {
                    Text(stringResource(R.string.cache_offer_decline))
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.cache_offer_later),
                color = BuroTextSecondary.copy(alpha = 0.8f),
                fontSize = 12.sp,
            )
        }
    }
}

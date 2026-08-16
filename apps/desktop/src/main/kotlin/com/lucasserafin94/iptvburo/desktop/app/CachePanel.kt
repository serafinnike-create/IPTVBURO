package com.lucasserafin94.iptvburo.desktop.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lucasserafin94.iptvburo.desktop.ui.BuroColors
import com.lucasserafin94.iptvburo.desktop.ui.BuroInteractiveRow
import com.lucasserafin94.iptvburo.desktop.ui.BuroRadius
import com.lucasserafin94.iptvburo.desktop.ui.BuroSpacing
import com.lucasserafin94.iptvburo.desktop.ui.strings
import com.lucasserafin94.iptvburo.domain.model.CacheBudget
import com.lucasserafin94.iptvburo.domain.model.CacheFillProgress
import com.lucasserafin94.iptvburo.domain.model.CacheFillState

/**
 * The sizes offered.
 *
 * A short list rather than a slider: the difference between 17 and 18 GB is not a decision anybody
 * has an opinion about, and a slider invites a precision the choice does not have. Zero is first
 * and named, so declining reads as an option rather than as dragging something to its end.
 */
private val OFFERED_SIZES = listOf(0, 2, 8, 16, 32, 64)

/**
 * The cache choice, offered on first run and again in settings.
 *
 * Shown as one component in both places so the explanation cannot drift apart from the setting it
 * explains — somebody who declines on the first run and reconsiders a week later should meet the
 * same words.
 */
@Composable
fun CacheChoicePanel(
    budget: CacheBudget,
    onChoose: (Int) -> Unit,
    /** Roughly what this library would need, already formatted. Null hides the estimate. */
    estimate: String?,
    progress: CacheFillProgress,
    bytesUsed: Long,
    onStartFill: () -> Unit,
    onPauseFill: () -> Unit,
    onResumeFill: () -> Unit,
    onCancelFill: () -> Unit,
    onClear: () -> Unit,
    /** Shown on the first-run panel, which needs an accept and a decline. Null in settings. */
    onDecline: (() -> Unit)? = null,
    /**
     * Whether the panel draws its own heading.
     *
     * False inside settings, where the section above it already carries the name — two headings
     * saying the same thing read as a mistake rather than as emphasis.
     */
    showTitle: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val text = strings.shareStrings.cache

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(BuroSpacing.Sm)) {
        if (showTitle) {
            Text(
                text = text.title,
                color = BuroColors.Text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = text.explanation,
            color = BuroColors.TextMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
        // Said before anybody commits, not after. The first fill of a large list is genuinely slow,
        // and somebody who is told afterwards has been misled rather than informed.
        Text(
            text = text.firstTimeWarning,
            color = BuroColors.TextSubtle,
            style = MaterialTheme.typography.bodySmall,
        )
        estimate?.let { size ->
            Text(
                text = text.estimate.format(size),
                color = BuroColors.TextSubtle,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Text(
            text = text.sizeLabel,
            color = BuroColors.TextMuted,
            style = MaterialTheme.typography.labelLarge,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Xs)) {
            OFFERED_SIZES.forEach { size ->
                val selected = budget.gigabytes == size
                val label = if (size == 0) text.disabled else text.gigabytes.format(size)
                BuroInteractiveRow(
                    onClick = { onChoose(size) },
                    selected = selected,
                    shape = BuroRadius.Pill,
                    contentDescription = label,
                ) { state ->
                    Text(
                        text = label,
                        modifier = Modifier.padding(horizontal = BuroSpacing.Md, vertical = BuroSpacing.Xs),
                        color =
                            when {
                                selected -> BuroColors.Primary
                                state.active -> BuroColors.Text
                                else -> BuroColors.TextMuted
                            },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        // Progress, and only while there is something to report. An idle bar sitting at zero says
        // "stuck" to anybody who did not read the label above it.
        if (budget.isEnabled && progress.state != CacheFillState.IDLE) {
            Spacer(Modifier.height(BuroSpacing.Xxs))
            CacheProgressRow(
                progress = progress,
                onPause = onPauseFill,
                onResume = onResumeFill,
                onCancel = onCancelFill,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (bytesUsed > 0) {
                Text(
                    text = text.used.format(formatBytes(bytesUsed)),
                    color = BuroColors.TextSubtle,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClear) {
                    Text(text.clear, color = BuroColors.TextSubtle)
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Sm)) {
            if (budget.isEnabled && !progress.isRunning && progress.state != CacheFillState.COMPLETE) {
                BuroInteractiveRow(
                    onClick = onStartFill,
                    selected = true,
                    shape = BuroRadius.Pill,
                    contentDescription = text.start,
                ) { _ ->
                    Text(
                        text = text.start,
                        modifier = Modifier.padding(horizontal = BuroSpacing.Lg, vertical = BuroSpacing.Xs),
                        color = BuroColors.Primary,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            onDecline?.let { decline ->
                TextButton(onClick = decline) {
                    Text(text.skip, color = BuroColors.TextSubtle)
                }
            }
        }

        // Stated rather than hidden: Coil's loader is built once per process, so a size chosen now
        // governs the *next* launch. Pretending otherwise would have somebody lower the setting,
        // watch the cache keep growing, and conclude the app ignores them.
        Text(
            text = text.restartNote,
            color = BuroColors.TextSubtle,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun CacheProgressRow(
    progress: CacheFillProgress,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
) {
    val text = strings.shareStrings.cache

    Column(verticalArrangement = Arrangement.spacedBy(BuroSpacing.Xxs)) {
        Text(
            text =
                if (progress.state == CacheFillState.COMPLETE) {
                    text.complete
                } else {
                    "${text.filling} — ${text.progress.format(progress.done, progress.total)}"
                },
            color = BuroColors.TextMuted,
            style = MaterialTheme.typography.bodySmall,
        )

        val fraction = progress.fraction
        Box(modifier = Modifier.fillMaxWidth().height(6.dp).clip(BuroRadius.Pill)) {
            if (fraction == null) {
                // Length unknown, so the bar moves rather than sitting at zero — the two look the
                // same and mean opposite things.
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = BuroColors.Primary,
                    trackColor = BuroColors.SurfaceRaised,
                )
            } else {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth(),
                    color = BuroColors.Primary,
                    trackColor = BuroColors.SurfaceRaised,
                )
            }
        }

        if (progress.state != CacheFillState.COMPLETE) {
            Row(horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Xs)) {
                TextButton(onClick = if (progress.isRunning) onPause else onResume) {
                    Text(
                        if (progress.isRunning) text.pause else text.resume,
                        color = BuroColors.Text,
                    )
                }
                TextButton(onClick = onCancel) {
                    Text(text.cancel, color = BuroColors.TextSubtle)
                }
            }
        }
    }
}

/** Bytes as the viewer would say them: "820 MB", "3,4 GB". */
internal fun formatBytes(bytes: Long): String =
    when {
        bytes >= CacheBudget.BYTES_PER_GB ->
            "%.1f GB".format(bytes.toDouble() / CacheBudget.BYTES_PER_GB)
        bytes >= 1_048_576 -> "${bytes / 1_048_576} MB"
        else -> "${bytes / 1024} KB"
    }

/**
 * The cache choice as the first run meets it: centred, with an estimate, and answerable either way.
 *
 * A panel on the home screen rather than a step in the setup flow, because the estimate is the
 * useful part and it needs a loaded catalogue to exist: "about 4 GB" can only be said once the app
 * knows how large this library is, which is after the source is connected.
 */
@Composable
fun CacheFirstRunPanel(appState: com.lucasserafin94.iptvburo.desktop.DesktopAppState) {
    val estimate =
        remember(appState.libraryTitleCount) {
            appState.libraryTitleCount
                .takeIf { count -> count > 0 }
                ?.let { count ->
                    formatBytes(
                        com.lucasserafin94.iptvburo.domain.model.CachePolicy.estimatedBytesFor(count),
                    )
                }
        }

    Box(modifier = Modifier.fillMaxWidth().padding(BuroSpacing.Lg), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier =
                Modifier
                    .width(560.dp)
                    .clip(BuroRadius.Large)
                    .background(BuroColors.Surface)
                    .padding(BuroSpacing.Lg),
        ) {
            CacheChoicePanel(
                budget = appState.cacheBudget,
                onChoose = appState::chooseCacheBudget,
                estimate = estimate,
                progress = appState.cacheProgress,
                bytesUsed = appState.cacheBytesUsed,
                onStartFill = appState::startCacheFill,
                onPauseFill = appState::pauseCacheFill,
                onResumeFill = appState::resumeCacheFill,
                onCancelFill = appState::cancelCacheFill,
                onClear = appState::clearArtworkCache,
                onDecline = appState::declineCacheChoice,
            )
        }
    }
}

/**
 * The fill, as a strip under the header that stays put while it runs.
 *
 * The panel in settings shows the same progress, but only while somebody is looking at settings —
 * which is precisely when they are not watching the download. This is the answer to "how far along
 * is it": always in the same place, readable at a glance, and carrying the two controls that matter
 * without making anybody go and find them.
 *
 * Absent when nothing is happening. A permanent strip reading 100% is furniture, and furniture is
 * what people stop seeing.
 */
@Composable
fun CacheProgressStrip(
    progress: CacheFillProgress,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRefresh: () -> Unit,
    onCancel: () -> Unit,
) {
    val text = strings.shareStrings.cache
    if (progress.state == CacheFillState.IDLE) return

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(BuroColors.Surface)
                .padding(horizontal = BuroSpacing.Lg, vertical = BuroSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Md),
    ) {
        Text(
            text = if (progress.state == CacheFillState.COMPLETE) text.complete else text.filling,
            color = BuroColors.TextMuted,
            style = MaterialTheme.typography.labelLarge,
        )

        val fraction = progress.fraction
        Box(modifier = Modifier.weight(1f).height(6.dp).clip(BuroRadius.Pill)) {
            if (fraction == null) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = BuroColors.Primary,
                    trackColor = BuroColors.SurfaceRaised,
                )
            } else {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth(),
                    color = BuroColors.Primary,
                    trackColor = BuroColors.SurfaceRaised,
                )
            }
        }

        // The percentage, which is the number people actually read off a progress bar. Absent while
        // the length is unknown, because a percentage of an unknown total would be invented.
        fraction?.let { value ->
            Text(
                text = text.percent.format((value * 100).toInt()),
                color = BuroColors.Text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = text.progress.format(progress.done, progress.total),
            color = BuroColors.TextSubtle,
            style = MaterialTheme.typography.bodySmall,
        )

        if (progress.state == CacheFillState.COMPLETE) {
            // A finished fill is only finished until the list grows. Refresh is how somebody asks
            // for the artwork of whatever has arrived since, without emptying what is already held.
            TextButton(onClick = onRefresh) { Text(text.refresh, color = BuroColors.Text) }
        } else {
            TextButton(onClick = if (progress.isRunning) onPause else onResume) {
                Text(if (progress.isRunning) text.pause else text.resume, color = BuroColors.Text)
            }
        }
        TextButton(onClick = onCancel) { Text("✕", color = BuroColors.TextSubtle) }
    }
}

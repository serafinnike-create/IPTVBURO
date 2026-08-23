package com.lucasserafin94.iptvburo.desktop.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lucasserafin94.iptvburo.desktop.download.DISPLAY_LOCALE
import com.lucasserafin94.iptvburo.desktop.download.formatRate
import com.lucasserafin94.iptvburo.desktop.ui.BuroColors
import com.lucasserafin94.iptvburo.desktop.ui.BuroInteractiveRow
import com.lucasserafin94.iptvburo.desktop.ui.BuroRadius
import com.lucasserafin94.iptvburo.desktop.ui.BuroSpacing
import com.lucasserafin94.iptvburo.desktop.ui.strings
import com.lucasserafin94.iptvburo.domain.model.CacheBudget
import com.lucasserafin94.iptvburo.domain.model.CacheFillProgress
import com.lucasserafin94.iptvburo.domain.model.CacheFillState
import kotlinx.coroutines.delay

/**
 * The sizes offered.
 *
 * A short list rather than a slider: the difference between 17 and 18 GB is not a decision anybody
 * has an opinion about, and a slider invites a precision the choice does not have. Zero is first
 * and named, so declining reads as an option rather than as dragging something to its end.
 */
private val OFFERED_SIZES = listOf(0, 2, 8, 16, 32, 64)

/**
 * How long the strip stays up after saying the fill is finished.
 *
 * Long enough to be read by somebody who was not watching the moment it changed, short enough that
 * it is not still there the next time they look up.
 */
private const val COMPLETION_VISIBLE_MILLIS = 6_000L

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
    /**
     * Fetches artwork the library has gained since the last fill.
     *
     * Offered here as well as on the strip, because the strip goes away once it has said it is
     * finished — and a control that only exists on a transient surface is one nobody can find when
     * they want it.
     */
    onRefresh: () -> Unit,
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
    var confirmingClear by remember { mutableStateOf(false) }

    if (confirmingClear) {
        AlertDialog(
            onDismissRequest = { confirmingClear = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingClear = false
                        onClear()
                    },
                ) {
                    Text(text.clear, color = BuroColors.Error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingClear = false }) {
                    Text(strings.cancel, color = BuroColors.TextMuted)
                }
            },
            title = { Text(text.clearTitle, color = BuroColors.Text) },
            text = { Text(text.clearBody, color = BuroColors.TextMuted) },
            containerColor = BuroColors.SurfaceRaised,
        )
    }

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
        // FlowRow, not Row: four pills do not fit the settings panel's width, and a Row gives the
        // early ones everything they ask for and the last one whatever is left. "16 GB" was left a
        // few pixels and filled them by breaking itself into "1 / 6 / G / B" down the panel's edge.
        //
        // Wrapping moves a pill that does not fit onto a second line instead, which is what the
        // language, region and subtitle pills on this same screen already do.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Xs),
            verticalArrangement = Arrangement.spacedBy(BuroSpacing.Xs),
        ) {
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
                        // A size is never worth breaking across lines. Even with the wrapping above,
                        // a narrower window could squeeze the last pill again, and this is what makes
                        // the label refuse to become a vertical letter stack.
                        softWrap = false,
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
                if (budget.isEnabled) {
                    TextButton(onClick = onRefresh) {
                        Text(text.refresh, color = BuroColors.Text)
                    }
                }
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
        // The count, the percentage, and how fast it is going.
        //
        // It said only "N de M", which answers "how far" only if you do the division yourself, and
        // said nothing at all about speed — so a slow fill and a stalled one looked identical. The
        // progress already carried a measured rate that nothing displayed. Same rule as the update
        // download: a figure appears once it means something and is left out until then, because an
        // estimate from the first moments swings wildly and reads as broken.
        Text(
            text =
                if (progress.state == CacheFillState.COMPLETE) {
                    text.complete
                } else {
                    buildList {
                        add("${text.filling} — ${text.progress.format(progress.done, progress.total)}")
                        progress.fraction?.let { done -> add("${(done * 100).toInt()}%") }
                        progress.bytesPerSecond?.takeIf { rate -> rate > 0L }?.let { rate ->
                            add(formatRate(rate))
                        }
                    }.joinToString("  ·  ")
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
            "%.1f GB".format(DISPLAY_LOCALE, bytes.toDouble() / CacheBudget.BYTES_PER_GB)
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
                onRefresh = appState::refreshCacheFill,
                onClear = appState::clearArtworkCache,
                onDecline = appState::declineCacheChoice,
            )
        }
    }
}

/**
 * The fill, drawn on the header's own summary line.
 *
 * A separate strip across the top pushed the whole app down and read as an interruption; this takes
 * the line the counts already occupy, so the header keeps its height and the progress sits where
 * somebody is already looking.
 *
 * Compact on purpose: a label, a short bar, the percentage, and one control. Everything else about
 * the cache lives in settings, which is where somebody goes when they want to change it rather than
 * watch it.
 */
@Composable
fun HeaderCacheProgress(
    progress: CacheFillProgress,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
) {
    val text = strings.shareStrings.cache

    // A finished fill says so and then gets out of the way, giving the line back to the counts.
    //
    // Without this the header sat at "Tudo guardado. 100%" for the rest of the session, because
    // COMPLETE is a state nothing moves out of. Reporting completion is worth a few seconds of the
    // line; keeping it there afterwards costs the viewer the information it displaced.
    var completionDismissed by remember(progress.state, progress.total) { mutableStateOf(false) }
    LaunchedEffect(progress.state, progress.total) {
        if (progress.state == CacheFillState.COMPLETE) {
            delay(COMPLETION_VISIBLE_MILLIS)
            completionDismissed = true
        }
    }
    if (progress.state == CacheFillState.COMPLETE && completionDismissed) return

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Sm),
    ) {
        Text(
            text = if (progress.state == CacheFillState.COMPLETE) text.complete else text.filling,
            color = BuroColors.TextSubtle,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
        )

        val fraction = progress.fraction
        Box(modifier = Modifier.width(HEADER_BAR_WIDTH).height(4.dp).clip(BuroRadius.Pill)) {
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

        // The model's own figure, not one computed here: it rounds down, so 999 of 1000 stays at 99
        // rather than claiming 100 while images are still arriving.
        progress.percent?.let { value ->
            Text(
                text = text.percent.format(value),
                color = BuroColors.Text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = text.progress.format(progress.done, progress.total),
            color = BuroColors.TextSubtle,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
        )

        if (progress.state != CacheFillState.COMPLETE) {
            TextButton(
                onClick = if (progress.isRunning) onPause else onResume,
                contentPadding = PaddingValues(horizontal = BuroSpacing.Xs, vertical = 0.dp),
            ) {
                Text(
                    if (progress.isRunning) text.pause else text.resume,
                    color = BuroColors.Text,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            TextButton(
                onClick = onCancel,
                contentPadding = PaddingValues(horizontal = BuroSpacing.Xs, vertical = 0.dp),
            ) {
                Text("✕", color = BuroColors.TextSubtle, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** Short enough to sit on a header line beside the text it shares with. */
private val HEADER_BAR_WIDTH = 160.dp

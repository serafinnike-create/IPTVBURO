package com.lucasserafin94.iptvburo.desktop.app

import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lucasserafin94.iptvburo.desktop.diagnostics.DiagnosticsRunner
import com.lucasserafin94.iptvburo.desktop.diagnostics.MachineDiagnostics
import com.lucasserafin94.iptvburo.desktop.ui.BuroColors
import com.lucasserafin94.iptvburo.desktop.ui.BuroInteractiveRow
import com.lucasserafin94.iptvburo.desktop.ui.BuroRadius
import com.lucasserafin94.iptvburo.desktop.ui.BuroSpacing
import com.lucasserafin94.iptvburo.desktop.ui.DesktopStrings
import com.lucasserafin94.iptvburo.domain.model.ConnectionDiagnostics.Finding
import com.lucasserafin94.iptvburo.domain.model.ConnectionDiagnostics.Severity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * The connection test, in a small window over whatever the viewer was doing.
 *
 * Somebody whose picture keeps freezing cannot tell whether the fault is their Wi-Fi, their
 * provider or the app. Left without an answer they assume the app is broken, so every line here
 * pairs a reading with a sentence saying what to do about it.
 */
@Composable
fun DiagnosticsDialog(
    text: DesktopStrings,
    runner: DiagnosticsRunner,
    onClose: () -> Unit,
) {
    var report by remember { mutableStateOf<DiagnosticsRunner.Report?>(null) }
    var running by remember { mutableStateOf(true) }
    var attempt by remember { mutableStateOf(0) }
    val network = remember(attempt) { MachineDiagnostics.network() }

    // Off the main thread: the test spends several seconds on the network on purpose, and a
    // shorter measurement would be a less honest one.
    LaunchedEffect(attempt) {
        running = true
        // Cleared before the run, not just after it. Leaving the previous readings on screen made
        // a second test look like a button that did nothing: the same numbers were still there, so
        // there was no sign anything had happened.
        report = null
        val started = System.currentTimeMillis()
        report = withContext(Dispatchers.IO) { runCatching { runner.run() }.getOrNull() }
        // A test that fails instantly — an unreachable provider — would otherwise finish before
        // the screen drew a single frame of it, which is why pressing the button looked like
        // nothing happening. The wait is for the person, not the measurement.
        val elapsed = System.currentTimeMillis() - started
        if (elapsed < MINIMUM_VISIBLE_MILLIS) delay(MINIMUM_VISIBLE_MILLIS - elapsed)
        running = false
    }

    Box(
        modifier = Modifier.fillMaxSize().background(BuroColors.Canvas.copy(alpha = 0.86f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = 560.dp)
                    .background(BuroColors.Surface, RoundedCornerShape(16.dp))
                    .padding(BuroSpacing.Lg)
                    // Bounded and scrollable: on a short window the findings would otherwise run
                    // off the bottom with no way to reach the close button.
                    .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = text.shareStrings.screens.diagnosticsTitle,
                color = BuroColors.Text,
                style = MaterialTheme.typography.titleLarge,
            )

            val current = report
            when {
                running -> {
                    Spacer(Modifier.height(BuroSpacing.Sm))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            color = BuroColors.Primary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(BuroSpacing.Sm))
                        Text(
                            text = text.shareStrings.screens.diagnosticsRunning,
                            color = BuroColors.Primary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    // Every row it is about to fill, each with its own spinner. Without this the
                    // panel emptied to a single line and then refilled, which on a fast failure
                    // looked like a button that did nothing at all — reported exactly that way.
                    Spacer(Modifier.height(BuroSpacing.Md))
                    PENDING_ROWS.forEach { id ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                color = BuroColors.TextSubtle,
                                strokeWidth = 1.5.dp,
                                modifier = Modifier.size(11.dp),
                            )
                            Spacer(Modifier.width(BuroSpacing.Sm))
                            Text(
                                text = findingLabel(text, id),
                                color = BuroColors.TextSubtle,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }

                current == null -> {
                    Spacer(Modifier.height(BuroSpacing.Md))
                    Text(
                        text = text.shareStrings.screens.diagnosticsQualityUnknown,
                        color = BuroColors.TextMuted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                else -> {
                    Spacer(Modifier.height(BuroSpacing.Md))
                    // The verdict as a banner in its own colour, not another line of text.
                    //
                    // It is the one thing somebody reading nothing else must still take away, and
                    // as a plain sentence above a list it read as a caption rather than a finding.
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(severityColor(current.overall).copy(alpha = 0.12f))
                                .padding(horizontal = BuroSpacing.Md, vertical = BuroSpacing.Sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = severityMark(current.overall),
                            color = severityColor(current.overall),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.width(BuroSpacing.Sm))
                        Column {
                            Text(
                                text = verdictLabel(text, current.overall),
                                color = severityColor(current.overall),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = qualityLabel(text, current.qualityCeiling),
                                color = BuroColors.TextMuted,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }

                    // The readings on their own surface, so the eye can tell a measurement from the
                    // verdict above it and the machine's addresses below.
                    Spacer(Modifier.height(BuroSpacing.Md))
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(BuroColors.SurfaceRaised)
                                .padding(vertical = BuroSpacing.Xs),
                    ) {
                        current.findings.forEach { finding ->
                            DiagnosticRow(text = text, finding = finding)
                        }
                    }

                    // The addresses, which are what a support call actually asks for. Quieter and
                    // apart: nobody reads these unless somebody on the telephone asks for them.
                    val facts =
                        listOfNotNull(
                            network.address?.let { text.shareStrings.screens.diagnosticsAddress to it },
                            network.netmask?.let { text.shareStrings.screens.diagnosticsNetmask to it },
                            network.gateway?.let { text.shareStrings.screens.diagnosticsGateway to it },
                        )
                    if (facts.isNotEmpty()) {
                        Spacer(Modifier.height(BuroSpacing.Md))
                        facts.forEach { (label, value) -> FactRow(label, value) }
                    }
                }
            }

            Spacer(Modifier.height(BuroSpacing.Lg))
            Row(horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Sm)) {
                BuroInteractiveRow(
                    // Guarded so a second click during a run cannot restart it half way, which
                    // would leave the first request still reading into a report nobody wants.
                    onClick = { if (!running) attempt += 1 },
                    enabled = !running,
                    selected = false,
                    shape = BuroRadius.Pill,
                    contentDescription = text.shareStrings.screens.diagnosticsRun,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = BuroSpacing.Md, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // The button says so itself while a test runs. A label that merely dimmed
                        // was indistinguishable from a button that had failed to respond.
                        if (running) {
                            CircularProgressIndicator(
                                color = BuroColors.Primary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(13.dp),
                            )
                            Spacer(Modifier.width(BuroSpacing.Sm))
                        }
                        Text(
                            text =
                                if (running) {
                                    text.shareStrings.screens.diagnosticsRunning
                                } else {
                                    text.shareStrings.screens.diagnosticsRun
                                },
                            color = if (running) BuroColors.TextSubtle else BuroColors.Primary,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
                BuroInteractiveRow(
                    onClick = onClose,
                    selected = false,
                    shape = BuroRadius.Pill,
                    contentDescription = text.shareStrings.screens.diagnosticsClose,
                ) {
                    Text(
                        text = text.shareStrings.screens.diagnosticsClose,
                        color = BuroColors.TextMuted,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = BuroSpacing.Md, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DiagnosticRow(
    text: DesktopStrings,
    finding: Finding,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = BuroSpacing.Md, vertical = 7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = severityMark(finding.severity),
                color = severityColor(finding.severity),
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.width(BuroSpacing.Sm))
            Text(
                text = findingLabel(text, finding.id),
                color = BuroColors.Text,
                style = MaterialTheme.typography.bodyMedium,
                // Weighted rather than a fixed width: the labels are longest in German, where a
                // fixed column either clipped them or left a gap in every other language.
                modifier = Modifier.weight(1f),
            )
            // The value at the end, so the numbers line up as a column somebody can scan for the
            // one that is wrong instead of reading every row.
            Text(
                text = finding.detail,
                color = BuroColors.Text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
        // The sentence, only when there is one worth reading: a healthy line needs no explaining,
        // and repeating "fine" under every reading buries the one that is not.
        adviceLabel(text, finding)?.let { advice ->
            Text(
                text = advice,
                color = BuroColors.TextSubtle,
                style = MaterialTheme.typography.labelMedium,
                // Indented past the mark, so it reads as a note on the line above rather than as
                // another reading of its own.
                modifier = Modifier.padding(start = 24.dp, top = 2.dp),
            )
        }
    }
}

@Composable
private fun FactRow(
    label: String,
    value: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = BuroSpacing.Md, vertical = 3.dp),
    ) {
        Text(
            text = label,
            color = BuroColors.TextSubtle,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            color = BuroColors.TextMuted,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

private fun severityMark(severity: Severity): String =
    when (severity) {
        Severity.GOOD -> "●"
        Severity.WARNING -> "▲"
        Severity.PROBLEM -> "■"
    }

private fun severityColor(severity: Severity): Color =
    when (severity) {
        Severity.GOOD -> BuroColors.Success
        Severity.WARNING -> BuroColors.Warning
        Severity.PROBLEM -> BuroColors.Error
    }

private fun verdictLabel(
    text: DesktopStrings,
    severity: Severity,
): String =
    when (severity) {
        Severity.GOOD -> text.shareStrings.screens.diagnosticsVerdictGood
        Severity.WARNING -> text.shareStrings.screens.diagnosticsVerdictWarning
        Severity.PROBLEM -> text.shareStrings.screens.diagnosticsVerdictProblem
    }

private fun qualityLabel(
    text: DesktopStrings,
    ceiling: String,
): String =
    when (ceiling) {
        "unstable" -> text.shareStrings.screens.diagnosticsQualityUnstable
        "sd" -> text.shareStrings.screens.diagnosticsQualitySd
        "hd" -> text.shareStrings.screens.diagnosticsQualityHd
        "uhd" -> text.shareStrings.screens.diagnosticsQualityUhd
        else -> text.shareStrings.screens.diagnosticsQualityUnknown
    }

private fun latencyLabel(
    text: DesktopStrings,
    advice: String,
): String =
    when (advice) {
        "good" -> text.shareStrings.screens.diagnosticsLatencyGood
        "fair" -> text.shareStrings.screens.diagnosticsLatencyFair
        "unstable" -> text.shareStrings.screens.diagnosticsLatencyUnstable
        else -> text.shareStrings.screens.diagnosticsLatencyUnknown
    }

private fun findingLabel(
    text: DesktopStrings,
    id: String,
): String =
    when (id) {
        "download" -> text.shareStrings.screens.diagnosticsDownload
        "ping" -> text.shareStrings.screens.diagnosticsPing
        "loss" -> text.shareStrings.screens.diagnosticsLoss
        "catalogue" -> text.shareStrings.screens.diagnosticsCatalogue
        "link" -> text.shareStrings.screens.diagnosticsConnection
        "memory" -> text.shareStrings.screens.diagnosticsMemory
        else -> id
    }

/**
 * The readings, in the order they will appear, so the panel can show them before it has them.
 *
 * Matches what DiagnosticsRunner produces. A row that appears while testing and then vanishes would
 * be worse than none, so this list and that order are meant to stay together.
 */
private val PENDING_ROWS = listOf("download", "ping", "loss", "catalogue", "link", "memory")

/**
 * How long the test stays visible even when it finishes sooner.
 *
 * Long enough to read as work happening, short enough that nobody waits on it needlessly.
 */
private const val MINIMUM_VISIBLE_MILLIS = 900L

/** The sentence under a reading, or null when the reading speaks for itself. */
private fun adviceLabel(
    text: DesktopStrings,
    finding: Finding,
): String? =
    when {
        finding.id == "download" && finding.severity != Severity.GOOD ->
            qualityLabel(text, finding.advice.orEmpty())
        // Always, not only when it is bad: "os canais trocam sem espera" is worth reading, and a
        // good reading with nothing under it looks like the app had nothing to say.
        finding.id == "ping" -> latencyLabel(text, finding.advice.orEmpty())
        finding.id == "link" && finding.advice == "wireless" -> text.shareStrings.screens.diagnosticsWireless
        finding.id == "link" && finding.advice == "none" -> text.shareStrings.screens.diagnosticsNoLink
        finding.id == "catalogue" && finding.advice == "empty" -> text.shareStrings.screens.diagnosticsCatalogueEmpty
        finding.id == "catalogue" && finding.advice == "signed-out" -> text.shareStrings.screens.diagnosticsSignedOut
        finding.id == "memory" && finding.severity != Severity.GOOD -> text.shareStrings.screens.diagnosticsLowMemory
        else -> null
    }

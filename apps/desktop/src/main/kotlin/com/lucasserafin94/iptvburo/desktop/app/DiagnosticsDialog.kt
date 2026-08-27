package com.lucasserafin94.iptvburo.desktop.app

import androidx.compose.foundation.background
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
        report = withContext(Dispatchers.IO) { runCatching { runner.run() }.getOrNull() }
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
                    Spacer(Modifier.height(BuroSpacing.Md))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            color = BuroColors.Primary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(BuroSpacing.Sm))
                        Text(
                            text = text.shareStrings.screens.diagnosticsRunning,
                            color = BuroColors.TextMuted,
                            style = MaterialTheme.typography.bodyMedium,
                        )
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
                    Spacer(Modifier.height(BuroSpacing.Sm))
                    // The verdict first. Somebody who reads nothing else must still learn whether
                    // their connection is the problem.
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

                    Spacer(Modifier.height(BuroSpacing.Md))
                    current.findings.forEach { finding ->
                        DiagnosticRow(text = text, finding = finding)
                    }

                    Spacer(Modifier.height(BuroSpacing.Md))
                    // The addresses, which are what a support call actually asks for.
                    network.address?.let { address -> FactRow(text.shareStrings.screens.diagnosticsAddress, address) }
                    network.netmask?.let { mask -> FactRow(text.shareStrings.screens.diagnosticsNetmask, mask) }
                    network.gateway?.let { gateway -> FactRow(text.shareStrings.screens.diagnosticsGateway, gateway) }
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
                    Text(
                        text = text.shareStrings.screens.diagnosticsRun,
                        color = if (running) BuroColors.TextSubtle else BuroColors.Primary,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = BuroSpacing.Md, vertical = 8.dp),
                    )
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
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
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
                modifier = Modifier.width(150.dp),
            )
            Text(
                text = finding.detail,
                color = BuroColors.TextMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        // The sentence, only when there is one worth reading: a healthy line needs no explaining,
        // and repeating "fine" under every reading buries the one that is not.
        adviceLabel(text, finding)?.let { advice ->
            Text(
                text = advice,
                color = BuroColors.TextSubtle,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 26.dp),
            )
        }
    }
}

@Composable
private fun FactRow(
    label: String,
    value: String,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = label,
            color = BuroColors.TextSubtle,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(150.dp),
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

/** The sentence under a reading, or null when the reading speaks for itself. */
private fun adviceLabel(
    text: DesktopStrings,
    finding: Finding,
): String? =
    when {
        finding.id == "download" && finding.severity != Severity.GOOD ->
            qualityLabel(text, finding.advice.orEmpty())
        finding.id == "link" && finding.advice == "wireless" -> text.shareStrings.screens.diagnosticsWireless
        finding.id == "link" && finding.advice == "none" -> text.shareStrings.screens.diagnosticsNoLink
        finding.id == "catalogue" && finding.advice == "empty" -> text.shareStrings.screens.diagnosticsCatalogueEmpty
        finding.id == "catalogue" && finding.advice == "signed-out" -> text.shareStrings.screens.diagnosticsSignedOut
        finding.id == "memory" && finding.severity != Severity.GOOD -> text.shareStrings.screens.diagnosticsLowMemory
        else -> null
    }

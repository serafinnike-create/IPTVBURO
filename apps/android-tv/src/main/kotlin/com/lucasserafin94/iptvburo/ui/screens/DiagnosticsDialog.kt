package com.lucasserafin94.iptvburo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lucasserafin94.iptvburo.R
import com.lucasserafin94.iptvburo.data.diagnostics.ConnectionTester
import com.lucasserafin94.iptvburo.domain.model.ConnectionDiagnostics.Finding
import com.lucasserafin94.iptvburo.domain.model.ConnectionDiagnostics.Severity
import com.lucasserafin94.iptvburo.ui.components.FocusSurface
import com.lucasserafin94.iptvburo.ui.theme.BuroAccent
import com.lucasserafin94.iptvburo.ui.theme.BuroDanger
import com.lucasserafin94.iptvburo.ui.theme.BuroGold
import com.lucasserafin94.iptvburo.ui.theme.BuroSurface
import com.lucasserafin94.iptvburo.ui.theme.BuroSurfaceRaised
import com.lucasserafin94.iptvburo.ui.theme.BuroTextPrimary
import com.lucasserafin94.iptvburo.ui.theme.BuroTextSecondary
import kotlinx.coroutines.delay

/**
 * The connection test.
 *
 * Somebody whose picture keeps freezing cannot tell whether the fault is their Wi-Fi, their
 * provider or the app. Left without an answer they assume the app is broken, so every line here
 * pairs a reading with a sentence saying what to do about it.
 */
@Composable
fun DiagnosticsDialog(
    runTest: suspend () -> ConnectionTester.Report?,
    onClose: () -> Unit,
) {
    var report by remember { mutableStateOf<ConnectionTester.Report?>(null) }
    var running by remember { mutableStateOf(true) }
    var attempt by remember { mutableStateOf(0) }

    LaunchedEffect(attempt) {
        running = true
        // Cleared before the run, not just after it: leaving the previous readings on screen made
        // a second test look like a button that did nothing.
        report = null
        val started = System.currentTimeMillis()
        report = runCatching { runTest() }.getOrNull()
        // A test that fails instantly — an unreachable provider — would otherwise finish before
        // the screen drew a single frame of it. The wait is for the person, not the measurement.
        val elapsed = System.currentTimeMillis() - started
        if (elapsed < MINIMUM_VISIBLE_MILLIS) delay(MINIMUM_VISIBLE_MILLIS - elapsed)
        running = false
    }

    Column(
        modifier =
            Modifier
                .widthIn(max = 620.dp)
                .background(BuroSurface, RoundedCornerShape(16.dp))
                .padding(24.dp)
                // Bounded and scrollable: on a short screen the findings would otherwise run off
                // the bottom with no way to reach the close button.
                .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = stringResource(R.string.diagnostics_title),
            color = BuroTextPrimary,
            style = MaterialTheme.typography.titleLarge,
        )

        val current = report
        when {
            running -> {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        color = BuroGold,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.diagnostics_running),
                        color = BuroGold,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                // Every row it is about to fill, each with its own spinner. Without this the panel
                // emptied to a single line and refilled, which on a fast failure looked like a
                // button that did nothing at all.
                Spacer(Modifier.height(16.dp))
                PENDING_ROWS.forEach { id ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            color = BuroTextSecondary,
                            strokeWidth = 1.5.dp,
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(labelFor(id)),
                            color = BuroTextSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            current == null -> {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.diagnostics_quality_unknown),
                    color = BuroTextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            else -> {
                Spacer(Modifier.height(14.dp))
                // The verdict as a banner in its own colour, not another line of text: it is the
                // one thing somebody reading nothing else must still take away, and as a plain
                // sentence above a list it read as a caption rather than a finding.
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colourFor(current.overall).copy(alpha = 0.12f))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = markFor(current.overall),
                        color = colourFor(current.overall),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(verdictFor(current.overall)),
                            color = colourFor(current.overall),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(qualityFor(current.qualityCeiling)),
                            color = BuroTextSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                // The readings on their own surface, so the eye can tell a measurement from the
                // verdict above it and the machine's addresses below.
                Spacer(Modifier.height(16.dp))
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(BuroSurfaceRaised)
                            .padding(vertical = 6.dp),
                ) {
                    current.findings.forEach { finding -> DiagnosticRow(finding) }
                }

                // The addresses, quieter and apart: nobody reads these unless somebody on the
                // telephone asks for them.
                val facts =
                    listOfNotNull(
                        current.address?.let { R.string.diagnostics_address to it },
                        current.netmask?.let { R.string.diagnostics_netmask to it },
                        current.gateway?.let { R.string.diagnostics_gateway to it },
                    )
                if (facts.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    facts.forEach { (label, value) -> FactRow(label, value) }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FocusSurface(
                onClick = { if (!running) attempt += 1 },
                backgroundColor = Color.Transparent,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // The button says so itself while a test runs. A label that merely dimmed was
                    // indistinguishable from a button that had failed to respond.
                    if (running) {
                        CircularProgressIndicator(
                            color = BuroGold,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text =
                            stringResource(
                                if (running) R.string.diagnostics_running else R.string.diagnostics_run,
                            ),
                        color = if (running) BuroTextSecondary else BuroGold,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            FocusSurface(onClick = onClose, backgroundColor = Color.Transparent) {
                Text(
                    text = stringResource(R.string.diagnostics_close),
                    color = BuroTextSecondary,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun DiagnosticRow(finding: Finding) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = markFor(finding.severity),
                color = colourFor(finding.severity),
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(labelFor(finding.id)),
                color = BuroTextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                // Weighted rather than a fixed width: the labels are longest in German, where a
                // fixed column either clipped them or left a gap in every other language.
                modifier = Modifier.weight(1f),
            )
            // The value at the end, so the numbers line up as a column somebody can scan for the
            // one that is wrong instead of reading every row.
            Text(
                text = finding.detail,
                color = BuroTextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
        // The sentence, only when there is one worth reading: a healthy line needs no explaining,
        // and repeating "fine" under every reading buries the one that is not.
        adviceFor(finding)?.let { advice ->
            Text(
                text = stringResource(advice),
                color = BuroTextSecondary,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 28.dp),
            )
        }
    }
}

@Composable
private fun FactRow(
    label: Int,
    value: String,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp)) {
        Text(
            text = stringResource(label),
            color = BuroTextSecondary,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            color = BuroTextSecondary,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

private fun markFor(severity: Severity): String =
    when (severity) {
        Severity.GOOD -> "●"
        Severity.WARNING -> "▲"
        Severity.PROBLEM -> "■"
    }

/** Composable because the palette reads the theme, as the rest of this app's colours do. */
@Composable
private fun colourFor(severity: Severity): Color =
    when (severity) {
        // The accent rather than a green of its own: the palette has no success colour, and
        // inventing one for this screen alone would make it look like a different app.
        Severity.GOOD -> BuroAccent
        Severity.WARNING -> BuroGold
        Severity.PROBLEM -> BuroDanger
    }

private fun verdictFor(severity: Severity): Int =
    when (severity) {
        Severity.GOOD -> R.string.diagnostics_verdict_good
        Severity.WARNING -> R.string.diagnostics_verdict_warning
        Severity.PROBLEM -> R.string.diagnostics_verdict_problem
    }

private fun qualityFor(ceiling: String): Int =
    when (ceiling) {
        "unstable" -> R.string.diagnostics_quality_unstable
        "sd" -> R.string.diagnostics_quality_sd
        "hd" -> R.string.diagnostics_quality_hd
        "uhd" -> R.string.diagnostics_quality_uhd
        else -> R.string.diagnostics_quality_unknown
    }

private fun latencyFor(advice: String): Int =
    when (advice) {
        "good" -> R.string.diagnostics_latency_good
        "fair" -> R.string.diagnostics_latency_fair
        "unstable" -> R.string.diagnostics_latency_unstable
        else -> R.string.diagnostics_latency_unknown
    }

private fun labelFor(id: String): Int =
    when (id) {
        "download" -> R.string.diagnostics_download
        "ping" -> R.string.diagnostics_ping
        "loss" -> R.string.diagnostics_loss
        "catalogue" -> R.string.diagnostics_catalogue
        "link" -> R.string.diagnostics_connection
        else -> R.string.diagnostics_memory
    }

/** The sentence under a reading, or null when the reading speaks for itself. */
private fun adviceFor(finding: Finding): Int? =
    when {
        finding.id == "download" && finding.severity != Severity.GOOD ->
            qualityFor(finding.advice.orEmpty())
        // Always, not only when it is bad: "os canais trocam sem espera" is worth reading, and a
        // good reading with nothing under it looks like the app had nothing to say.
        finding.id == "ping" -> latencyFor(finding.advice.orEmpty())
        finding.id == "link" && finding.advice == "wireless" -> R.string.diagnostics_wireless
        finding.id == "link" && finding.advice == "wired" -> R.string.diagnostics_wired
        finding.id == "link" && finding.advice == "none" -> R.string.diagnostics_no_link
        finding.id == "catalogue" && finding.advice == "empty" -> R.string.diagnostics_catalogue_empty
        finding.id == "catalogue" && finding.advice == "signed-out" -> R.string.diagnostics_signed_out
        finding.id == "memory" && finding.severity != Severity.GOOD -> R.string.diagnostics_low_memory
        else -> null
    }

/**
 * The readings, in the order they will appear, so the panel can show them before it has them.
 *
 * Matches what ConnectionTester produces. A row that appears while testing and then vanishes would
 * be worse than none, so this list and that order are meant to stay together.
 */
private val PENDING_ROWS = listOf("download", "ping", "loss", "catalogue", "link", "memory")

/**
 * How long the test stays visible even when it finishes sooner.
 *
 * Long enough to read as work happening, short enough that nobody waits on it needlessly.
 */
private const val MINIMUM_VISIBLE_MILLIS = 900L

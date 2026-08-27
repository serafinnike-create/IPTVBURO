package com.lucasserafin94.iptvburo.desktop.diagnostics

import com.lucasserafin94.iptvburo.desktop.data.CatalogueRepository
import com.lucasserafin94.iptvburo.domain.model.ConnectionDiagnostics
import com.lucasserafin94.iptvburo.domain.model.ConnectionDiagnostics.Finding
import com.lucasserafin94.iptvburo.domain.model.ConnectionDiagnostics.Severity

/**
 * The whole diagnostic, from measurement to the sentences a person reads.
 *
 * Why this exists: somebody whose picture freezes has no way to tell whether the fault is their
 * Wi-Fi, their provider, or the app. Without an answer their only options are to ask for help or to
 * assume the app is broken — and the second one is what usually happens.
 *
 * So every step produces a [Finding] with a reading and a sentence. The screen renders them; it
 * decides nothing. That keeps the thresholds in one place, shared with the television and the
 * phone, and lets these be tested without a window.
 */
class DiagnosticsRunner(
    private val repository: CatalogueRepository,
    private val machine: () -> MachineDiagnostics.Network = MachineDiagnostics::network,
    private val freeMemory: () -> Long = MachineDiagnostics::freeMemoryMegabytes,
    private val usedMemory: () -> Long = MachineDiagnostics::usedMemoryMegabytes,
) {
    /** Everything the test learned, in the order it should be read. */
    data class Report(
        val findings: List<Finding>,
        val downloadMbps: Double?,
        val pingMillis: Int?,
        val lossPercent: Double?,
        val qualityCeiling: String,
    ) {
        val overall: Severity
            get() = ConnectionDiagnostics.overall(findings)
    }

    /**
     * Runs every check and returns what to show.
     *
     * Blocking, and meant to be called off the main thread: it deliberately spends several seconds
     * on the network, because a shorter measurement is a less honest one.
     */
    fun run(): Report {
        val findings = mutableListOf<Finding>()

        val transfer = repository.measureProviderTransfer()
        val downloadMbps =
            transfer?.let { sample ->
                ConnectionDiagnostics.megabitsPerSecond(sample.bytes, sample.milliseconds)
            }
        findings += downloadFinding(downloadMbps)

        val latency = repository.measureProviderLatency()
        findings += pingFinding(latency?.medianMillis)
        findings += lossFinding(latency?.lossPercent, latency?.attempted ?: 0)

        findings += catalogueFinding()
        findings += networkFinding()
        findings += memoryFinding()

        return Report(
            findings = findings,
            downloadMbps = downloadMbps,
            pingMillis = latency?.medianMillis,
            lossPercent = latency?.lossPercent,
            qualityCeiling = ConnectionDiagnostics.qualityCeiling(downloadMbps),
        )
    }

    private fun downloadFinding(mbps: Double?): Finding {
        val severity = ConnectionDiagnostics.downloadVerdict(mbps)
        return Finding(
            id = "download",
            severity = severity,
            detail = mbps?.let { formatMbps(it) } ?: "—",
            // The advice names what the viewer will actually experience, not the number again.
            advice = ConnectionDiagnostics.qualityCeiling(mbps),
        )
    }

    private fun pingFinding(millis: Int?): Finding =
        Finding(
            id = "ping",
            severity = ConnectionDiagnostics.pingVerdict(millis),
            detail = millis?.let { "$it ms" } ?: "—",
        )

    private fun lossFinding(
        percent: Double?,
        attempted: Int,
    ): Finding =
        Finding(
            id = "loss",
            severity = ConnectionDiagnostics.packetLossVerdict(percent),
            detail = percent?.let { "${formatPercent(it)} de $attempted" } ?: "—",
        )

    /**
     * Whether the list itself loaded.
     *
     * Asked because a connection can measure perfectly while the catalogue is empty — an expired
     * subscription, a provider that moved address, a panel that answers but sends nothing. Without
     * this line the screen would tell somebody everything is fine while their app shows nothing at
     * all, which is the worst thing it could say.
     */
    private fun catalogueFinding(): Finding {
        val summary = repository.summary()
        val loaded = summary?.loadedItemCount ?: 0
        return when {
            summary == null ->
                Finding("catalogue", Severity.WARNING, "sem sessão", advice = "signed-out")
            loaded <= 0 ->
                Finding("catalogue", Severity.PROBLEM, "0", advice = "empty")
            else -> Finding("catalogue", Severity.GOOD, loaded.toString())
        }
    }

    private fun networkFinding(): Finding {
        val network = machine()
        val severity =
            when (network.kind) {
                MachineDiagnostics.LinkKind.NONE -> Severity.PROBLEM
                // Not a fault, but worth naming: wireless is the single most common explanation for
                // a connection that measures well and still stutters.
                MachineDiagnostics.LinkKind.WIRELESS -> Severity.WARNING
                else -> Severity.GOOD
            }
        return Finding(
            id = "link",
            severity = severity,
            detail = network.interfaceName ?: "—",
            advice = network.kind.name.lowercase(),
        )
    }

    private fun memoryFinding(): Finding {
        val free = freeMemory()
        return Finding(
            id = "memory",
            severity =
                if (free < ConnectionDiagnostics.LOW_MEMORY_MEGABYTES) Severity.WARNING else Severity.GOOD,
            detail = "${usedMemory()} MB / ${usedMemory() + free} MB",
        )
    }

    /** One decimal, because a second says nothing a viewer can act on. */
    private fun formatMbps(value: Double): String = "${(value * 10).toLong() / 10.0} Mbit/s"

    private fun formatPercent(value: Double): String = "${(value * 10).toLong() / 10.0}%"
}

package com.lucasserafin94.iptvburo.desktop.diagnostics

import com.lucasserafin94.iptvburo.desktop.data.CatalogueRepository
import com.lucasserafin94.iptvburo.desktop.data.LatencySample
import com.lucasserafin94.iptvburo.desktop.data.SessionXtreamRepository
import com.lucasserafin94.iptvburo.desktop.data.TransferSample
import com.lucasserafin94.iptvburo.domain.model.ConnectionDiagnostics.Severity
import com.lucasserafin94.iptvburo.desktop.model.XtreamSessionSummary
import com.lucasserafin94.iptvburo.xtream.XtreamAccount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The screen that tells somebody why their picture is freezing.
 *
 * Everything here is about not lying. A viewer who is told their connection is fine will conclude
 * the app is broken; a viewer told it is bad when it is not will pay for a faster line that changes
 * nothing. Both outcomes are worse than saying "could not measure".
 */
class DiagnosticsRunnerTest {
    /**
     * A repository that answers exactly what a test wants, including "nothing".
     *
     * Delegates the other twenty methods to a real, signed-out repository rather than stubbing
     * them: those answers are irrelevant here, and twenty hand-written stubs would drift from the
     * interface the moment it changed.
     */
    private class FakeRepository(
        private val transfer: TransferSample?,
        private val latency: LatencySample?,
        private val loadedItems: Int?,
        private val real: SessionXtreamRepository = SessionXtreamRepository(),
    ) : CatalogueRepository by real {
        override fun measureProviderTransfer(budgetMillis: Long) = transfer

        override fun measureProviderLatency(attempts: Int) = latency

        override fun summary(): XtreamSessionSummary? =
            loadedItems?.let { count ->
                XtreamSessionSummary(
                    sourceId = "diagnostico",
                    account =
                        XtreamAccount(
                            authenticated = true,
                            status = "Active",
                            isTrial = false,
                            activeConnections = 0,
                            maximumConnections = 1,
                            allowedOutputFormats = setOf("ts"),
                        ),
                    loadedItemCount = count,
                    loadedContentTypes = emptySet(),
                )
            }
    }

    private fun runner(
        transfer: TransferSample? = TransferSample(bytes = 12_500_000, milliseconds = 2_000),
        latency: LatencySample? = LatencySample(samplesMillis = List(8) { 20 }, attempted = 8),
        loadedItems: Int? = null,
        wireless: Boolean = false,
        freeMemory: Long = 2_048,
    ) = DiagnosticsRunner(
        repository = FakeRepository(transfer, latency, loadedItems),
        machine = {
            MachineDiagnostics.Network(
                kind =
                    if (wireless) MachineDiagnostics.LinkKind.WIRELESS else MachineDiagnostics.LinkKind.WIRED,
                interfaceName = if (wireless) "Wi-Fi" else "Ethernet",
                address = "192.168.1.42",
                netmask = "255.255.255.0",
                gateway = "192.168.1.1",
            )
        },
        freeMemory = { freeMemory },
        usedMemory = { 512 },
    )

    private fun finding(
        report: DiagnosticsRunner.Report,
        id: String,
    ) = assertNotNull(report.findings.firstOrNull { it.id == id }, "sem leitura de $id")

    @Test
    fun `a fast clean connection is reported as good`() {
        val report = runner().run()

        assertEquals(Severity.GOOD, finding(report, "download").severity)
        assertEquals(Severity.GOOD, finding(report, "ping").severity)
        assertEquals("uhd", report.qualityCeiling, "50 Mbit/s aguenta 4K")
    }

    /** The user's own figure: under 10 Mbit/s is where freezing starts. */
    @Test
    fun `a slow connection is called a problem and explains why`() {
        // 2 MB em 2 s = 8 Mbit/s.
        val report = runner(transfer = TransferSample(bytes = 2_000_000, milliseconds = 2_000)).run()

        assertEquals(Severity.PROBLEM, finding(report, "download").severity)
        assertEquals(Severity.PROBLEM, report.overall)
        assertEquals("unstable", report.qualityCeiling)
    }

    /**
     * A fast line that drops requests is a line that freezes, and the speed must not hide it.
     */
    @Test
    fun `packet loss decides the verdict even when the speed is excellent`() {
        val report =
            runner(latency = LatencySample(samplesMillis = List(6) { 15 }, attempted = 8)).run()

        assertEquals(Severity.GOOD, finding(report, "download").severity)
        assertEquals(Severity.PROBLEM, finding(report, "loss").severity, "2 de 8 perdidos")
        assertEquals(Severity.PROBLEM, report.overall, "a velocidade nao pode esconder a perda")
    }

    /**
     * The reading the user asked for by name: the list failing to load.
     *
     * A connection can measure perfectly while the catalogue is empty — an expired subscription, a
     * provider that moved. Telling somebody everything is fine while their app shows nothing is the
     * worst thing this screen could do.
     */
    @Test
    fun `an empty catalogue is reported even when the connection is perfect`() {
        val report = runner(loadedItems = 0).run()

        assertEquals(Severity.PROBLEM, finding(report, "catalogue").severity)
        assertEquals(Severity.PROBLEM, report.overall)
    }

    @Test
    fun `wireless is named, because it explains a connection that measures well and still stutters`() {
        val report = runner(wireless = true).run()

        val link = finding(report, "link")
        assertEquals(Severity.WARNING, link.severity)
        assertEquals("wireless", link.advice)
    }

    @Test
    fun `a wired connection is not warned about`() {
        assertEquals(Severity.GOOD, finding(runner().run(), "link").severity)
    }

    @Test
    fun `low memory is reported, since it stalls playback on a good connection`() {
        val report = runner(freeMemory = 128).run()

        assertEquals(Severity.WARNING, finding(report, "memory").severity)
    }

    /**
     * A measurement that failed is never dressed up as a healthy one.
     */
    @Test
    fun `a connection that could not be measured is not called good`() {
        val report = runner(transfer = null, latency = null).run()

        assertEquals(Severity.PROBLEM, finding(report, "download").severity)
        assertEquals("—", finding(report, "download").detail)
        assertEquals("unknown", report.qualityCeiling)
    }

    /** Every check has to produce a line, or the screen quietly drops what it cannot measure. */
    @Test
    fun `every check reports something`() {
        val report = runner().run()

        val ids = report.findings.map { it.id }.toSet()
        assertTrue(
            ids.containsAll(setOf("download", "ping", "loss", "catalogue", "link", "memory")),
            "faltam leituras: $ids",
        )
    }

    /** A repository with no session at all must not crash the screen. */
    @Test
    fun `a signed-out machine still gets a report`() {
        val report =
            DiagnosticsRunner(
                repository = SessionXtreamRepository(),
                machine = { MachineDiagnostics.Network(MachineDiagnostics.LinkKind.NONE, null, null, null, null) },
                freeMemory = { 1_024 },
                usedMemory = { 256 },
            ).run()

        assertTrue(report.findings.isNotEmpty())
        assertEquals(Severity.PROBLEM, finding(report, "link").severity)
    }
}

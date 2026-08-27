package com.lucasserafin94.iptvburo.data.diagnostics

import com.lucasserafin94.iptvburo.data.security.SourceConnectionStore
import com.lucasserafin94.iptvburo.domain.model.ConnectionDiagnostics.Severity
import com.lucasserafin94.iptvburo.stalker.StalkerCredentials
import com.lucasserafin94.iptvburo.xtream.XtreamCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The screen that tells somebody why their picture is freezing.
 *
 * Everything here is about not lying. A viewer told their connection is fine will conclude the app
 * is broken; one told it is bad when it is not will pay for a faster line that changes nothing.
 * Both are worse than "could not measure".
 *
 * The measurements come through a fake probe: what the arithmetic in the client does is covered by
 * the client's own tests, and what matters here is the verdict each reading produces.
 */
class ConnectionTesterTest {
    /** Answers exactly what a test asks for, including "nothing came back". */
    private class FakeProbe(
        private val transfer: Pair<Long, Long>? = 12_500_000L to 2_000L,
        private val latency: List<Int> = List(8) { 20 },
    ) : ProviderProbe {
        override fun transfer(
            credentials: XtreamCredentials,
            budgetMillis: Long,
        ) = transfer

        override fun latency(
            credentials: XtreamCredentials,
            attempts: Int,
        ) = latency
    }

    /** Answers what a test asks for, including "no subscription at all". */
    private class FakeStore(private val credentials: XtreamCredentials?) : SourceConnectionStore {
        override fun saveXtream(sourceId: String, credentials: XtreamCredentials) = Unit

        override fun readXtream(sourceId: String): XtreamCredentials? = credentials

        override fun saveStalker(sourceId: String, credentials: StalkerCredentials) = Unit

        override fun readStalker(sourceId: String): StalkerCredentials? = null

        override fun remove(sourceId: String) = Unit
    }

    private fun tester(
        hasCredentials: Boolean = true,
        probe: ProviderProbe = FakeProbe(),
    ): ConnectionTester =
        ConnectionTester(
            // Null: the context is only read for the local network facts, which degrade to
            // "unknown" without it. What matters here is the measurement, which is what decides
            // what somebody is told about their own connection.
            context = null,
            probe = probe,
            sourceConnectionStore =
                FakeStore(
                    if (hasCredentials) {
                        XtreamCredentials(
                            serverUrl = "http://provedor.invalid:8080",
                            username = "sintetico",
                            password = "sintetica",
                        )
                    } else {
                        null
                    },
                ),
            ioDispatcher = Dispatchers.IO,
        )

    /** requireNotNull, not JUnit's assertNotNull: only the former narrows the type. */
    private fun finding(
        report: ConnectionTester.Report,
        id: String,
    ) = requireNotNull(report.findings.firstOrNull { it.id == id }) { "sem leitura de $id" }

    @Test
    fun `every check reports something`() = runBlocking {
        val report = tester().run(sourceId = "fonte", loadedItems = 40_000)

        val ids = report.findings.map { it.id }.toSet()
        assertTrue(
            "faltam leituras: $ids",
            ids.containsAll(setOf("download", "ping", "loss", "catalogue", "link", "memory")),
        )
    }

    /**
     * The reading the owner asked for by name.
     *
     * A connection can measure perfectly while the catalogue is empty — an expired subscription, a
     * provider that moved. Telling somebody everything is fine while their app shows nothing is the
     * worst thing this screen could do.
     */
    @Test
    fun `an empty catalogue is reported as a problem`() = runBlocking {
        val report = tester().run(sourceId = "fonte", loadedItems = 0)

        assertEquals(Severity.PROBLEM, finding(report, "catalogue").severity)
        assertEquals(Severity.PROBLEM, report.overall)
    }

    @Test
    fun `a loaded catalogue is reported as fine`() = runBlocking {
        val report = tester().run(sourceId = "fonte", loadedItems = 40_000)

        assertEquals(Severity.GOOD, finding(report, "catalogue").severity)
        assertEquals("40000", finding(report, "catalogue").detail)
    }

    /** With no subscription there is nothing to measure, and the screen must still work. */
    @Test
    fun `a device with no subscription still gets a report`() = runBlocking {
        val report = tester(hasCredentials = false).run(sourceId = null, loadedItems = 0)

        assertEquals("signed-out", finding(report, "catalogue").advice)
        // Never dressed up as healthy: a measurement that did not happen is not a good one.
        assertEquals(Severity.PROBLEM, finding(report, "download").severity)
        assertEquals("—", finding(report, "download").detail)
        assertEquals("unknown", report.qualityCeiling)
    }

    /** 12,5 MB em 2 s = 50 Mbit/s, que aguenta 4K. */
    @Test
    fun `a working provider produces a measured speed`() = runBlocking {
        val report = tester().run(sourceId = "fonte", loadedItems = 40_000)

        assertEquals(Severity.GOOD, finding(report, "download").severity)
        assertEquals("uhd", report.qualityCeiling)
    }

    /** The owner's figure: under 10 Mbit/s is where freezing starts. */
    @Test
    fun `a slow provider is called a problem`() = runBlocking {
        // 2 MB em 2 s = 8 Mbit/s.
        val report =
            tester(probe = FakeProbe(transfer = 2_000_000L to 2_000L))
                .run(sourceId = "fonte", loadedItems = 40_000)

        assertEquals(Severity.PROBLEM, finding(report, "download").severity)
        assertEquals("unstable", report.qualityCeiling)
        assertEquals(Severity.PROBLEM, report.overall)
    }

    /**
     * A fast line that drops requests is a line that freezes, and the speed must not hide it.
     */
    @Test
    fun `packet loss decides the verdict even when the speed is excellent`() = runBlocking {
        val report =
            tester(probe = FakeProbe(latency = List(6) { 15 }))
                .run(sourceId = "fonte", loadedItems = 40_000)

        assertEquals(Severity.GOOD, finding(report, "download").severity)
        assertEquals(Severity.PROBLEM, finding(report, "loss").severity)
        assertEquals(Severity.PROBLEM, report.overall)
    }

    /** A provider that answered nothing is never reported as a healthy connection. */
    @Test
    fun `a provider that could not be measured is not called good`() = runBlocking {
        val report =
            tester(probe = FakeProbe(transfer = null, latency = emptyList()))
                .run(sourceId = "fonte", loadedItems = 40_000)

        assertEquals(Severity.PROBLEM, finding(report, "download").severity)
        assertEquals("—", finding(report, "download").detail)
        assertEquals(Severity.PROBLEM, finding(report, "ping").severity)
    }

    /** Memory is read from the runtime, so it always has an answer. */
    @Test
    fun `memory is always reported`() = runBlocking {
        val detail = finding(tester().run("fonte", 100), "memory").detail

        assertTrue("memoria sem valor: $detail", detail.contains("MB"))
    }
}

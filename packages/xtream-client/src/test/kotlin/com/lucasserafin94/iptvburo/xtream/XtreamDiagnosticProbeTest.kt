package com.lucasserafin94.iptvburo.xtream

import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

/**
 * The measurements behind the diagnostics screen.
 *
 * These are what turn "it keeps freezing" into an answer, so a probe that silently returns nothing
 * is worse than no screen at all: the viewer is told their connection could not be measured and
 * learns nothing, which is exactly what the first build did.
 */
class XtreamDiagnosticProbeTest {
    private lateinit var server: MockWebServer
    private lateinit var client: XtreamClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client =
            XtreamClient(
                httpClient = OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build(),
            )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun credentials() =
        XtreamCredentials(
            serverUrl = server.url("/").toString(),
            username = "sintetico",
            password = "sintetica",
        )

    @Test
    fun `a transfer reports the bytes it read and the time it took`() {
        // A body big enough to outlast the minimum sample the domain model insists on.
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("x".repeat(2_000_000)),
        )

        val sample = requireNotNull(client.measureTransfer(credentials(), budgetMillis = 6_000))

        assertTrue("leu apenas ${sample.first} bytes", sample.first > 1_000_000)
        assertTrue("tempo negativo", sample.second >= 0)
    }

    /** A provider that refuses the request tells us nothing about the connection's speed. */
    @Test
    fun `a refused transfer reports nothing rather than zero`() {
        server.enqueue(MockResponse().setResponseCode(401))

        assertNull(client.measureTransfer(credentials(), budgetMillis = 2_000))
    }

    @Test
    fun `latency reports one reading per successful round trip`() {
        repeat(4) { server.enqueue(MockResponse().setResponseCode(200)) }

        val samples = client.measureLatency(credentials(), attempts = 4)

        assertEquals("faltaram leituras: $samples", 4, samples.size)
        assertTrue("leitura negativa em $samples", samples.all { it >= 0 })
    }

    /**
     * A connection losing requests is the finding, not a failure of the test.
     *
     * Throwing here would replace "two of your four requests never came back" — which is the most
     * actionable thing this screen can say — with "the test failed".
     */
    @Test
    fun `a request that fails is counted as loss rather than thrown`() {
        server.enqueue(MockResponse().setResponseCode(200))
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(200))
        server.enqueue(MockResponse().setResponseCode(503))

        val samples = client.measureLatency(credentials(), attempts = 4)

        assertEquals("duas deviam ter falhado: $samples", 2, samples.size)
    }

    /**
     * A panel that refuses HEAD is not a panel that is losing packets.
     *
     * Plenty answer 405 or 501 to a method they have never been asked for. Counting that as loss
     * would report a perfectly healthy connection as dropping every single request, which sends the
     * viewer to reset a router that was never the problem.
     */
    @Test
    fun `a provider that refuses HEAD is measured with GET instead`() {
        // First HEAD refused, then every GET answered.
        server.enqueue(MockResponse().setResponseCode(405))
        repeat(4) { server.enqueue(MockResponse().setResponseCode(200).setBody("{}")) }

        val samples = client.measureLatency(credentials(), attempts = 3)

        assertEquals("um 405 nao e perda de pacotes: $samples", 3, samples.size)
    }

    /** An unreachable provider is a real answer, and must not take the screen down. */
    @Test
    fun `an unreachable provider yields no readings and no exception`() {
        server.shutdown()

        assertNull(client.measureTransfer(credentials(), budgetMillis = 1_000))
        assertEquals(emptyList<Int>(), client.measureLatency(credentials(), attempts = 2))
    }
}

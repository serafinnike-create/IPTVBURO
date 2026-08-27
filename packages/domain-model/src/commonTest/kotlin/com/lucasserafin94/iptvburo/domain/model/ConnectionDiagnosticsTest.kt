package com.lucasserafin94.iptvburo.domain.model

import com.lucasserafin94.iptvburo.domain.model.ConnectionDiagnostics.Finding
import com.lucasserafin94.iptvburo.domain.model.ConnectionDiagnostics.Severity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The sentences the diagnostics screen is allowed to say.
 *
 * These thresholds decide what somebody is told about their own connection, and a wrong one sends
 * them to the wrong place: told their Internet is fine, they blame the app; told it is bad when it
 * is not, they pay for a faster line that changes nothing.
 */
class ConnectionDiagnosticsTest {
    @Test
    fun `a connection below ten megabits is called a problem`() {
        // The figure the user gave: under 10 down is where freezing starts.
        assertEquals(Severity.PROBLEM, ConnectionDiagnostics.downloadVerdict(9.9))
        assertEquals(Severity.WARNING, ConnectionDiagnostics.downloadVerdict(10.0))
    }

    @Test
    fun `fifteen to twenty-five megabits is good for 1080p`() {
        assertEquals(Severity.GOOD, ConnectionDiagnostics.downloadVerdict(15.0))
        assertEquals("hd", ConnectionDiagnostics.qualityCeiling(20.0))
        assertEquals("hd", ConnectionDiagnostics.qualityCeiling(25.0))
    }

    @Test
    fun `thirty and above is offered for 4K and live television`() {
        assertEquals("uhd", ConnectionDiagnostics.qualityCeiling(30.0))
        assertEquals("hd", ConnectionDiagnostics.qualityCeiling(29.9), "29,9 nao e 4K")
    }

    @Test
    fun `a thin upload is warned about`() {
        assertEquals(Severity.WARNING, ConnectionDiagnostics.uploadVerdict(4.9))
        assertEquals(Severity.GOOD, ConnectionDiagnostics.uploadVerdict(5.0))
    }

    @Test
    fun `latency is judged apart from speed`() {
        // A fast line with terrible latency still freezes on every channel change, so speed alone
        // must not be able to produce a clean verdict.
        assertEquals(Severity.GOOD, ConnectionDiagnostics.pingVerdict(60))
        assertEquals(Severity.WARNING, ConnectionDiagnostics.pingVerdict(61))
        assertEquals(Severity.PROBLEM, ConnectionDiagnostics.pingVerdict(151))
    }

    @Test
    fun `any packet loss at all is worth saying`() {
        assertEquals(Severity.GOOD, ConnectionDiagnostics.packetLossVerdict(0.0))
        assertEquals(Severity.WARNING, ConnectionDiagnostics.packetLossVerdict(0.5))
        assertEquals(Severity.PROBLEM, ConnectionDiagnostics.packetLossVerdict(2.0))
    }

    /**
     * The verdict is the worst part, never the average.
     *
     * A connection with 200 Mbit/s and 8% loss is a connection that freezes. Averaging it into
     * "good" would tell somebody their setup is fine while they watch it stutter.
     */
    @Test
    fun `one bad reading decides the whole verdict`() {
        val findings =
            listOf(
                Finding("download", Severity.GOOD, "200 Mbit/s"),
                Finding("ping", Severity.GOOD, "12 ms"),
                Finding("loss", Severity.PROBLEM, "8%"),
            )

        assertEquals(Severity.PROBLEM, ConnectionDiagnostics.overall(findings))
    }

    @Test
    fun `a clean test says so`() {
        val findings = listOf(Finding("download", Severity.GOOD, "80 Mbit/s"))

        assertEquals(Severity.GOOD, ConnectionDiagnostics.overall(findings))
    }

    /**
     * A transfer that finished instantly measured the local buffer, not the network.
     *
     * Reporting 900 Mbit/s to somebody whose video is freezing destroys the credibility of every
     * other line on the screen, so a sample too short to mean anything reports nothing.
     */
    @Test
    fun `a sample too short to mean anything is refused`() {
        assertNull(ConnectionDiagnostics.megabitsPerSecond(bytes = 4_000_000, milliseconds = 12))
        assertNull(ConnectionDiagnostics.megabitsPerSecond(bytes = 0, milliseconds = 4_000))
    }

    @Test
    fun `a real transfer converts to megabits per second`() {
        // 12,5 MB em 2 s = 50 Mbit/s.
        val mbps = ConnectionDiagnostics.megabitsPerSecond(bytes = 12_500_000, milliseconds = 2_000)

        assertTrue(mbps != null && mbps > 49.9 && mbps < 50.1, "deu $mbps")
    }

    /** A measurement that failed must never be reported as a healthy one. */
    @Test
    fun `a missing reading is never called good`() {
        assertEquals(Severity.PROBLEM, ConnectionDiagnostics.downloadVerdict(null))
        assertEquals(Severity.PROBLEM, ConnectionDiagnostics.pingVerdict(null))
        assertEquals("unknown", ConnectionDiagnostics.qualityCeiling(null))
    }
}

package com.lucasserafin94.iptvburo.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerReconnectTest {
    @Test
    fun `a dropped connection backs off and then gives up`() {
        val delays = (0 until RECONNECT_ATTEMPTS).map { attempt -> reconnectDelayMillis(2_001, null, attempt) }

        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L), delays)
        assertNull(reconnectDelayMillis(2_001, null, RECONNECT_ATTEMPTS))
    }

    @Test
    fun `falling behind the live window reconnects at once`() {
        assertEquals(0L, reconnectDelayMillis(1_002, null, 0))
    }

    @Test
    fun `a provider refusing the stream is shown, not retried`() {
        listOf(401, 403, 404, 410).forEach { status ->
            assertNull("HTTP $status", reconnectDelayMillis(2_004, status, 0))
        }
    }

    @Test
    fun `a server error is retried`() {
        assertEquals(1_000L, reconnectDelayMillis(2_004, 503, 0))
    }

    @Test
    fun `decoder and unknown failures are shown at once`() {
        assertNull(reconnectDelayMillis(4_001, null, 0))
        assertNull(reconnectDelayMillis(1_000, null, 0))
        // Cleartext refused and file not found will fail identically every time.
        assertNull(reconnectDelayMillis(2_007, null, 0))
        assertNull(reconnectDelayMillis(2_005, null, 0))
    }
}

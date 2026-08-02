package com.lucasserafin94.iptvburo.ui.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecureWindowEffectTest {
    @Test
    fun `lease adds and later removes a flag it owns`() {
        val lease = secureWindowFlagLease(wasSecure = false)

        assertTrue(lease.addOnEnter)
        assertTrue(lease.clearOnExit)
    }

    @Test
    fun `lease preserves a secure flag owned by the host`() {
        val lease = secureWindowFlagLease(wasSecure = true)

        assertFalse(lease.addOnEnter)
        assertFalse(lease.clearOnExit)
    }
}

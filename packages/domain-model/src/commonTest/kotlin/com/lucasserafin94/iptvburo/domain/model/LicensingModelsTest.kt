package com.lucasserafin94.iptvburo.domain.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LicensingModelsTest {
    @Test
    fun `only trial active and grace grant playback`() {
        val allowed = setOf(EntitlementState.TRIAL, EntitlementState.ACTIVE, EntitlementState.GRACE)
        EntitlementState.entries.forEach { state ->
            val entitlement = Entitlement(state, "ABCD-EFGH-JKLM")
            if (state in allowed) assertTrue(entitlement.grantsPlayback) else assertFalse(entitlement.grantsPlayback)
        }
    }

    @Test
    fun `device id format excludes ambiguous characters`() {
        DeviceIdentity("ABCD-EFGH-JKLM", "public-key")
        assertFailsWith<IllegalArgumentException> { DeviceIdentity("AB01-EFGH-JKLM", "public-key") }
    }
}

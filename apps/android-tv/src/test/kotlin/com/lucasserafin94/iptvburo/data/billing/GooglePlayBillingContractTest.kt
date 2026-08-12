package com.lucasserafin94.iptvburo.data.billing

import com.lucasserafin94.iptvburo.data.licensing.canonicalGooglePlayPurchaseProof
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GooglePlayBillingContractTest {
    @Test
    fun `account id is stable opaque and scoped to the release installation`() {
        val first = obfuscatedPlayAccountId("DEVICE-CODE1", "com.lucasserafin94.iptvburo")
        val repeated = obfuscatedPlayAccountId("DEVICE-CODE1", "com.lucasserafin94.iptvburo")
        val anotherDevice = obfuscatedPlayAccountId("DEVICE-CODE2", "com.lucasserafin94.iptvburo")

        assertEquals(first, repeated)
        assertTrue(first.matches(Regex("^[a-f0-9]{64}$")))
        assertNotEquals(first, anotherDevice)
    }

    @Test
    fun `device proof binds token digest and Play account id in server wire order`() {
        assertEquals(
            "iptvburo-google-play-purchase-v1\nDEVICE-CODE1\nnonce-123\ntoken-hash\naccount-hash",
            canonicalGooglePlayPurchaseProof(
                deviceId = "DEVICE-CODE1",
                nonce = "nonce-123",
                purchaseTokenHash = "token-hash",
                accountId = "account-hash",
            ),
        )
    }
}

package com.lucasserafin94.iptvburo.data.licensing

import java.nio.charset.StandardCharsets
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignedAndroidLicenseTest {
    @Test
    fun `valid server document verifies on every supported Android API path`() {
        val fixture = signedFixture()

        val verified =
            fixture.licence.verified(
                publicKeyBase64 = fixture.publicKeyBase64,
                expectedDeviceId = DEVICE_ID,
                expectedNonce = NONCE,
            )

        assertNotNull(verified)
        assertEquals(DEVICE_ID, verified?.deviceId)
        assertEquals("2026-08-16T12:00:00Z", verified?.trialEndsAt.toString())
    }

    @Test
    fun `signed response is bound to both device and live nonce`() {
        val fixture = signedFixture()

        assertNull(fixture.licence.verified(fixture.publicKeyBase64, "ZZZZ-ZZZZ-ZZZZ", NONCE))
        assertNull(fixture.licence.verified(fixture.publicKeyBase64, DEVICE_ID, "replayed-nonce"))
    }

    @Test
    fun `editing a signed trial date fails closed`() {
        val fixture = signedFixture()
        val tampered =
            fixture.licence.copy(
                payload = fixture.licence.payload.replace("2026-08-16", "2036-08-16"),
            )

        assertNull(tampered.verified(fixture.publicKeyBase64, DEVICE_ID, NONCE))
    }

    @Test
    fun `purchase URL keeps device and selected language`() {
        val url = AndroidLicenseEndpoints.purchaseUrl(DEVICE_ID, "pt-BR")

        assertTrue(url.startsWith("https://"))
        assertTrue("device=$DEVICE_ID" in url)
        assertTrue("lang=pt" in url)
    }

    private fun signedFixture(): Fixture {
        val pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val payload =
            """{"deviceId":"$DEVICE_ID","state":"TRIAL","serverTime":"2026-08-09T12:00:00Z","trialEndsAt":"2026-08-16T12:00:00Z","nonce":"$NONCE"}"""
        val signature =
            Signature.getInstance("Ed25519").run {
                initSign(pair.private)
                update(payload.toByteArray(StandardCharsets.UTF_8))
                sign()
            }
        return Fixture(
            publicKeyBase64 = Base64.getEncoder().encodeToString(pair.public.encoded),
            licence =
                SignedAndroidLicense(
                    payload = payload,
                    signatureBase64 = Base64.getEncoder().encodeToString(signature),
                ),
        )
    }

    private data class Fixture(
        val publicKeyBase64: String,
        val licence: SignedAndroidLicense,
    )

    private companion object {
        const val DEVICE_ID = "ABCD-EFGH-JKLM"
        const val NONCE = "live-nonce"
    }
}

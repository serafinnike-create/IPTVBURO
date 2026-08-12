package com.lucasserafin94.iptvburo.data.licensing

import java.nio.charset.StandardCharsets
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidDeviceProofTest {
    @Test
    fun `canonical proof matches the worker protocol byte for byte`() {
        assertEquals(
            "iptvburo-device-proof-v1\nvalidate\nABCD-EFGH-JKLM\nAAAAAAAAAAAAAAAAAAAAAA",
            canonicalDeviceProof(
                action = AndroidDeviceProofAction.VALIDATE,
                deviceId = "ABCD-EFGH-JKLM",
                nonce = "AAAAAAAAAAAAAAAAAAAAAA",
            ),
        )
    }

    @Test
    fun `device id derivation remains stable`() {
        assertEquals(
            "ENH7-2JFH-F4B5",
            deriveDeviceId(
                publicKey = "public-key".toByteArray(StandardCharsets.UTF_8),
                installationId = "550e8400-e29b-41d4-a716-446655440000",
            ),
        )
    }

    @Test
    fun `android DER signature converts to the fixed width worker format`() {
        val keyPair =
            KeyPairGenerator.getInstance("EC").apply {
                initialize(ECGenParameterSpec("secp256r1"))
            }.generateKeyPair()
        val message = "proof-contract".toByteArray(StandardCharsets.UTF_8)
        val der =
            Signature.getInstance("SHA256withECDSA").run {
                initSign(keyPair.private)
                update(message)
                sign()
            }

        val p1363 = derEcdsaToP1363(der)

        assertEquals(64, p1363.size)
        assertTrue(
            Signature.getInstance("SHA256withECDSAinP1363Format").run {
                initVerify(keyPair.public)
                update(message)
                verify(p1363)
            },
        )
    }

    @Test
    fun `DER leading sign bytes are removed and components are left padded`() {
        val der = byteArrayOf(
            0x30,
            0x08,
            0x02,
            0x02,
            0x00,
            0x80.toByte(),
            0x02,
            0x02,
            0x00,
            0xFF.toByte(),
        )
        val expected = ByteArray(64).also {
            it[31] = 0x80.toByte()
            it[63] = 0xFF.toByte()
        }

        assertArrayEquals(expected, derEcdsaToP1363(der))
    }
}

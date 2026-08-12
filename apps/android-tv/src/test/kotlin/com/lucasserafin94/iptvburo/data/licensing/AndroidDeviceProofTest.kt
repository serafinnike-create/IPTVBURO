package com.lucasserafin94.iptvburo.data.licensing

import java.nio.charset.StandardCharsets
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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

    /**
     * The derivation is fixed, and has to match the Worker's byte for byte.
     *
     * Registration recomputes this server-side and answers `bad_identity` when the two disagree, so
     * a change here that is not mirrored in `services/license-server/src/index.js` stops every new
     * device from registering. This value was briefly changed — the public key was dropped in an
     * attempt to make the id survive a reinstall — and no unit test noticed, because both sides of
     * *this* test moved together. Only a real phone failing to register revealed it.
     */
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

    /**
     * Both inputs matter, which is what makes the value above worth pinning.
     *
     * A derivation that ignored either field would still be stable and would still pass the test
     * above; it would simply disagree with the server.
     */
    @Test
    fun `both the key and the installation id change the device id`() {
        val key = "public-key".toByteArray(StandardCharsets.UTF_8)
        val other = "another-key".toByteArray(StandardCharsets.UTF_8)
        val installation = "installation-under-test"

        assertEquals(
            deriveDeviceId(key, installation),
            deriveDeviceId(key, installation),
        )
        assertNotEquals(deriveDeviceId(key, installation), deriveDeviceId(other, installation))
        assertNotEquals(deriveDeviceId(key, installation), deriveDeviceId(key, "different"))
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

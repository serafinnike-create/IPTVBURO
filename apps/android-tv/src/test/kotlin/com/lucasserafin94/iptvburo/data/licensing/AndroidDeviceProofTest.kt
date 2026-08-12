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
     * The derivation is fixed, so a device keeps its name across app versions.
     *
     * The expected value changed once, deliberately: the id used to be derived from the Keystore
     * public key as well as the installation id, and the key pair does not survive an uninstall.
     * That made a paid licence unrecoverable on reinstall — the user was dropped back onto the
     * trial. Changing it was a one-off migration, and this test is what stops it drifting again.
     */
    @Test
    fun `device id derivation remains stable`() {
        assertEquals(
            "WQW8-D5NZ-GMFM",
            deriveDeviceId(installationId = "550e8400-e29b-41d4-a716-446655440000"),
        )
    }

    /**
     * The same installation id yields the same device id however often it is asked for.
     *
     * The whole reinstall fix rests on this: the app recomputes the id from scratch after being
     * reinstalled, and the server has to recognise the result as the device it already knows.
     */
    @Test
    fun `the same installation id always yields the same device id`() {
        val first = deriveDeviceId(installationId = "installation-under-test")
        val second = deriveDeviceId(installationId = "installation-under-test")

        assertEquals(first, second)
        assertNotEquals(first, deriveDeviceId(installationId = "a-different-installation"))
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

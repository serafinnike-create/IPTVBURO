package com.lucasserafin94.iptvburo.desktop.license

import com.sun.jna.Platform
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import kotlin.io.path.deleteRecursively
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Regression tests for the UUID + P-256 installation identity required by ADR-004. */
class DeviceFingerprintTest {
    @Test
    fun `the protected identity is stable across store instances`() {
        withIdentityFile { file ->
            val first = WindowsDeviceIdentityStore(file, TestProtector).getOrCreate()
            val second = WindowsDeviceIdentityStore(file, TestProtector).getOrCreate()

            assertEquals(first.installationId, second.installationId)
            assertEquals(first.deviceId, second.deviceId)
            assertEquals(first.publicKeyDerBase64, second.publicKeyDerBase64)
        }
    }

    @Test
    fun `separate installations receive separate UUIDs keys and public codes`() {
        val root = Files.createTempDirectory("iptvburo-identities")
        try {
            val first = WindowsDeviceIdentityStore(root.resolve("first.dpapi"), TestProtector).getOrCreate()
            val second = WindowsDeviceIdentityStore(root.resolve("second.dpapi"), TestProtector).getOrCreate()

            assertNotEquals(first.installationId, second.installationId)
            assertNotEquals(first.publicKeyDerBase64, second.publicKeyDerBase64)
            assertNotEquals(first.deviceId, second.deviceId)
        } finally {
            @OptIn(kotlin.io.path.ExperimentalPathApi::class)
            root.deleteRecursively()
        }
    }

    @Test
    fun `the public code is derived from SPKI and canonical UUID`() {
        withIdentityFile { file ->
            val identity = WindowsDeviceIdentityStore(file, TestProtector).getOrCreate()
            val publicKey = Base64.getDecoder().decode(identity.publicKeyDerBase64)

            assertEquals(identity.deviceId, deriveDeviceId(identity.installationId, publicKey))
            assertTrue(Regex("[A-Z2-9]{4}(?:-[A-Z2-9]{4}){2}").matches(identity.deviceId))
            assertTrue(identity.deviceId.none { it in "0O1I" })
        }
    }

    @Test
    fun `persisted installation ids must remain canonical random UUID v4`() {
        assertFailsWith<IllegalArgumentException> {
            canonicalUuid("33333333-3333-1333-8333-333333333333")
        }
        assertFailsWith<IllegalArgumentException> {
            canonicalUuid("33333333-3333-4333-7333-333333333333")
        }
        assertEquals(
            "33333333-3333-4333-8333-333333333333",
            canonicalUuid("33333333-3333-4333-8333-333333333333"),
        )
    }

    @Test
    fun `a proof verifies only for its exact action device and nonce`() {
        withIdentityFile { file ->
            val identity = WindowsDeviceIdentityStore(file, TestProtector).getOrCreate()
            val nonce = "ABCDEFGHIJKLMNOPQRSTUV"
            val proof = Base64.getUrlDecoder().decode(identity.proof(DeviceProofAction.VALIDATE, nonce))
            val publicKey =
                KeyFactory.getInstance("EC").generatePublic(
                    X509EncodedKeySpec(Base64.getDecoder().decode(identity.publicKeyDerBase64)),
                )

            fun verifies(action: DeviceProofAction, deviceId: String, candidateNonce: String): Boolean {
                val verifier = Signature.getInstance("SHA256withECDSAinP1363Format")
                verifier.initVerify(publicKey)
                verifier.update(
                    canonicalDeviceProof(action, deviceId, candidateNonce)
                        .toByteArray(StandardCharsets.UTF_8),
                )
                return verifier.verify(proof)
            }

            assertTrue(verifies(DeviceProofAction.VALIDATE, identity.deviceId, nonce))
            assertFalse(verifies(DeviceProofAction.REGISTER, identity.deviceId, nonce))
            assertFalse(verifies(DeviceProofAction.VALIDATE, "AAAA-BBBB-CCCC", nonce))
            assertFalse(verifies(DeviceProofAction.VALIDATE, identity.deviceId, "BBCDEFGHIJKLMNOPQRSTUV"))
        }
    }

    @Test
    fun `an unreadable protected identity fails closed instead of being replaced`() {
        withIdentityFile { file ->
            Files.createDirectories(file.parent)
            Files.write(file, byteArrayOf(1, 2, 3, 4))
            val before = Files.readAllBytes(file)

            assertFailsWith<IllegalStateException> {
                WindowsDeviceIdentityStore(file, TestProtector).getOrCreate()
            }
            assertTrue(before.contentEquals(Files.readAllBytes(file)))
        }
    }

    @Test
    fun `identity creation fails closed when platform protection is unavailable`() {
        withIdentityFile { file ->
            val unavailable =
                object : DeviceIdentityProtector {
                    override val isAvailable = false
                    override fun protect(plaintext: ByteArray) = error("must not be called")
                    override fun unprotect(protected: ByteArray) = error("must not be called")
                }

            assertFailsWith<IllegalStateException> {
                WindowsDeviceIdentityStore(file, unavailable).getOrCreate()
            }
            assertFalse(Files.exists(file))
        }
    }

    @Test
    fun `the real Windows DPAPI blob does not contain the private key or UUID`() {
        if (!Platform.isWindows()) return
        withIdentityFile { file ->
            val identity =
                WindowsDeviceIdentityStore(file, WindowsDpapiIdentityProtector).getOrCreate()
            val blob = Files.readAllBytes(file)
            val privateKey = identity.encodedPrivateKey()

            assertFalse(blob.containsSequence(privateKey))
            assertFalse(blob.containsSequence(identity.installationId.toByteArray(StandardCharsets.UTF_8)))
            assertEquals(
                identity.deviceId,
                WindowsDeviceIdentityStore(file, WindowsDpapiIdentityProtector).getOrCreate().deviceId,
            )
        }
    }

    private fun withIdentityFile(block: (java.nio.file.Path) -> Unit) {
        val root = Files.createTempDirectory("iptvburo-device-identity")
        try {
            block(root.resolve("identity.dpapi"))
        } finally {
            @OptIn(kotlin.io.path.ExperimentalPathApi::class)
            root.deleteRecursively()
        }
    }

    private fun ByteArray.containsSequence(needle: ByteArray): Boolean {
        if (needle.isEmpty() || needle.size > size) return false
        return indices.take(size - needle.size + 1).any { offset ->
            needle.indices.all { index -> this[offset + index] == needle[index] }
        }
    }

    private object TestProtector : DeviceIdentityProtector {
        override val isAvailable = true
        override fun protect(plaintext: ByteArray): ByteArray =
            ByteArray(plaintext.size) { index -> (plaintext[index].toInt() xor 0xA5).toByte() }

        override fun unprotect(protected: ByteArray): ByteArray =
            ByteArray(protected.size) { index -> (protected[index].toInt() xor 0xA5).toByte() }
    }
}

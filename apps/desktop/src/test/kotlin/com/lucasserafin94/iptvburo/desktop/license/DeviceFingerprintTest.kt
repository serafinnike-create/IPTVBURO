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

    /**
     * A fresh identity file on the same machine keeps the machine's own installation id.
     *
     * This inverts what the test here used to assert, deliberately. Two files producing two
     * unrelated identities was the trial reset: delete the three files the app keeps, and it
     * introduced itself as a computer the server had never met, which correctly granted another
     * seven days — repeatable for ever, by anyone, with no skill required.
     *
     * The installation id is derived from the Windows MachineGuid, so it survives the deletion and
     * the server can find the trial this machine already started.
     *
     * The **device id** must survive it too, and that is what this now asserts. It did not: the key
     * pair was regenerated, the device id is a hash of the key *and* the installation id, so the
     * same machine came back with a different public code. A customer who reinstalled lost a
     * thirty-day licence they had paid for and was dropped back to the trial — the anchor was
     * carrying the machine's identity while the random key threw it away again.
     */
    @Test
    fun `a new identity file on the same machine reproduces the same device`() {
        val root = Files.createTempDirectory("iptvburo-identities")
        try {
            val first = WindowsDeviceIdentityStore(root.resolve("first.dpapi"), TestProtector).getOrCreate()
            val second = WindowsDeviceIdentityStore(root.resolve("second.dpapi"), TestProtector).getOrCreate()

            // The anchor, which is what makes a returning machine recognisable. On a host with no
            // readable MachineGuid the code falls back to a random UUID, and there the old
            // behaviour — and the old hole — necessarily remains; asserting equality there would
            // fail for a reason that is not a fault.
            if (MachineAnchor.installationUuid() != null) {
                assertEquals(
                    first.installationId,
                    second.installationId,
                    "deleting the identity file must not mint a new machine",
                )
                // The whole point of the fix: the public code the server knows this machine by.
                assertEquals(
                    first.deviceId,
                    second.deviceId,
                    "reinstalling must not change the device code and lose a paid licence",
                )
                // Same key, so the signature the server pinned still verifies. Regenerating it was
                // what made the device id move.
                assertEquals(first.publicKeyDerBase64, second.publicKeyDerBase64)
            }
        } finally {
            @OptIn(kotlin.io.path.ExperimentalPathApi::class)
            root.deleteRecursively()
        }
    }

    /**
     * The reproduced key must be a working key, not merely an identical one.
     *
     * The server pins the public key at registration and verifies a signature on every request, so
     * a rebuilt identity that produced the same bytes but could not sign would fail in the least
     * obvious way possible: the right device code, refused.
     */
    @Test
    fun `a reproduced identity still signs a proof its own public key verifies`() {
        if (MachineAnchor.installationUuid() == null) return
        val root = Files.createTempDirectory("iptvburo-identities")
        try {
            val first = WindowsDeviceIdentityStore(root.resolve("first.dpapi"), TestProtector).getOrCreate()
            val second = WindowsDeviceIdentityStore(root.resolve("second.dpapi"), TestProtector).getOrCreate()

            val proof = second.proof(DeviceProofAction.VALIDATE, "abcdefghijklmnopqrstuv")
            val canonical = canonicalDeviceProof(DeviceProofAction.VALIDATE, second.deviceId, "abcdefghijklmnopqrstuv")

            // Verified against the *first* identity's key, which is the one the server would hold.
            val publicKey =
                KeyFactory.getInstance("EC")
                    .generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(first.publicKeyDerBase64)))
            val verifier =
                Signature.getInstance("SHA256withECDSAinP1363Format").apply {
                    initVerify(publicKey)
                    update(canonical.toByteArray(StandardCharsets.UTF_8))
                }

            assertTrue(
                verifier.verify(Base64.getUrlDecoder().decode(proof)),
                "the rebuilt identity must sign what the server already trusts",
            )
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

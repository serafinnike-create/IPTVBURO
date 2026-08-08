package com.lucasserafin94.iptvburo.desktop.license

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * That the licence system is actually connected to the app.
 *
 * These are guard tests rather than behaviour tests. Each one pins a property whose absence would be
 * silent: the app would compile, launch, and simply never enforce anything — which is how this stood
 * for most of the work, with every piece written and none of them wired together.
 */
class LicenseWiringTest {

    @Test
    fun `the build has a server key to verify against`() {
        // An empty key means the client cannot verify any answer, so it fails closed and blocks
        // every customer. Shipping that would be worse than shipping no licence check at all.
        assertTrue(
            LicenseEndpoints.SERVER_PUBLIC_KEY.isNotBlank(),
            "SERVER_PUBLIC_KEY is empty: every launch would be blocked",
        )
        assertTrue(LicenseEndpoints.isConfigured)
    }

    @Test
    fun `the public key is a well formed Ed25519 key`() {
        val decoded = java.util.Base64.getDecoder().decode(LicenseEndpoints.SERVER_PUBLIC_KEY)

        // An X.509-wrapped Ed25519 public key is 44 bytes. A private key pasted here by mistake
        // would be longer — and would be a catastrophe, since this constant ships in the binary.
        assertEquals(44, decoded.size, "not an Ed25519 public key")

        val key = java.security.KeyFactory.getInstance("Ed25519")
            .generatePublic(java.security.spec.X509EncodedKeySpec(decoded))
        assertEquals("EdDSA", key.algorithm)
    }

    @Test
    fun `no private key material is present in the client`() {
        // The signing key must never leave the Worker. If it were ever pasted into this file, every
        // licence in existence becomes forgeable by anyone holding the binary.
        val source = Files.readString(
            java.nio.file.Path.of(
                "src/main/kotlin/com/lucasserafin94/iptvburo/desktop/license/LicenseEndpoints.kt",
            ),
        )

        assertFalse(source.contains("PRIVATE KEY"), "a private key is in the client")
        assertFalse(source.contains("MC4CAQAw"), "an Ed25519 private key prefix is in the client")
    }

    @Test
    fun `every endpoint points at the configured domain`() {
        val endpoints = listOf(
            LicenseEndpoints.VALIDATE,
            LicenseEndpoints.REGISTER,
            LicenseEndpoints.REDEEM,
            LicenseEndpoints.purchaseUrl("FP86-XARB-9JZW"),
        )

        endpoints.forEach { url ->
            assertTrue(url.startsWith("https://"), "$url is not https")
            assertTrue(url.contains(LicenseEndpoints.DOMAIN), "$url does not use DOMAIN")
        }
    }

    @Test
    fun `endpoints are https, never plain http`() {
        // A licence answer travelling in clear text can be replaced in transit. The signature would
        // catch a forged one, but downgrade is the kind of thing worth refusing structurally.
        val source = Files.readString(
            java.nio.file.Path.of(
                "src/main/kotlin/com/lucasserafin94/iptvburo/desktop/license/LicenseEndpoints.kt",
            ),
        )

        assertFalse(source.contains("http://"), "a plain-http endpoint is present")
    }

    /**
     * The purchase URL carries the app's language.
     *
     * Without it the site guesses from the browser header, which is wrong for the case that matters
     * most: the QR code opens on a phone whose language may have nothing to do with the computer
     * being licensed.
     */
    @Test
    fun `the purchase url carries the language when given one`() {
        val url = LicenseEndpoints.purchaseUrl("FP86-XARB-9JZW", "en")

        assertTrue(url.contains("device=FP86-XARB-9JZW"))
        assertTrue(url.contains("lang=en"), "the app's language must travel with the link")
    }

    @Test
    fun `the purchase url omits the language when there is none`() {
        val url = LicenseEndpoints.purchaseUrl("FP86-XARB-9JZW")

        assertFalse(url.contains("lang="), "an absent language should not become an empty parameter")
    }

    /**
     * The device identifier is stable across calls.
     *
     * Everything is keyed on it: the trial, the payment, the manual grant. An identifier that
     * changed between launches would hand every customer a fresh trial for ever, and would make a
     * paid licence stop working for no visible reason.
     */
    @Test
    fun `the device identifier is stable`() {
        withTemporaryIdentity { _, identities ->
            val first = identities.getOrCreate().deviceId
            val second = identities.getOrCreate().deviceId

            assertEquals(first, second)
            assertTrue(first.matches(Regex("^[A-Z2-9]{4}(-[A-Z2-9]{4}){2}$")), "unexpected shape: $first")
        }
    }

    @Test
    fun `the device identifier matches what the server accepts`() {
        // The server validates against this exact pattern before touching a query. A client that
        // produced anything else would be refused at every endpoint with no useful error.
        val serverPattern = Regex("^[A-Z2-9]{4}(-[A-Z2-9]{4}){2}$")

        withTemporaryIdentity { _, identities ->
            assertTrue(serverPattern.matches(identities.getOrCreate().deviceId))
        }
    }

    /**
     * The installation holds a signing key, and the public half is what travels.
     *
     * This is what makes the device code safe to print on a screen and read out over the phone: on
     * its own it proves nothing, because every request is signed by a private key that never leaves
     * this machine.
     */
    @Test
    fun `the installation identity carries a public key and keeps the private one`() {
        withTemporaryIdentity { _, identities ->
            val identity = identities.getOrCreate()

            assertTrue(identity.publicKeyDerBase64.isNotBlank())
            val decoded = java.util.Base64.getDecoder().decode(identity.publicKeyDerBase64)
            val key = java.security.KeyFactory.getInstance("EC")
                .generatePublic(java.security.spec.X509EncodedKeySpec(decoded))
            assertEquals("EC", key.algorithm)

            // A proof over a challenge must be produced without exposing the private key.
            val nonce = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(ByteArray(16).also { java.security.SecureRandom().nextBytes(it) })
            val proof = identity.proof(DeviceProofAction.VALIDATE, nonce = nonce)
            assertTrue(proof.isNotBlank())

            val other = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(ByteArray(16).also { java.security.SecureRandom().nextBytes(it) })
            assertTrue(proof != identity.proof(DeviceProofAction.VALIDATE, nonce = other))
        }
    }

    /**
     * A licence check never throws.
     *
     * An exception at launch would stop a paying customer opening a program they own — worse than
     * any bypass this system exists to prevent.
     */
    @Test
    fun `a check returns a decision rather than throwing`() {
        withTemporaryIdentity { root, identities ->
            val preferences = java.util.prefs.Preferences.userRoot()
                .node("com/lucasserafin94/iptvburo/test/${System.nanoTime()}")
            try {
                val client = LicenseClient(
                    store = LicenseStore(
                        appDirectory = root.resolve("licence"),
                        homeMarker = root.resolve("home-marker"),
                        preferences = preferences,
                    ),
                    identityProvider = identities,
                    // No network in a unit test. An unconfigured verifier must return a blocked
                    // decision synchronously and fail closed.
                    server = LicenseServerConfiguration("", "", "", ""),
                )

                val status = client.check()

                assertNotNull(status)
                assertTrue(status.allowsUse || status.blockReason != null)
            } finally {
                runCatching { preferences.removeNode() }
            }
        }
    }

    private fun withTemporaryIdentity(
        block: (java.nio.file.Path, WindowsDeviceIdentityStore) -> Unit,
    ) {
        val root = Files.createTempDirectory("buro-device-identity-test")
        val protector =
            object : DeviceIdentityProtector {
                override val isAvailable = true
                override fun protect(plaintext: ByteArray): ByteArray = plaintext.clone()
                override fun unprotect(protected: ByteArray): ByteArray = protected.clone()
            }
        try {
            block(root, WindowsDeviceIdentityStore(root.resolve("identity.dpapi"), protector))
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}

package com.lucasserafin94.iptvburo.desktop.license

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lucasserafin94.iptvburo.domain.model.LicenseBlockReason
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.Collections
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.io.path.deleteRecursively
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest

/** End-to-end client tests for proof-bearing requests and the clock-rollback fallback. */
class LicenseClientTest {
    private val serverKeys: KeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    private val serverPublicKey = Base64.getEncoder().encodeToString(serverKeys.public.encoded)
    private val serverNow = Instant.parse("2026-08-08T12:00:00Z")

    @Test
    fun `unknown device validates then registers with proof and without machine data`() {
        withState { store, identityStore ->
            val identity = identityStore.getOrCreate()
            val captured = Collections.synchronizedList(mutableListOf<Pair<String, JsonObject>>())

            MockWebServer().use { mockServer ->
                mockServer.dispatcher =
                    object : Dispatcher() {
                        override fun dispatch(request: RecordedRequest): MockResponse {
                            val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
                            captured += request.path.orEmpty() to body
                            if (request.path == "/v1/validate") {
                                return json("""{"error":"not_registered"}""", 404)
                            }
                            val nonce = body.get("nonce").asString
                            return json(signedEnvelope(identity.deviceId, nonce, "TRIAL"))
                        }
                    }

                val status =
                    client(mockServer, store, identityStore, serverNow).check()

                assertTrue(status.allowsUse)
                assertEquals(identity.deviceId, status.deviceId)
            }

            assertEquals(listOf("/v1/validate", "/v1/register"), captured.map { it.first })
            val validation = captured[0].second
            val registration = captured[1].second
            assertFalse(validation.has("macAddress"))
            assertFalse(registration.has("macAddress"))
            val profile = validation.getAsJsonObject("deviceProfile")
            assertEquals("WINDOWS_PC", profile.get("deviceType").asString)
            assertEquals("WINDOWS", profile.get("platform").asString)
            assertTrue(profile.get("appVersion").asString.isNotBlank())
            assertFalse(profile.has("hostname"))
            assertFalse(profile.has("serialNumber"))
            assertFalse(profile.has("machineGuid"))
            assertFalse(validation.has("installationId"))
            assertFalse(validation.has("publicKey"))
            assertEquals(identity.installationId, registration.get("installationId").asString)
            assertEquals(identity.publicKeyDerBase64, registration.get("publicKey").asString)
            assertProof(validation, identity, DeviceProofAction.VALIDATE)
            assertProof(registration, identity, DeviceProofAction.REGISTER)
        }
    }

    /**
     * A device whose row predates cryptographic identity registers instead of giving up.
     *
     * The server answers `identity_upgrade_required` because validation has no key to check a proof
     * against. Registration is exactly what supplies one — so treating the refusal as fatal left the
     * device unable to register, because registering was the thing being refused.
     *
     * This happened to a real machine. It showed "could not verify your licence", which reads as a
     * network problem, and no amount of retrying or reinstalling could ever have fixed it.
     */
    @Test
    fun `a legacy row without a key is recovered by registering`() {
        withState { store, identityStore ->
            val identity = identityStore.getOrCreate()
            val visited = Collections.synchronizedList(mutableListOf<String>())

            MockWebServer().use { mockServer ->
                mockServer.dispatcher =
                    object : Dispatcher() {
                        override fun dispatch(request: RecordedRequest): MockResponse {
                            val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
                            visited += request.path.orEmpty()
                            if (request.path == "/v1/validate") {
                                return json("""{"error":"identity_upgrade_required"}""", 409)
                            }
                            val nonce = body.get("nonce").asString
                            return json(signedEnvelope(identity.deviceId, nonce, "TRIAL"))
                        }
                    }

                val status = client(mockServer, store, identityStore, serverNow).check()

                assertTrue(status.allowsUse, "the device must recover rather than stay blocked")
            }

            assertEquals(listOf("/v1/validate", "/v1/register"), visited)
        }
    }

    /**
     * A refusal the client cannot act on is not reported as a network failure.
     *
     * UNREACHABLE puts "check your connection" on screen. Sending somebody to look at a working
     * router for a server-side refusal wastes their time and produces a support message; the device
     * code and the purchase route are what they can actually act on.
     */
    @Test
    fun `a refused registration is not reported as a network problem`() {
        withState { store, identityStore ->
            MockWebServer().use { mockServer ->
                mockServer.dispatcher =
                    object : Dispatcher() {
                        override fun dispatch(request: RecordedRequest): MockResponse =
                            if (request.path == "/v1/validate") {
                                json("""{"error":"not_registered"}""", 404)
                            } else {
                                json("""{"error":"identity_upgrade_required"}""", 409)
                            }
                    }

                val status = client(mockServer, store, identityStore, serverNow).check()

                assertFalse(status.allowsUse)
                assertEquals(LicenseBlockReason.NOT_ACTIVATED, status.blockReason)
            }
        }
    }

    /** A genuinely unreachable server still says so, so the two remain distinguishable. */
    @Test
    fun `a server that cannot be reached is still reported as unreachable`() {
        withState { store, identityStore ->
            val mockServer = MockWebServer()
            mockServer.start()
            val licenceClient = client(mockServer, store, identityStore, serverNow)
            // Shut down before the call, so the connection is refused rather than answered.
            mockServer.shutdown()

            val status = licenceClient.check()

            assertFalse(status.allowsUse)
            assertEquals(LicenseBlockReason.UNREACHABLE, status.blockReason)
        }
    }

    @Test
    fun `redeem signs the exact redeem action and never sends a MAC`() {
        withState { store, identityStore ->
            val identity = identityStore.getOrCreate()
            var captured: JsonObject? = null
            MockWebServer().use { mockServer ->
                mockServer.dispatcher =
                    object : Dispatcher() {
                        override fun dispatch(request: RecordedRequest): MockResponse {
                            val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
                            captured = body
                            return json(
                                signedEnvelope(
                                    deviceId = identity.deviceId,
                                    nonce = body.get("nonce").asString,
                                    state = "ACTIVE",
                                ),
                            )
                        }
                    }

                val status =
                    assertNotNull(
                        client(mockServer, store, identityStore, serverNow).redeem(" abcd-efgh "),
                    )
                assertTrue(status.allowsUse)
            }

            val request = assertNotNull(captured)
            assertEquals("ABCD-EFGH", request.get("key").asString)
            assertFalse(request.has("macAddress"))
            assertProof(request, identity, DeviceProofAction.REDEEM)
        }
    }

    @Test
    fun `offline fallback blocks when the local clock moved backwards beyond tolerance`() {
        val status = offlineStatus(serverNow.minus(LicensePolicyClock.tolerance).minus(1.seconds))

        assertFalse(status.allowsUse)
        assertEquals(LicenseBlockReason.NEEDS_VERIFICATION, status.blockReason)
        assertTrue(status.offline)
        assertTrue(status.clockSuspect)
    }

    @Test
    fun `offline fallback still allows the exact backward clock tolerance`() {
        val status = offlineStatus(serverNow.minus(LicensePolicyClock.tolerance))

        assertTrue(status.allowsUse)
        assertTrue(status.offline)
        assertFalse(status.clockSuspect)
    }

    @Test
    fun `a legacy future local verification time cannot extend offline grace`() {
        val status =
            offlineStatus(
                localNow = serverNow.plus(15.days),
                storedVerifiedAt = serverNow.plus(365.days),
            )

        assertFalse(status.allowsUse)
        assertEquals(LicenseBlockReason.NEEDS_VERIFICATION, status.blockReason)
        assertFalse(status.clockSuspect)
    }

    private fun offlineStatus(
        localNow: Instant,
        storedVerifiedAt: Instant = serverNow,
    ): LicenseStatus {
        var result: LicenseStatus? = null
        withState { store, identityStore ->
            val identity = identityStore.getOrCreate()
            val payload =
                """{"deviceId":"${identity.deviceId}","state":"TRIAL","serverTime":"$serverNow","trialEndsAt":"${serverNow.plus(7.days)}"}"""
            store.write(
                StoredLicense(
                    license = SignedLicense(payload, signServerPayload(payload)),
                    lastVerifiedAt = storedVerifiedAt,
                ),
            )

            MockWebServer().use { mockServer ->
                mockServer.enqueue(json("""{"error":"temporarily_unavailable"}""", 503))
                result = client(mockServer, store, identityStore, localNow).check()
            }
        }
        return assertNotNull(result)
    }

    private fun client(
        mockServer: MockWebServer,
        store: LicenseStore,
        identityStore: WindowsDeviceIdentityStore,
        now: Instant,
    ): LicenseClient {
        val root = mockServer.url("/").toString().removeSuffix("/")
        return LicenseClient(
            store = store,
            identityProvider = identityStore,
            server =
                LicenseServerConfiguration(
                    registerUrl = "$root/v1/register",
                    validateUrl = "$root/v1/validate",
                    redeemUrl = "$root/v1/redeem",
                    publicKeyBase64 = serverPublicKey,
                ),
            clock = { now },
        )
    }

    private fun signedEnvelope(deviceId: String, nonce: String, state: String): String {
        val timeFields =
            if (state == "TRIAL") {
                "\"trialEndsAt\":\"${serverNow.plus(7.days)}\""
            } else {
                "\"expiresAt\":\"${serverNow.plus(730.days)}\""
            }
        val payload =
            """{"deviceId":"$deviceId","nonce":"$nonce","serverTime":"$serverNow","state":"$state",$timeFields}"""
        return JsonObject().apply {
            addProperty("payload", payload)
            addProperty("signature", signServerPayload(payload))
        }.toString()
    }

    private fun signServerPayload(payload: String): String {
        val signer = Signature.getInstance("Ed25519")
        signer.initSign(serverKeys.private)
        signer.update(payload.toByteArray(StandardCharsets.UTF_8))
        return Base64.getEncoder().encodeToString(signer.sign())
    }

    private fun assertProof(
        body: JsonObject,
        identity: DeviceInstallationIdentity,
        action: DeviceProofAction,
    ) {
        val nonce = body.get("nonce").asString
        assertTrue(Regex("[A-Za-z0-9_-]{22}").matches(nonce))
        val verifier = Signature.getInstance("SHA256withECDSAinP1363Format")
        val publicKey =
            KeyFactory.getInstance("EC").generatePublic(
                X509EncodedKeySpec(Base64.getDecoder().decode(identity.publicKeyDerBase64)),
            )
        verifier.initVerify(publicKey)
        verifier.update(
            canonicalDeviceProof(action, identity.deviceId, nonce).toByteArray(StandardCharsets.UTF_8),
        )
        assertTrue(verifier.verify(Base64.getUrlDecoder().decode(body.get("proof").asString)))
    }

    private fun json(body: String, status: Int = 200): MockResponse =
        MockResponse()
            .setResponseCode(status)
            .setHeader("Content-Type", "application/json")
            .setBody(body)

    private fun withState(block: (LicenseStore, WindowsDeviceIdentityStore) -> Unit) {
        val root = Files.createTempDirectory("iptvburo-license-client")
        val preferences =
            Preferences.userRoot().node("com/lucasserafin94/iptvburo/client-test-${UUID.randomUUID()}")
        try {
            val store =
                LicenseStore(
                    appDirectory = root.resolve("licence"),
                    homeMarker = root.resolve("home-marker"),
                    preferences = preferences,
                )
            val identityStore =
                WindowsDeviceIdentityStore(root.resolve("identity.dpapi"), CopyProtector)
            block(store, identityStore)
        } finally {
            runCatching { preferences.removeNode() }
            @OptIn(kotlin.io.path.ExperimentalPathApi::class)
            root.deleteRecursively()
        }
    }

    private object CopyProtector : DeviceIdentityProtector {
        override val isAvailable = true
        override fun protect(plaintext: ByteArray): ByteArray = plaintext.clone()
        override fun unprotect(protected: ByteArray): ByteArray = protected.clone()
    }

    /** Keeps these boundary assertions tied to the production policy without changing its module. */
    private object LicensePolicyClock {
        val tolerance: Duration = com.lucasserafin94.iptvburo.domain.model.LicensePolicy.CLOCK_TOLERANCE
    }
}

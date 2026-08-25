package com.lucasserafin94.iptvburo.desktop.license

import com.google.gson.JsonParser
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

/**
 * Collecting a connection a reseller set up for this machine.
 *
 * Two things have to hold. The payload is provider credentials arriving from a server, so it is
 * checked rather than trusted — the address decides where the viewer's password will be sent next.
 * And the delivery is only erased once it has actually been applied, because a customer whose app
 * closed mid-way is otherwise left with no list and no way to ask for it again.
 */
class ProvisioningClientTest {
    /**
     * A throwaway identity, built in memory.
     *
     * Deliberately not the real one: DeviceFingerprint.getOrCreate() reads and writes this
     * machine's actual identity file, and a test has no business touching the installation's
     * licence identity.
     */
    private fun identity(): DeviceIdentityProvider {
        val keyPair =
            java.security.KeyPairGenerator.getInstance("EC").apply {
                initialize(java.security.spec.ECGenParameterSpec("secp256r1"))
            }.generateKeyPair()
        val publicBytes = keyPair.public.encoded
        val installationId = java.util.UUID.randomUUID().toString()
        val stored =
            DeviceInstallationIdentity(
                installationId = installationId,
                deviceId = deriveDeviceId(installationId, publicBytes),
                publicKeyDerBase64 = java.util.Base64.getEncoder().encodeToString(publicBytes),
                privateKey = keyPair.private,
            )
        return DeviceIdentityProvider { stored }
    }

    private fun withServer(
        vararg responses: MockResponse,
        block: (ProvisioningClient, MockWebServer) -> Unit,
    ) {
        val server = MockWebServer()
        responses.forEach(server::enqueue)
        server.start()
        try {
            val client =
                ProvisioningClient(
                    identityProvider = identity(),
                    claimUrl = server.url("/v1/provisioning/claim").toString(),
                    confirmUrl = server.url("/v1/provisioning/confirm").toString(),
                    http =
                        OkHttpClient.Builder()
                            .connectTimeout(2, TimeUnit.SECONDS)
                            .readTimeout(5, TimeUnit.SECONDS)
                            .build(),
                )
            block(client, server)
        } finally {
            server.shutdown()
        }
    }

    private fun sourceResponse(
        server: String = "http://provedor.invalid:8080",
        username: String = "cliente",
        password: String = "senha-do-cliente",
    ) = MockResponse()
        .setResponseCode(200)
        .setBody("""{"source":{"server":"$server","username":"$username","password":"$password"}}""")

    @Test
    fun `a waiting connection is collected`() {
        withServer(sourceResponse()) { client, _ ->
            val source = client.claim()
            assertNotNull(source)
            assertEquals("http://provedor.invalid:8080", String(source.server))
            assertEquals("cliente", String(source.username))
            assertEquals("senha-do-cliente", String(source.password))
        }
    }

    @Test
    fun `nothing waiting is silence, not an error`() {
        // The ordinary case: every launch of every machine no operator has configured.
        withServer(MockResponse().setResponseCode(204)) { client, _ ->
            assertNull(client.claim())
        }
    }

    @Test
    fun `an unreachable server is silence too`() {
        val client =
            ProvisioningClient(
                identityProvider = identity(),
                claimUrl = "http://127.0.0.1:1/claim",
                confirmUrl = "http://127.0.0.1:1/confirm",
            )
        assertNull(client.claim())
    }

    @Test
    fun `a refusal is not mistaken for a connection`() {
        withServer(MockResponse().setResponseCode(401).setBody("""{"error":"invalid_proof"}""")) { client, _ ->
            assertNull(client.claim())
        }
    }

    @Test
    fun `a malformed answer is dropped rather than half-applied`() {
        listOf(
            """{"source":{"username":"u","password":"p"}}""",
            """{"source":{"server":"http://a.invalid","password":"p"}}""",
            """{"source":{"server":"http://a.invalid","username":"u"}}""",
            """{"source":{}}""",
            """{}""",
            """nao e json""",
        ).forEach { body ->
            withServer(MockResponse().setResponseCode(200).setBody(body)) { client, _ ->
                assertNull(client.claim(), "must not build a source from: $body")
            }
        }
    }

    @Test
    fun `an address that is not http is refused`() {
        // This value decides where the viewer's password gets sent next, and the server that
        // vouched for it is not the one this app answers to.
        listOf(
            "file:///c:/windows/win.ini",
            "javascript:alert(1)",
            "ftp://provedor.invalid",
            "provedor.invalid:8080",
        ).forEach { hostile ->
            withServer(sourceResponse(server = hostile)) { client, _ ->
                assertNull(client.claim(), "must refuse: $hostile")
            }
        }
    }

    @Test
    fun `an address carrying its own credentials is refused`() {
        // A second copy of the password, in the half of the value that gets logged.
        withServer(sourceResponse(server = "http://user:pass@provedor.invalid")) { client, _ ->
            assertNull(client.claim())
        }
    }

    @Test
    fun `the request proves who this machine is, bound to this action`() {
        // Without the action in the signature, a proof captured from an ordinary launch check
        // could be replayed to fetch someone's provider credentials.
        withServer(sourceResponse()) { client, server ->
            client.claim()
            val body = JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
            assertTrue(body.has("deviceId"), "the server needs to know which machine")
            assertTrue(body.has("proof"), "and that it really is that machine")
            assertTrue(body.has("nonce"), "fresh each time, so a proof cannot be replayed")
            assertTrue(body.has("installationId"))
            assertTrue(body.has("publicKey"))
        }
    }

    @Test
    fun `the signature really covers this action, not another`() {
        // The field being present proves nothing — it was, while the client signed "validate".
        // This verifies the signature the way the server does, so signing the wrong action fails
        // here instead of only in production against the real Worker.
        val keyPair =
            java.security.KeyPairGenerator.getInstance("EC").apply {
                initialize(java.security.spec.ECGenParameterSpec("secp256r1"))
            }.generateKeyPair()
        val publicBytes = keyPair.public.encoded
        val installationId = java.util.UUID.randomUUID().toString()
        val known =
            DeviceInstallationIdentity(
                installationId = installationId,
                deviceId = deriveDeviceId(installationId, publicBytes),
                publicKeyDerBase64 = java.util.Base64.getEncoder().encodeToString(publicBytes),
                privateKey = keyPair.private,
            )

        val server = MockWebServer()
        server.enqueue(sourceResponse())
        server.start()
        try {
            ProvisioningClient(
                identityProvider = DeviceIdentityProvider { known },
                claimUrl = server.url("/v1/provisioning/claim").toString(),
                confirmUrl = server.url("/v1/provisioning/confirm").toString(),
            ).claim()

            val body = JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
            val signature =
                java.util.Base64
                    .getUrlDecoder()
                    .decode(body.get("proof").asString)
            val expected =
                canonicalDeviceProof(
                    action = DeviceProofAction.PROVISIONING,
                    deviceId = known.deviceId,
                    nonce = body.get("nonce").asString,
                )
            val verifier =
                java.security.Signature.getInstance("SHA256withECDSAinP1363Format").apply {
                    initVerify(keyPair.public)
                    update(expected.toByteArray(Charsets.UTF_8))
                }
            assertTrue(
                verifier.verify(signature),
                "the proof must sign the provisioning action; signing any other action would let " +
                    "a proof from an ordinary launch check be replayed to fetch credentials",
            )
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `each request carries a fresh nonce`() {
        withServer(sourceResponse(), sourceResponse()) { client, server ->
            client.claim()
            client.claim()
            val first = JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
            val second = JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
            assertFalse(
                first.get("nonce").asString == second.get("nonce").asString,
                "a repeated nonce would let a captured request be replayed",
            )
        }
    }

    @Test
    fun `confirming is a separate step from collecting`() {
        // The delivery must survive an app that closes between collecting and saving. Erasing on
        // delivery would leave the customer with no list and no way to ask for it again.
        withServer(sourceResponse(), MockResponse().setResponseCode(200).setBody("""{"ok":true}""")) { client, server ->
            client.claim()
            assertEquals(1, server.requestCount, "collecting must not confirm on its own")
            client.confirmApplied()
            assertEquals(2, server.requestCount)
            server.takeRequest()
            assertEquals("/v1/provisioning/confirm", server.takeRequest().path)
        }
    }

    @Test
    fun `a failure is reported so the seller can see it`() {
        // Their customer can only say "it did not work"; the panel is where the wrong password is
        // actually visible.
        withServer(MockResponse().setResponseCode(200).setBody("""{"ok":true}""")) { client, server ->
            client.reportFailure("bad_credentials")
            val body = JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
            assertEquals("bad_credentials", body.get("errorCode").asString)
        }
    }

    @Test
    fun `the metadata keys arrive when the seller sent them`() {
        // The same person who cannot set up an Xtream will not create a TMDb account and paste a
        // key either, so the seller can hand over an app that already shows artwork and synopsis.
        val body =
            """{"source":{"server":"http://provedor.invalid","username":"u","password":"p",""" +
                """"metadataKey":"chave-tmdb","criticsKey":"chave-omdb"}}"""
        withServer(MockResponse().setResponseCode(200).setBody(body)) { client, _ ->
            val source = assertNotNull(client.claim())
            assertEquals("chave-tmdb", source.metadataKey?.let(::String))
            assertEquals("chave-omdb", source.criticsKey?.let(::String))
        }
    }

    @Test
    fun `the list keeps the name the seller chose`() {
        // Derived from the host otherwise, which is the server's own name rather than the one
        // their customer was sold.
        val body =
            com.google.gson.JsonObject().apply {
                add(
                    "source",
                    com.google.gson.JsonObject().apply {
                        addProperty("server", "http://provedor.invalid")
                        addProperty("username", "u")
                        addProperty("password", "p")
                        addProperty("listLabel", "Lista do Lucas")
                    },
                )
            }.toString()
        withServer(MockResponse().setResponseCode(200).setBody(body)) { client, _ ->
            assertEquals("Lista do Lucas", assertNotNull(client.claim()).listLabel)
        }
    }

    @Test
    fun `no name sent means fall back, not blank`() {
        withServer(sourceResponse()) { client, _ ->
            assertNull(assertNotNull(client.claim()).listLabel)
        }
    }

    @Test
    fun `an absent key is null, so it can mean leave alone`() {
        // A seller replacing a provider address that went down sends the three connection fields
        // and nothing else. That must not wipe a key the viewer configured themselves, so absent
        // and empty have to be distinguishable here.
        withServer(sourceResponse()) { client, _ ->
            val source = assertNotNull(client.claim())
            assertNull(source.metadataKey)
            assertNull(source.criticsKey)
        }
    }

    @Test
    fun `a key that is not shaped like one is dropped, and the list still arrives`() {
        // A mis-paste in a request URL is a malformed address, not a failed lookup. And a bad key
        // must never be why the customer cannot watch.
        listOf("com espaco", "aspas\"dentro", "<script>", "a".repeat(401)).forEach { bad ->
            // Built with the JSON writer rather than by hand: one of these values contains a
            // quote, and hand-escaping it produced a malformed body that the client rejected
            // outright — which looked like the key check working while nothing was being tested.
            val source =
                com.google.gson.JsonObject().apply {
                    addProperty("server", "http://provedor.invalid")
                    addProperty("username", "u")
                    addProperty("password", "p")
                    addProperty("metadataKey", bad)
                }
            val body = com.google.gson.JsonObject().apply { add("source", source) }.toString()

            withServer(MockResponse().setResponseCode(200).setBody(body)) { client, _ ->
                val claimed = assertNotNull(client.claim(), "the list must still arrive: $bad")
                assertNull(claimed.metadataKey, "the bad key must not pass: $bad")
            }
        }
    }

    @Test
    fun `clearing wipes the credentials rather than dropping the reference`() {
        // Why these are char arrays at all: a String cannot be cleared, so a password put in one
        // survives in the heap — and in any crash dump — until the collector happens to reclaim it.
        val body =
            com.google.gson.JsonObject().apply {
                add(
                    "source",
                    com.google.gson.JsonObject().apply {
                        addProperty("server", "http://provedor.invalid")
                        addProperty("username", "cliente")
                        addProperty("password", "senha-do-cliente")
                        addProperty("metadataKey", "chave-tmdb")
                        addProperty("criticsKey", "chave-omdb")
                    },
                )
            }.toString()

        withServer(MockResponse().setResponseCode(200).setBody(body)) { client, _ ->
            val source = assertNotNull(client.claim())
            source.clear()
            // Every field, the keys included: they are credentials too, and an earlier version of
            // this test checked only the first three, which left the keys in the heap and stayed
            // green when the wipe for them was removed.
            listOfNotNull(
                source.password,
                source.username,
                source.server,
                source.metadataKey,
                source.criticsKey,
            ).forEach { field ->
                assertEquals(
                    "\u0000".repeat(field.size),
                    String(field),
                    "every character has to be overwritten, not merely dereferenced",
                )
            }
        }
    }

    @Test
    fun `the credentials never appear in a printed form`() {
        // This type reaches a log by accident, which is exactly what the rule is for.
        val printed =
            ProvisionedSource(
                server = "http://provedor.invalid".toCharArray(),
                username = "cliente".toCharArray(),
                password = "senha-secreta".toCharArray(),
            ).toString()
        assertFalse(printed.contains("senha-secreta"), "the password must never be printed")
        assertFalse(printed.contains("cliente"), "nor the username")
        assertFalse(printed.contains("provedor.invalid"), "nor the address")
    }
}

package com.lucasserafin94.iptvburo.stalker

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class StalkerClientTest {
    private val server = MockWebServer()
    private val client = StalkerClient()

    private fun credentials() =
        StalkerCredentials(
            portalUrl = server.url("/").toString(),
            macAddress = "00:1A:79:AB:CD:EF",
        )

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `handshake returns the token and sends set-top-box identity`() {
        server.enqueue(MockResponse().setBody("""{"js":{"token":"abc123"}}"""))

        val session = client.handshake(credentials())

        assertEquals("abc123", session.token)
        val recorded = server.takeRequest()
        // Portals fingerprint the client; a generic user agent is refused outright.
        assertContains(recorded.getHeader("User-Agent").orEmpty(), "MAG200")
        assertContains(recorded.getHeader("Cookie").orEmpty(), "mac=00%3A1A%3A79%3AAB%3ACD%3AEF")
        assertContains(recorded.path.orEmpty(), "action=handshake")
    }

    @Test
    fun `authenticated calls carry the bearer token and the mac`() {
        server.enqueue(MockResponse().setBody("""{"js":{"token":"tok"}}"""))
        server.enqueue(MockResponse().setBody("""{"js":{"tariff_plan":"basic","blocked":"0"}}"""))

        val creds = credentials()
        val session = client.handshake(creds)
        val account = client.account(creds, session)

        assertTrue(account.authenticated)
        assertFalse(account.blocked)
        server.takeRequest()
        val second = server.takeRequest()
        assertEquals("Bearer tok", second.getHeader("Authorization"))
        assertContains(second.getHeader("Cookie").orEmpty(), "token=tok")
    }

    @Test
    fun `a rejected mac surfaces as unauthorised, not as a generic failure`() {
        // Every candidate endpoint is tried, so all of them must answer 403.
        repeat(4) { server.enqueue(MockResponse().setResponseCode(403)) }

        val failure = assertFailsWith<StalkerClientException> { client.handshake(credentials()) }

        assertEquals(StalkerFailureReason.UNAUTHORISED, failure.reason)
    }

    @Test
    fun `an invalid mac fails before any network call`() {
        val failure =
            assertFailsWith<StalkerClientException> {
                client.handshake(credentials().copy(macAddress = "not-a-mac"))
            }

        assertEquals(StalkerFailureReason.UNAUTHORISED, failure.reason)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `live categories drop the all pseudo entry`() {
        server.enqueue(MockResponse().setBody("""{"js":{"token":"very-secret-token-123"}}"""))
        server.enqueue(
            MockResponse().setBody(
                """{"js":[{"id":"*","title":"All"},{"id":"7","title":"Sports"}]}""",
            ),
        )

        val creds = credentials()
        val session = client.handshake(creds)
        val categories = client.categories(creds, session, StalkerContentType.LIVE)

        assertEquals(1, categories.size)
        assertEquals("Sports", categories.single().name)
    }

    @Test
    fun `page parses items and total`() {
        server.enqueue(MockResponse().setBody("""{"js":{"token":"t"}}"""))
        server.enqueue(
            MockResponse().setBody(
                """
                {"js":{"total_items":"120","data":[
                  {"id":"31915","name":"Movie One","year":"1999","rating_imdb":"7.4",
                   "cmd":"ffmpeg http://localhost/ch/31915_","screenshot_uri":"http://img/a.jpg"}
                ]}}
                """.trimIndent(),
            ),
        )

        val creds = credentials()
        val session = client.handshake(creds)
        val page = client.page(creds, session, StalkerContentType.MOVIE, categoryId = "3", page = 1)

        assertEquals(120, page.totalItems)
        val item = page.items.single()
        assertEquals("Movie One", item.name)
        assertEquals(1999, item.year)
        assertEquals(7.4, item.rating)
    }

    @Test
    fun `create_link unwraps the ffmpeg prefix into a playable url`() {
        server.enqueue(MockResponse().setBody("""{"js":{"token":"t"}}"""))
        server.enqueue(
            MockResponse().setBody(
                """{"js":{"cmd":"ffmpeg http://cdn.example/live/1.m3u8?play_token=xyz"}}""",
            ),
        )

        val creds = credentials()
        val session = client.handshake(creds)
        val item =
            StalkerCatalogItem(
                providerId = "1",
                name = "Ch",
                contentType = StalkerContentType.LIVE,
                categoryId = null,
                artworkUrl = null,
                year = null,
                rating = null,
                command = "ffmpeg http://localhost/ch/1_",
            )

        val url = client.resolvePlaybackUrl(creds, session, item)

        // Handing the raw "ffmpeg ..." string to a player would fail.
        assertEquals("http://cdn.example/live/1.m3u8?play_token=xyz", url)
    }

    @Test
    fun `command without a url is reported as malformed`() {
        assertNotNull(StalkerClient.extractUrl("ffmpeg http://a/b"))
        assertEquals(null, StalkerClient.extractUrl("ffmpeg -i localfile"))
    }

    @Test
    fun `errors never leak the portal address or the mac`() {
        repeat(4) { server.enqueue(MockResponse().setResponseCode(500)) }

        val failure = assertFailsWith<StalkerClientException> { client.handshake(credentials()) }

        val rendered = failure.toString() + " " + failure.message.orEmpty()
        assertFalse(rendered.contains("00:1A:79"), "leaked the MAC: $rendered")
        assertFalse(rendered.contains(server.hostName), "leaked the host: $rendered")
    }

    @Test
    fun `redirects never forward the mac cookie or bearer token to another host`() {
        val redirectTarget = MockWebServer()
        try {
            val crossHostLocation =
                redirectTarget.url("/capture").newBuilder()
                    .host("127.0.0.1")
                    .build()
            val permissiveTransport =
                OkHttpClient.Builder()
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .readTimeout(2, TimeUnit.SECONDS)
                    .build()
            val hardenedClient = StalkerClient(httpClient = permissiveTransport)
            server.enqueue(MockResponse().setBody("""{"js":{"token":"secret-token"}}"""))
            repeat(4) {
                server.enqueue(
                    MockResponse()
                        .setResponseCode(302)
                        .setHeader("Location", crossHostLocation),
                )
            }
            redirectTarget.enqueue(
                MockResponse().setBody("""{"js":{"tariff_plan":"must-not-be-used"}}"""),
            )

            val creds = credentials()
            val session = hardenedClient.handshake(creds)
            val failure =
                assertFailsWith<StalkerClientException> {
                    hardenedClient.account(creds, session)
                }

            assertEquals(StalkerFailureReason.NETWORK, failure.reason)
            assertEquals(0, redirectTarget.requestCount)
        } finally {
            redirectTarget.shutdown()
        }
    }

    @Test
    fun `chunked response is stopped at the byte limit before creating a string`() {
        val hardenedClient = StalkerClient(maximumResponseBytes = 64)
        // A distinctive token, not "t". A single common letter appears inside ordinary English
        // words — "portal response exceeded safety limit" contains four of them — so a one-character
        // token makes the leak assertions below fail on the error message's own prose rather than on
        // anything having leaked.
        server.enqueue(MockResponse().setBody("""{"js":{"token":"zqx9token"}}"""))
        val oversizedBody = """{"js":{"padding":"${"x".repeat(128)}"}}"""
        repeat(4) {
            server.enqueue(MockResponse().setChunkedBody(oversizedBody, 7))
        }

        val creds = credentials()
        val session = hardenedClient.handshake(creds)
        val failure =
            assertFailsWith<StalkerClientException> {
                hardenedClient.account(creds, session)
            }

        assertEquals(StalkerFailureReason.MALFORMED, failure.reason)
        assertEquals("portal response exceeded safety limit", failure.message)
        val rendered = failure.toString() + " " + failure.message.orEmpty()
        assertFalse(rendered.contains(creds.macAddress))
        assertFalse(rendered.contains(session.token))
    }
}

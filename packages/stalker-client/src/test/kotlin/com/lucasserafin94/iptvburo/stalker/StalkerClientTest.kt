package com.lucasserafin94.iptvburo.stalker

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
        // Every candidate endpoint is tried, and each transient 500 is retried once: three
        // candidates times two attempts.
        repeat(6) { server.enqueue(MockResponse().setResponseCode(500)) }

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
    fun `a transient 429 is retried within the budget`() {
        val retrying = StalkerClient(retryDelayMillis = 0)
        server.enqueue(MockResponse().setResponseCode(429))
        server.enqueue(MockResponse().setBody("""{"js":{"token":"abc123"}}"""))

        val session = retrying.handshake(credentials())

        assertEquals("abc123", session.token)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a persistent 500 fails after the retry budget, not before it`() {
        val retrying = StalkerClient(retryDelayMillis = 0)
        // Three candidate endpoints, two attempts each: the budget is per request, and a portal
        // that only ever answers 500 must exhaust all of them before giving up.
        repeat(6) { server.enqueue(MockResponse().setResponseCode(500)) }

        val failure =
            assertFailsWith<StalkerClientException> { retrying.handshake(credentials()) }

        assertEquals(StalkerFailureReason.NETWORK, failure.reason)
        assertEquals(6, server.requestCount)
    }

    @Test
    fun `a 403 is never retried`() {
        val retrying = StalkerClient(retryDelayMillis = 0)
        // A wrong MAC is wrong on every attempt; retrying it would only triple the traffic.
        repeat(3) { server.enqueue(MockResponse().setResponseCode(403)) }

        assertFailsWith<StalkerClientException> { retrying.handshake(credentials()) }

        assertEquals(3, server.requestCount)
    }

    @Test
    fun `artwork urls carrying credentials or queries are dropped`() {
        server.enqueue(MockResponse().setBody("""{"js":{"token":"t"}}"""))
        server.enqueue(
            MockResponse().setBody(
                """
                {"js":{"total_items":"3","data":[
                  {"id":"1","name":"Com userinfo","cmd":"ffmpeg http://localhost/ch/1_",
                   "screenshot_uri":"http://user:pass@img.example/a.jpg"},
                  {"id":"2","name":"Com query","cmd":"ffmpeg http://localhost/ch/2_",
                   "screenshot_uri":"http://img.example/b.jpg?token=secret"},
                  {"id":"3","name":"Valida","cmd":"ffmpeg http://localhost/ch/3_",
                   "screenshot_uri":"http://img.example/c.jpg"}
                ]}}
                """.trimIndent(),
            ),
        )

        val creds = credentials()
        val session = client.handshake(creds)
        val page = client.page(creds, session, StalkerContentType.MOVIE, categoryId = "3", page = 1)

        assertEquals(3, page.items.size)
        assertNull(page.items[0].artworkUrl, "userinfo is a credential in a URL")
        assertNull(page.items[1].artworkUrl, "a query rides a tracking token too easily")
        assertEquals("http://img.example/c.jpg", page.items[2].artworkUrl)
    }

    @Test
    fun `a bad screenshot falls back to a valid cover`() {
        server.enqueue(MockResponse().setBody("""{"js":{"token":"t"}}"""))
        server.enqueue(
            MockResponse().setBody(
                """
                {"js":{"total_items":"1","data":[
                  {"id":"1","name":"Filme","cmd":"ffmpeg http://localhost/ch/1_",
                   "screenshot_uri":"ftp://img.example/a.jpg",
                   "cover":"http://img.example/cover.jpg"}
                ]}}
                """.trimIndent(),
            ),
        )

        val creds = credentials()
        val session = client.handshake(creds)
        val page = client.page(creds, session, StalkerContentType.MOVIE, categoryId = "3", page = 1)

        assertEquals("http://img.example/cover.jpg", page.items.single().artworkUrl)
    }

    @Test
    fun `a portal without a scheme is warned about once, without naming it`() {
        val warnings = mutableListOf<String>()
        val warningClient = StalkerClient(onInsecurePortal = { warnings += it })
        server.enqueue(MockResponse().setBody("""{"js":{"token":"t"}}"""))
        server.enqueue(MockResponse().setBody("""{"js":{"tariff_plan":"basic"}}"""))
        val creds =
            StalkerCredentials(
                portalUrl = "${server.hostName}:${server.port}",
                macAddress = "00:1A:79:AB:CD:EF",
            )

        val session = warningClient.handshake(creds)
        warningClient.account(creds, session)

        assertEquals(1, warnings.size, "the warning is once per client, not once per call")
        val message = warnings.single()
        assertFalse(message.contains(server.hostName), "leaked the host: $message")
        assertFalse(message.contains("00:1A:79"), "leaked the MAC: $message")
    }

    @Test
    fun `a portal with an explicit scheme is not warned about`() {
        val warnings = mutableListOf<String>()
        val warningClient = StalkerClient(onInsecurePortal = { warnings += it })
        server.enqueue(MockResponse().setBody("""{"js":{"token":"t"}}"""))

        warningClient.handshake(credentials())

        assertTrue(warnings.isEmpty())
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

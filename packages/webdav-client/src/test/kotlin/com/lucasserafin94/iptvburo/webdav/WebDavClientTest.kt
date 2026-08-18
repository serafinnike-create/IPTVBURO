package com.lucasserafin94.iptvburo.webdav

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Talking to a share.
 *
 * Against a real HTTP server rather than a stub, because the things that break here are protocol
 * details — the verb, the depth header, the status code a server chooses — and a stub would only
 * confirm what this test already assumed.
 */
class WebDavClientTest {
    private val server = MockWebServer()

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    private fun clientFor(): WebDavClient =
        WebDavClient(
            WebDavCredentials(
                displayName = "Share",
                baseUrl = server.url("/").toString(),
                username = "lucas",
                password = "hunter2",
            ),
        )

    @Test
    fun `a share that answers 207 is reachable`() {
        server.enqueue(MockResponse().setResponseCode(207).setBody(EMPTY_MULTISTATUS))

        assertEquals(WebDavProbe.Reachable, clientFor().probe())
    }

    /**
     * The distinction that matters most to somebody who cannot connect.
     *
     * A refused password and an unreachable host need completely different things checked, so they
     * must not collapse into one message.
     */
    @Test
    fun `a refused password is told apart from an unreachable server`() {
        server.enqueue(MockResponse().setResponseCode(401))
        assertEquals(WebDavProbe.Rejected, clientFor().probe())

        server.enqueue(MockResponse().setResponseCode(500))
        assertEquals(WebDavProbe.Unreachable, clientFor().probe())
    }

    /** An ordinary web page at the address is a wrong address, not a wrong password. */
    @Test
    fun `a host that is not a WebDAV server is reported as such`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("<html>hello</html>"))

        assertEquals(WebDavProbe.NotWebDav, clientFor().probe())
    }

    /**
     * The request itself, which is where a working client differs from a plausible one.
     *
     * `Depth: 1` is what keeps a listing to one folder. `infinity` asks the server to walk the
     * whole tree, which on a film library is refused outright by many servers and ruinous on the
     * rest.
     */
    @Test
    fun `listing asks for one level and authenticates`() {
        server.enqueue(MockResponse().setResponseCode(207).setBody(EMPTY_MULTISTATUS))

        clientFor().list("/media/")
        val request = server.takeRequest()

        assertEquals("PROPFIND", request.method)
        assertEquals("1", request.getHeader("Depth"))
        assertTrue(request.getHeader("Authorization").orEmpty().startsWith("Basic "))
    }

    @Test
    fun `entries come back from a real response`() {
        server.enqueue(
            MockResponse().setResponseCode(207).setBody(
                """
                <d:multistatus xmlns:d="DAV:">
                  <d:response>
                    <d:href>/media/Duna.mkv</d:href>
                    <d:propstat><d:prop>
                      <d:displayname>Duna.mkv</d:displayname>
                      <d:resourcetype/>
                    </d:prop></d:propstat>
                  </d:response>
                </d:multistatus>
                """.trimIndent(),
            ),
        )

        val entries = clientFor().list("/media/")

        assertEquals(1, entries.size)
        assertEquals("Duna.mkv", entries.single().displayName)
    }

    /** A failure is an empty folder, never an exception thrown at a screen mid-scroll. */
    @Test
    fun `a failed listing yields nothing rather than throwing`() {
        server.enqueue(MockResponse().setResponseCode(500))

        assertTrue(clientFor().list("/media/").isEmpty())
    }

    /**
     * A share must not be usable to reach another host.
     *
     * A hostile or misconfigured server can return an href pointing anywhere. Following it would
     * send the subscriber's credentials to a third party, so anything off-host is refused.
     */
    @Test
    fun `an href pointing at another host is refused`() {
        assertNull(clientFor().downloadUrl("https://evil.example/steal.mkv"))
    }

    @Test
    fun `credentials never appear in diagnostics`() {
        val credentials =
            WebDavCredentials(
                displayName = "Casa",
                baseUrl = "https://dav.example/remote",
                username = "lucas",
                password = "hunter2",
            )

        val printed = credentials.toString()
        assertFalse("hunter2" in printed, "The password must never be printed.")
        assertFalse("lucas" in printed, "The username must never be printed.")
        assertFalse("dav.example" in printed, "The address identifies the account and is redacted.")
        assertTrue("Casa" in printed, "The name the viewer chose is safe and is what identifies it.")
    }

    private companion object {
        const val EMPTY_MULTISTATUS = """<d:multistatus xmlns:d="DAV:"></d:multistatus>"""
    }
}

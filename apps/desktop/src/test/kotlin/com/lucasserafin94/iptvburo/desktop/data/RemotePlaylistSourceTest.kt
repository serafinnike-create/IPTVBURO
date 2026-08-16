package com.lucasserafin94.iptvburo.desktop.data

import java.io.IOException
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Reading a playlist off the user's own server.
 *
 * The interesting failures here are not "did it download" — they are whether a password reaches a
 * log line, an error message or the sidebar. A NAS password is usually the household's, so leaking
 * one into a message the user screenshots for support is a real cost.
 */
class RemotePlaylistSourceTest {
    private lateinit var server: MockWebServer

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    private val playlist =
        """
        #EXTM3U
        #EXTINF:-1,Canal Um
        http://example.test/one.ts
        """.trimIndent()

    @Test
    fun `a playlist is read from the server`() {
        server.enqueue(MockResponse().setBody(playlist))

        val body =
            WebDavPlaylistReader(url = server.url("/list.m3u").toString())
                .open()
                .use { stream -> stream.readBytes().decodeToString() }

        assertEquals(playlist, body)
    }

    /**
     * Sent before being asked rather than after a 401.
     *
     * Servers differ in whether they challenge, and one that answers an unauthenticated GET with a
     * login page would have that page parsed as a playlist — producing an import that succeeds with
     * no channels in it, which looks like an empty list rather than a rejected password.
     */
    @Test
    fun `credentials are sent without waiting for a challenge`() {
        server.enqueue(MockResponse().setBody(playlist))

        WebDavPlaylistReader(
            url = server.url("/list.m3u").toString(),
            username = "maria",
            password = "hunter2",
        ).open().close()

        val header = server.takeRequest().getHeader("Authorization")
        assertTrue(header?.startsWith("Basic ") == true, "the first request should carry Basic auth")
    }

    /** No username means no header at all, rather than an empty credential the server rejects. */
    @Test
    fun `without a username no authorization header is sent`() {
        server.enqueue(MockResponse().setBody(playlist))

        WebDavPlaylistReader(url = server.url("/list.m3u").toString()).open().close()

        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    /**
     * The message is shown to the user and may be pasted into a support thread. A URL can carry a
     * password in its userinfo, so the failure says the status and nothing else.
     */
    @Test
    fun `a rejected request reports the status without the address`() {
        server.enqueue(MockResponse().setResponseCode(401))

        val url = server.url("/list.m3u").toString()
        val error =
            assertFailsWith<IOException> {
                WebDavPlaylistReader(url = url, username = "maria", password = "hunter2").open()
            }

        assertTrue(error.message?.contains("401") == true, "the status is what the user can act on")
        assertFalse(error.message.orEmpty().contains("hunter2"), "the password must not appear")
        assertFalse(error.message.orEmpty().contains(url), "the address must not appear")
    }

    @Test
    fun `a server error is reported rather than parsed`() {
        server.enqueue(MockResponse().setResponseCode(500))

        assertFailsWith<IOException> {
            WebDavPlaylistReader(url = server.url("/list.m3u").toString()).open()
        }
    }

    /** The sidebar shows this, so it must be the host and never the credential-bearing URL. */
    @Test
    fun `the display name is the host alone`() {
        val reader =
            WebDavPlaylistReader(
                url = "https://maria:hunter2@nas.example.test/media/list.m3u",
                username = "maria",
                password = "hunter2",
            )

        assertEquals("nas.example.test", reader.displayName)
        assertFalse(reader.displayName.contains("hunter2"))
    }

    @Test
    fun `toString carries neither the password nor the path`() {
        val description =
            WebDavPlaylistReader(
                url = "https://nas.example.test/media/list.m3u",
                username = "maria",
                password = "hunter2",
            ).toString()

        assertFalse(description.contains("hunter2"), "the password must not appear in toString")
        assertFalse(description.contains("/media/list.m3u"), "the path must not appear either")
    }

    /**
     * People paste what their NAS's own interface shows them, which is a webdav:// address. No HTTP
     * client accepts that scheme, and the resulting error blames the address the server told them
     * to use.
     */
    @Test
    fun `a webdav scheme is read as http`() {
        server.enqueue(MockResponse().setBody(playlist))

        val webdavUrl = server.url("/list.m3u").toString().replaceFirst("http://", "webdav://")
        val body =
            WebDavPlaylistReader(url = webdavUrl)
                .open()
                .use { stream -> stream.readBytes().decodeToString() }

        assertEquals(playlist, body)
    }

    @Test
    fun `protocols are recognised by scheme`() {
        assertEquals(RemotePlaylistProtocol.WEBDAV, RemotePlaylistProtocol.of("https://nas.test/a.m3u"))
        assertEquals(RemotePlaylistProtocol.WEBDAV, RemotePlaylistProtocol.of("http://nas.test/a.m3u"))
        assertEquals(RemotePlaylistProtocol.WEBDAV, RemotePlaylistProtocol.of("webdav://nas.test/a.m3u"))
        assertEquals(RemotePlaylistProtocol.WEBDAV, RemotePlaylistProtocol.of("WEBDAVS://nas.test/a.m3u"))
        assertEquals(RemotePlaylistProtocol.FTP, RemotePlaylistProtocol.of("ftp://nas.test/a.m3u"))
        assertNull(RemotePlaylistProtocol.of("smb://nas.test/a.m3u"))
        assertNull(RemotePlaylistProtocol.of("/home/maria/list.m3u"))
    }

    /** FTP puts the credential in the URL, so this one must never surface it. */
    @Test
    fun `the ftp reader shows only the host`() {
        val reader =
            FtpPlaylistReader(
                host = "nas.example.test",
                path = "/media/list.m3u",
                username = "maria",
                password = "hunter2",
            )

        assertEquals("nas.example.test", reader.displayName)
        assertFalse(reader.toString().contains("hunter2"), "the password must not appear in toString")
    }

    /**
     * A failure names the host and stops there. The JDK's own FTP errors quote the URL, which at
     * that point contains the password in plain text.
     */
    @Test
    fun `an unreachable ftp server fails without quoting the credentials`() {
        val error =
            assertFailsWith<IOException> {
                FtpPlaylistReader(
                    // .invalid is reserved by RFC 2606 and never resolves, so this is a failure
                    // rather than a request to somebody's real server.
                    host = "nas.invalid",
                    path = "/list.m3u",
                    username = "maria",
                    password = "hunter2",
                ).open()
            }

        assertFalse(error.message.orEmpty().contains("hunter2"), "the password must not appear")
        assertTrue(error.message.orEmpty().contains("nas.invalid"), "the host is what the user can act on")
    }
}

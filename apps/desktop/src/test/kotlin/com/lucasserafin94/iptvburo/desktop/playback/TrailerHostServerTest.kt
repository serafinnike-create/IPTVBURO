package com.lucasserafin94.iptvburo.desktop.playback

import java.net.URI
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The loopback page that lets YouTube's player configure itself.
 *
 * Loading the embed directly gives "Video player configuration error, Error 153": the player wants
 * a page with a real origin behind the frame, and a top-level embed has none. These pin the page it
 * gets, including the `origin` parameter that has to agree with where the page is served from.
 */
class TrailerHostServerTest {
    private var server: TrailerHostServer? = null

    @AfterTest
    fun tearDown() {
        server?.stop()
    }

    private fun started(): TrailerHostServer = TrailerHostServer.start().also { server = it }!!

    private fun fetch(url: String): String =
        URI(url).toURL().openConnection().let { connection ->
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.getInputStream().bufferedReader().use { reader -> reader.readText() }
        }

    @Test
    fun `the page embeds the requested video in an iframe`() {
        val host = started()

        val page = fetch(host.pageUrlFor("cTW78JSBoyw"))

        assertTrue("<iframe" in page, "no iframe in the page")
        assertTrue("cTW78JSBoyw" in page, "the video id did not reach the embed")
    }

    /**
     * The parameter the whole server exists for. It has to match the address the page came from —
     * an origin that disagrees with the document's own is what YouTube rejects.
     */
    @Test
    fun `the embed carries an origin matching where the page is served from`() {
        val host = started()

        val page = fetch(host.pageUrlFor("cTW78JSBoyw"))

        assertTrue("origin=${host.origin}" in page, "origin missing or wrong in: $page")
        assertTrue(host.origin.startsWith("http://127.0.0.1:"), "not a loopback origin: ${host.origin}")
    }

    @Test
    fun `a muted trailer loops silently and shows no controls`() {
        val host = started()

        val page = fetch(host.pageUrlFor("cTW78JSBoyw", autoplay = true, muted = true))

        assertTrue("mute=1" in page)
        assertTrue("controls=0" in page, "a banner trailer must not show controls")
        assertTrue("loop=1" in page)
    }

    @Test
    fun `a deliberately opened trailer has sound and controls`() {
        val host = started()

        val page = fetch(host.pageUrlFor("cTW78JSBoyw", autoplay = true, muted = false))

        assertTrue("mute=0" in page)
        assertTrue("controls=1" in page)
    }

    /**
     * The server is reachable by anything running as this user, and the id is interpolated into
     * HTML. Anything that is not a video id must produce no markup at all.
     */
    @Test
    fun `a bogus id yields a blank page rather than injected markup`() {
        val host = started()

        // Percent-encoded, because that is how a browser would actually send it — the raw form is
        // not a legal URI and fails before it reaches the server, which tests nothing.
        val page = fetch("${host.origin}/watch?v=%3Cscript%3Ealert(1)%3C/script%3E")

        assertFalse("<script>" in page, "markup was injected: $page")
        assertFalse("<iframe" in page, "an iframe was built from an invalid id")
    }

    @Test
    fun `it binds to loopback only`() {
        val host = started()

        // Not 0.0.0.0 and not a LAN address: nothing outside this machine may reach the page.
        assertTrue(host.origin.startsWith("http://127.0.0.1:"), host.origin)
    }

    @Test
    fun `stopping twice is harmless`() {
        val host = started()
        host.stop()
        host.stop()
    }

    @Test
    fun `the page url carries the id and the flags`() {
        val host = started()

        val url = host.pageUrlFor("abc123XYZ_-", autoplay = false, muted = true)

        assertTrue("v=abc123XYZ_-" in url)
        assertTrue("autoplay=0" in url)
        assertTrue("mute=1" in url)
        assertNotNull(URI(url).port.takeIf { it > 0 })
        assertEquals("127.0.0.1", URI(url).host)
    }
}

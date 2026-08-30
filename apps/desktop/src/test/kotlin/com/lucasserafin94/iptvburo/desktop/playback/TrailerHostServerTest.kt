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
    fun `a banner trailer loops and shows no controls`() {
        val host = started()

        val page = fetch(host.pageUrlFor("cTW78JSBoyw", autoplay = true, muted = true, blendIntoHero = true))

        assertTrue("mute=1" in page)
        assertTrue("controls=0" in page, "a banner trailer must not show controls")
        assertTrue("loop=1" in page)
    }

    /**
     * Turning the banner's sound on changes the sound and nothing else.
     *
     * These two were one: controls and looping were decided by `muted`, so turning the sound on put
     * YouTube's pause button over the banner and stopped it repeating. Reported with a screenshot of
     * the controls sitting there over a stopped video. Where the video sits and whether it has sound
     * are unrelated questions.
     */
    @Test
    fun `a banner trailer with sound still loops and shows no controls`() {
        val host = started()

        val page = fetch(host.pageUrlFor("cTW78JSBoyw", autoplay = true, muted = false, blendIntoHero = true))

        assertTrue("controls=0" in page, "the sound switch brought YouTube's controls with it")
        assertTrue("loop=1" in page, "the sound switch stopped the banner looping")
    }

    /**
     * The banner loads muted even when the sound is wanted, and raises it once it is playing.
     *
     * Asking for sound up front does not produce a loud trailer — it produces no trailer, because
     * every engine refuses to autoplay audio and shows its play button over a still frame instead.
     * Seen exactly that way on the banner once the sound preference had been remembered. Starting
     * silent and unmuting after the engine has granted the autoplay is the only order that plays.
     */
    @Test
    fun `a banner asked for sound still starts muted and raises it after`() {
        val host = started()

        val page = fetch(host.pageUrlFor("cTW78JSBoyw", autoplay = true, muted = false, blendIntoHero = true))

        assertTrue("mute=1" in page, "the banner asked for audio up front and will not autoplay")
        assertTrue("enablejsapi=1" in page, "nothing can unmute a player it cannot talk to")
        assertTrue("unMute" in page, "the sound was never raised after the start")
    }

    /** A silent banner is left silent: no script, nothing to raise. */
    @Test
    fun `a banner left muted is not unmuted behind the viewer's back`() {
        val host = started()

        val page = fetch(host.pageUrlFor("cTW78JSBoyw", autoplay = true, muted = true, blendIntoHero = true))

        assertTrue("mute=1" in page)
        assertFalse("unMute" in page, "a banner the viewer silenced started making noise")
    }

    @Test
    fun `a deliberately opened trailer has sound and controls`() {
        val host = started()

        val page = fetch(host.pageUrlFor("cTW78JSBoyw", autoplay = true, muted = false))

        assertTrue("mute=0" in page)
        assertTrue("controls=1" in page)
        assertFalse("class=\"cinematic-hero\"" in page, "the trailer lightbox must stay unobstructed")
    }

    /**
     * The Home banner is partly Compose and partly an embedded AWT surface.
     *
     * A Compose gradient cannot cover the latter, so the page itself must feather both seams or
     * the moving picture looks like a rectangular player laid over the artwork.
     */
    @Test
    fun `the home trailer blends its left and bottom edges into the hero`() {
        val host = started()

        val page =
            fetch(
                host.pageUrlFor(
                    "cTW78JSBoyw",
                    autoplay = true,
                    muted = true,
                    blendIntoHero = true,
                ),
            )

        assertTrue("class=\"cinematic-hero\"" in page, "the banner did not select the blended page")
        assertTrue("body.cinematic-hero::before" in page, "the side seam has no mask")
        assertTrue("body.cinematic-hero::after" in page, "the bottom seam has no mask")
        assertTrue("linear-gradient(90deg" in page, "the video does not dissolve into the copy")
        assertTrue("linear-gradient(0deg" in page, "the video does not dissolve into the shelf")
        assertTrue("pointer-events:none" in page, "the masks intercept the embedded player")
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
        assertTrue("hero=0" in url)
        assertNotNull(URI(url).port.takeIf { it > 0 })
        assertEquals("127.0.0.1", URI(url).host)
    }
}

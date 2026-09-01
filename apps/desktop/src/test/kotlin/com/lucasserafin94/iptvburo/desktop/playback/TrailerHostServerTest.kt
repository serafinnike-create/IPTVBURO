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

        val page = fetch(host.pageUrlFor("cTW78JSBoyw", autoplay = true, muted = true, blendIntoHero = true, unattended = true))

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

        val page = fetch(host.pageUrlFor("cTW78JSBoyw", autoplay = true, muted = false, blendIntoHero = true, unattended = true))

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

        val page = fetch(host.pageUrlFor("cTW78JSBoyw", autoplay = true, muted = false, blendIntoHero = true, unattended = true))

        assertTrue("mute=1" in page, "the banner asked for audio up front and will not autoplay")
        assertTrue("enablejsapi=1" in page, "nothing can unmute a player it cannot talk to")
        assertTrue("unMute" in page, "the sound was never raised after the start")
    }

    /**
     * And it is raised only once the player says it is playing — never on a timer.
     *
     * The previous attempt polled `raise()` every 500ms alongside the state request. An unMute
     * arriving before playback has begun is read as a request to autoplay with audio, which the
     * engine refuses outright, so the banner showed YouTube's play button over a still frame with
     * the sound switch already on. Reported with a screenshot of exactly that.
     *
     * Asserted on the timer body rather than on the presence of unMute, because the broken version
     * contained unMute too — what was wrong was when it ran.
     */
    @Test
    fun `the sound is never raised on a timer, only when playing`() {
        val host = started()

        val page = fetch(host.pageUrlFor("cTW78JSBoyw", autoplay = true, muted = false, blendIntoHero = true, unattended = true))

        val marker = "var timer=setInterval(function(){"
        assertTrue(marker in page, "o temporizador mudou: este teste ja nao le nada")
        val timerBody = page.substringAfter(marker).substringBefore("},500);")
        assertFalse(
            "raise()" in timerBody,
            "o som e pedido antes do video tocar, e o motor recusa o arranque: $timerBody",
        )

        // The one place it may happen: the branch taken when the player reports it is playing.
        val playingBranch = "if(s===1){"
        assertTrue(
            playingBranch in page,
            "o ramo do estado 'a tocar' mudou: este teste ja nao le nada",
        )
        val onPlaying = page.substringAfter(playingBranch).substringBefore("}")
        assertTrue(
            "raise()" in onPlaying,
            "o som nao e levantado no momento em que o video comeca a tocar",
        )
    }

    /** A silent banner is left silent: no script, nothing to raise. */
    @Test
    fun `a banner left muted is not unmuted behind the viewer's back`() {
        val host = started()

        val page = fetch(host.pageUrlFor("cTW78JSBoyw", autoplay = true, muted = true, blendIntoHero = true, unattended = true))

        assertTrue("mute=1" in page)
        assertFalse("unMute" in page, "a banner the viewer silenced started making noise")
    }

    /**
     * The Descobrir card gets the same treatment as the banner, without the banner's masks.
     *
     * It plays beside a poster and nobody asked for it, so it starts muted, shows no controls and
     * repeats — but it is a card with its own edges, so the hero's edge-fading masks would only
     * smear it. Those two things were one flag, and the card inherited the wrong half: it was
     * served `mute=0&controls=1`, so it did not start at all and showed YouTube's play button.
     */
    @Test
    fun `an unattended trailer outside the banner still starts muted and bare`() {
        val host = started()

        val page =
            fetch(
                host.pageUrlFor(
                    "cTW78JSBoyw",
                    autoplay = true,
                    muted = false,
                    blendIntoHero = false,
                    unattended = true,
                ),
            )

        assertTrue("mute=1" in page, "o cartao pede audio ao arrancar, e o motor recusa-o")
        assertTrue("controls=0" in page, "o cartao mostra os controlos do YouTube")
        assertTrue("loop=1" in page, "o trailer do cartao para no fim em vez de repetir")
        assertFalse(
            "class=\"cinematic-hero\"" in page,
            "o cartao levou as mascaras do banner, que so lhe borram o video",
        )
        assertTrue("class=\"ambient-card unattended\"" in page)
        assertTrue("id=\"pointer-glass\"" in page, "hover ainda mostra os controles do YouTube")
        assertTrue("disablekb=1" in page)
        assertTrue("body.ambient-card #fit" in page, "o card nao tem enquadramento proprio")
        assertTrue("top:-2px;right:-2px;bottom:-2px;left:-2px" in page, "a borda nativa continua exposta")
        assertTrue("border-radius:18px" in page, "o Chromium continua com cantos quadrados")
        assertTrue("body.ambient-card iframe{outline:0}" in page, "o iframe ainda pode desenhar contorno")
    }

    /**
     * A video YouTube refuses at playback hides itself, rather than showing its error card.
     *
     * The availability check asks oEmbed whether the video is public, and a video can pass that and
     * still be refused when it actually plays — a region lock, an embedding restriction, a rights
     * holder blocking this player. What showed then was YouTube's own "An error occurred. Please
     * try again later" sitting beside the poster. Reported with a screenshot. The artwork is always
     * drawn underneath, so hiding the page leaves the card exactly as it would have been.
     */
    @Test
    fun `a video refused at playback hides itself instead of showing an error`() {
        val host = started()

        val page =
            fetch(
                host.pageUrlFor(
                    "cTW78JSBoyw",
                    autoplay = true,
                    muted = true,
                    blendIntoHero = true,
                    unattended = true,
                ),
            )

        assertTrue("onError" in page, "nada repara que o YouTube recusou o video")
        assertTrue(
            "style.display='none'" in page,
            "o cartao de erro do YouTube fica a vista, ao lado da capa",
        )
    }

    /** A trailer somebody opened reports readiness too, so its lightbox never stays blank. */
    @Test
    fun `a deliberately opened trailer reports playback and failure to its lightbox`() {
        val host = started()

        val page = fetch(host.pageUrlFor("cTW78JSBoyw", autoplay = true, muted = false))

        assertTrue("onError" in page, "o lightbox nao descobre que o trailer falhou")
        assertTrue("signal('playing')" in page, "o lightbox nao descobre que o video iniciou")
        assertTrue("signal('failed')" in page, "o lightbox fica vazio depois de uma recusa")
        assertTrue("window.cefQuery" in page, "a pagina nao comunica o estado ao aplicativo")
        assertFalse(
            "document.documentElement.style.display='none';" in page,
            "o trailer manual esconde a mensagem antes de o fallback assumir",
        )
    }

    /**
     * Unredeemed artwork tokens do not accumulate for ever.
     *
     * A token is minted for every banner page built, and only a page that is actually fetched
     * removes its own. A trailer that fails, or one the rotation replaces before its page loads,
     * leaves its entry behind — and the banner rotates all day, each entry holding an artwork
     * address. Left unbounded, an app open all evening keeps every one of them.
     */
    @Test
    fun `unredeemed artwork tokens are bounded`() {
        val host = started()

        // Far more pages than a session would ever build, none of them fetched.
        repeat(600) { index ->
            host.pageUrlFor(
                "cTW78JSBoyw",
                blendIntoHero = true,
                artworkUrl = "https://images.invalid/$index.jpg",
            )
        }

        // The most recent token still works: dropping the oldest must not break the page being
        // built right now, which is the whole point of dropping the oldest rather than refusing.
        val url =
            host.pageUrlFor(
                "cTW78JSBoyw",
                blendIntoHero = true,
                artworkUrl = "https://images.invalid/final.jpg",
            )

        assertTrue("art=" in url, "o token da arte deixou de ser emitido")
        assertTrue(
            "images.invalid/final.jpg" in fetch(url),
            "o token mais recente foi descartado, e a pagina ficou sem a arte",
        )
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

        assertTrue(
            "class=\"cinematic-hero unattended\"" in page,
            "the banner did not select the blended page",
        )
        assertTrue("body.cinematic-hero::before" in page, "the side seam has no mask")
        assertTrue("body.cinematic-hero::after" in page, "the bottom seam has no mask")
        assertTrue("linear-gradient(90deg" in page, "the video does not dissolve into the copy")
        assertTrue("linear-gradient(0deg" in page, "the video does not dissolve into the shelf")
        assertTrue("pointer-events:none" in page, "the masks intercept the embedded player")
        assertTrue("animation:hero-reveal 850ms" in page, "the trailer drops in without a reveal")
        assertTrue("scale(1.035)" in page, "the reveal has no cinematic pull-back")
        assertTrue(
            "translate(-50%,-50%) scale(1)" in page,
            "the reveal does not finish in the correctly centred position",
        )
    }

    @Test
    fun `the home trailer carries the still artwork through the moving picture seam`() {
        val host = started()
        val privateArtwork = "https://images.example.test/backdrop.jpg?token=secret-value"
        val pageUrl =
            host.pageUrlFor(
                "cTW78JSBoyw",
                autoplay = true,
                muted = true,
                blendIntoHero = true,
                unattended = true,
                artworkUrl = privateArtwork,
            )

        assertFalse(privateArtwork in pageUrl, "a signed artwork URL leaked into browser history")
        val page = fetch(pageUrl)
        assertTrue("class=\"cinematic-hero unattended with-art\"" in page)
        assertTrue(privateArtwork in page, "the still artwork did not reach the transition surface")
        assertTrue("mask-image:linear-gradient(90deg" in page)
        assertTrue("background-position:center,right center" in page)
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

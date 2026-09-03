package com.lucasserafin94.iptvburo.desktop.app

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The trailer lightbox gets the plain page, not the banner's.
 *
 * `blendIntoHero` defaults to true, and the lightbox was taking that default: the hero page masks
 * the bottom 46% at 96% opacity, masks half the width on the left, and blows the player up to
 * 126vw to carry YouTube's own controls out of view. In a box of its own that is a black rectangle
 * with the audio still running — reported as the trailer screen going black with sound.
 *
 * A source scan, because the real check needs Chromium and a window.
 */
class TrailerOverlayPageTest {
    private val overlay =
        Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/TrailerOverlay.kt").readText()

    @Test
    fun `the lightbox does not blend into the hero`() {
        val marker = "browser.createComponent("
        assertTrue(marker in overlay, "o lightbox mudou de nome: este teste ja nao le nada")

        val call = overlay.substringAfter(marker).substringBefore("onPlaying")

        assertTrue(
            "blendIntoHero = false" in call,
            "o lightbox do trailer volta a receber as mascaras do banner e fica preto",
        )
    }

    /**
     * And the page can talk to the player, whoever opened it.
     *
     * `enablejsapi` was set only for an automatic preview, so the lightbox's page never completed
     * the `listening` handshake and never learned the player's state. `signal('playing')` could not
     * fire, the readiness timeout called onFailed after ten seconds, and the viewer got a black box
     * with the trailer's audio playing behind it — reported twice as exactly that.
     */
    @Test
    fun `every trailer page can read the player's state`() {
        val host =
            Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/playback/TrailerHostServer.kt")
                .readText()

        assertTrue(
            "append(\"&enablejsapi=1\")" in host,
            "a pagina do trailer voltou a nao poder ler o estado do player",
        )
        assertTrue(
            "if (unattended) append(\"&enablejsapi=1\")" !in host,
            "o enablejsapi voltou a ser so para a previa automatica, e o lightbox fica preto",
        )
    }
}

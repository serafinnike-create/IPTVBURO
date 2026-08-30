package com.lucasserafin94.iptvburo.desktop.app

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The banner plays a trailer, and falls back to the poster the moment it cannot.
 *
 * A source scan, because the real thing needs a window, a Chromium runtime and a TMDb key. The
 * decision itself is covered by BannerTrailerTest in the shared model; what is worth pinning here
 * is that the pieces are connected — a lookup nobody calls and a failure nobody records both
 * compile perfectly, and the symptom would be an error frame on the opening screen.
 */
class BannerTrailerWiringTest {
    private val home =
        Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/XtreamDailyHome.kt").readText()
    private val state =
        Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/DesktopAppState.kt").readText()

    /** The banner asks for a trailer, and only for the title it has reached. */
    @Test
    fun `the banner looks up a trailer for the title on screen`() {
        assertTrue(
            home.contains("heroItem?.let(appState::loadHeroTrailer)"),
            "o banner nunca procura trailer nenhum",
        )
        // The rotation holds twenty and most are never seen; looking all twenty up would be twenty
        // requests for one viewing.
        assertTrue(
            state.contains("if (heroTrailers.containsKey(key)) return"),
            "o mesmo titulo e procurado outra vez a cada volta da rotacao",
        )
    }

    /** And it plays it behind the title. */
    @Test
    fun `the trailer is drawn in the banner`() {
        assertTrue(home.contains("HeroTrailer("), "nada toca o trailer no banner")
        assertTrue(
            home.contains("trailerId = heroItem?.let(appState::heroTrailerFor)"),
            "o banner nao recebe o trailer",
        )
        // The banner takes the default rather than passing the flag, so the default is what is
        // asserted. It became a parameter when the Descobrir card started using the same player
        // without wanting the hero's masks — those fade the left edge and the bottom, which on a
        // card with its own edges only smears the video.
        assertTrue(
            home.contains("blendIntoHero: Boolean = true"),
            "o Chromium fica como um retangulo sobreposto, sem se fundir ao hero",
        )
    }

    /**
     * The decision to play comes from the shared rule, not from the screen.
     *
     * The screen would otherwise have its own opinion about a failed video, and the three apps
     * would drift.
     */
    @Test
    fun `the decision comes from the shared rule`() {
        assertTrue(
            state.contains("BannerTrailer.shouldPlay("),
            "o banner decide sozinho se toca, em vez de usar a regra partilhada",
        )
        assertTrue(
            home.contains("delay(BannerTrailer.SETTLE_MILLIS)"),
            "o banner comeca o video antes de assentar no titulo",
        )
    }

    /**
     * A failure is remembered, so the next rotation shows artwork at once.
     *
     * A video that was pulled or made private stays that way; retrying every rotation costs the
     * viewer a wait each time for the same answer.
     */
    @Test
    fun `a failure is remembered`() {
        assertTrue(
            home.contains("heroItem?.let(appState::rememberHeroTrailerFailure)"),
            "uma falha do trailer nao e registada, e repete-se a cada volta",
        )
        assertTrue(
            state.contains("BannerTrailer.pruneFailures("),
            "as falhas guardadas nunca expiram",
        )
    }

    /**
     * And nothing is drawn when the player cannot start.
     *
     * A black rectangle on the opening screen reads as a broken app; the artwork underneath is
     * already there, so the banner simply stays as it was.
     */
    @Test
    fun `nothing is drawn when the player cannot start`() {
        // Asserted before slicing: substringAfter hands back the whole file when the marker is
        // missing, so a renamed or deleted composable would leave this test passing against nothing.
        // It already broke once the other way, when the function stopped being private.
        val marker = "fun HeroTrailer("
        assertTrue(marker in home, "HeroTrailer mudou de nome: este teste ja nao le nada")

        val block = home.substringAfter(marker).substringBefore("\n}")

        assertTrue(block.contains("if (panel == null)"), "um painel que nao abre fica na tela")
        assertTrue(block.contains("onFailed()"), "a falha nao e comunicada")
    }

    /** Scrolling away stops it. */
    @Test
    fun `scrolling stops the trailer`() {
        assertTrue(
            home.contains("scrolling = homeState.isScrollInProgress"),
            "rolar a pagina nao para o trailer",
        )
        assertTrue(
            home.contains("&& !scrolling"),
            "o trailer continua a tocar enquanto se rola",
        )
    }

    /**
     * The trailer starts muted, which is the only way it starts at all.
     *
     * Every browser engine refuses to autoplay audio: asking for sound produced a play button on
     * the banner instead of a playing trailer. Reported exactly that way.
     */
    @Test
    fun `the trailer starts muted so it can autoplay`() {
        assertTrue(
            home.contains("muted = !soundOn"),
            "o trailer pede som ao arrancar e o motor bloqueia-o",
        )
        assertTrue(
            state.contains("userStore.bannerTrailerSound()"),
            "a escolha de som nao sobrevive ao proximo arranque",
        )
    }

    /** And the viewer holds a switch for the sound, both ways round. */
    @Test
    fun `the viewer can turn the sound on and off`() {
        assertTrue(
            home.contains("onClick = onToggleSound"),
            "nao ha como ligar nem calar o som do banner",
        )
        assertTrue(
            state.contains("fun toggleBannerTrailerSound()"),
            "nada guarda a escolha do som",
        )
    }

    /**
     * The banner holds a title while its trailer plays.
     *
     * Ten seconds is right for a still poster and cut every trailer off mid-sentence, one after
     * another — the viewer never saw the part that decides whether they want the film.
     */
    @Test
    fun `the rotation waits while a trailer is playing`() {
        assertTrue(
            home.contains("BannerTrailer.HOLD_WHILE_PLAYING_MILLIS"),
            "a rotacao corta o trailer a meio",
        )
        // Only while one is actually playing: a title showing artwork keeps the ordinary pace.
        assertTrue(
            home.contains("LaunchedEffect(trailerPlaying) { onTrailerPlaying(trailerPlaying) }"),
            "uma capa parada ficaria um minuto no ecra",
        )
    }

    /** A live channel is never asked about: a channel has no trailer. */
    @Test
    fun `a live channel is not looked up`() {
        assertTrue(
            state.contains("if (item.contentType == XtreamContentType.LIVE) return"),
            "pede trailer para um canal ao vivo, que nunca tem um",
        )
    }

    /**
     * Nothing is shown until the page has actually loaded.
     *
     * Chromium paints its own white page before any content arrives, and the panel is a heavyweight
     * surface sitting above Compose, where nothing can cover it. On the banner that white never
     * showed, because the video fills a dark hero; on the Descobrir card it was a blank white
     * rectangle where the trailer should be. Reported with a screenshot of exactly that.
     */
    @Test
    fun `the player is hidden until its page has loaded`() {
        assertTrue(
            home.contains("onReady = { ready = true }"),
            "nada sabe quando a pagina do trailer acabou de carregar",
        )
        assertTrue(
            home.contains("if (ready) 1f else 0f"),
            "o Chromium aparece antes de ter video, e o que se ve e um rectangulo branco",
        )
    }
}

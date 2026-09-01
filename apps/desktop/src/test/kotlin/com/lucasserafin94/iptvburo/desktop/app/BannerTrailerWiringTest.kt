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
        // Always muted at the start, not `!soundOn`. The switch is a message to the running
        // player now, so the embed no longer carries the sound choice at all — and asking for
        // audio up front is what stops a trailer starting in the first place.
        assertTrue(
            home.contains("muted = true"),
            "o trailer pede som ao arrancar e o motor bloqueia-o",
        )
        assertTrue(
            home.contains("browser.setSound(soundOn)"),
            "o som nunca chega ao leitor que ja esta a tocar",
        )
        assertTrue(
            state.contains("userStore.bannerTrailerSound()"),
            "a escolha de som nao sobrevive ao proximo arranque",
        )
    }

    /**
     * The artwork stops where the trailer starts, and takes the banner when there is none.
     *
     * It used to run the whole width and sit behind the video — a picture nobody can see paying for
     * a decode and a crop on every rotation, and on the seam its own subject showed through the
     * player's edge. Reported as the photo appearing behind the trailer.
     */
    @Test
    fun `the artwork stops where the trailer begins`() {
        assertTrue(
            home.contains("bannerWidth * (1f - BannerTrailer.TRAILER_WIDTH_FRACTION)"),
            "a capa passa por tras do trailer em vez de parar onde ele comeca",
        )
        assertTrue(
            home.contains("Modifier.width(artworkWidth).fillMaxHeight()"),
            "a capa nao e limitada a largura que lhe sobra",
        )
    }

    /**
     * And it is the best picture the provider has.
     *
     * The catalogue's own artwork is a poster: portrait, often a few hundred pixels wide, and
     * visibly soft stretched across the largest image in the app. A backdrop is landscape and made
     * for this, and it arrives with the synopsis the banner already fetches.
     */
    @Test
    fun `the banner uses the widest picture available`() {
        assertTrue(
            home.contains("appState::heroArtworkFor"),
            "o banner usa a capa de retrato em vez da melhor imagem",
        )
        assertTrue(
            state.contains("heroBackdrop[heroSynopsisKey(item.contentType, item.providerId)]"),
            "nada guarda o fundo largo do titulo",
        )
        // From the details the synopsis fetch already makes: a picture is not worth a second call.
        assertTrue(
            state.contains("rememberHeroBackdrop(item, details.backdropUrls.firstOrNull())"),
            "o fundo custa um pedido proprio quando ja vinha com a sinopse",
        )
    }

    /**
     * The trailer leaves the scrollbar its lane down the right edge.
     *
     * An embedded video is a heavyweight surface: it paints above Compose whatever the drawing
     * order says. A trailer running to the window's edge buries the scrollbar under itself — still
     * drawn, and no longer clickable or draggable. Reported as the sidebar disappearing under the
     * trailer with no way to scroll the page.
     */
    @Test
    fun `the trailer does not bury the scrollbar`() {
        assertTrue(
            home.contains("padding(end = HERO_SCROLLBAR_LANE)"),
            "o trailer corre ate a borda e enterra a barra de rolagem por baixo dele",
        )
        assertTrue(
            home.contains("private val HERO_SCROLLBAR_LANE"),
            "a faixa da barra de rolagem nao tem largura propria",
        )
    }

    /**
     * The details page offers a trailer for series as well as films, and finds one.
     *
     * The button was inside a film-only branch and read only the provider's own field. Most
     * providers send no trailer id at all, so it never appeared for anything — reported for films
     * and series alike. The banner has searched TMDb for this all along and caches per title, so
     * reusing it costs nothing and makes the two screens agree about the same film.
     */
    @Test
    fun `the details page offers a trailer it can actually find`() {
        val workspace =
            Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/XtreamWorkspace.kt")
                .readText()

        assertTrue(
            workspace.contains("lookedUpTrailerId = appState.heroTrailerFor(item)"),
            "os detalhes so olham para o campo do fornecedor, que quase nunca vem preenchido",
        )
        assertTrue(
            workspace.contains("onNeedTrailer = { appState.loadHeroTrailer(item) }"),
            "nada pede a procura do trailer para o titulo aberto",
        )
        // Outside the film-only branch: a series shows trailers as often as a film does.
        val detail =
            workspace.substringAfter("internal fun XtreamItemDetail(")
                .substringBefore("private fun LiveEpgContent(")
        val trailerAt = detail.indexOf("trailerId?.let { id ->")
        val filmOnlyAt = detail.indexOf("if (item.contentType == XtreamContentType.MOVIE) {")
        assertTrue(trailerAt > 0, "o botao do trailer desapareceu dos detalhes")
        assertTrue(
            trailerAt < filmOnlyAt,
            "o botao do trailer voltou para dentro do ramo so-de-filmes",
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

    /**
     * A title whose provider carries no plot gets one from TMDb.
     *
     * Providers leave the description empty constantly, and a provider whose encoding destroyed the
     * accents is treated the same way. Both left the banner showing its fixed line about the daily
     * selection — which reads as a description of the title and describes nothing. Reported with a
     * 2024 series showing that line rather than its own plot.
     */
    @Test
    fun `an empty provider plot falls back to TMDb`() {
        assertTrue(
            state.contains("metadataClient.findOverview("),
            "sem sinopse do fornecedor o banner fica com a frase fixa para sempre",
        )
        // Ordered: the provider first, TMDb only when it had nothing usable. A search per banner
        // title regardless would be a request for something already in hand.
        val marker = "fun loadHeroSynopsis("
        assertTrue(marker in state, "loadHeroSynopsis mudou de nome: este teste ja nao le nada")
        val body = state.substringAfter(marker).substringBefore("\n    }")
        assertTrue(
            body.indexOf("ProviderText::usableOrNull") < body.indexOf("findOverview("),
            "a sinopse do TMDb e pedida antes de se olhar para a do fornecedor",
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
}

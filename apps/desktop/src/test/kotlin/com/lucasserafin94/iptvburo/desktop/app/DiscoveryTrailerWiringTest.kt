package com.lucasserafin94.iptvburo.desktop.app

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The Descobrir card plays a trailer beside its poster, and moves on when it has run.
 *
 * A source scan, because the real thing needs a window, a Chromium runtime and a TMDb key. The rules
 * themselves are covered by DiscoveryDeckTest in the shared model; what is worth pinning here is
 * that the pieces are connected — a lookup nobody calls and a callback nobody passes both compile
 * perfectly, and the symptom is a screen that quietly behaves as it did before.
 */
class DiscoveryTrailerWiringTest {
    private val screen =
        Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/DiscoveryScreen.kt").readText()
    private val app =
        Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/DesktopApp.kt").readText()

    /** The card on top asks for a trailer — and only that card, not the whole deck. */
    @Test
    fun `the card on top looks up a trailer`() {
        assertTrue(
            screen.contains("top?.let(onNeedTrailer)"),
            "o cartao nunca procura trailer nenhum",
        )
        assertTrue(
            app.contains("onNeedTrailer = appState::loadHeroTrailer"),
            "a procura do cartao nao esta ligada a nada",
        )
    }

    /**
     * The same lookup the banner uses.
     *
     * Two lookups would mean two answers for the same film: a trailer on the home screen and none
     * on the card, or the other way round, for no reason a viewer could see.
     */
    @Test
    fun `the card and the banner agree about a film`() {
        assertTrue(
            app.contains("trailerFor = appState::heroTrailerFor"),
            "o cartao usa uma procura diferente da do banner",
        )
    }

    /** It is drawn beside the poster, not over it. */
    @Test
    fun `the trailer is drawn next to the card`() {
        assertTrue(screen.contains("HeroTrailer("), "nada toca o trailer no cartao")
        assertTrue(
            screen.contains("TRAILER_WIDTH"),
            "o trailer nao tem largura propria ao lado da capa",
        )
    }

    /**
     * A card that ran out its trailer is passed over, never judged.
     *
     * Wired to onDecide, every card whose trailer simply finished would be filed as a film this
     * viewer rejected — a taste profile built from choices nobody made, and the title buried,
     * since a judged card does not come round again.
     */
    @Test
    fun `a finished trailer passes the card over rather than judging it`() {
        assertTrue(
            screen.contains("onPassOver(top)"),
            "o cartao nao passa sozinho quando o trailer acaba",
        )
        assertTrue(
            app.contains("onPassOver = appState::passOverDiscovery"),
            "passar a frente nao esta ligado a nada",
        )
        // And what it is wired to records nothing: the deck loses the card, and no verdict, no
        // favourite and no "seen" mark is written. Asserted on the state itself because that is
        // where a wrong wiring would do the damage.
        val state =
            Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/DesktopAppState.kt").readText()
        // Checked before slicing: substringAfter hands back the whole file when the marker is
        // missing, and the assertions below would then be reading DesktopAppState entire.
        assertTrue(
            "fun passOverDiscovery(" in state,
            "passOverDiscovery mudou de nome: este teste ja nao le nada",
        )
        val body = state.substringAfter("fun passOverDiscovery(").substringBefore("\n    }")
        assertTrue("discoveryDeck = " in body, "passar a frente nao tira o cartao da mao")
        assertTrue("discoverySeen" !in body, "um filme que ninguem julgou ficou marcado como visto")
        assertTrue("toggleFavorite" !in body, "passar a frente marcou um favorito")
        assertTrue("discoverySession" !in body, "passar a frente ensinou o perfil de gosto")
    }

    /** And a card with no trailer waits to be judged, rather than sliding away. */
    @Test
    fun `a card without a trailer does not advance on its own`() {
        assertTrue(
            screen.contains("DiscoveryDeck.advancesOnItsOwn(trailerId)"),
            "um cartao sem trailer tambem passa sozinho",
        )
    }

    /** The sound is the banner's own, so the choice is made once and honoured everywhere. */
    @Test
    fun `the sound choice is shared with the banner`() {
        assertTrue(
            app.contains("soundOn = appState.bannerTrailerSound"),
            "o som do cartao e uma escolha separada da do banner",
        )
    }
}

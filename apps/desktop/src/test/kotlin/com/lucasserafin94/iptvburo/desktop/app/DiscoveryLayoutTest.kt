package com.lucasserafin94.iptvburo.desktop.app

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Descobrir keeps one shape whether or not the film has a trailer.
 *
 * The decision buttons and the synopsis sit below the video. On a film with no trailer that column
 * had nothing above it, so they climbed to the top and the screen changed shape between cards —
 * reported as the text and buttons jumping upwards.
 *
 * A source scan, because the real check needs a window, a connected source and a card whose film
 * has no trailer. What it pins is that the space is still reserved.
 */
class DiscoveryLayoutTest {
    private val screen =
        Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/DiscoveryScreen.kt").readText()

    @Test
    fun `a film without a trailer still reserves the trailer's space`() {
        val marker = "} else if (trailerFits) {"
        assertTrue(marker in screen, "o ramo sem trailer mudou: este teste ja nao le nada")

        val branch = screen.substringAfter(marker).substringBefore("DiscoveryDecisionActions")

        assertTrue(
            "aspectRatio(16f / 9f)" in branch,
            "os botoes e a sinopse voltam a subir ao topo num filme sem trailer",
        )
    }

    /**
     * And the browser panel still stops a pixel short of the card.
     *
     * Its own final row renders near-white whatever the page paints, and Compose cannot cover a
     * heavyweight AWT surface — ending the panel early is what puts that row outside the card.
     */
    @Test
    fun `the trailer panel stops short of the card edge`() {
        val marker = "HeroTrailer("
        assertTrue(marker in screen, "o trailer mudou de nome: este teste ja nao le nada")

        // To the end of the call's argument list, not to the first ")" — that one closes
        // fillMaxSize() and cut the modifier off before the padding it is looking for.
        val call = screen.substringAfter(marker).substringBefore("blendIntoHero")

        assertTrue(
            "padding(1.dp)" in call,
            "as linhas brancas voltam a aparecer nas margens do trailer em Descobrir",
        )
    }

    /**
     * And the banner's player, which is the same surface with a different frame.
     *
     * Fixed on the Descobrir card first; the banner kept its bright rule down the right-hand side,
     * against the scrollbar lane, because the two call sites carry their own modifiers.
     */
    @Test
    fun `the banner trailer panel stops short of its frame`() {
        val home =
            Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/XtreamDailyHome.kt")
                .readText()

        val marker = "HeroTrailer("
        assertTrue(marker in home, "o trailer do banner mudou de nome: este teste ja nao le nada")

        val call = home.substringAfter("youtubeId = activeTrailerId").substringBefore("soundOn =")

        assertTrue(
            "padding(1.dp)" in call,
            "a linha branca volta a aparecer na margem do trailer do banner",
        )
    }
}

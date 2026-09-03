package com.lucasserafin94.iptvburo.desktop.app

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A film with no trailer says so, in the space the trailer would have used.
 *
 * That box is reserved either way, so the decision buttons and synopsis do not jump between cards.
 * Left empty it read as a broken player rather than an honest answer, asked for directly.
 *
 * A source scan, because the real check needs a window, a connected source and a card whose film
 * has no trailer. What it pins is that the reserved space carries a message.
 */
class DiscoveryNoTrailerNoticeTest {
    // Normalized to \n: a fresh checkout on Windows turns every line ending into \r\n, which
    // silently breaks a marker string with a hardcoded \n baked into it. See
    // BannerTrailerEndedTest / WindowTeardownTest for the CI failure this caused once already.
    private val screen =
        Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/DiscoveryScreen.kt")
            .readText()
            .replace("\r\n", "\n")

    @Test
    fun `the reserved trailer space shows a notice instead of staying blank`() {
        val marker = "} else if (trailerFits) {"
        assertTrue(marker in screen, "o ramo sem trailer mudou: este teste ja nao le nada")

        val branch = screen.substringAfter(marker).substringBefore("DiscoveryDecisionActions")

        assertTrue(
            "text.noTrailer" in branch,
            "o espaco reservado ao trailer volta a ficar em branco, sem aviso, num filme sem trailer",
        )
    }
}

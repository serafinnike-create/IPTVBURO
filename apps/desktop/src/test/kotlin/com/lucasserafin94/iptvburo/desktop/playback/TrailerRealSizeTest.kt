package com.lucasserafin94.iptvburo.desktop.playback

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * "playing" never hands CEF a fabricated size.
 *
 * Measured with a log line before this was understood: the panel is genuinely 0x0 when YouTube
 * first reports playback, before Compose has laid the SwingPanel out. `coerceAtLeast(1)` on that
 * call turned the real 0x0 into a reported 1x1 — CEF's own viewport locked onto that, and YouTube's
 * IFrame Player script, which sizes itself once against the container it is first given, kept the
 * video small and off-centre long after the panel had grown to its real size on screen. Reported as
 * the trailer sitting small with YouTube's own controls showing through, on the banner and in
 * Descobrir alike.
 *
 * A source scan, because the real check needs Chromium and a window.
 */
class TrailerRealSizeTest {
    // Normalized to \n: see BannerTrailerEndedTest for the CI failure a hardcoded \n marker caused
    // once already, on a fresh Windows checkout's CRLF conversion.
    private val browser =
        Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/playback/TrailerBrowser.kt")
            .readText()
            .replace("\r\n", "\n")

    @Test
    fun `the playing signal only resizes with a real, non-zero size`() {
        val marker = "\"playing\" ->"
        assertTrue(marker in browser, "o sinal de playing mudou: este teste ja nao le nada")

        val handler = browser.substringAfter(marker).substringBefore("\"failed\" ->")

        assertTrue(
            "val w = browserComponent.width\n" in handler,
            "o sinal de playing volta a fingir um tamanho de 1x1 em vez do real",
        )
        assertTrue(
            "if (w > 0 && h > 0) browser?.wasResized(w, h)" in handler,
            "o sinal de playing ja nao guarda o CEF de um tamanho fabricado",
        )
    }

    @Test
    fun `the AWT layout listener is what sends the real size once it exists`() {
        val marker = "override fun componentResized"
        assertTrue(marker in browser, "o listener de resize mudou: este teste ja nao le nada")

        val body = browser.substringAfter(marker).substringBefore("},\n                )")

        assertTrue(
            "child.width <= 0 || child.height <= 0" in body,
            "o listener de resize deixou de recusar tamanhos ainda nao assentes",
        )
        assertTrue(
            "wasResized(child.width, child.height)" in body,
            "o listener de resize ja nao informa o CEF do tamanho real assim que ele existe",
        )
    }
}

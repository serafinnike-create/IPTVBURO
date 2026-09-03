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
            "if (w > 0 && h > 0) {" in handler,
            "o sinal de playing ja nao guarda o CEF de um tamanho fabricado",
        )
        assertTrue(
            "browser?.wasResized(w, h)" in handler,
            "o sinal de playing deixou de informar o CEF do tamanho quando ele e real",
        )
        assertTrue(
            "pushPlayerSize(w, h)" in handler,
            "o sinal de playing deixou de pedir ao proprio player do YouTube para se redimensionar",
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
        assertTrue(
            "pushPlayerSize(child.width, child.height)" in body,
            "o listener de resize deixou de pedir ao proprio player do YouTube para se " +
                "redimensionar -- wasResized() muda a superficie do CEF mas nao o layout interno " +
                "do player, que so o setSize da API do YouTube alcanca",
        )
    }

    /**
     * Chromium is told the display scale, because that is where the page is laid out.
     *
     * Measured on a screen at 125%: Windows gives the panel 919x465 physical pixels while Chromium
     * laid the page out for 735x372 and painted it into the top-left corner -- black down the right
     * and along the bottom, the video filling exactly 80% of each axis. Sizing the player from the
     * Java side was tried first and measured to change nothing, because the player takes its size
     * from the iframe element, and that element is sized by the page's CSS against Chromium's own
     * viewport.
     */
    @Test
    fun `the engine is told the display scale factor`() {
        assertTrue(
            "--force-device-scale-factor=" in browser,
            "o Chromium volta a assumir uma escala de 1.0, e o video encolhe para 80% num ecra a 125%",
        )
        assertTrue(
            "--high-dpi-support=1" in browser,
            "o suporte de alta densidade deixou de ser pedido ao Chromium",
        )
    }

    /**
     * And the scale reaches the flag as a dot, whatever the machine's locale says.
     *
     * Chromium parses "1,25" as 1, so a comma locale would silently undo the whole fix -- and the
     * machine this was found on is set to one.
     */
    @Test
    fun `the scale is formatted with a dot, not the locale's separator`() {
        val marker = "private fun primaryScreenScale"
        assertTrue(marker in browser, "o primaryScreenScale mudou de nome: este teste ja nao le nada")

        val body = browser.substringAfter(marker).substringBefore("private fun sharedApp")

        assertTrue(
            "Locale.ROOT" in body,
            "a escala volta a ser formatada com a virgula da localizacao, e o Chromium le-a como 1",
        )
    }

    /**
     * The player itself is sized in logical pixels, since the engine now handles the scale.
     */
    @Test
    fun `the player size is not scaled a second time`() {
        val marker = "private fun pushPlayerSize"
        assertTrue(marker in browser, "o pushPlayerSize mudou de nome: este teste ja nao le nada")

        val body = browser.substringAfter(marker).substringBefore("fun setSound")

        assertTrue(
            "val width = widthPx" in body && "val height = heightPx" in body,
            "a escala do ecra volta a ser aplicada aqui, em cima da que o motor ja aplica",
        )
    }
}

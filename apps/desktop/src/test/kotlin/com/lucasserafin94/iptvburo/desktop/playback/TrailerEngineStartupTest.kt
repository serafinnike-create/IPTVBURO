package com.lucasserafin94.iptvburo.desktop.playback

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Chromium is given a cache directory, because without one it does not start.
 *
 * This is the whole of the white-rectangle defect. `cache_path = null` read as the private choice —
 * nothing about a trailer needs to survive the session — and it made CEF refuse to initialise:
 * `N_Initialize failed` in Chromium's own log, and from then on every call on every browser
 * answering "can't invoke native method … before native context initialized".
 *
 * What that looked like from outside was a blank white rectangle where the trailer should be, on the
 * Descobrir card and then across the whole banner. Nothing about it pointed at the cache: the panel
 * was created, sized and parented, the page was served with HTTP 200, the runtime was complete, and
 * the engine reported no error to Java at all. Eight causes were ruled out before turning
 * Chromium's own logging on, which named it in one line.
 *
 * A source scan, because the real check needs a Chromium runtime and a window. What it pins is the
 * pair of facts that cost a night: there is a cache path, and it is not permanent.
 */
class TrailerEngineStartupTest {
    private val browser =
        Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/playback/TrailerBrowser.kt")
            .readText()

    /**
     * There is a cache path at all.
     *
     * Without one CEF does not come up, and every trailer in the app is a white rectangle.
     */
    @Test
    fun `the engine is given somewhere to cache`() {
        assertTrue(
            browser.contains("cache_path = scratchCache()"),
            "sem cache_path o CEF nao arranca, e todos os trailers ficam brancos",
        )
        // Read from the code, not the prose: the comment above the setting quotes the old value,
        // and a plain search for it matches that explanation rather than any assignment.
        val code = browser.lines().filterNot { it.trim().startsWith("//") }.joinToString("\n")
        assertFalse(
            code.contains("cache_path = null"),
            "o cache_path a null impede o motor de inicializar",
        )
    }

    /**
     * And it is thrown away, so the privacy the null was reaching for is kept.
     *
     * A browser cache inside an IPTV app is a record of what somebody watched. The directory lives
     * under the system temp directory and a shutdown hook deletes it, which leaves nothing behind
     * and still lets the engine start.
     */
    @Test
    fun `the cache does not outlive the session`() {
        val marker = "private fun scratchCache(): String {"
        assertTrue(marker in browser, "scratchCache mudou de nome: este teste ja nao le nada")
        val body = browser.substringAfter(marker).substringBefore("\n        }")

        assertTrue(
            body.contains("java.io.tmpdir"),
            "a cache do navegador ficaria num sitio permanente",
        )
        assertTrue(
            body.contains("addShutdownHook") && body.contains("deleteRecursively"),
            "a cache do navegador sobrevive a sessao, e e um registo do que se viu",
        )
    }

    /** And session cookies are still not persisted: the cache is for starting, not for keeping. */
    @Test
    fun `session cookies are still not kept`() {
        assertTrue(
            browser.contains("persist_session_cookies = false"),
            "as cookies de sessao passaram a ser guardadas",
        )
    }

    /**
     * Chromium is told it may start a video without being clicked first.
     *
     * Its default policy is `document-user-activation-required`: a page may not play anything until
     * somebody has interacted with it. A banner nobody clicks never satisfies that, so the trailer
     * sat on its first frame however correct the embed was — `autoplay=1` and `mute=1` both arrived
     * intact and were simply refused. Reported as the video not starting on its own, in the banner
     * and on the Descobrir card alike.
     *
     * This is a separate rule from the muted-audio one, which stays: the trailer still starts
     * silent and raises the sound once it is playing.
     */
    @Test
    fun `the engine may start a video nobody clicked`() {
        assertTrue(
            browser.contains("--autoplay-policy=no-user-gesture-required"),
            "o Chromium exige um clique antes de tocar, e o banner nunca leva nenhum",
        )
    }

    /**
     * Chromium's logging stays off in what ships.
     *
     * It was turned on to find this, and it writes a file naming every page the engine loads —
     * which is exactly the record this whole arrangement exists to avoid keeping.
     */
    @Test
    fun `chromium's own logging is off`() {
        assertTrue(
            browser.contains("log_severity = CefSettings.LogSeverity.LOGSEVERITY_DISABLE"),
            "o log do Chromium ficou ligado, e ele regista as paginas que abriu",
        )
        assertFalse(
            browser.contains("log_file ="),
            "o Chromium escreve um ficheiro de log com o que foi aberto",
        )
    }
}

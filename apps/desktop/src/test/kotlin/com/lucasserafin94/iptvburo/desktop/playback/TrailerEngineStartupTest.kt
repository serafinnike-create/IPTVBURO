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

    /**
     * A cache directory per run, and a sweep of the ones nobody is using.
     *
     * Chromium takes a singleton lock on its cache directory. A run that did not shut down cleanly
     * leaves that lock held, and the next start then fails outright — `N_Initialize failed`, and
     * every trailer in the app is a dead rectangle. That is exactly what came back the morning
     * after this was first fixed: the directory was still there from the night before.
     *
     * The sweep skips directories whose process is still alive, so a second copy of the app running
     * right now keeps its own cache rather than having it deleted underneath it.
     */
    @Test
    fun `a stale cache lock cannot stop the engine starting`() {
        val marker = "private fun scratchCache(): String {"
        assertTrue(marker in browser, "scratchCache mudou de nome: este teste ja nao le nada")
        val body = browser.substringAfter(marker).substringBefore("\n        }")

        assertTrue(
            body.contains("ProcessHandle.current().pid()"),
            "duas copias da app partilham o mesmo directorio, e disputam o mesmo bloqueio",
        )
        assertTrue(
            body.contains("ProcessHandle.of(pid).isEmpty"),
            "a limpeza apagaria a cache de uma copia da app que esta a correr agora",
        )
        assertTrue(
            body.contains("deleteRecursively"),
            "um bloqueio deixado por um arranque anterior impede o motor de arrancar outra vez",
        )
    }

    /**
     * A pid alone does not prove a directory is still in use.
     *
     * The sweep skipped any directory whose pid belonged to a live process. But the operating
     * system hands pid numbers out again, so a directory left by a run that crashed before a
     * restart can carry a number some unrelated program now holds — and it would be spared for
     * ever, its Chromium lock held, with the engine refusing to start on every launch after that.
     * That failure already cost a night once; age closes the hole a pid leaves open.
     */
    @Test
    fun `an abandoned cache is swept even when its pid was handed out again`() {
        val marker = "private fun scratchCache(): String {"
        assertTrue(marker in browser, "scratchCache mudou de nome: este teste ja nao le nada")
        val body = browser.substringAfter(marker).substringBefore("\n        }")

        assertTrue(
            body.contains("stale.lastModified() < staleBefore"),
            "um pid reciclado deixa a pasta antiga para sempre, e o motor nunca mais arranca",
        )
        // And never our own, whatever its age: a machine left running for days would otherwise
        // have a second copy of the app delete the cache the first one is still using.
        assertTrue(
            body.contains("pid == ours -> false"),
            "uma segunda copia da app apaga a cache que a primeira ainda esta a usar",
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

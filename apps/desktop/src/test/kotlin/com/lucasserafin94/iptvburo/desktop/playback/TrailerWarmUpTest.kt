package com.lucasserafin94.iptvburo.desktop.playback

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Starting Chromium during boot rather than on the first press of "Ver trailer".
 *
 * Reported as the app freezing: the first trailer of a session pays to start an entire embedded
 * browser, with nothing on screen to say so. The fix moves that cost to startup, and what has to
 * stay true of it is that it never becomes a cost of its own — it must not run on the interface
 * thread, must not hold the process open, and must not be paid twice.
 *
 * Read from the source because the alternative is starting Chromium inside the test suite, which
 * is slow, needs a native runtime that CI may not have, and cannot be undone once done.
 */
class TrailerWarmUpTest {
    private val browser =
        Path
            .of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/playback/TrailerBrowser.kt")
            .readText()

    private val main =
        Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/Main.kt").readText()

    private fun warmUpBody(): String {
        val marker = "fun warmUp() {"
        assertTrue(browser.contains(marker), "warmUp was renamed; this test needs updating")
        return browser.substringAfter(marker).substringBefore("\n        }")
    }

    @Test
    fun `startup asks for the warm-up`() {
        assertTrue(
            main.contains("TrailerBrowser.warmUp()"),
            "without the call the first trailer still pays for Chromium, which is the whole report",
        )
    }

    @Test
    fun `the warm-up never runs on the thread that draws the window`() {
        // Starting Chromium inline here would freeze the window at boot instead of at the first
        // trailer, which is the same defect moved rather than fixed.
        assertTrue(warmUpBody().contains("Thread {"), "it runs on its own thread")
    }

    @Test
    fun `the warm-up cannot keep the app running after its window closes`() {
        assertTrue(
            warmUpBody().contains("isDaemon = true"),
            "a non-daemon thread part-way through starting Chromium would outlive the window",
        )
    }

    @Test
    fun `the warm-up yields to the interface`() {
        assertTrue(
            warmUpBody().contains("priority = Thread.MIN_PRIORITY"),
            "it is an optimisation and must not compete with drawing, like the other boot work",
        )
    }

    @Test
    fun `asking twice does not start two`() {
        // Both would then race into the same synchronised lock, and the second would sit there
        // holding a thread for the whole of Chromium's startup for nothing.
        assertTrue(warmUpBody().contains("warming.getAndSet(true)"))
    }

    @Test
    fun `a machine with no Chromium is not asked twice`() {
        // sharedApp records a failed start, so the warm-up cannot turn a missing runtime into a
        // repeated cost. That flag is what makes warmUp safe to call unconditionally.
        assertTrue(browser.contains("unavailable = true"), "a failure is remembered")
        assertTrue(
            browser.contains("if (unavailable) return null"),
            "and checked before trying again",
        )
    }

    @Test
    fun `the warm-up cannot take the app down`() {
        assertTrue(
            warmUpBody().contains("runCatching"),
            "a Chromium failure during boot must not reach an uncaught handler",
        )
    }
}

package com.lucasserafin94.iptvburo

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Nothing survives the window that could still try to draw into it.
 *
 * Both background scopes write Compose state when their work finishes. Left running past the
 * window, a cast discovery or a shelf load that was still out comes back to a scene whose Skia
 * layer is gone — and Compose does not fail quietly: it puts "SkiaLayer is disposed" on screen in
 * a dialog box the viewer has to dismiss. Reported as an error appearing constantly.
 *
 * `downloadScope` was the one left behind. A source scan, because the real check needs a window and
 * a running app; what it pins is that every scope is cancelled and every exit path cancels them.
 */
class WindowTeardownTest {
    // Normalized to \n: a fresh checkout on Windows applies core.autocrlf and turns every line
    // ending into \r\n. A substringBefore("\n    }") that never matches does not fail loudly — it
    // returns the whole rest of the file, and a `"... " in body` assertion against that oversized
    // body can pass by accident on text found anywhere further down, not at the boundary intended.
    private val state =
        Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/DesktopAppState.kt")
            .readText()
            .replace("\r\n", "\n")
    private val main =
        Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/Main.kt")
            .readText()
            .replace("\r\n", "\n")

    /**
     * Every long-lived scope is cancelled, not just the one somebody remembered.
     *
     * Asserted by counting the scopes rather than naming them: a third scope added later would
     * otherwise slip past a test that only knows about the two here today.
     */
    @Test
    fun `closing the window cancels every background scope`() {
        val marker = "fun dispose() {"
        assertTrue(marker in state, "dispose mudou de nome: este teste ja nao le nada")
        val body = state.substringAfter(marker).substringBefore("\n    }")

        val scopes =
            Regex("""private val (\w+Scope) =""")
                .findAll(state)
                .map { match -> match.groupValues[1] }
                .toList()

        assertTrue(scopes.isNotEmpty(), "nenhum scope encontrado: este teste ja nao le nada")
        scopes.forEach { scope ->
            assertTrue(
                body.contains("$scope.cancel()"),
                "o $scope continua a correr depois da janela fechar, e escreve estado num Skia " +
                    "que ja nao existe",
            )
        }
    }

    /**
     * And every way out of the app goes through it.
     *
     * There are two — the ordinary close and the one that hands over to an installer — and a third
     * added later without the teardown would leak in exactly the same way.
     */
    @Test
    fun `every exit path tears the app down first`() {
        val exits = Regex("""exitApplication\(\)""").findAll(main).count()
        val teardowns = Regex("""appState\.dispose\(\)""").findAll(main).count()

        assertEquals(
            exits,
            teardowns,
            "ha uma saida da app que nao desmonta o estado antes de sair",
        )
    }

    /**
     * And that teardown closes every trailer still playing, not just the coroutine scopes.
     *
     * Each TrailerBrowser disposes itself through Compose's own DisposableEffect in the normal
     * case, but exitApplication() stops the composition without guaranteeing every onDispose runs
     * first — a heavyweight Chromium child can survive it. Reported as the trailer's audio still
     * playing, and the process still resident, after the window had already closed.
     */
    @Test
    fun `closing the window silences every trailer still playing`() {
        val marker = "fun dispose() {"
        val body = state.substringAfter(marker).substringBefore("\n    }")

        assertTrue(
            "TrailerBrowser.disposeAll()" in body,
            "fechar a janela deixa o trailer a tocar som, e o processo residente",
        )
    }
}

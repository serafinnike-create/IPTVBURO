package com.lucasserafin94.iptvburo.ui

import java.nio.file.Path
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The connection test has to be reachable, not merely present.
 *
 * A button that calls nothing and a screen nobody draws both compile perfectly and do nothing. This
 * has already happened once on Windows, where the two measurement probes were added to the
 * interface and to the repository but never to the switcher in between — the screen reported "could
 * not measure the speed" on a connection that had just loaded seventy thousand titles.
 *
 * A source scan because the alternative needs an Android runtime for a Compose tree, and what is
 * worth pinning here is that the ends are joined.
 */
class DiagnosticsWiringTest {
    private fun read(relative: String): String = Path.of(relative).readText()

    private val shell =
        read("src/main/kotlin/com/lucasserafin94/iptvburo/ui/screens/AppShellScreen.kt")
    private val viewModel = read("src/main/kotlin/com/lucasserafin94/iptvburo/ui/MainViewModel.kt")
    private val activity = read("src/main/kotlin/com/lucasserafin94/iptvburo/MainActivity.kt")
    private val dialog =
        read("src/main/kotlin/com/lucasserafin94/iptvburo/ui/screens/DiagnosticsDialog.kt")

    @Test
    fun `the top bar carries the button`() {
        assertTrue(
            "sem botao, ninguem chega ao teste",
            shell.contains("onOpenDiagnostics") && shell.contains("R.string.diagnostics_action"),
        )
    }

    @Test
    fun `the button opens the dialog`() {
        assertTrue(
            "o botao nao abre nada",
            shell.contains("diagnosticsOpen = true") && shell.contains("DiagnosticsDialog("),
        )
    }

    @Test
    fun `the dialog reaches a real measurement`() {
        assertTrue("a activity nao liga o teste", activity.contains("onRunDiagnostics"))
        assertTrue("o view model nao corre nada", viewModel.contains("connectionTester.run("))
    }

    /**
     * The loading effect the owner asked for by name.
     *
     * Without it a test that fails instantly finishes before the screen draws a frame, and pressing
     * the button looks like nothing happening — which is exactly how it was reported.
     */
    @Test
    fun `the dialog shows the work happening`() {
        assertTrue("sem linhas pendentes", dialog.contains("PENDING_ROWS"))
        assertTrue("sem duracao minima", dialog.contains("MINIMUM_VISIBLE_MILLIS"))
        assertTrue("sem indicador", dialog.contains("CircularProgressIndicator"))
    }

    /** A reading with no sentence under it is a number nobody can act on. */
    @Test
    fun `every reading can produce advice`() {
        listOf("download", "link", "catalogue", "memory").forEach { id ->
            assertTrue("$id nao tem conselho", dialog.contains("\"$id\""))
        }
    }
}

package com.lucasserafin94.iptvburo.desktop.app

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Music announces itself before there is any music.
 *
 * The section was hidden until a playlist had been loaded, which is backwards: a profile with no M3U
 * is exactly the one that needs telling the feature exists. With nothing in the sidebar there was no
 * difference between "add a file to use this" and "this was never built" — and the user reported the
 * second while the truth was the first.
 *
 * The same shape of mistake hid multiview for days. Both were complete features made invisible by a
 * condition that assumed the user already knew about them.
 */
class MusicDiscoverabilityTest {
    private val appSource: String =
        Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/DesktopApp.kt").readText()

    private val workspaceSource: String =
        Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/MusicWorkspace.kt").readText()

    @Test
    fun `the sidebar entry does not wait for a library`() {
        assertTrue(
            appSource.contains("hasMusic = capabilities.audioSupported,"),
            "the entry must depend on the feature being released, not on a file existing",
        )
        assertFalse(
            appSource.contains("hasMusic = capabilities.audioSupported && appState.hasMusicLibrary"),
            "gating on the library is what made the feature invisible",
        )
    }

    /**
     * An empty section explains itself and offers the fix.
     *
     * Showing empty shelves would be worse than hiding the section: it looks broken rather than
     * unconfigured, and there is still nothing to act on.
     */
    @Test
    fun `an empty music section says what to do`() {
        assertTrue(workspaceSource.contains("if (!appState.hasMusicLibrary)"), "no empty state")
        assertTrue(workspaceSource.contains("musicEmptyTitle"), "the empty state must be titled")
        assertTrue(workspaceSource.contains("musicEmptyBody"), "and explain itself")
        assertTrue(
            workspaceSource.contains("chooseLocalPlaylist(ownerWindow)"),
            "and offer the file picker, which is the only thing that resolves it",
        )
    }

    @Test
    fun `the empty state is translated into all four languages`() {
        val strings = Path
            .of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/ui/DesktopStrings.kt")
            .readText()

        for (key in listOf("musicEmptyTitle", "musicEmptyBody")) {
            val uses = Regex("""\b$key = "[^"]+"""").findAll(strings).count()
            assertTrue(uses >= 4, "$key is translated $uses times, expected at least four")
        }
    }

    /**
     * The release gate still governs.
     *
     * Discoverability is not the same as shipping something unfinished: the manifest remains the one
     * thing that decides whether music appears at all.
     */
    @Test
    fun `music still respects the capability manifest`() {
        assertTrue(
            appSource.contains("capabilities.audioSupported"),
            "the manifest must remain the release gate",
        )
    }
}

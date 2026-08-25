package com.lucasserafin94.iptvburo.desktop.app

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * One finished film, one row.
 *
 * Reported with a screenshot: after a download completed the same film was listed twice, once with
 * its poster and once without. The copy is moved into place before the sidecar beside it is
 * written, so a refresh landing in that window recovers the film under the sanitised file name —
 * a key nothing else in the app uses — and the real key arrives later without displacing it.
 */
class DownloadRowDeduplicationTest {
    private val state =
        Path
            .of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/DesktopAppState.kt")
            .readText()

    private fun refreshBody(): String {
        val marker = "fun refreshDownloadStates() {"
        // substringAfter returns the whole file when its marker is missing, which would leave every
        // check below searching all of DesktopAppState and passing on an unrelated line.
        assertTrue(state.contains(marker), "the refresh was renamed; this test needs updating")
        return state.substringAfter(marker).substringBefore("\n    }")
    }

    @Test
    fun `a row recovered under the file name is dropped once the real key arrives`() {
        val body = refreshBody()
        assertTrue(body.contains("supersededByRealKey"), "the stale key is identified")
        assertTrue(
            body.contains("downloadMetadata = (stored + downloadMetadata) - supersededByRealKey"),
            "and removed from the metadata, or the poster-less row keeps its title",
        )
        assertTrue(
            body.contains("- supersededByRealKey") &&
                body.substringAfter("downloads =").contains("- supersededByRealKey"),
            "and from the states, which is what the list is actually built from",
        )
    }

    @Test
    fun `only a key with no file of its own is dropped`() {
        // A download that genuinely has no sidecar is still a finished copy and must keep its row.
        // Dropping every sanitised name would hide it entirely.
        assertTrue(
            refreshBody().contains("!stored.containsKey(sanitised)"),
            "a sanitised key that is itself a stored download must survive",
        )
    }

    @Test
    fun `the sanitising rule is the download manager's own`() {
        // Copying it here would let the two drift apart, and the only symptom would be the
        // duplicate row silently returning.
        assertTrue(
            refreshBody().contains("downloadManager::safeName"),
            "one rule, owned by the component that names the files",
        )
    }
}

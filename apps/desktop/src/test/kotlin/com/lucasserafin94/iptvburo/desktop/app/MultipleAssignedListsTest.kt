package com.lucasserafin94.iptvburo.desktop.app

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A second list from the seller lands beside the first, not on top of it.
 *
 * The seller's panel holds one pending delivery per machine — a deliberate limit, so a stale
 * configuration cannot be applied after a newer one — but that is about the *queue*, not about the
 * customer's library. Applying a second list has to leave the first where it is: someone may have
 * bought a second subscription, or be trying a replacement while the old one still works.
 *
 * The opposite behaviour is the dangerous one and cannot be undone from the app: a delivery that
 * silently replaced a working list would take away something the viewer might still be paying for.
 */
class MultipleAssignedListsTest {
    private fun read(relative: String): String = Path.of(relative).readText()

    private val state = read("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/DesktopAppState.kt")
    private val library =
        read("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/security/XtreamSourceLibrary.kt")

    private fun collectorBody(): String {
        val marker = "suspend fun collectProvisionedSource() {"
        // substringAfter returns the whole file when its marker is missing, which would leave every
        // check below searching all of DesktopAppState and passing on an unrelated line.
        assertTrue(state.contains(marker), "the collector was renamed; this test needs updating")
        return state.substringAfter(marker).substringBefore("\n    }")
    }

    @Test
    fun `the library appends rather than replaces`() {
        val marker = "fun create(label: String): XtreamSource {"
        assertTrue(library.contains(marker), "create was renamed; this test needs updating")
        val body = library.substringAfter(marker).substringBefore("\n    }")
        assertTrue(
            body.contains("write(sources() + source)"),
            "a second list has to join the first, not overwrite the set",
        )
    }

    @Test
    fun `applying a delivery never removes what the viewer already had`() {
        // The property that cannot be recovered afterwards.
        val body = collectorBody()
        listOf("sourceLibrary.remove", "sourceLibrary.clear", ".forget(", "= emptyList()").forEach {
            assertFalse(
                body.contains(it),
                "a remotely applied list must never delete the viewer's own sources: $it",
            )
        }
    }

    @Test
    fun `each delivery gets its own identity`() {
        // Two lists from the same seller, on the same host, must still be two rows — otherwise the
        // second would be indistinguishable from the first in the picker.
        val marker = "fun create(label: String): XtreamSource {"
        val body = library.substringAfter(marker).substringBefore("\n    }")
        assertTrue(body.contains("UUID.randomUUID()"), "identity is per source, not per host")
    }

    @Test
    fun `credentials are stored per source rather than in one shared slot`() {
        // One shared slot would mean the second list's password overwriting the first's, leaving a
        // source in the picker that can no longer sign in.
        assertTrue(
            library.contains("directory.resolve(\"source-\$sourceId.dpapi\")"),
            "each source keeps its own protected file",
        )
    }
}

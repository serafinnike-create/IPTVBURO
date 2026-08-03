package com.lucasserafin94.iptvburo.desktop.security

import java.nio.file.Files
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.io.path.deleteRecursively
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class XtreamSourceLibraryTest {
    private fun <T> withLibrary(block: (XtreamSourceLibrary) -> T): T {
        val node = Preferences.userRoot().node("com/lucasserafin94/iptvburo/test-${UUID.randomUUID()}")
        val directory = Files.createTempDirectory("iptvburo-sources")
        return try {
            block(XtreamSourceLibrary(node, directory))
        } finally {
            node.removeNode()
            @OptIn(kotlin.io.path.ExperimentalPathApi::class)
            directory.deleteRecursively()
        }
    }

    @Test
    fun `sources are listed in creation order with their labels`() {
        withLibrary { library ->
            library.create("Casa")
            library.create("Trabalho")

            assertEquals(listOf("Casa", "Trabalho"), library.sources().map(XtreamSource::label))
        }
    }

    /**
     * Two playlists must not share a credential file, or connecting the second would silently
     * replace the first — the whole point of a per-profile subscription.
     */
    @Test
    fun `each source stores its credentials separately`() {
        withLibrary { library ->
            val first = library.create("Casa")
            val second = library.create("Trabalho")
            assertNotEquals(first.id, second.id)

            val firstStore = library.store(first.id)
            val secondStore = library.store(second.id)
            firstStore.save("host-a".toCharArray(), "user-a".toCharArray(), "pass-a".toCharArray())
            secondStore.save("host-b".toCharArray(), "user-b".toCharArray(), "pass-b".toCharArray())

            // DPAPI is Windows-only; elsewhere save is a documented no-op and there is nothing to
            // read back, so only the separation of identities is asserted.
            val reloaded = firstStore.load()
            if (reloaded != null) {
                assertEquals("host-a", String(reloaded.copyServer()))
                reloaded.clear()
                val other = secondStore.load()
                assertEquals("host-b", String(requireNotNull(other).copyServer()))
                other.clear()
            }
        }
    }

    /** The label is user text, so a name containing the record separators must survive a round trip. */
    @Test
    fun `labels containing separators round trip`() {
        withLibrary { library ->
            library.create("Casa; principal: 4K")

            assertEquals("Casa; principal: 4K", library.sources().single().label)
        }
    }

    @Test
    fun `renaming keeps the identity so profiles stay attached`() {
        withLibrary { library ->
            val source = library.create("Casa")
            library.rename(source.id, "Casa 4K")

            assertEquals(source.id, library.sources().single().id)
            assertEquals("Casa 4K", library.sources().single().label)
        }
    }

    @Test
    fun `removing drops the source from the list`() {
        withLibrary { library ->
            val keep = library.create("Casa")
            val drop = library.create("Antiga")
            library.remove(drop.id)

            assertEquals(listOf(keep.id), library.sources().map(XtreamSource::id))
        }
    }

    @Test
    fun `an empty library lists nothing`() {
        withLibrary { library ->
            assertTrue(library.sources().isEmpty())
        }
    }
}

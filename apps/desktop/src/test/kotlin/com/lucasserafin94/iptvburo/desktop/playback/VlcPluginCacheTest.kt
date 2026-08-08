package com.lucasserafin94.iptvburo.desktop.playback

import java.io.File
import java.nio.file.Files
import kotlin.io.path.deleteRecursively
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * When the plugin index counts as stale, and when it must be left alone.
 *
 * The failure this guards against was invisible: the index was generated at build time against the
 * build directory, the installer copied the plugins elsewhere with new timestamps, and VLC then
 * rejected every entry and rescanned several hundred DLLs on every launch. Nothing reported an
 * error — the app simply took ten seconds longer to show a picture than it needed to.
 *
 * The generator itself is a Windows executable and is not run here; what is pinned is the decision
 * to run it, which is the part that was wrong.
 */
class VlcPluginCacheTest {
    private fun <T> withVlcTree(block: (root: File) -> T): T {
        val root = Files.createTempDirectory("iptvburo-vlc")
        return try {
            val plugins = root.resolve("plugins").toFile()
            plugins.resolve("codec").mkdirs()
            plugins.resolve("codec/libavcodec_plugin.dll").writeText("not really a dll")
            block(root.toFile())
        } finally {
            @OptIn(kotlin.io.path.ExperimentalPathApi::class)
            root.deleteRecursively()
        }
    }

    /** No index at all: it has to be built. */
    @Test
    fun `a missing index is stale`() {
        withVlcTree { root ->
            assertTrue(
                VlcPluginCache.isStaleForTesting(root.resolve("plugins")),
                "with no plugins.dat there is nothing for VLC to use",
            )
        }
    }

    /** An index newer than every plugin is exactly the case where the scan must be skipped. */
    @Test
    fun `an index written after the plugins is fresh`() {
        withVlcTree { root ->
            val plugins = root.resolve("plugins")
            val index = plugins.resolve("plugins.dat")
            index.writeText("index")
            index.setLastModified(plugins.resolve("codec/libavcodec_plugin.dll").lastModified() + 10_000L)

            assertFalse(
                VlcPluginCache.isStaleForTesting(plugins),
                "a valid index must not be rebuilt on every launch",
            )
        }
    }

    /**
     * The packaging failure itself: the installer rewrites plugin timestamps, leaving them newer
     * than the index that was shipped beside them.
     */
    @Test
    fun `an index older than the plugins is stale`() {
        withVlcTree { root ->
            val plugins = root.resolve("plugins")
            val index = plugins.resolve("plugins.dat")
            index.writeText("index")
            val dll = plugins.resolve("codec/libavcodec_plugin.dll")
            index.setLastModified(1_000_000L)
            dll.setLastModified(9_000_000L)

            assertTrue(
                VlcPluginCache.isStaleForTesting(plugins),
                "this is the exact state the MSI produced, and it has to be detected",
            )
        }
    }

    /**
     * An index written in the same moment as the plugins counts as stale.
     *
     * This was the opposite way round, allowing a second of slack on the theory that files an
     * installer wrote together are not evidence of a change. A real install then produced exactly
     * that — plugins.dat and the DLLs equal to the second — the check said "fresh", and VLC
     * rejected the index with 363 `stale plugins cache` errors. VLC compares more finely than a
     * second, so only a strictly newer index may be trusted.
     */
    @Test
    fun `an index written in the same moment as the plugins is stale`() {
        withVlcTree { root ->
            val plugins = root.resolve("plugins")
            val index = plugins.resolve("plugins.dat")
            index.writeText("index")
            index.setLastModified(5_000_000L)
            plugins.resolve("codec/libavcodec_plugin.dll").setLastModified(5_000_000L)

            assertTrue(
                VlcPluginCache.isStaleForTesting(plugins),
                "an equal timestamp is what a real install produces, and VLC rejects that index",
            )
        }
    }

    /**
     * And regeneration must settle: the freshly written index has to read as fresh.
     *
     * Otherwise the generator would run on every single launch — a 1.1 s background task forever,
     * which is the cost this class exists to remove.
     */
    @Test
    fun `an index newer by a moment is fresh`() {
        withVlcTree { root ->
            val plugins = root.resolve("plugins")
            val index = plugins.resolve("plugins.dat")
            index.writeText("index")
            plugins.resolve("codec/libavcodec_plugin.dll").setLastModified(5_000_000L)
            index.setLastModified(5_000_001L)

            assertFalse(
                VlcPluginCache.isStaleForTesting(plugins),
                "a just-regenerated index must not immediately look stale again",
            )
        }
    }

    /** Nothing to do, and nothing to crash on, when the runtime is absent entirely. */
    @Test
    fun `an absent runtime is handled without throwing`() {
        withVlcTree { root ->
            assertFalse(
                VlcPluginCache.ensureFresh(root.resolve("nonexistent")),
                "a missing VLC directory is a no-op, not a failure",
            )
        }
    }

    /**
     * A directory with plugins but no generator cannot be rebuilt, and must not pretend otherwise.
     *
     * This is the shape a system-wide VLC install can take, where the app has no business writing
     * anything at all.
     */
    @Test
    fun `a runtime without the generator is left alone`() {
        withVlcTree { root ->
            assertFalse(
                VlcPluginCache.ensureFresh(root),
                "without vlc-cache-gen there is no way to build an index, so nothing is claimed",
            )
        }
    }
}

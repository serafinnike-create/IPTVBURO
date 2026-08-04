package com.lucasserafin94.iptvburo.desktop.download

import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteRecursively
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer

/**
 * The round trip that "Assistir" depends on after a restart.
 *
 * The library rebuilds itself from disk, and whatever key it reports has to be the key that finds
 * the file again. When those disagreed, a finished download sat in the list and playing it did
 * nothing at all.
 */
class OfflineLibraryTest {
    private fun <T> withManager(block: (DesktopDownloadManager, Path, MockWebServer) -> T): T {
        val directory = Files.createTempDirectory("iptvburo-offline")
        val server = MockWebServer()
        server.start()
        return try {
            block(DesktopDownloadManager(directory, OkHttpClient()), directory, server)
        } finally {
            server.shutdown()
            @OptIn(kotlin.io.path.ExperimentalPathApi::class)
            directory.deleteRecursively()
        }
    }

    private fun downloadOne(
        manager: DesktopDownloadManager,
        server: MockWebServer,
        key: String,
        title: String,
        container: String,
    ) {
        server.enqueue(MockResponse().setBody(Buffer().write(ByteArray(4_096) { 3 })))
        runBlocking {
            manager.download(key, title, URI(server.url("/a.$container").toString()), container) { _, _ -> }
        }
        manager.remember(key, title, artworkUrl = null)
    }

    /** The exact failure: an episode key round-tripped through disk must still find its file. */
    @Test
    fun `an episode survives a restart and is still playable by its key`() {
        withManager { manager, _, server ->
            val key = "series:the_show_2020|s1e3"
            downloadOne(manager, server, key, "The Show · T1E3", "mkv")

            // What a fresh session sees: only what is on disk.
            val rebuilt = manager.storedDownloads()

            assertEquals(setOf(key), rebuilt.keys, "the reported key must be the app's own key")
            assertNotNull(
                manager.downloadedFile(rebuilt.keys.single()),
                "the key the library reports must find the file",
            )
            assertEquals("The Show · T1E3", rebuilt.getValue(key).title)
        }
    }

    @Test
    fun `a film survives a restart and is still playable by its key`() {
        withManager { manager, _, server ->
            val key = "movie:supergirl_2026"
            downloadOne(manager, server, key, "Supergirl", "mp4")

            val rebuilt = manager.storedDownloads()

            assertEquals(setOf(key), rebuilt.keys)
            assertNotNull(manager.downloadedFile(key))
            assertEquals("Supergirl", rebuilt.getValue(key).title)
        }
    }

    @Test
    fun `films and episodes coexist in the library`() {
        withManager { manager, _, server ->
            downloadOne(manager, server, "movie:a_2020", "A", "mp4")
            downloadOne(manager, server, "series:b_2021|s2e5", "B · T2E5", "mkv")

            val rebuilt = manager.storedDownloads()

            assertEquals(setOf("movie:a_2020", "series:b_2021|s2e5"), rebuilt.keys)
            rebuilt.keys.forEach { key ->
                assertNotNull(manager.downloadedFile(key), "$key must be findable")
            }
        }
    }

    /** Deleting through the library must remove both the file and its record. */
    @Test
    fun `deleting by the reported key removes the entry`() {
        withManager { manager, _, server ->
            val key = "series:the_show_2020|s1e3"
            downloadOne(manager, server, key, "The Show · T1E3", "mkv")

            manager.delete(key)

            assertTrue(manager.storedDownloads().isEmpty(), "was ${manager.storedDownloads()}")
            assertTrue(manager.downloadedFile(key) == null)
        }
    }

    /**
     * Copies made before the sidecar recorded the original key still have to be playable; they just
     * fall back to the sanitised name.
     */
    @Test
    fun `a download with no sidecar is still listed and findable`() {
        withManager { manager, directory, _ ->
            Files.writeString(directory.resolve("movie_supergirl_2026.mp4"), "x")

            val rebuilt = manager.storedDownloads()
            val key = rebuilt.keys.single()

            assertEquals("movie_supergirl_2026", key)
            assertNotNull(manager.downloadedFile(key))
            assertEquals("Supergirl", rebuilt.getValue(key).title)
        }
    }
}

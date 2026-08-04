package com.lucasserafin94.iptvburo.desktop.download

import com.lucasserafin94.iptvburo.desktop.model.XtreamPlaybackTarget
import com.lucasserafin94.iptvburo.xtream.XtreamContentType
import com.lucasserafin94.iptvburo.xtream.XtreamEpisode
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteRecursively
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer

class DesktopDownloadManagerTest {
    private fun <T> withManager(block: (DesktopDownloadManager, Path, MockWebServer) -> T): T {
        val directory = Files.createTempDirectory("iptvburo-downloads")
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

    private fun episode(
        id: String = "9001",
        container: String? = "mkv",
    ) = XtreamEpisode(
        providerId = id,
        title = "S01E03",
        seasonNumber = 1,
        episodeNumber = 3,
        containerExtension = container,
        artworkUrl = null,
    )

    private fun body(bytes: Int): MockResponse =
        MockResponse().setBody(Buffer().write(ByteArray(bytes) { 7 }))

    /** An episode is downloadable; only live has no end and must be refused. */
    @Test
    fun `an episode may be downloaded`() {
        withManager { manager, _, _ ->
            val target =
                XtreamPlaybackTarget.Episode(
                    seriesId = "5",
                    episode = episode(),
                    contentKey = "series:x|s1e3",
                )

            assertTrue(manager.isDownloadable(target))
        }
    }

    @Test
    fun `live is refused because it never ends`() {
        withManager { manager, _, _ ->
            val target =
                XtreamPlaybackTarget.CatalogItem(
                    providerId = "1",
                    contentType = XtreamContentType.LIVE,
                    containerExtension = "ts",
                    contentKey = "live:1",
                )

            assertTrue(!manager.isDownloadable(target))
        }
    }

    /**
     * The episode key contains characters the filesystem rejects, so the stored name is sanitised.
     * Finding the file again has to use the same sanitising, or the download completes and the
     * library never sees it.
     */
    @Test
    fun `an episode key with separators is found again after download`() {
        withManager { manager, _, server ->
            server.enqueue(body(2_048))
            val key = "series:the_show_2020|s1e3"

            val result =
                runBlocking {
                    manager.download(
                        contentKey = key,
                        displayName = "The Show · T1E3",
                        uri = URI(server.url("/episode.mkv").toString()),
                        containerExtension = "mkv",
                    ) { _, _ -> }
                }

            assertTrue(result is DownloadResult.Completed, "was $result")
            assertTrue(manager.isDownloaded(key), "the stored copy must be findable by its key")
            assertNotNull(manager.downloadedFile(key))
        }
    }

    /** An mkv must not be stored as mp4, or playback later refuses to open it. */
    @Test
    fun `the provider container extension is kept`() {
        withManager { manager, _, server ->
            server.enqueue(body(512))

            runBlocking {
                manager.download(
                    contentKey = "series:x|s1e3",
                    displayName = "x",
                    uri = URI(server.url("/e.mkv").toString()),
                    containerExtension = "mkv",
                ) { _, _ -> }
            }

            val stored = assertNotNull(manager.downloadedFile("series:x|s1e3"))
            assertTrue(stored.fileName.toString().endsWith(".mkv"), "was ${stored.fileName}")
        }
    }

    @Test
    fun `progress is reported as bytes arrive`() {
        withManager { manager, _, server ->
            server.enqueue(body(300_000))
            var lastRead = 0L

            runBlocking {
                manager.download(
                    contentKey = "movie:x",
                    displayName = "x",
                    uri = URI(server.url("/m.mp4").toString()),
                    containerExtension = "mp4",
                ) { read, _ -> lastRead = read }
            }

            assertEquals(300_000L, lastRead)
        }
    }

    /** A rejected request must not leave a partial file that later looks like a complete download. */
    @Test
    fun `a rejected download leaves nothing behind`() {
        withManager { manager, directory, server ->
            server.enqueue(MockResponse().setResponseCode(403))

            val result =
                runBlocking {
                    manager.download(
                        contentKey = "series:x|s1e9",
                        displayName = "x",
                        uri = URI(server.url("/e.mkv").toString()),
                        containerExtension = "mkv",
                    ) { _, _ -> }
                }

            assertTrue(result is DownloadResult.Failed, "was $result")
            assertNull(manager.downloadedFile("series:x|s1e9"))
            assertEquals(0, Files.list(directory).use { it.count() }.toInt())
        }
    }

    /** The library rebuilds from disk after a restart, so a stored episode has to appear there. */
    @Test
    fun `a stored episode is listed with its remembered title`() {
        withManager { manager, _, server ->
            server.enqueue(body(1_024))
            val key = "series:the_show_2020|s1e3"
            runBlocking {
                manager.download(key, "The Show · T1E3", URI(server.url("/e.mkv").toString()), "mkv") { _, _ -> }
            }
            manager.remember(key, "The Show · T1E3", artworkUrl = null)

            val stored = manager.storedDownloads()

            assertEquals(1, stored.size, "was $stored")
            assertEquals("The Show · T1E3", stored.values.single().title)
        }
    }
}

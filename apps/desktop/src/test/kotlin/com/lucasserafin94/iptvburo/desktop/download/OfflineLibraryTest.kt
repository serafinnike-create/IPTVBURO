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
     * The exact failure a user hit: a complete 983 MB episode on disk with an empty index.
     *
     * The download ran in the details page's own coroutine scope, so navigating away cancelled it
     * after the file had been moved into place but before the sidecar was written. The library kept
     * showing "downloading" for something already finished, and Assistir did nothing.
     */
    @Test
    fun `a finished file with no sidecar is playable`() {
        withManager { manager, directory, _ ->
            Files.writeString(directory.resolve("series_my-show_2026_s1e1.mp4"), "x")
            Files.writeString(directory.resolve("library.json"), "{}")

            val rebuilt = manager.storedDownloads()
            val key = rebuilt.keys.single()

            assertEquals(1, rebuilt.size, "the copy on disk is what proves it finished")
            assertNotNull(manager.downloadedFile(key), "it must be playable")
        }
    }

    /**
     * An interrupted download continues from where it stopped.
     *
     * A 600 MB episode over a domestic line takes long enough that an interruption is ordinary
     * rather than exceptional. Starting from zero every time made a large download on an unreliable
     * connection effectively impossible to finish.
     */
    @Test
    fun `a download resumes from the bytes already on disk`() {
        withManager { manager, directory, server ->
            val key = "movie:big_2026"
            // What a previous attempt left behind: the first 1,000 bytes of a 3,000-byte file.
            val part = directory.resolve("movie_big_2026.mp4.part")
            Files.write(part, ByteArray(1_000) { 1 })

            // The server answers the range with the remainder only.
            server.enqueue(
                MockResponse()
                    .setResponseCode(206)
                    .setHeader("Content-Range", "bytes 1000-2999/3000")
                    .setBody(Buffer().write(ByteArray(2_000) { 2 })),
            )

            val result =
                runBlocking {
                    manager.download(key, "Big", URI(server.url("/big.mp4").toString()), "mp4") { _, _ -> }
                }

            assertTrue(result is DownloadResult.Completed, "was $result")
            assertEquals(3_000L, result.bytes, "the resumed total counts what was already there")
            assertEquals(3_000L, Files.size(assertNotNull(manager.downloadedFile(key))))
        }
    }

    /** The request has to ask for the remainder, or the server has no way to know. */
    @Test
    fun `a resumed download sends a Range header`() {
        withManager { manager, directory, server ->
            Files.write(directory.resolve("movie_ranged_2026.mp4.part"), ByteArray(512) { 1 })
            server.enqueue(
                MockResponse()
                    .setResponseCode(206)
                    .setBody(Buffer().write(ByteArray(512) { 2 })),
            )

            runBlocking {
                manager.download(
                    "movie:ranged_2026",
                    "Ranged",
                    URI(server.url("/r.mp4").toString()),
                    "mp4",
                ) { _, _ -> }
            }

            assertEquals("bytes=512-", server.takeRequest().getHeader("Range"))
        }
    }

    /**
     * A server that ignores the range must not produce a corrupt file.
     *
     * Answering 200 means the whole file is coming again. Appending it to the chunk would
     * concatenate two copies — a file that exists, plays for a while and then breaks, which is far
     * worse than one that failed outright.
     */
    @Test
    fun `a server that ignores the range restarts cleanly`() {
        withManager { manager, directory, server ->
            val key = "movie:norange_2026"
            Files.write(directory.resolve("movie_norange_2026.mp4.part"), ByteArray(1_000) { 1 })
            // 200, not 206: the whole file, range ignored.
            server.enqueue(MockResponse().setBody(Buffer().write(ByteArray(2_000) { 2 })))

            val result =
                runBlocking {
                    manager.download(key, "No range", URI(server.url("/n.mp4").toString()), "mp4") { _, _ -> }
                }

            assertTrue(result is DownloadResult.Completed, "was $result")
            assertEquals(2_000L, result.bytes, "the whole file, not the chunk plus the whole file")
            assertEquals(2_000L, Files.size(assertNotNull(manager.downloadedFile(key))))
        }
    }

    /**
     * A chunk still being written must survive a restart.
     *
     * The sweep used to delete every `.part` unconditionally, which destroyed a transfer the user
     * was waiting for: 106 MB in flight, the app restarted, and the download's own file swept out
     * from under it. There is no error for this — the bar simply stops and the title vanishes.
     */
    @Test
    fun `a recently written chunk is not swept`() {
        withManager { manager, directory, _ ->
            val part = directory.resolve("series_in-flight_2026_s1e1.mp4.part")
            Files.writeString(part, "half a download")

            val swept = manager.discardInterruptedDownloads()

            assertEquals(0, swept, "a chunk written moments ago belongs to a live transfer")
            assertTrue(Files.exists(part), "the download's own file must still be there")
        }
    }

    /** A chunk nothing has touched for a long time is genuinely abandoned, and does go. */
    @Test
    fun `a stale chunk is swept`() {
        withManager { manager, directory, _ ->
            val part = directory.resolve("series_abandoned_2020_s1e1.mp4.part")
            Files.writeString(part, "orphaned")
            // An hour ago: well past the point where an active transfer would have written again.
            part.toFile().setLastModified(System.currentTimeMillis() - 60 * 60 * 1000L)

            val swept = manager.discardInterruptedDownloads()

            assertEquals(1, swept)
            assertTrue(Files.notExists(part))
        }
    }

    /** The sweep never touches a finished copy, whatever its age. */
    @Test
    fun `a completed download is never swept`() {
        withManager { manager, directory, _ ->
            val finished = directory.resolve("movie_old_1999.mp4")
            Files.writeString(finished, "a complete film")
            finished.toFile().setLastModified(System.currentTimeMillis() - 365L * 24 * 60 * 60 * 1000L)

            manager.discardInterruptedDownloads()

            assertTrue(Files.exists(finished), "a finished download is not a chunk")
        }
    }

    /**
     * A download in progress must not make the finished ones disappear from the index.
     *
     * `remember` rewrites the whole index from `readIndex()`, so anything that empties or replaces
     * that file loses every completed download's title and poster at once — which is what a user
     * saw: a finished episode listed by its raw file name, with no artwork, while a second download
     * was running.
     */
    @Test
    fun `a second download preserves the first one's record`() {
        withManager { manager, _, server ->
            downloadOne(manager, server, "movie:first_2020", "The First", "mp4")
            downloadOne(manager, server, "movie:second_2021", "The Second", "mp4")

            val stored = manager.storedDownloads()

            assertEquals(2, stored.size, "was $stored")
            assertEquals("The First", stored.getValue("movie:first_2020").title)
            assertEquals("The Second", stored.getValue("movie:second_2021").title)
        }
    }

    /**
     * Deleting one download leaves the others' records alone.
     *
     * The index is rewritten wholesale on every delete, so a mistake here does not lose one row —
     * it loses all of them, and every remaining download falls back to its sanitised file name.
     */
    @Test
    fun `deleting one download keeps the others intact`() {
        withManager { manager, _, server ->
            downloadOne(manager, server, "movie:keep_2020", "Keep This", "mp4")
            downloadOne(manager, server, "movie:remove_2021", "Remove This", "mp4")

            manager.delete("movie:remove_2021")

            val stored = manager.storedDownloads()
            assertEquals(1, stored.size, "was $stored")
            assertEquals("Keep This", stored.getValue("movie:keep_2020").title, "its title must survive")
        }
    }

    /**
     * The poster survives the round trip, which is what a missing cover comes down to.
     *
     * A title read back from disk with a null artwork URL draws its placeholder letter, and that is
     * indistinguishable on screen from an image that failed to load — so the two have to be told
     * apart here rather than by looking at the app.
     */
    @Test
    fun `a remembered poster is read back`() {
        withManager { manager, _, server ->
            server.enqueue(MockResponse().setBody(Buffer().write(ByteArray(1_024) { 5 })))
            val key = "series:the_show_2026|s1e1"
            runBlocking {
                manager.download(key, "The Show · T1E1", URI(server.url("/e.mp4").toString()), "mp4") { _, _ -> }
            }
            manager.remember(key, "The Show · T1E1", artworkUrl = "https://images.invalid/poster.jpg")

            val stored = manager.storedDownloads().getValue(key)

            assertEquals("https://images.invalid/poster.jpg", stored.artworkUrl)
            assertEquals("The Show · T1E1", stored.title)
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

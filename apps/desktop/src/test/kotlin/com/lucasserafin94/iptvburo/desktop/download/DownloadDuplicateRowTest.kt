package com.lucasserafin94.iptvburo.desktop.download

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * One finished film, one row in the library.
 *
 * Reported with a screenshot: after a download completed the same film appeared twice — once with
 * its poster and once without. Two rows means two keys for one file, because the list is keyed by
 * content key, and the row without a poster is the one that came back from disk under a key nothing
 * else in the app uses.
 */
class DownloadDuplicateRowTest {
    private val root: Path = Files.createTempDirectory("iptvburo-downloads")

    @AfterTest
    fun cleanUp() {
        Files.walk(root).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
    }

    private fun manager() = DesktopDownloadManager(rootDirectory = root)

    /** The exact layout found on the reporter's machine. */
    private fun storeCompletedFilm() {
        root.resolve("movie_dupla-perigosa_2026.mp4").writeText("conteudo")
        root.resolve("library.json").writeText(
            """{"movie_dupla-perigosa_2026":{"title":"Dupla Perigosa",""" +
                """"artworkUrl":"http://exemplo.invalid/poster.jpg",""" +
                """"contentKey":"movie:dupla-perigosa:2026"}}""",
        )
    }

    @Test
    fun `a stored film comes back under the key the rest of the app uses`() {
        // The file name is a sanitised form of the key — ':' becomes '_' — so recovering the
        // sanitised name instead would give the library a key that matches nothing it already
        // holds, and the running entry and the stored one would both be listed.
        storeCompletedFilm()
        val stored = manager().storedDownloads()
        assertEquals(setOf("movie:dupla-perigosa:2026"), stored.keys)
        assertEquals("Dupla Perigosa", stored.values.single().title)
        assertTrue(stored.values.single().artworkUrl != null, "the poster comes from the sidecar")
    }

    @Test
    fun `the stored copy is found by the real key`() {
        storeCompletedFilm()
        assertTrue(manager().isDownloaded("movie:dupla-perigosa:2026"))
    }

    @Test
    fun `a file whose sidecar was lost still appears exactly once`() {
        // The copy is what proves the download finished, so a missing sidecar must not hide it.
        // It falls back to the sanitised name — which is the best available — but it must still be
        // one row, not a second one beside the real key.
        root.resolve("movie_sem_sidecar.mp4").writeText("conteudo")
        val stored = manager().storedDownloads()
        assertEquals(1, stored.size)
    }

    @Test
    fun `a part file is not a stored download`() {
        root.resolve("movie_em-curso.mp4.part").writeText("meio")
        assertTrue(manager().storedDownloads().isEmpty())
    }

    @Test
    fun `the index itself is never listed as a download`() {
        storeCompletedFilm()
        assertTrue(manager().storedDownloads().keys.none { it.contains("library") })
    }
}

/**
 * The window between the file landing and its sidecar being written.
 *
 * `Files.move` puts the copy in place inside `download`, and the sidecar is written afterwards by
 * the caller. Anything that reads the folder in between — the library screen refreshing, another
 * download finishing — sees a file with no sidecar and recovers it under the sanitised name. That
 * key matches nothing else in the app, so the film is listed a second time, without its poster.
 */
class DownloadSidecarRaceTest {
    private val root: java.nio.file.Path = Files.createTempDirectory("iptvburo-race")

    @AfterTest
    fun cleanUp() {
        Files.walk(root).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
    }

    @Test
    fun `a file read before its sidecar exists yields the sanitised key`() {
        // Reproduces the reported screenshot: one row with a poster, one without.
        root.resolve("movie_dupla-perigosa_2026.mp4").writeText("conteudo")
        val beforeSidecar = DesktopDownloadManager(rootDirectory = root).storedDownloads()
        assertEquals(
            setOf("movie_dupla-perigosa_2026"),
            beforeSidecar.keys,
            "with no sidecar the only name available is the sanitised one",
        )

        // The caller then writes the sidecar, and the same file comes back under the real key.
        DesktopDownloadManager(rootDirectory = root).remember(
            contentKey = "movie:dupla-perigosa:2026",
            title = "Dupla Perigosa",
            artworkUrl = "http://exemplo.invalid/poster.jpg",
        )
        val afterSidecar = DesktopDownloadManager(rootDirectory = root).storedDownloads()
        assertEquals(setOf("movie:dupla-perigosa:2026"), afterSidecar.keys)

        // Both keys name the same film, and a library that saw the first still holds it: that is
        // the duplicate row. The fix has to make the second reading replace the first, not add to
        // it.
        assertTrue(
            beforeSidecar.keys.first() != afterSidecar.keys.first(),
            "the two readings disagree about the key, which is what produces two rows",
        )
    }
}

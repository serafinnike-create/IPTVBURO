package com.lucasserafin94.iptvburo.desktop.data

import com.lucasserafin94.iptvburo.domain.model.MusicLibrary
import com.lucasserafin94.iptvburo.domain.model.MusicTrack
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MusicLibraryLoaderTest {
    /** Hosts are deliberately example.invalid: this repository is public. */
    private val playlist =
        """
        #EXTM3U
        #EXTINF:214 tvg-logo="https://cdn.example.invalid/a.png" group-title="Rock",Pink Floyd - Time
        https://media.example.invalid/1.mp3
        #EXTINF:187 group-title="Rock",Pink Floyd - Money
        https://media.example.invalid/2.mp3
        #EXTINF:203 group-title="MPB",Djavan - Oceano
        https://media.example.invalid/3.mp3
        #EXTINF:-1 group-title="Rádio",Rádio Cidade FM
        https://stream.example.invalid/live
        """.trimIndent()

    private fun writePlaylist(content: String = playlist): Path {
        val directory = createTempDirectory("music-loader")
        val file = directory.resolve("music.m3u")
        Files.writeString(file, content)
        return file
    }

    @Test
    fun `a music playlist loads into tracks, artists and stations`() {
        val library = MusicLibraryLoader().load(writePlaylist())

        assertNotNull(library)
        assertEquals(4, library.tracks.size)
        assertEquals(3, library.songs.size)
        assertEquals(1, library.radioStations.size)
        // Two Pink Floyd tracks group into one artist, ahead of the single Djavan track.
        assertEquals("Pink Floyd", library.artists.first().name)
        assertEquals(2, library.artists.first().trackCount)
    }

    /** Ids are namespaced so a track can never collide with a video content key. */
    @Test
    fun `track ids are namespaced to music`() {
        val library = assertNotNull(MusicLibraryLoader().load(writePlaylist()))
        assertTrue(library.tracks.all { track -> track.id.startsWith("music:") })
    }

    /**
     * The file is the user's own and may have been moved since they chose it. That must leave the
     * app with no music rather than with an exception on the startup path.
     */
    @Test
    fun `a missing file loads as null rather than throwing`() {
        val directory = createTempDirectory("music-loader-missing")
        assertNull(MusicLibraryLoader().load(directory.resolve("absent.m3u")))
    }

    @Test
    fun `a directory in place of a playlist loads as null`() {
        assertNull(MusicLibraryLoader().load(createTempDirectory("music-loader-dir")))
    }

    @Test
    fun `a file that is not a playlist yields an empty library rather than failing`() {
        val library = MusicLibraryLoader().load(writePlaylist("not a playlist at all"))
        // Either outcome is acceptable to the caller; what must not happen is a throw.
        assertTrue(library == null || library.isEmpty)
    }
}

class MusicHomeShelvesTest {
    private fun track(
        id: String,
        title: String,
        isRadio: Boolean = false,
    ) = MusicTrack(
        id = id,
        title = title,
        artist = "Artist",
        streamUri = "https://media.example.invalid/$id",
        isRadio = isRadio,
    )

    @Test
    fun `new releases follow the playlist author's own order`() {
        val library =
            MusicLibrary(
                tracks = listOf(track("1", "First"), track("2", "Second"), track("3", "Third")),
            )

        val shelves = musicHomeShelves(library, playCounts = emptyMap())
        assertEquals(listOf("First", "Second", "Third"), shelves.newReleases.map(MusicTrack::title))
    }

    /** A ranking of plays that never happened would be a fabrication, so the shelf stays empty. */
    @Test
    fun `most played is empty until something has been played`() {
        val library = MusicLibrary(tracks = listOf(track("1", "First"), track("2", "Second")))
        assertTrue(musicHomeShelves(library, playCounts = emptyMap()).mostPlayed.isEmpty())
    }

    @Test
    fun `most played ranks by count, highest first`() {
        val library =
            MusicLibrary(
                tracks = listOf(track("1", "Once"), track("2", "Thrice"), track("3", "Twice")),
            )

        val shelves =
            musicHomeShelves(library, playCounts = mapOf("1" to 1, "2" to 3, "3" to 2))
        assertEquals(listOf("Thrice", "Twice", "Once"), shelves.mostPlayed.map(MusicTrack::title))
    }

    /** Stations belong on the radio shelf; they would otherwise dominate a "most played" row. */
    @Test
    fun `radio stations stay out of the home shelves`() {
        val library =
            MusicLibrary(
                tracks = listOf(track("1", "Song"), track("2", "A Station", isRadio = true)),
            )

        val shelves = musicHomeShelves(library, playCounts = mapOf("2" to 9))
        assertEquals(listOf("Song"), shelves.newReleases.map(MusicTrack::title))
        assertTrue(shelves.mostPlayed.isEmpty())
    }

    @Test
    fun `a count for a track no longer in the playlist is ignored`() {
        val library = MusicLibrary(tracks = listOf(track("1", "Still here")))
        val shelves = musicHomeShelves(library, playCounts = mapOf("removed" to 40, "1" to 1))
        assertEquals(listOf("Still here"), shelves.mostPlayed.map(MusicTrack::title))
    }

    @Test
    fun `shelves are capped so a huge playlist cannot flood the home`() {
        val many = (1..60).map { index -> track(index.toString(), "Track $index") }
        val shelves = musicHomeShelves(MusicLibrary(tracks = many), playCounts = emptyMap())
        assertEquals(MUSIC_SHELF_SIZE, shelves.newReleases.size)
    }
}

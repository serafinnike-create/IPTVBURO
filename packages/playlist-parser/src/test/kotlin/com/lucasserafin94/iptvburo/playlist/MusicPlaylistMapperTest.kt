package com.lucasserafin94.iptvburo.playlist

import com.lucasserafin94.iptvburo.domain.model.MusicArtist
import com.lucasserafin94.iptvburo.domain.model.MusicGenre
import com.lucasserafin94.iptvburo.domain.model.MusicTrack
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The music mapping is where a playlist stops being lines of text and becomes shelves, so the
 * cases that decide what lands on which shelf are what is pinned down here: the dash, the absent
 * dash, the dash inside a title, and everything that makes an entry a radio station.
 *
 * Every host in this file is `example.invalid`, which RFC 2606 reserves precisely so that a public
 * repository can carry a playlist fixture without carrying anyone's subscription.
 */
class MusicPlaylistMapperTest {
    // ---------------------------------------------------------------------------------------
    // Splitting "Artist - Title"
    // ---------------------------------------------------------------------------------------

    @Test
    fun `splits a display name on the first dash`() {
        val split = MusicPlaylistMapper.splitArtistAndTitle("Daft Punk - Around the World")

        assertEquals("Daft Punk", split.artist)
        assertEquals("Around the World", split.title)
    }

    @Test
    fun `treats a name with no dash as a title with an unknown artist`() {
        val split = MusicPlaylistMapper.splitArtistAndTitle("Bohemian Rhapsody")

        assertNull(split.artist)
        assertEquals("Bohemian Rhapsody", split.title)
    }

    @Test
    fun `keeps a dash inside the title with the title`() {
        // The first separator splits, never the last: otherwise this files under an artist called
        // "Pink Floyd - Wish You Were Here".
        val split = MusicPlaylistMapper.splitArtistAndTitle("Pink Floyd - Wish You Were Here - Live")

        assertEquals("Pink Floyd", split.artist)
        assertEquals("Wish You Were Here - Live", split.title)
    }

    @Test
    fun `does not split a hyphenated name that has no spaced dash`() {
        // "Jay-Z - Encore" must not become artist "Jay". The space around the separator is the
        // only thing distinguishing the two hyphens.
        val split = MusicPlaylistMapper.splitArtistAndTitle("Jay-Z - Encore")

        assertEquals("Jay-Z", split.artist)
        assertEquals("Encore", split.title)
    }

    @Test
    fun `does not split on a hyphen with no surrounding space`() {
        val split = MusicPlaylistMapper.splitArtistAndTitle("Blink-182")

        assertNull(split.artist)
        assertEquals("Blink-182", split.title)
    }

    @Test
    fun `splits on an en dash and an em dash`() {
        // Playlist authors paste from music services, which favour typographic dashes.
        val enDash = MusicPlaylistMapper.splitArtistAndTitle("Air – La Femme d'Argent")
        val emDash = MusicPlaylistMapper.splitArtistAndTitle("Portishead — Roads")

        assertEquals("Air", enDash.artist)
        assertEquals("La Femme d'Argent", enDash.title)
        assertEquals("Portishead", emDash.artist)
        assertEquals("Roads", emDash.title)
    }

    @Test
    fun `refuses a split that would leave a blank half`() {
        // An empty artist creates a shelf with no name, which is worse than not splitting.
        val leading = MusicPlaylistMapper.splitArtistAndTitle("- Untitled")
        val trailing = MusicPlaylistMapper.splitArtistAndTitle("Untitled -")

        assertNull(leading.artist)
        assertEquals("- Untitled", leading.title)
        assertNull(trailing.artist)
        assertEquals("Untitled -", trailing.title)
    }

    @Test
    fun `trims surrounding whitespace on both halves`() {
        val split = MusicPlaylistMapper.splitArtistAndTitle("   Massive Attack   -   Teardrop   ")

        assertEquals("Massive Attack", split.artist)
        assertEquals("Teardrop", split.title)
    }

    @Test
    fun `an empty display name yields an empty title`() {
        val split = MusicPlaylistMapper.splitArtistAndTitle("   ")

        assertNull(split.artist)
        assertEquals("", split.title)
    }

    // ---------------------------------------------------------------------------------------
    // Radio detection
    // ---------------------------------------------------------------------------------------

    @Test
    fun `detects a station from an accented group title`() {
        val channel = channel(name = "Cidade", groupTitle = "Rádio", durationSeconds = -1L)

        assertTrue(MusicPlaylistMapper.isRadioStation(channel))
    }

    @Test
    fun `detects a station from the name`() {
        val fromWord = channel(name = "Rádio Cidade", groupTitle = "Pop", durationSeconds = -1L)
        val fromBand = channel(name = "Antena 1 FM", groupTitle = "Pop", durationSeconds = -1L)

        assertTrue(MusicPlaylistMapper.isRadioStation(fromWord))
        assertTrue(MusicPlaylistMapper.isRadioStation(fromBand))
    }

    @Test
    fun `detects a station from missing track structure`() {
        // No duration, no group, no "Artist - Title" leaves nothing that looks like a track.
        val channel = channel(name = "Some Stream", groupTitle = null, durationSeconds = -1L)

        assertTrue(MusicPlaylistMapper.isRadioStation(channel))
    }

    @Test
    fun `does not mistake a band inside a word for a station`() {
        // "Confirm" ends in "fm" and "Amsterdam" contains "am"; a substring match classified both
        // as stations, which is why the keyword match is word-bounded.
        val confirm = channel(name = "The Killers - Confirm", groupTitle = "Rock", durationSeconds = 210L)
        val amsterdam = channel(name = "Nothing But Thieves - Amsterdam", groupTitle = "Rock", durationSeconds = 189L)

        assertFalse(MusicPlaylistMapper.isRadioStation(confirm))
        assertFalse(MusicPlaylistMapper.isRadioStation(amsterdam))
    }

    @Test
    fun `a positive duration settles the question`() {
        // A finite recording is proof of a track even when everything else is missing.
        val channel = channel(name = "Untitled", groupTitle = null, durationSeconds = 240L)

        assertFalse(MusicPlaylistMapper.isRadioStation(channel))
    }

    @Test
    fun `a station keeps its whole name as the title`() {
        // "Rádio Cidade - 102.9 FM" must not produce an artist called "Rádio Cidade".
        val track = MusicPlaylistMapper.toTrack(
            channel(name = "Rádio Cidade - 102.9 FM", groupTitle = "Rádio", durationSeconds = -1L),
            index = 0,
        )

        assertTrue(track.isRadio)
        assertEquals("Rádio Cidade - 102.9 FM", track.title)
        assertNull(track.artist)
    }

    @Test
    fun `a station carries no duration`() {
        // The conventional -1 would otherwise surface as a "-1 second" track.
        val track = MusicPlaylistMapper.toTrack(
            channel(name = "Rádio Cidade", groupTitle = "Rádio", durationSeconds = -1L),
            index = 0,
        )

        assertNull(track.durationSeconds)
    }

    // ---------------------------------------------------------------------------------------
    // Grouping
    // ---------------------------------------------------------------------------------------

    @Test
    fun `orders artists by track count then by name`() {
        val tracks = listOf(
            track(title = "A", artist = "Zeta"),
            track(title = "B", artist = "Alpha"),
            track(title = "C", artist = "Alpha"),
            track(title = "D", artist = "Beta"),
        )

        val artists = MusicPlaylistMapper.artistsFrom(tracks)

        assertEquals(listOf("Alpha", "Beta", "Zeta"), artists.map(MusicArtist::name))
        assertEquals(listOf(2, 1, 1), artists.map(MusicArtist::trackCount))
    }

    @Test
    fun `folds artist spellings that differ only in case`() {
        // A playlist that mixes "Daft Punk" and "daft punk" would otherwise show two half shelves.
        val tracks = listOf(
            track(title = "One More Time", artist = "Daft Punk"),
            track(title = "Digital Love", artist = "daft punk"),
        )

        val artists = MusicPlaylistMapper.artistsFrom(tracks)

        assertEquals(1, artists.size)
        // The first spelling wins: it is the one the playlist author led with.
        assertEquals("Daft Punk", artists.single().name)
        assertEquals(2, artists.single().trackCount)
    }

    @Test
    fun `collects artistless tracks under a single unknown shelf`() {
        val tracks = listOf(track(title = "One", artist = null), track(title = "Two", artist = null))

        val artists = MusicPlaylistMapper.artistsFrom(tracks)

        assertEquals(MusicTrack.UNKNOWN_ARTIST, artists.single().name)
        assertEquals(2, artists.single().trackCount)
    }

    @Test
    fun `excludes radio stations from artist shelves`() {
        // Stations have no artist; including them piles every station into one "Unknown" shelf.
        val tracks = listOf(
            track(title = "Teardrop", artist = "Massive Attack"),
            track(title = "Rádio Cidade", artist = null, isRadio = true),
        )

        val artists = MusicPlaylistMapper.artistsFrom(tracks)

        assertEquals(listOf("Massive Attack"), artists.map(MusicArtist::name))
    }

    @Test
    fun `borrows artwork from the first track that has any`() {
        val tracks = listOf(
            track(title = "One", artist = "Alpha", artworkUri = null),
            track(title = "Two", artist = "Alpha", artworkUri = "https://example.invalid/art.png"),
        )

        assertEquals("https://example.invalid/art.png", MusicPlaylistMapper.artistsFrom(tracks).single().artworkUri)
    }

    @Test
    fun `orders genres by track count then by name and folds case`() {
        val tracks = listOf(
            track(title = "A", artist = "X", genre = "Rock"),
            track(title = "B", artist = "Y", genre = "rock"),
            track(title = "C", artist = "Z", genre = "MPB"),
            track(title = "D", artist = "W", genre = null),
        )

        val genres = MusicPlaylistMapper.genresFrom(tracks)

        assertEquals(listOf("Rock", "MPB"), genres.map(MusicGenre::name))
        assertEquals(listOf(2, 1), genres.map(MusicGenre::trackCount))
    }

    @Test
    fun `an ungrouped track contributes no genre`() {
        // An "Unknown" genre row would be a row of unrelated things.
        val genres = MusicPlaylistMapper.genresFrom(listOf(track(title = "A", artist = "X", genre = null)))

        assertTrue(genres.isEmpty())
    }

    // ---------------------------------------------------------------------------------------
    // End to end, through the real parser
    // ---------------------------------------------------------------------------------------

    @Test
    fun `parses a playlist mixing stations and tracks`() {
        val playlist =
            """
            #EXTM3U
            #EXTINF:-1 group-title="Rádio" tvg-logo="https://example.invalid/art/cidade.png",Rádio Cidade
            https://example.invalid/radio/cidade.mp3
            #EXTINF:217 group-title="Rock" tvg-logo="https://example.invalid/art/floyd.png",Pink Floyd - Wish You Were Here - Live
            https://example.invalid/tracks/wywh.mp3
            #EXTINF:196 group-title="Rock",Jay-Z - Encore
            https://example.invalid/tracks/encore.mp3
            #EXTINF:184,Bohemian Rhapsody
            https://example.invalid/tracks/bohemian.mp3
            """.trimIndent()

        val library = parseMusic(playlist).library

        assertEquals(4, library.tracks.size)
        assertEquals(1, library.radioStations.size)
        assertEquals(3, library.songs.size)

        val station = library.radioStations.single()
        assertEquals("Rádio Cidade", station.title)
        assertNull(station.artist)
        assertNull(station.durationSeconds)
        assertEquals("Rádio", station.genre)
        assertEquals("https://example.invalid/art/cidade.png", station.artworkUri)

        val floyd = library.tracks[1]
        assertEquals("Pink Floyd", floyd.artist)
        assertEquals("Wish You Were Here - Live", floyd.title)
        assertEquals(217L, floyd.durationSeconds)

        // "Bohemian Rhapsody" has a duration, so it stays a track despite having no artist.
        val bohemian = library.tracks[3]
        assertFalse(bohemian.isRadio)
        assertNull(bohemian.artist)
        assertEquals(MusicTrack.UNKNOWN_ARTIST, bohemian.artistOrUnknown)
        assertNull(bohemian.genre)

        // "Rádio" is a group-title like any other, so it earns a genre row alongside "Rock".
        // Ordering is by track count, so the two Rock tracks lead.
        assertEquals(listOf("Rock", "Rádio"), library.genres.map(MusicGenre::name))
        assertEquals(listOf(2, 1), library.genres.map(MusicGenre::trackCount))
    }

    @Test
    fun `an entry with no group still becomes a track`() {
        val playlist =
            """
            #EXTM3U
            #EXTINF:203,Radiohead - Creep
            https://example.invalid/tracks/creep.mp3
            """.trimIndent()

        val track = parseMusic(playlist).library.tracks.single()

        assertEquals("Radiohead", track.artist)
        assertEquals("Creep", track.title)
        assertNull(track.genre)
        assertNull(track.artworkUri)
        assertFalse(track.isRadio)
    }

    @Test
    fun `an empty playlist yields an empty library`() {
        val result = parseMusic("#EXTM3U\n")

        assertTrue(result.library.isEmpty)
        assertTrue(result.library.artists.isEmpty())
        assertTrue(result.library.genres.isEmpty())
        // The empty case is a warning, not an exception: the import should report, not crash.
        assertTrue(result.warnings.any { it.code == M3uWarningCode.EMPTY_PLAYLIST })
    }

    @Test
    fun `a malformed line is skipped and the rest of the playlist survives`() {
        val playlist =
            """
            #EXTM3U
            #EXTINF:this-is-not-a-duration group-title="Rock"
            #EXTINF:203 group-title="Rock",Radiohead - Creep
            https://example.invalid/tracks/creep.mp3
            """.trimIndent()

        val result = parseMusic(playlist)

        // The broken EXTINF is reported but does not take the good entry down with it.
        assertEquals(1, result.library.tracks.size)
        assertEquals("Creep", result.library.tracks.single().title)
        assertTrue(result.warnings.any { it.code == M3uWarningCode.MALFORMED_EXTINF })
    }

    @Test
    fun `an unsafe stream scheme is skipped rather than imported`() {
        val playlist =
            """
            #EXTM3U
            #EXTINF:203 group-title="Rock",Radiohead - Creep
            file:///etc/passwd
            #EXTINF:196 group-title="Rock",Jay-Z - Encore
            https://example.invalid/tracks/encore.mp3
            """.trimIndent()

        val result = parseMusic(playlist)

        assertEquals(listOf("Encore"), result.library.tracks.map(MusicTrack::title))
        assertTrue(result.warnings.any { it.code == M3uWarningCode.UNSAFE_STREAM_URI_SCHEME })
    }

    @Test
    fun `ids are unique and prefixed by the source`() {
        // A music playlist repeats titles across albums and often omits tvg-id, so the playlist
        // position is the only identifier guaranteed to be unique.
        val playlist =
            """
            #EXTM3U
            #EXTINF:203,Radiohead - Creep
            https://example.invalid/tracks/creep-1.mp3
            #EXTINF:203,Radiohead - Creep
            https://example.invalid/tracks/creep-2.mp3
            #EXTINF:196 tvg-id="encore",Jay-Z - Encore
            https://example.invalid/tracks/encore.mp3
            """.trimIndent()

        val ids = parseMusic(playlist, idPrefix = "music-src").library.tracks.map(MusicTrack::id)

        assertEquals(listOf("music-src:0", "music-src:1", "music-src:encore"), ids)
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `request headers survive into the music model`() {
        // The player needs the same headers the video path relies on, or the stream 403s.
        val playlist =
            """
            #EXTM3U
            #EXTINF:203 group-title="Rock",Radiohead - Creep
            #EXTVLCOPT:http-user-agent=IPTV-BURO-Test/0.1
            https://example.invalid/tracks/creep.mp3
            """.trimIndent()

        val track = parseMusic(playlist).library.tracks.single()

        assertEquals("IPTV-BURO-Test/0.1", track.requestHeaders["User-Agent"])
    }

    @Test
    fun `sensitive values stay out of toString`() {
        val track = track(
            title = "Creep",
            artist = "Radiohead",
            artworkUri = "https://example.invalid/art/creep.png",
        )

        val rendered = track.toString()

        assertFalse(rendered.contains("example.invalid"))
        assertTrue(rendered.contains("<redacted>"))
    }

    // ---------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------

    private fun parseMusic(
        playlist: String,
        idPrefix: String = "",
    ): MusicPlaylistResult =
        ByteArrayInputStream(playlist.toByteArray(StandardCharsets.UTF_8)).use { input ->
            MusicPlaylistParser().parse(input, idPrefix)
        }

    private fun channel(
        name: String,
        groupTitle: String?,
        durationSeconds: Long?,
    ) = ParsedChannel(
        name = name,
        streamUri = "https://example.invalid/stream.mp3",
        durationSeconds = durationSeconds,
        groupTitle = groupTitle,
    )

    private fun track(
        title: String,
        artist: String?,
        genre: String? = null,
        artworkUri: String? = null,
        isRadio: Boolean = false,
    ) = MusicTrack(
        id = "$artist:$title",
        title = title,
        artist = artist,
        streamUri = "https://example.invalid/stream.mp3",
        artworkUri = artworkUri,
        genre = genre,
        isRadio = isRadio,
    )
}

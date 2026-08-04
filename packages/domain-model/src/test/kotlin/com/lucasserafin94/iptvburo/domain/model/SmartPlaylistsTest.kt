package com.lucasserafin94.iptvburo.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The smart playlist rules from GDD 8 section 17.
 *
 * Written against the cases a naive implementation gets wrong: "never played" reading the presence
 * of a history row instead of its play count, and a decade filter built from `year / 10 == x / 10`
 * arithmetic that quietly moves the boundary.
 *
 * Every URI here is `example.invalid`, which RFC 2606 reserves so it can never resolve.
 */
class SmartPlaylistsTest {
    private fun track(
        id: String,
        title: String = "Track $id",
        artist: String? = "Artist",
        album: String? = null,
        genre: String? = null,
        isRadio: Boolean = false,
    ) = MusicTrack(
        id = id,
        title = title,
        artist = artist,
        streamUri = "https://example.invalid/$id.mp3",
        album = album,
        genre = genre,
        isRadio = isRadio,
    )

    private fun history(
        id: String,
        playCount: Int,
        lastPlayedAt: Long = 1_000L,
    ) = ListeningHistoryEntry(
        profileId = "p1",
        mediaIdentity = id,
        kind = ListeningKind.MUSIC,
        startedAtEpochMillis = 0L,
        lastPlayedAtEpochMillis = lastPlayedAt,
        playCount = playCount,
    )

    // -----------------------------------------------------------------------------------------
    // Never played
    // -----------------------------------------------------------------------------------------

    @Test
    fun `never played excludes anything with a play count`() {
        val tracks = listOf(track("a"), track("b"), track("c"))
        val result =
            SmartPlaylists.evaluate(
                rule = SmartPlaylistRule.NeverPlayed,
                tracks = tracks,
                history = mapOf("b" to history("b", playCount = 3)),
            )

        assertEquals(listOf("a", "c"), result.map(MusicTrack::id))
    }

    /**
     * The case that separates a correct implementation from one that tests for the row's absence.
     * A track started and abandoned below the threshold has a history row with a zero count, and
     * per GDD 8 section 18 it has still never been played.
     */
    @Test
    fun `never played includes a track whose history row never crossed the threshold`() {
        val tracks = listOf(track("a"), track("b"))
        val result =
            SmartPlaylists.evaluate(
                rule = SmartPlaylistRule.NeverPlayed,
                tracks = tracks,
                history = mapOf("a" to history("a", playCount = 0)),
            )

        assertEquals(listOf("a", "b"), result.map(MusicTrack::id))
    }

    @Test
    fun `never played and recently played never contain the same track`() {
        val tracks = listOf(track("a"), track("b"))
        val zeroCount = mapOf("a" to history("a", playCount = 0))

        val never = SmartPlaylists.evaluate(SmartPlaylistRule.NeverPlayed, tracks, zeroCount).map(MusicTrack::id)
        val recent = SmartPlaylists.evaluate(SmartPlaylistRule.RecentlyPlayed, tracks, zeroCount).map(MusicTrack::id)

        assertTrue(never.intersect(recent.toSet()).isEmpty(), "a track cannot be both never and recently played")
    }

    // -----------------------------------------------------------------------------------------
    // By decade
    // -----------------------------------------------------------------------------------------

    @Test
    fun `by decade groups 1999 and 1990 together but not 2000`() {
        val tracks =
            listOf(
                track("a", album = "Album (1990)"),
                track("b", album = "Album (1999)"),
                track("c", album = "Album (2000)"),
                track("d", album = "Album (1989)"),
            )

        val nineties =
            SmartPlaylists
                .evaluate(SmartPlaylistRule.ByDecade(1990), tracks)
                .map(MusicTrack::id)

        assertEquals(listOf("a", "b"), nineties)
    }

    @Test
    fun `a track with no discoverable year belongs to no decade`() {
        val tracks = listOf(track("a", title = "Untitled", album = null))

        assertNull(SmartPlaylists.releaseYearOf(tracks.single()))
        assertTrue(SmartPlaylists.evaluate(SmartPlaylistRule.ByDecade(1990), tracks).isEmpty())
        assertTrue(SmartPlaylists.evaluate(SmartPlaylistRule.ByDecade(2020), tracks).isEmpty())
    }

    /**
     * An unbracketed run of four digits is not a year. Reading one would file "Blink 182" and a
     * station frequency into decade shelves they have nothing to do with.
     */
    @Test
    fun `a bare four digit number is not read as a year`() {
        assertNull(SmartPlaylists.releaseYearOf(track("a", title = "Sector 1999", album = null)))
        assertEquals(1999, SmartPlaylists.releaseYearOf(track("b", title = "Sector [1999]", album = null)))
    }

    @Test
    fun `decades are listed newest first with their counts`() {
        val tracks =
            listOf(
                track("a", album = "X (1985)"),
                track("b", album = "Y (1988)"),
                track("c", album = "Z (2001)"),
                track("d", album = null),
            )

        val decades = SmartPlaylists.decadesIn(tracks)

        assertEquals(listOf(2000, 1980), decades.map(MusicDecade::startYear))
        assertEquals(listOf(1, 2), decades.map(MusicDecade::trackCount))
        assertEquals("1980s", decades.last().label)
    }

    // -----------------------------------------------------------------------------------------
    // Most played, recently played, recently added, genre
    // -----------------------------------------------------------------------------------------

    @Test
    fun `most played ranks by count and breaks ties by title`() {
        val tracks =
            listOf(
                track("a", title = "Zebra"),
                track("b", title = "Apple"),
                track("c", title = "Mango"),
            )
        val plays =
            mapOf(
                "a" to history("a", playCount = 2),
                "b" to history("b", playCount = 2),
                "c" to history("c", playCount = 9),
            )

        val ranked = SmartPlaylists.evaluate(SmartPlaylistRule.MostPlayed, tracks, plays).map(MusicTrack::id)

        assertEquals(listOf("c", "b", "a"), ranked)
    }

    @Test
    fun `recently played orders by last played descending`() {
        val tracks = listOf(track("a"), track("b"), track("c"))
        val plays =
            mapOf(
                "a" to history("a", playCount = 1, lastPlayedAt = 100L),
                "b" to history("b", playCount = 1, lastPlayedAt = 900L),
                "c" to history("c", playCount = 1, lastPlayedAt = 500L),
            )

        val recent = SmartPlaylists.evaluate(SmartPlaylistRule.RecentlyPlayed, tracks, plays).map(MusicTrack::id)

        assertEquals(listOf("b", "c", "a"), recent)
    }

    /** No date exists in an M3U, so "recently added" is the author's ordering read from the end. */
    @Test
    fun `recently added reverses the playlist order`() {
        val tracks = listOf(track("a"), track("b"), track("c"))

        val added = SmartPlaylists.evaluate(SmartPlaylistRule.RecentlyAdded, tracks).map(MusicTrack::id)

        assertEquals(listOf("c", "b", "a"), added)
    }

    @Test
    fun `by genre matches case insensitively`() {
        val tracks =
            listOf(
                track("a", genre = "Rock"),
                track("b", genre = "rock"),
                track("c", genre = "Jazz"),
            )

        val rock = SmartPlaylists.evaluate(SmartPlaylistRule.ByGenre("ROCK"), tracks).map(MusicTrack::id)

        assertEquals(listOf("a", "b"), rock)
    }

    @Test
    fun `genres are deduplicated across spellings`() {
        val tracks = listOf(track("a", genre = "Rock"), track("b", genre = "rock"), track("c", genre = "Jazz"))

        assertEquals(listOf("Jazz", "Rock"), SmartPlaylists.genresIn(tracks))
    }

    // -----------------------------------------------------------------------------------------
    // Radio and favourites
    // -----------------------------------------------------------------------------------------

    /**
     * A station has no play count, year or album, so it would sit permanently at the top of "never
     * played" without that ever telling the user anything.
     */
    @Test
    fun `radio stations are excluded from never played`() {
        val tracks = listOf(track("a"), track("station", isRadio = true))

        val never = SmartPlaylists.evaluate(SmartPlaylistRule.NeverPlayed, tracks).map(MusicTrack::id)

        assertEquals(listOf("a"), never)
    }

    /** Favourites is the one list where a favourited station belongs. */
    @Test
    fun `favourites keeps radio stations`() {
        val tracks = listOf(track("a"), track("station", isRadio = true))

        val favourites =
            SmartPlaylists
                .evaluate(SmartPlaylistRule.Favourites, tracks, favouriteIds = setOf("a", "station"))
                .map(MusicTrack::id)

        assertEquals(listOf("a", "station"), favourites)
    }

    @Test
    fun `the limit bounds the result`() {
        val tracks = (1..50).map { track("t$it") }

        assertEquals(5, SmartPlaylists.evaluate(SmartPlaylistRule.NeverPlayed, tracks, limit = 5).size)
    }
}

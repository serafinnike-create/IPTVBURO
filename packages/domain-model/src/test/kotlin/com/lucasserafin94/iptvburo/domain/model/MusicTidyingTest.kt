package com.lucasserafin94.iptvburo.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Cleaning up the names in a music playlist.
 *
 * The cases here are the ones real playlists actually contain, because the risk in this code is not
 * failing to clean something — it is cleaning something that was already correct. A song called
 * "1979" must not lose its name to a track-number rule, and "k.d. lang" must not be title-cased into
 * something its owner never wrote.
 */
class MusicTidyingTest {

    private fun track(
        title: String,
        artist: String? = null,
        isRadio: Boolean = false,
        id: String = "t1",
        uri: String = "https://example.invalid/a.mp3",
    ) = MusicTrack(id = id, title = title, artist = artist, streamUri = uri, isRadio = isRadio)

    // ---------------------------------------------------------------------------------------
    // Filenames
    // ---------------------------------------------------------------------------------------

    @Test
    fun `an audio extension is removed`() {
        assertEquals("Time", MusicTidying.tidyTitle("Time.mp3"))
        assertEquals("Time", MusicTidying.tidyTitle("Time.FLAC"))
    }

    @Test
    fun `a domain in a title is not mistaken for an extension`() {
        // Only audio and video extensions are stripped, and only at the very end.
        assertEquals("Welcome to example.com", MusicTidying.tidyTitle("Welcome to example.com"))
    }

    @Test
    fun `underscores become spaces`() {
        assertEquals("Pink Floyd - Time", MusicTidying.tidyTitle("Pink_Floyd_-_Time"))
    }

    @Test
    fun `a leading track number is removed`() {
        assertEquals("Time", MusicTidying.tidyTitle("01 - Time"))
        assertEquals("Time", MusicTidying.tidyTitle("01. Time"))
        assertEquals("Time", MusicTidying.tidyTitle("(01) Time"))
    }

    /**
     * A number that is the name keeps it.
     *
     * This is the rule that makes track-number stripping safe. "1979", "99 Luftballons" and "7 Rings"
     * are songs; a rule that ate them would be worse than leaving every "01 - " in place.
     */
    @Test
    fun `a song whose name is a number is left alone`() {
        assertEquals("1979", MusicTidying.tidyTitle("1979"))
        assertEquals("99 Luftballons", MusicTidying.tidyTitle("99 Luftballons"))
        assertEquals("7 Rings", MusicTidying.tidyTitle("7 Rings"))
    }

    @Test
    fun `bracketed noise is removed`() {
        assertEquals("Time", MusicTidying.tidyTitle("Time [320kbps]"))
        assertEquals("Time", MusicTidying.tidyTitle("Time (Official Video)"))
        assertEquals("Time", MusicTidying.tidyTitle("Time (Official Music Video)"))
        assertEquals("Time", MusicTidying.tidyTitle("[HD] Time"))
    }

    /**
     * Brackets that belong to the title survive.
     *
     * "(Live at Pompeii)" and "(feat. …)" are part of a song's name. Only a short list of things
     * that are never part of a name is removed.
     */
    @Test
    fun `meaningful brackets are kept`() {
        assertEquals(
            "Echoes (Live at Pompeii)",
            MusicTidying.tidyTitle("Echoes (Live at Pompeii)"),
        )
        assertEquals(
            "Stay (feat. Someone)",
            MusicTidying.tidyTitle("Stay (feat. Someone)"),
        )
    }

    @Test
    fun `trailing noise after a dash is removed`() {
        assertEquals("Time", MusicTidying.tidyTitle("Time - Official Video"))
        assertEquals("Time", MusicTidying.tidyTitle("Time | HD"))
    }

    @Test
    fun `capitalisation is never changed`() {
        // "REM" is not "Rem" and "k.d. lang" is not "K.D. Lang". A rule that fixes shouty filenames
        // breaks band names that are shouty deliberately, so there is no such rule.
        assertEquals("REM", MusicTidying.tidyTitle("REM"))
        assertEquals("k.d. lang", MusicTidying.tidyTitle("k.d. lang"))
        assertEquals("DEATH GRIPS", MusicTidying.tidyTitle("DEATH GRIPS"))
    }

    @Test
    fun `a name made entirely of decoration keeps its original`() {
        // Better a bad name than an empty row, which cannot be found or corrected.
        assertEquals("[320kbps].mp3", MusicTidying.tidyTitle("[320kbps].mp3"))
    }

    // ---------------------------------------------------------------------------------------
    // Artist recovery
    // ---------------------------------------------------------------------------------------

    @Test
    fun `an artist is recovered from an unsplit name`() {
        val result = MusicTidying.recoverArtist("Pink Floyd - Time", existingArtist = null)

        assertEquals("Pink Floyd", result.artist)
        assertEquals("Time", result.title)
    }

    @Test
    fun `only the first separator splits`() {
        val result = MusicTidying.recoverArtist("Pink Floyd - Wish You Were Here - Live", null)

        assertEquals("Pink Floyd", result.artist)
        assertEquals("Wish You Were Here - Live", result.title)
    }

    /**
     * A stated artist is never overwritten.
     *
     * A playlist that names the artist knows something the title does not. Scraping a different one
     * out of the title would trade real information for a guess.
     */
    @Test
    fun `an existing artist survives`() {
        val result = MusicTidying.recoverArtist("Some Other Name - Time", existingArtist = "Pink Floyd")

        assertEquals("Pink Floyd", result.artist)
    }

    @Test
    fun `a title repeating its own artist is shortened`() {
        // Extremely common: the playlist names the artist and the title repeats it.
        val result = MusicTidying.recoverArtist("Pink Floyd - Time", existingArtist = "Pink Floyd")

        assertEquals("Pink Floyd", result.artist)
        assertEquals("Time", result.title)
    }

    @Test
    fun `a title with no separator stays whole`() {
        val result = MusicTidying.recoverArtist("Time", existingArtist = null)

        assertNull(result.artist)
        assertEquals("Time", result.title)
    }

    @Test
    fun `an unspaced hyphen in a real name does not invent an artist`() {
        listOf("AC-DC", "Blink-182", "COVID-19").forEach { title ->
            val result = MusicTidying.recoverArtist(title, existingArtist = null)

            assertNull(result.artist, "artist for: $title")
            assertEquals(title, result.title)
        }
    }

    // ---------------------------------------------------------------------------------------
    // Proposals
    // ---------------------------------------------------------------------------------------

    @Test
    fun `a clean track proposes no change`() {
        // Null rather than an identical copy, so a caller can honestly say how many tracks a tidy
        // would touch.
        assertNull(MusicTidying.proposalFor(track(title = "Time", artist = "Pink Floyd")))
    }

    @Test
    fun `a filename track proposes both parts`() {
        val proposal = MusicTidying.proposalFor(track(title = "01 - Pink_Floyd_-_Time.mp3"))

        assertEquals("Pink Floyd", proposal?.artist)
        assertEquals("Time", proposal?.title)
    }

    /**
     * A radio station is left entirely alone.
     *
     * "Rádio Cidade - 102.9 FM" is one name. Splitting it files the station under an artist called
     * "Rádio Cidade", and stripping "102.9" as a track number would be worse still.
     */
    @Test
    fun `a radio station is never tidied`() {
        assertNull(
            MusicTidying.proposalFor(track(title = "Rádio Cidade - 102.9 FM", isRadio = true)),
        )
    }

    @Test
    fun `proposals cover only the tracks that would change`() {
        val tracks = listOf(
            track(id = "a", title = "Time", artist = "Pink Floyd"),
            track(id = "b", title = "02 - Money.mp3"),
            track(id = "c", title = "Us and Them", artist = "Pink Floyd"),
        )

        val proposals = MusicTidying.proposalsFor(tracks)

        assertEquals(1, proposals.size)
        assertEquals("b", proposals.single().trackId)
    }

    // ---------------------------------------------------------------------------------------
    // Duplicates
    // ---------------------------------------------------------------------------------------

    @Test
    fun `the same song under two filenames is one group`() {
        val tracks = listOf(
            track(id = "a", title = "01 - Pink Floyd - Time.mp3"),
            track(id = "b", title = "Pink_Floyd_-_Time"),
            track(id = "c", title = "Money", artist = "Pink Floyd"),
        )

        val groups = MusicTidying.duplicateGroups(tracks)

        assertEquals(1, groups.size)
        assertEquals(setOf("a", "b"), groups.single().map(MusicTrack::id).toSet())
    }

    /**
     * A live version is not a duplicate of the studio version.
     *
     * They are different recordings. A tool that quietly removed one is a tool nobody can trust with
     * the other.
     */
    @Test
    fun `different versions are not duplicates`() {
        val tracks = listOf(
            track(id = "a", title = "Time", artist = "Pink Floyd"),
            track(id = "b", title = "Time (Live at Pompeii)", artist = "Pink Floyd"),
        )

        assertTrue(MusicTidying.duplicateGroups(tracks).isEmpty())
    }

    @Test
    fun `two entries with one address are certainly the same`() {
        val tracks = listOf(
            track(id = "a", title = "Time", uri = "https://example.invalid/x.mp3"),
            track(id = "b", title = "Completely Different", uri = "https://example.invalid/x.mp3"),
            track(id = "c", title = "Money", uri = "https://example.invalid/y.mp3"),
        )

        val groups = MusicTidying.sameAddressGroups(tracks)

        assertEquals(1, groups.size)
        assertEquals(setOf("a", "b"), groups.single().map(MusicTrack::id).toSet())
    }

    @Test
    fun `missing addresses are not a duplicate signal`() {
        val tracks = listOf(
            track(id = "a", title = "One", uri = ""),
            track(id = "b", title = "Two", uri = "  "),
        )

        assertTrue(MusicTidying.sameAddressGroups(tracks).isEmpty())
    }

    @Test
    fun `radio stations are excluded from name duplicates`() {
        // Two stations may legitimately share a name across regions, and neither should be offered
        // for removal on that basis.
        val tracks = listOf(
            track(id = "a", title = "Rádio Cidade", isRadio = true, uri = "https://example.invalid/1"),
            track(id = "b", title = "Rádio Cidade", isRadio = true, uri = "https://example.invalid/2"),
        )

        assertTrue(MusicTidying.duplicateGroups(tracks).isEmpty())
    }

    /**
     * The rules composed, on names that carry several problems at once.
     *
     * Each rule is tested alone above. This is where they collide: a track number and an extension
     * and a bitrate tag and an artist all in one string, in the order a real filename has them. The
     * risk is not any single rule but their interaction — stripping the extension after the noise
     * rule has already moved things around, for instance.
     */
    @Test
    fun `the rules compose on realistic filenames`() {
        val cases = mapOf(
            "01 - Pink_Floyd_-_Time.mp3" to ("Pink Floyd" to "Time"),
            "[320kbps] 02. Money (Official Video).flac" to ("Money" to "Money"),
            "03 - Artista - Musica - Ao Vivo.mp3" to ("Artista" to "Musica - Ao Vivo"),
            "REM - Losing My Religion" to ("REM" to "Losing My Religion"),
        )

        cases.forEach { (raw, expected) ->
            val cleaned = MusicTidying.tidyTitle(raw)
            val split = MusicTidying.recoverArtist(cleaned, existingArtist = null)
            if (expected.first != expected.second) {
                assertEquals(expected.first, split.artist, "artist for: $raw")
            }
            assertEquals(expected.second, split.title, "title for: $raw")
        }
    }

    @Test
    fun `an empty library produces nothing`() {
        assertTrue(MusicTidying.proposalsFor(emptyList()).isEmpty())
        assertTrue(MusicTidying.duplicateGroups(emptyList()).isEmpty())
        assertTrue(MusicTidying.sameAddressGroups(emptyList()).isEmpty())
    }
}

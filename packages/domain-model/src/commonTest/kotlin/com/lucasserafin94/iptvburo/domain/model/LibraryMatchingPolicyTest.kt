package com.lucasserafin94.iptvburo.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LibraryMatchingPolicyTest {
    private fun local(
        id: String = "local-1",
        title: String,
        originalTitle: String? = null,
        year: Int? = null,
        kind: MatchKind = MatchKind.MOVIE,
        durationMinutes: Int? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
        externalIds: Map<String, String> = emptyMap(),
    ) = LibraryCandidate(id, title, originalTitle, year, kind, durationMinutes, seasonNumber, episodeNumber, externalIds)

    private fun external(
        id: String = "external-1",
        title: String,
        originalTitle: String? = null,
        year: Int? = null,
        kind: MatchKind = MatchKind.MOVIE,
        durationMinutes: Int? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
        externalIds: Map<String, String> = emptyMap(),
    ) = ExternalCandidate(id, title, originalTitle, year, kind, durationMinutes, seasonNumber, episodeNumber, externalIds)

    @Test
    fun `the remake is not the original`() {
        val result =
            LibraryMatchingPolicy.match(
                local(title = "Duna", year = 1984),
                external(title = "Duna", year = 2021),
            )

        assertEquals(MatchStatus.REJECTED, result.status)
        assertEquals(listOf(MatchReason.YEAR_CONFLICT), result.reasons)
        assertFalse(result.status.mayClaimAutomatically)
    }

    @Test
    fun `the remake never wins the automatic match either`() {
        // The catalogue holds the 1984 film; the service is describing the 2021 one. There is no
        // right answer here, and the screen must say nothing rather than offer the wrong film.
        val match =
            LibraryMatchingPolicy.bestAutomaticMatch(
                candidates = listOf(local(title = "Duna", year = 1984)),
                external = external(title = "Duna", year = 2021),
            )

        assertNull(match)
    }

    @Test
    fun `title and year together may be claimed`() {
        val result =
            LibraryMatchingPolicy.match(
                local(title = "Duna", year = 2021),
                external(title = "Duna", year = 2021),
            )

        assertEquals(MatchStatus.HIGH_CONFIDENCE, result.status)
        assertTrue(MatchReason.TITLE_AND_YEAR in result.reasons)
        assertTrue(result.status.mayClaimAutomatically)
    }

    @Test
    fun `a shared external id outranks everything`() {
        val result =
            LibraryMatchingPolicy.match(
                local(title = "Duna", externalIds = mapOf("tmdb" to "438631")),
                external(title = "Dune", externalIds = mapOf("tmdb" to "438631")),
            )

        assertEquals(MatchStatus.CONFIRMED, result.status)
        assertEquals(listOf(MatchReason.EXTERNAL_ID), result.reasons)
        assertEquals(1.0, result.confidence)
    }

    @Test
    fun `a title with no year is only possible - never claimed`() {
        val result =
            LibraryMatchingPolicy.match(
                local(title = "Duna"),
                external(title = "Duna"),
            )

        assertEquals(MatchStatus.POSSIBLE, result.status)
        assertFalse(result.status.mayClaimAutomatically)
    }

    @Test
    fun `punctuation and accents do not break a title`() {
        val result =
            LibraryMatchingPolicy.match(
                local(title = "Duna: Parte Dois", year = 2024),
                external(title = "duna  parte dois", year = 2024),
            )

        assertEquals(MatchStatus.HIGH_CONFIDENCE, result.status)
    }

    @Test
    fun `a film is never a series`() {
        val result =
            LibraryMatchingPolicy.match(
                local(title = "Fargo", year = 1996, kind = MatchKind.MOVIE),
                external(title = "Fargo", year = 1996, kind = MatchKind.SERIES),
            )

        assertEquals(MatchStatus.REJECTED, result.status)
        assertEquals(listOf(MatchReason.TYPE_CONFLICT), result.reasons)
    }

    @Test
    fun `episodes must agree on their numbering`() {
        val result =
            LibraryMatchingPolicy.match(
                local(title = "Piloto", kind = MatchKind.EPISODE, seasonNumber = 1, episodeNumber = 1),
                external(title = "Piloto", kind = MatchKind.EPISODE, seasonNumber = 2, episodeNumber = 1),
            )

        assertEquals(MatchStatus.REJECTED, result.status)
        assertEquals(listOf(MatchReason.EPISODE_CONFLICT), result.reasons)
    }

    @Test
    fun `a matching episode carries its numbering as a reason`() {
        val result =
            LibraryMatchingPolicy.match(
                local(title = "Piloto", year = 2008, kind = MatchKind.EPISODE, seasonNumber = 1, episodeNumber = 1),
                external(title = "Piloto", year = 2008, kind = MatchKind.EPISODE, seasonNumber = 1, episodeNumber = 1),
            )

        assertEquals(MatchStatus.HIGH_CONFIDENCE, result.status)
        assertTrue(MatchReason.SERIES_SEASON_EPISODE in result.reasons)
    }

    @Test
    fun `an agreeing runtime strengthens a match without ever making one`() {
        val strengthened =
            LibraryMatchingPolicy.match(
                local(title = "Duna", year = 2021, durationMinutes = 155),
                external(title = "Duna", year = 2021, durationMinutes = 157),
            )
        val plain =
            LibraryMatchingPolicy.match(
                local(title = "Duna", year = 2021),
                external(title = "Duna", year = 2021),
            )
        assertTrue(strengthened.confidence > plain.confidence)

        // On its own it decides nothing: same runtime, different films.
        val unrelated =
            LibraryMatchingPolicy.match(
                local(title = "Duna", year = 2021, durationMinutes = 155),
                external(title = "Oppenheimer", year = 2023, durationMinutes = 155),
            )
        assertEquals(MatchStatus.REJECTED, unrelated.status)
    }

    @Test
    fun `unrelated titles are refused rather than guessed at`() {
        val result =
            LibraryMatchingPolicy.match(
                local(title = "Duna", year = 2021),
                external(title = "Oppenheimer", year = 2021),
            )

        assertEquals(MatchStatus.REJECTED, result.status)
        assertEquals(listOf(MatchReason.NO_SIGNAL), result.reasons)
    }

    @Test
    fun `the original title carries a match when the local name was translated`() {
        val result =
            LibraryMatchingPolicy.match(
                local(title = "A Origem", originalTitle = "Inception", year = 2010),
                external(title = "Inception", originalTitle = "Inception", year = 2010),
            )

        assertEquals(MatchStatus.HIGH_CONFIDENCE, result.status)
    }

    @Test
    fun `the strongest candidate wins among several`() {
        val match =
            LibraryMatchingPolicy.bestAutomaticMatch(
                candidates =
                    listOf(
                        local(id = "weak", title = "Duna", year = 2021),
                        local(id = "strong", title = "Duna", externalIds = mapOf("tmdb" to "438631")),
                        local(id = "wrong", title = "Duna", year = 1984),
                    ),
                external = external(title = "Duna", year = 2021, externalIds = mapOf("tmdb" to "438631")),
            )

        assertEquals("strong", match?.localContentId)
        assertEquals(MatchStatus.CONFIRMED, match?.status)
    }

    @Test
    fun `a merely possible candidate is not returned as the best`() {
        val match =
            LibraryMatchingPolicy.bestAutomaticMatch(
                candidates = listOf(local(title = "Duna")),
                external = external(title = "Duna"),
            )

        assertNull(match)
    }

    @Test
    fun `an empty library yields no match rather than an error`() {
        val match =
            LibraryMatchingPolicy.bestAutomaticMatch(
                candidates = emptyList(),
                external = external(title = "Duna", year = 2021),
            )

        assertNull(match)
    }

    @Test
    fun `a blank external id is not a signal`() {
        val result =
            LibraryMatchingPolicy.match(
                local(title = "Duna", year = 1984, externalIds = mapOf("tmdb" to "")),
                external(title = "Duna", year = 2021, externalIds = mapOf("tmdb" to "")),
            )

        assertEquals(MatchStatus.REJECTED, result.status)
    }

    @Test
    fun `identifiers from different namespaces never match each other`() {
        val result =
            LibraryMatchingPolicy.match(
                local(title = "Duna", year = 1984, externalIds = mapOf("tmdb" to "1234")),
                external(title = "Duna", year = 2021, externalIds = mapOf("imdb" to "1234")),
            )

        assertEquals(MatchStatus.REJECTED, result.status)
    }
}

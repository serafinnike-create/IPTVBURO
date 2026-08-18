package com.lucasserafin94.iptvburo.desktop.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Matching the user's own films to the services that carry them.
 *
 * The Serviço selector could only read the playlist's category names, so a list that files films by
 * genre — which is the common case — had no service information at all. This index supplies it by
 * asking TMDb what each service carries and matching that against the library.
 *
 * The risk in doing it this way is a false match: two different films sharing a name, and a filter
 * that conflates them is worse than one that misses one of them. That is what most of these cases
 * are about.
 */
class ServiceTitleIndexTest {
    private fun identity(name: String, year: Int?) = "MOVIE:$name:$year"

    private fun library(vararg entries: Pair<String, Int?>) =
        entries.map { (name, year) -> Triple(name, year, identity(name, year)) }

    @Test
    fun `a title on a service is matched to the library`() {
        val index =
            ServiceTitleIndex.build(
                serviceTitles = mapOf("Netflix" to listOf("Duna" to 2021)),
                library = library("Duna" to 2021, "Outro Filme" to 2020),
            )

        assertEquals(1, index.countFor("Netflix"))
        assertTrue(index.idsFor("Netflix").contains(identity("Duna", 2021)))
    }

    /**
     * The case that makes a naive matcher dangerous.
     *
     * "Duna" is a 1984 film and a 2021 one. Matching on the name alone would put the wrong film under
     * Netflix, and the user would filter by a service and be shown something it does not carry.
     */
    @Test
    fun `a different year is a different film`() {
        val index =
            ServiceTitleIndex.build(
                serviceTitles = mapOf("Netflix" to listOf("Duna" to 2021)),
                library = library("Duna" to 1984),
            )

        assertEquals(0, index.countFor("Netflix"), "The 1984 film is not the one Netflix carries.")
    }

    /**
     * A missing year on either side is not a match.
     *
     * Providers omit the year often, and treating absence as "matches anything" is how one film's
     * availability gets attached to another's.
     */
    @Test
    fun `an unknown year is never matched`() {
        val fromService =
            ServiceTitleIndex.build(
                serviceTitles = mapOf("Netflix" to listOf("Duna" to null)),
                library = library("Duna" to 2021),
            )
        val fromLibrary =
            ServiceTitleIndex.build(
                serviceTitles = mapOf("Netflix" to listOf("Duna" to 2021)),
                library = library("Duna" to null),
            )

        assertEquals(0, fromService.countFor("Netflix"))
        assertEquals(0, fromLibrary.countFor("Netflix"))
    }

    /** Names are matched after normalisation, since providers decorate them freely. */
    @Test
    fun `accents and case do not prevent a match`() {
        val index =
            ServiceTitleIndex.build(
                serviceTitles = mapOf("Netflix" to listOf("O Poderoso Chefão" to 1972)),
                library = library("o poderoso chefao" to 1972),
            )

        assertEquals(1, index.countFor("Netflix"))
    }

    @Test
    fun `one film on two services appears under both`() {
        val index =
            ServiceTitleIndex.build(
                serviceTitles =
                    mapOf(
                        "Netflix" to listOf("Duna" to 2021),
                        "Max" to listOf("Duna" to 2021),
                    ),
                library = library("Duna" to 2021),
            )

        assertEquals(1, index.countFor("Netflix"))
        assertEquals(1, index.countFor("Max"))
    }

    /** A service whose titles the library does not hold is absent, not empty. */
    @Test
    fun `a service with no library titles is not listed`() {
        val index =
            ServiceTitleIndex.build(
                serviceTitles = mapOf("Netflix" to listOf("Filme Que Nao Tenho" to 2021)),
                library = library("Duna" to 2021),
            )

        assertTrue(index.isEmpty, "A service matching nothing must not be offered as a filter.")
        assertEquals(0, index.countFor("Netflix"))
    }

    @Test
    fun `an empty library or empty answer yields nothing`() {
        assertTrue(ServiceTitleIndex.build(emptyMap(), library("Duna" to 2021)).isEmpty)
        assertTrue(ServiceTitleIndex.build(mapOf("Netflix" to listOf("Duna" to 2021)), emptyList()).isEmpty)
    }

    /** The same film listed twice by a service counts once. */
    @Test
    fun `duplicates collapse`() {
        val index =
            ServiceTitleIndex.build(
                serviceTitles = mapOf("Netflix" to listOf("Duna" to 2021, "Duna" to 2021)),
                library = library("Duna" to 2021),
            )

        assertEquals(1, index.countFor("Netflix"))
    }
}

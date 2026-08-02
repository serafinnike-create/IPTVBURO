package com.lucasserafin94.iptvburo.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContentIdentityTest {
    @Test
    fun `same film from two providers resolves to one identity`() {
        // The whole point: a user replacing their list must keep their favourites pointing at the
        // same films, even though each provider decorates the title its own way.
        val first = ContentIdentity.of(ContentKind.MOVIE, "[4K] O Poderoso Chefão (1972) DUAL", 1972)
        val second = ContentIdentity.of(ContentKind.MOVIE, "O Poderoso Chefao 1080p LEG", 1972)

        assertEquals(first, second)
    }

    @Test
    fun `different films do not collide`() {
        val alien = ContentIdentity.of(ContentKind.MOVIE, "Alien", 1979)
        val aliens = ContentIdentity.of(ContentKind.MOVIE, "Aliens", 1986)

        assertNotEquals(alien, aliens)
    }

    @Test
    fun `remakes are separated by year`() {
        val original = ContentIdentity.of(ContentKind.MOVIE, "Dune", 1984)
        val remake = ContentIdentity.of(ContentKind.MOVIE, "Dune", 2021)

        assertNotEquals(original, remake)
    }

    @Test
    fun `a film and a series sharing a name do not collide`() {
        val film = ContentIdentity.of(ContentKind.MOVIE, "Fargo", 1996)
        val series = ContentIdentity.of(ContentKind.SERIES, "Fargo", 1996)

        assertNotEquals(film, series)
    }

    @Test
    fun `accents and case do not change identity`() {
        val accented = ContentIdentity.of(ContentKind.MOVIE, "Amélie", 2001)
        val plain = ContentIdentity.of(ContentKind.MOVIE, "AMELIE", 2001)

        assertEquals(accented, plain)
    }

    @Test
    fun `quality and language markers are stripped`() {
        assertEquals("matrix", ContentIdentity.slugify("[4K] Matrix HDR DUAL 2160p"))
        assertEquals("matrix", ContentIdentity.slugify("Matrix - H265 DUBLADO"))
    }

    @Test
    fun `a title that is only decoration still yields a usable identity`() {
        // Slugifying "[4K] HD DUAL" removes everything. Without the fallback every such title
        // would collapse onto one key and mark unrelated content as favourite.
        val first = ContentIdentity.of(ContentKind.MOVIE, "[4K] HD DUAL")
        val second = ContentIdentity.of(ContentKind.MOVIE, "[4K] HD LEG")

        assertTrue(first.key.isNotBlank())
        assertNotEquals(first, second)
    }

    @Test
    fun `year is read from the title when the provider omits it`() {
        assertEquals(1998, ContentIdentity.yearFromTitle("The Big Lebowski (1998)"))
        assertNull(ContentIdentity.yearFromTitle("The Big Lebowski"))
    }

    @Test
    fun `an implausible year in the title is ignored`() {
        // Providers put resolutions and channel numbers in parentheses too.
        assertNull(ContentIdentity.yearFromTitle("Some Channel (1080)"))
        assertNull(ContentIdentity.yearFromTitle("Some Channel (9999)"))
    }

    @Test
    fun `identity without a year differs from the same title with one`() {
        val dated = ContentIdentity.of(ContentKind.MOVIE, "Heat", 1995)
        val undated = ContentIdentity.of(ContentKind.MOVIE, "Heat", null)

        assertNotEquals(dated, undated)
    }

    @Test
    fun `a blank identity is rejected`() {
        assertFailsWith<IllegalArgumentException> { ContentIdentity("") }
    }
}

package com.lucasserafin94.iptvburo.desktop.ui

import com.lucasserafin94.iptvburo.xtream.XtreamCategory
import com.lucasserafin94.iptvburo.xtream.XtreamContentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Sorting a playlist's flat category list into "what kind of film" and "from which service".
 *
 * The categories are untyped: "Filmes | Netflix" and "Filmes | Acao" are the same kind of record and
 * only their names say which is which. Everything here is therefore about reading names the way
 * providers actually write them — inconsistently.
 */
class CategorySplitTest {
    private fun category(name: String, id: String = name) =
        XtreamCategory(providerId = id, name = name, contentType = XtreamContentType.MOVIE)

    @Test
    fun `genres and services go to different groups`() {
        val split =
            splitCategories(
                listOf(
                    category("Filmes | Acao"),
                    category("Filmes | Netflix"),
                    category("Filmes | Aventura"),
                    category("Filmes | Disney+"),
                ),
            )

        assertEquals(listOf("Acao", "Aventura"), split.genres.map(CategoryChoice::label))
        assertEquals(listOf("Netflix", "Disney+"), split.providers.map(CategoryChoice::label))
    }

    /**
     * The provider's own ordering survives.
     *
     * "Lançamentos" is first because the provider put it first, and sorting alphabetically would bury
     * it under "4K" and "Acao" — losing information the provider was deliberately conveying.
     */
    @Test
    fun `order within each group is preserved`() {
        val split =
            splitCategories(
                listOf(
                    category("Filmes | Lancamentos"),
                    category("Filmes | 4K"),
                    category("Filmes | Acao"),
                ),
            )

        assertEquals(listOf("Lancamentos", "4K", "Acao"), split.genres.map(CategoryChoice::label))
    }

    /**
     * One entry per service, named for the service.
     *
     * A playlist routinely files "Netflix" and "Netflix 4K" separately. Two rows both reading
     * "Netflix" cannot be told apart in a menu, so the first wins and keeps its own id.
     */
    @Test
    fun `a service appearing twice is offered once`() {
        val split =
            splitCategories(
                listOf(
                    category("Filmes | Netflix", id = "10"),
                    category("Filmes | Netflix 4K", id = "11"),
                    category("SERIES - NETFLIX", id = "12"),
                ),
            )

        assertEquals(1, split.providers.size)
        assertEquals("Netflix", split.providers.single().label)
        assertEquals("10", split.providers.single().id, "The first category's id must be kept.")
    }

    /** Providers write service names in every casing and with every separator. */
    @Test
    fun `service names are recognised however they are written`() {
        val split =
            splitCategories(
                listOf(
                    category("SÉRIES - NETFLIX", id = "a"),
                    category("VOD amazon prime video", id = "b"),
                    category("Filmes | HBO Max", id = "c"),
                    category("APPLE TV+ | Filmes", id = "d"),
                ),
            )

        assertEquals(4, split.providers.size, "Expected every spelling to be recognised.")
        assertTrue(split.genres.isEmpty(), "No genre should have been produced: ${split.genres}")
    }

    /**
     * "Cinemax" is a channel, not the streaming service.
     *
     * The word-boundary rule exists for exactly this, and a substring test would badge it as Max.
     */
    @Test
    fun `cinemax is not the streaming service`() {
        val split = splitCategories(listOf(category("Canais | Cinemax")))

        assertTrue(split.providers.isEmpty(), "Cinemax was badged as Max.")
        assertEquals(listOf("Cinemax"), split.genres.map(CategoryChoice::label))
    }

    /** But "Max" on its own is. */
    @Test
    fun `max on its own is the streaming service`() {
        val split = splitCategories(listOf(category("Filmes | Max")))

        assertEquals(listOf("Max"), split.providers.map(CategoryChoice::label))
    }

    @Test
    fun `a playlist with no services offers no provider selector`() {
        val split = splitCategories(listOf(category("Filmes | Acao"), category("Filmes | Drama")))

        assertFalse(split.hasProviders, "A provider selector with nothing in it should not be drawn.")
    }

    @Test
    fun `an empty playlist splits into nothing`() {
        val split = splitCategories(emptyList())

        assertTrue(split.genres.isEmpty())
        assertFalse(split.hasProviders)
    }

    /** The closed selectors read their label and chip back out of the split. */
    @Test
    fun `a selected id can be looked up`() {
        val split =
            splitCategories(
                listOf(category("Filmes | Acao", id = "g1"), category("Filmes | Netflix", id = "p1")),
            )

        assertEquals("Acao", split.labelFor("g1"))
        assertEquals("Netflix", split.labelFor("p1"))
        assertTrue(split.isProvider("p1"))
        assertFalse(split.isProvider("g1"))
        assertEquals("N", split.providerFor("p1")?.monogram)
        assertNull(split.providerFor("g1"), "A genre has no service chip.")
    }

    /** Nothing selected is the ordinary state, and must not be mistaken for a provider. */
    @Test
    fun `no selection resolves to nothing`() {
        val split = splitCategories(listOf(category("Filmes | Netflix", id = "p1")))

        assertNull(split.labelFor(null))
        assertNull(split.providerFor(null))
        assertFalse(split.isProvider(null))
    }
}

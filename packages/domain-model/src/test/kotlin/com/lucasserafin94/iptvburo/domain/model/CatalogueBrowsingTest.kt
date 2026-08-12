package com.lucasserafin94.iptvburo.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The browsing rules a catalogue screen depends on.
 *
 * These are asserted here rather than through the UI because they decide what a user can find: a
 * filter that quietly drops entries makes part of someone's library unreachable, and that is not
 * visible by looking at a working grid.
 */
class CatalogueBrowsingTest {
    private val items =
        listOf(
            BrowsableItem("1", "Zulu", "Ação, Aventura", 1964, 7.2),
            BrowsableItem("2", "Alien", "Terror / Ficção científica", 1979, 8.4),
            BrowsableItem("3", "Amélie", "comédia", 2001, 8.3),
            BrowsableItem("4", "Sem data", "Ação", null, null),
        )

    @Test
    fun `genres are split on the separators providers actually use`() {
        val genres = availableGenres(items)

        // "Ação, Aventura" is two genres to anyone reading it, and "Terror / Ficção científica"
        // is two more. Treating either as one string would offer a filter nobody would pick.
        assertTrue(genres.containsAll(listOf("Ação", "Aventura", "Terror", "Ficção científica")))
    }

    @Test
    fun `a genre spelled two ways appears once`() {
        val mixed =
            listOf(
                BrowsableItem("a", "One", "AÇÃO", null, null),
                BrowsableItem("b", "Two", "ação", null, null),
            )

        assertEquals(1, availableGenres(mixed).size)
    }

    @Test
    fun `filtering by genre matches within a packed genre field`() {
        val filtered = applyCatalogueFilter(items, CatalogueFilter(genre = "Aventura"))

        // Equality against the whole field would have found nothing here, hiding a film the user
        // can plainly see is an adventure.
        assertEquals(listOf("1"), filtered.map(BrowsableItem::id))
    }

    @Test
    fun `filtering by genre ignores case`() {
        val filtered = applyCatalogueFilter(items, CatalogueFilter(genre = "COMÉDIA"))

        assertEquals(listOf("3"), filtered.map(BrowsableItem::id))
    }

    @Test
    fun `an empty filter is the whole catalogue rather than nothing`() {
        val filtered = applyCatalogueFilter(items, CatalogueFilter())

        assertEquals(items.map(BrowsableItem::id), filtered.map(BrowsableItem::id))
    }

    @Test
    fun `the provider's own order is preserved when no sort is chosen`() {
        // A provider's ordering often carries editorial meaning that any sort of ours destroys.
        val filtered = applyCatalogueFilter(items, CatalogueFilter(sort = CatalogueSort.PROVIDER))

        assertEquals(listOf("1", "2", "3", "4"), filtered.map(BrowsableItem::id))
    }

    @Test
    fun `undated entries sort last rather than counting as year zero`() {
        val ascending = applyCatalogueFilter(items, CatalogueFilter(sort = CatalogueSort.YEAR_ASC))

        assertEquals(
            "Sem data",
            ascending.last().title,
            "A missing year treated as zero would put undated films above everything, which " +
                "reads as the catalogue being broken.",
        )
    }

    @Test
    fun `newest first puts the most recent release at the top`() {
        val descending = applyCatalogueFilter(items, CatalogueFilter(sort = CatalogueSort.YEAR_DESC))

        assertEquals(2001, descending.first().year)
    }

    @Test
    fun `unrated entries never outrank rated ones`() {
        val byRating = applyCatalogueFilter(items, CatalogueFilter(sort = CatalogueSort.RATING_DESC))

        assertEquals(8.4, byRating.first().rating)
        assertEquals(null, byRating.last().rating)
    }

    @Test
    fun `sorting is fully determined so the grid cannot appear to shuffle itself`() {
        val duplicates =
            listOf(
                BrowsableItem("b", "Same Title", null, 2000, null),
                BrowsableItem("a", "Same Title", null, 2000, null),
            )

        val once = applyCatalogueFilter(duplicates, CatalogueFilter(sort = CatalogueSort.TITLE_ASC))
        val twice =
            applyCatalogueFilter(duplicates.reversed(), CatalogueFilter(sort = CatalogueSort.TITLE_ASC))

        // Same set, different input order, identical output: without the id tie-break these two
        // could disagree and the grid would reorder itself between recompositions.
        assertEquals(once.map(BrowsableItem::id), twice.map(BrowsableItem::id))
    }

    @Test
    fun `years are offered newest first`() {
        assertEquals(listOf(2001, 1979, 1964), availableYears(items))
    }

    @Test
    fun `layout ids survive a reordered enum`() {
        // The id is persisted; resolving by ordinal would silently change what someone chose.
        assertEquals(CatalogueLayout.LIST, CatalogueLayout.fromId("list"))
        assertEquals(CatalogueLayout.POSTER, CatalogueLayout.fromId(null))
        assertEquals(CatalogueLayout.POSTER, CatalogueLayout.fromId("nonsense"))
    }

    @Test
    fun `an untouched filter reports itself inactive`() {
        assertTrue(!CatalogueFilter().isActive)
        assertTrue(CatalogueFilter(genre = "Ação").isActive)
        assertTrue(CatalogueFilter(sort = CatalogueSort.TITLE_ASC).isActive)
    }
}

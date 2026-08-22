package com.lucasserafin94.iptvburo.domain.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The seasonal shelf is driven entirely by the calendar, so the calendar is what has to be pinned
 * down. Every boundary is asserted from both sides: an off-by-one here shows up as a Christmas row
 * in November, which is exactly the kind of thing nobody notices until December.
 */
class SeasonalCollectionsTest {
    private fun idsOn(
        month: Int,
        day: Int,
        year: Int = 2025,
    ): List<String> = SeasonalCollections.collectionsFor(LocalDate(year, month, day)).map(SeasonalCollection::id)

    // -----------------------------------------------------------------------------------------
    // The occasions themselves
    // -----------------------------------------------------------------------------------------

    @Test
    fun `December days offer the Christmas shelf`() {
        assertTrue("christmas" in idsOn(12, 1))
        assertTrue("christmas" in idsOn(12, 12))
        assertTrue("christmas" in idsOn(12, 25))
    }

    @Test
    fun `late October offers the Halloween shelf`() {
        assertTrue("halloween" in idsOn(10, 20))
        assertTrue("halloween" in idsOn(10, 31))
    }

    @Test
    fun `February offers the Valentine shelf`() {
        assertTrue("valentines" in idsOn(2, 14))
    }

    @Test
    fun `Brazilian Dia dos Namorados also offers the Valentine shelf`() {
        // The same collection serves both dates; the app ships in a market that keeps 12 June.
        assertTrue("valentines" in idsOn(6, 12))
    }

    @Test
    fun `July offers the family holiday shelf`() {
        assertTrue("school-holidays" in idsOn(7, 1))
        assertTrue("school-holidays" in idsOn(7, 20))
        assertTrue("school-holidays" in idsOn(7, 31))
    }

    // -----------------------------------------------------------------------------------------
    // The rest of the year
    // -----------------------------------------------------------------------------------------

    @Test
    fun `an ordinary March day has no seasonal shelf`() {
        assertEquals(emptyList(), idsOn(3, 15))
        assertNull(SeasonalCollections.primaryCollectionFor(LocalDate(2025, 3, 15)))
    }

    @Test
    fun `the quiet months stay quiet`() {
        // Sampled rather than exhaustive: these are the months with no window at all, and a shelf
        // appearing in any of them means a window was widened without anyone noticing.
        listOf(1 to 20, 3 to 1, 4 to 10, 5 to 5, 8 to 15, 9 to 9, 11 to 20).forEach { (month, day) ->
            assertEquals(emptyList(), idsOn(month, day), "expected nothing seasonal on $month/$day")
        }
    }

    // -----------------------------------------------------------------------------------------
    // Boundaries
    // -----------------------------------------------------------------------------------------

    @Test
    fun `Halloween starts on the eighteenth and not before`() {
        assertFalse("halloween" in idsOn(10, 17))
        assertTrue("halloween" in idsOn(10, 18))
    }

    @Test
    fun `Halloween ends on the first of November`() {
        assertTrue("halloween" in idsOn(11, 1))
        assertFalse("halloween" in idsOn(11, 2))
    }

    @Test
    fun `Christmas does not leak into November`() {
        assertFalse("christmas" in idsOn(11, 30))
        assertTrue("christmas" in idsOn(12, 1))
    }

    @Test
    fun `Christmas stops once the day has passed`() {
        assertTrue("christmas" in idsOn(12, 26))
        assertFalse("christmas" in idsOn(12, 27))
    }

    @Test
    fun `the Valentine window is bounded on both sides`() {
        assertFalse("valentines" in idsOn(2, 6))
        assertTrue("valentines" in idsOn(2, 7))
        assertTrue("valentines" in idsOn(2, 15))
        assertFalse("valentines" in idsOn(2, 16))
    }

    @Test
    fun `the family window does not spill into August`() {
        assertFalse("school-holidays" in idsOn(6, 30))
        assertFalse("school-holidays" in idsOn(8, 1))
    }

    @Test
    fun `the new year window wraps across the turn of the year`() {
        // The one window whose end is in an earlier month than its start, so it is the only proof
        // that the containment check is not a naive month/day range comparison.
        assertTrue("new-year" in idsOn(12, 31))
        assertTrue("new-year" in idsOn(1, 1))
        assertTrue("new-year" in idsOn(1, 6))
        assertFalse("new-year" in idsOn(1, 7))
        assertFalse("new-year" in idsOn(12, 26))
    }

    @Test
    fun `a leap day resolves without a window of its own`() {
        // 29 February exists only every fourth year; nothing may depend on it being a boundary.
        assertEquals(idsOn(2, 28, year = 2024), idsOn(2, 29, year = 2024))
    }

    // -----------------------------------------------------------------------------------------
    // Shape of the result
    // -----------------------------------------------------------------------------------------

    @Test
    fun `a collection never appears twice on one day`() {
        // Valentine's has two windows; if they were ever widened into each other the shelf would
        // otherwise be rendered twice.
        (1..12).forEach { month ->
            (1..28).forEach { day ->
                val ids = idsOn(month, day)
                assertEquals(ids.distinct(), ids, "duplicate collection on $month/$day")
            }
        }
    }

    @Test
    fun `the primary collection is the first match of the day`() {
        val date = LocalDate(2025, 12, 25)
        assertEquals(
            SeasonalCollections.collectionsFor(date).first(),
            SeasonalCollections.primaryCollectionFor(date),
        )
    }

    @Test
    fun `every collection carries search terms in Portuguese and in English`() {
        // Providers mix languages inside a single playlist, so a term list in one language finds
        // only part of what the catalogue actually holds.
        val christmas = assertNotNull(SeasonalCollections.primaryCollectionFor(LocalDate(2025, 12, 10)))
        assertTrue("natal" in christmas.searchTerms)
        assertTrue("christmas" in christmas.searchTerms)

        val halloween = assertNotNull(SeasonalCollections.primaryCollectionFor(LocalDate(2025, 10, 25)))
        assertTrue("terror" in halloween.searchTerms)
        assertTrue("horror" in halloween.searchTerms)
    }

    @Test
    fun `search terms are lowercase and free of blanks`() {
        // The catalogue match is case-insensitive but not trimmed, so a stray space would quietly
        // stop a term matching anything at all.
        allCollections().forEach { collection ->
            assertTrue(collection.searchTerms.isNotEmpty(), "${collection.id} has no search terms")
            collection.searchTerms.forEach { term ->
                assertEquals(term.lowercase(), term, "term '$term' is not lowercase")
                assertEquals(term.trim(), term, "term '$term' has surrounding blanks")
                assertTrue(term.length >= 3, "term '$term' is too short to be a safe substring")
            }
        }
    }

    // -----------------------------------------------------------------------------------------
    // Titles
    // -----------------------------------------------------------------------------------------

    @Test
    fun `every collection is titled in all four shipped languages`() {
        // The string table refuses to compile with a missing translation; these titles are data, so
        // nothing but a test protects them.
        allCollections().forEach { collection ->
            listOf("pt-BR", "en", "de", "it").forEach { tag ->
                val title = collection.title(tag)
                assertTrue(title.isNotBlank(), "${collection.id} has no title for $tag")
                assertTrue(title != collection.id, "${collection.id} fell back to its identifier for $tag")
            }
        }
    }

    @Test
    fun `an unknown language falls back to English rather than to the identifier`() {
        val christmas = assertNotNull(SeasonalCollections.primaryCollectionFor(LocalDate(2025, 12, 10)))
        assertEquals(christmas.title("en"), christmas.title("fr"))
    }

    private fun allCollections(): List<SeasonalCollection> =
        (1..12)
            .flatMap { month -> (1..28).map { day -> LocalDate(2025, month, day) } }
            .flatMap(SeasonalCollections::collectionsFor)
            .distinctBy(SeasonalCollection::id)
}

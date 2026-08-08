package com.lucasserafin94.iptvburo.desktop

import com.lucasserafin94.iptvburo.domain.model.shelfDeduplicationKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DailyHomeRotationTest {
    @Test
    fun `daily selection is stable during a day and changes with the next seed`() {
        val today = rotatingPageIndex(seed = 42_001, pageCount = 97)
        val repeated = rotatingPageIndex(seed = 42_001, pageCount = 97)
        val tomorrow = rotatingPageIndex(seed = 42_032, pageCount = 97)

        assertEquals(today, repeated)
        assertNotEquals(today, tomorrow)
        assertTrue(today in 0 until 97)
        assertTrue(tomorrow in 0 until 97)
    }

    @Test
    fun `empty catalogs resolve to the safe first page`() {
        assertEquals(0, rotatingPageIndex(seed = Int.MIN_VALUE, pageCount = 0))
    }

    /**
     * The shelf key groups a provider's variants of one film.
     *
     * These cases came from `editorialCatalogKey`, which the shelves used until it was found to
     * miss accents, pipe-separated labels and trailing single-letter tags — the shapes that were
     * still showing as duplicate posters on the home screen. Kept here so the replacement is held
     * to everything the original already handled.
     */
    @Test
    fun `shelf key groups quality and language variants`() {
        assertEquals(
            "Dívida de Honra [L1]".shelfDeduplicationKey(),
            "Dívida de Honra [L2] 4K".shelfDeduplicationKey(),
        )
        assertEquals("Filme Exemplo HEVC".shelfDeduplicationKey(), "Filme Exemplo HD".shelfDeduplicationKey())
        assertNotEquals("Filme Exemplo".shelfDeduplicationKey(), "Outro Filme".shelfDeduplicationKey())
    }

    /** A numbered sequel is a different film, and no amount of tag-stripping may fuse them. */
    @Test
    fun `sequels stay distinct`() {
        assertNotEquals("Enola Holmes 2".shelfDeduplicationKey(), "Enola Holmes 3".shelfDeduplicationKey())
        assertNotEquals("Duna".shelfDeduplicationKey(), "Duna: Parte Dois".shelfDeduplicationKey())
    }
}

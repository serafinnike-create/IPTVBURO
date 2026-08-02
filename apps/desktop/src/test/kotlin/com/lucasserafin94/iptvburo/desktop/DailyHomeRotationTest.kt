package com.lucasserafin94.iptvburo.desktop

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

    @Test
    fun `editorial key groups quality and language variants`() {
        assertEquals(editorialCatalogKey("Dívida de Honra [L1]"), editorialCatalogKey("Dívida de Honra [L2] 4K"))
        assertEquals(editorialCatalogKey("Filme Exemplo HEVC"), editorialCatalogKey("Filme Exemplo HD"))
        assertNotEquals(editorialCatalogKey("Filme Exemplo"), editorialCatalogKey("Outro Filme"))
    }
}

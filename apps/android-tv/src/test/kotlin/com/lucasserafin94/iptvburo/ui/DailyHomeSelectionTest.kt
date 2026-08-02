package com.lucasserafin94.iptvburo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DailyHomeSelectionTest {
    @Test
    fun `daily rank is stable for one day and changes on the next`() {
        val today = dailyEditorialRank("movie-42", 20_000)

        assertEquals(today, dailyEditorialRank("movie-42", 20_000))
        assertNotEquals(today, dailyEditorialRank("movie-42", 20_001))
    }

    @Test
    fun `title key groups duplicated provider variants`() {
        assertEquals(dailyCatalogTitleKey("Filme Legal [L1] HD"), dailyCatalogTitleKey("Filme Legal [L2] 4K"))
        assertNotEquals(dailyCatalogTitleKey("Filme Legal"), dailyCatalogTitleKey("Outro Filme"))
    }
}

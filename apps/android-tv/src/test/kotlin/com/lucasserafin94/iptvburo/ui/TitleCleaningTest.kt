package com.lucasserafin94.iptvburo.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What is stripped from a provider's title before TMDb is asked about it.
 *
 * Found on a device: a Netflix series showed no platform logo. The cause was not the badge or the
 * lookup but this — the title went to TMDb as "Minha Carreira Brilhante (2026)", TMDb matches on the
 * name alone and takes the year as a separate filter, so the search returned nothing and every
 * follow-up that needed the match — logo, score, trailer — came back empty.
 */
class TitleCleaningTest {
    @Test
    fun `a trailing release year is dropped so the search can match`() {
        assertEquals(
            "Minha Carreira Brilhante",
            "Minha Carreira Brilhante (2026)".withoutTrailingYear(),
        )
        assertEquals("O Silo", "O Silo (1999)".withoutTrailingYear())
    }

    /**
     * Only a year, and only at the end.
     *
     * A parenthetical that is part of the name has to survive, or the search would be sent looking
     * for a film that does not exist under that title.
     */
    @Test
    fun `a parenthetical that is not a trailing year is kept`() {
        assertEquals("Blade Runner (2049)", "Blade Runner (2049)".withoutTrailingYear())
        assertEquals("Ocean's (Eleven)", "Ocean's (Eleven)".withoutTrailingYear())
        assertEquals("Se7en (1995) parte 2", "Se7en (1995) parte 2".withoutTrailingYear())
    }

    @Test
    fun `a title with no year is left exactly as it is`() {
        assertEquals("Interestelar", "Interestelar".withoutTrailingYear())
    }
}

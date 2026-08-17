package com.lucasserafin94.iptvburo.desktop.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The critics' chips, and the one rule among them that can be wrong rather than merely ugly.
 *
 * Metacritic's colour is not decoration: green means favourable, yellow mixed, red unfavourable, and
 * those bands are published. The chip was a fixed green for every score, which announced "favourable"
 * next to a 32 — a colour that contradicts the number beside it is worse than no colour at all,
 * because the reader believes it.
 */
class CriticMarkTest {
    @Test
    fun `a favourable metascore is green`() {
        // Metacritic's own floor for "generally favourable".
        assertEquals(criticMarkMetascore(100).accent, criticMarkMetascore(METASCORE_FAVOURABLE).accent)
        assertNotEquals(criticMarkMetascore(METASCORE_FAVOURABLE).accent, criticMarkMetascore(METASCORE_FAVOURABLE - 1).accent)
    }

    @Test
    fun `a mixed metascore is neither the favourable nor the unfavourable colour`() {
        val mixed = criticMarkMetascore(50).accent

        assertNotEquals(criticMarkMetascore(90).accent, mixed)
        assertNotEquals(criticMarkMetascore(10).accent, mixed)
    }

    @Test
    fun `an unfavourable metascore does not reuse the favourable colour`() {
        // The reported defect, stated as a test: 32 must not be dressed as a good review.
        assertNotEquals(criticMarkMetascore(90).accent, criticMarkMetascore(32).accent)
    }

    @Test
    fun `each band holds across its whole range`() {
        val favourable = criticMarkMetascore(METASCORE_FAVOURABLE).accent
        val mixed = criticMarkMetascore(METASCORE_MIXED).accent

        assertEquals(favourable, criticMarkMetascore(100).accent)
        assertEquals(mixed, criticMarkMetascore(METASCORE_FAVOURABLE - 1).accent)
        assertEquals(criticMarkMetascore(0).accent, criticMarkMetascore(METASCORE_MIXED - 1).accent)
    }

    /**
     * Every chip has to be readable, which is the whole reason the ink is a field rather than always
     * white: white lettering on IMDb's yellow is the one combination here that disappears.
     */
    @Test
    fun `the bright chips take dark ink`() {
        assertEquals(CriticInkDark, CriticMarkImdb.ink)
        assertEquals(CriticInkDark, criticMarkMetascore(90).ink)
        assertEquals(CriticInkDark, criticMarkMetascore(50).ink)
        assertEquals(CriticInkDark, criticMarkMetascore(10).ink)
    }

    /** The letters are what identify the source now that the dot is gone, so none may be blank. */
    @Test
    fun `every mark carries its own short form`() {
        val marks = listOf(CriticMarkTomatometer, CriticMarkImdb, criticMarkMetascore(70))

        marks.forEach { mark ->
            assertTrue(mark.initials.isNotBlank(), "A chip with no letters identifies nothing.")
        }
        assertEquals(
            marks.size,
            marks.map(CriticMark::initials).distinct().size,
            "Two sources sharing one short form would be indistinguishable.",
        )
    }
}

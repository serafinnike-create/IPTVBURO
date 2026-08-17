package com.lucasserafin94.iptvburo.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * How a vote tally is written on the details screen.
 *
 * The count is what gives the percentage beside it any weight, so it has to stay readable at every
 * magnitude: a blockbuster carries hundreds of thousands of votes and an obscure film carries nine.
 */
class RatingStripTest {
    @Test
    fun `small tallies are written exactly`() {
        assertEquals("9", formatVotes(9))
        assertEquals("999", formatVotes(999))
    }

    /** Past a thousand the magnitude is the point; nobody reads "92431". */
    @Test
    fun `thousands are abbreviated`() {
        assertEquals("1k", formatVotes(1_000))
        assertEquals("92k", formatVotes(92_431))
    }

    @Test
    fun `millions keep one decimal so the scale still reads`() {
        assertEquals("1.2M", formatVotes(1_250_000))
    }
}

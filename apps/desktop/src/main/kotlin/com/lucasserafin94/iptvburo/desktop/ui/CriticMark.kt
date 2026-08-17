package com.lucasserafin94.iptvburo.desktop.ui

import androidx.compose.ui.graphics.Color

/**
 * How a critic's score identifies itself on screen.
 *
 * Letters and a colour, not a logo. Rotten Tomatoes' tomato and Metacritic's shield are licensed
 * images with no public address to fetch them from — unlike TMDb's mark, which that company
 * publishes through its own image CDN for exactly this use — so the only honest options are the
 * companies' colours and their own short forms.
 *
 * This started as a plain coloured dot per source and was reported as the icons being missing, which
 * was fair: three bullets in three colours identify nothing to somebody who has not learnt the code.
 */
data class CriticMark(
    /** The company's own short form. Never translated. */
    val initials: String,
    /** The company's colour, as the chip's background. */
    val accent: Color,
    /** Ink for [initials], chosen so the letters read against [accent]. */
    val ink: Color,
)

/**
 * Dark ink, for the chips whose brand colour is bright enough that white would disappear.
 *
 * Declared before the marks that use it: top-level properties initialise in file order, so a mark
 * built above this line would take a null colour and paint nothing.
 */
val CriticInkDark = Color(0xFF111111)

/** Rotten Tomatoes' red, carrying "RT" rather than their tomato. */
val CriticMarkTomatometer =
    CriticMark(initials = "RT", accent = Color(0xFFFA320A), ink = Color.White)

/** IMDb's yellow with dark lettering, which is very nearly how IMDb draws its own mark. */
val CriticMarkImdb =
    CriticMark(initials = "IMDb", accent = Color(0xFFF5C518), ink = CriticInkDark)

/**
 * Metacritic's chip, in the colour Metacritic itself would print this score in.
 *
 * Their bands are public and are part of how the number is read: green favourable, yellow mixed, red
 * unfavourable. The chip used to be a fixed green, which announced "favourable" beside a score of 32
 * — worse than no colour at all, since it contradicted the figure next to it.
 */
fun criticMarkMetascore(percent: Int): CriticMark =
    CriticMark(
        initials = "MC",
        accent =
            when {
                percent >= METASCORE_FAVOURABLE -> Color(0xFF00CE7A)
                percent >= METASCORE_MIXED -> Color(0xFFFFBD3F)
                else -> Color(0xFFFF6874)
            },
        // Every band is a bright colour, so the letters stay dark across all three rather than
        // switching ink partway up the scale.
        ink = CriticInkDark,
    )

/** Metacritic's own threshold for a favourable score. */
const val METASCORE_FAVOURABLE = 61

/** And for a mixed one; below this they call it unfavourable. */
const val METASCORE_MIXED = 40

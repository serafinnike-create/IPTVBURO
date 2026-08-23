package com.lucasserafin94.iptvburo.desktop.app

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The similar-titles rail runs the width of the window, not the width of the text beside it.
 *
 * Everything else on the detail page sits in the column next to the poster, which is right for
 * prose — a synopsis measured against a wide window is genuinely harder to read. A rail of posters
 * is not prose. Indented it fitted three fewer cards while the left of the window sat empty, which
 * is what was reported.
 *
 * Three things stack up to that inset: the poster (208dp), the gap after it (24dp) and the page's
 * own padding (24dp). The shelf reclaims all three on the left and the padding again on the right.
 */
class ShelfUsesFullWidthTest {
    private val source: String =
        Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/XtreamWorkspace.kt").readText()

    private val shelf: String =
        source.substringAfter("private fun SimilarTitlesShelf(").substringBefore("\n}\n")

    @Test
    fun `the inset is named rather than repeated`() {
        // Two places have to agree about it: the layout that creates the inset and the shelf that
        // escapes it. A literal in either would drift from the other.
        assertTrue(
            source.contains("private val POSTER_COLUMN_INSET = 208.dp + BuroSpacing.Lg + BuroSpacing.Lg"),
            "The inset must be stated once, from the parts that make it up.",
        )
    }

    @Test
    fun `the shelf moves itself and nothing else`() {
        // `offset` rather than negative padding on a shared parent: the heading and the synopsis
        // above keep their own measure, which is the point of pulling only the rail out.
        assertTrue(
            shelf.contains("Modifier.offset(x = -reclaimedInset)"),
            "The rail should shift alone, leaving the text above it where it was.",
        )
    }

    @Test
    fun `the rail is measured wider than its parent allows`() {
        // Offsetting alone would move the rail left and leave it the old width, with its right edge
        // ending short of the window. The layout modifier is what actually widens it.
        assertTrue(
            shelf.contains("constraints.maxWidth + reclaimedInset.roundToPx()"),
            "The rail has to be measured against the reclaimed width, not merely moved into it.",
        )
        assertTrue(
            shelf.contains("BuroSpacing.Lg.roundToPx()"),
            "The page's padding exists on the right too, so it is reclaimed at both ends.",
        )
    }

    @Test
    fun `the compact layout reclaims nothing`() {
        // There is no poster column to escape when the window is narrow, and offsetting there would
        // push the first card under the sidebar.
        assertTrue(
            source.contains("reclaimedInset = if (compact) 0.dp else POSTER_COLUMN_INSET"),
            "A layout without a poster column must not shift the rail at all.",
        )
        assertFalse(
            shelf.contains("reclaimedInset: Dp,\n"),
            "The parameter should default to zero so a caller that forgets it cannot break a layout.",
        )
    }

    @Test
    fun `both panes widen their shelf`() {
        // Films and series, because the series page is the one that historically missed out.
        assertTrue(
            source.split("reclaimedInset = if (compact) 0.dp else POSTER_COLUMN_INSET").size - 1 == 2,
            "The film page and the series page must both pass the inset.",
        )
    }
}

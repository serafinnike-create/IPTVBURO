package com.lucasserafin94.iptvburo.desktop.app

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * How much of the catalogue screen the controls are allowed to take.
 *
 * Three bands of chrome sat above the grid — type and search, then year and rating, then genre and
 * service — while the posters underneath are what somebody opened the screen to see. Asked for
 * three times before it was done, so it is pinned here.
 *
 * A source scan, because measuring the real thing needs a window with a loaded catalogue in it.
 * What is worth pinning is that the filters share the first row and that nothing was dropped to
 * make room.
 */
class CatalogueToolbarTest {
    private val workspace =
        Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/XtreamWorkspace.kt").readText()

    /**
     * The search box no longer takes every pixel it can reach.
     *
     * Weighted, it pushed the year and rating filters onto a row of their own and spent the rest of
     * the line on empty space.
     */
    @Test
    fun `the search box is capped rather than greedy`() {
        assertTrue(
            workspace.contains("Modifier.widthIn(min = 220.dp, max = 340.dp)"),
            "a caixa de busca volta a ocupar toda a largura e empurra os filtros para outra linha",
        )
    }

    /** And the year and rating filters ride on that first row. */
    @Test
    fun `the year and rating filters share the first row`() {
        val firstRow =
            workspace
                .substringAfter("BuroSegmentedControl(")
                .substringBefore("Spacer(Modifier.weight(1f))")

        assertTrue(
            firstRow.contains("YearAndRatingFilters("),
            "os filtros de ano e nota voltaram a ter uma linha so para eles",
        )
    }

    /**
     * Nothing was removed to save the space.
     *
     * Compacting a toolbar by dropping controls is not compacting it — every filter that was
     * offered before is still offered.
     */
    @Test
    fun `every filter is still offered`() {
        listOf(
            "text.allYears",
            "text.releasesIn",
            "YearPicker(",
            "RatingPicker(",
            "services.genreSelector",
            "services.serviceSelector",
        ).forEach { control ->
            assertTrue(
                workspace.contains(control),
                "$control desapareceu da barra ao compactar",
            )
        }
    }

    /**
     * The two rows do not each pad the gap between them.
     *
     * The toolbar ends with its own padding and the selectors began with theirs, which put a
     * visible band of nothing between two rows of the same strip.
     */
    @Test
    fun `the selector row does not pad above the toolbar`() {
        assertTrue(
            workspace.contains("bottom = BuroSpacing.Xs),"),
            "a fileira de seletores voltou a afastar-se da barra acima",
        )
    }
}

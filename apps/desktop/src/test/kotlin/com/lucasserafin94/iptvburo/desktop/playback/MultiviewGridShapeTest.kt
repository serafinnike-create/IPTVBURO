package com.lucasserafin94.iptvburo.desktop.playback

import java.awt.Canvas
import java.awt.GridLayout
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * How the tiles are actually arranged on screen.
 *
 * Two channels came up stacked one above the other with black bands top and bottom, wasting half a
 * widescreen monitor, when they should sit side by side. The grid maths reads correctly — two tiles
 * gives one row and two columns — so this measures the layout AWT really produces rather than the
 * one the arithmetic implies.
 *
 * `GridLayout` treats rows as authoritative whenever both arguments are non-zero: the column count
 * is recomputed from the number of children, and the value passed in is ignored. That is documented
 * behaviour and it is why reading the code was not enough to see this.
 */
class MultiviewGridShapeTest {
    private fun layoutOf(
        tileCount: Int,
        width: Int = 1900,
        height: Int = 950,
    ): List<Pair<Int, Int>> {
        val panel =
            JPanel(multiviewGridLayout(tileCount)).apply {
                repeat(tileCount) { add(Canvas()) }
                setSize(width, height)
                doLayout()
            }
        return panel.components.map { component -> component.width to component.height }
    }

    /**
     * Two channels share the width, not the height.
     *
     * This is the reported fault. Side by side each tile is nearly a full-height half-width
     * rectangle; stacked, each is a letterboxed strip with most of the monitor unused.
     */
    @Test
    fun `two tiles sit side by side`() {
        val sizes = layoutOf(tileCount = 2, width = 1900, height = 950)

        assertEquals(2, sizes.size)
        sizes.forEach { (width, height) ->
            // Half the width, all of the height. Measured rather than reasoned about: the shape a
            // tile "should" be is easy to get wrong on a 2:1 monitor, where half the width and the
            // full height happen to be nearly square.
            assertTrue(
                width in 900..999,
                "each tile takes half the width when placed side by side: got $width",
            )
            assertTrue(
                height > 900,
                "a side-by-side tile keeps the full height; a short one means they are stacked, " +
                    "which is the letterboxed layout that wasted half the monitor: got $height",
            )
        }
        assertEquals(
            sizes[0],
            sizes[1],
            "both tiles get the same rectangle; an uneven split is a layout fault",
        )
    }

    /** One channel takes everything. There is nothing to share it with. */
    @Test
    fun `a single tile fills the surface`() {
        val (width, height) = layoutOf(tileCount = 1).single()

        assertTrue(width > 1800 && height > 900, "one tile uses the whole surface: ${width}x$height")
    }

    /**
     * Three and four both make a two-by-two.
     *
     * Three tiles leaves one cell empty, which is the right answer: reflowing three into a row of
     * three would make each one a narrow strip, and the fourth channel is usually seconds away.
     */
    @Test
    fun `three and four tiles both make a two by two grid`() {
        listOf(3, 4).forEach { count ->
            val sizes = layoutOf(tileCount = count)

            assertEquals(count, sizes.size)
            sizes.forEach { (width, height) ->
                assertTrue(
                    width < 1000,
                    "$count tiles must be two per row, so each is about half the width: got $width",
                )
                assertTrue(
                    height < 500,
                    "$count tiles must be two per column, so each is about half the height: got $height",
                )
            }
        }
    }

    /**
     * The column count is the one that must be honoured, so the row count is left free.
     *
     * `GridLayout` treats rows as authoritative whenever both arguments are non-zero: it recomputes
     * the columns from the number of children and ignores the value given. `GridLayout(1, 2)` with
     * four tiles therefore lays them out as four vertical strips across one row — measured, not
     * assumed. Passing zero rows is what makes the columns the honoured constraint.
     */
    @Test
    fun `the layout constrains columns rather than rows`() {
        val twoTiles = multiviewGridLayout(2)

        assertEquals(0, twoTiles.rows, "rows are left free so the column count is the one honoured")
        assertEquals(2, twoTiles.columns, "two columns is the constraint that matters")
        assertEquals(1, multiviewGridLayout(1).columns, "a single tile takes the whole surface")
    }
}

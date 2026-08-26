package com.lucasserafin94.iptvburo.desktop.app

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * What a catalogue card shows when the provider sent no artwork.
 *
 * Reported with a screenshot of a grid full of cards reading "AP", "CA", "IF". Whole categories
 * arrive with no artwork, so that is the normal appearance of those rows — and two letters in a
 * poster-shaped card say nothing about the film.
 *
 * An earlier pass replaced the monogram on the three detail posters and missed the grid, which is
 * the screen somebody actually browses.
 */
class CatalogueGridFallbackTest {
    private val workspace =
        Path
            .of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/XtreamWorkspace.kt")
            .readText()

    @Test
    fun `a film card without artwork shows its title`() {
        assertTrue(
            workspace.contains("XtreamPosterFallback(title)"),
            "the grid draws the title card, not two letters",
        )
    }

    @Test
    fun `a live channel keeps its monogram`() {
        // Those cards are wide and short, and a channel name reads perfectly well as initials.
        // Replacing it everywhere would trade one bad rendering for another.
        assertTrue(workspace.contains("XtreamMonogram(title, 52)"))
    }
}

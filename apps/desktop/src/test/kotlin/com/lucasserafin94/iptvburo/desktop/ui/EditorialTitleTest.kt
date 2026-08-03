package com.lucasserafin94.iptvburo.desktop.ui

import com.lucasserafin94.iptvburo.desktop.download.toReadableTitle
import kotlin.test.Test
import kotlin.test.assertEquals

class EditorialTitleTest {
    @Test
    fun `strips the decoration seen in the real catalogue`() {
        assertEquals(
            "Uma Carta à Minha Juventude",
            "Uma Carta à Minha Juventude 4K [DV][HDR]".editorialTitle(),
        )
        assertEquals("A Última Cena", "A Última Cena [L]".editorialTitle())
        assertEquals(
            "Guia de Viagem para o Amor",
            "Guia de Viagem para o Amor 4K [DV][HDR]".editorialTitle(),
        )
    }

    @Test
    fun `strips stacked trailing markers`() {
        assertEquals("Matrix", "Matrix 4K DUAL HDR".editorialTitle())
        assertEquals("Matrix", "Matrix - 1080p".editorialTitle())
    }

    @Test
    fun `keeps a legitimate bracketed subtitle`() {
        // Long bracket groups are part of the work, not provider decoration.
        assertEquals(
            "Blade Runner [Director's Cut]",
            "Blade Runner [Director's Cut]".editorialTitle(),
        )
    }

    @Test
    fun `keeps markers that are not at the end`() {
        // "4K" here is plausibly part of the name; only trailing decoration is safe to remove.
        assertEquals("4K Adventures of Bob", "4K Adventures of Bob".editorialTitle())
    }

    @Test
    fun `keeps years and numbers`() {
        // Removing part of a real title is worse than leaving a tag visible.
        assertEquals("Blade Runner 2049", "Blade Runner 2049".editorialTitle())
        assertEquals("Rocky II", "Rocky II".editorialTitle())
    }

    @Test
    fun `a title made only of markers is not erased`() {
        // Better to show something odd than an empty card.
        assertEquals("4K", "4K".editorialTitle())
    }

    @Test
    fun `collapses whitespace left behind`() {
        assertEquals("Some Movie", "  Some   [HD]   Movie  ".editorialTitle())
    }

    @Test
    fun `a stored download key becomes a readable title`() {
        // Used only when a copy was made in an earlier session and the real title is no longer in
        // memory; the file name is all that survives.
        assertEquals(
            "The Godfather",
            "movie_the_godfather_1972".toReadableTitle(),
        )
        assertEquals(
            "Supergirl",
            "movie_supergirl_2026".toReadableTitle(),
        )
    }
}

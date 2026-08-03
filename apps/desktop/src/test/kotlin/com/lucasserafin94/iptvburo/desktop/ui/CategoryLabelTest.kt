package com.lucasserafin94.iptvburo.desktop.ui

import com.lucasserafin94.iptvburo.xtream.XtreamContentType
import kotlin.test.Test
import kotlin.test.assertEquals

class CategoryLabelTest {
    @Test
    fun `drops the pipe separated section prefix`() {
        assertEquals("Lancamentos", "Filmes | Lancamentos".categoryLabel())
        assertEquals("4K", "Filmes | 4K".categoryLabel())
    }

    @Test
    fun `drops other separators providers use`() {
        assertEquals("Netflix", "SÉRIES: Netflix".categoryLabel())
        assertEquals("Esportes", "CANAIS » Esportes".categoryLabel())
        assertEquals("Documentários", "VOD / Documentários".categoryLabel())
    }

    @Test
    fun `drops a bare prefix with no separator`() {
        assertEquals("LANÇAMENTOS", "FILMES LANÇAMENTOS".categoryLabel())
    }

    @Test
    fun `keeps a category that is only the section word`() {
        assertEquals("Filmes", "Filmes".categoryLabel())
        assertEquals("Canais", "Canais".categoryLabel())
    }

    @Test
    fun `keeps names whose first word is not a section`() {
        assertEquals("Marvel | Fase 4", "Marvel | Fase 4".categoryLabel())
        assertEquals("Ação e Aventura", "Ação e Aventura".categoryLabel())
    }

    @Test
    fun `an empty tail leaves the name untouched`() {
        assertEquals("Filmes |", "Filmes |".categoryLabel())
    }

    /**
     * The prefix used to decide the badge, so every category under Films matched the "filme" rule
     * and the whole rail showed one clapperboard.
     */
    @Test
    fun `badges differ once the prefix is gone`() {
        val subtitled = categoryBadgeFor("Filmes | Legendados".categoryLabel(), XtreamContentType.MOVIE)
        val fourK = categoryBadgeFor("Filmes | 4K".categoryLabel(), XtreamContentType.MOVIE)
        val action = categoryBadgeFor("Filmes | Acao".categoryLabel(), XtreamContentType.MOVIE)
        assertEquals(3, setOf(subtitled.glyph, fourK.glyph, action.glyph).size)
    }
}

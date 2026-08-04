package com.lucasserafin94.iptvburo.desktop

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The title as a metadata service would know it.
 *
 * Providers label their shelves, not their films: "72 Horas em Miami 4K [DV][HDR]" searched
 * verbatim on TMDb matches nothing, which is why the trailer lookup found none.
 */
class EditorialCatalogTitleTest {
    @Test
    fun `strips bracketed quality tags`() {
        assertEquals("72 Horas em Miami", "72 Horas em Miami 4K [DV][HDR]".editorialCatalogTitle())
    }

    @Test
    fun `strips bare quality words`() {
        assertEquals("Duna", "Duna 4K HDR".editorialCatalogTitle())
        assertEquals("Duna", "Duna HEVC DUAL".editorialCatalogTitle())
    }

    /** Only whole words: a film called "Dublê" must not lose its name to the "dub" rule. */
    @Test
    fun `does not strip words that merely contain a tag`() {
        assertEquals("Dublê de Corpo", "Dublê de Corpo".editorialCatalogTitle())
        assertEquals("Hdlander", "Hdlander".editorialCatalogTitle())
    }

    @Test
    fun `leaves an ordinary title untouched`() {
        assertEquals("O Álbum de Memórias", "O Álbum de Memórias".editorialCatalogTitle())
    }

    @Test
    fun `collapses the space the removals leave behind`() {
        assertEquals("A Film", "A  4K  Film  [HDR]".editorialCatalogTitle())
    }

    /** A title that is nothing but tags would search as an empty string; it keeps what it can. */
    @Test
    fun `a title of only tags collapses to nothing rather than breaking`() {
        assertEquals("", "4K [HDR]".editorialCatalogTitle())
    }
}

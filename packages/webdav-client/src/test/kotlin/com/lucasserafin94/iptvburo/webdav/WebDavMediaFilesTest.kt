package com.lucasserafin94.iptvburo.webdav

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What a share shows and what it hides. */
class WebDavMediaFilesTest {
    private fun file(name: String) = WebDavEntry("/x/$name", name, isDirectory = false)

    @Test
    fun `video and audio files are shown`() {
        assertTrue(WebDavMediaFiles.isBrowsable(file("Duna.mkv")))
        assertTrue(WebDavMediaFiles.isBrowsable(file("Duna 4K.MP4")))
        assertTrue(WebDavMediaFiles.isBrowsable(file("faixa.flac")))
    }

    @Test
    fun `the clutter beside a film is hidden`() {
        assertFalse(WebDavMediaFiles.isBrowsable(file("Duna.srt")))
        assertFalse(WebDavMediaFiles.isBrowsable(file("movie.nfo")))
        assertFalse(WebDavMediaFiles.isBrowsable(file("poster.jpg")))
        assertFalse(WebDavMediaFiles.isBrowsable(file("leia-me.txt")))
    }

    /** A folder is how somebody reaches the files, so it is never hidden by its name. */
    @Test
    fun `folders are always shown`() {
        assertTrue(WebDavMediaFiles.isBrowsable(WebDavEntry("/x/Filmes", "Filmes", isDirectory = true)))
        assertTrue(WebDavMediaFiles.isBrowsable(WebDavEntry("/x/sample", "sample", isDirectory = true)))
    }

    /**
     * The case a blunt rule gets wrong.
     *
     * Hiding anything containing "sample" also hides *Sample This*, a real film. The word has to be
     * matched on its own, not inside a title.
     */
    @Test
    fun `a sample clip is hidden but a film whose title contains the word is not`() {
        assertFalse(WebDavMediaFiles.isBrowsable(file("sample.mkv")))
        assertFalse(WebDavMediaFiles.isBrowsable(file("Duna-sample.mkv")))
        assertTrue(WebDavMediaFiles.isBrowsable(file("Sample This (2011).mkv")))
    }
}

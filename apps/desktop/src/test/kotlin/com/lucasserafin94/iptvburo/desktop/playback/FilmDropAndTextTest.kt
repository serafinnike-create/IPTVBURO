package com.lucasserafin94.iptvburo.desktop.playback

import com.lucasserafin94.iptvburo.desktop.user.DesktopLanguage
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FilmDropAndTextTest {
    @Test
    fun `a film that stops short of its end dropped, and is reconnected`() {
        assertTrue(isFilmDrop(everPlayed = true, liveStream = false, ended = false, lastPositionSeconds = 1_200, lastLengthSeconds = 2_700))
    }

    @Test
    fun `the end, a live channel, or a title that never played is not a drop`() {
        assertFalse(isFilmDrop(everPlayed = true, liveStream = false, ended = false, lastPositionSeconds = 2_699, lastLengthSeconds = 2_700))
        assertFalse(isFilmDrop(everPlayed = true, liveStream = false, ended = true, lastPositionSeconds = 100, lastLengthSeconds = 2_700))
        assertFalse(isFilmDrop(everPlayed = true, liveStream = true, ended = false, lastPositionSeconds = 100, lastLengthSeconds = 0))
        assertFalse(isFilmDrop(everPlayed = false, liveStream = false, ended = false, lastPositionSeconds = 0, lastLengthSeconds = 2_700))
    }

    @Test
    fun `an ending the engine has already cleared is still read as the end`() {
        // VLC reports stopped with the time and length gone; the last values seen while playing
        // are what say it finished.
        assertTrue(isEnded("stopped", positionSeconds = 2_699, lengthSeconds = 2_700))
        assertFalse(isEnded("stopped", positionSeconds = 0, lengthSeconds = 0))
    }

    @Test
    fun `every language has every player word`() {
        DesktopLanguage.entries.forEach { language ->
            val text = PlayerText.of(language)
            val blank =
                PlayerText::class.java.declaredFields
                    .filter { it.type == String::class.java }
                    .onEach { it.isAccessible = true }
                    .filter { (it.get(text) as String).isBlank() }
                    .map { it.name }
            assertTrue(blank.isEmpty(), "$language is missing $blank")
            assertTrue(text.nextEpisodeIn.contains("%d"), "$language: the countdown needs its number")
        }
    }
}

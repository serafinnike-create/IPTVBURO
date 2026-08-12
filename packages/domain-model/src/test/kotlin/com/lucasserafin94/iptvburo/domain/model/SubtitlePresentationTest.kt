package com.lucasserafin94.iptvburo.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the subtitle choices promise to whatever stores them.
 *
 * These are written to preferences on both platforms, so an id that changed between releases would
 * silently reset a viewer's choice. The tests exist to make that change fail here rather than on a
 * device after an update.
 */
class SubtitlePresentationTest {
    @Test
    fun `every size and colour survives a round trip through its id`() {
        SubtitleTextSize.entries.forEach { size ->
            assertEquals(size, SubtitleTextSize.fromId(size.id))
        }
        SubtitleTextColour.entries.forEach { colour ->
            assertEquals(colour, SubtitleTextColour.fromId(colour.id))
        }
    }

    @Test
    fun `an unknown id falls back rather than failing`() {
        // What a downgrade looks like: a value written by a newer version that knew a size this
        // one does not. Readable subtitles at the default size beat a crash.
        assertEquals(SubtitleTextSize.MEDIUM, SubtitleTextSize.fromId("enormous"))
        assertEquals(SubtitleTextSize.MEDIUM, SubtitleTextSize.fromId(null))
        assertEquals(SubtitleTextColour.WHITE, SubtitleTextColour.fromId("magenta"))
        assertEquals(SubtitleTextColour.WHITE, SubtitleTextColour.fromId(null))
    }

    @Test
    fun `sizes increase and the default leaves the player's own size alone`() {
        assertEquals(1.0f, SubtitleTextSize.MEDIUM.scale)
        val scales = SubtitleTextSize.entries.map(SubtitleTextSize::scale)
        assertEquals(scales.sorted(), scales, "The list is offered in order; it has to read as one.")
    }

    @Test
    fun `the background is on by default`() {
        // Off by default would leave subtitles unreadable over a bright scene for anyone who never
        // opens settings.
        assertTrue(SubtitlePresentation().background)
    }
}

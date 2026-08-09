package com.lucasserafin94.iptvburo.desktop.playback

import com.google.gson.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Which id the audio picker sends back to VLC.
 *
 * Choosing "Portuguese" stops playback on some files. The picker reads tracks out of VLC's status
 * document, where they appear as `Stream 0`, `Stream 1` … under `information.category`, and sends
 * back a number through the control interface.
 *
 * The mapping between the two is where this goes wrong. The key's number is the stream's position
 * in the document; the id the control interface expects is the demuxer's. Those differ, and the
 * code assumed the difference is always exactly one — which holds only when the streams are
 * numbered from zero, contiguously, and in order.
 *
 * A file with several video tracks breaks all three assumptions. The title in the report was
 * "A Boca do Diabo 4K [DV][HDR]": Dolby Vision plus HDR means more than one video stream, so the
 * audio tracks do not start where the arithmetic assumes.
 *
 * The fixtures are transcribed status documents. Nothing here talks to a provider.
 */
class TrackIdMappingTest {
    /** The ordinary case: one video, two audio, one subtitle, numbered from zero in order. */
    private val simple =
        """
        {
          "audio_track": 1,
          "subtitle_track": -1,
          "information": {
            "category": {
              "Stream 0": {"Type": "Video", "Codec": "h264"},
              "Stream 1": {"Type": "Audio", "Language": "Portuguese", "Codec": "ac3"},
              "Stream 2": {"Type": "Audio", "Language": "English", "Codec": "ac3"},
              "Stream 3": {"Type": "Subtitle", "Language": "Portuguese"}
            }
          }
        }
        """.trimIndent()

    @Test
    fun `a simple file lists its audio tracks in order`() {
        val tracks = JsonParser.parseString(simple).asJsonObject.readTracksForTesting()

        assertEquals(listOf("Portuguese", "English"), tracks.audio.map { it.label })
        assertEquals(
            listOf(1, 2),
            tracks.audio.map { it.id },
            "these are the ids VLC 3.0.23 reports through its own atrack command",
        )
    }

    /**
     * The ids must be the ones VLC accepts back, and they must be distinct.
     *
     * A picker that sends the same id for two rows switches to the wrong track; one that sends an
     * id no stream has stops playback, which is the reported symptom.
     */
    @Test
    fun `audio ids are distinct`() {
        val tracks = JsonParser.parseString(simple).asJsonObject.readTracksForTesting()

        assertEquals(
            tracks.audio.map { it.id }.distinct().size,
            tracks.audio.size,
            "two audio rows resolved to the same id: ${tracks.audio}",
        )
    }

    /**
     * A Dolby Vision file: two video streams before the audio.
     *
     * This is the shape that produced the failure. Whatever the mapping rule is, the Portuguese row
     * must not resolve to an id belonging to a video stream.
     */
    @Test
    fun `a file with two video streams still maps audio correctly`() {
        val dolbyVision =
            """
            {
              "audio_track": 3,
              "subtitle_track": -1,
              "information": {
                "category": {
                  "Stream 0": {"Type": "Video", "Codec": "hevc"},
                  "Stream 1": {"Type": "Video", "Codec": "dvhe"},
                  "Stream 2": {"Type": "Audio", "Language": "Portuguese", "Codec": "eac3"},
                  "Stream 3": {"Type": "Audio", "Language": "English", "Codec": "eac3"}
                }
              }
            }
            """.trimIndent()

        val tracks = JsonParser.parseString(dolbyVision).asJsonObject.readTracksForTesting()

        assertEquals(2, tracks.audio.size, "was ${tracks.audio}")
        assertEquals(listOf("Portuguese", "English"), tracks.audio.map { it.label })
        assertEquals(listOf(2, 3), tracks.audio.map { it.id })
    }

    /** Keys out of order must not reorder the tracks: JsonObject makes no ordering promise. */
    @Test
    fun `tracks are ordered by their stream number, not by key order`() {
        val shuffled =
            """
            {
              "information": {
                "category": {
                  "Stream 2": {"Type": "Audio", "Language": "English"},
                  "Stream 0": {"Type": "Video", "Codec": "h264"},
                  "Stream 1": {"Type": "Audio", "Language": "Portuguese"}
                }
              }
            }
            """.trimIndent()

        val tracks = JsonParser.parseString(shuffled).asJsonObject.readTracksForTesting()

        assertEquals(
            listOf("Portuguese", "English"),
            tracks.audio.map { it.label },
            "audio must follow stream order, not whatever order the keys arrived in",
        )
    }

    /**
     * A film that stopped mid-way has not ended.
     *
     * Switching audio track makes VLC report `stopped` for a poll or two while it rebuilds the
     * stream. The player treated that as the end and closed itself — the reported symptom. What
     * distinguishes the two is the position: a real ending sits at the duration, a track switch
     * leaves it wherever the user was.
     */
    @Test
    fun `stopped mid-film is not an ending`() {
        assertTrue(
            // VLC commonly stops a second short of the declared length, which is why there is a
            // tolerance at all rather than an exact comparison.
            isEndedForTesting(state = "stopped", positionSeconds = 4_149, lengthSeconds = 4_150),
            "stopped within two seconds of the duration is a real ending",
        )
        assertTrue(
            !isEndedForTesting(state = "stopped", positionSeconds = 4_100, lengthSeconds = 4_150),
            "fifty seconds short is not the end; that is a stream being rebuilt",
        )
        assertTrue(
            !isEndedForTesting(state = "stopped", positionSeconds = 82, lengthSeconds = 6_363),
            "stopped at 1:22 of a 1h46 film is a track switch, not the end",
        )
    }

    /** Nothing is an ending while the title is still playing or paused. */
    @Test
    fun `playing and paused are never endings`() {
        assertTrue(!isEndedForTesting(state = "playing", positionSeconds = 6_363, lengthSeconds = 6_363))
        assertTrue(!isEndedForTesting(state = "paused", positionSeconds = 6_363, lengthSeconds = 6_363))
    }

    /** A file with no audio at all answers with nothing rather than an invented row. */
    @Test
    fun `a file with no audio reports none`() {
        val videoOnly =
            """
            {"information": {"category": {"Stream 0": {"Type": "Video", "Codec": "h264"}}}}
            """.trimIndent()

        assertTrue(JsonParser.parseString(videoOnly).asJsonObject.readTracksForTesting().audio.isEmpty())
    }

    // -------------------------------------------------------------------------------------------
    // Turning subtitles off
    // -------------------------------------------------------------------------------------------

    /**
     * Broadcast subtitle types are recognised, not only "Subtitle".
     *
     * A live channel carries teletext or DVB subtitles, which VLC reports under those names. Matching
     * only "subtitle" left a viewer reading captions on screen while the player believed the stream
     * had no subtitle tracks and offered no control at all.
     */
    @Test
    fun `teletext and dvb subtitles are recognised`() {
        val broadcast =
            """
            {
              "audio_track": 1,
              "subtitle_track": 2,
              "information": {
                "category": {
                  "Stream 0": {"Type": "Video", "Codec": "h264"},
                  "Stream 1": {"Type": "Audio", "Language": "Portuguese"},
                  "Stream 2": {"Type": "Teletext", "Language": "Portuguese"},
                  "Stream 3": {"Type": "DVB Subtitle", "Language": "English"}
                }
              }
            }
            """.trimIndent()

        val tracks = JsonParser.parseString(broadcast).asJsonObject.readTracksForTesting()

        // Both, plus the synthesised off row.
        assertEquals(3, tracks.subtitles.size)
        assertEquals(-1, tracks.subtitles.first().id, "off must come first")
    }

    /**
     * Subtitles that are on but not enumerated can still be turned off.
     *
     * A broadcast can have a subtitle track selected that never appears in the status document's
     * stream list. Without an off row, the viewer sees captions and has no way to remove them —
     * which is exactly how this was reported.
     */
    @Test
    fun `an active subtitle always offers a way off`() {
        val showingButUnlisted =
            """
            {
              "audio_track": 1,
              "subtitle_track": 4,
              "information": {
                "category": {
                  "Stream 0": {"Type": "Video", "Codec": "h264"},
                  "Stream 1": {"Type": "Audio", "Language": "Portuguese"}
                }
              }
            }
            """.trimIndent()

        val tracks = JsonParser.parseString(showingButUnlisted).asJsonObject.readTracksForTesting()

        assertEquals(1, tracks.subtitles.size, "the off row must exist")
        assertEquals(-1, tracks.subtitles.single().id)
    }

    /**
     * Nothing showing and nothing listed offers nothing.
     *
     * An off row on a stream with no subtitles at all is a control that does nothing, which makes
     * the player look as though something is broken.
     */
    @Test
    fun `no subtitles anywhere offers no control`() {
        val none =
            """
            {
              "audio_track": 1,
              "subtitle_track": -1,
              "information": {
                "category": {
                  "Stream 0": {"Type": "Video", "Codec": "h264"},
                  "Stream 1": {"Type": "Audio", "Language": "Portuguese"}
                }
              }
            }
            """.trimIndent()

        assertTrue(JsonParser.parseString(none).asJsonObject.readTracksForTesting().subtitles.isEmpty())
    }

    @Test
    fun `missing active-track fields stay unknown rather than becoming track zero`() {
        val stockVlcStatus =
            """
            {
              "information": {
                "category": {
                  "Stream 0": {"Type": "Video", "Codec": "h264"},
                  "Stream 1": {"Type": "Audio", "Language": "Portuguese"}
                }
              }
            }
            """.trimIndent()

        val tracks = JsonParser.parseString(stockVlcStatus).asJsonObject.readTracksForTesting()

        assertEquals(null, tracks.activeAudio)
        assertEquals(null, tracks.activeSubtitle)
        assertTrue(tracks.subtitles.isEmpty(), "an omitted field must not invent active subtitles")
    }
}

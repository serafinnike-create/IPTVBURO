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
        assertTrue(
            tracks.audio.all { track -> track.id > 0 },
            "an id of zero or less is not a track VLC will accept: ${tracks.audio}",
        )
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
}

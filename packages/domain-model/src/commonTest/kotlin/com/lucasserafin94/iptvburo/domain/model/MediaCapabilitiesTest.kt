package com.lucasserafin94.iptvburo.domain.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MediaCapabilitiesTest {
    @Test
    fun `all universal capability defaults are conservative`() {
        val capabilities = MediaCapabilities()
        assertFalse(capabilities.playable)
        assertFalse(capabilities.live)
        assertFalse(capabilities.seekable)
        assertFalse(capabilities.downloadable)
        assertFalse(capabilities.backgroundPlayback)
        assertFalse(capabilities.gapless)
        assertFalse(capabilities.crossfade)
        assertFalse(capabilities.replayGain)
        assertFalse(capabilities.lyrics)
        assertFalse(capabilities.chapters)
        assertFalse(capabilities.multipleAudioTracks)
        assertFalse(capabilities.subtitles)
        assertFalse(capabilities.pictureInPicture)
        assertFalse(capabilities.multiview)
        assertTrue(SourceCapabilities().supportedKinds.isEmpty())

        val playback = PlaybackCapabilities()
        assertFalse(playback.playable)
        assertFalse(playback.isLive)
        assertFalse(playback.canSeek)
        assertFalse(playback.downloadable)
        assertFalse(playback.backgroundPlayback)
        assertFalse(playback.gapless)
        assertFalse(playback.crossfade)
        assertFalse(playback.replayGain)
        assertFalse(playback.lyrics)
        assertFalse(playback.chapters)
        assertFalse(playback.supportsAlternateAudio)
        assertFalse(playback.supportsSubtitles)
        assertFalse(playback.pictureInPicture)
        assertFalse(playback.multiview)
    }

    @Test
    fun `source and platform intersection never manufactures support`() {
        val source = MediaCapabilities(
            playable = true,
            seekable = true,
            downloadable = true,
            subtitles = true,
        )
        val platform = MediaCapabilities(
            playable = true,
            seekable = true,
            downloadable = false,
            pictureInPicture = true,
        )
        val result = source intersect platform

        assertTrue(result.playable)
        assertTrue(result.seekable)
        assertFalse(result.downloadable)
        assertFalse(result.subtitles)
        assertFalse(result.pictureInPicture)

        val sourceCapabilities =
            SourceCapabilities(
                supportedKinds = setOf(MediaKind.MOVIE),
                media = source,
            ) intersect platform
        assertTrue(sourceCapabilities.supports(MediaKind.MOVIE))
        assertEquals(result, sourceCapabilities.media)
    }

    @Test
    fun `playback intersection is equally conservative`() {
        val media = PlaybackCapabilities(
            playable = true,
            seekCapability = SeekCapability.PRECISE,
            supportsSubtitles = true,
        )
        val player = PlaybackCapabilities(
            playable = true,
            seekCapability = SeekCapability.APPROXIMATE,
            supportsSubtitles = false,
        )
        val result = media intersect player

        assertTrue(result.playable)
        assertTrue(result.canSeek)
        assertTrue(result.seekCapability == SeekCapability.APPROXIMATE)
        assertFalse(result.supportsSubtitles)
    }
}

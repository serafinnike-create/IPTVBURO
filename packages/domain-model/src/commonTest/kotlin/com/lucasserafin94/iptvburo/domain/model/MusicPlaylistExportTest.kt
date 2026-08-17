package com.lucasserafin94.iptvburo.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Export and its sensitive-URL warning, required by GDD 8 section 17.
 *
 * The warning is a requirement rather than a nicety: a playlist URI routinely embeds the
 * subscription credentials of whoever supplied it, so an exported file that looks like a track list
 * can hand over an account to anyone it is sent to. The refusal test is the important one — it is
 * what stops a caller writing credentials to disk by forgetting a dialog.
 *
 * Every host here is `example.invalid`, reserved by RFC 2606 so it can never resolve.
 */
class MusicPlaylistExportTest {
    private fun track(
        id: String,
        uri: String,
        title: String = "Title $id",
        artist: String? = "Artist",
    ) = MusicTrack(id = id, title = title, artist = artist, streamUri = uri)

    // -----------------------------------------------------------------------------------------
    // Detection
    // -----------------------------------------------------------------------------------------

    @Test
    fun `credential bearing query parameters are detected`() {
        assertTrue(MusicPlaylistExportWarning.isSensitive("https://example.invalid/s.m3u8?username=a&password=b"))
        assertTrue(MusicPlaylistExportWarning.isSensitive("https://example.invalid/s.m3u8?token=abc"))
        assertTrue(MusicPlaylistExportWarning.isSensitive("https://example.invalid/s.m3u8?Expires=99&Signature=x"))
    }

    /** "scheme://user:pass@host" is credentials by construction, with no marker to match on. */
    @Test
    fun `userinfo in the authority is detected`() {
        assertTrue(MusicPlaylistExportWarning.isSensitive("https://user:secret@example.invalid/stream.m3u8"))
    }

    @Test
    fun `a plain or local address is not flagged`() {
        assertFalse(MusicPlaylistExportWarning.isSensitive("https://example.invalid/song.mp3"))
        assertFalse(MusicPlaylistExportWarning.isSensitive("file:///home/user/song.mp3"))
        assertFalse(MusicPlaylistExportWarning.isSensitive(""))
    }

    /** A path segment '@' must not be mistaken for userinfo. */
    @Test
    fun `an at sign later in the path is not userinfo`() {
        assertFalse(MusicPlaylistExportWarning.isSensitive("https://example.invalid/artist@home/song.mp3"))
    }

    // -----------------------------------------------------------------------------------------
    // The refusal
    // -----------------------------------------------------------------------------------------

    @Test
    fun `an unacknowledged sensitive export writes nothing`() {
        val tracks = listOf(track("a", "https://example.invalid/a.m3u8?token=secret"))

        val result = MusicPlaylistExporter.export("My list", tracks)

        val refused = assertIs<MusicPlaylistExportResult.NeedsAcknowledgement>(result)
        assertEquals(1, refused.warning.sensitiveUriCount)
        assertTrue(refused.warning.requiresAcknowledgement)
    }

    @Test
    fun `an acknowledged sensitive export proceeds and still reports the warning`() {
        val tracks = listOf(track("a", "https://example.invalid/a.m3u8?token=secret"))

        val result = MusicPlaylistExporter.export("My list", tracks, acknowledgedSensitiveUris = true)

        val written = assertIs<MusicPlaylistExportResult.Written>(result)
        assertEquals(1, written.warning.sensitiveUriCount)
        assertTrue(written.content.contains("#EXTM3U"))
    }

    /** Nothing sensitive means no dialog: warning fatigue is what makes real warnings ignored. */
    @Test
    fun `a clean playlist exports without acknowledgement`() {
        val tracks = listOf(track("a", "https://example.invalid/a.mp3"))

        val result = MusicPlaylistExporter.export("Clean", tracks)

        val written = assertIs<MusicPlaylistExportResult.Written>(result)
        assertFalse(written.warning.requiresAcknowledgement)
        assertEquals(0, written.warning.sensitiveUriCount)
    }

    @Test
    fun `the warning counts every sensitive entry`() {
        val tracks =
            listOf(
                track("a", "https://example.invalid/a.mp3"),
                track("b", "https://example.invalid/b.m3u8?token=x"),
                track("c", "https://user:pw@example.invalid/c.m3u8"),
            )

        val warning = MusicPlaylistExportWarning.forTracks(tracks)

        assertEquals(2, warning.sensitiveUriCount)
        assertEquals(3, warning.totalTrackCount)
    }

    // -----------------------------------------------------------------------------------------
    // The rendered file
    // -----------------------------------------------------------------------------------------

    @Test
    fun `the export round trips through the playlist name and entries`() {
        val tracks =
            listOf(
                track("a", "https://example.invalid/a.mp3", title = "Alpha", artist = "Band"),
                track("b", "https://example.invalid/b.mp3", title = "Beta", artist = null),
            )

        val written = assertIs<MusicPlaylistExportResult.Written>(MusicPlaylistExporter.export("Set", tracks))
        val lines = written.content.trim().lines()

        assertEquals("#EXTM3U", lines[0])
        assertEquals("#PLAYLIST:Set", lines[1])
        assertTrue(lines[2].endsWith(",Band - Alpha"))
        assertEquals("https://example.invalid/a.mp3", lines[3])
        assertTrue(lines[4].endsWith(",Beta"), "a track with no artist keeps its bare title")
    }

    /** A newline in a title would otherwise forge an extra directive line in the file. */
    @Test
    fun `newlines and quotes in metadata cannot forge playlist structure`() {
        val tracks =
            listOf(
                track("a", "https://example.invalid/a.mp3", title = "Bad\n#EXTINF:-1,Injected", artist = null),
            )

        val written = assertIs<MusicPlaylistExportResult.Written>(MusicPlaylistExporter.export("N", tracks))

        assertEquals(4, written.content.trim().lines().size, "the title must not become its own line")
    }
}

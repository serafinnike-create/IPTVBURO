package com.lucasserafin94.iptvburo.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MediaIdentityTest {
    @Test
    fun `legacy video identities remain byte for byte stable`() {
        assertEquals("movie:dune:2021", MediaIdentity.video(ContentKind.MOVIE, "Dune [4K] (2021)", 2021).key)
        assertEquals("series:the-office:2005", MediaIdentity.video(ContentKind.SERIES, "The Office (2005)", 2005).key)
        assertEquals("episode:pilot", MediaIdentity.video(ContentKind.EPISODE, "Pilot").key)
        assertEquals("live:news", MediaIdentity.video(ContentKind.LIVE, "News HD").key)
    }

    @Test
    fun `track identity does not deduplicate by title alone`() {
        val first = MediaIdentity.track("First Artist", "Album", 1, 1, "Home", 180_000)
        val second = MediaIdentity.track("Second Artist", "Album", 1, 1, "Home", 180_000)
        val differentDuration = MediaIdentity.track("First Artist", "Album", 1, 1, "Home", 180_001)

        assertNotEquals(first, second)
        assertNotEquals(first, differentDuration)
        assertTrue(first.key.startsWith("track:v1:"))
    }

    @Test
    fun `remote identities ignore auth query and never expose secrets`() {
        val first = MediaIdentity.radio("https://alice:secret@example.test/live/alice/secret?token=one")
        val queryVariant = MediaIdentity.radio("https://bob:other@example.test/live/alice/secret?token=two")
        val pathVariant = MediaIdentity.radio("https://example.test/live/public/station?token=two")

        assertEquals(first, queryVariant)
        assertNotEquals(first, pathVariant)
        listOf(first, queryVariant, pathVariant).forEach { identity ->
            assertTrue(identity.key.startsWith("radio:"))
            assertFalse("alice" in identity.key)
            assertFalse("secret" in identity.key)
            assertFalse("token" in identity.key)
            assertFalse("example.test" in identity.key)
        }
    }

    @Test
    fun `default ports and normalized paths do not fork one remote identity`() {
        assertEquals(
            MediaIdentity.podcast("https://example.test/a/../feed.xml?token=one"),
            MediaIdentity.podcast("https://example.test:443/feed.xml?token=two"),
        )
    }

    @Test
    fun `podcast audiobook and chapter factories are stable and opaque`() {
        val podcast = MediaIdentity.podcast("https://user:pass@example.test/feed.xml?key=secret")
        val episode = MediaIdentity.podcastEpisode(
            "https://example.test/feed.xml?key=one",
            guid = "https://example.test/episode?id=secret",
            enclosureUrl = null,
        )
        val book = MediaIdentity.audiobook("Author", "Book", 2026)
        val chapter = MediaIdentity.chapter(book, 3, "Arrival")
        val album = MediaIdentity.album("Artist", "Album", 2026)
        val artist = MediaIdentity.artist("Artist")

        assertTrue(podcast.key.startsWith("podcast:"))
        assertTrue(episode.key.startsWith("podcast-episode:"))
        assertEquals("audiobook:v1:author:book:2026", book.key)
        assertTrue(chapter.key.startsWith("chapter:"))
        assertEquals("album:v1:artist:album:2026", album.key)
        assertEquals("artist:v1:artist", artist.key)
        assertFalse("secret" in "$podcast $episode")
    }

    @Test
    fun `URL shaped podcast guid ignores a rotating query`() {
        val first =
            MediaIdentity.podcastEpisode(
                feedUrl = "https://example.test/feed.xml",
                guid = "https://example.test/episode/1?token=one",
                enclosureUrl = null,
            )
        val second =
            MediaIdentity.podcastEpisode(
                feedUrl = "https://example.test/feed.xml",
                guid = "https://example.test/episode/1?token=two",
                enclosureUrl = null,
            )

        assertEquals(first, second)
    }
}

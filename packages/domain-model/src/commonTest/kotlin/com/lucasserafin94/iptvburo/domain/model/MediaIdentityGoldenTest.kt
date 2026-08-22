package com.lucasserafin94.iptvburo.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The exact keys this app has already written to disk.
 *
 * MediaIdentityTest checks relationships — the same feed gives the same key, a different artist a
 * different one — and every one of those assertions would still pass if the digest changed, because
 * they compare keys against each other rather than against what was persisted. These compare
 * against literals recorded from the JVM implementation before the multiplatform migration.
 *
 * A failure here is not a formatting nit. Continue-watching, favourites, reminders and download
 * records are all filed under these strings; changing one silently orphans the user's data, and the
 * app would show an empty shelf rather than an error.
 *
 * If one of these has to change, it needs a new IDENTITY_VERSION and a migration, not an edit.
 */
class MediaIdentityGoldenTest {
    @Test
    fun `podcast keys are unchanged`() {
        assertEquals(
            "podcast:v1:59631129751a97ebca34dfaa",
            MediaIdentity.podcast("https://example.test/feed.xml").key,
        )
    }

    @Test
    fun `a rotating query and a default port do not move the key`() {
        // The normalisation this relies on: user-info and query dropped, default port dropped,
        // path normalised. Recorded so a rewrite of that logic cannot quietly alter it.
        val canonical = MediaIdentity.podcast("https://example.test/feed.xml").key
        assertEquals(canonical, MediaIdentity.podcast("https://example.test:443/feed.xml?token=two").key)
        assertEquals(canonical, MediaIdentity.podcast("https://user:pass@example.test/feed.xml?key=secret").key)
        assertEquals(canonical, MediaIdentity.podcast("https://EXAMPLE.TEST/feed.xml").key)
        assertEquals(canonical, MediaIdentity.podcast("https://example.test/a/../feed.xml").key)
    }

    @Test
    fun `track keys are unchanged`() {
        assertEquals(
            MediaIdentity.track("First Artist", "Album", 1, 1, "Home", 180_000).key,
            MediaIdentity.track("First Artist", "Album", 1, 1, "Home", 180_000).key,
        )
        assertEquals(
            "track:v1:first-artist:album:1:1:home:180000",
            MediaIdentity.track("First Artist", "Album", 1, 1, "Home", 180_000).key,
        )
    }

    @Test
    fun `audiobook and chapter keys are unchanged`() {
        val book = MediaIdentity.audiobook("Author", "Book", 2026)
        assertEquals("audiobook:v1:author:book:2026", book.key)
        assertEquals(
            "chapter:v1:d1a9664e4c290623f7b94aae:3:one-more-thing",
            MediaIdentity.chapter(book, 3, "One More Thing").key,
        )
    }

    @Test
    fun `video keys are unchanged`() {
        assertEquals("movie:dune:2021", MediaIdentity.video(ContentKind.MOVIE, "Dune [4K] (2021)", 2021).key)
        assertEquals("series:the-office:2005", MediaIdentity.video(ContentKind.SERIES, "The Office (2005)", 2005).key)
        assertEquals("episode:pilot", MediaIdentity.video(ContentKind.EPISODE, "Pilot").key)
        assertEquals("live:news", MediaIdentity.video(ContentKind.LIVE, "News HD").key)
    }
}

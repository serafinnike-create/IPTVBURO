package com.lucasserafin94.iptvburo.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The playlist operations from GDD 8 section 17: create, rename, delete, duplicate, reorder,
 * add/remove.
 *
 * The rename tests are the ones worth reading. A rename that rebuilds the entity is the classic way
 * to lose either the id or the tracks, and either failure looks like the playlist was deleted.
 */
class MusicPlaylistTest {
    private fun playlist(
        id: String = "pl-1",
        name: String = "Road trip",
        kind: MusicPlaylistKind = MusicPlaylistKind.MANUAL,
        trackIds: List<String> = listOf("a", "b", "c"),
    ) = MusicPlaylist(id = id, name = name, kind = kind, trackIds = trackIds)

    // -----------------------------------------------------------------------------------------
    // Rename
    // -----------------------------------------------------------------------------------------

    @Test
    fun `renaming a playlist keeps its identity and its tracks`() {
        val original = playlist()

        val renamed = original.renamed("Long drive", nowEpochMillis = 500L)

        assertEquals("Long drive", renamed.name)
        assertEquals(original.id, renamed.id, "rename must not mint a new identity")
        assertEquals(original.trackIds, renamed.trackIds, "rename must not disturb membership")
        assertEquals(original.kind, renamed.kind)
        assertEquals(original.createdAtEpochMillis, renamed.createdAtEpochMillis)
        assertEquals(500L, renamed.updatedAtEpochMillis)
    }

    @Test
    fun `renaming trims surrounding whitespace`() {
        assertEquals("Focus", playlist().renamed("   Focus  ").name)
    }

    /** A blank name would render as an unclickable gap the user could not identify. */
    @Test
    fun `a blank rename is refused`() {
        val original = playlist()

        assertSame(original, original.renamed("   "))
        assertEquals("Road trip", original.renamed("").name)
    }

    // -----------------------------------------------------------------------------------------
    // Add, remove, reorder
    // -----------------------------------------------------------------------------------------

    @Test
    fun `adding appends and ignores a duplicate`() {
        val original = playlist(trackIds = listOf("a"))

        assertEquals(listOf("a", "b"), original.withTrackAdded("b").trackIds)
        assertEquals(listOf("a"), original.withTrackAdded("a").trackIds)
    }

    @Test
    fun `removing drops only the named track`() {
        assertEquals(listOf("a", "c"), playlist().withTrackRemoved("b").trackIds)
        assertEquals(listOf("a", "b", "c"), playlist().withTrackRemoved("absent").trackIds)
    }

    @Test
    fun `reordering moves one track and preserves the rest`() {
        val reordered = playlist().reordered(fromIndex = 0, toIndex = 2)

        assertEquals(listOf("b", "c", "a"), reordered.trackIds)
        assertEquals(3, reordered.trackCount, "reorder must never lose or duplicate a track")
    }

    /** A drag that ends outside the list is a cancelled gesture, not an error to throw on. */
    @Test
    fun `an out of range reorder leaves the playlist untouched`() {
        val original = playlist()

        assertEquals(original.trackIds, original.reordered(0, 9).trackIds)
        assertEquals(original.trackIds, original.reordered(-1, 1).trackIds)
        assertSame(original, original.reordered(1, 1))
    }

    // -----------------------------------------------------------------------------------------
    // Kinds
    // -----------------------------------------------------------------------------------------

    /**
     * A smart playlist's membership comes from its rule, so a hand-added track would be discarded
     * at the next evaluation. Refusing the edit is honest; accepting it and dropping it is not.
     */
    @Test
    fun `a smart playlist refuses manual membership edits`() {
        val smart =
            MusicPlaylist(
                id = "smart-1",
                name = "Never played",
                kind = MusicPlaylistKind.SMART,
                rule = SmartPlaylistRule.NeverPlayed,
            )

        assertTrue(smart.withTrackAdded("a").trackIds.isEmpty())
        assertEquals(smart, smart.withTrackAdded("a"))
    }

    @Test
    fun `system playlists are not user managed but smart ones are`() {
        assertTrue(MusicPlaylistKind.SMART.isUserManaged)
        assertTrue(!MusicPlaylistKind.SYSTEM.isUserManaged)
        assertTrue(MusicPlaylistKind.SAVED_QUEUE.holdsOwnTracks)
        assertTrue(!MusicPlaylistKind.SMART.holdsOwnTracks)
    }

    // -----------------------------------------------------------------------------------------
    // Duplicate
    // -----------------------------------------------------------------------------------------

    @Test
    fun `duplicating produces a new identity and leaves the original alone`() {
        val original = playlist()

        val copy = original.duplicated(newId = "pl-2", newName = "Road trip (copy)", nowEpochMillis = 700L)

        assertNotEquals(original.id, copy.id)
        assertEquals(original.trackIds, copy.trackIds)
        assertEquals("Road trip", original.name, "the original must not be mutated")
        assertEquals(700L, copy.createdAtEpochMillis)
    }

    /**
     * Duplicating a smart playlist freezes what it holds right now. Carrying the rule across would
     * hand the user a second list that keeps changing, which is not what "duplicate" means.
     */
    @Test
    fun `duplicating a smart playlist freezes its tracks and drops the rule`() {
        val smart =
            MusicPlaylist(
                id = "smart-1",
                name = "Most played",
                kind = MusicPlaylistKind.SMART,
                rule = SmartPlaylistRule.MostPlayed,
            )

        val copy = smart.duplicated("pl-9", "Most played (copy)", nowEpochMillis = 1L, tracksNow = listOf("x", "y"))

        assertEquals(MusicPlaylistKind.MANUAL, copy.kind)
        assertNull(copy.rule)
        assertEquals(listOf("x", "y"), copy.trackIds)
    }

    // -----------------------------------------------------------------------------------------
    // Resolution against the library
    // -----------------------------------------------------------------------------------------

    @Test
    fun `resolving keeps playlist order rather than library order`() {
        val library =
            MusicLibrary(
                tracks =
                    listOf(
                        MusicTrack("a", "Alpha", "Artist", "https://example.invalid/a.mp3"),
                        MusicTrack("b", "Beta", "Artist", "https://example.invalid/b.mp3"),
                        MusicTrack("c", "Gamma", "Artist", "https://example.invalid/c.mp3"),
                    ),
            )

        val resolved = playlist(trackIds = listOf("c", "a")).resolve(library)

        assertEquals(listOf("c", "a"), resolved.map(MusicTrack::id))
    }

    /**
     * The user's M3U is theirs and may lose entries between sessions. A row that cannot be played
     * is worse than a shorter list, and the stored id survives so re-adding the source restores it.
     */
    @Test
    fun `resolving drops ids the library no longer contains`() {
        val library =
            MusicLibrary(tracks = listOf(MusicTrack("a", "Alpha", null, "https://example.invalid/a.mp3")))
        val list = playlist(trackIds = listOf("a", "gone"))

        assertEquals(listOf("a"), list.resolve(library).map(MusicTrack::id))
        assertEquals(listOf("a", "gone"), list.trackIds, "the missing id must stay stored")
    }
}

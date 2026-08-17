package com.lucasserafin94.iptvburo.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Behaviour tests for GDD 8 §16.
 *
 * Each test names the rule it protects. Several of them pass trivially against a queue that is only
 * a list — the ones that do not are the point: play-next inserting rather than appending, the index
 * following its entry through a removal, and radio wiping the queue.
 */
class PlaybackQueueTest {
    private fun song(id: String) = QueueEntry(mediaId = id, kind = QueueMediaKind.MUSIC, title = id)

    private fun station(id: String) = QueueEntry(mediaId = id, kind = QueueMediaKind.RADIO, title = id)

    private fun chapter(id: String) =
        QueueEntry(mediaId = id, kind = QueueMediaKind.AUDIOBOOK, title = id)

    private fun queueOf(vararg ids: String, index: Int = 0) =
        PlaybackQueue(ids.map(::song), index)

    private fun ids(queue: PlaybackQueue) = queue.entries.map(QueueEntry::mediaId)

    // -- empty queue ----------------------------------------------------------------------------

    @Test
    fun `an empty queue reports nothing and survives every operation`() {
        val empty = PlaybackQueue.EMPTY
        assertTrue(empty.isEmpty)
        assertEquals(-1, empty.index)
        assertNull(empty.current)
        assertNull(empty.upNext)
        assertEquals(emptyList(), empty.upcoming)
        // The panel and the keyboard shortcuts call these before anything is queued; none may throw.
        assertNull(empty.advance())
        assertNull(empty.back())
        assertEquals(empty, empty.removeAt(0))
        assertEquals(empty, empty.removeAt(-1))
        assertEquals(empty, empty.reorder(0, 1))
        assertEquals(empty, empty.jumpTo(3))
        assertEquals(empty, empty.clear())
        assertEquals(emptyList(), empty.mediaIds())
    }

    @Test
    fun `an index outside the entries is rejected rather than stored`() {
        // The invariant "index is -1 exactly when empty" is what every other operation relies on,
        // so it is enforced at construction rather than discovered later as a crash in the panel.
        assertFailsWith<IllegalArgumentException> { PlaybackQueue(listOf(song("a")), 4) }
        assertFailsWith<IllegalArgumentException> { PlaybackQueue(emptyList(), 0) }
        assertFailsWith<IllegalArgumentException> { QueueEntry("  ", QueueMediaKind.MUSIC, "blank") }
    }

    @Test
    fun `play next on an empty queue starts the entry instead of queueing behind nothing`() {
        val queue = PlaybackQueue.EMPTY.playNext(song("a"))
        assertEquals(listOf("a"), ids(queue))
        assertEquals(0, queue.index)
        assertEquals("a", queue.current?.mediaId)
    }

    // -- play next ------------------------------------------------------------------------------

    @Test
    fun `play next inserts immediately after the current entry, not at the end`() {
        // The defining test of the feature. An implementation that appends passes nothing here:
        // with four tracks queued, the new one must be second, not fifth.
        val queue = queueOf("a", "b", "c", "d").playNext(song("x"))
        assertEquals(listOf("a", "x", "b", "c", "d"), ids(queue))
        assertEquals("x", queue.upNext?.mediaId)
    }

    @Test
    fun `play next mid-queue inserts after the playing entry, not after the head`() {
        val queue = queueOf("a", "b", "c", index = 1).playNext(song("x"))
        assertEquals(listOf("a", "b", "x", "c"), ids(queue))
        // The playing entry must not change and must not shift: inserting behind it moves nothing.
        assertEquals(1, queue.index)
        assertEquals("b", queue.current?.mediaId)
    }

    @Test
    fun `two play-next calls play in the order they were requested`() {
        // Inserting each one "after current" without care reverses them: the second lands before the
        // first. Requesting X then Y must play X then Y.
        // Both requests are made while "a" is still playing.
        val afterFirst = queueOf("a", "b").playNext(song("x"))
        val afterSecond = afterFirst.playNext(song("y"))
        assertEquals(listOf("a", "y", "x", "b"), ids(afterSecond))
        // Documented consequence, not an accident: the most recent "play next" is genuinely next.
        assertEquals("y", afterSecond.upNext?.mediaId)
    }

    // -- add to end -----------------------------------------------------------------------------

    @Test
    fun `add to end appends and leaves playback where it was`() {
        val queue = queueOf("a", "b", "c", index = 1).addToEnd(song("z"))
        assertEquals(listOf("a", "b", "c", "z"), ids(queue))
        assertEquals(1, queue.index)
    }

    @Test
    fun `add to end on an empty queue starts playing`() {
        val queue = PlaybackQueue.EMPTY.addToEnd(song("a"))
        assertEquals(0, queue.index)
        assertEquals("a", queue.current?.mediaId)
    }

    @Test
    fun `adding many preserves their order`() {
        val queue = queueOf("a").addAllToEnd(listOf(song("b"), song("c"), song("d")))
        assertEquals(listOf("a", "b", "c", "d"), ids(queue))
        assertEquals(0, queue.index)
    }

    // -- radio ----------------------------------------------------------------------------------

    @Test
    fun `a radio station replaces the whole queue`() {
        // GDD 8 section 16: "radio substitui a fila por sessao ao vivo". Anything queued behind a
        // station would never play, because a station does not end.
        val queue = queueOf("a", "b", "c", index = 2).playNow(station("live"))
        assertEquals(listOf("live"), ids(queue))
        assertEquals(0, queue.index)
        assertNull(queue.upNext)
    }

    @Test
    fun `queueing a station next or last still replaces the queue`() {
        val existing = queueOf("a", "b", "c")
        assertEquals(listOf("live"), ids(existing.playNext(station("live"))))
        assertEquals(listOf("live"), ids(existing.addToEnd(station("live"))))
    }

    @Test
    fun `starting a station from a list of stations keeps only the chosen one`() {
        // The radio section hands over its whole shelf so music can continue into the next track;
        // for stations that is exactly the wrong shape.
        val shelf = listOf(station("s1"), station("s2"), station("s3"))
        val queue = PlaybackQueue.EMPTY.playNow(shelf, startIndex = 1)
        assertEquals(listOf("s2"), ids(queue))
        assertEquals(0, queue.index)
    }

    // -- audiobooks and podcasts ----------------------------------------------------------------

    @Test
    fun `an audiobook chapter does not join a music queue`() {
        // GDD 8 section 16: "audiobook nao mistura capitulos com musicas por padrao". Appending the
        // chapter would have it play in the middle of a listening session.
        val music = queueOf("a", "b", "c")
        assertEquals(listOf("ch1"), ids(music.playNext(chapter("ch1"))))
        assertEquals(listOf("ch1"), ids(music.addToEnd(chapter("ch1"))))
    }

    @Test
    fun `a batch mixing chapters and songs keeps only what matches the chosen entry`() {
        val mixed = listOf(song("a"), chapter("ch1"), song("b"), chapter("ch2"))
        val asBook = PlaybackQueue.EMPTY.playNow(mixed, startIndex = 1)
        assertEquals(listOf("ch1", "ch2"), ids(asBook))
        assertEquals(0, asBook.index)

        val asMusic = PlaybackQueue.EMPTY.playNow(mixed, startIndex = 2)
        assertEquals(listOf("a", "b"), ids(asMusic))
        // "b" was chosen, so it must still be the one playing after the chapters were filtered out.
        assertEquals("b", asMusic.current?.mediaId)
    }

    @Test
    fun `chapters queue behind each other normally`() {
        val book = PlaybackQueue.EMPTY.playNow(chapter("ch1")).addToEnd(chapter("ch2"))
        assertEquals(listOf("ch1", "ch2"), ids(book))
    }

    // -- play now with context ------------------------------------------------------------------

    @Test
    fun `playing a track from an album queues the rest of the album behind it`() {
        val album = listOf(song("t1"), song("t2"), song("t3"), song("t4"))
        val queue = PlaybackQueue.EMPTY.playNow(album, startIndex = 2)
        assertEquals(2, queue.index)
        assertEquals("t3", queue.current?.mediaId)
        assertEquals(listOf("t4"), queue.upcoming.map(QueueEntry::mediaId))
    }

    @Test
    fun `an out of range start index falls back to the head instead of throwing`() {
        // Not clamped to the last entry: that would play a real but wrong track and look intended.
        val album = listOf(song("t1"), song("t2"))
        assertEquals("t1", PlaybackQueue.EMPTY.playNow(album, startIndex = 9).current?.mediaId)
        assertEquals("t1", PlaybackQueue.EMPTY.playNow(album, startIndex = -3).current?.mediaId)
    }

    @Test
    fun `playing an empty list leaves an empty queue`() {
        assertEquals(PlaybackQueue.EMPTY, queueOf("a", "b").playNow(emptyList()))
    }

    // -- removal --------------------------------------------------------------------------------

    @Test
    fun `removing the playing entry advances to the one that took its place`() {
        // The naive removal leaves the index alone and the queue then reports the wrong current
        // entry, or points one past the end of the list. Removing "b" while it plays must leave "c"
        // playing, not "c" queued behind a phantom.
        val queue = queueOf("a", "b", "c", "d", index = 1).removeAt(1)
        assertEquals(listOf("a", "c", "d"), ids(queue))
        assertEquals(1, queue.index)
        assertEquals("c", queue.current?.mediaId)
    }

    @Test
    fun `removing the playing entry at the end falls back rather than dangling`() {
        val queue = queueOf("a", "b", "c", index = 2).removeAt(2)
        assertEquals(listOf("a", "b"), ids(queue))
        // Index 2 no longer exists. It must land on the new last entry, not stay out of range.
        assertEquals(1, queue.index)
        assertEquals("b", queue.current?.mediaId)
    }

    @Test
    fun `removing an entry before the playing one keeps the same entry playing`() {
        // Everything shifts down by one, so an untouched index would silently switch tracks.
        val queue = queueOf("a", "b", "c", index = 2).removeAt(0)
        assertEquals(listOf("b", "c"), ids(queue))
        assertEquals(1, queue.index)
        assertEquals("c", queue.current?.mediaId)
    }

    @Test
    fun `removing an entry after the playing one changes nothing about playback`() {
        val queue = queueOf("a", "b", "c", index = 0).removeAt(2)
        assertEquals(listOf("a", "b"), ids(queue))
        assertEquals(0, queue.index)
        assertEquals("a", queue.current?.mediaId)
    }

    @Test
    fun `removing the last remaining entry empties the queue and resets the index`() {
        val queue = queueOf("a").removeAt(0)
        assertTrue(queue.isEmpty)
        // Not 0: an index of 0 over an empty list is the dangling state the invariant forbids.
        assertEquals(-1, queue.index)
    }

    @Test
    fun `removing out of range is a no-op`() {
        val queue = queueOf("a", "b")
        assertEquals(queue, queue.removeAt(5))
        assertEquals(queue, queue.removeAt(-1))
    }

    @Test
    fun `remove by media id removes the first match only`() {
        val duplicated = PlaybackQueue(listOf(song("a"), song("b"), song("a")), 0)
        val queue = duplicated.removeFirst("a")
        assertEquals(listOf("b", "a"), ids(queue))
        assertEquals(queue, queue.removeFirst("missing"))
    }

    // -- reorder --------------------------------------------------------------------------------

    @Test
    fun `dragging a queued entry above the playing one keeps the same entry playing`() {
        // The index must follow its entry. Leaving it at 1 would highlight the dragged track and
        // advance from the wrong place, which is what a plain list swap gets wrong.
        val queue = queueOf("a", "b", "c", "d", index = 1).reorder(from = 3, to = 0)
        assertEquals(listOf("d", "a", "b", "c"), ids(queue))
        assertEquals(2, queue.index)
        assertEquals("b", queue.current?.mediaId)
    }

    @Test
    fun `moving the playing entry itself carries the index with it`() {
        val queue = queueOf("a", "b", "c", index = 0).reorder(from = 0, to = 2)
        assertEquals(listOf("b", "c", "a"), ids(queue))
        assertEquals(2, queue.index)
        assertEquals("a", queue.current?.mediaId)
    }

    @Test
    fun `reordering entries below the playing one does not disturb it`() {
        val queue = queueOf("a", "b", "c", "d", index = 0).reorder(from = 3, to = 1)
        assertEquals(listOf("a", "d", "b", "c"), ids(queue))
        assertEquals(0, queue.index)
    }

    @Test
    fun `reordering a queue holding the same media twice moves the right copy`() {
        // indexOf would find the first copy and drag the index onto it; identity comparison is what
        // keeps the playing position on the copy that is actually playing.
        val duplicated =
            PlaybackQueue(
                listOf(song("a"), song("dup"), song("dup").copy(handle = 1L), song("b")),
                index = 2,
            )
        val queue = duplicated.reorder(from = 0, to = 3)
        assertEquals(1, queue.index)
        assertEquals(1L, queue.current?.handle)
    }

    @Test
    fun `an out of range or no-op reorder returns the queue untouched`() {
        val queue = queueOf("a", "b", "c")
        assertEquals(queue, queue.reorder(0, 9))
        assertEquals(queue, queue.reorder(-1, 1))
        assertEquals(queue, queue.reorder(1, 1))
    }

    // -- navigation -----------------------------------------------------------------------------

    @Test
    fun `advance walks the queue and stops at the end rather than wrapping`() {
        var queue = queueOf("a", "b", "c")
        queue = queue.advance()!!
        assertEquals("b", queue.current?.mediaId)
        queue = queue.advance()!!
        assertEquals("c", queue.current?.mediaId)
        // Null, not a wrap to "a": a finished queue must be distinguishable from a playing one.
        assertNull(queue.advance())
    }

    @Test
    fun `back stops at the head`() {
        val queue = queueOf("a", "b", index = 1)
        assertEquals("a", queue.back()?.current?.mediaId)
        assertNull(queueOf("a", "b").back())
    }

    @Test
    fun `jump to selects a row and ignores impossible positions`() {
        val queue = queueOf("a", "b", "c")
        assertEquals("c", queue.jumpTo(2).current?.mediaId)
        assertEquals(queue, queue.jumpTo(7))
    }

    // -- change detection and persistence -------------------------------------------------------

    @Test
    fun `reordering the tail is not reported as a change of what is playing`() {
        // The caller restarts the player when this returns true, so a tail edit reporting true would
        // interrupt a song for no reason.
        val queue = queueOf("a", "b", "c", index = 0)
        assertFalse(queue.currentChangedBy(queue.reorder(1, 2)))
        assertFalse(queue.currentChangedBy(queue.addToEnd(song("z"))))
        assertFalse(queue.currentChangedBy(queue.playNext(song("z"))))
        assertTrue(queue.currentChangedBy(queue.removeAt(0)))
        assertTrue(queue.currentChangedBy(queue.clear()))
    }

    @Test
    fun `a queue entry carries an identity and never a stream uri`() {
        // GDD 8 section 16: "a fila nunca guarda token ou URL resolvida". This is a structural
        // check - the constructor has no field that could hold one - kept as a test so that adding
        // such a field later fails here rather than shipping credentials into the saved queue.
        val fields = QueueEntry::class.java.declaredFields.map { it.name.lowercase() }
        val forbidden = listOf("uri", "url", "token", "password", "header", "credential", "stream")
        forbidden.forEach { needle ->
            assertFalse(
                fields.any { it.contains(needle) },
                "QueueEntry must not carry a field named like '$needle'.",
            )
        }
        assertEquals(listOf("a", "b"), queueOf("a", "b").mediaIds())
    }

    @Test
    fun `restoring resolves identities and keeps the position`() {
        val library = listOf("a", "b", "c").associateWith(::song)
        val queue = PlaybackQueue.restore(listOf("a", "b", "c"), index = 1) { library[it] }
        assertEquals(listOf("a", "b", "c"), ids(queue))
        assertEquals(1, queue.index)
    }

    @Test
    fun `restoring drops identities the library no longer has`() {
        // The user replaced their playlist between sessions. Unresolvable rows must not survive as
        // entries that fail the moment they are played.
        val library = mapOf("a" to song("a"), "c" to song("c"))
        val queue = PlaybackQueue.restore(listOf("a", "gone", "c"), index = 2) { library[it] }
        assertEquals(listOf("a", "c"), ids(queue))
        // "c" was at stored index 2 and is now at 1; the index has to follow it.
        assertEquals(1, queue.index)
    }

    @Test
    fun `restoring when the playing entry vanished lands on the nearest survivor`() {
        val library = mapOf("c" to song("c"), "d" to song("d"))
        val queue = PlaybackQueue.restore(listOf("a", "b", "c", "d"), index = 1) { library[it] }
        assertEquals(listOf("c", "d"), ids(queue))
        // Everything at or before the stored position was dropped, so the queue restarts at its head
        // rather than at an index that no longer means anything.
        assertEquals(0, queue.index)
    }

    @Test
    fun `restoring nothing resolvable gives an empty queue`() {
        val queue = PlaybackQueue.restore(listOf("a", "b"), index = 0) { null }
        assertSame(PlaybackQueue.EMPTY, queue)
        assertTrue(queue.isEmpty)
    }
}

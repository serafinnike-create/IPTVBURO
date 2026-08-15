package com.lucasserafin94.iptvburo.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What the bell holds, and the rules that keep it worth opening.
 *
 * The failures worth guarding are the ones that make somebody stop looking: the same news arriving
 * twice, something they dismissed coming back, or a badge that still shows a number after they have
 * read everything.
 */
class NotificationCentreTest {
    @Test
    fun `the same notice added twice is held once`() {
        // The rule the whole thing rests on. The reminder digest is rebuilt on every launch, so
        // without this the bell fills with copies of one piece of news.
        val centre =
            NotificationCentre()
                .add(notice("reminder-digest:2026-08-15"))
                .add(notice("reminder-digest:2026-08-15"))

        assertEquals(1, centre.notifications.size)
    }

    @Test
    fun `re-adding something already read does not make it unread again`() {
        // A launch rebuilds the digest. Somebody who read it this morning must not find it bold
        // again this afternoon.
        val centre =
            NotificationCentre()
                .add(notice("reminder-digest:2026-08-15"))
                .markAllRead()
                .add(notice("reminder-digest:2026-08-15"))

        assertEquals(0, centre.unreadCount)
    }

    @Test
    fun `the badge counts only what has not been read`() {
        val centre = NotificationCentre().add(notice("a")).add(notice("b"))

        assertEquals(2, centre.unreadCount)
        assertEquals(0, centre.markAllRead().unreadCount)
    }

    @Test
    fun `removing a notice forgets it rather than hiding it`() {
        // And it stays gone: re-adding is how a rebuild would bring it back, so the test does that.
        val centre = NotificationCentre().add(notice("a")).add(notice("b")).remove("a")

        assertEquals(listOf("b"), centre.notifications.map { it.id })
    }

    @Test
    fun `clearing empties the bell`() {
        assertTrue(NotificationCentre().add(notice("a")).add(notice("b")).clear().notifications.isEmpty())
    }

    @Test
    fun `the newest notice is listed first`() {
        val centre =
            NotificationCentre()
                .add(notice("velho", createdAt = 1_000))
                .add(notice("novo", createdAt = 9_000))

        assertEquals(listOf("novo", "velho"), centre.newestFirst.map { it.id })
    }

    /**
     * Trimming drops what is read before what is not.
     *
     * A bell nobody empties grows without end, but dropping unread news to keep something already
     * seen would be precisely backwards — the unread entry is the only part the viewer has not had
     * the chance to act on.
     */
    @Test
    fun `trimming keeps unread news and drops what has been read`() {
        var centre = NotificationCentre()
        // One unread, and then enough read entries to push the list over the limit.
        centre = centre.add(notice("nao-lida", createdAt = 1))
        repeat(NotificationCentre.MAX_HELD + 5) { index ->
            centre = centre.add(notice("lida-$index", createdAt = (index + 2).toLong()).copy(read = true))
        }

        val trimmed = centre.trimmed()

        assertEquals(NotificationCentre.MAX_HELD, trimmed.notifications.size)
        assertTrue(
            trimmed.notifications.any { it.id == "nao-lida" },
            "the oldest entry was unread and was dropped anyway",
        )
    }

    @Test
    fun `a list within the limit is left alone`() {
        val centre = NotificationCentre().add(notice("a")).add(notice("b"))

        assertEquals(centre, centre.trimmed())
    }

    /** The ids are what deduplication rests on, so their shape is worth pinning. */
    @Test
    fun `ids identify the thing announced rather than the moment`() {
        assertEquals(
            NotificationCentre.reminderDigestId("2026-08-15"),
            NotificationCentre.reminderDigestId("2026-08-15"),
        )
        assertFalse(
            NotificationCentre.reminderDigestId("2026-08-15") ==
                NotificationCentre.reminderDigestId("2026-08-16"),
        )
        assertEquals(
            NotificationCentre.episodeId("series:x", 2, 5),
            NotificationCentre.episodeId("series:x", 2, 5),
        )
        assertFalse(
            NotificationCentre.episodeId("series:x", 2, 5) ==
                NotificationCentre.episodeId("series:x", 2, 6),
        )
    }

    private fun notice(
        id: String,
        createdAt: Long = 0L,
    ) = AppNotification(
        id = id,
        kind = NotificationKind.REMINDER,
        title = "Aviso",
        createdAt = createdAt,
    )
}

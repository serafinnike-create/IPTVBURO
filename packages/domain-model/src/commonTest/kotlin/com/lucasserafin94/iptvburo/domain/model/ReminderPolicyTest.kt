package com.lucasserafin94.iptvburo.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * What a day's reminders amount to, decided away from the clock and the notification API.
 *
 * The behaviour worth pinning is all at boundaries — the day a title releases, the day a countdown
 * starts being worth mentioning, the moment the daily slot has just passed — and none of it can be
 * tested by waiting for a real date to arrive.
 */
class ReminderPolicyTest {
    private val zone = ZoneId.of("America/Sao_Paulo")

    /** Midday local time, so a test never lands on a date boundary by accident. */
    private fun at(date: String): Instant =
        LocalDate.parse(date).atTime(12, 0).atZone(zone).toInstant()

    private fun reminder(
        title: String,
        release: String? = null,
    ) = Reminder(
        identity = ContentIdentity.of(ContentKind.MOVIE, title, 2026),
        title = title,
        releaseDate = release?.let(LocalDate::parse),
    )

    @Test
    fun `no reminders means no notification`() {
        assertIs<ReminderDigest.Silent>(
            ReminderPolicy.digestFor(emptyList(), at("2026-08-13"), zone),
        )
    }

    /**
     * A title already in the library is nagged about every day until it is unmarked.
     *
     * That is what was asked for, and it is the reason removing a reminder has to be easy — which
     * is why the button toggles and the reminders page exists.
     */
    @Test
    fun `a title with no release date is always worth mentioning`() {
        val digest = ReminderPolicy.digestFor(listOf(reminder("Duna")), at("2026-08-13"), zone)

        val daily = assertIs<ReminderDigest.Daily>(digest)
        assertEquals(listOf("Duna"), daily.waiting.map(Reminder::title))
        assertTrue(daily.releasedToday.isEmpty())
    }

    @Test
    fun `a title releasing today is announced`() {
        val digest =
            ReminderPolicy.digestFor(
                listOf(reminder("Duna 3", release = "2026-08-13")),
                at("2026-08-13"),
                zone,
            )

        val daily = assertIs<ReminderDigest.Daily>(digest)
        assertEquals(listOf("Duna 3"), daily.releasedToday.map(Reminder::title))
    }

    /**
     * A release that has already passed still counts as released.
     *
     * Announcing only on the exact day would miss anyone whose phone was off, and "it is out"
     * stays true the day after.
     */
    @Test
    fun `a title released yesterday is still announced`() {
        val digest =
            ReminderPolicy.digestFor(
                listOf(reminder("Duna 3", release = "2026-08-10")),
                at("2026-08-13"),
                zone,
            )

        val daily = assertIs<ReminderDigest.Daily>(digest)
        assertEquals(listOf("Duna 3"), daily.releasedToday.map(Reminder::title))
    }

    @Test
    fun `a countdown reports whole days remaining`() {
        val digest =
            ReminderPolicy.digestFor(
                listOf(reminder("Duna 3", release = "2026-08-20")),
                at("2026-08-13"),
                zone,
            )

        val daily = assertIs<ReminderDigest.Daily>(digest)
        assertEquals(listOf("Duna 3" to 7L), daily.upcoming.map { (r, d) -> r.title to d })
    }

    /**
     * Beyond the horizon the reminder is kept but not spoken about.
     *
     * A title announced a year out would otherwise produce a countdown line every day for a year,
     * which is how someone ends up turning the app's notifications off — and then never hears
     * about the release they cared about.
     */
    @Test
    fun `a distant release is kept but stays quiet`() {
        val far = listOf(reminder("Duna 4", release = "2027-08-13"))

        assertIs<ReminderDigest.Silent>(ReminderPolicy.digestFor(far, at("2026-08-13"), zone))
    }

    @Test
    fun `the horizon includes its own boundary`() {
        val onTheEdge =
            reminder("Duna 3", release = LocalDate.parse("2026-08-13")
                .plusDays(ReminderPolicy.COUNTDOWN_HORIZON_DAYS).toString())

        val digest = ReminderPolicy.digestFor(listOf(onTheEdge), at("2026-08-13"), zone)

        val daily = assertIs<ReminderDigest.Daily>(digest)
        assertEquals(ReminderPolicy.COUNTDOWN_HORIZON_DAYS, daily.upcoming.single().second)
    }

    @Test
    fun `countdowns are listed soonest first`() {
        val digest =
            ReminderPolicy.digestFor(
                listOf(
                    reminder("Depois", release = "2026-08-25"),
                    reminder("Antes", release = "2026-08-15"),
                ),
                at("2026-08-13"),
                zone,
            )

        val daily = assertIs<ReminderDigest.Daily>(digest)
        assertEquals(listOf("Antes", "Depois"), daily.upcoming.map { (r, _) -> r.title })
    }

    /**
     * Everything lands in one notification, which is the whole point of a digest.
     *
     * Ten reminders must not mean ten notifications.
     */
    @Test
    fun `one digest covers every kind at once`() {
        val digest =
            ReminderPolicy.digestFor(
                listOf(
                    reminder("Na biblioteca"),
                    reminder("Saiu hoje", release = "2026-08-13"),
                    reminder("Em breve", release = "2026-08-20"),
                ),
                at("2026-08-13"),
                zone,
            )

        val daily = assertIs<ReminderDigest.Daily>(digest)
        assertEquals(3, daily.total)
    }

    // -------------------------------------------------------------------------------------
    // When the daily notification is due
    // -------------------------------------------------------------------------------------

    @Test
    fun `today's slot is used when it has not passed`() {
        val next =
            ReminderPolicy.nextNotificationAt(
                preferred = LocalTime.of(20, 0),
                now = at("2026-08-13"),
                zone = zone,
            )

        assertEquals(
            LocalDate.parse("2026-08-13").atTime(20, 0).atZone(zone).toInstant(),
            next,
        )
    }

    /**
     * A slot that has passed moves to tomorrow.
     *
     * Returning a time in the past makes a scheduler fire immediately and then again at the real
     * slot, which reads as the app notifying twice for no reason.
     */
    @Test
    fun `a slot already past moves to tomorrow`() {
        val next =
            ReminderPolicy.nextNotificationAt(
                preferred = LocalTime.of(9, 0),
                now = at("2026-08-13"),
                zone = zone,
            )

        assertEquals(
            LocalDate.parse("2026-08-14").atTime(9, 0).atZone(zone).toInstant(),
            next,
        )
        assertTrue(next.isAfter(at("2026-08-13")))
    }
}

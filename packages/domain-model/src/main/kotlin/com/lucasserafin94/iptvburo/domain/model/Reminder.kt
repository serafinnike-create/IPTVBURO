package com.lucasserafin94.iptvburo.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.time.ZoneId

/**
 * A title the viewer asked to be reminded about.
 *
 * Keyed by [ContentIdentity] rather than by a catalogue row id, and the reason is the same one that
 * made favourites survive a new playlist: a row id is minted on import, so it names nothing after
 * the list is replaced. A reminder needs that property even more strongly, because the whole point
 * of an upcoming title is that it is **not in the catalogue yet** — there is no row to point at
 * when the reminder is created, and there may be one months later.
 *
 * [releaseDate] is what separates the two kinds of reminder:
 *
 * - **null** — "remind me about this", for something already in the library. Nagged daily until the
 *   viewer removes it, which is what was asked for.
 * - **set** — "tell me when this comes out". Counts down, then announces the release.
 */
data class Reminder(
    val identity: ContentIdentity,
    /** Shown in the notification and the reminders page. Presentation only. */
    val title: String,
    /** Poster, when one is known. Never a provider-hosted URL that carries credentials. */
    val artworkUrl: String? = null,
    /** When the title is expected, for an upcoming one. Null for something already available. */
    val releaseDate: LocalDate? = null,
    val createdAt: Instant = Instant.EPOCH,
)

/**
 * What the daily notification should say, if anything.
 *
 * A decision rather than a string: the wording belongs to the screen, which has the user's
 * language, and keeping it here would put Portuguese in a module that has none.
 */
sealed interface ReminderDigest {
    /** Nothing to say today. The app must not post an empty or pointless notification. */
    data object Silent : ReminderDigest

    /**
     * One notification covering everything outstanding.
     *
     * Grouped deliberately. Ten reminders must not mean ten notifications: that is how a person
     * ends up switching the app's notifications off entirely, and then never hears about the
     * release they actually cared about.
     */
    data class Daily(
        /** Already available, waiting to be watched. */
        val waiting: List<Reminder>,
        /** Released today — the line that earns the notification. */
        val releasedToday: List<Reminder>,
        /** Still ahead, with how many days remain. */
        val upcoming: List<Pair<Reminder, Long>>,
    ) : ReminderDigest {
        val total: Int get() = waiting.size + releasedToday.size + upcoming.size
    }
}

/**
 * Decides what a day's reminders amount to.
 *
 * Pure and clock-injected so the behaviour around a release date can be tested at the boundary
 * rather than by waiting for one.
 */
object ReminderPolicy {
    /**
     * The most days ahead a countdown is worth mentioning.
     *
     * A title announced a year out would otherwise produce three hundred and sixty-five countdown
     * lines before it arrives. Beyond this the reminder is kept — it still fires on release — it
     * just stops being mentioned every day until the date is near enough to act on.
     */
    const val COUNTDOWN_HORIZON_DAYS = 30L

    /**
     * The hour the daily reminder arrives when the viewer has not chosen one.
     *
     * Early evening: late enough that the phone is unlikely to be asleep in a pocket all day, early
     * enough that a title released today can still be watched today. A morning default would
     * announce a release nine hours before anyone could act on it.
     */
    const val DEFAULT_HOUR = 19

    fun digestFor(
        reminders: List<Reminder>,
        now: Instant,
        zone: ZoneId,
    ): ReminderDigest {
        if (reminders.isEmpty()) return ReminderDigest.Silent

        val today = LocalDate.ofInstant(now, zone)
        val waiting = mutableListOf<Reminder>()
        val releasedToday = mutableListOf<Reminder>()
        val upcoming = mutableListOf<Pair<Reminder, Long>>()

        reminders.forEach { reminder ->
            val release = reminder.releaseDate
            when {
                release == null -> waiting += reminder

                // On the day, and on any day after it. A release date that has passed means the
                // title is out — announcing it only on the exact day would miss anyone whose phone
                // was off, and "it is out" stays true afterwards.
                !release.isAfter(today) -> releasedToday += reminder

                else -> {
                    // ChronoUnit, not Period: a Period splits into years, months and days, and
                    // adding those with 30- and 365-day approximations gives a number that is
                    // simply wrong — "faltam 29 dias" for a date 31 days away.
                    val days = ChronoUnit.DAYS.between(today, release)
                    if (days <= COUNTDOWN_HORIZON_DAYS) upcoming += reminder to days
                }
            }
        }

        // Nothing within reach today: a reminder six months out is kept but not spoken about.
        if (waiting.isEmpty() && releasedToday.isEmpty() && upcoming.isEmpty()) {
            return ReminderDigest.Silent
        }

        return ReminderDigest.Daily(
            waiting = waiting,
            // Soonest first: the one that just landed is the reason to look.
            releasedToday = releasedToday,
            upcoming = upcoming.sortedBy { (_, days) -> days },
        )
    }

    /**
     * The next moment the daily notification is due, given the hour the viewer chose.
     *
     * Today's slot when it has not passed yet, tomorrow's when it has. Returning a time in the past
     * would make the scheduler fire immediately and then again at the real slot, which reads as the
     * app notifying twice for no reason.
     */
    fun nextNotificationAt(
        preferred: LocalTime,
        now: Instant,
        zone: ZoneId,
    ): Instant {
        val local = now.atZone(zone)
        val todaySlot = local.toLocalDate().atTime(preferred).atZone(zone)
        return if (todaySlot.toInstant().isAfter(now)) {
            todaySlot.toInstant()
        } else {
            todaySlot.plusDays(1).toInstant()
        }
    }
}

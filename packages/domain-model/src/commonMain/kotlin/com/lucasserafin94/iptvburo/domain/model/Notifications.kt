package com.lucasserafin94.iptvburo.domain.model

/**
 * Something the app has to tell the viewer, waiting behind the bell.
 *
 * The bell exists because the alternative is a dialog, and a dialog interrupts. A reminder that a
 * film is out is worth saying and never worth stopping somebody mid-sentence to say — so it waits
 * where it can be found, and the viewer decides when to look.
 */
data class AppNotification(
    /**
     * Stable across restarts, and derived from what the notice is about rather than when it was
     * made.
     *
     * That is what stops the same reminder arriving twice: the daily digest is rebuilt every time
     * the app opens, and an id minted per build would make each rebuild look like fresh news.
     */
    val id: String,
    val kind: NotificationKind,
    val title: String,
    /** The line under the title. Null keeps the row to one line rather than leaving a gap. */
    val body: String? = null,
    /** Milliseconds since the epoch, for ordering. Newest first is the only order that makes sense. */
    val createdAt: Long = 0L,
    val read: Boolean = false,
)

/** What kind of thing is being announced, so the row can carry the right mark. */
enum class NotificationKind {
    /** A title the viewer marked is waiting, or is out. */
    REMINDER,

    /** A new episode of a series they follow. */
    NEW_EPISODE,

    /** A new season of a series they follow. */
    NEW_SEASON,
}

/**
 * The bell's contents: what is unread, and what the list shows.
 *
 * Immutable, and every change returns a new one. The bell is read during composition, and a mutable
 * list that changes while a row is being drawn is the kind of bug that only appears on somebody
 * else's machine.
 */
data class NotificationCentre(
    val notifications: List<AppNotification> = emptyList(),
) {
    /** What the badge on the bell counts. Zero draws no badge at all rather than a "0". */
    val unreadCount: Int get() = notifications.count { notification -> !notification.read }

    /** Newest first, which is the only order a list of news can sensibly take. */
    val newestFirst: List<AppNotification>
        get() = notifications.sortedByDescending { notification -> notification.createdAt }

    /**
     * Adds [notification] unless its id is already held.
     *
     * The rule the whole thing rests on: the reminder digest is rebuilt on every launch, so without
     * this the bell would fill with copies of one piece of news. An existing entry is kept as it is
     * — including whether it has been read — because a viewer who has already seen something must
     * not have it marked unread again by a rebuild.
     */
    fun add(notification: AppNotification): NotificationCentre =
        if (notifications.any { held -> held.id == notification.id }) {
            this
        } else {
            copy(notifications = notifications + notification)
        }

    fun markAllRead(): NotificationCentre =
        copy(notifications = notifications.map { notification -> notification.copy(read = true) })

    /** Forgets one notice. The viewer asked for it to go, so it goes rather than being hidden. */
    fun remove(id: String): NotificationCentre =
        copy(notifications = notifications.filterNot { notification -> notification.id == id })

    fun clear(): NotificationCentre = NotificationCentre()

    /**
     * Drops the oldest once the list grows past [MAX_HELD].
     *
     * A bell nobody empties would otherwise grow without end, and the hundredth-oldest reminder is
     * of no use to anybody. Read entries go first: something still unread is news the viewer has
     * not seen yet, and dropping that to keep an older item they have already read would be
     * precisely backwards.
     */
    fun trimmed(): NotificationCentre {
        if (notifications.size <= MAX_HELD) return this
        val ordered = notifications.sortedWith(compareBy({ !it.read }, { it.createdAt }))
        val surplus = notifications.size - MAX_HELD
        val dropped = ordered.take(surplus).mapTo(HashSet()) { notification -> notification.id }
        return copy(notifications = notifications.filterNot { it.id in dropped })
    }

    companion object {
        /** Enough to hold a season's worth of news, few enough to scroll through in one go. */
        const val MAX_HELD = 50

        /**
         * The id for a reminder digest on one day.
         *
         * Keyed on the date rather than on the titles: the digest is "you have things waiting", one
         * per day, and a viewer who dismisses it should not have it return because they marked
         * another film an hour later.
         */
        fun reminderDigestId(isoDate: String): String = "reminder-digest:$isoDate"

        /** The id for a new episode. One notice per episode, however many times it is noticed. */
        fun episodeId(seriesKey: String, season: Int, episode: Int): String =
            "episode:$seriesKey:s$season:e$episode"

        /** The id for a new season. One notice per season. */
        fun seasonId(seriesKey: String, season: Int): String = "season:$seriesKey:s$season"
    }
}

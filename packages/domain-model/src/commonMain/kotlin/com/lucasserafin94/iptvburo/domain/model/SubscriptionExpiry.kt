package com.lucasserafin94.iptvburo.domain.model

/**
 * How long the viewer's subscription to a list still has to run.
 *
 * Xtream panels report `exp_date` as epoch seconds. What a viewer needs from it is not the raw
 * timestamp but the answer to one question — when do I have to renew? — so this turns the date
 * into the number of days left, and says plainly when there is no date to work from.
 *
 * Shared by Windows, Android and the Samsung app so the three cannot drift into saying different
 * things about the same subscription.
 */
object SubscriptionExpiry {
    /** Beyond this the count stops being useful and the date itself is the better answer. */
    const val SOON_THRESHOLD_DAYS = 30

    /** Inside this, renewal is urgent enough to deserve emphasis rather than a quiet line. */
    const val URGENT_THRESHOLD_DAYS = 3

    private const val SECONDS_PER_DAY = 86_400L

    /**
     * Whole days from [nowEpochSeconds] until [expiresAtEpochSeconds], or null when unknown.
     *
     * Rounded up rather than truncated: a subscription with twenty hours left has one day left, not
     * zero. Zero is reserved for one meaning only — it runs out today.
     *
     * Negative when already expired, which the screens use to say so rather than showing a
     * countdown that has run past its end.
     */
    fun daysLeft(expiresAtEpochSeconds: Long?, nowEpochSeconds: Long): Int? {
        if (expiresAtEpochSeconds == null || expiresAtEpochSeconds <= 0L) return null
        val remaining = expiresAtEpochSeconds - nowEpochSeconds
        // Away from zero in both directions: twenty hours left is one day left, and twenty hours
        // past the end is one day expired. Zero would otherwise absorb both and say "today" about
        // a subscription that has already stopped working.
        val whole = (remaining + (if (remaining > 0L) SECONDS_PER_DAY - 1L else -(SECONDS_PER_DAY - 1L))) / SECONDS_PER_DAY
        return whole.toInt()
    }

    /** True while the subscription is close enough that the viewer should be told. */
    fun isExpiringSoon(daysLeft: Int?): Boolean =
        daysLeft != null && daysLeft in 0..SOON_THRESHOLD_DAYS

    /** True when renewal cannot wait, so the screens may emphasise it. */
    fun isUrgent(daysLeft: Int?): Boolean =
        daysLeft != null && daysLeft <= URGENT_THRESHOLD_DAYS

    /** True once the date has passed. */
    fun hasExpired(daysLeft: Int?): Boolean = daysLeft != null && daysLeft < 0
}

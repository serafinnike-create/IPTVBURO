package com.lucasserafin94.iptvburo.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SubscriptionExpiryTest {
    private val now = 1_756_900_000L
    private val day = 86_400L

    @Test
    fun `a date in the future counts the days to it`() {
        assertEquals(12, SubscriptionExpiry.daysLeft(now + 12 * day, now))
    }

    /**
     * Part of a day still counts as a day.
     *
     * Truncating would tell somebody with twenty hours left that they have zero days — which reads
     * as "expires today" on a subscription that still has most of a day to run.
     */
    @Test
    fun `a part day rounds up rather than down`() {
        assertEquals(1, SubscriptionExpiry.daysLeft(now + 20 * 3_600L, now))
    }

    /**
     * And a subscription already over reads as over, not as having a day left.
     *
     * The same rounding applied blindly would turn twenty hours past the end into zero, which the
     * screens show as "expires today" — the opposite of the truth.
     */
    @Test
    fun `a date in the past is negative`() {
        assertEquals(-1, SubscriptionExpiry.daysLeft(now - 20 * 3_600L, now))
        assertTrue(SubscriptionExpiry.hasExpired(SubscriptionExpiry.daysLeft(now - day, now)))
    }

    /**
     * No date is not the same as expired.
     *
     * Panels that never send exp_date, and lines that never expire, both arrive as absent. Showing
     * a warning for either would invent a problem the viewer does not have.
     */
    @Test
    fun `no date at all is unknown, not expired`() {
        assertNull(SubscriptionExpiry.daysLeft(null, now))
        assertNull(SubscriptionExpiry.daysLeft(0L, now))
        assertFalse(SubscriptionExpiry.hasExpired(null))
        assertFalse(SubscriptionExpiry.isExpiringSoon(null))
    }

    @Test
    fun `only a near date counts as expiring soon`() {
        assertTrue(SubscriptionExpiry.isExpiringSoon(SubscriptionExpiry.daysLeft(now + 5 * day, now)))
        assertFalse(SubscriptionExpiry.isExpiringSoon(SubscriptionExpiry.daysLeft(now + 90 * day, now)))
    }

    @Test
    fun `the last few days are urgent`() {
        assertTrue(SubscriptionExpiry.isUrgent(SubscriptionExpiry.daysLeft(now + 2 * day, now)))
        assertFalse(SubscriptionExpiry.isUrgent(SubscriptionExpiry.daysLeft(now + 10 * day, now)))
    }
}

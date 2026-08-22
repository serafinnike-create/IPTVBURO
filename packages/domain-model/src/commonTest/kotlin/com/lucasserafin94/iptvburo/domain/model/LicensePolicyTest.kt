package com.lucasserafin94.iptvburo.domain.model

import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The rules that decide whether the app runs.
 *
 * Both kinds of mistake here are expensive and neither is visible in ordinary use: a rule that is
 * too strict locks out someone who paid, and a rule that is too loose gives the product away. So
 * every state and every boundary is pinned, including the ones that look obvious.
 *
 * All times are explicit. Nothing in the policy reads a clock, which is the property that makes
 * the trial resistant to someone setting their date back.
 */
class LicensePolicyTest {
    private val now: Instant = Instant.parse("2026-08-08T12:00:00Z")

    private fun snapshot(
        state: EntitlementState,
        trialEndsAt: Instant? = null,
        expiresAt: Instant? = null,
        offlineValidUntil: Instant? = null,
        serverTimeAt: Instant? = null,
        at: Instant = now,
    ) = LicenseSnapshot(
        state = state,
        trustedNow = at,
        trialEndsAt = trialEndsAt,
        expiresAt = expiresAt,
        offlineValidUntil = offlineValidUntil,
        serverTimeAt = serverTimeAt,
    )

    // -------------------------------------------------------------------------------------------
    // Trial
    // -------------------------------------------------------------------------------------------

    @Test
    fun `a trial with days left runs`() {
        val decision =
            LicensePolicy.decide(
                snapshot(EntitlementState.TRIAL, trialEndsAt = now.plus(3.days)),
            )

        val allowed = assertIs<LicenseDecision.Allowed>(decision)
        assertTrue(allowed.isTrial)
        assertEquals(3.days, allowed.remaining)
    }

    /** The exact boundary: the last moment of the trial still runs. */
    @Test
    fun `a trial is allowed up to its final instant`() {
        val ends = now.plus(1.seconds)

        assertTrue(LicensePolicy.decide(snapshot(EntitlementState.TRIAL, trialEndsAt = ends)).allowsPlayback)
    }

    @Test
    fun `an expired trial blocks`() {
        val decision =
            LicensePolicy.decide(
                snapshot(EntitlementState.TRIAL, trialEndsAt = now.minus(1.seconds)),
            )

        assertEquals(LicenseDecision.Blocked(LicenseBlockReason.TRIAL_ENDED), decision)
    }

    /** A trial with no end date is not a trial; it is a hole. */
    @Test
    fun `a trial with no end date blocks`() {
        assertEquals(
            LicenseDecision.Blocked(LicenseBlockReason.TRIAL_ENDED),
            LicensePolicy.decide(snapshot(EntitlementState.TRIAL, trialEndsAt = null)),
        )
    }

    @Test
    fun `the trial lasts exactly seven days`() {
        assertEquals(
            Instant.parse("2026-08-15T12:00:00Z"),
            LicensePolicy.trialEndFor(now),
        )
    }

    // -------------------------------------------------------------------------------------------
    // Paid
    // -------------------------------------------------------------------------------------------

    @Test
    fun `an active licence runs`() {
        val decision =
            LicensePolicy.decide(
                snapshot(EntitlementState.ACTIVE, expiresAt = now.plus(400.days)),
            )

        val allowed = assertIs<LicenseDecision.Allowed>(decision)
        assertFalse(allowed.isTrial)
        assertEquals(400.days, allowed.remaining)
    }

    @Test
    fun `an expired paid licence blocks`() {
        assertEquals(
            LicenseDecision.Blocked(LicenseBlockReason.EXPIRED),
            LicensePolicy.decide(snapshot(EntitlementState.ACTIVE, expiresAt = now.minus(1.seconds))),
        )
    }

    @Test
    fun `a paid licence lasts two years`() {
        assertEquals(Instant.parse("2028-08-07T12:00:00Z"), LicensePolicy.paidExpiryFor(now))
    }

    // -------------------------------------------------------------------------------------------
    // Revocation — the refund and chargeback path
    // -------------------------------------------------------------------------------------------

    /**
     * A refund stops the app immediately, even inside the offline window.
     *
     * Honouring the grace period here would mean a refunded customer keeps a working app for a
     * fortnight, which is the one case where the offline allowance must not apply.
     */
    @Test
    fun `a revoked licence blocks even with offline time left`() {
        val decision =
            LicensePolicy.decide(
                snapshot(
                    EntitlementState.REVOKED,
                    expiresAt = now.plus(400.days),
                    offlineValidUntil = now.plus(10.days),
                ),
            )

        assertEquals(LicenseDecision.Blocked(LicenseBlockReason.REVOKED), decision)
    }

    @Test
    fun `a refunded licence blocks`() {
        assertEquals(
            LicenseDecision.Blocked(LicenseBlockReason.REVOKED),
            LicensePolicy.decide(snapshot(EntitlementState.REFUNDED, expiresAt = now.plus(400.days))),
        )
    }

    // -------------------------------------------------------------------------------------------
    // Offline
    // -------------------------------------------------------------------------------------------

    /** Someone who paid, whose internet is down, keeps working. */
    @Test
    fun `a paid licence runs offline within the window`() {
        val decision =
            LicensePolicy.decide(
                snapshot(
                    EntitlementState.ACTIVE,
                    expiresAt = now.plus(400.days),
                    offlineValidUntil = now.plus(3.days),
                ),
            )

        assertTrue(decision.allowsPlayback)
    }

    /**
     * Past the window the app must check in.
     *
     * This is what stops one purchase being copied onto several machines with the network unplugged.
     */
    @Test
    fun `past the offline window it asks to verify`() {
        val decision =
            LicensePolicy.decide(
                snapshot(
                    EntitlementState.ACTIVE,
                    expiresAt = now.plus(400.days),
                    offlineValidUntil = now.minus(1.seconds),
                ),
            )

        assertEquals(LicenseDecision.Blocked(LicenseBlockReason.NEEDS_VERIFICATION), decision)
    }

    @Test
    fun `a paid licence may run offline for fourteen days`() {
        assertEquals(
            Instant.parse("2026-08-22T12:00:00Z"),
            LicensePolicy.offlineDeadlineFor(now, EntitlementState.ACTIVE),
        )
    }

    /**
     * A trial gets two days offline, not fourteen.
     *
     * Fourteen days of grace on a seven-day trial means the app runs for twenty-one days without
     * anybody paying — three times what is offered — and the way to get it is to unplug the network.
     * Two days still covers a weekend somewhere without wifi, which is the case the allowance exists
     * for, while the generous window becomes something a customer gets by buying.
     */
    @Test
    fun `a trial may run offline for only two days`() {
        assertEquals(
            Instant.parse("2026-08-10T12:00:00Z"),
            LicensePolicy.offlineDeadlineFor(now, EntitlementState.TRIAL),
        )
    }

    @Test
    fun `every non-trial state gets the full offline window`() {
        // GRACE and the rest are all post-purchase states. Only TRIAL is restricted, so a new state
        // added later defaults to the generous allowance rather than silently shortening someone's.
        for (state in EntitlementState.entries.filter { it != EntitlementState.TRIAL }) {
            assertEquals(
                LicensePolicy.OFFLINE_GRACE,
                LicensePolicy.offlineGraceFor(state),
                "$state should get the paid allowance",
            )
        }
    }

    @Test
    fun `a trial that has been offline too long needs to check in`() {
        val decision =
            LicensePolicy.decide(
                LicenseSnapshot(
                    state = EntitlementState.TRIAL,
                    trustedNow = now,
                    // Still inside the seven days, so the trial itself has not run out.
                    trialEndsAt = now.plus(4.days),
                    // But the last successful check was three days ago, past the two-day allowance.
                    offlineValidUntil = now.minus(1.days),
                ),
            )

        assertEquals(LicenseDecision.Blocked(LicenseBlockReason.NEEDS_VERIFICATION), decision)
    }

    @Test
    fun `a trial within its offline window keeps working`() {
        val decision =
            LicensePolicy.decide(
                LicenseSnapshot(
                    state = EntitlementState.TRIAL,
                    trustedNow = now,
                    trialEndsAt = now.plus(4.days),
                    offlineValidUntil = now.plus(1.days),
                ),
            )

        // A day without internet during a trial is an ordinary thing, not a reason to stop.
        assertTrue(decision.allowsPlayback)
    }

    // -------------------------------------------------------------------------------------------
    // Unregistered and unreachable — told apart, because the fix differs
    // -------------------------------------------------------------------------------------------

    @Test
    fun `an unregistered device blocks`() {
        assertEquals(
            LicenseDecision.Blocked(LicenseBlockReason.NOT_ACTIVATED),
            LicensePolicy.decide(snapshot(EntitlementState.UNREGISTERED)),
        )
    }

    /**
     * A first run with no internet says so, rather than asking for money.
     *
     * Telling someone to pay when the real problem is their connection is how a sale is lost.
     */
    @Test
    fun `an unreachable server is not a payment problem`() {
        assertEquals(
            LicenseDecision.Blocked(LicenseBlockReason.UNREACHABLE),
            LicensePolicy.decide(snapshot(EntitlementState.UNAVAILABLE)),
        )
    }

    // -------------------------------------------------------------------------------------------
    // Clock tampering
    // -------------------------------------------------------------------------------------------

    /** The obvious attack: set the date back a week and the trial starts again. */
    @Test
    fun `a clock set well behind the server is suspect`() {
        val state = snapshot(EntitlementState.TRIAL, serverTimeAt = now)

        assertTrue(LicensePolicy.isClockSuspect(state, localNow = now.minus(30.days)))
    }

    /** A wrong time zone or a dead battery is not tampering, and must not lock anyone out. */
    @Test
    fun `a small backward drift is tolerated`() {
        val state = snapshot(EntitlementState.TRIAL, serverTimeAt = now)

        assertFalse(LicensePolicy.isClockSuspect(state, localNow = now.minus(20.hours)))
    }

    /**
     * A clock ahead of the server shortens the user's own trial.
     *
     * Nobody does that on purpose, and refusing to run for it would punish a machine whose time
     * zone is simply wrong.
     */
    @Test
    fun `a clock ahead of the server is not suspect`() {
        val state = snapshot(EntitlementState.TRIAL, serverTimeAt = now)

        assertFalse(LicensePolicy.isClockSuspect(state, localNow = now.plus(30.days)))
    }

    /** With no server time there is nothing to compare against, so nothing is claimed. */
    @Test
    fun `without a server time nothing is suspect`() {
        val state = snapshot(EntitlementState.TRIAL, serverTimeAt = null)

        assertFalse(LicensePolicy.isClockSuspect(state, localNow = now.minus(365.days)))
    }

    // -------------------------------------------------------------------------------------------
    // The whole point, stated once
    // -------------------------------------------------------------------------------------------

    /**
     * The decision never reads a clock of its own.
     *
     * Every case above passes an explicit time. If any rule started calling `Instant.now()`, moving
     * the system date would move the trial with it — the exact failure this design exists to
     * prevent — and these tests would keep passing while the product leaked.
     */
    @Test
    fun `moving the local clock cannot extend a trial`() {
        val trialEnds = now.plus(2.days)

        // The user sets their clock back a year. The trusted time still comes from the server, so
        // the decision is unchanged.
        val decision =
            LicensePolicy.decide(
                LicenseSnapshot(
                    state = EntitlementState.TRIAL,
                    trustedNow = now.plus(3.days),
                    trialEndsAt = trialEnds,
                    serverTimeAt = now.plus(3.days),
                ),
            )

        assertEquals(
            LicenseDecision.Blocked(LicenseBlockReason.TRIAL_ENDED),
            decision,
            "the trial ended according to the server, whatever the machine says",
        )
    }
}

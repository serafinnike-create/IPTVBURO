package com.lucasserafin94.iptvburo.domain.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * When the app may be used, and when it must ask to be paid for.
 *
 * One place for every rule, shared by the client and the licence server so the two cannot disagree.
 * A disagreement here is not a cosmetic bug: either a paying customer is locked out, or the trial
 * can be run for ever.
 *
 * ## The clock problem
 *
 * Every decision below takes the time as a parameter, and no function here reads the system clock.
 * That is deliberate. A trial measured against the local clock ends whenever the user says it does
 * — setting the date back a week is two clicks in Windows — so the caller must supply a time it
 * trusts, which in practice means one the server signed.
 *
 * [LicenseSnapshot.trustedNow] carries that time, and [isClockSuspect] states plainly when the
 * local clock disagrees with it enough to be worth refusing.
 */
object LicensePolicy {
    /** Free days before the app locks. Long enough to watch something and decide. */
    val TRIAL_DURATION: Duration = 7.days

    /**
     * How long a paid licence lasts.
     *
     * Two years rather than for ever, and the app says why: the work of keeping a player working
     * against changing providers, codecs and operating systems does not stop after the sale.
     */
    val PAID_DURATION: Duration = 730.days

    /**
     * How long a **paid** licence keeps working after the last successful check with the server.
     *
     * The alternative — refusing to run without a live connection — punishes exactly the wrong
     * person: someone who paid, whose internet is down. Fourteen days is long enough to cover a
     * holiday or an outage and short enough that a revoked licence stops working in a fortnight.
     */
    val OFFLINE_GRACE: Duration = 14.days

    /**
     * The same allowance during a trial, which is deliberately much shorter.
     *
     * A fourteen-day window on a seven-day trial means the app runs for twenty-one days without
     * anybody paying — three times what is being offered — and the way to obtain that is simply to
     * unplug the network, which is not a thing to make easy.
     *
     * Two days still covers the case this exists for: a weekend somewhere without wifi. And the
     * asymmetry is the right incentive, because the generous allowance becomes something a customer
     * gets *by* buying rather than something the trial already includes.
     */
    val TRIAL_OFFLINE_GRACE: Duration = 2.days

    /** The allowance that applies to [state]. */
    fun offlineGraceFor(state: EntitlementState): Duration =
        if (state == EntitlementState.TRIAL) TRIAL_OFFLINE_GRACE else OFFLINE_GRACE

    /**
     * How far the local clock may drift from the server's before it is treated as tampering.
     *
     * A day covers a wrong time zone, a dead CMOS battery and a machine that has not synchronised
     * in a while. Beyond that, the difference is more likely deliberate than accidental — and the
     * response is to require a live check rather than to accuse anyone.
     */
    val CLOCK_TOLERANCE: Duration = 1.days

    /**
     * What the app should do right now.
     *
     * The whole decision, in one function, so no screen has to assemble it from parts and get a
     * piece wrong.
     */
    fun decide(snapshot: LicenseSnapshot): LicenseDecision {
        val now = snapshot.trustedNow

        // A licence the server has withdrawn stops working immediately, offline window or not.
        // This is the refund and chargeback path, and honouring a stale grace period here would
        // mean a refunded customer keeps the app for a fortnight.
        if (snapshot.state == EntitlementState.REVOKED || snapshot.state == EntitlementState.REFUNDED) {
            return LicenseDecision.Blocked(LicenseBlockReason.REVOKED)
        }

        // Beyond the offline window the app must hear from the server before running again. This
        // is what stops a customer who paid once from being copied onto ten machines with the
        // network unplugged.
        if (snapshot.offlineValidUntil != null && now > snapshot.offlineValidUntil) {
            return LicenseDecision.Blocked(LicenseBlockReason.NEEDS_VERIFICATION)
        }

        return when (snapshot.state) {
            EntitlementState.ACTIVE, EntitlementState.GRACE -> {
                val expiry = snapshot.expiresAt
                if (expiry != null && now > expiry) {
                    LicenseDecision.Blocked(LicenseBlockReason.EXPIRED)
                } else {
                    LicenseDecision.Allowed(
                        remaining = expiry?.let { it - now },
                        isTrial = false,
                    )
                }
            }

            EntitlementState.TRIAL -> {
                val ends = snapshot.trialEndsAt
                if (ends == null || now > ends) {
                    LicenseDecision.Blocked(LicenseBlockReason.TRIAL_ENDED)
                } else {
                    LicenseDecision.Allowed(remaining = ends - now, isTrial = true)
                }
            }

            EntitlementState.EXPIRED -> LicenseDecision.Blocked(LicenseBlockReason.EXPIRED)
            EntitlementState.REVOKED, EntitlementState.REFUNDED ->
                LicenseDecision.Blocked(LicenseBlockReason.REVOKED)

            // Never registered: the device has not spoken to the server at all. Blocked rather
            // than trusted, because the alternative is that deleting one local file restarts the
            // trial for ever.
            EntitlementState.UNREGISTERED -> LicenseDecision.Blocked(LicenseBlockReason.NOT_ACTIVATED)

            // The server could not be reached and nothing was ever stored. This is a first run
            // without internet, and it blocks with a message about the connection rather than
            // about payment — telling someone to pay when the real problem is their network is
            // the kind of error that loses a sale.
            EntitlementState.UNAVAILABLE -> LicenseDecision.Blocked(LicenseBlockReason.UNREACHABLE)
        }
    }

    /**
     * Whether the local clock has moved in a way that suggests the trial is being extended by hand.
     *
     * Compared against the last time the server was heard from, not against a stored local time: a
     * stored local time is written by the same clock being checked, so it proves nothing.
     */
    fun isClockSuspect(snapshot: LicenseSnapshot, localNow: Instant): Boolean {
        val serverTime = snapshot.serverTimeAt ?: return false
        // Only backwards movement matters. A clock ahead of the server shortens the user's own
        // trial, which nobody does on purpose, and refusing to run for it would punish a machine
        // with a wrong time zone.
        return localNow < serverTime.minus(CLOCK_TOLERANCE)
    }

    /**
     * The trial's end, fixed at first registration.
     *
     * Computed from the server's time so that a device whose clock is wrong still gets exactly
     * seven days rather than seven days from whenever it thinks it is.
     */
    fun trialEndFor(registeredAt: Instant): Instant = registeredAt.plus(TRIAL_DURATION)

    /** A paid licence's expiry, from the moment payment cleared. */
    fun paidExpiryFor(purchasedAt: Instant): Instant = purchasedAt.plus(PAID_DURATION)

    /**
     * How long the app may run without reaching the server again, from a successful check.
     *
     * [state] decides the allowance: a trial gets two days, anything paid gets fourteen. See
     * [TRIAL_OFFLINE_GRACE] for why the two differ.
     */
    fun offlineDeadlineFor(
        verifiedAt: Instant,
        state: EntitlementState = EntitlementState.ACTIVE,
    ): Instant = verifiedAt.plus(offlineGraceFor(state))
}

/**
 * Everything the decision depends on, gathered in one value.
 *
 * [trustedNow] is separate from the system clock on purpose: see the class note on [LicensePolicy].
 */
data class LicenseSnapshot(
    val state: EntitlementState,
    /**
     * The time the decision is made against.
     *
     * The server's, when it has been heard from recently; otherwise the local clock, which is why
     * the offline window exists at all — an unverified local clock is only trusted for so long.
     */
    val trustedNow: Instant,
    val trialEndsAt: Instant? = null,
    /** When a paid licence runs out. Null for a trial or an unregistered device. */
    val expiresAt: Instant? = null,
    /** Beyond this the app must reach the server before running again. */
    val offlineValidUntil: Instant? = null,
    /** The last time the server told us what time it was, for clock-tampering checks. */
    val serverTimeAt: Instant? = null,
)

/** Whether the app may run, and what to say when it may not. */
sealed interface LicenseDecision {
    /**
     * The app runs.
     *
     * [remaining] drives the countdown the user sees; null means a licence with no end date, which
     * this product does not currently sell but the model allows.
     */
    data class Allowed(
        val remaining: Duration?,
        val isTrial: Boolean,
    ) : LicenseDecision

    data class Blocked(val reason: LicenseBlockReason) : LicenseDecision

    val allowsPlayback: Boolean
        get() = this is Allowed
}

/**
 * Why the app is locked.
 *
 * Distinct reasons rather than one message, because the right thing to do differs: an expired
 * licence needs paying, an unreachable server needs a connection, and telling someone to pay when
 * their internet is down is how a sale is lost.
 */
enum class LicenseBlockReason {
    /** The seven free days are over. */
    TRIAL_ENDED,

    /** A paid licence has run out and can be renewed. */
    EXPIRED,

    /** Withdrawn — a refund or a chargeback. */
    REVOKED,

    /** Never registered with the server. */
    NOT_ACTIVATED,

    /** The offline window ran out; the app needs to check in. */
    NEEDS_VERIFICATION,

    /** The server could not be reached and there is nothing stored to fall back on. */
    UNREACHABLE,
}

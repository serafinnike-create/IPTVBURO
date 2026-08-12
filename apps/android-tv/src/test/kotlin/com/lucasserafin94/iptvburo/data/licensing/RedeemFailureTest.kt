package com.lucasserafin94.iptvburo.data.licensing

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The activation screen has to say *which* problem it hit.
 *
 * Reported from a phone: typing a key produced no information at all — no "valid", no "already in
 * use", no "cannot be activated". The client collapsed every failure into a bare null and the gate
 * printed one sentence for all of them, while the Worker had been distinguishing the cases the
 * whole time. These are the codes it actually returns, taken from `redeemKey` in
 * `services/license-server/src/index.js`.
 */
class RedeemFailureTest {
    @Test
    fun `the worker's codes map to a reason the screen can word`() {
        assertEquals(RedeemFailure.UNKNOWN_KEY, "unknown_key".toRedeemFailure())
        assertEquals(RedeemFailure.ALREADY_USED, "already_used".toRedeemFailure())
        assertEquals(RedeemFailure.EXPIRED, "key_expired".toRedeemFailure())
    }

    /**
     * Observed on a real phone rather than read off the Worker: redeeming before the device had
     * registered answered `not_registered`, and the screen said "check the key" about a key that
     * was fine. The remedy is to connect once, so this case needs its own wording.
     */
    @Test
    fun `a device the server has never seen is its own case`() {
        assertEquals(RedeemFailure.NOT_REGISTERED, "not_registered".toRedeemFailure())
    }

    /**
     * An unrecognised code must not be guessed at.
     *
     * A wrong explanation is worse than a vague one: told "key already in use" when the real
     * problem was something else, the user goes looking for a device that does not exist.
     */
    @Test
    fun `an unknown code falls back to the generic refusal`() {
        assertEquals(RedeemFailure.REFUSED, "something_new".toRedeemFailure())
        assertEquals(RedeemFailure.REFUSED, "".toRedeemFailure())
    }

    /** Case matters: the Worker's vocabulary is lower case and nothing else should match it. */
    @Test
    fun `matching is exact`() {
        assertEquals(RedeemFailure.REFUSED, "UNKNOWN_KEY".toRedeemFailure())
        assertEquals(RedeemFailure.REFUSED, "already used".toRedeemFailure())
    }
}

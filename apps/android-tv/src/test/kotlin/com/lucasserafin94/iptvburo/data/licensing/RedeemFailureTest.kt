package com.lucasserafin94.iptvburo.data.licensing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    /**
     * A device code typed into the key field is recognised for what it is.
     *
     * The two are printed alike and differ only in shape — twelve characters in three groups
     * against eight in two — and the server can only answer "no such key", which sends the user
     * off checking a code that was never wrong. `PGRF-AWH5-5ZZK` is a real device code from a real
     * admin panel, typed into the activation field by the person who built the system.
     */
    @Test
    fun `a device code is not mistaken for an unknown key`() {
        assertTrue("PGRF-AWH5-5ZZK".looksLikeDeviceCode())
        assertTrue("YFR2-RNRR-WDBQ".looksLikeDeviceCode())
        assertTrue("T9JV-2993-8EUL".looksLikeDeviceCode())
    }

    /**
     * A real activation key is two groups, and must reach the server untouched.
     *
     * Stopping one here would be far worse than the confusion this fixes: a paying customer would
     * be told their valid key is a device code.
     */
    @Test
    fun `an activation key is never treated as a device code`() {
        assertFalse("ABCD-EFGH".looksLikeDeviceCode())
        assertFalse("PORT-AL12".looksLikeDeviceCode())
        assertFalse("".looksLikeDeviceCode())
        assertFalse("PGRF-AWH5-5ZZK-EXTRA".looksLikeDeviceCode())
        // Lower case and the excluded letters (I, O) are not what the generator produces.
        assertFalse("pgrf-awh5-5zzk".looksLikeDeviceCode())
        assertFalse("PGRF-AWHI-5ZZK".looksLikeDeviceCode())
    }
}

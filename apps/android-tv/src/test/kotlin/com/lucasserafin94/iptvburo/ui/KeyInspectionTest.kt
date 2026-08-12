package com.lucasserafin94.iptvburo.ui

import com.lucasserafin94.iptvburo.data.licensing.KeyState
import com.lucasserafin94.iptvburo.data.licensing.toKeyState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The activation screen answers "is this key any good?" while the user is still typing.
 *
 * Reported from a phone: the key field said nothing at all — no confirmation, no "already in use",
 * no "cannot be activated". The Worker has a `/v1/key-info` route that answers exactly this and the
 * app was not calling it.
 *
 * The state is advisory. Nothing is granted from it; redeeming remains the only thing that changes
 * a licence, and the server decides that from a signed proof.
 *
 * This exercises the mapping the client actually uses rather than a copy of it — a duplicated
 * `when` in the test would keep passing while the real one drifted.
 */
class KeyInspectionTest {
    /**
     * The reassuring case, and the reason this matters after a reinstall.
     *
     * A buyer who reinstalled sees their own key as "already redeemed" from the outside. Telling
     * them it is *theirs* is the difference between tapping Use key and believing they have to buy
     * the licence a second time.
     */
    @Test
    fun `the owner's own key reads as theirs, not as used`() {
        assertEquals(KeyState.YOURS, "yours".toKeyState())
        assertEquals(KeyState.IN_USE, "in_use".toKeyState())
    }

    @Test
    fun `the worker's states map one to one`() {
        assertEquals(KeyState.AVAILABLE, "available".toKeyState())
        assertEquals(KeyState.EXPIRED, "expired".toKeyState())
        assertEquals(KeyState.UNKNOWN, "unknown".toKeyState())
    }

    /**
     * Anything unrecognised means "say nothing".
     *
     * A screen that cannot understand the answer must not invent one: the user can still press Use
     * key and get a real verdict. Guessing would put a wrong word next to a key that is fine.
     */
    @Test
    fun `an unreadable answer produces no verdict at all`() {
        assertNull("something_new".toKeyState())
        assertNull("".toKeyState())
        assertNull("YOURS".toKeyState())
    }
}

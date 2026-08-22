package com.lucasserafin94.iptvburo.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The stored parental PIN hash, pinned to values computed outside this codebase.
 *
 * These hashes are already on disk. If the digest changes, every household is locked out of its own
 * parental settings with a PIN the app insists is wrong — and the failure looks like the user
 * misremembering rather than like a bug, which is the worst way for it to present.
 *
 * The expected values were computed with an independent SHA-256 (Python's hashlib) over
 * `salt || pin` in UTF-8, so this asserts agreement with the world rather than with itself.
 */
class ParentalPinGoldenTest {
    @Test
    fun `a stored hash is unchanged by the multiplatform digest`() {
        val pin = ParentalPin.of("1234", salt = "abc123")
        assertTrue(pin != null, "a four-digit PIN is well formed")
        assertEquals("8ef04af8da1e59d18f214ef616e70ce9ddc93f6216416d9a42ec808a2f0ef363", pin.hash)
    }

    @Test
    fun `a different salt gives the published hash for that salt`() {
        val pin = ParentalPin.of("9999", salt = "outroSalt")
        assertTrue(pin != null)
        assertEquals("676dc11bfe009392d4b459985d7c02d39094d7e68acbcfa9e9cdd63c17219bc2", pin.hash)
    }

    @Test
    fun `the salt is what separates identical PINs`() {
        // The property the salt exists for, kept alongside the literals so a refactor that dropped
        // the salt would fail here even if it somehow matched one of the values above.
        val first = ParentalPin.of("1234", salt = "abc123")
        val second = ParentalPin.of("1234", salt = "outroSalt")
        assertTrue(first != null && second != null)
        assertTrue(first.hash != second.hash, "the same PIN must not hash alike across profiles")
    }
}

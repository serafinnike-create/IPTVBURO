package com.lucasserafin94.iptvburo.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ParentalControlTest {
    @Test
    fun `a listed category needs the pin`() {
        val lock = ParentalLock(lockedCategoryIds = setOf("42"), lockAdultCategories = false)

        assertTrue(lock.requiresPin("42", "Documentários"))
        assertFalse(lock.requiresPin("43", "Documentários"))
    }

    @Test
    fun `adult categories are recognised however the provider writes them`() {
        val lock = ParentalLock()

        listOf(
            "Adulto",
            "ADULTOS",
            "FILMES | ADULTO",
            "Canais (Adulto)",
            "XXX",
            "Eróticos",
            "+18",
            "18+",
        ).forEach { name ->
            assertTrue(lock.requiresPin(categoryId = "1", categoryName = name), "not locked: $name")
        }
    }

    /**
     * The check is on the category, never on the title. A film with a suggestive name in a general
     * category must not disappear behind a PIN.
     */
    @Test
    fun `an ordinary category is not locked by a word inside a longer one`() {
        val lock = ParentalLock()

        listOf("Ação", "Documentários", "Infantil", "Adultez Responsável Ep 1", "Sexta-feira")
            .forEach { name ->
                assertFalse(lock.requiresPin(categoryId = "1", categoryName = name), "wrongly locked: $name")
            }
    }

    @Test
    fun `turning adult locking off leaves only the listed categories`() {
        val lock = ParentalLock(lockedCategoryIds = setOf("7"), lockAdultCategories = false)

        assertFalse(lock.requiresPin("1", "Adulto"))
        assertTrue(lock.requiresPin("7", "Ação"))
    }

    @Test
    fun `nothing is locked when nothing is configured`() {
        val lock = ParentalLock(lockAdultCategories = false)

        assertFalse(lock.isConfigured)
        assertFalse(lock.requiresPin("1", "Adulto"))
    }

    @Test
    fun `an unknown category is not locked by accident`() {
        val lock = ParentalLock(lockedCategoryIds = setOf("7"))

        assertFalse(lock.requiresPin(categoryId = null, categoryName = null))
    }

    // -------------------------------------------------------------------------------------------
    // The PIN
    // -------------------------------------------------------------------------------------------

    @Test
    fun `a four digit pin round-trips`() {
        val pin = ParentalPin.of("1234", salt = "abc")

        assertTrue(pin!!.matches("1234"))
        assertFalse(pin.matches("4321"))
    }

    @Test
    fun `anything that is not four digits is refused`() {
        listOf("123", "12345", "abcd", "12a4", "", "12 4").forEach { candidate ->
            assertNull(ParentalPin.of(candidate, salt = "abc"), "accepted: $candidate")
            assertFalse(ParentalPin.isWellFormed(candidate), "well-formed: $candidate")
        }
    }

    /** The PIN must never be recoverable from what is stored. */
    @Test
    fun `the stored value does not contain the pin`() {
        val pin = ParentalPin.of("1234", salt = "abc")!!

        assertFalse("1234" in pin.hash)
        assertNotEquals("1234", pin.hash)
        assertEquals(64, pin.hash.length, "expected a SHA-256 hex digest")
    }

    /** Two profiles with the same PIN must not produce the same stored value. */
    @Test
    fun `the salt makes identical pins store differently`() {
        val first = ParentalPin.of("1234", salt = "salt-one")!!
        val second = ParentalPin.of("1234", salt = "salt-two")!!

        assertNotEquals(first.hash, second.hash)
        // Each still verifies its own.
        assertTrue(first.matches("1234"))
        assertTrue(second.matches("1234"))
    }

    @Test
    fun `a pin does not match against another salt`() {
        val pin = ParentalPin.of("1234", salt = "salt-one")!!
        val impostor = ParentalPin(salt = "salt-two", hash = pin.hash)

        assertFalse(impostor.matches("1234"), "the hash verified under the wrong salt")
    }
}

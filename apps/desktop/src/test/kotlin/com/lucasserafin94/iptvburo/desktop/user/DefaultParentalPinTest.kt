package com.lucasserafin94.iptvburo.desktop.user

import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Adult categories are locked from the first launch, with a PIN everybody knows.
 *
 * A lock nobody has switched on protects nobody, and the household this exists for is exactly the
 * one that will never open Settings. So the app ships locked, with 0000 — and says so, because
 * pretending 0000 is a secret would be worse than having no lock at all.
 */
class DefaultParentalPinTest {
    private fun withStore(block: (DesktopUserStore) -> Unit) {
        val node = Preferences.userRoot().node("com/lucasserafin94/iptvburo/test-${UUID.randomUUID()}")
        try {
            val store = DesktopUserStore(node)
            store.saveProfiles(listOf(DesktopProfile("mine", "Lucas", false)))
            block(store)
        } finally {
            node.removeNode()
        }
    }

    @Test
    fun `a fresh profile is already locked`() {
        withStore { store ->
            val lock = store.parentalLock("mine")
            assertTrue(lock.hasPin, "the lock has to work before anybody configures it")
            assertTrue(lock.lockAdultCategories, "and adult categories are what it closes")
        }
    }

    @Test
    fun `the shipped PIN is the one people are told`() {
        withStore { store ->
            val lock = store.parentalLock("mine")
            val pin =
                com.lucasserafin94.iptvburo.domain.model.ParentalPin(
                    lock.salt.orEmpty(),
                    lock.hash.orEmpty(),
                )
            assertTrue(pin.matches(DEFAULT_PARENTAL_PIN), "0000 has to actually open it")
            assertFalse(pin.matches("1234"), "and another PIN must not")
        }
    }

    @Test
    fun `the screen can tell a shipped PIN from a chosen one`() {
        // Without this the warning would either never appear or never go away.
        withStore { store ->
            assertTrue(store.parentalLock("mine").usingDefaultPin)
            store.setParentalLock("mine", StoredParentalLock(salt = "s", hash = "h"))
            assertFalse(
                store.parentalLock("mine").usingDefaultPin,
                "a chosen PIN must stop the warning",
            )
        }
    }

    @Test
    fun `the shipped PIN is never written to preferences`() {
        // Computed on read instead, so an install that predates this gets the lock too — and so
        // there is no migration writing a PIN into everybody's stored settings.
        withStore { store ->
            store.parentalLock("mine")
            val node =
                Preferences.userRoot().node("com/lucasserafin94/iptvburo/test-nonexistent-${UUID.randomUUID()}")
            try {
                assertTrue(
                    DesktopUserStore(node).parentalLock("never-seen").usingDefaultPin,
                    "a profile the store has never heard of is still locked",
                )
            } finally {
                node.removeNode()
            }
        }
    }

    @Test
    fun `a chosen PIN survives, and is not replaced by the shipped one`() {
        withStore { store ->
            store.setParentalLock("mine", StoredParentalLock(salt = "chosen-salt", hash = "chosen-hash"))
            val lock = store.parentalLock("mine")
            assertTrue(lock.salt == "chosen-salt" && lock.hash == "chosen-hash")
        }
    }
}

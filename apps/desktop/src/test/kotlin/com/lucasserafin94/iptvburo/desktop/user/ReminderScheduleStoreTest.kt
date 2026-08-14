package com.lucasserafin94.iptvburo.desktop.user

import com.lucasserafin94.iptvburo.domain.model.ReminderPolicy
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The reminder notice's own settings: whether to announce, when, and what was last shown.
 *
 * Separated from [DesktopUserStoreTest] because these are about the schedule rather than the marked
 * titles, and the two are stored and read independently.
 */
class ReminderScheduleStoreTest {
    /**
     * A fresh install announces in the early evening, not at midnight.
     *
     * `getInt` returns 0 for a missing key, which is a valid hour, so a store that simply trusted it
     * would announce at 00:00 on every machine that had never opened the setting — indistinguishable
     * from the feature being broken, since nobody is watching then.
     */
    @Test
    fun `an unset hour falls back to the policy default rather than to midnight`() {
        withStore { store ->
            assertEquals(ReminderPolicy.DEFAULT_HOUR, store.reminderHour())
        }
    }

    @Test
    fun `a chosen hour is kept`() {
        withStore { store ->
            store.setReminderHour(9)

            assertEquals(9, store.reminderHour())
        }
    }

    /** Out-of-range values are clamped rather than stored and handed back as an invalid hour. */
    @Test
    fun `an impossible hour is clamped into the day`() {
        withStore { store ->
            store.setReminderHour(48)
            assertEquals(23, store.reminderHour())

            store.setReminderHour(-3)
            assertEquals(0, store.reminderHour())
        }
    }

    /** Announcing is on unless the user turns it off: the feature is useless silent by default. */
    @Test
    fun `announcing defaults to on and survives being switched off`() {
        withStore { store ->
            assertTrue(store.remindersAnnounced())

            store.setRemindersAnnounced(false)

            assertFalse(store.remindersAnnounced())
        }
    }

    /**
     * The day-stamp is what stops the notice reappearing all day.
     *
     * Null before anything has been shown, so a first run is not mistaken for "already seen today".
     */
    @Test
    fun `the last shown day is absent until something has been shown`() {
        withStore { store ->
            assertNull(store.reminderLastShownOn())

            store.setReminderLastShownOn("2026-08-14")

            assertEquals("2026-08-14", store.reminderLastShownOn())
        }
    }

    /**
     * The pairing code outlives the session, which is what makes it a one-time thing to type.
     *
     * Absent until something stores one, so a first run mints its own rather than starting from a
     * blank string that would match a blank code.
     */
    @Test
    fun `the pairing code is kept between sessions and can be thrown away`() {
        val node = Preferences.userRoot().node("com/lucasserafin94/iptvburo/test-${UUID.randomUUID()}")
        try {
            assertNull(DesktopUserStore(node).castPairingCode())

            DesktopUserStore(node).setCastPairingCode("2275")

            // A second store over the same node is what the next launch does.
            assertEquals("2275", DesktopUserStore(node).castPairingCode())

            DesktopUserStore(node).clearCastPairingCode()
            assertNull(DesktopUserStore(node).castPairingCode())
        } finally {
            node.removeNode()
        }
    }

    private fun withStore(block: (DesktopUserStore) -> Unit) {
        val node = Preferences.userRoot().node("com/lucasserafin94/iptvburo/test-${UUID.randomUUID()}")
        try {
            block(DesktopUserStore(node))
        } finally {
            runCatching { node.removeNode() }
        }
    }
}

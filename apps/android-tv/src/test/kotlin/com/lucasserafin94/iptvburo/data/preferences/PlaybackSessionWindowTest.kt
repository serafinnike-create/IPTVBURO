package com.lucasserafin94.iptvburo.data.preferences

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How long a stored playback session stays worth reopening.
 *
 * The window is the whole safety of this feature: the app reopens a player by itself, which is
 * welcome moments after Android killed the process and unwelcome the next morning. These assert the
 * real rule rather than a copy of it — the DataStore around it needs an Android context, but the
 * decision does not, which is why it is a function of its own.
 */
class PlaybackSessionWindowTest {
    @Test
    fun `a session saved moments ago is restored`() {
        assertTrue(isSessionWorthRestoring(savedAtEpochMillis = 1_000L, now = 31_000L))
    }

    @Test
    fun `a session saved exactly at the boundary is still restored`() {
        assertTrue(isSessionWorthRestoring(savedAtEpochMillis = 0L, now = MAX_RESUME_AGE_MILLIS))
    }

    @Test
    fun `a session older than the window is left alone`() {
        // The position is still in the database and Continue assistindo shows it; what stops is the
        // app reopening the player on its own, hours after somebody put the phone down.
        assertFalse(isSessionWorthRestoring(savedAtEpochMillis = 0L, now = MAX_RESUME_AGE_MILLIS + 1L))
    }

    @Test
    fun `a session from the future is refused rather than trusted`() {
        // A clock that moved backwards — a timezone change, a manual correction — would otherwise
        // leave a session that never expires, reopening a film on every launch from then on.
        assertFalse(isSessionWorthRestoring(savedAtEpochMillis = 10_000L, now = 5_000L))
    }

    @Test
    fun `the window is four hours`() {
        // Pinned so shortening or lengthening it is a deliberate edit rather than a silent one:
        // this number decides when the app stops acting on its own.
        assertTrue(MAX_RESUME_AGE_MILLIS == 4L * 60L * 60L * 1_000L)
    }
}

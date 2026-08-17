package com.lucasserafin94.iptvburo.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first

private val Context.playbackSessionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "playback-session",
)

/**
 * What was on screen when the app was last playing something.
 *
 * Enough to reopen the player and nothing more: the catalogue row id, whose profile it was, and
 * when it was written. The position itself is **not** here — that already lives in
 * `playback_progress`, keyed by identity, and a second copy would be a second answer to the same
 * question and eventually the wrong one.
 */
data class PlaybackSession(
    val channelId: String,
    val profileId: String,
    val savedAtEpochMillis: Long,
)

/**
 * The part of session storage the view model depends on.
 *
 * An interface for the same reason `ReminderScheduling` is one: the real implementation opens a
 * DataStore, which needs a working Android context, so a plain JVM test asserting what navigation
 * does would otherwise have to stand one up to get there.
 */
interface PlaybackSessionStore {
    suspend fun current(now: Long = System.currentTimeMillis()): PlaybackSession?

    suspend fun remember(channelId: String, profileId: String, now: Long = System.currentTimeMillis())

    suspend fun clear()
}

/**
 * Whether a session written at [savedAtEpochMillis] is still worth reopening at [now].
 *
 * Its own function so the rule can be asserted directly rather than through a DataStore that needs
 * an Android context to open — a test that had to reimplement the window to reach it would be
 * asserting its own copy, and the two would drift.
 *
 * The lower bound is not decoration: a clock that moved backwards, from a timezone change or a
 * manual correction, would otherwise leave a session that never expires.
 */
fun isSessionWorthRestoring(
    savedAtEpochMillis: Long,
    now: Long,
    maxAgeMillis: Long = MAX_RESUME_AGE_MILLIS,
): Boolean = (now - savedAtEpochMillis) in 0..maxAgeMillis

/**
 * How stale a session may be and still be worth reopening.
 *
 * Four hours: long enough to cover a phone left on the sofa through a meal, short enough that
 * opening the app the next morning does not drop somebody back into last night's film. Past this the
 * position is still in the database — Continue assistindo shows it — the app just stops reopening
 * the player by itself.
 */
const val MAX_RESUME_AGE_MILLIS: Long = 4L * 60L * 60L * 1_000L

/**
 * Remembers which title was open so the app can return to it after Android kills the process.
 *
 * The player is created inside the composable, so everything it holds dies with the process — and
 * Android reclaims a backgrounded app freely, which on a phone with little memory happens while the
 * viewer is answering one message. The app then reopens on the home screen and the film they were
 * watching is simply gone from view; the *position* survived, because that is checkpointed to the
 * database, but nothing led back to it.
 *
 * Deliberately not a general "resume where I left off" feature. It only fires when the process died
 * with a player open, and only for a short while afterwards — see [MAX_RESUME_AGE_MILLIS]. Reopening
 * a film somebody finished with hours ago would be the app arguing with them about what to watch.
 */
@Singleton
class PlaybackSessionPreferences
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : PlaybackSessionStore {
        private val dataStore = context.playbackSessionDataStore

        /**
         * The session to restore, or null when there is none worth restoring.
         *
         * Returns null rather than throwing on a corrupt or unreadable store: failing to restore is
         * the ordinary outcome, and it must never be a reason for the app not to start.
         */
        override suspend fun current(now: Long): PlaybackSession? =
            runCatching {
                val stored =
                    dataStore.data
                        .catch { error ->
                            if (error is IOException) emit(emptyPreferences()) else throw error
                        }.first()
                val channelId = stored[CHANNEL_ID]?.takeIf(String::isNotBlank) ?: return@runCatching null
                val profileId = stored[PROFILE_ID]?.takeIf(String::isNotBlank) ?: return@runCatching null
                val savedAt = stored[SAVED_AT] ?: return@runCatching null
                if (!isSessionWorthRestoring(savedAt, now)) return@runCatching null
                PlaybackSession(channelId, profileId, savedAt)
            }.getOrNull()

        override suspend fun remember(
            channelId: String,
            profileId: String,
            now: Long,
        ) {
            runCatching {
                dataStore.edit { stored ->
                    stored[CHANNEL_ID] = channelId
                    stored[PROFILE_ID] = profileId
                    stored[SAVED_AT] = now
                }
            }
        }

        /**
         * Forgets the session, which is what closing the player means.
         *
         * Leaving it would reopen a film the viewer deliberately left — the difference between
         * "Android took the app away" and "I am done with this" is exactly what this call carries.
         */
        override suspend fun clear() {
            runCatching { dataStore.edit { stored -> stored.clear() } }
        }

        private companion object {
            val CHANNEL_ID = stringPreferencesKey("channel_id")
            val PROFILE_ID = stringPreferencesKey("profile_id")
            val SAVED_AT = longPreferencesKey("saved_at")

            /**
             * How stale a session may be and still be worth reopening.
             *
             * Four hours: long enough to cover a phone left on the sofa through a meal, short
             * enough that opening the app the next morning does not drop somebody back into last
             * night's film. Past this the position is still in the database — Continue assistindo
             * shows it — the app just stops reopening the player by itself.
             */
            const val MAX_RESUME_AGE_MILLIS = 4L * 60L * 60L * 1_000L
        }
    }

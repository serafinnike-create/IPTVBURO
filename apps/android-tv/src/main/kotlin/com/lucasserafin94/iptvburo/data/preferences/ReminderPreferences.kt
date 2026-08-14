package com.lucasserafin94.iptvburo.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lucasserafin94.iptvburo.domain.model.ReminderPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.reminderDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "reminders",
)

/** When the daily reminder arrives, and whether it arrives at all. */
data class ReminderSchedule(
    val notify: Boolean,
    val time: LocalTime,
)

/**
 * The reminder notification setting, kept across sessions.
 *
 * Stored as hour and minute rather than as one "minutes past midnight" number so a stored value
 * stays readable and a corrupt one is obvious. Both are clamped on read: a value outside the clock
 * would otherwise make [LocalTime.of] throw on a background thread, on every launch, with nothing
 * the user could do about it.
 *
 * Household-wide rather than per profile, because the notification is a property of the phone. One
 * device posts one notification at one time; asking each profile for its own hour would mean
 * several notifications a day from an app that is meant to send at most one.
 */
@Singleton
class ReminderPreferences
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val dataStore = context.reminderDataStore

        val schedule: Flow<ReminderSchedule> =
            dataStore.data
                .catch { error ->
                    if (error is IOException) emit(emptyPreferences()) else throw error
                }.map { stored ->
                    ReminderSchedule(
                        notify = stored[NOTIFY] ?: true,
                        time =
                            LocalTime.of(
                                (stored[HOUR] ?: ReminderPolicy.DEFAULT_HOUR).coerceIn(0, 23),
                                (stored[MINUTE] ?: 0).coerceIn(0, 59),
                            ),
                    )
                }

        suspend fun current(): ReminderSchedule = schedule.first()

        suspend fun setTime(time: LocalTime) {
            dataStore.edit { stored ->
                stored[HOUR] = time.hour
                stored[MINUTE] = time.minute
            }
        }

        suspend fun setNotify(notify: Boolean) {
            dataStore.edit { stored -> stored[NOTIFY] = notify }
        }

        private companion object {
            val HOUR = intPreferencesKey("reminder_hour")
            val MINUTE = intPreferencesKey("reminder_minute")
            val NOTIFY = booleanPreferencesKey("reminder_notify")
        }
    }

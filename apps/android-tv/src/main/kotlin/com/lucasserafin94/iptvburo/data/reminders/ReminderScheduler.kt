package com.lucasserafin94.iptvburo.data.reminders

import android.content.Context
import com.lucasserafin94.iptvburo.data.preferences.ReminderPreferences
import com.lucasserafin94.iptvburo.data.preferences.ReminderSchedule
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * The part of scheduling the view model depends on.
 *
 * An interface for the same reason `CatalogueGuard` is one: the real implementation reaches
 * `WorkManager.getInstance`, which needs an initialised Android runtime, so a plain JVM test that
 * only wants to assert what a button does would otherwise have to stand up WorkManager to do it.
 */
interface ReminderScheduling {
    /**
     * The setting as it stands, for the page that shows and edits it.
     *
     * A flow rather than a one-shot read so the reminders page reflects a change made anywhere
     * else — the same store is written by [setTime] and [setNotify], and a page holding a snapshot
     * would keep showing the old hour after one of those ran.
     */
    val schedule: Flow<ReminderSchedule>

    suspend fun sync()

    suspend fun setTime(hour: Int, minute: Int)

    suspend fun setNotify(notify: Boolean)
}

/**
 * Keeps the daily notification in step with the setting behind it.
 *
 * One place, because the schedule has to be corrected from three unrelated directions — the app
 * starting, the hour being changed, reminders being turned off — and three call sites each doing
 * their own `if (notify)` is how one of them ends up leaving a cancelled notification running.
 *
 * Reading the preference here rather than taking it as an argument means a caller cannot schedule
 * a notification the viewer has switched off.
 */
@Singleton
class ReminderScheduler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val preferences: ReminderPreferences,
    ) : ReminderScheduling {
        override val schedule: Flow<ReminderSchedule> get() = preferences.schedule

        /**
         * Applies whatever the setting currently says.
         *
         * Safe to call repeatedly: scheduling is unique work with `UPDATE`, so a second call
         * replaces the first rather than stacking a second notification.
         */
        override suspend fun sync() {
            val schedule = preferences.current()
            if (schedule.notify) {
                // Converted at the boundary: the preference stays java.time.LocalTime, which is
                // what the Android time picker hands back, while the shared ReminderPolicy this
                // feeds into speaks kotlinx.datetime.
                ReminderWorker.schedule(
                    context,
                    kotlinx.datetime.LocalTime(schedule.time.hour, schedule.time.minute),
                )
            } else {
                ReminderWorker.cancel(context)
            }
        }

        override suspend fun setTime(hour: Int, minute: Int) {
            preferences.setTime(LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59)))
            sync()
        }

        override suspend fun setNotify(notify: Boolean) {
            preferences.setNotify(notify)
            sync()
        }
    }

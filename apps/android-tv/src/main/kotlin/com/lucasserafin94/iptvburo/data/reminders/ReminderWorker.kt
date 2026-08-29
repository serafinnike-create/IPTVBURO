package com.lucasserafin94.iptvburo.data.reminders

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lucasserafin94.iptvburo.data.preferences.NotificationCentreStore
import com.lucasserafin94.iptvburo.data.repository.ReminderRepository
import com.lucasserafin94.iptvburo.data.repository.SeriesWatchRepository
import com.lucasserafin94.iptvburo.domain.model.ReminderDigest
import com.lucasserafin94.iptvburo.domain.model.ReminderPolicy
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone

/**
 * Wakes once a day and posts the reminder digest.
 *
 * WorkManager rather than a coroutine in the app: the notification has to arrive when the app is
 * closed, which is the whole point, and it has to survive a reboot. Nothing in the process can
 * promise either.
 *
 * The work is deliberately tiny — read the reminders, ask the policy what they amount to, post at
 * most one notification — because a periodic worker runs on a schedule the system controls and a
 * slow one gets deferred or killed.
 */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val reminders: ReminderRepository,
    private val seriesWatch: SeriesWatchRepository,
    private val notifier: ReminderNotifier,
    private val notificationCentre: NotificationCentreStore,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        // Nothing to post to. Success rather than retry: a refused permission is a decision, not a
        // transient failure, and retrying would burn battery to be refused again.
        if (!notifier.canNotify()) return Result.success()

        return runCatching {
            // Grouped by profile so one household member's titles are never announced as another's.
            // Whoever has something to say today gets a digest; in practice that is one person.
            reminders.allByProfile().values.forEach { profileReminders ->
                val digest =
                    ReminderPolicy.digestFor(
                        reminders = profileReminders,
                        now = Clock.System.now(),
                        zone = TimeZone.currentSystemDefault(),
                    )
                if (digest is ReminderDigest.Daily) notifier.notify(digest)
            }

            // And the series each profile follows, which is a different question from reminders:
            // a reminder is something the viewer marked and is waiting for, while this is something
            // they already favourited having quietly gained an episode.
            //
            // Runs on the same daily schedule rather than its own worker: it needs the same
            // permission, the same battery budget and the same once-a-day cadence, and a second
            // periodic worker would double the wake-ups for no benefit.
            //
            // Failures here must not lose the reminder digest above, which has already been posted.
            runCatching {
                seriesWatch.checkAllProfiles().forEach { (profileId, changes) ->
                    notifier.notifySeriesChanges(changes)
                    // And into the bell, so the news survives the notification being swiped away.
                    //
                    // A system notification is gone the moment it is dismissed, and somebody who
                    // clears their shade on the bus has no way back to what it said. The bell is
                    // where it waits until they choose to look.
                    changes.forEach { notice ->
                        notificationCentre.add(profileId, notice.toAppNotification())
                    }
                }
            }
            Result.success()
        }.getOrElse {
            // Retried rather than failed: the database being briefly unavailable is exactly the
            // kind of thing that works on the next attempt, and a reminder missed today is worth
            // one more try rather than silence.
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "iptvburo-daily-reminders"

        /**
         * Schedules the daily digest, replacing whatever was scheduled before.
         *
         * `UPDATE` rather than `KEEP`: changing the notification hour has to move the existing
         * schedule, and KEEP would silently ignore the new time — the setting would appear to save
         * and change nothing.
         *
         * The initial delay carries the chosen hour. WorkManager guarantees "once per interval",
         * not "at exactly this minute", so the notification can arrive late — that is the trade for
         * work that survives the app being closed, and a reminder is not a stopwatch.
         */
        fun schedule(
            context: Context,
            preferred: LocalTime,
            now: Instant = Clock.System.now(),
            zone: TimeZone = TimeZone.currentSystemDefault(),
        ) {
            val firstRun = ReminderPolicy.nextNotificationAt(preferred, now, zone)
            val delay = (firstRun - now).coerceAtLeast(kotlin.time.Duration.ZERO)

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
                    .setInitialDelay(delay.inWholeMilliseconds, TimeUnit.MILLISECONDS)
                    .build(),
            )
        }

        /** Stops the daily digest, for a user who turns reminders off entirely. */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}

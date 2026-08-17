package com.lucasserafin94.iptvburo.data.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lucasserafin94.iptvburo.R
import com.lucasserafin94.iptvburo.domain.model.AppNotification
import com.lucasserafin94.iptvburo.domain.model.NotificationCentre
import com.lucasserafin94.iptvburo.domain.model.NotificationKind
import com.lucasserafin94.iptvburo.domain.model.ReminderDigest
import com.lucasserafin94.iptvburo.domain.model.SeriesChange
import com.lucasserafin94.iptvburo.domain.model.TitleShareLink
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts the one daily reminder notification.
 *
 * One notification, never one per title. Ten reminders posted separately is how a person ends up
 * switching the app's notifications off, and then never hears about the release they cared about —
 * so the digest is summarised into a single entry with the detail underneath.
 *
 * Every path here fails quietly. A notification is a courtesy: if the channel is blocked, the
 * permission refused or the poster unreadable, the reminder still exists in the app and still shows
 * on the home screen and the reminders page. None of that is worth an error the user must dismiss.
 */
@Singleton
class ReminderNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    /**
     * Whether the app may post at all.
     *
     * Android 13 made notifications a runtime permission. Asking here rather than assuming means
     * the worker can skip its work entirely instead of building a notification that is dropped.
     */
    fun canNotify(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

    fun notify(digest: ReminderDigest) {
        val daily = digest as? ReminderDigest.Daily ?: return
        if (!canNotify()) return

        runCatching {
            ensureChannel()

            val lines =
                buildList {
                    daily.releasedToday.forEach { reminder ->
                        add(context.getString(R.string.reminder_line_released, reminder.title))
                    }
                    daily.upcoming.forEach { (reminder, days) ->
                        add(
                            context.resources.getQuantityString(
                                R.plurals.reminder_line_countdown,
                                days.toInt(),
                                reminder.title,
                                days.toInt(),
                            ),
                        )
                    }
                    daily.waiting.forEach { reminder -> add(reminder.title) }
                }

            // The headline names what changed today, because that is the only reason to look now.
            // "You have three reminders" is true every day and says nothing.
            val title =
                if (daily.releasedToday.isNotEmpty()) {
                    context.resources.getQuantityString(
                        R.plurals.reminder_title_released,
                        daily.releasedToday.size,
                        daily.releasedToday.size,
                    )
                } else {
                    context.resources.getQuantityString(
                        R.plurals.reminder_title_waiting,
                        daily.total,
                        daily.total,
                    )
                }

            val notification =
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher)
                    .setContentTitle(title)
                    .setContentText(lines.firstOrNull().orEmpty())
                    .setStyle(
                        NotificationCompat.InboxStyle().also { style ->
                            // Capped: Android shows about this many, and a digest is a summary
                            // rather than the whole list. The reminders page has the rest.
                            lines.take(MAX_LINES).forEach(style::addLine)
                        },
                    )
                    .setContentIntent(openAppIntent(daily))
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .build()

            // Caught rather than only guarded by canNotify() above.
            //
            // The permission can be revoked between the check and this line — the worker runs
            // while the app is closed, and a user can withdraw it from Settings at any moment. An
            // uncaught SecurityException here fails the worker, and a failed periodic worker stops
            // being rescheduled: one revocation would end reminders permanently, silently.
            val manager = NotificationManagerCompat.from(context)
            // Checked here as well as in canNotify(), which is not redundant: the permission can be
            // withdrawn between the two, since the worker runs while the app is closed and Settings
            // is always reachable. The catch is what matters — an uncaught SecurityException fails
            // the worker, and a failed periodic worker stops being rescheduled, so a single
            // revocation would end reminders permanently and silently.
            if (manager.areNotificationsEnabled()) {
                runCatching { manager.notify(NOTIFICATION_ID, notification) }
            }
        }
    }

    /**
     * Tells the viewer that a series they follow has grown.
     *
     * Its own notification rather than a line inside the daily digest, and its own id so the two do
     * not replace each other: a reminder is about something the viewer marked and is waiting for,
     * while this is about something they already follow having quietly changed. They arrive for
     * different reasons and are acted on differently.
     *
     * Silent when [changes] is empty, which is the ordinary day. An app that posts "nothing new"
     * is one whose notifications get switched off.
     */
    fun notifySeriesChanges(changes: List<SeriesSeriesNotice>) {
        if (changes.isEmpty() || !canNotify()) return

        runCatching {
            ensureChannel()

            val lines =
                changes.mapNotNull { notice ->
                    when (val change = notice.change) {
                        is SeriesChange.NewSeason ->
                            context.getString(
                                R.string.series_new_season_line,
                                notice.title,
                                change.season,
                            )

                        // One episode per notice: the policy reports the episode that leads, not a
                        // count, so the singular wording is the only one that can be honest here.
                        is SeriesChange.NewEpisode ->
                            context.getString(R.string.series_new_episode_line, notice.title)

                        SeriesChange.None -> null
                    }
                }
            if (lines.isEmpty()) return@runCatching

            val notification =
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher)
                    .setContentTitle(context.getString(R.string.series_new_episode_title))
                    .setContentText(lines.first())
                    .setStyle(
                        NotificationCompat.InboxStyle().also { style ->
                            lines.take(MAX_LINES).forEach(style::addLine)
                        },
                    )
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .build()

            // Guarded and caught for the same reason the digest is: the permission can be withdrawn
            // between the check and this line, and an uncaught SecurityException fails the worker —
            // which then stops being rescheduled, ending both notices permanently and silently.
            val manager = NotificationManagerCompat.from(context)
            if (manager.areNotificationsEnabled()) {
                runCatching { manager.notify(SERIES_NOTIFICATION_ID, notification) }
            }
        }
    }

    /**
     * Where tapping the notification goes.
     *
     * Straight to the title when exactly one thing released today — that is the whole reason the
     * notification arrived, and making the user find it again would be pointless. Anything else
     * opens the app, which shows the reminders in their own row.
     *
     * The deep link is the existing share format, so a reminder resolves the same way a shared
     * link does: by identity, against this device's own catalogue.
     */
    private fun openAppIntent(daily: ReminderDigest.Daily): PendingIntent? {
        val single = daily.releasedToday.singleOrNull()
        val intent =
            if (single != null) {
                Intent(Intent.ACTION_VIEW, android.net.Uri.parse(
                    "${TitleShareLink.APP_SCHEME}://title?id=${single.identity.key}",
                ))
            } else {
                context.packageManager.getLaunchIntentForPackage(context.packageName)
            } ?: return null

        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        // Default importance, not high: a reminder is not urgent enough to interrupt what someone
        // is doing, and an app that buzzes for a film gets muted.
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.reminder_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.reminder_channel_description)
            },
        )
    }

    private companion object {
        const val CHANNEL_ID = "iptvburo-reminders"

        /** Fixed, so today's digest replaces yesterday's rather than stacking up. */
        const val NOTIFICATION_ID = 4_517

        /**
         * The series notice's own id, so it does not replace the daily digest.
         *
         * Sharing an id would have whichever arrived second silently erase the first, and the
         * two say different things: one is about a title being waited for, the other about a
         * series already being followed.
         */
        const val SERIES_NOTIFICATION_ID = 4_518

        const val MAX_LINES = 6
    }
}

/**
 * A series change with the name to announce it under.
 *
 * [SeriesChange] deliberately carries no title: it lives in the domain, where a series is an
 * identity rather than a display name, and the same change is worded differently in five languages.
 * The name is added here, at the edge that actually writes the notification.
 */
data class SeriesSeriesNotice(
    val title: String,
    val change: SeriesChange,
)

/**
 * The same notice, as the bell holds it.
 *
 * The id is derived from what the notice is *about* rather than from when it was made, which is what
 * stops the bell filling with copies: the daily check runs every day, and a series that has not
 * changed since yesterday produces the same id and is ignored by `NotificationCentre.add`.
 */
fun SeriesSeriesNotice.toAppNotification(
    seriesKey: String = title,
    now: Long = System.currentTimeMillis(),
): AppNotification =
    when (val outcome = change) {
        is SeriesChange.NewSeason ->
            AppNotification(
                id = NotificationCentre.seasonId(seriesKey, outcome.season),
                kind = NotificationKind.NEW_SEASON,
                title = title,
                createdAt = now,
            )

        is SeriesChange.NewEpisode ->
            AppNotification(
                id = NotificationCentre.episodeId(seriesKey, outcome.season, outcome.episode),
                kind = NotificationKind.NEW_EPISODE,
                title = title,
                createdAt = now,
            )

        SeriesChange.None ->
            AppNotification(
                id = "none:$seriesKey",
                kind = NotificationKind.NEW_EPISODE,
                title = title,
                createdAt = now,
            )
    }

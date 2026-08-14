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
import com.lucasserafin94.iptvburo.domain.model.ReminderDigest
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

            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
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

        const val MAX_LINES = 6
    }
}

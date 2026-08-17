package com.lucasserafin94.iptvburo.ui.screens

import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lucasserafin94.iptvburo.R
import com.lucasserafin94.iptvburo.domain.model.ContentIdentity
import com.lucasserafin94.iptvburo.domain.model.Reminder
import com.lucasserafin94.iptvburo.domain.model.ReminderPolicy
import com.lucasserafin94.iptvburo.ui.designsystem.BuroButton
import com.lucasserafin94.iptvburo.ui.designsystem.BuroButtonStyle
import com.lucasserafin94.iptvburo.ui.designsystem.BuroEmptyState
import com.lucasserafin94.iptvburo.ui.designsystem.BuroTheme
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Everything the profile marked, and when the phone says something about it.
 *
 * The list and the schedule share one screen deliberately. The only reason to open this page is the
 * list, and the only thing anyone wants to change about the notification is the hour it arrives —
 * putting that hour three taps away in Settings would mean opening a different destination to
 * adjust a setting that is about what is on this one.
 *
 * Ordered by how soon each title matters rather than by when it was marked: something out today is
 * what the page is for, and a reminder made months ago for a film released this week would
 * otherwise sit below one made yesterday for a film a year out.
 */
@Composable
fun RemindersScreen(
    reminders: List<Reminder>,
    notify: Boolean,
    time: LocalTime,
    onSetNotify: (Boolean) -> Unit,
    onSetTime: (Int, Int) -> Unit,
    onRemove: (ContentIdentity) -> Unit,
    /** Opens the title a reminder refers to. */
    onOpen: (Reminder) -> Unit = {},
    onBack: () -> Unit,
) {
    val colors = BuroTheme.colors
    val today = LocalDate.now()
    // Sorted here rather than in the view model: this is a presentation order, and the same list
    // feeds the home shelf, which wants the same ordering for the same reason.
    val ordered =
        remember(reminders, today) {
            reminders.sortedWith(
                compareBy(
                    { reminder -> reminder.releaseDate?.let { if (it.isAfter(today)) 1 else 0 } ?: 2 },
                    { reminder -> reminder.releaseDate ?: LocalDate.MAX },
                    { reminder -> reminder.title.lowercase() },
                ),
            )
        }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compactPortrait = maxWidth < 600.dp && maxHeight >= maxWidth
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = if (compactPortrait) 16.dp else 42.dp,
                        vertical = if (compactPortrait) 18.dp else 34.dp,
                    ),
        ) {
            ScreenHeader(
                title = stringResource(R.string.nav_reminders),
                subtitle = stringResource(R.string.reminders_subtitle),
                onBack = onBack,
            )
            Spacer(Modifier.height(if (compactPortrait) 16.dp else 22.dp))

            ReminderScheduleCard(
                notify = notify,
                time = time,
                onSetNotify = onSetNotify,
                onSetTime = onSetTime,
            )
            Spacer(Modifier.height(if (compactPortrait) 14.dp else 18.dp))

            if (ordered.isEmpty()) {
                BuroEmptyState(
                    title = stringResource(R.string.reminders_empty),
                    message = stringResource(R.string.reminders_empty_message),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    icon = {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(44.dp),
                        )
                    },
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(ordered, key = { reminder -> reminder.identity.key }) { reminder ->
                        ReminderRow(
                            onOpen = { onOpen(reminder) },
                            reminder = reminder,
                            today = today,
                            onRemove = { onRemove(reminder.identity) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * The daily notice: whether it arrives, and at what hour.
 *
 * The platform time picker rather than a hand-rolled one, so the hour is entered the way every
 * other alarm on the phone is entered, and 12- or 24-hour display follows the device setting
 * instead of being decided here.
 */
@Composable
private fun ReminderScheduleCard(
    notify: Boolean,
    time: LocalTime,
    onSetNotify: (Boolean) -> Unit,
    onSetTime: (Int, Int) -> Unit,
) {
    val colors = BuroTheme.colors
    val context = LocalContext.current

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surface)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.reminders_notify_label),
                    color = colors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text =
                        stringResource(
                            if (notify) {
                                R.string.reminders_notify_on
                            } else {
                                R.string.reminders_notify_off
                            },
                        ),
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                )
            }
            // The app's own colours rather than the Material default, which paints the track
            // purple — a colour that appears nowhere else in BURO.
            Switch(
                checked = notify,
                onCheckedChange = onSetNotify,
                colors =
                    SwitchDefaults.colors(
                        checkedThumbColor = colors.onBrand,
                        checkedTrackColor = colors.brandPrimary,
                        uncheckedThumbColor = colors.textSecondary,
                        uncheckedTrackColor = colors.elevated,
                        uncheckedBorderColor = colors.borderSubtle,
                    ),
            )
        }

        // Kept visible while the notice is off, rather than hidden: the hour is still the one that
        // will be used when it is switched back on, and a control that disappears reads as the
        // setting having been lost.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.reminders_time_label),
                color = if (notify) colors.textPrimary else colors.textMuted,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = time.format(DateTimeFormatter.ofPattern("HH:mm")),
                color = if (notify) colors.textPrimary else colors.textMuted,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(12.dp))
            BuroButton(
                onClick = {
                    TimePickerDialog(
                        context,
                        { _, hour, minute -> onSetTime(hour, minute) },
                        time.hour,
                        time.minute,
                        android.text.format.DateFormat.is24HourFormat(context),
                    ).show()
                },
                enabled = notify,
                style = BuroButtonStyle.Ghost,
            ) {
                Text(stringResource(R.string.reminders_time_change))
            }
        }
    }
}

/** One marked title: its poster, what it is waiting on, and the way to drop it. */
@Composable
private fun ReminderRow(
    reminder: Reminder,
    today: LocalDate,
    onRemove: () -> Unit,
    onOpen: () -> Unit = {},
) {
    val colors = BuroTheme.colors

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surface)
                // The row opens the title. It was inert, so a reminders page was a list of things
                // the viewer was waiting for with no way to reach any of them.
                .clickable(onClick = onOpen)
                .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(width = 54.dp, height = 78.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.elevated),
            contentAlignment = Alignment.Center,
        ) {
            if (reminder.artworkUrl != null) {
                AsyncImage(
                    model = reminder.artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // Blank far more often than it looks like it should be: a provider-hosted poster is
                // dropped on the way into the database, because those URLs routinely carry the
                // subscriber's credentials and a reminder outlives the playlist it came from. Only
                // TMDB artwork and the app's own files survive that filter.
                //
                // So this is the ordinary case, not the rare one, and it uses the title's initial
                // rather than a bell: every row would carry the same bell, and a list of identical
                // icons tells the reader nothing about which title is which.
                Text(
                    text = reminder.title.trimStart().firstOrNull()?.uppercase() ?: "?",
                    color = colors.textSecondary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = reminder.title,
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
            )
            Text(
                text = reminder.statusLabel(today),
                color = colors.textSecondary,
                fontSize = 13.sp,
            )
        }
        Spacer(Modifier.width(8.dp))
        BuroButton(onClick = onRemove, style = BuroButtonStyle.Ghost) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(R.string.reminders_remove),
                tint = colors.textSecondary,
            )
        }
    }
}

/**
 * What this reminder is waiting on, said the way the notification says it.
 *
 * Reads from the same three cases [ReminderPolicy] sorts a digest into, so the row and the
 * notification never disagree about whether something is out.
 */
@Composable
private fun Reminder.statusLabel(today: LocalDate): String {
    val release = releaseDate ?: return stringResource(R.string.reminders_status_waiting)
    if (!release.isAfter(today)) return stringResource(R.string.reminders_status_released)
    val days = ChronoUnit.DAYS.between(today, release)
    return pluralStringResource(
        R.plurals.reminders_status_countdown,
        days.toInt(),
        days.toInt(),
    )
}

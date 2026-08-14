package com.lucasserafin94.iptvburo.desktop.app

import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lucasserafin94.iptvburo.desktop.user.StoredReminder
import com.lucasserafin94.iptvburo.domain.model.ReminderDigest
import com.lucasserafin94.iptvburo.domain.model.ReminderPolicy
import com.lucasserafin94.iptvburo.desktop.ui.BuroColors
import com.lucasserafin94.iptvburo.desktop.ui.BuroInteractiveRow
import com.lucasserafin94.iptvburo.desktop.ui.BuroRadius
import com.lucasserafin94.iptvburo.desktop.ui.BuroRemoteArtwork
import com.lucasserafin94.iptvburo.desktop.ui.BuroSpacing
import com.lucasserafin94.iptvburo.desktop.ui.strings

/** Small enough that a long list stays scannable, at the 2:3 every other poster here uses. */
private val POSTER_WIDTH = 40.dp
private val POSTER_HEIGHT = 60.dp

/**
 * The titles the viewer marked to come back to.
 *
 * A list rather than the wall of posters History uses, and the reason is what these entries are:
 * the ones that most need this screen are films that have not been released yet, so there is no
 * artwork to recognise and often no catalogue row behind them. A poster grid of blank fallbacks
 * would be a worse answer to "which films did I mark?" than plain names.
 *
 * An entry the library does hold can be opened. One it does not — the upcoming film, the title from
 * a playlist since replaced — is shown and can be removed, but does not pretend to be clickable.
 */
@Composable
fun RemindersGallery(
    reminders: List<StoredReminder>,
    /**
     * Opens a marked title, or null when the library has no row for it.
     *
     * A function rather than a flag so the caller resolves the row once per entry and the list does
     * not have to know how a catalogue lookup works.
     */
    onOpen: (StoredReminder) -> (() -> Unit)?,
    onRemove: (StoredReminder) -> Unit,
    /** Whether the in-app notice is wanted, and the hour it is due. */
    announced: Boolean = true,
    onAnnouncedChange: (Boolean) -> Unit = {},
    hour: Int = ReminderPolicy.DEFAULT_HOUR,
    onHourChange: (Int) -> Unit = {},
    /** Today's notice, when one is due. Null draws nothing. */
    notice: ReminderDigest.Daily? = null,
    onDismissNotice: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val text = strings

    Column(modifier = modifier.fillMaxSize().padding(BuroSpacing.Lg)) {
        Text(
            text = text.savedForLater.remindersTitle,
            color = BuroColors.Text,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(BuroSpacing.Xs))
        // What the app will and will not do, said where someone comes to check exactly that.
        Text(
            text = text.savedForLater.reminderInAppOnly,
            color = BuroColors.TextSubtle,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(BuroSpacing.Md))

        notice?.let { due ->
            ReminderNotice(total = due.total, onDismiss = onDismissNotice)
            Spacer(Modifier.height(BuroSpacing.Md))
        }

        ReminderSchedule(
            announced = announced,
            onAnnouncedChange = onAnnouncedChange,
            hour = hour,
            onHourChange = onHourChange,
        )
        Spacer(Modifier.height(BuroSpacing.Md))

        if (reminders.isEmpty()) {
            Text(
                text = text.savedForLater.remindersEmpty,
                color = BuroColors.TextMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
            return@Column
        }

        val listState = rememberLazyListState()
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(end = BuroSpacing.Md, bottom = BuroSpacing.Xl),
                verticalArrangement = Arrangement.spacedBy(BuroSpacing.Xs),
            ) {
                items(reminders, key = { reminder -> reminder.identityKey }) { reminder ->
                    ReminderRow(
                        reminder = reminder,
                        onOpen = onOpen(reminder),
                        onRemove = { onRemove(reminder) },
                    )
                }
            }
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(listState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                style =
                    LocalScrollbarStyle.current.copy(
                        thickness = 8.dp,
                        unhoverColor = BuroColors.BorderSoft,
                        hoverColor = BuroColors.Primary,
                    ),
            )
        }
    }
}

/**
 * Today's notice, shown once per day after the chosen hour.
 *
 * A panel in the page rather than a system toast, because this process only runs while the app is
 * open: a Windows notification would fire while the user is already looking at the app and never
 * when they are not, which is the opposite of what a reminder is for.
 */
@Composable
private fun ReminderNotice(
    total: Int,
    onDismiss: () -> Unit,
) {
    val text = strings

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(BuroRadius.Small)
                .background(BuroColors.Primary.copy(alpha = 0.12f))
                .padding(horizontal = BuroSpacing.Md, vertical = BuroSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "◉",
            color = BuroColors.Primary,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.width(BuroSpacing.Md))
        Text(
            text = text.savedForLater.reminderNoticeBody.format(total),
            color = BuroColors.Text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDismiss) {
            Text(text.savedForLater.reminderNoticeDismiss, color = BuroColors.Primary)
        }
    }
}

/** Whether to be told at all, and at what time of day. */
@Composable
private fun ReminderSchedule(
    announced: Boolean,
    onAnnouncedChange: (Boolean) -> Unit,
    hour: Int,
    onHourChange: (Int) -> Unit,
) {
    val text = strings

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(BuroRadius.Small)
                .background(BuroColors.Surface)
                .padding(BuroSpacing.Md),
        verticalArrangement = Arrangement.spacedBy(BuroSpacing.Sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = text.savedForLater.reminderAnnounce,
                color = BuroColors.Text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = announced,
                onCheckedChange = onAnnouncedChange,
                colors =
                    SwitchDefaults.colors(
                        checkedThumbColor = BuroColors.OnPrimary,
                        checkedTrackColor = BuroColors.Primary,
                    ),
            )
        }

        // Only while the notice is wanted: choosing an hour for something switched off is a
        // control that does nothing, which reads as the setting being broken.
        if (announced) {
            Text(
                text = text.savedForLater.reminderHourLabel,
                color = BuroColors.TextMuted,
                style = MaterialTheme.typography.labelLarge,
            )
            // Every third hour rather than all twenty-four: this is "morning, afternoon, evening"
            // precision, and a 24-item row would be a wall of numbers to no purpose.
            val choices = listOf(6, 9, 12, 15, 18, 21)
            Row(horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Xs)) {
                choices.forEach { candidate ->
                    val label = "%02d:00".format(candidate)
                    BuroInteractiveRow(
                        onClick = { onHourChange(candidate) },
                        selected = candidate == hour,
                        shape = BuroRadius.Pill,
                        contentDescription = label,
                    ) { state ->
                        Text(
                            text = label,
                            modifier =
                                Modifier.padding(
                                    horizontal = BuroSpacing.Md,
                                    vertical = BuroSpacing.Xs,
                                ),
                            color =
                                when {
                                    candidate == hour -> BuroColors.Primary
                                    state.active -> BuroColors.Text
                                    else -> BuroColors.TextMuted
                                },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

/** One marked title: the name, the year when known, and the way to forget it. */
@Composable
private fun ReminderRow(
    reminder: StoredReminder,
    onOpen: (() -> Unit)?,
    onRemove: () -> Unit,
) {
    val text = strings

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(BuroRadius.Small)
                .background(BuroColors.Surface)
                .padding(horizontal = BuroSpacing.Md, vertical = BuroSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The poster, at the 2:3 the rest of the app uses. BuroRemoteArtwork draws the fallback
        // behind the image, so a null URL and a failed load both land on a designed tile rather
        // than a broken-image icon — the ordinary case for a film that is not out yet.
        Box(
            modifier =
                Modifier
                    .width(POSTER_WIDTH)
                    .height(POSTER_HEIGHT)
                    .clip(BuroRadius.Small)
                    .background(BuroColors.SurfaceRaised),
        ) {
            BuroRemoteArtwork(
                artworkUrl = reminder.artworkUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            ) {
                // The same filled mark the detail page shows once a title is marked, so a title
                // with no artwork still reads as "this one is set".
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "◉",
                        color = BuroColors.Primary,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
        Spacer(Modifier.width(BuroSpacing.Md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = reminder.title,
                color = BuroColors.Text,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // The year, and — when the library cannot open it — why. Said plainly rather than
            // leaving a row that silently does nothing when clicked.
            val detail =
                listOfNotNull(
                    reminder.year?.toString(),
                    if (onOpen == null) text.savedForLater.reminderNotInLibrary else null,
                ).joinToString("  ·  ")
            if (detail.isNotBlank()) {
                Text(
                    text = detail,
                    color = BuroColors.TextSubtle,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        onOpen?.let { open ->
            BuroInteractiveRow(
                onClick = open,
                selected = false,
                shape = BuroRadius.Pill,
                contentDescription = text.savedForLater.reminderOpen,
            ) { state ->
                Text(
                    text = text.savedForLater.reminderOpen,
                    modifier =
                        Modifier.padding(horizontal = BuroSpacing.Md, vertical = BuroSpacing.Xs),
                    color = if (state.active) BuroColors.Primary else BuroColors.Text,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        TextButton(onClick = onRemove) {
            Text(text.savedForLater.reminderRemove, color = BuroColors.TextSubtle)
        }
    }
}

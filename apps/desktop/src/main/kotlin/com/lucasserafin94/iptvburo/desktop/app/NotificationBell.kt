package com.lucasserafin94.iptvburo.desktop.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.window.Popup
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lucasserafin94.iptvburo.desktop.ui.BuroColors
import com.lucasserafin94.iptvburo.desktop.ui.BuroInteractiveRow
import com.lucasserafin94.iptvburo.desktop.ui.BuroRadius
import com.lucasserafin94.iptvburo.desktop.ui.BuroSpacing
import com.lucasserafin94.iptvburo.desktop.ui.strings
import com.lucasserafin94.iptvburo.domain.model.NotificationCentre

/**
 * The bell beside the profile, and the panel behind it.
 *
 * A bell rather than a dialog because none of this is urgent: a film being out is worth saying and
 * never worth stopping somebody mid-film to say. It waits where it can be found.
 *
 * ## Black and white
 *
 * The bell itself is monochrome, like every other mark in the chrome — it is a place, not an alarm,
 * and a coloured icon sitting permanently in the header reads as a warning that never clears. The
 * one exception is the unread count, which has to be seen to do its job at all; that carries the
 * app's own gold, and only while there is something unread.
 */
@Composable
fun NotificationBell(
    centre: NotificationCentre,
    onOpened: () -> Unit,
    onDismiss: (String) -> Unit,
    onClearAll: () -> Unit,
) {
    val text = strings.shareStrings.notifications
    var open by remember { mutableStateOf(false) }
    val unread = centre.unreadCount

    Box {
        BuroInteractiveRow(
            onClick = {
                open = !open
                // Reading is opening. Marking them read when the panel closes would leave the badge
                // up while the viewer is looking straight at the list it counts.
                if (open) onOpened()
            },
            selected = open,
            shape = CircleShape,
            contentDescription = text.title,
        ) { state ->
            Box(modifier = Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = null,
                    tint =
                        when {
                            open -> BuroColors.Text
                            state.active -> BuroColors.Text
                            else -> BuroColors.TextMuted
                        },
                    modifier = Modifier.size(20.dp),
                )
                // Only while something is unread, and never as a "0": a badge that is always there
                // stops being a signal and becomes part of the furniture.
                if (unread > 0) {
                    Text(
                        text = if (unread > MAX_BADGE) "$MAX_BADGE+" else unread.toString(),
                        color = BuroColors.OnPrimary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 2.dp, y = (-2).dp)
                                .clip(CircleShape)
                                .background(BuroColors.Primary)
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
        }

        if (open) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = androidx.compose.ui.unit.IntOffset(0, 46),
                onDismissRequest = { open = false },
                properties = androidx.compose.ui.window.PopupProperties(focusable = true),
            ) {
                NotificationPanel(
                    centre = centre,
                    onDismiss = onDismiss,
                    onClearAll = {
                        onClearAll()
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
private fun NotificationPanel(
    centre: NotificationCentre,
    onDismiss: (String) -> Unit,
    onClearAll: () -> Unit,
) {
    val text = strings.shareStrings.notifications
    val held = centre.newestFirst

    Column(
        modifier =
            Modifier
                .width(PANEL_WIDTH)
                .clip(BuroRadius.Small)
                .background(BuroColors.Surface)
                .border(1.dp, BuroColors.BorderSoft, BuroRadius.Small)
                .padding(BuroSpacing.Md),
        verticalArrangement = Arrangement.spacedBy(BuroSpacing.Xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = text.title,
                color = BuroColors.Text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (held.isNotEmpty()) {
                TextButton(onClick = onClearAll) {
                    Text(text.clearAll, color = BuroColors.TextSubtle)
                }
            }
        }

        if (held.isEmpty()) {
            Text(
                text = text.empty,
                color = BuroColors.TextMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
            return@Column
        }

        // Bounded, and scrolling past that. A panel that grows with the list would eventually run
        // off the bottom of the window with no way to reach its end.
        LazyColumn(
            modifier = Modifier.heightIn(max = PANEL_MAX_HEIGHT),
            contentPadding = PaddingValues(vertical = BuroSpacing.Xxs),
            verticalArrangement = Arrangement.spacedBy(BuroSpacing.Xs),
        ) {
            items(held, key = { notification -> notification.id }) { notification ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(BuroRadius.Small)
                            .background(BuroColors.SurfaceRaised)
                            .padding(horizontal = BuroSpacing.Sm, vertical = BuroSpacing.Xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = notification.title,
                            color = BuroColors.Text,
                            // Unread reads heavier, which is the whole of the distinction: a second
                            // colour would compete with the badge for the same meaning.
                            fontWeight = if (notification.read) FontWeight.Normal else FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        notification.body?.let { body ->
                            Text(
                                text = body,
                                color = BuroColors.TextSubtle,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    TextButton(onClick = { onDismiss(notification.id) }) {
                        Text("✕", color = BuroColors.TextSubtle)
                    }
                }
            }
        }
        Spacer(Modifier.height(BuroSpacing.Xxs))
    }
}

/** Wide enough for a sentence, narrow enough not to cover the screen it hangs over. */
private val PANEL_WIDTH = 320.dp

/** Roughly six rows. Past that the list scrolls rather than the panel growing. */
private val PANEL_MAX_HEIGHT = 320.dp

/** Beyond this the exact number stops mattering and the badge stops fitting. */
private const val MAX_BADGE = 9

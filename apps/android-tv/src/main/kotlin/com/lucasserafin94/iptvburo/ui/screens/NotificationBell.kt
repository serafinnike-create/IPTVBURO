package com.lucasserafin94.iptvburo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text
import com.lucasserafin94.iptvburo.R
import com.lucasserafin94.iptvburo.domain.model.AppNotification
import com.lucasserafin94.iptvburo.domain.model.NotificationCentre
import com.lucasserafin94.iptvburo.ui.components.FocusSurface
import com.lucasserafin94.iptvburo.ui.designsystem.BuroButton
import com.lucasserafin94.iptvburo.ui.designsystem.BuroButtonStyle
import com.lucasserafin94.iptvburo.ui.theme.BuroCanvas
import com.lucasserafin94.iptvburo.ui.theme.BuroSurface
import com.lucasserafin94.iptvburo.ui.theme.BuroTextPrimary
import com.lucasserafin94.iptvburo.ui.theme.BuroTextSecondary

/**
 * The bell beside the profile, and the list behind it.
 *
 * Notices wait here instead of interrupting. A reminder that a film is out is worth saying and never
 * worth a dialog in front of whatever somebody was doing — so it sits behind the bell and the viewer
 * decides when to look.
 *
 * ## Black and white, deliberately
 *
 * The bell is monochrome even while the app's accent is gold. Everything else in the top bar is
 * chrome; colour here would read as an alert whether or not anything had arrived. The count badge is
 * the one exception, and it appears only when something is genuinely unread.
 */
@Composable
fun NotificationBell(
    centre: NotificationCentre,
    onMarkAllRead: () -> Unit,
    onDismiss: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        FocusSurface(
            onClick = {
                open = true
                // Opening is reading. Marking them read when the panel closes instead would leave
                // the badge up while the viewer is looking straight at the list.
                onMarkAllRead()
            },
            modifier = Modifier.size(42.dp),
            shape = CircleShape,
            backgroundColor = Color.Transparent,
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = stringResource(R.string.notifications_open),
                tint = BuroTextPrimary,
                modifier = Modifier.size(22.dp),
            )
        }

        // Only while something is unread, and never drawn as a "0": a badge that is always there
        // stops meaning anything, which is the whole reason it is worth drawing at all.
        if (centre.unreadCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 2.dp)
                    .size(if (centre.unreadCount > 9) 20.dp else 16.dp)
                    .clip(CircleShape)
                    // White on the dark bar: the badge has to carry against the chrome without
                    // introducing a colour the top bar does not otherwise use.
                    .background(BuroTextPrimary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    // Capped: past this the exact number stops being information and the badge
                    // stops fitting the circle.
                    text = if (centre.unreadCount > 9) "9+" else centre.unreadCount.toString(),
                    color = BuroCanvas,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }

    if (open) {
        Dialog(onDismissRequest = { open = false }) {
            NotificationPanel(
                centre = centre,
                onDismiss = onDismiss,
                onClearAll = {
                    onClearAll()
                    open = false
                },
                onClose = { open = false },
            )
        }
    }
}

@Composable
private fun NotificationPanel(
    centre: NotificationCentre,
    onDismiss: (String) -> Unit,
    onClearAll: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(BuroCanvas)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.notifications_title),
                color = BuroTextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            FocusSurface(
                onClick = onClose,
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                backgroundColor = Color.Transparent,
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.common_close),
                    tint = BuroTextSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        if (centre.notifications.isEmpty()) {
            Text(
                text = stringResource(R.string.notifications_empty),
                color = BuroTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.notifications_empty_body),
                color = BuroTextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
            return@Column
        }

        LazyColumn(
            // Bounded so a full bell cannot grow the dialog past the screen; scrolls beyond that.
            modifier = Modifier.heightIn(max = 420.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(centre.newestFirst, key = { notification -> notification.id }) { notification ->
                NotificationRow(notification = notification, onDismiss = { onDismiss(notification.id) })
            }
        }
        Spacer(Modifier.height(14.dp))
        BuroButton(onClick = onClearAll, style = BuroButtonStyle.Ghost) {
            Text(stringResource(R.string.notifications_clear_all))
        }
    }
}

@Composable
private fun NotificationRow(
    notification: AppNotification,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BuroSurface)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A dot for what has not been read, and nothing at all for what has. Kept white rather
        // than coloured, for the same reason the bell is.
        if (!notification.read) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(BuroTextPrimary),
            )
            Spacer(Modifier.width(10.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = notification.title,
                color = BuroTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
            )
            notification.body?.takeIf(String::isNotBlank)?.let { body ->
                Spacer(Modifier.height(2.dp))
                Text(text = body, color = BuroTextSecondary, fontSize = 12.sp, maxLines = 2)
            }
        }
        Spacer(Modifier.width(8.dp))
        FocusSurface(
            onClick = onDismiss,
            modifier = Modifier.size(34.dp),
            shape = CircleShape,
            backgroundColor = Color.Transparent,
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.notifications_dismiss),
                tint = BuroTextSecondary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

package com.lucasserafin94.iptvburo.desktop.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lucasserafin94.iptvburo.desktop.ui.BuroColors
import com.lucasserafin94.iptvburo.desktop.ui.BuroSpacing
import com.lucasserafin94.iptvburo.desktop.ui.ScreenStrings
import com.lucasserafin94.iptvburo.domain.model.SubscriptionExpiry

/**
 * When the viewer's subscription to their list runs out.
 *
 * The list's own subscription, from the panel's `exp_date` — not the app's licence, which the chip
 * beside this one reports. The two look alike and mean different things, so this one names what it
 * is talking about: "Lista: faltam 12 dias", never a bare number.
 *
 * ## When it appears
 *
 * Only inside the last month, and once it has run out. A viewer with eight months left does not
 * need a daily reminder, and the header is deliberately sparse — a permanent second countdown next
 * to the licence one would turn it back into the debug toolbar it used to be.
 *
 * Nothing at all when the panel does not send a date. Plenty do not, and a line that never expires
 * arrives the same way; inventing "expired" from a missing field would be worse than silence.
 */
@Composable
fun SubscriptionChip(
    daysLeft: Int?,
    text: ScreenStrings,
) {
    if (daysLeft == null) return
    val expired = SubscriptionExpiry.hasExpired(daysLeft)
    if (!expired && !SubscriptionExpiry.isExpiringSoon(daysLeft)) return

    val urgent = expired || SubscriptionExpiry.isUrgent(daysLeft)
    val label =
        when {
            expired -> text.subscriptionExpired
            daysLeft <= 1 -> text.subscriptionLastDay
            else -> text.subscriptionDaysLeft.format(daysLeft)
        }

    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (urgent) URGENT_SUBSCRIPTION_BACKGROUND else BuroColors.SurfaceRaised)
                .border(
                    width = 1.dp,
                    color = if (urgent) BuroColors.Primary else BuroColors.BorderSoft,
                    shape = RoundedCornerShape(8.dp),
                )
                .padding(horizontal = BuroSpacing.Sm, vertical = BuroSpacing.Xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = if (urgent) BuroColors.Primary else BuroColors.TextMuted,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (urgent) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

private val URGENT_SUBSCRIPTION_BACKGROUND = BuroColors.Primary.copy(alpha = 0.12f)

package com.lucasserafin94.iptvburo.desktop.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lucasserafin94.iptvburo.desktop.license.LicenseStatus
import com.lucasserafin94.iptvburo.desktop.ui.BuroColors
import com.lucasserafin94.iptvburo.desktop.ui.BuroInteractiveRow
import com.lucasserafin94.iptvburo.desktop.ui.BuroSpacing
import com.lucasserafin94.iptvburo.desktop.ui.LicenseStrings
import com.lucasserafin94.iptvburo.desktop.ui.strings

/**
 * How much time is left, in the header.
 *
 * ## When it appears
 *
 * During a trial, always: somebody who discovers the trial is over by finding the app locked has
 * been ambushed, and a countdown is what turns an ending into something expected.
 *
 * On a paid licence, only in the final month. Two years is long enough that a permanent countdown
 * would be noise for twenty-three of them, and a customer who paid should not be reminded daily that
 * their purchase is finite. Thirty days is enough warning to renew without hurry.
 *
 * ## What it does
 *
 * Clicking opens the purchase details — the device code, the QR code, and the price. The same
 * information the blocking screen shows, reachable before the blocking screen is the only way to see
 * it.
 */
@Composable
fun LicenseChip(
    status: LicenseStatus,
    languageTag: String,
    onOpenPurchase: () -> Unit,
) {
    val text = strings.licenseText
    val days = status.daysRemaining ?: return

    // A paid licence says nothing until its last month. See the note above.
    if (!status.isTrial && days > PAID_WARNING_DAYS) return

    val urgent = days <= URGENT_DAYS
    val label = labelFor(days, status.isTrial, text)

    BuroInteractiveRow(
        onClick = onOpenPurchase,
        selected = false,
        shape = RoundedCornerShape(8.dp),
        contentDescription = label,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (urgent) URGENT_BACKGROUND else BuroColors.SurfaceRaised)
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
            // The way to act on it, offered only while a trial is running or a licence is ending.
            // Once someone has paid and has time left, there is nothing here worth clicking.
            if (status.isTrial || urgent) {
                Spacer(Modifier.width(BuroSpacing.Xs))
                Text(
                    text = text.buyNow,
                    color = BuroColors.Primary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * The countdown, in words.
 *
 * Days throughout rather than switching to months or years. "359 dias" is a number somebody can act
 * on; "11 meses" invites them to work out what that means and put it off.
 */
private fun labelFor(days: Long, isTrial: Boolean, text: LicenseStrings): String =
    when {
        days <= 0L -> text.trialLastDay
        isTrial && days == 1L -> text.trialLastDay
        isTrial -> text.trialDaysLeft.format(days)
        days == 1L -> text.licenseLastDay
        else -> text.licenseDaysLeft.format(days)
    }

/** A paid licence is silent until this many days remain. */
private const val PAID_WARNING_DAYS = 30L

/** Below this the chip turns gold: near enough that leaving it costs an interruption. */
private const val URGENT_DAYS = 3L

private val URGENT_BACKGROUND = BuroColors.Primary.copy(alpha = 0.12f)

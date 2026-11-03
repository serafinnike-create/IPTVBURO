package com.lucasserafin94.iptvburo.desktop.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lucasserafin94.iptvburo.desktop.ui.BuroColors
import com.lucasserafin94.iptvburo.desktop.ui.BuroSpacing

/**
 * Says a newer version is out, in the header, and updates when clicked.
 *
 * The updater used to run only from a button inside Settings > Manutenção, so an install stayed on
 * whatever version it started with unless somebody went looking. The check now runs by itself;
 * downloading still waits for this click, since it ends with the app restarting.
 */
@Composable
fun UpdateChip(
    label: String,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        color = BuroColors.Primary,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier =
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(BuroColors.Primary.copy(alpha = 0.12f))
                .border(1.dp, BuroColors.Primary, RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = BuroSpacing.Sm, vertical = BuroSpacing.Xxs),
    )
}

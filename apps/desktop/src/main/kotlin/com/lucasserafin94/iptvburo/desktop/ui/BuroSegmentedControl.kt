package com.lucasserafin94.iptvburo.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * A row of mutually exclusive choices, of which exactly one is active.
 *
 * A segmented control rather than several filled buttons: only one option can be selected, and
 * competing gold buttons made every state read as "selected". The catalogue toolbar has used this
 * shape since the beginning; it is here so the other screens that need the same choice — continue
 * watching, downloads — use the same control rather than three near-copies that drift apart.
 *
 * @param options what to show, in order. Two or three; beyond that a control this wide stops being
 *   readable and the choice belongs in a menu.
 */
@Composable
fun <T> BuroSegmentedControl(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .clip(BuroRadius.Pill)
                .background(BuroColors.SurfaceRaised)
                .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { option ->
            val text = label(option)
            val isSelected = option == selected
            BuroInteractiveRow(
                onClick = { onSelect(option) },
                selected = isSelected,
                shape = BuroRadius.Pill,
                contentDescription = text,
            ) {
                Text(
                    text = text,
                    modifier = Modifier.padding(horizontal = BuroSpacing.Md, vertical = BuroSpacing.Xs),
                    color = if (isSelected) BuroColors.Primary else BuroColors.TextMuted,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
            }
        }
    }
}

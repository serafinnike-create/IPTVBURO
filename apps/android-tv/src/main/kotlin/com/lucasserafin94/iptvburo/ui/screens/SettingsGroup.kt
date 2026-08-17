package com.lucasserafin94.iptvburo.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucasserafin94.iptvburo.ui.theme.BuroGold
import com.lucasserafin94.iptvburo.ui.theme.BuroTextSecondary

/**
 * A heading over a run of related settings.
 *
 * The screen was a single column of unlabelled cards — the licence, then a metadata key, then
 * casting, then the cache, then subtitles, then the language — with nothing saying which of them
 * belonged together. Finding one meant reading all of them.
 *
 * Headings rather than collapsing sections or separate pages: everything stays on one scroll, so a
 * setting is never hidden behind a tap, and the eye can skip a whole group at once.
 */
@Composable
fun SettingsGroup(
    title: String,
    subtitle: String? = null,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title.uppercase(),
            color = BuroGold,
            fontSize = if (compact) 12.sp else 13.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.2.sp,
        )
        subtitle?.let { line ->
            Spacer(Modifier.height(3.dp))
            Text(
                text = line,
                color = BuroTextSecondary,
                fontSize = if (compact) 12.sp else 13.sp,
            )
        }
        Spacer(Modifier.height(if (compact) 10.dp else 12.dp))
        content()
    }
}

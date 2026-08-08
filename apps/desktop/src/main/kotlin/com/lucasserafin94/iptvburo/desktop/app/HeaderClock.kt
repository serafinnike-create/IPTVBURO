package com.lucasserafin94.iptvburo.desktop.app

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.lucasserafin94.iptvburo.desktop.ui.BuroColors
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The time and date in the header.
 *
 * Ticks once a minute rather than once a second: nothing here shows seconds, and a per-second
 * recomposition of the top bar is work for a display that would not change.
 */
@Composable
fun HeaderClock(
    uses24Hour: Boolean,
    languageTag: String,
    modifier: Modifier = Modifier,
) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            // Aligned to the next minute rather than sleeping a flat sixty seconds, so the display
            // changes when the minute does instead of drifting up to a minute behind it.
            delay(((60 - now.second) * 1_000L).coerceAtLeast(1_000L))
        }
    }

    val locale = runCatching { Locale.forLanguageTag(languageTag) }.getOrDefault(Locale.getDefault())
    val timePattern = if (uses24Hour) "HH:mm" else "h:mm a"

    Column(modifier = modifier, horizontalAlignment = Alignment.End) {
        Text(
            text = now.format(DateTimeFormatter.ofPattern(timePattern, locale)),
            color = BuroColors.Text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
        )
        Text(
            // Day and short month: enough to answer "what is today" without crowding the bar.
            text = now.format(DateTimeFormatter.ofPattern("EEE, d MMM", locale)),
            color = BuroColors.TextSubtle,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.End,
        )
    }
}

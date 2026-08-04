package com.lucasserafin94.iptvburo.desktop.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lucasserafin94.iptvburo.desktop.playback.TrailerBrowser
import com.lucasserafin94.iptvburo.desktop.ui.BuroColors
import com.lucasserafin94.iptvburo.desktop.ui.BuroRadius
import com.lucasserafin94.iptvburo.desktop.ui.BuroSpacing

/**
 * The trailer, played in a panel over the app rather than in the user's browser.
 *
 * Sending someone to Chrome to watch thirty seconds of footage takes them out of the app and, on a
 * television, out of the input they are holding. The embedded engine keeps it here.
 *
 * When Chromium cannot start — a machine missing the native libraries, most likely — [onFallback]
 * opens the browser instead. A trailer is an extra; it must never be the reason the app fails.
 */
@Composable
fun TrailerOverlay(
    youtubeId: String,
    title: String,
    onClose: () -> Unit,
    onFallback: () -> Unit,
) {
    val browser = remember(youtubeId) { TrailerBrowser() }
    DisposableEffect(browser) { onDispose { browser.dispose() } }

    val panel =
        remember(youtubeId) {
            browser.createComponent(youtubeId = youtubeId, autoplay = true, muted = false)
        }

    // Nothing to show: hand it to the browser and close, rather than presenting an empty black box
    // the user has to dismiss themselves.
    if (panel == null) {
        DisposableEffect(youtubeId) {
            onFallback()
            onClose()
            onDispose { }
        }
        return
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(BuroColors.Scrim)
                // A click outside closes it, the way any lightbox behaves.
                .clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = 1_100.dp)
                    .fillMaxWidth(0.82f)
                    .clip(BuroRadius.Large)
                    .background(BuroColors.Canvas)
                    // Consumes the click so pressing the video does not dismiss the panel under it.
                    .clickable(enabled = false) {}
                    .padding(BuroSpacing.Md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = BuroColors.Text,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClose) {
                    Text("✕", color = BuroColors.TextMuted, style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.height(BuroSpacing.Sm))
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(BuroRadius.Medium)
                        .background(Color.Black),
            ) {
                SwingPanel(factory = { panel }, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

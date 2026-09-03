package com.lucasserafin94.iptvburo.desktop.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.delay

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
    var failed by remember(youtubeId) { mutableStateOf(false) }
    var playing by remember(youtubeId) { mutableStateOf(false) }

    val panel =
        remember(youtubeId) {
            browser.createComponent(
                youtubeId = youtubeId,
                autoplay = true,
                muted = false,
                // Not the banner, despite the default.
                //
                // blendIntoHero defaults to true, so this lightbox was being served the hero page:
                // a bottom mask 46% tall at 96% opacity, a left mask half the width, and the player
                // blown up to 126vw to carry YouTube's controls out of view. In a box of its own
                // that combination is a black rectangle with sound playing behind it — reported as
                // the trailer screen going black while the audio ran.
                //
                // Here the controls are the point and the edges are the card's own, so the page
                // must be the plain one.
                blendIntoHero = false,
                onPlaying = { playing = true },
                onFailed = { failed = true },
            )
        }

    LaunchedEffect(panel, youtubeId) {
        if (panel == null) return@LaunchedEffect
        delay(10_000L)
        if (!playing) failed = true
    }

    // Nothing to show: hand it to the browser and close, rather than presenting an empty black box
    // the user has to dismiss themselves.
    if (panel == null || failed) {
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
                //
                // No indication: Material's default ripple on a window-sized target washes grey over
                // the whole screen on hover. Here it sits on top of a scrim that is deliberately dark
                // and even, so the ripple shows as a patch moving with the pointer.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose,
                ),
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
                SwingPanel(
                    factory = { panel },
                    modifier = Modifier.fillMaxSize(),
                    background = Color.Black,
                )
            }
        }
    }
}

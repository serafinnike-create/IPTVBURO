package com.lucasserafin94.iptvburo.desktop.app

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lucasserafin94.iptvburo.desktop.ui.BuroColors
import com.lucasserafin94.iptvburo.desktop.ui.BuroMark
import com.lucasserafin94.iptvburo.desktop.ui.BuroSpacing
import com.lucasserafin94.iptvburo.desktop.ui.strings

/**
 * What the app shows while the catalogue is being fetched.
 *
 * The work carries on behind this: the session is restored and the lists are loaded, so by the time
 * it clears there is something to look at. Before, the first thing a returning user met was an
 * empty library that filled in piecemeal, which reads as a broken app rather than a loading one.
 *
 * [message] describes the current step, so a slow provider looks like progress instead of a hang.
 */
@Composable
fun SplashScreen(
    message: String,
    progress: Float,
    /**
     * Whether this is the first launch after setting an account up.
     *
     * The wait is longest exactly then — the whole catalogue is being read for the first time — and
     * a user with no reason to expect it reads a long unexplained pause as a hang. Saying so, once,
     * costs nothing and is only shown when it is true.
     */
    isFirstRun: Boolean = false,
    /**
     * Artwork to drift behind the mark, as texture.
     *
     * Empty on a true first run — the catalogue is being read for the first time and there is
     * nothing to show yet — and the wall then uses the bundled fictional artwork rather than
     * depending on the network during the busiest part of startup.
     */
    backdropPosters: List<String> = emptyList(),
) {
    val transition = rememberInfiniteTransition(label = "splash")

    // A slow breath rather than a spinner. The mark is the brand; making it pulse says "working"
    // without putting a progress bar on a wait whose length nobody can predict.
    val breath by transition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1_800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "breath",
    )
    val glow by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.6f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1_800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "glow",
    )
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1_400, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "sweep",
    )

    Box(
        modifier = Modifier.fillMaxSize().background(BuroColors.Canvas),
        contentAlignment = Alignment.Center,
    ) {
        // Behind everything, and behind its own scrim: the mark and the progress bar are what the
        // user is reading, and posters bright enough to compete with them would be a worse screen
        // rather than a richer one.
        SplashPosterWall(posters = backdropPosters)

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                // A halo behind the mark, breathing with it. Gold on near-black is the app's whole
                // palette, so the loading screen looks like the product rather than a placeholder.
                Box(
                    modifier =
                        Modifier
                            .size(260.dp)
                            .alpha(glow)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        BuroColors.Primary.copy(alpha = 0.22f),
                                        Color.Transparent,
                                    ),
                                ),
                                shape = CircleShape,
                            ),
                )
                // Drawn, not loaded: the PNG carried an opaque black square that showed as a tile
                // around the ring, and its edge softened at this size on a scaled display.
                BuroMark(size = 132.dp, modifier = Modifier.scale(breath))
            }

            Spacer(Modifier.height(BuroSpacing.Xl))
            Text(
                text = "IPTV BURO",
                color = BuroColors.Text,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.height(BuroSpacing.Xs))
            Text(
                text = message,
                color = BuroColors.TextSubtle,
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(Modifier.height(BuroSpacing.Xl))
            // An indeterminate sweep: the provider never says how much is left, so a percentage
            // would be invented.
            Box(
                modifier =
                    Modifier
                        .width(260.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(BuroColors.Surface),
            ) {
                // A real measure, counted from the steps that actually happen. Eased so the bar
                // glides between them instead of jumping, which is what makes a five-step count
                // feel continuous without inventing progress it does not have.
                val settled by animateFloatAsState(
                    targetValue = progress.coerceIn(0f, 1f),
                    animationSpec = tween(durationMillis = 450),
                    label = "progress",
                )
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(settled)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(BuroColors.Primary),
                )
                // The sweep rides on top of whatever has been filled, so the bar still looks alive
                // during a long step rather than frozen at the same percentage.
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.3f)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color.Transparent,
                                        BuroColors.Primary.copy(alpha = 0.55f),
                                        Color.Transparent,
                                    ),
                                ),
                            )
                            .splashOffset(sweep),
                )
            }
            Spacer(Modifier.height(BuroSpacing.Sm))
            Text(
                text = "${(progress.coerceIn(0f, 1f) * 100).toInt()}%",
                color = BuroColors.Primary,
                style = MaterialTheme.typography.labelMedium,
            )

            if (isFirstRun) {
                val text = strings
                Spacer(Modifier.height(BuroSpacing.Xl))
                Column(
                    modifier = Modifier.widthIn(max = 420.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    FirstRunNote(title = text.settingsText.firstRunTitle, body = text.settingsText.firstRunBody)
                    Spacer(Modifier.height(BuroSpacing.Md))
                    // The TMDb key is the difference between a wall of titles and a wall of covers,
                    // and the moment the user is watching a progress bar is the one moment they
                    // have nothing else to do. Told here rather than buried in Settings.
                    FirstRunNote(
                        title = text.settingsText.firstRunTmdbTitle,
                        body = text.settingsText.firstRunTmdbBody,
                    )
                }
            }
        }
    }
}

/** One explanatory note under the progress bar: a heading and a sentence, centred. */
@Composable
private fun FirstRunNote(title: String, body: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = title,
            color = BuroColors.Text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = body,
            color = BuroColors.TextSubtle,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
    }
}

/** Slides the sweep across its track without laying out a new element on every frame. */
private fun Modifier.splashOffset(fraction: Float): Modifier =
    this.then(
        Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val travel = (constraints.maxWidth - placeable.width).coerceAtLeast(0)
            layout(constraints.maxWidth, placeable.height) {
                placeable.placeRelative((travel * fraction).toInt(), 0)
            }
        },
    )

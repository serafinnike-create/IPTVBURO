package com.lucasserafin94.iptvburo.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lucasserafin94.iptvburo.R
import com.lucasserafin94.iptvburo.ui.theme.BuroCanvas
import coil3.compose.AsyncImage
import kotlin.math.ceil

/** Animated real catalogue covers used while the app checks access and prepares the library. */
@Composable
fun BuroCinematicBackdrop(
    modifier: Modifier = Modifier,
    posterUrls: List<String> = emptyList(),
) {
    val usablePosters = posterUrls.filter(String::isNotBlank).distinct()
    val movement = rememberInfiniteTransition(label = "poster-wall")
    val firstProgress by
        movement.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 18_000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "poster-row-one",
        )
    val secondProgress by
        movement.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 23_000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "poster-row-two",
        )
    val thirdProgress by
        movement.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 27_000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "poster-row-three",
        )
    val posterCyclePixels = with(LocalDensity.current) { POSTER_CYCLE_WIDTH.toPx() }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(BuroCanvas)) {
        val portrait = maxHeight > maxWidth
        val rowStep = if (portrait) 184.dp else 188.dp
        // Two extra strips keep the top and bottom covered after the small alternating rotations.
        // The previous three hard-coded rows ended halfway down a tall phone, leaving a black lower
        // half that made the artwork look missing.
        val rowCount = ceil(maxHeight.value / rowStep.value).toInt() + 2

        repeat(rowCount) { row ->
            val progress =
                when (row % 3) {
                    0 -> firstProgress
                    1 -> secondProgress
                    else -> thirdProgress
                }
            val movesLeft = row % 2 == 1
            val translation =
                if (movesLeft) {
                    -posterCyclePixels * progress
                } else {
                    -posterCyclePixels * (1f - progress)
                }
            PosterStrip(
                offsetPixels = translation,
                posterUrls =
                    if (usablePosters.isNotEmpty()) {
                        List(POSTERS_PER_CYCLE) { index ->
                            usablePosters[(row * POSTERS_PER_CYCLE + index) % usablePosters.size]
                        }
                    } else {
                        emptyList()
                    },
                modifier =
                    Modifier
                        .offset(y = rowStep * row - 72.dp)
                        .graphicsLayer {
                            // Rotate around the visible edge. The strip is several screens wide;
                            // rotating around its centre pushed the first row down by hundreds of
                            // pixels on a phone and left the top of the background apparently blank.
                            transformOrigin = TransformOrigin(0f, 0.5f)
                            rotationZ = if (row % 2 == 0) -2.2f else 2.2f
                            scaleX = 1.06f
                            scaleY = 1.06f
                        },
            )
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            BuroCanvas.copy(alpha = 0.18f),
                            BuroCanvas.copy(alpha = 0.46f),
                            BuroCanvas.copy(alpha = 0.68f),
                        ),
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            BuroCanvas.copy(alpha = if (portrait) 0.30f else 0.64f),
                            Color.Transparent,
                            BuroCanvas.copy(alpha = 0.34f),
                        ),
                    ),
                ),
        )
    }
}

@Composable
private fun PosterStrip(
    offsetPixels: Float,
    posterUrls: List<String>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier.graphicsLayer {
                translationX = offsetPixels
                alpha = 0.82f
            },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Six identical cycles cover phones, tablets and 4K televisions while one complete cycle
        // slides through. Restarting at exactly the cycle width is visually seamless.
        repeat(6) {
            if (posterUrls.isNotEmpty()) {
                posterUrls.forEachIndexed { index, artworkUrl ->
                    val localFallback = painterResource(POSTER_ART[index % POSTER_ART.size])
                    AsyncImage(
                        model = artworkUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        placeholder = localFallback,
                        error = localFallback,
                        fallback = localFallback,
                        modifier = posterModifier(index),
                    )
                }
            } else {
                POSTER_ART.forEachIndexed { index, artwork ->
                    Image(
                        painter = painterResource(artwork),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = posterModifier(index),
                    )
                }
            }
        }
    }
}

private fun posterModifier(index: Int): Modifier =
    Modifier
        .padding(top = if (index % 2 == 0) 12.dp else 0.dp)
        .width(116.dp)
        .height(174.dp)
        .clip(RoundedCornerShape(14.dp))

private val POSTER_ART: List<Int> =
    listOf(
        R.drawable.buro_forest_signal,
        R.drawable.buro_paper_sun,
        R.drawable.buro_nocturne_hero,
        R.drawable.buro_category_atlas_v1,
    )

/** Four 116 dp covers plus the four 12 dp gaps up to the next identical cycle. */
private val POSTER_CYCLE_WIDTH: Dp = 512.dp

private const val POSTERS_PER_CYCLE = 4

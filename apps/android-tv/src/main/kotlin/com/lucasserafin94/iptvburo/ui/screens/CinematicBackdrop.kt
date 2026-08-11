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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.lucasserafin94.iptvburo.R
import com.lucasserafin94.iptvburo.ui.theme.BuroCanvas
import kotlin.math.roundToInt

/** Animated local artwork used while the app checks access and prepares the library. */
@Composable
fun BuroCinematicBackdrop(modifier: Modifier = Modifier) {
    val movement = rememberInfiniteTransition(label = "poster-wall")
    val firstOffset by
        movement.animateFloat(
            initialValue = -520f,
            targetValue = 0f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 18_000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "poster-row-one",
        )
    val secondOffset by
        movement.animateFloat(
            initialValue = 0f,
            targetValue = -520f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 23_000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "poster-row-two",
        )

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(BuroCanvas)) {
        val portrait = maxHeight > maxWidth
        PosterStrip(
            offsetPixels = firstOffset,
            modifier =
                Modifier
                    .offset(y = if (portrait) 16.dp else (-52).dp)
                    .rotate(-4f)
                    .scale(1.08f),
        )
        PosterStrip(
            offsetPixels = secondOffset,
            modifier =
                Modifier
                    .offset(y = if (portrait) 224.dp else 176.dp)
                    .rotate(3f)
                    .scale(1.06f),
        )
        if (portrait) {
            PosterStrip(
                offsetPixels = firstOffset * 0.72f,
                modifier = Modifier.offset(y = 438.dp).rotate(-2f).scale(1.1f),
            )
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            BuroCanvas.copy(alpha = 0.24f),
                            BuroCanvas.copy(alpha = 0.72f),
                            BuroCanvas.copy(alpha = 0.96f),
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
                            BuroCanvas.copy(alpha = if (portrait) 0.50f else 0.92f),
                            Color.Transparent,
                            BuroCanvas.copy(alpha = 0.56f),
                        ),
                    ),
                ),
        )
    }
}

@Composable
private fun PosterStrip(
    offsetPixels: Float,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier.graphicsLayer {
                translationX = offsetPixels.roundToInt().toFloat()
                alpha = 0.62f
            },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(4) {
            POSTER_ART.forEachIndexed { index, artwork ->
                Image(
                    painter = painterResource(artwork),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .padding(top = if (index % 2 == 0) 12.dp else 0.dp)
                            .width(116.dp)
                            .height(174.dp)
                            .clip(RoundedCornerShape(14.dp)),
                )
            }
        }
    }
}

private val POSTER_ART: List<Int> =
    listOf(
        R.drawable.buro_forest_signal,
        R.drawable.buro_paper_sun,
        R.drawable.buro_nocturne_hero,
        R.drawable.buro_category_atlas_v1,
    )

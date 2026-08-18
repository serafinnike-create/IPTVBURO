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
import androidx.compose.runtime.remember
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
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
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
        // As many identical cycles as the screen actually needs, rather than a fixed six.
        //
        // Six cycles is 3072 dp of strip. That was chosen to cover a 4K television, but a phone is
        // 360 dp wide, so it drew about eight screens of posters to show one — 168 `AsyncImage`s on
        // a 360x820 device. Every one of them is composed, measured and requested: the wall issued
        // 168 concurrent image requests, of which 84 were cancelled when the boot screen ended and
        // the first decode only landed 6.6 seconds in, which is longer than the boot screen lives.
        // That is why the wall showed its bundled placeholders and never the catalogue's covers.
        //
        // One cycle covers the screen, a second supplies what slides in during the animation, and
        // the scale and rotation above need a little more than the raw width. A television still
        // gets its six; a phone now gets two.
        val cycleCount = posterCyclesFor(maxWidth.value)

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
                cycleCount = cycleCount,
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
    cycleCount: Int,
    offsetPixels: Float,
    posterUrls: List<String>,
    modifier: Modifier = Modifier,
) {
    // Resolved once for the strip rather than once per poster. These are the same four drawables
    // every cycle, and reading them inside the loop meant a resource lookup for each of the
    // strip's images on every recomposition of an animation that never stops.
    val localArt = POSTER_ART.map { artwork -> painterResource(artwork) }
    Row(
        modifier =
            modifier.graphicsLayer {
                translationX = offsetPixels
                alpha = 0.82f
            },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Identical cycles, enough to cover this screen while one complete cycle slides through.
        // Restarting at exactly the cycle width is visually seamless.
        repeat(cycleCount) {
            if (posterUrls.isNotEmpty()) {
                posterUrls.forEachIndexed { index, artworkUrl ->
                    val localFallback = localArt[index % localArt.size]
                    AsyncImage(
                        model = rememberWallRequest(artworkUrl),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        placeholder = localFallback,
                        error = localFallback,
                        fallback = localFallback,
                        modifier = posterModifier(index),
                    )
                }
            } else {
                localArt.forEachIndexed { index, artwork ->
                    Image(
                        painter = artwork,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = posterModifier(index),
                    )
                }
            }
        }
    }
}

/**
 * The request for one cover on the wall, built once per URL.
 *
 * Two things this needs that a plain string model does not give it.
 *
 * It is remembered, so a cover that stays on the wall is not a fresh request on every frame of an
 * animation that never stops. And it skips the disk cache, which is what stopped the wall working
 * at all: Coil serialises disk-cache access through a single lane, the wall asks for dozens of
 * covers in the same sixth of a second, and on a cold start not one of those requests reached the
 * screen before the loading screen was gone. Measured on a phone: 58 requests in, 0 out. With the
 * disk cache out of the way, 46 completed and the covers appeared.
 *
 * Skipping it costs nothing here. These are decorative, they are already in the memory cache while
 * the screen is up, and the catalogue's own screens still cache the same artwork normally.
 */
@Composable
private fun rememberWallRequest(artworkUrl: String): ImageRequest {
    val context = LocalPlatformContext.current
    return remember(artworkUrl, context) {
        ImageRequest.Builder(context)
            .data(artworkUrl)
            .diskCachePolicy(CachePolicy.DISABLED)
            .build()
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

/**
 * How many identical poster cycles a screen this wide needs.
 *
 * One to cover the screen, one to slide in behind it, and a margin for the scale and rotation the
 * strips are drawn with. Bounded at both ends: a phone must not draw a television's worth of
 * posters, and nothing wider than 4K gains from more than the ceiling.
 */
internal fun posterCyclesFor(widthDp: Float): Int =
    (ceil(widthDp * WALL_OVERDRAW / POSTER_CYCLE_WIDTH.value).toInt() + 1)
        .coerceIn(MINIMUM_POSTER_CYCLES, MAXIMUM_POSTER_CYCLES)

/** Four 116 dp covers plus the four 12 dp gaps up to the next identical cycle. */
private val POSTER_CYCLE_WIDTH: Dp = 512.dp

private const val POSTERS_PER_CYCLE = 4

/** The 116x174 dp cover at the density this app's phones and televisions actually use. */
private const val POSTER_PIXEL_WIDTH = 232

private const val POSTER_PIXEL_HEIGHT = 348

/**
 * How much wider than the screen a strip is drawn.
 *
 * The strips are scaled to 1.06 and rotated by 2.2 degrees around their leading edge, both of which
 * push their far end past the right of the screen. This covers that without measuring it exactly.
 */
private const val WALL_OVERDRAW = 1.2f

/** One cycle to cover the screen and one to slide in behind it. */
private const val MINIMUM_POSTER_CYCLES = 2

/** What a 4K television needs, and the ceiling for anything wider. */
private const val MAXIMUM_POSTER_CYCLES = 6

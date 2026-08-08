package com.lucasserafin94.iptvburo.desktop.app

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.lucasserafin94.iptvburo.desktop.ui.BuroColors
import com.lucasserafin94.iptvburo.desktop.ui.BuroRadius
import com.lucasserafin94.iptvburo.desktop.ui.BuroRemoteArtwork

/**
 * A slow wall of posters behind loading and licence screens.
 *
 * Three columns drifting at different speeds, heavily dimmed, with the app's own dark gradient over
 * the top so the mark and the progress bar stay the thing being read. The posters are texture, not
 * content: nothing here is clickable and nothing is labelled.
 *
 * ## What it costs
 *
 * Deliberately little, because this runs while the app is at its busiest — restoring a session and
 * reading a catalogue of tens of thousands of items. Three columns of six posters is eighteen
 * images at most; the animation is one float per column driving an offset, which is a translation
 * rather than a re-layout. It draws nothing at all when the caller has no posters to give, which is
 * the case on a true first run.
 *
 * @param posters artwork URLs to drift. Fewer than [MINIMUM_POSTERS] uses the bundled fictional
 *   collage, so the first launch is cinematic before a catalogue exists.
 */
@Composable
fun SplashPosterWall(
    posters: List<String>,
    modifier: Modifier = Modifier,
) {
    if (posters.size < MINIMUM_POSTERS) {
        Box(modifier = modifier.fillMaxSize().testTag(POSTER_WALL_TAG)) {
            LocalCinematicPosterWall(Modifier.fillMaxSize())
        }
        return
    }

    // Split into columns once, not on every frame. Shuffled with a fixed seed so the wall
    // looks arranged rather than alphabetical, and is the same for the whole of one launch.
    val columns =
        remember(posters) {
            // A fixed seed, so the arrangement is stable for the whole of one launch rather than
            // reshuffling on every recomposition — which would make the wall flicker.
            posters
                .shuffled(kotlin.random.Random(SHUFFLE_SEED))
                .chunked((posters.size / COLUMN_COUNT).coerceAtLeast(2))
                .take(COLUMN_COUNT)
        }

    val transition = rememberInfiniteTransition(label = "poster-wall")

    Box(modifier = modifier.fillMaxSize().clipToBounds().testTag(POSTER_WALL_TAG)) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(POSTER_GAP),
        ) {
            columns.forEachIndexed { index, column ->
                // Each column drifts at its own pace and direction, which is what stops the wall
                // reading as one sliding sheet.
                val drift by transition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec =
                        infiniteRepeatable(
                            animation = tween(
                                durationMillis = DRIFT_BASE_MILLIS + index * DRIFT_STAGGER_MILLIS,
                                easing = LinearEasing,
                            ),
                            repeatMode = RepeatMode.Reverse,
                        ),
                    label = "drift-$index",
                )
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .graphicsLayer {
                                // Adjacent columns travel in opposite directions. Both stay at or
                                // above their original top edge, so movement never reveals an empty
                                // strip behind the first card.
                                translationY =
                                    if (index % 2 == 0) {
                                        (drift - 1f) * DRIFT_DISTANCE.toPx()
                                    } else {
                                        -drift * DRIFT_DISTANCE.toPx()
                                    }
                            },
                    verticalArrangement = Arrangement.spacedBy(POSTER_GAP),
                ) {
                    column.forEach { url ->
                        BuroRemoteArtwork(
                            artworkUrl = url,
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(POSTER_RATIO)
                                    .clip(BuroRadius.Medium),
                            contentScale = ContentScale.Crop,
                        ) {
                            // No letter fallback here: an empty tile is texture, a letter is noise.
                            Box(modifier = Modifier.fillMaxSize().background(BuroColors.Surface))
                        }
                    }
                }
            }
        }

        // The scrim, and the reason this can be a wall of bright posters without competing with the
        // progress bar. Darkest in the middle, where the mark and the text sit.
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors =
                                listOf(
                                    BuroColors.Canvas.copy(alpha = SCRIM_CENTRE),
                                    BuroColors.Canvas.copy(alpha = SCRIM_EDGE),
                                ),
                        ),
                    ),
        )
    }
}

/**
 * First-run backdrop, available before a provider has supplied any artwork.
 *
 * Every scene is an original fictional image shipped with the app. A very slow pan, scale and
 * fraction-of-a-degree rotation gives the requested carousel feeling without turning the loading
 * screen into a distracting spinner or depending on the network at its busiest moment.
 */
@Composable
private fun LocalCinematicPosterWall(modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "local-poster-wall")
    val drift by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = LOCAL_DRIFT_MILLIS, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "local-poster-drift",
    )

    Box(modifier = modifier.fillMaxSize().clipToBounds().testTag(LOCAL_POSTER_WALL_TAG)) {
        Image(
            painter = painterResource("brand/buro-cinematic-poster-wall.png"),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.10f + drift * 0.018f
                        scaleY = 1.10f + drift * 0.018f
                        translationX = drift * 56f
                        translationY = -drift * 24f
                        rotationZ = drift * 0.45f
                    },
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors =
                                listOf(
                                    BuroColors.Canvas.copy(alpha = SCRIM_CENTRE),
                                    BuroColors.Canvas.copy(alpha = SCRIM_EDGE),
                                ),
                        ),
                    ),
        )
    }
}

/** Below this the wall looks like a failed load rather than a backdrop. */
private const val MINIMUM_POSTERS = 9

private const val COLUMN_COUNT = 4

/** Any constant will do; what matters is that it does not change between recompositions. */
private const val SHUFFLE_SEED = 20_260_807

/** Posters are 2:3, as every provider and TMDb serve them. */
private const val POSTER_RATIO = 2f / 3f

private val POSTER_GAP = 12.dp

/** How far a column travels. Small: this is a drift, not a carousel. */
private val DRIFT_DISTANCE = 96.dp

/** Slow enough to read as ambient rather than as something demanding attention. */
private const val DRIFT_BASE_MILLIS = 14_000
private const val DRIFT_STAGGER_MILLIS = 2_500

private const val LOCAL_DRIFT_MILLIS = 20_000

/** Strongest where the text sits, while the covers stay clearly visible around the edges. */
private const val SCRIM_CENTRE = 0.86f
private const val SCRIM_EDGE = 0.44f

/** Stable semantics hooks: the visual regression test proves both backdrops are actually composed. */
internal const val POSTER_WALL_TAG = "cinematic-poster-wall"
internal const val LOCAL_POSTER_WALL_TAG = "cinematic-poster-wall-local"

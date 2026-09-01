package com.lucasserafin94.iptvburo.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.lucasserafin94.iptvburo.ui.screens.MutedTrailerBackdrop
import com.lucasserafin94.iptvburo.R
import com.lucasserafin94.iptvburo.ui.ChannelUi
import com.lucasserafin94.iptvburo.ui.components.FocusSurface
import com.lucasserafin94.iptvburo.ui.designsystem.BuroButton
import com.lucasserafin94.iptvburo.ui.designsystem.BuroButtonStyle
import com.lucasserafin94.iptvburo.ui.designsystem.BuroEmptyState
import com.lucasserafin94.iptvburo.ui.theme.BuroAccent
import com.lucasserafin94.iptvburo.ui.theme.BuroCanvas
import com.lucasserafin94.iptvburo.ui.theme.BuroDanger
import com.lucasserafin94.iptvburo.ui.theme.BuroGold
import com.lucasserafin94.iptvburo.ui.theme.BuroSurface
import com.lucasserafin94.iptvburo.ui.theme.BuroTextPrimary
import com.lucasserafin94.iptvburo.ui.theme.BuroTextSecondary
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * One card at a time, kept or skipped.
 *
 * The deck comes from `DiscoveryDeck`, which ranks the catalogue against what this profile already
 * favourites and watches — the screen only draws what it is handed and reports the verdict back.
 * Keeping the ranking out of here is what lets it be tested without a device.
 *
 * ## Why the card moves with the finger
 *
 * A pair of buttons would be less code and a worse feature. The appeal of this pattern is that the
 * decision is *felt*: the card follows the drag, tilts, and shows which way it is going before it is
 * let go — so somebody can change their mind halfway. The buttons are kept for people who would
 * rather tap, and for anyone using a remote or a keyboard, where a drag is not available at all.
 */
@Composable
fun DiscoverScreen(
    deck: List<ChannelUi>,
    /** Plot, genres and rating for the card on top, when the provider has them. */
    detailsFor: (ChannelUi) -> DiscoverCardDetails,
    isLoading: Boolean,
    hasCatalogue: Boolean,
    onKeep: (ChannelUi) -> Unit,
    onSkip: (ChannelUi) -> Unit,
    onOpenDetails: (ChannelUi) -> Unit,
    onDealAgain: () -> Unit,
    onBack: () -> Unit,
    /** Total the deck started with, so the counter can say "3 of 15" rather than just what is left. */
    dealtCount: Int,
    /**
     * The trailer for the card on top, when there is one that plays.
     *
     * The same lookup the home banner uses, so a title with a trailer there has one here rather
     * than the two screens disagreeing about the same film.
     */
    trailerFor: (ChannelUi) -> String? = { null },
    /** Asks for that card's trailer. Called for the card on top, not for the whole deck. */
    onNeedTrailer: (ChannelUi) -> Unit = {},
    /** Whether the trailer carries sound, shared with the banner so the choice is made once. */
    trailerSoundOn: Boolean = false,
) {
    // Asked for as the card reaches the top, not for the whole hand: most of a hand is never seen,
    // and looking every card up would be a request per title for one viewing.
    val top = deck.firstOrNull()
    LaunchedEffect(top?.id) { top?.let(onNeedTrailer) }
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(BuroCanvas)) {
        val compact = maxWidth < 600.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (compact) 16.dp else 42.dp, vertical = if (compact) 14.dp else 28.dp),
        ) {
            ScreenHeader(
                title = stringResource(R.string.buro_nav_discover),
                subtitle = stringResource(R.string.discover_subtitle),
                onBack = onBack,
            )
            Spacer(Modifier.height(if (compact) 10.dp else 18.dp))

            when {
                isLoading ->
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.discover_loading),
                            color = BuroTextSecondary,
                        )
                    }

                !hasCatalogue ->
                    BuroEmptyState(
                        title = stringResource(R.string.discover_needs_catalogue),
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )

                deck.isEmpty() ->
                    BuroEmptyState(
                        title = stringResource(R.string.discover_empty_title),
                        message = stringResource(R.string.discover_empty_body),
                        actionLabel = stringResource(R.string.discover_again),
                        onAction = onDealAgain,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        icon = {
                            Icon(
                                Icons.Default.Explore,
                                contentDescription = null,
                                tint = BuroTextSecondary,
                                modifier = Modifier.size(44.dp),
                            )
                        },
                    )

                else -> {
                    // Position in the hand, so the deck feels finite — the whole point of dealing
                    // fifteen rather than an endless feed.
                    val seen = (dealtCount - deck.size + 1).coerceAtLeast(1)
                    Text(
                        text = stringResource(R.string.discover_remaining, seen, dealtCount.coerceAtLeast(seen)),
                        color = BuroTextSecondary,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        // Only the next card is drawn behind, not the whole deck: it is what gives
                        // the pile depth, and stacking fifteen posters would decode fifteen images
                        // to show slivers of fourteen of them.
                        deck.getOrNull(1)?.let { behind ->
                            DiscoverCard(
                                channel = behind,
                                details = detailsFor(behind),
                                compact = compact,
                                modifier = Modifier.graphicsLayer {
                                    scaleX = 0.94f
                                    scaleY = 0.94f
                                    translationY = 18.dp.toPx()
                                    alpha = 0.55f
                                },
                            )
                        }
                        SwipeableCard(
                            channel = deck.first(),
                            details = detailsFor(deck.first()),
                            compact = compact,
                            onKeep = onKeep,
                            onSkip = onSkip,
                            // Only the top card: the one behind is a sliver giving the pile depth,
                            // and a video playing where nobody can see it is a wasted player.
                            trailerId = trailerFor(deck.first()),
                            trailerSoundOn = trailerSoundOn,
                        )
                    }
                    Spacer(Modifier.height(if (compact) 10.dp else 16.dp))
                    DiscoverActions(
                        onSkip = { onSkip(deck.first()) },
                        onKeep = { onKeep(deck.first()) },
                        onOpenDetails = { onOpenDetails(deck.first()) },
                        compact = compact,
                    )
                }
            }
        }
    }
}

/**
 * The top card, which follows the finger and decides on release.
 *
 * Keyed on the channel id so the drag state resets for each new card: without that key the next
 * card would inherit the last one's offset and start halfway off screen.
 */
@Composable
private fun SwipeableCard(
    channel: ChannelUi,
    details: DiscoverCardDetails,
    compact: Boolean,
    onKeep: (ChannelUi) -> Unit,
    onSkip: (ChannelUi) -> Unit,
    /** The trailer for this card, and whether it carries sound. Only the top card gets one. */
    trailerId: String? = null,
    trailerSoundOn: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    // Live drag position, updated synchronously on every pointer delta — snapTo never animates
    // anything, so tracking the drag itself never needed a coroutine per pixel moved.
    var dragOffsetX by remember(channel.id) { mutableFloatStateOf(0f) }
    // Only the release transitions (spring back, fly away) are actual animations, so this is the
    // only place that still needs an Animatable.
    val releaseOffsetX = remember(channel.id) { Animatable(0f) }
    var isAnimatingRelease by remember(channel.id) { mutableStateOf(false) }
    var decided by remember(channel.id) { mutableStateOf(false) }
    val thresholdPx = with(density) { DECISION_THRESHOLD.toPx() }
    val flyAwayPx = with(density) { FLY_AWAY_DISTANCE.toPx() }

    val offsetX = if (isAnimatingRelease) releaseOffsetX.value else dragOffsetX

    // The verdict a release at the current offset would give, or null while it is still undecided.
    val leaning =
        when {
            offsetX > thresholdPx -> true
            offsetX < -thresholdPx -> false
            else -> null
        }

    Box(
        modifier = Modifier
            .graphicsLayer {
                translationX = offsetX
                // A slight tilt, in proportion to the drag. This is what makes the card feel like a
                // physical thing being tossed rather than a rectangle sliding sideways.
                rotationZ = (offsetX / thresholdPx).coerceIn(-1f, 1f) * MAX_TILT_DEGREES
            }
            .pointerInput(channel.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (decided) return@detectHorizontalDragGestures
                        val verdict =
                            when {
                                dragOffsetX > thresholdPx -> true
                                dragOffsetX < -thresholdPx -> false
                                else -> null
                            }
                        isAnimatingRelease = true
                        if (verdict == null) {
                            // Short of the threshold: springs back, so a hesitant drag is not a
                            // decision. Being able to change your mind halfway is the point.
                            scope.launch {
                                releaseOffsetX.snapTo(dragOffsetX)
                                releaseOffsetX.animateTo(0f, tween(FLY_BACK_MILLIS))
                                isAnimatingRelease = false
                                dragOffsetX = 0f
                            }
                        } else {
                            decided = true
                            scope.launch {
                                releaseOffsetX.snapTo(dragOffsetX)
                                releaseOffsetX.animateTo(
                                    if (verdict) flyAwayPx else -flyAwayPx,
                                    tween(FLY_AWAY_MILLIS),
                                )
                                if (verdict) onKeep(channel) else onSkip(channel)
                            }
                        }
                    },
                ) { _, dragAmount ->
                    if (!decided) {
                        dragOffsetX += dragAmount
                    }
                }
            },
    ) {
        DiscoverCard(
            channel = channel,
            details = details,
            compact = compact,
            leaning = leaning,
            trailerId = trailerId,
            trailerSoundOn = trailerSoundOn,
        )
    }
}

/** Plot, genres and rating as the card needs them. Absent fields simply do not draw. */
data class DiscoverCardDetails(
    val plot: String? = null,
    val genres: List<String> = emptyList(),
    val rating: Double? = null,
    val year: Int? = null,
)

@Composable
private fun DiscoverCard(
    channel: ChannelUi,
    details: DiscoverCardDetails,
    compact: Boolean,
    modifier: Modifier = Modifier,
    /** True while a release would keep, false while it would skip, null while undecided. */
    leaning: Boolean? = null,
    /**
     * The trailer to play over the artwork, or null to leave the poster alone.
     *
     * Only ever passed for the card on top. The one behind it is a sliver of a still poster giving
     * the pile depth, and a video playing in a sliver nobody can see is a wasted player.
     */
    trailerId: String? = null,
    /** Whether that trailer carries sound. */
    trailerSoundOn: Boolean = false,
) {
    // Poster above, facts below, rather than text laid over the artwork.
    //
    // The overlay version put the title, the year and four lines of synopsis across the bottom
    // third of the image — on the one screen whose entire job is showing you a cover. Sitting the
    // text in its own panel underneath gives the artwork back that third, and reads as a card in
    // the physical sense: a picture with a caption, not a picture with writing on it.
    Column(
        modifier = modifier
            .fillMaxWidth(if (compact) 1f else 0.62f)
            .fillMaxHeight()
            .clip(RoundedCornerShape(22.dp))
            .background(BuroSurface),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // Weighted rather than a fixed aspect ratio: posters arrive in several shapes, and
                // the card has to fill the space it is given on any screen without the panel below
                // being pushed off the bottom.
                .weight(1f),
        ) {
            channel.logoUrl?.let { poster ->
                AsyncImage(
                    model = poster,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // The trailer plays over the artwork, which stays underneath as the fallback.
            //
            // MutedTrailerBackdrop removes itself from the composition when the embed fails rather
            // than fading to nothing, so a trailer that will not load leaves the poster exactly as
            // it was. It also starts muted, because no engine autoplays audio.
            if (trailerId != null) {
                MutedTrailerBackdrop(
                    youtubeId = trailerId,
                    modifier = Modifier.fillMaxSize(),
                    soundOn = trailerSoundOn,
                )
            }
            // A short wash where the artwork meets the panel, so a bright poster does not end in a
            // hard line against the dark surface below it.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Transparent, Color(0x66090A0D)),
                        ),
                    ),
            )

            // The verdict, shown while the card is being dragged far enough to mean it. On the
            // artwork rather than the whole card: it is the thing being thrown, and a wash over the
            // synopsis would obscure the text somebody is still reading to decide.
            leaning?.let { keeping ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            (if (keeping) BuroGold else BuroDanger).copy(alpha = VERDICT_WASH_ALPHA),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (keeping) Icons.Default.Check else Icons.Default.Close,
                        contentDescription = null,
                        tint = BuroTextPrimary,
                        modifier = Modifier.size(96.dp),
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(BuroSurface)
                .padding(horizontal = 18.dp, vertical = if (compact) 14.dp else 18.dp),
        ) {
            Text(
                text = channel.name,
                color = BuroTextPrimary,
                fontSize = if (compact) 20.sp else 24.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val facts =
                listOfNotNull(
                    details.year?.toString(),
                    details.rating?.takeIf { it > 0.0 }?.let { "★ %.1f".format(it) },
                    details.genres.take(3).joinToString(" · ").takeIf(String::isNotBlank),
                ).joinToString("  •  ")
            if (facts.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(text = facts, color = BuroAccent, fontSize = 13.sp, maxLines = 1)
            }
            details.plot?.takeIf(String::isNotBlank)?.let { plot ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = plot,
                    color = BuroTextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    // Shorter than the overlay version carried: the panel takes real height from the
                    // poster now, and somebody who wants the whole synopsis opens the title rather
                    // than reading it here.
                    maxLines = if (compact) 3 else 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The two buttons under the deck.
 *
 * Kept alongside the gesture rather than replaced by it: a drag is unavailable on a remote or a
 * keyboard, and some people simply prefer a target to hit. Large and far apart, because the two
 * outcomes are opposite and a mis-tap files something into favourites.
 */
@Composable
private fun DiscoverActions(
    onSkip: () -> Unit,
    onKeep: () -> Unit,
    onOpenDetails: () -> Unit,
    compact: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VerdictButton(
            icon = Icons.Default.Close,
            tint = BuroDanger,
            label = stringResource(R.string.discover_skip),
            onClick = onSkip,
            compact = compact,
        )
        Spacer(Modifier.width(if (compact) 24.dp else 42.dp))
        VerdictButton(
            icon = Icons.Default.Check,
            tint = BuroGold,
            label = stringResource(R.string.discover_keep),
            onClick = onKeep,
            compact = compact,
        )
        Spacer(Modifier.width(if (compact) 24.dp else 42.dp))
        VerdictButton(
            icon = Icons.Default.Info,
            tint = BuroTextSecondary,
            label = stringResource(R.string.discover_details),
            onClick = onOpenDetails,
            compact = compact,
        )
    }
}

@Composable
private fun VerdictButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    label: String,
    onClick: () -> Unit,
    compact: Boolean,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FocusSurface(
            onClick = onClick,
            modifier = Modifier.size(if (compact) 62.dp else 70.dp),
            shape = CircleShape,
            backgroundColor = BuroSurface,
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(if (compact) 30.dp else 34.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(text = label, color = BuroTextSecondary, fontSize = 12.sp)
    }
}

/** How far the card must travel before releasing it counts as a decision. */
private val DECISION_THRESHOLD = 110.dp

/** Far enough that the card is off screen on any phone before the next one is dealt. */
private val FLY_AWAY_DISTANCE = 720.dp

private const val MAX_TILT_DEGREES = 12f
private const val VERDICT_WASH_ALPHA = 0.32f
private const val FLY_BACK_MILLIS = 220
private const val FLY_AWAY_MILLIS = 260

package com.lucasserafin94.iptvburo.desktop.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.lucasserafin94.iptvburo.desktop.ui.BuroColors
import com.lucasserafin94.iptvburo.desktop.ui.BuroInteractiveRow
import com.lucasserafin94.iptvburo.desktop.ui.BuroRadius
import com.lucasserafin94.iptvburo.desktop.ui.BuroRemoteArtwork
import com.lucasserafin94.iptvburo.desktop.ui.BuroSpacing
import com.lucasserafin94.iptvburo.desktop.ui.editorialTitle
import com.lucasserafin94.iptvburo.desktop.ui.strings
import androidx.compose.foundation.focusable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.lucasserafin94.iptvburo.domain.model.DiscoveryDeck
import com.lucasserafin94.iptvburo.domain.model.DiscoveryVerdict
import com.lucasserafin94.iptvburo.xtream.XtreamCatalogItem
import kotlinx.coroutines.delay

/**
 * One title at a time: keep it or pass over it.
 *
 * A deck rather than a grid, and the difference is the point. A grid asks somebody to compare forty
 * things and choose; a card asks one question about one film, which is a decision anybody can make
 * in a second. What it buys the app is the answers — a grid teaches it nothing, while every swipe
 * here says something about what this viewer likes.
 *
 * Keeping adds the title to favourites, so the game leaves something behind. A swipe that only
 * advanced a deck would take the viewer's attention and give nothing back.
 */
@Composable
fun DiscoveryScreen(
    deck: List<XtreamCatalogItem>,
    loading: Boolean,
    synopsisFor: (XtreamCatalogItem) -> String?,
    genresFor: (XtreamCatalogItem) -> List<String>,
    onDecide: (XtreamCatalogItem, DiscoveryVerdict) -> Unit,
    /**
     * Opens the title's full page.
     *
     * The card carries a synopsis only once TMDb has answered for that title, and until then there
     * is nothing on screen but a poster, a year and a genre — which is not enough to decide on, and
     * was reported as such. This is the way to read the rest without leaving a verdict.
     */
    onOpenDetails: (XtreamCatalogItem) -> Unit,
    onAnother: () -> Unit,
    /**
     * The trailer for a card, when there is one that plays.
     *
     * The same lookup the home banner uses, and deliberately so: a title with a trailer there has
     * one here rather than the two screens disagreeing about the same film.
     */
    trailerFor: (XtreamCatalogItem) -> String? = { null },
    /** Asks for a card's trailer. Called for the card on top, not for the whole deck. */
    onNeedTrailer: (XtreamCatalogItem) -> Unit = {},
    /** Reported when the player will not play, so the next card is not made to wait for it too. */
    onTrailerFailed: (XtreamCatalogItem) -> Unit = {},
    /**
     * Moves past a card whose trailer has finished its while, without filing a verdict.
     *
     * Not [onDecide]: nobody judged this film, and recording a rejection for a card that ran out
     * the clock would teach the taste profile something the viewer never said.
     */
    onPassOver: (XtreamCatalogItem) -> Unit = {},
    /** Whether the trailer carries sound, shared with the banner so the choice is made once. */
    soundOn: Boolean = false,
    onToggleSound: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val text = strings.shareStrings.discovery
    val top = deck.firstOrNull()

    // Asked for as the card reaches the top, not for the whole deck: most of a deck is never seen,
    // and looking every card up would be a request per title for one viewing.
    LaunchedEffect(top?.providerId) { top?.let(onNeedTrailer) }

    val trailerId = top?.let(trailerFor)

    /**
     * A card whose trailer has played its while moves on by itself.
     *
     * Only when there is a trailer: a card showing a still poster has nothing to finish, so it
     * waits for a verdict rather than sliding away from somebody still reading it. Passing on is
     * not a verdict either — skipping would teach the app that a film nobody judged was unwanted.
     */
    LaunchedEffect(top?.providerId, trailerId) {
        if (top == null || !DiscoveryDeck.advancesOnItsOwn(trailerId)) return@LaunchedEffect
        delay(DiscoveryDeck.TRAILER_HOLD_MILLIS)
        onPassOver(top)
    }

    /**
     * The screen has to hold focus to receive keys at all.
     *
     * Requested once, when the deck first has something on it. Without this the handler below is
     * attached and simply never fires, which looks exactly like the keys not being wired up.
     */
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(top != null) {
        if (top != null) runCatching { focusRequester.requestFocus() }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusable()
                // The same three decisions the buttons offer, and the same directions the swipe
                // already uses: right keeps, left skips. A third way to do what is already
                // possible, for somebody whose hands are on the keyboard.
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val card = top ?: return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft -> {
                            onDecide(card, DiscoveryVerdict.SKIPPED)
                            true
                        }
                        Key.DirectionRight -> {
                            onDecide(card, DiscoveryVerdict.KEPT)
                            true
                        }
                        Key.DirectionUp -> {
                            onOpenDetails(card)
                            true
                        }
                        // Down is not handled here. It closes the details page, and by the time
                        // there is one to close this screen is no longer showing — so it lives on
                        // the window's own handler, which sees every screen.
                        else -> false
                    }
                }.verticalScroll(rememberScrollState())
                // More room at the foot than at the head.
                //
                // The decision buttons are the last thing in the card and were reported cut off at
                // the bottom of the window: an even padding leaves them flush against the edge, and
                // a round button flush against an edge reads as clipped even when it is not. This
                // is the same trick the shelves use, and it also gives the scroll somewhere to end.
                .padding(
                    start = BuroSpacing.Lg,
                    end = BuroSpacing.Lg,
                    top = BuroSpacing.Lg,
                    bottom = BuroSpacing.Xxl,
                ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(BuroSpacing.Sm),
    ) {
        Text(
            text = text.title,
            color = BuroColors.Text,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = text.hint,
            color = BuroColors.TextMuted,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.widthIn(max = 620.dp),
        )
        Spacer(Modifier.height(BuroSpacing.Sm))

        when {
            loading && top == null ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = BuroColors.Primary, strokeWidth = 2.dp)
                    Spacer(Modifier.height(BuroSpacing.Sm))
                    Text(text.loading, color = BuroColors.TextMuted)
                }

            top == null ->
                // A real state with its own words, not a spinner: somebody who has swiped through
                // everything has earned being told so rather than left waiting.
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text.exhausted, color = BuroColors.TextMuted)
                    Spacer(Modifier.height(BuroSpacing.Sm))
                    BuroInteractiveRow(
                        onClick = onAnother,
                        selected = true,
                        shape = BuroRadius.Pill,
                        contentDescription = text.another,
                    ) { _ ->
                        Text(
                            text = text.another,
                            modifier =
                                Modifier.padding(horizontal = BuroSpacing.Lg, vertical = BuroSpacing.Xs),
                            color = BuroColors.Primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

            else ->
                DiscoveryCard(
                    item = top,
                    synopsis = synopsisFor(top),
                    genres = genresFor(top),
                    onDecide = { verdict -> onDecide(top, verdict) },
                    onOpenDetails = { onOpenDetails(top) },
                    trailerId = trailerId,
                    onTrailerFailed = { onTrailerFailed(top) },
                    soundOn = soundOn,
                    onToggleSound = onToggleSound,
                )
        }
    }
}

@Composable
private fun DiscoveryCard(
    item: XtreamCatalogItem,
    synopsis: String?,
    genres: List<String>,
    onDecide: (DiscoveryVerdict) -> Unit,
    onOpenDetails: () -> Unit,
    trailerId: String?,
    onTrailerFailed: () -> Unit,
    soundOn: Boolean,
    onToggleSound: () -> Unit,
) {
    val text = strings.shareStrings.discovery

    // How far the card has been dragged, and where it settles.
    //
    // Reset by keying the state on the item: the next card starts centred rather than inheriting
    // the offset that flung the last one away.
    var dragX by remember(item.providerId) { mutableStateOf(0f) }
    var dragging by remember(item.providerId) { mutableStateOf(false) }
    val offsetX by animateFloatAsState(dragX, label = "discovery-drag")

    // The poster and the trailer, side by side.
    //
    // The card was a poster alone, which asks somebody to judge a film by its artwork; the trailer
    // beside it is what turns a guess into a decision. When there is no trailer the row holds only
    // the card and the screen looks exactly as it did.
    // Constraints, so the trailer fits the room there is rather than the room it would like.
    //
    // The card and a 460dp video together are wider than a modest window, and the overflow went off
    // the right edge — seen with the video half outside the screen. Below that width the video
    // takes what is left instead.
    // fillMaxWidth, so maxWidth below is the screen's width and not the row's own wish.
    //
    // Unconstrained, BoxWithConstraints reports what the content asked for, the centred column
    // overflows equally on both sides, and the video ends up hanging past the right edge of the
    // window — where a heavyweight browser surface is clipped by the window and paints nothing.
    // That is the white block beside the poster.
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // Room left over once the poster and the gaps either side of it are taken.
        //
        // Four gaps, not two: one between the poster and the video, and one at each outer edge so
        // neither runs to the window's border. Counting only two left the row wider than the space
        // it had, and the synopsis under it ran off the right of the screen.
        val roomForTrailer = maxWidth - CARD_WIDTH - BuroSpacing.Lg * 4
        val trailerWidth = minOf(TRAILER_WIDTH, roomForTrailer)
        val trailerFits = trailerWidth >= TRAILER_MIN_WIDTH
        val activeTrailerId = trailerId?.takeIf { trailerFits }
        val trailerAvailable = activeTrailerId != null

    // Anchored to the top, not centred.
    //
    // Centring made the poster jump: each card's text is a different height, and the whole row was
    // recentred around it, so the artwork moved between cards even though it never changed size.
    // Pinned to the top it stays exactly where the eye left it.
    Column(
        // The full width, always — with a trailer or without one.
        //
        // This is centred by the column outside it, so something that shrinks when there is no
        // trailer gets recentred, and the poster slides to the middle of the screen and back as the
        // deck moves. Reported as the artwork never staying still. Holding the width means the slot
        // beside the poster is simply empty when a card has no trailer, and the poster does not
        // move at all.
        modifier =
            Modifier
                .align(Alignment.TopCenter)
                .width(CARD_WIDTH + BuroSpacing.Lg + trailerWidth),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Lg),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            // During a swipe the heavyweight browser is withdrawn below and every visible layer
            // is Compose again. The z-index then does what the gesture promises: the poster passes
            // in front of the decision surface instead of being swallowed by the video rectangle.
            modifier = Modifier.width(CARD_WIDTH).zIndex(if (dragging) 2f else 0f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(POSTER_RATIO)
                        .graphicsLayer {
                            translationX = offsetX
                            // A small tilt, because a card that slides without turning reads as a
                            // panel being scrolled rather than one being thrown away.
                            rotationZ = (offsetX / CARD_WIDTH.value) * MAX_TILT_DEGREES
                        }.clip(BuroRadius.Large)
                        .background(BuroColors.SurfaceRaised)
                        .border(
                            width = 1.dp,
                            color =
                                when {
                                    dragX > DECISION_THRESHOLD -> BuroColors.Primary
                                    dragX < -DECISION_THRESHOLD -> BuroColors.TextSubtle
                                    else -> BuroColors.BorderSoft
                                },
                            shape = BuroRadius.Large,
                        ).pointerInput(item.providerId) {
                            detectDragGestures(
                                onDragStart = { dragging = true },
                                onDragEnd = {
                                    // Past the threshold the card is thrown; short of it, it returns.
                                    // Deciding on release rather than on crossing means a drag can be
                                    // reconsidered mid-gesture, which is what makes it feel forgiving.
                                    when {
                                        dragX > DECISION_THRESHOLD -> onDecide(DiscoveryVerdict.KEPT)
                                        dragX < -DECISION_THRESHOLD -> onDecide(DiscoveryVerdict.SKIPPED)
                                        else -> {
                                            dragX = 0f
                                        }
                                    }
                                    dragging = false
                                },
                                onDragCancel = {
                                    dragX = 0f
                                    dragging = false
                                },
                            ) { change, dragAmount: Offset ->
                                change.consume()
                                // Rightward travel is capped short of the video.
                                //
                                // The player is a heavyweight browser surface, and it always paints
                                // above Compose whatever the draw order — this codebase has the
                                // note in three places. So a poster dragged right did not pass in
                                // front of the trailer, it disappeared behind it, which reads as
                                // the card being swallowed. It cannot be made to pass in front, so
                                // it is stopped before it gets there: past the threshold the card
                                // is thrown anyway, and the rest of the travel was only ever
                                // decoration.
                                dragX = (dragX + dragAmount.x).coerceAtMost(MAX_RIGHTWARD_DRAG)
                            }
                        },
            ) {
                BuroRemoteArtwork(
                    artworkUrl = item.artworkUrl,
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = item.name.editorialTitle().take(1).uppercase(),
                            color = BuroColors.TextSubtle,
                            style = MaterialTheme.typography.headlineLarge,
                        )
                    }
                }
            }

            Spacer(Modifier.height(BuroSpacing.Sm))
            Text(
                text = item.name.editorialTitle(),
                color = BuroColors.Text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            // Year and genres on one quiet line: what the film is, in the two words somebody uses to
            // decide. Terror, ação, comédia — the reason the card is worth reading at all.
            val facts =
                listOfNotNull(
                    item.year?.toString(),
                    genres.take(MAX_GENRES).joinToString(" · ").takeIf(String::isNotBlank),
                ).joinToString("  ·  ")
            if (facts.isNotBlank()) {
                Text(
                    text = facts,
                    color = BuroColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // With no trailer there is no right-hand decision column, so the synopsis belongs to
            // the card. When a trailer exists it is placed directly below that video instead —
            // never in the same bounds as the heavyweight browser.
            if (!trailerAvailable && !trailerFits) {
                synopsis?.takeIf(String::isNotBlank)?.let { plot ->
                    Spacer(Modifier.height(BuroSpacing.Md))
                    DiscoverySynopsis(plot)
                }
                Spacer(Modifier.height(BuroSpacing.Lg))
                DiscoveryDecisionActions(
                    text = text,
                    onDecide = onDecide,
                    onOpenDetails = onOpenDetails,
                )
            }

        }

        if (activeTrailerId != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier =
                        Modifier
                            .width(trailerWidth)
                            .aspectRatio(16f / 9f)
                            .clip(BuroRadius.Large)
                            // Canvas, not SurfaceRaised. The browser panel is a heavyweight surface
                            // that Compose cannot clip or cover, so whatever shows through before
                            // the video arrives should be the dark the video itself is, not a pale
                            // card that reads as a broken image.
                            .background(BuroColors.Canvas),
                ) {
                    if (dragging) {
                        DiscoverySwipePreview(
                            kept = dragX >= 0f,
                            label = if (dragX >= 0f) text.keep else text.skip,
                        )
                    } else {
                        HeroTrailer(
                            youtubeId = activeTrailerId,
                            // The hosted page owns the rounded crop. Padding here exposed the AWT
                            // panel's top and right edges as a thin grey frame around the film.
                            modifier = Modifier.fillMaxSize(),
                            soundOn = soundOn,
                            onFailed = onTrailerFailed,
                            // Not the banner. The hosted page uses its dedicated card frame:
                            // exact 16:9 bounds, dark inset corners and no interactive hover layer.
                            blendIntoHero = false,
                        )
                    }
                }
                if (!dragging) {
                    Spacer(Modifier.height(BuroSpacing.Sm))
                    // Beside the video rather than over it: the player is a browser surface that
                    // always paints above Compose, so a control laid on top of it is simply absent.
                    BuroInteractiveRow(
                        onClick = onToggleSound,
                        selected = false,
                        shape = BuroRadius.Pill,
                        contentDescription =
                            if (soundOn) {
                                strings.shareStrings.screens.trailerMute
                            } else {
                                strings.shareStrings.screens.trailerUnmute
                            },
                    ) { _ ->
                        Text(
                            text =
                                if (soundOn) {
                                    strings.shareStrings.screens.trailerMute
                                } else {
                                    strings.shareStrings.screens.trailerUnmute
                                },
                            modifier =
                                Modifier.padding(
                                    horizontal = BuroSpacing.Md,
                                    vertical = BuroSpacing.Xs,
                                ),
                            color = BuroColors.TextMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Spacer(Modifier.height(BuroSpacing.Md))
                DiscoveryDecisionActions(
                    text = text,
                    onDecide = onDecide,
                    onOpenDetails = onOpenDetails,
                )
                synopsis?.takeIf(String::isNotBlank)?.let { plot ->
                    Spacer(Modifier.height(BuroSpacing.Md))
                    DiscoverySynopsis(plot)
                }
            }
        } else if (trailerFits) {
            Column(
                modifier = Modifier.width(trailerWidth),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(BuroSpacing.Md),
            ) {
                DiscoveryDecisionActions(
                    text = text,
                    onDecide = onDecide,
                    onOpenDetails = onOpenDetails,
                )
                synopsis?.takeIf(String::isNotBlank)?.let { plot ->
                    DiscoverySynopsis(plot)
                }
            }
        }
    }
    }
    }
}

@Composable
private fun DiscoveryDecisionActions(
    text: com.lucasserafin94.iptvburo.desktop.ui.DiscoveryStrings,
    onDecide: (DiscoveryVerdict) -> Unit,
    onOpenDetails: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Xl)) {
        DecisionButton(
            label = "✕",
            caption = text.skip,
            tint = BuroColors.TextMuted,
            onClick = { onDecide(DiscoveryVerdict.SKIPPED) },
        )
        DecisionButton(
            label = "✓",
            caption = text.keep,
            tint = BuroColors.Primary,
            onClick = { onDecide(DiscoveryVerdict.KEPT) },
        )
        DecisionButton(
            label = "▶",
            caption = text.details,
            tint = BuroColors.TextMuted,
            onClick = onOpenDetails,
        )
    }
}

@Composable
private fun DiscoverySynopsis(plot: String) {
    Text(
        text = plot,
        modifier = Modifier.fillMaxWidth(),
        color = BuroColors.TextMuted,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 5,
        overflow = TextOverflow.Ellipsis,
    )
}

/** The video yields during a swipe so the poster can genuinely be the front-most surface. */
@Composable
private fun DiscoverySwipePreview(
    kept: Boolean,
    label: String,
) {
    val tint = if (kept) BuroColors.Primary else BuroColors.TextSubtle
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(tint.copy(alpha = 0.12f))
                .border(1.dp, tint.copy(alpha = 0.55f), BuroRadius.Large),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (kept) "✓" else "✕",
                color = tint,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = label,
                color = BuroColors.Text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun DecisionButton(
    label: String,
    caption: String,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BuroInteractiveRow(
            onClick = onClick,
            selected = false,
            shape = BuroRadius.Pill,
            contentDescription = caption,
        ) { state ->
            Box(
                modifier = Modifier.size(BUTTON_SIZE),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (state.active) tint else tint.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
        }
        Text(
            text = caption,
            color = BuroColors.TextSubtle,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/** Wide enough for a poster to read as one, narrow enough to sit in a window beside a sidebar. */
private val CARD_WIDTH = 300.dp

/**
 * The trailer that plays beside the poster.
 *
 * Wider than the card, because a 16:9 video next to a 2:3 poster looks starved at the poster's
 * width. The two together are what the screen is for: the artwork says what the film looks like and
 * the trailer says what it is, which is the difference between guessing and deciding.
 */
private val TRAILER_WIDTH = 460.dp

/**
 * Below this the trailer is not shown at all.
 *
 * A video squeezed into a sliver beside the card is not a trailer, it is a distraction with a
 * soundtrack. At that width the card goes back to being a card, which is what it was before.
 */
private val TRAILER_MIN_WIDTH = 280.dp

/**
 * How far right a card may be dragged before it stops moving.
 *
 * Comfortably past [DECISION_THRESHOLD], so the gesture still completes and still feels like a
 * throw, but short of the video beside it. The player paints above everything Compose draws, so a
 * card that travelled that far vanished behind it rather than over it.
 */
private const val MAX_RIGHTWARD_DRAG = 190f


private const val POSTER_RATIO = 2f / 3f

/** How far a card travels before releasing it counts as a decision. */
private const val DECISION_THRESHOLD = 110f

/** Enough tilt to read as a throw, little enough not to look like a fault. */
private const val MAX_TILT_DEGREES = 8f

private val BUTTON_SIZE = 56.dp

/** Two or three genres is a description; six is a list nobody reads. */
private const val MAX_GENRES = 3

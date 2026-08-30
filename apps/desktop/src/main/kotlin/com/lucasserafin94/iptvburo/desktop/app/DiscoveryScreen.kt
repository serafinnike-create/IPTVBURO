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
                .padding(BuroSpacing.Lg),
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
            color = BuroColors.TextSubtle,
            style = MaterialTheme.typography.bodySmall,
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
    BoxWithConstraints {
        val roomForTrailer = maxWidth - CARD_WIDTH - BuroSpacing.Lg * 2
        val trailerWidth = minOf(TRAILER_WIDTH, roomForTrailer)
        val trailerFits = trailerWidth >= TRAILER_MIN_WIDTH

    // Anchored to the top, not centred.
    //
    // Centring made the poster jump: each card's text is a different height, and the whole row was
    // recentred around it, so the artwork moved between cards even though it never changed size.
    // Pinned to the top it stays exactly where the eye left it.
    Row(
        horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Lg),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.width(CARD_WIDTH),
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
                                onDragEnd = {
                                    // Past the threshold the card is thrown; short of it, it returns.
                                    // Deciding on release rather than on crossing means a drag can be
                                    // reconsidered mid-gesture, which is what makes it feel forgiving.
                                    when {
                                        dragX > DECISION_THRESHOLD -> onDecide(DiscoveryVerdict.KEPT)
                                        dragX < -DECISION_THRESHOLD -> onDecide(DiscoveryVerdict.SKIPPED)
                                        else -> dragX = 0f
                                    }
                                },
                                onDragCancel = { dragX = 0f },
                            ) { change, dragAmount: Offset ->
                                change.consume()
                                dragX += dragAmount.x
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

            // The synopsis is not here: it sits under the video, where there is room to read it.
            // Beside a 300dp poster it was a narrow strip of text competing with the artwork.

            Spacer(Modifier.height(BuroSpacing.Md))
            Row(horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Lg)) {
                // Buttons as well as the drag, because a mouse is not a finger: dragging a card across
                // a desktop screen is a chore next to clicking, and somebody on a trackpad should not
                // have to.
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
                // Neither a verdict nor a swipe: it opens the film's own page.
                //
                // Was "Detalhes", which read as a panel of extra facts. It goes to the title itself,
                // and coming back returns to this card — so the deck is not lost by looking.
                DecisionButton(
                    label = "▶",
                    caption = text.details,
                    tint = BuroColors.TextMuted,
                    onClick = onOpenDetails,
                )
            }
        }

        if (trailerId != null && trailerFits) {
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
                    HeroTrailer(
                        youtubeId = trailerId,
                        modifier = Modifier.fillMaxSize(),
                        soundOn = soundOn,
                        onFailed = onTrailerFailed,
                        // Not the banner. Those masks fade the left edge and the bottom into the
                        // hero behind them; on a card with its own edges they only smear the video.
                        blendIntoHero = false,
                    )
                }
                // The synopsis, under the video and as wide as it.
                //
                // It used to sit beside the poster, in a 300dp strip competing with the artwork.
                // Here it has the video's full width, which is where there is room to actually read
                // it — and the point of the card is deciding, which needs reading.
                synopsis?.takeIf(String::isNotBlank)?.let { plot ->
                    Spacer(Modifier.height(BuroSpacing.Sm))
                    Text(
                        text = plot,
                        modifier = Modifier.width(trailerWidth),
                        color = BuroColors.TextSubtle,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.height(BuroSpacing.Sm))
                // Beside the video rather than over it: the player is a browser surface that always
                // paints above Compose, so a control laid on top of it is simply not there.
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
                            Modifier.padding(horizontal = BuroSpacing.Md, vertical = BuroSpacing.Xs),
                        color = BuroColors.TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
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


private const val POSTER_RATIO = 2f / 3f

/** How far a card travels before releasing it counts as a decision. */
private const val DECISION_THRESHOLD = 110f

/** Enough tilt to read as a throw, little enough not to look like a fault. */
private const val MAX_TILT_DEGREES = 8f

private val BUTTON_SIZE = 56.dp

/** Two or three genres is a description; six is a list nobody reads. */
private const val MAX_GENRES = 3

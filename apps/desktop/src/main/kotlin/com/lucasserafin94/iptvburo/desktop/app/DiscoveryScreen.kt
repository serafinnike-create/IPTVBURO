package com.lucasserafin94.iptvburo.desktop.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
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
import com.lucasserafin94.iptvburo.domain.model.DiscoveryVerdict
import com.lucasserafin94.iptvburo.xtream.XtreamCatalogItem

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
    onAnother: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = strings.shareStrings.discovery
    val top = deck.firstOrNull()

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(BuroSpacing.Lg),
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
) {
    val text = strings.shareStrings.discovery

    // How far the card has been dragged, and where it settles.
    //
    // Reset by keying the state on the item: the next card starts centred rather than inheriting
    // the offset that flung the last one away.
    var dragX by remember(item.providerId) { mutableStateOf(0f) }
    val offsetX by animateFloatAsState(dragX, label = "discovery-drag")

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

        synopsis?.takeIf(String::isNotBlank)?.let { plot ->
            Spacer(Modifier.height(BuroSpacing.Xs))
            Text(
                text = plot,
                color = BuroColors.TextSubtle,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }

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
private const val POSTER_RATIO = 2f / 3f

/** How far a card travels before releasing it counts as a decision. */
private const val DECISION_THRESHOLD = 110f

/** Enough tilt to read as a throw, little enough not to look like a fault. */
private const val MAX_TILT_DEGREES = 8f

private val BUTTON_SIZE = 56.dp

/** Two or three genres is a description; six is a list nobody reads. */
private const val MAX_GENRES = 3

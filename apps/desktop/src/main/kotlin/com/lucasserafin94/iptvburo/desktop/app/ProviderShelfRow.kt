package com.lucasserafin94.iptvburo.desktop.app

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lucasserafin94.iptvburo.desktop.ui.BuroColors
import com.lucasserafin94.iptvburo.desktop.ui.BuroInteractiveSurface
import com.lucasserafin94.iptvburo.desktop.ui.BuroRadius
import com.lucasserafin94.iptvburo.desktop.ui.BuroRemoteArtwork
import com.lucasserafin94.iptvburo.desktop.ui.BuroScrim
import com.lucasserafin94.iptvburo.desktop.ui.BuroSpacing
import com.lucasserafin94.iptvburo.desktop.ui.DesktopStrings
import com.lucasserafin94.iptvburo.desktop.ui.arrowScrollable
import com.lucasserafin94.iptvburo.desktop.ui.edgeScrollable
import com.lucasserafin94.iptvburo.desktop.ui.strings
import com.lucasserafin94.iptvburo.domain.model.ExternalTitle
import com.lucasserafin94.iptvburo.domain.model.ExternalTitleDetails
import com.lucasserafin94.iptvburo.domain.model.ExternalTitleKind
import com.lucasserafin94.iptvburo.domain.model.ProviderShelf

/**
 * One service's rail — GDD 9's "browse by service" view.
 *
 * The shape follows the music and video rails: a heading, then a horizontally scrolling row of
 * poster cards, so the shelves read like a rental shop's wall rather than a list of file names.
 * The artwork is the *film's own* poster, which discovery catalogues serve for display. What is
 * still deliberately absent is brand artwork: GDD 9 section 10 forbids copying a service's marks,
 * so a provider remains its [com.lucasserafin94.iptvburo.domain.model.StreamingProvider.displayName]
 * in text and no logo is ever fetched.
 *
 * Scrolling is the part this screen has historically got wrong, so it is spelled out: the rail is a
 * [LazyRow] with keyboard arrows via [arrowScrollable], pointer-edge travel via [edgeScrollable],
 * and a visible [HorizontalScrollbar] beneath it. A rail that can only be reached by dragging is a
 * rail most users never reach the end of.
 */
@Composable
fun ProviderShelfRow(
    shelf: ProviderShelf,
    /**
     * Whether each card carries the DEMO badge.
     *
     * Driven by `StreamingDiscoveryCapability.requiresDemoLabel`, never by the card itself. These
     * listings name real companies and were invented; the badge is the only thing telling the user
     * so, which is why it is passed down rather than inferred locally.
     */
    showDemoBadge: Boolean,
    onSelectTitle: (ExternalTitleDetails) -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = strings
    val railState = rememberLazyListState()

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = BuroSpacing.Lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Sm),
        ) {
            // The provider's name as text, and only as text. No logo, no brand colour.
            Text(
                text = shelf.provider.displayName,
                color = BuroColors.Text,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // The count tells the user the rail has an end, which a clipped row otherwise hides.
            Text(
                text = shelf.size.toString(),
                color = BuroColors.TextSubtle,
                style = MaterialTheme.typography.labelLarge,
                modifier =
                    Modifier
                        .clip(BuroRadius.Pill)
                        .background(BuroColors.Surface)
                        .padding(horizontal = BuroSpacing.Xs, vertical = 2.dp),
            )
        }

        Spacer(Modifier.height(BuroSpacing.Sm))

        LazyRow(
            state = railState,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .arrowScrollable(railState)
                    .edgeScrollable(railState),
            contentPadding = PaddingValues(horizontal = BuroSpacing.Lg),
            horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Md),
        ) {
            items(shelf.titles, key = { details -> details.title.id.key }) { details ->
                ProviderShelfCard(
                    details = details,
                    showDemoBadge = showDemoBadge,
                    text = text,
                    onClick = { onSelectTitle(details) },
                )
            }
        }

        // Visible rather than implied. The rail can hold more than fits, and a scrollbar the user
        // cannot see is the reason a screen gets reported as "not scrollable" when it always was.
        HorizontalScrollbar(
            adapter = rememberScrollbarAdapter(railState),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = BuroSpacing.Lg, vertical = BuroSpacing.Xxs),
            style =
                LocalScrollbarStyle.current.copy(
                    thickness = 8.dp,
                    unhoverColor = BuroColors.BorderSoft,
                    hoverColor = BuroColors.Primary,
                ),
        )
    }
}

/**
 * One title on a service's rail: a poster, then the title and year underneath.
 *
 * The same shape and 2:3 ratio as the catalogue grid's card, so moving between the catalogue and
 * the shelves does not feel like moving between two products.
 *
 * The poster is the film's own art. It is never a service's logo, and no brand mark is fetched
 * here — see the note on [ProviderShelfRow].
 */
@Composable
internal fun ProviderShelfCard(
    details: ExternalTitleDetails,
    showDemoBadge: Boolean,
    text: DesktopStrings,
    onClick: () -> Unit,
) {
    val title = details.title

    BuroInteractiveSurface(
        onClick = onClick,
        modifier = Modifier.width(CARD_WIDTH),
        shape = BuroRadius.Medium,
        compact = true,
        contentDescription = title.title,
    ) { state ->
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(POSTER_RATIO)
                        .clip(BuroRadius.Medium)
                        .background(BuroColors.SurfaceRaised)
                        .border(
                            width = 1.dp,
                            color = if (state.active) BuroColors.Primary else BuroColors.BorderSoft,
                            shape = BuroRadius.Medium,
                        ),
            ) {
                // The fallback is drawn *behind* the image by BuroRemoteArtwork, so a null URL and a
                // failed load both land on the designed card rather than on a broken-image icon or
                // an empty grey box. That is the ordinary case here, not the exception: the demo
                // catalogue carries no artwork at all.
                BuroRemoteArtwork(
                    artworkUrl = title.posterUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    PosterFallback(title = title, text = text)
                }

                // A scrim under the badge only, and only as tall as the badge needs. Without it the
                // badge sits directly on artwork whose brightness is unknown, which is exactly the
                // case where a light-on-light pill becomes unreadable.
                if (showDemoBadge) {
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.TopStart)
                                .fillMaxWidth()
                                .fillMaxHeight(BADGE_SCRIM_FRACTION)
                                .background(BuroScrim.badgeHeader()),
                    )
                    // Never suppressed by the layout: the badge is what distinguishes an invented
                    // availability answer from a real one. Opaque canvas fill plus a hairline
                    // border, so it reads as a chip on top of a bright poster as well as a dark one.
                    Text(
                        text = text.subscriptionsDemoBadge,
                        color = BuroColors.Primary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier =
                            Modifier
                                .align(Alignment.TopStart)
                                .padding(BuroSpacing.Xs)
                                .clip(BuroRadius.Pill)
                                .background(BuroColors.Canvas)
                                .border(1.dp, BuroColors.BorderSoft, BuroRadius.Pill)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }

                // Film or series, bottom-left over the poster's own footer scrim.
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .fillMaxHeight(FOOTER_SCRIM_FRACTION)
                            .background(BuroScrim.cardFooter()),
                )
                Text(
                    text = kindLabel(title.kind, text),
                    color = BuroColors.TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(horizontal = BuroSpacing.Xs, vertical = 6.dp),
                )
            }

            Spacer(Modifier.height(BuroSpacing.Xs))

            // Under the poster rather than over it: a two-line title over artwork needs a scrim
            // heavy enough to hide the poster it is sitting on.
            Text(
                text = title.title,
                color = if (state.active) BuroColors.Primary else BuroColors.Text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = title.year?.toString().orEmpty(),
                    color = BuroColors.TextSubtle,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
                // The card's purpose stated on the card: clicking asks where this can be watched,
                // it does not start playing anything.
                Text(
                    text = text.subscriptionsWhereToWatch,
                    color = if (state.active) BuroColors.Primary else BuroColors.TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

/**
 * What a card shows when there is no poster.
 *
 * Not a placeholder waiting for an image: it is the card's finished state for a title the catalogue
 * has no artwork for, which is every title while the demo catalogue is what backs the screen. The
 * title is set large over a tinted gradient, so a rail of them reads as designed covers rather than
 * as a row of failed loads. Deriving the tint from the title keeps neighbouring cards distinct
 * without inventing colour that means anything.
 */
@Composable
private fun PosterFallback(
    title: ExternalTitle,
    text: DesktopStrings,
) {
    val tint = POSTER_TINTS[(title.title.hashCode().toUInt() % POSTER_TINTS.size.toUInt()).toInt()]

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(
                            tint.copy(alpha = 0.34f),
                            BuroColors.Surface,
                        ),
                    ),
                ).padding(BuroSpacing.Sm),
    ) {
        Text(
            text = title.title,
            color = BuroColors.Text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
        )
        // A quiet mark of the medium, so an artless card still has the shape of a cover rather than
        // being a floating caption.
        Text(
            text = kindLabel(title.kind, text),
            color = BuroColors.TextSubtle,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            modifier = Modifier.align(Alignment.TopEnd),
        )
    }
}

/** Film or series, in the user's language. */
private fun kindLabel(
    kind: ExternalTitleKind,
    text: DesktopStrings,
): String =
    when (kind) {
        ExternalTitleKind.MOVIE -> text.movies
        ExternalTitleKind.SERIES -> text.series
    }

/**
 * Tints for the artless card.
 *
 * Muted on purpose and drawn from the app's own palette: these must read as the product's covers,
 * not as a service's colours. No entry here corresponds to any brand.
 */
private val POSTER_TINTS =
    listOf(
        Color(0xFF3E4C5E),
        Color(0xFF5E4A3E),
        Color(0xFF3E5E4C),
        Color(0xFF4C3E5E),
        Color(0xFF5E3E4A),
    )

/** Poster proportions, matching the catalogue grid's VOD card. */
private const val POSTER_RATIO = 2f / 3f

/** Just enough darkening behind the DEMO badge to guarantee contrast on a bright poster. */
private const val BADGE_SCRIM_FRACTION = 0.22f

/** The footer scrim behind the kind label. */
private const val FOOTER_SCRIM_FRACTION = 0.3f

/** Matches the catalogue grid's poster column width, so the two views scan alike. */
private val CARD_WIDTH = 168.dp

/**
 * The empty state for the shelves view.
 *
 * Distinct from "no results for a search": this is the catalogue having nothing to arrange at all.
 */
@Composable
fun ProviderShelvesEmpty(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            color = BuroColors.TextSubtle,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

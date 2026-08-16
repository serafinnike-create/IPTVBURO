package com.lucasserafin94.iptvburo.desktop.app

import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lucasserafin94.iptvburo.desktop.ExpandedService
import com.lucasserafin94.iptvburo.desktop.ui.BuroColors
import com.lucasserafin94.iptvburo.desktop.ui.BuroInteractiveRow
import com.lucasserafin94.iptvburo.desktop.ui.BuroRadius
import com.lucasserafin94.iptvburo.desktop.ui.BuroRemoteArtwork
import com.lucasserafin94.iptvburo.desktop.ui.BuroSpacing
import com.lucasserafin94.iptvburo.desktop.ui.strings
import com.lucasserafin94.iptvburo.domain.model.ExternalTitle
import com.lucasserafin94.iptvburo.domain.model.ExternalTitleDetails
import com.lucasserafin94.iptvburo.domain.model.OfferType
import com.lucasserafin94.iptvburo.domain.model.StreamingOffer

/**
 * One service's whole catalogue, reached from the card that ends its shelf.
 *
 * A grid rather than another rail. The shelf is a rail because it is a sample — twenty titles to
 * glance along — and this is the opposite: a hundred titles to hunt through, which is a job for
 * something that fills the screen and scrolls in one direction.
 *
 * The shelf's own titles are shown while the wider list loads, so the grid is never empty at the
 * moment it opens. The viewer has just been looking at those posters; blanking them to fetch more
 * of the same would read as the button having lost what was already there.
 */
@Composable
fun ServiceCatalogueGrid(
    service: ExpandedService,
    loading: Boolean,
    onSelectTitle: (ExternalTitle) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = strings

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(BuroSpacing.Lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Md),
        ) {
            BuroInteractiveRow(
                onClick = onBack,
                selected = false,
                shape = BuroRadius.Pill,
                contentDescription = text.shareStrings.serviceCatalogue.backToShelves,
            ) { state ->
                Text(
                    text = "←  ${text.shareStrings.serviceCatalogue.backToShelves}",
                    modifier = Modifier.padding(horizontal = BuroSpacing.Md, vertical = BuroSpacing.Xs),
                    color = if (state.active) BuroColors.Primary else BuroColors.Text,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            // The service's own mark, when TMDb carries one, beside its name — the same pairing
            // used on the shelf this page was opened from, so the heading confirms the viewer
            // landed where the button pointed.
            service.provider.logoUrl?.let { logo ->
                Box(
                    modifier =
                        Modifier
                            .size(HEADING_LOGO_SIZE)
                            .clip(BuroRadius.Small)
                            .background(BuroColors.Canvas),
                ) {
                    BuroRemoteArtwork(
                        artworkUrl = logo,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().padding(2.dp),
                        contentScale = ContentScale.Fit,
                    ) {}
                }
            }

            Text(
                text = text.shareStrings.serviceCatalogue.allFrom.format(service.provider.displayName),
                color = BuroColors.Text,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            // Only while the wider list is still coming. The grid already holds the shelf's titles,
            // so this says "more on the way" rather than "nothing here yet".
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.height(20.dp),
                    color = BuroColors.Primary,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    text = service.titles.size.toString(),
                    color = BuroColors.TextSubtle,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        val gridState = rememberLazyGridState()
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = CARD_MIN_WIDTH),
                modifier = Modifier.fillMaxSize(),
                contentPadding =
                    PaddingValues(
                        start = BuroSpacing.Lg,
                        end = BuroSpacing.Lg,
                        bottom = BuroSpacing.Xl,
                    ),
                horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Md),
                verticalArrangement = Arrangement.spacedBy(BuroSpacing.Md),
            ) {
                items(service.titles, key = { title -> title.id.key }) { title ->
                    ProviderShelfCard(
                        // One synthetic offer naming this service, so each card carries its mark
                        // like every other card in the app. The grid's heading already says which
                        // service this is; the corner logo is what keeps a card recognisable when
                        // it is looked at on its own rather than as part of this page.
                        details =
                            ExternalTitleDetails(
                                title = title,
                                offers =
                                    listOf(
                                        StreamingOffer(
                                            provider = service.provider,
                                            type = OfferType.SUBSCRIPTION,
                                        ),
                                    ),
                            ),
                        showDemoBadge = title.isDemo,
                        text = text,
                        onClick = { onSelectTitle(title) },
                    )
                }
            }

            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(gridState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                style =
                    LocalScrollbarStyle.current.copy(
                        thickness = 8.dp,
                        unhoverColor = BuroColors.BorderSoft,
                        hoverColor = BuroColors.Primary,
                    ),
            )
        }
        Spacer(Modifier.height(BuroSpacing.Xs))
    }
}

/** Large enough to read a wordmark at a glance, small enough not to compete with the heading. */
private val HEADING_LOGO_SIZE = 26.dp

/** Wide enough for a poster and its title, narrow enough that a 1080p window fits six. */
private val CARD_MIN_WIDTH = 168.dp

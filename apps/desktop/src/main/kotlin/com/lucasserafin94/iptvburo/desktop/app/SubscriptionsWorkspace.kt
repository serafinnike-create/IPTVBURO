package com.lucasserafin94.iptvburo.desktop.app

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import com.lucasserafin94.iptvburo.desktop.ui.rememberRestoredListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollbarAdapter
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lucasserafin94.iptvburo.desktop.ExpandedService
import com.lucasserafin94.iptvburo.desktop.platform.openStreamingOfferExternally
import com.lucasserafin94.iptvburo.desktop.ui.BuroColors
import com.lucasserafin94.iptvburo.desktop.ui.BuroMark
import com.lucasserafin94.iptvburo.desktop.ui.BuroInteractiveRow
import com.lucasserafin94.iptvburo.desktop.ui.BuroRadius
import com.lucasserafin94.iptvburo.desktop.ui.BuroRemoteArtwork
import com.lucasserafin94.iptvburo.desktop.ui.BuroSpacing
import com.lucasserafin94.iptvburo.desktop.ui.DesktopStrings
import com.lucasserafin94.iptvburo.desktop.ui.arrowScrollable
import com.lucasserafin94.iptvburo.desktop.ui.arrowScrollableList
import com.lucasserafin94.iptvburo.desktop.ui.edgeScrollable
import com.lucasserafin94.iptvburo.desktop.ui.edgeScrollableVertically
import com.lucasserafin94.iptvburo.desktop.ui.strings
import com.lucasserafin94.iptvburo.domain.model.ExternalTitleDetails
import com.lucasserafin94.iptvburo.domain.model.OfferReason
import com.lucasserafin94.iptvburo.metadata.TmdbCastMember
import com.lucasserafin94.iptvburo.metadata.TmdbDiscoverKind
import com.lucasserafin94.iptvburo.metadata.TmdbTitleDetails
import com.lucasserafin94.iptvburo.domain.model.OfferRanking
import com.lucasserafin94.iptvburo.domain.model.OfferType
import com.lucasserafin94.iptvburo.metadata.WATCH_PROVIDER_ATTRIBUTION
import com.lucasserafin94.iptvburo.domain.model.ProviderShelf
import com.lucasserafin94.iptvburo.domain.model.StreamingProvider
import com.lucasserafin94.iptvburo.domain.model.RankedOffer
import com.lucasserafin94.iptvburo.domain.model.StreamingDiscoveryCapability
import com.lucasserafin94.iptvburo.domain.model.streamingShelves

/**
 * The Assinaturas screen — GDD 9, phase 1.
 *
 * It answers one question: where can this title be watched. It never plays anything itself. Every
 * row leads to the provider's own app or website through [com.lucasserafin94.iptvburo.domain.model.ExternalContentLauncher],
 * which is what keeps the app a signpost rather than a way around a paywall.
 *
 * The screen has two states. It opens on the shelves — one rail per service, so the question the
 * user actually arrives with ("what is on Netflix?") is answerable by looking rather than by
 * searching. Choosing a title switches to that title's ranked offers, with a way back. The flat
 * list of every title that used to sit here answered neither question well: it was neither a
 * catalogue nor an answer.
 *
 * While [capability] is DEMO_ONLY every result carries a DEMO badge and the notice at the top says
 * plainly that nothing real is connected. That marking is not decoration: these listings are
 * invented, and presenting an invented availability answer in the product's own voice would be a
 * lie the user has no way to detect.
 */
@Composable
fun SubscriptionsWorkspace(
    capability: StreamingDiscoveryCapability,
    ranking: OfferRanking,
    /** The catalogue to arrange into per-service shelves. Empty means there is nothing to browse. */
    titles: List<ExternalTitleDetails> = emptyList(),
    onSelectTitle: (ExternalTitleDetails) -> Unit = {},
    /** Which of films, series or upcoming the shelves are showing. */
    kind: TmdbDiscoverKind = TmdbDiscoverKind.MOVIES,
    onSelectKind: (TmdbDiscoverKind) -> Unit = {},
    /** A failed TMDb request, distinct from a successful catalogue with no shelves. */
    loadFailed: Boolean = false,
    /** Whether that failure was TMDb rejecting the key, which needs a different instruction. */
    keyRejected: Boolean = false,
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier,
    /**
     * Returns to the shelves from a title's offers.
     *
     * Defaulted to selecting nothing through [onSelectTitle]'s absence rather than requiring every
     * call site to supply one; a caller that cannot clear its selection simply keeps the offers
     * visible, which is the previous behaviour rather than a broken one.
     */
    onBackToShelves: (() -> Unit)? = null,
    /**
     * Defaulted to the checked launcher rather than left to the caller.
     *
     * A required callback here would mean every future call site has to remember to route through
     * the safety checks, and one that forgot would open whatever the catalogue handed it. The
     * default does the right thing; overriding it is for tests.
     */
    onOpenOffer: (RankedOffer) -> Unit = { ranked ->
        ranked.offer.launchTarget?.let(::openStreamingOfferExternally)
    },
    /**
     * Artwork, synopsis, cast and trailer for the title whose offers are showing.
     *
     * Null is ordinary, not an error: it covers the moment before the lookup returns, an
     * unconfigured metadata key, and the many real titles TMDb has nothing for. The screen then
     * draws only the provider rows, exactly as it did before this existed.
     */
    page: TmdbTitleDetails? = null,
    /** Opens the trailer in the browser when the embedded engine cannot start. */
    onOpenTrailerExternally: (String) -> Unit = {},
    /**
     * Whether this profile asked to be reminded about the title whose page is showing.
     *
     * Takes the title and year rather than an id because that is what a reminder is keyed by — see
     * ContentIdentity — and an upcoming film has no catalogue row to name it with.
     */
    hasReminderFor: (String, Int?) -> Boolean = { _, _ -> false },
    /**
     * Marks or unmarks that title, with its poster so the reminders list can show a cover.
     *
     * Null hides the button, which is what a test gets by default.
     */
    onToggleReminder: ((String, Int?, String?) -> Unit)? = null,
    /**
     * Whether a title is open, as opposed to the shelves being browsed.
     *
     * This used to be inferred from the offers being non-empty, which quietly meant "a title is
     * open *and* somebody knows where to watch it". An upcoming film has no offers by definition —
     * that is exactly what the Em breve shelf says about itself — so opening one fell straight back
     * to the shelves, and its release date and reminder button could not be reached at all.
     */
    titleOpen: Boolean = !ranking.isEmpty,
    /**
     * Opens one service's full catalogue, from the card that ends its shelf.
     *
     * Null leaves the shelves as they were, which is what a test gets by default and what the
     * Em breve tab gets deliberately.
     */
    onSeeMore: ((StreamingProvider) -> Unit)? = null,
    /** The service whose full catalogue is open, or null while the shelves are showing. */
    expandedService: ExpandedService? = null,
    expandedLoading: Boolean = false,
    onCloseExpanded: () -> Unit = {},
) {
    val text = strings

    // Which trailer is open, if any. Keyed on the title so it closes when the screen moves on
    // rather than surviving into a page it does not belong to.
    var openTrailerId by remember(page?.youtubeTrailerId) { mutableStateOf<String?>(null) }

    // The grouping is a domain rule, computed once per catalogue rather than on every
    // recomposition. Keying on the list keeps a scroll or a hover from rebuilding every shelf.
    val shelves = remember(titles) { streamingShelves(titles) }

    // Column, not Box: the header is fixed and the content below it scrolls. The content must be
    // the weighted child — see the scroll note on the browsing branch.
    Column(modifier = modifier.fillMaxSize()) {
        SubscriptionsHeader(
            text = text,
            capability = capability,
            // The expanded catalogue carries its own heading and its own way back, so the shelf
            // filters above it would be two sets of controls arguing about where the user is.
            showingOffers = titleOpen || expandedService != null,
            kind = kind,
            onSelectKind = onSelectKind,
            onBack = onBackToShelves,
        )

        // Weighted, never fillMaxSize: an unweighted Column child is measured against unbounded
        // height, so a scrollable one lays its whole content out past the bottom of the window and
        // never becomes scrollable at all. This screen has shipped that way before.
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // Order matters, and getting it wrong is what shipped a grid whose posters could not be
            // opened: an expanded service stays open behind a chosen title — that is how "back"
            // returns to it — so testing the grid first kept it on screen over the very page the
            // click had just asked for. A title, when one is open, wins.
            val expanded = expandedService?.takeIf { !titleOpen }
            if (expanded != null) {
                ServiceCatalogueGrid(
                    service = expanded,
                    loading = expandedLoading,
                    // Wrapped with no offers on purpose: the caller looks the title up and fetches
                    // where it can be watched, exactly as choosing from a shelf does. Handing it a
                    // pre-built empty ranking would open a page whose availability panel is blank
                    // and never fills, which is what this screen did when it first shipped.
                    onSelectTitle = { title ->
                        onSelectTitle(ExternalTitleDetails(title = title, offers = emptyList()))
                    },
                    onBack = onCloseExpanded,
                )
            } else if (!titleOpen) {
                ProviderShelves(
                    shelves = shelves,
                    showDemoBadge = capability.requiresDemoLabel,
                    onSelectTitle = onSelectTitle,
                    // Not offered on Em breve: those films belong to no service yet, so there is no
                    // fuller catalogue behind them to open.
                    onSeeMore = onSeeMore?.takeIf { kind != TmdbDiscoverKind.UPCOMING },
                    emptyMessage = text.subscriptionsNoShelves,
                    loadFailed = loadFailed,
                    failureMessage =
                        if (keyRejected) text.subscriptionsKeyRejected else text.subscriptionsLoadFailed,
                    retryLabel = text.tryAgain,
                    onRetry = onRetry,
                )
            } else {
                OfferList(
                    ranking = ranking,
                    showDemoBadge = capability.requiresDemoLabel,
                    text = text,
                    onOpenOffer = onOpenOffer,
                    page = page,
                    onPlayTrailer = { trailerId -> openTrailerId = trailerId },
                    hasReminderFor = hasReminderFor,
                    onToggleReminder = onToggleReminder,
                )
            }
        }
    }

    // Drawn last so it sits over the page. Without this the button would set a value that nothing
    // reads — a failure this project has shipped three times.
    openTrailerId?.let { trailerId ->
        TrailerOverlay(
            youtubeId = trailerId,
            title = page?.title.orEmpty(),
            onClose = { openTrailerId = null },
            onFallback = { onOpenTrailerExternally(trailerId) },
        )
    }
}

/** The title, the back control and the demo notice, all of which stay put while the body scrolls. */
@Composable
private fun SubscriptionsHeader(
    text: DesktopStrings,
    capability: StreamingDiscoveryCapability,
    showingOffers: Boolean,
    kind: TmdbDiscoverKind,
    onSelectKind: (TmdbDiscoverKind) -> Unit,
    onBack: (() -> Unit)?,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    start = BuroSpacing.Lg,
                    end = BuroSpacing.Lg,
                    top = BuroSpacing.Lg,
                    bottom = BuroSpacing.Sm,
                ),
        verticalArrangement = Arrangement.spacedBy(BuroSpacing.Sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Back appears only where there is somewhere to go back to.
            if (showingOffers && onBack != null) {
                BuroInteractiveRow(
                    onClick = onBack,
                    selected = false,
                    shape = BuroRadius.Pill,
                    contentDescription = text.subscriptionsBackToServices,
                ) { state ->
                    Text(
                        text = "‹  ${text.subscriptionsBackToServices}",
                        modifier =
                            Modifier.padding(horizontal = BuroSpacing.Sm, vertical = BuroSpacing.Xs),
                        color = if (state.active) BuroColors.Primary else BuroColors.TextMuted,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Spacer(Modifier.width(BuroSpacing.Md))
            }
            Text(
                text = if (showingOffers) text.subscriptionsWhereToWatch else text.subscriptionsBrowseByService,
                color = BuroColors.Text,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }

        // Only over the shelves. On a title's offers there is nothing to filter, and leaving the
        // buttons there would suggest they change what is on screen when they do not.
        if (!showingOffers) {
            Row(horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Xs)) {
                KindFilter(text.subscriptionsFilterMovies, kind == TmdbDiscoverKind.MOVIES) {
                    onSelectKind(TmdbDiscoverKind.MOVIES)
                }
                KindFilter(text.subscriptionsFilterSeries, kind == TmdbDiscoverKind.SERIES) {
                    onSelectKind(TmdbDiscoverKind.SERIES)
                }
                // Between Series and Coming soon: it is a view of series, and it reads as a
                // timeline — what aired, then what is due.
                KindFilter(text.subscriptionsFilterThisWeek, kind == TmdbDiscoverKind.THIS_WEEK) {
                    onSelectKind(TmdbDiscoverKind.THIS_WEEK)
                }
                KindFilter(text.subscriptionsFilterUpcoming, kind == TmdbDiscoverKind.UPCOMING) {
                    onSelectKind(TmdbDiscoverKind.UPCOMING)
                }
            }

            // The caveat, shown only where it applies. A release date is known; the service that
            // will carry it is not, and a shelf under a provider's name implies otherwise.
            if (kind == TmdbDiscoverKind.UPCOMING) {
                Text(
                    text = text.subscriptionsUpcomingNote,
                    color = BuroColors.TextSubtle,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        if (capability.requiresDemoLabel) DemoNotice(text)
    }
}

/** One of the three shelf filters. Pill-shaped, gold when it is the one in effect. */
@Composable
private fun KindFilter(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    BuroInteractiveRow(
        onClick = onClick,
        selected = selected,
        shape = BuroRadius.Pill,
        contentDescription = label,
    ) { state ->
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = BuroSpacing.Md, vertical = BuroSpacing.Xs),
            color =
                when {
                    selected -> BuroColors.Primary
                    state.active -> BuroColors.Text
                    else -> BuroColors.TextMuted
                },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/**
 * The shelves themselves: a vertical list of horizontal rails.
 *
 * Two scroll axes, both of which have to be reachable without a drag — the column carries arrow
 * keys, pointer-edge travel and a visible scrollbar; each rail carries its own, in
 * [ProviderShelfRow].
 */
@Composable
private fun ProviderShelves(
    shelves: List<ProviderShelf>,
    showDemoBadge: Boolean,
    onSelectTitle: (ExternalTitleDetails) -> Unit,
    emptyMessage: String,
    loadFailed: Boolean,
    failureMessage: String,
    retryLabel: String,
    onRetry: () -> Unit,
    /** Opens one service's full catalogue. Null while there is no wider list to open. */
    onSeeMore: ((StreamingProvider) -> Unit)? = null,
) {
    if (shelves.isEmpty()) {
        if (loadFailed) {
            ProviderShelvesFailure(
                message = failureMessage,
                retryLabel = retryLabel,
                onRetry = onRetry,
            )
        } else {
            ProviderShelvesEmpty(message = emptyMessage)
        }
        return
    }

    val listState = rememberRestoredListState("subscriptions")

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .arrowScrollableList(listState)
                    .edgeScrollableVertically(listState),
            contentPadding = PaddingValues(bottom = BuroSpacing.Xl),
            verticalArrangement = Arrangement.spacedBy(BuroSpacing.Lg),
        ) {
            items(shelves, key = { shelf -> shelf.provider.id }) { shelf ->
                ProviderShelfRow(
                    shelf = shelf,
                    showDemoBadge = showDemoBadge,
                    onSelectTitle = onSelectTitle,
                    // Null where there is nothing wider to open. The caller decides: "Em breve" is
                    // not a service, so it has no fuller catalogue and gets no card.
                    onSeeMore = onSeeMore?.let { open -> { open(shelf.provider) } },
                )
            }
        }

        SubscriptionsScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd),
            adapter = rememberScrollbarAdapter(listState),
        )
    }
}

/** A recoverable request error. Unlike the valid empty state, it always offers another attempt. */
@Composable
private fun ProviderShelvesFailure(
    message: String,
    retryLabel: String,
    onRetry: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                color = BuroColors.TextSubtle,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(BuroSpacing.Md))
            BuroInteractiveRow(
                onClick = onRetry,
                selected = false,
                shape = BuroRadius.Pill,
                contentDescription = retryLabel,
            ) {
                Text(
                    text = retryLabel,
                    modifier = Modifier.padding(horizontal = BuroSpacing.Lg, vertical = BuroSpacing.Sm),
                    color = BuroColors.Primary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/** One title's ranked offers. Nothing is hidden: the ranking orders, it never filters. */
/**
 * The film at the top of the page: backdrop, poster, facts and the trailer button.
 *
 * The backdrop is the film's own art. It sits behind a scrim because its brightness is unknown and
 * white text over a pale image is unreadable. A film with no backdrop simply gets no image — the
 * facts still read, and a grey placeholder would look like a failed load.
 */
@Composable
private fun TitleFacts(
    details: TmdbTitleDetails,
    text: DesktopStrings,
    onPlayTrailer: (String) -> Unit,
    /** Whether this profile already asked to be reminded about this title. */
    hasReminder: Boolean = false,
    /** Marks or unmarks the title. Null hides the button. */
    onToggleReminder: (() -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(BuroSpacing.Xs)) {
        Text(
            text = details.title,
            color = BuroColors.Text,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        // Year, rating, runtime and genres, each dropped when absent so the line never reads
        // "· · ·" around missing values.
        val facts =
            listOfNotNull(
                details.year?.toString(),
                details.rating?.takeIf { it > 0 }?.let { rating -> "★ %.1f".format(rating) },
                details.runtimeMinutes?.takeIf { it > 0 }?.let { minutes -> "$minutes min" },
                details.genres.take(3).joinToString(", ").takeIf(String::isNotBlank),
            ).joinToString("  ·  ")
        if (facts.isNotBlank()) {
            Text(
                text = facts,
                color = BuroColors.TextMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        // Trailer and reminder on one line. Both are things to do with a film you cannot watch
        // yet, which is exactly what this page is about.
        if (details.youtubeTrailerId != null || onToggleReminder != null) {
            Spacer(Modifier.height(BuroSpacing.Xxs))
            Row(horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Xs)) {
                details.youtubeTrailerId?.let { trailerId ->
                    BuroInteractiveRow(
                        onClick = { onPlayTrailer(trailerId) },
                        selected = false,
                        shape = BuroRadius.Pill,
                        contentDescription = text.subscriptionsWatchTrailer,
                    ) { state ->
                        Text(
                            text = text.subscriptionsWatchTrailer,
                            modifier =
                                Modifier.padding(horizontal = BuroSpacing.Md, vertical = BuroSpacing.Xs),
                            color = if (state.active) BuroColors.Primary else BuroColors.Text,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                // The reason this screen needed the button most: an upcoming film has no catalogue
                // row to open, so marking it here is the only way to say "tell me when this lands".
                onToggleReminder?.let { toggle ->
                    val label =
                        if (hasReminder) {
                            "◉  ${text.savedForLater.reminderActive}"
                        } else {
                            "○  ${text.savedForLater.reminderAdd}"
                        }
                    BuroInteractiveRow(
                        onClick = toggle,
                        selected = hasReminder,
                        shape = BuroRadius.Pill,
                        contentDescription = label,
                    ) { state ->
                        Text(
                            text = label,
                            modifier =
                                Modifier.padding(horizontal = BuroSpacing.Md, vertical = BuroSpacing.Xs),
                            color =
                                when {
                                    hasReminder -> BuroColors.Primary
                                    state.active -> BuroColors.Primary
                                    else -> BuroColors.Text
                                },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            // Said once the mark exists, for the same reason it is said on the film page: the app
            // stores this but does not yet announce it, and a promise it cannot keep is worse than
            // a plainly stated limit.
            if (hasReminder) {
                Text(
                    text = text.savedForLater.reminderNoNotice,
                    color = BuroColors.TextSubtle,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/** A heading over one section of the page. */
@Composable
private fun SectionHeading(label: String) {
    Text(
        text = label,
        color = BuroColors.TextMuted,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
    )
}

/**
 * The cast, as circular photos with names.
 *
 * Scrolls horizontally with arrow keys and pointer-edge travel, like every other rail here. A
 * performer with no photo keeps their place with an initial instead — dropping them would make the
 * strip lie about who is in the film.
 */
@Composable
private fun CastStrip(cast: List<TmdbCastMember>) {
    val railState = rememberLazyListState()
    LazyRow(
        state = railState,
        modifier = Modifier.fillMaxWidth().arrowScrollable(railState).edgeScrollable(railState),
        horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Sm),
    ) {
        items(cast, key = { member -> member.name }) { member ->
            Column(
                modifier = Modifier.width(84.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier.size(72.dp).clip(CircleShape).background(BuroColors.SurfaceRaised),
                    contentAlignment = Alignment.Center,
                ) {
                    if (member.photoUrl != null) {
                        BuroRemoteArtwork(
                            artworkUrl = member.photoUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            CastInitial(member.name)
                        }
                    } else {
                        CastInitial(member.name)
                    }
                }
                Text(
                    text = member.name,
                    color = BuroColors.Text,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                member.character?.takeIf(String::isNotBlank)?.let { character ->
                    Text(
                        text = character,
                        color = BuroColors.TextSubtle,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Stands in for a missing headshot: the performer's initial, never a broken-image box. */
@Composable
private fun CastInitial(name: String) {
    Text(
        text = name.trim().take(1).uppercase(),
        color = BuroColors.TextSubtle,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun OfferList(
    ranking: OfferRanking,
    showDemoBadge: Boolean,
    text: DesktopStrings,
    onOpenOffer: (RankedOffer) -> Unit,
    /** The film's own page. Null draws only the provider rows, as this screen did before. */
    page: TmdbTitleDetails? = null,
    onPlayTrailer: (String) -> Unit = {},
    /** Whether the profile asked to be reminded about this title. Keyed by title and year. */
    hasReminderFor: (String, Int?) -> Boolean = { _, _ -> false },
    /** Marks or unmarks it, carrying the poster. Null hides the button. */
    onToggleReminder: ((String, Int?, String?) -> Unit)? = null,
) {
    val listState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .arrowScrollableList(listState)
                    .edgeScrollableVertically(listState),
            contentPadding =
                PaddingValues(
                    start = BuroSpacing.Lg,
                    end = BuroSpacing.Lg,
                    bottom = BuroSpacing.Xl,
                ),
            verticalArrangement = Arrangement.spacedBy(BuroSpacing.Sm),
        ) {
            // Facts and the answer on the left, poster on the right. "Where can I watch this" is
            // the question the screen exists for, so it sits at the top rather than below a
            // synopsis the user has to scroll past to reach it.
            item(key = "page-top") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Lg),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(BuroSpacing.Sm),
                    ) {
                        page?.let { details ->
                            TitleFacts(
                                details = details,
                                text = text,
                                onPlayTrailer = onPlayTrailer,
                                hasReminder = hasReminderFor(details.title, details.year),
                                onToggleReminder =
                                    onToggleReminder?.let { toggle ->
                                        { toggle(details.title, details.year, details.posterUrl) }
                                    },
                            )
                        }
                        SectionHeading(text.subscriptionsAvailableOn)
                        // An upcoming film reaches this page with nothing to list. Saying so is
                        // the point of opening it: the heading alone over a blank space reads as a
                        // panel that failed to load rather than as an answer.
                        if (ranking.isEmpty) {
                            Text(
                                text = text.subscriptionsUpcomingNote,
                                color = BuroColors.TextSubtle,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        ranking.all.forEach { ranked ->
                            OfferRow(
                                ranked = ranked,
                                text = text,
                                showDemoBadge = showDemoBadge,
                                // Credited on real listings only: demo rows came from nobody, and
                                // the user's own library is something the app worked out itself.
                                showAttribution =
                                    !showDemoBadge && ranked.offer.type != OfferType.USER_LIBRARY,
                                onClick = { onOpenOffer(ranked) },
                            )
                        }
                    }

                    // Fixed width so the offers keep a readable measure on a wide window; absent
                    // entirely when TMDb has no poster, rather than leaving a grey rectangle.
                    page?.posterUrl?.let { poster ->
                        Box(
                            modifier =
                                Modifier
                                    .width(200.dp)
                                    .aspectRatio(2f / 3f)
                                    .clip(BuroRadius.Medium)
                                    .background(BuroColors.SurfaceRaised),
                        ) {
                            BuroRemoteArtwork(
                                artworkUrl = poster,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                            ) {}
                        }
                    }
                }
            }

            // Below the fold: the reading matter. Each part appears only when TMDb has it —
            // coverage is uneven, and a heading over nothing reads as a fault.
            page?.overview?.takeIf(String::isNotBlank)?.let { overview ->
                item(key = "page-synopsis") {
                    Column(verticalArrangement = Arrangement.spacedBy(BuroSpacing.Xs)) {
                        Spacer(Modifier.height(BuroSpacing.Xs))
                        SectionHeading(text.subscriptionsSynopsis)
                        Text(
                            text = overview,
                            color = BuroColors.TextMuted,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            page?.cast?.takeIf { it.isNotEmpty() }?.let { cast ->
                item(key = "page-cast") {
                    Column(verticalArrangement = Arrangement.spacedBy(BuroSpacing.Xs)) {
                        SectionHeading(text.subscriptionsCast)
                        CastStrip(cast = cast)
                    }
                }
            }
        }

        SubscriptionsScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd),
            adapter = rememberScrollbarAdapter(listState),
        )
    }
}

/**
 * Scrollbar with explicit colours.
 *
 * The default track is very nearly the canvas colour, which on this palette makes it invisible —
 * a long list then looks unscrollable even though it is not.
 */
@Composable
private fun SubscriptionsScrollbar(
    modifier: Modifier,
    adapter: androidx.compose.foundation.v2.ScrollbarAdapter,
) {
    VerticalScrollbar(
        adapter = adapter,
        modifier = modifier.fillMaxHeight().padding(vertical = BuroSpacing.Xs),
        style =
            LocalScrollbarStyle.current.copy(
                thickness = 8.dp,
                unhoverColor = BuroColors.BorderSoft,
                hoverColor = BuroColors.Primary,
            ),
    )
}

/** Says, before any result is read, that the listings below are invented. */
@Composable
private fun DemoNotice(text: DesktopStrings) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(BuroRadius.Medium)
                .background(BuroColors.SurfaceRaised)
                .padding(BuroSpacing.Md),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        DemoBadge(text)
        Text(
            text = text.subscriptionsDemoNotice,
            color = BuroColors.TextSubtle,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun DemoBadge(text: DesktopStrings) {
    Text(
        text = text.subscriptionsDemoBadge,
        color = BuroColors.Primary,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        modifier =
            Modifier
                .clip(BuroRadius.Pill)
                .background(BuroColors.Surface)
                .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun OfferRow(
    ranked: RankedOffer,
    text: DesktopStrings,
    showDemoBadge: Boolean,
    /**
     * Whether this row's availability came from JustWatch, via TMDb.
     *
     * Their terms require the source to be credited on **every item** that shows the data — a line
     * in an About screen is explicitly not enough — and state that access is revoked for usage that
     * does not comply. So this is not a nicety that can be dropped to tidy the layout.
     *
     * False for the user's own library, which the app worked out itself, and false for demo rows,
     * which came from nobody and are already labelled as invented.
     */
    showAttribution: Boolean,
    onClick: () -> Unit,
) {
    BuroInteractiveRow(
        onClick = onClick,
        // Nothing here is a current selection; the row is an action, not a choice being displayed.
        selected = false,
        modifier = Modifier.fillMaxWidth(),
        shape = BuroRadius.Medium,
        contentDescription = "${ranked.provider.displayName} — ${reasonLabel(ranked.reason, text)}",
    ) { _ ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(BuroSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // This app's own mark on its own row, so the entry the viewer cares about most
                    // is findable at a glance.
                    if (ranked.offer.type == OfferType.USER_LIBRARY) {
                        BuroMark(size = 24.dp)
                    } else {
                        // The service's mark, shown at the product owner's explicit instruction —
                        // the rule that kept these text-only was reversed deliberately, and the
                        // marks belong to the services themselves.
                        //
                        // Always beside the name rather than instead of it: a logo that fails to
                        // load, or a service TMDb has no image for, then degrades to exactly the
                        // previous behaviour instead of to a nameless gap.
                        ranked.provider.logoUrl?.let { logo ->
                            Box(
                                modifier =
                                    Modifier
                                        .size(24.dp)
                                        .clip(BuroRadius.Small)
                                        .background(BuroColors.SurfaceRaised),
                            ) {
                                BuroRemoteArtwork(
                                    artworkUrl = logo,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit,
                                ) {}
                            }
                        }
                    }
                    Text(
                        text = ranked.provider.displayName,
                        color = BuroColors.Text,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (showDemoBadge) DemoBadge(text)
                }
                Text(
                    text = reasonLabel(ranked.reason, text),
                    color = BuroColors.TextSubtle,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (showAttribution) {
                    // Not translated: it names a company and must read identically everywhere.
                    Text(
                        text = WATCH_PROVIDER_ATTRIBUTION,
                        color = BuroColors.TextSubtle,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.width(BuroSpacing.Sm))

            // A price only when the offer actually has one. A subscription or the user's own library
            // shows nothing here rather than "R$ 0,00", which would read as free content.
            ranked.offer.price?.let { price ->
                Text(
                    text = formatPrice(price.amountMinor, price.currencyCode),
                    color = BuroColors.Text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

/** The user-facing wording for why an offer is where it is. */
private fun reasonLabel(
    reason: OfferReason,
    text: DesktopStrings,
): String =
    when (reason) {
        OfferReason.IN_YOUR_LIBRARY -> text.subscriptionsInYourLibrary
        OfferReason.INCLUDED_IN_YOUR_SUBSCRIPTION -> text.subscriptionsIncludedInSubscription
        OfferReason.FREE_WITH_ADS -> text.subscriptionsFreeWithAds
        // The cheapest rental and any other rental read the same to the user; the ordering already
        // put the cheapest first, and a "cheapest" badge on a two-item list is noise.
        OfferReason.CHEAPEST_RENTAL, OfferReason.RENTAL -> text.subscriptionsRent
        OfferReason.PURCHASE -> text.subscriptionsBuy
        OfferReason.REQUIRES_NEW_SUBSCRIPTION -> text.subscriptionsRequiresSubscription
        OfferReason.NOT_AVAILABLE -> text.subscriptionsUnavailable
    }

/**
 * Minor units to a readable amount.
 *
 * Two decimals with the currency code rather than a symbol: the code is unambiguous, and the app
 * holds no locale table mapping every currency to its symbol and separator.
 */
private fun formatPrice(
    amountMinor: Long,
    currencyCode: String,
): String {
    val whole = amountMinor / 100
    val cents = (amountMinor % 100).toString().padStart(2, '0')
    return "$currencyCode $whole,$cents"
}

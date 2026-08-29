package com.lucasserafin94.iptvburo.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.lucasserafin94.iptvburo.R
import com.lucasserafin94.iptvburo.domain.model.OfferReason
import com.lucasserafin94.iptvburo.metadata.WATCH_PROVIDER_ATTRIBUTION
import com.lucasserafin94.iptvburo.ui.ExpandedServiceUi
import com.lucasserafin94.iptvburo.ui.ProviderShelfUi
import com.lucasserafin94.iptvburo.ui.SubscriptionOfferUi
import com.lucasserafin94.iptvburo.ui.SubscriptionTitleUi
import com.lucasserafin94.iptvburo.ui.SubscriptionsKindUi
import com.lucasserafin94.iptvburo.ui.SubscriptionsUi
import com.lucasserafin94.iptvburo.ui.components.FocusSurface
import com.lucasserafin94.iptvburo.ui.designsystem.BuroButton
import com.lucasserafin94.iptvburo.ui.designsystem.BuroButtonStyle
import com.lucasserafin94.iptvburo.ui.designsystem.BuroMark
import com.lucasserafin94.iptvburo.ui.theme.BuroAccent
import com.lucasserafin94.iptvburo.ui.theme.BuroSurface
import com.lucasserafin94.iptvburo.ui.theme.BuroSurfaceRaised
import com.lucasserafin94.iptvburo.ui.theme.BuroTextPrimary
import com.lucasserafin94.iptvburo.ui.theme.BuroTextSecondary
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Assinaturas on Android — GDD 9, the counterpart of the Windows screen.
 *
 * It answers one question: where can this title be watched. **It never plays anything.** Every row
 * leads to the service's own app or site, which is what keeps the app a signpost rather than a way
 * around a paywall.
 *
 * Two states, not two destinations: the shelves, and one title's offers. Back returns to the
 * shelves, so making the offers a separate destination would have turned that into a navigation
 * special case.
 *
 * The screen is only reachable when a metadata key is configured — see `StreamingDiscoveryCapability`
 * — so it never opens onto nothing.
 */
@Composable
fun SubscriptionsScreen(
    state: SubscriptionsUi,
    onSelectKind: (SubscriptionsKindUi) -> Unit,
    onSelectTitle: (SubscriptionTitleUi) -> Unit,
    onBackToShelves: () -> Unit,
    onOpenOffer: (SubscriptionOfferUi) -> Unit,
    /** Marks or unmarks the open title. Null hides the button, as on the details screens. */
    onToggleReminder: (() -> Unit)? = null,
    /** Whether the open title is already marked. */
    hasReminder: Boolean = false,
    /** Opens one service's whole catalogue, from the end of its shelf. */
    onSeeMore: (ProviderShelfUi) -> Unit = {},
    /** Returns from that catalogue to the shelves. */
    onCloseExpanded: () -> Unit = {},
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compact = maxWidth < 600.dp
        val gutter = if (compact) 16.dp else 28.dp

        Column(modifier = Modifier.fillMaxSize()) {
            SubscriptionsHeader(
                showingOffers = state.selected != null || state.expandedService != null,
                kind = state.kind,
                onSelectKind = onSelectKind,
                // Back leaves the expanded catalogue first: it is the layer on top, and
                // skipping to the shelves would discard a step the viewer took.
                onBack = { if (state.expandedService != null) onCloseExpanded() else onBackToShelves() },
                gutter = gutter,
            )

            // Weighted so the scrolling body is measured against the space that is left. An
            // unweighted child in a Column gets unbounded height and never becomes scrollable.
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.selected != null ->
                        OfferList(
                            title = state.selected,
                            offers = state.offers,
                            isLoading = state.isSelectionLoading,
                            unknown = state.selectionUnknown,
                            gutter = gutter,
                            onOpenOffer = onOpenOffer,
                            onToggleReminder = onToggleReminder,
                            hasReminder = hasReminder,
                        )

                    state.expandedService != null ->
                        ExpandedServiceGrid(
                            expanded = state.expandedService,
                            gutter = gutter,
                            onSelectTitle = onSelectTitle,
                        )

                    state.isLoading -> LoadingBody()

                    state.shelves.isEmpty() ->
                        EmptyBody(
                            message = stringResource(R.string.subscriptions_no_shelves),
                            gutter = gutter,
                        )

                    else ->
                        ProviderShelves(
                            shelves = state.shelves,
                            gutter = gutter,
                            onSelectTitle = onSelectTitle,
                            onSeeMore = onSeeMore,
                        )
                }
            }
        }
    }
}

@Composable
private fun SubscriptionsHeader(
    showingOffers: Boolean,
    kind: SubscriptionsKindUi,
    onSelectKind: (SubscriptionsKindUi) -> Unit,
    onBack: () -> Unit,
    gutter: androidx.compose.ui.unit.Dp,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = gutter, end = gutter, top = gutter, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showingOffers) {
                // wrapContentWidth: FocusSurface propagates its minimum constraints to the
                // content, so a Box filling that size made this button as wide as the row and
                // pushed the title off screen entirely.
                FocusSurface(
                    onClick = onBack,
                    shape = RoundedCornerShape(50),
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.height(40.dp).wrapContentWidth(),
                ) {
                    Text(
                        text = "‹  ${stringResource(R.string.subscriptions_back_to_services)}",
                        color = BuroTextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 14.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
            }
            Text(
                text =
                    stringResource(
                        if (showingOffers) {
                            R.string.subscriptions_where_to_watch
                        } else {
                            R.string.subscriptions_browse_by_service
                        },
                    ),
                color = BuroTextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        // Only over the shelves. On a title's offers there is nothing to filter, and leaving the
        // buttons there would suggest they change what is on screen when they do not.
        if (!showingOffers) {
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(SubscriptionsKindUi.entries.toList(), key = { it.name }) { entry ->
                    KindFilter(
                        label = stringResource(entry.labelResource()),
                        selected = entry == kind,
                        onClick = { onSelectKind(entry) },
                    )
                }
            }

            // The caveat, shown only where it applies. A release date is known; the service that
            // will carry it is not, and a shelf under a provider's name implies otherwise.
            if (kind == SubscriptionsKindUi.UPCOMING) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.subscriptions_upcoming_note),
                    color = BuroTextSecondary,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

private fun SubscriptionsKindUi.labelResource(): Int =
    when (this) {
        SubscriptionsKindUi.MOVIES -> R.string.subscriptions_filter_movies
        SubscriptionsKindUi.SERIES -> R.string.subscriptions_filter_series
        SubscriptionsKindUi.THIS_WEEK -> R.string.subscriptions_filter_this_week
        SubscriptionsKindUi.UPCOMING -> R.string.subscriptions_filter_upcoming
    }

@Composable
private fun KindFilter(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FocusSurface(
        onClick = onClick,
        selected = selected,
        shape = RoundedCornerShape(50),
        contentAlignment = Alignment.Center,
        modifier = Modifier.height(38.dp).wrapContentWidth(),
    ) {
        Text(
            text = label,
            color = if (selected) BuroAccent else BuroTextSecondary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

/** A vertical list of horizontal rails, one per service. */
@Composable
private fun ProviderShelves(
    shelves: List<ProviderShelfUi>,
    gutter: androidx.compose.ui.unit.Dp,
    onSelectTitle: (SubscriptionTitleUi) -> Unit,
    onSeeMore: (ProviderShelfUi) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        items(shelves, key = { shelf -> shelf.providerId }) { shelf ->
            Column {
                Text(
                    text = shelf.providerName,
                    color = BuroTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = gutter, vertical = 8.dp),
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = gutter),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(shelf.titles, key = { title -> "${shelf.providerId}:${title.externalId}" }) { title ->
                        PosterCard(title = title, onClick = { onSelectTitle(title) })
                    }
                    // The end of the rail is where "what else is on here?" gets asked, so the
                    // answer sits exactly there rather than in a heading nobody reads.
                    //
                    // Only for a shelf that is really one service: "Em breve" is the set of films
                    // no service carries yet, so there is no catalogue behind it to open.
                    shelf.tmdbProviderId?.let {
                        item(key = "${shelf.providerId}:see-more") {
                            SeeMoreCard(onClick = { onSeeMore(shelf) })
                        }
                    }
                }
            }
        }
    }
}

/** One title's cover. A title with no poster keeps its place with its name rather than vanishing. */
@Composable
private fun PosterCard(
    title: SubscriptionTitleUi,
    onClick: () -> Unit,
) {
    FocusSurface(
        onClick = onClick,
        modifier = Modifier.width(120.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(BuroSurfaceRaised),
                contentAlignment = Alignment.Center,
            ) {
                if (title.posterUrl != null) {
                    AsyncImage(
                        model = title.posterUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        text = title.title,
                        color = BuroTextSecondary,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = title.title,
                color = BuroTextPrimary,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            title.year?.let { year ->
                Text(text = year.toString(), color = BuroTextSecondary, fontSize = 11.sp)
            }
        }
    }
}

/**
 * One title's page: facts, where to watch, then the reading matter.
 *
 * "Where can I watch this" is the question the screen exists for, so the offers sit above the
 * synopsis rather than below something the user has to scroll past.
 */
@Composable
private fun OfferList(
    title: SubscriptionTitleUi,
    offers: List<SubscriptionOfferUi>,
    isLoading: Boolean,
    unknown: Boolean,
    gutter: androidx.compose.ui.unit.Dp,
    onOpenOffer: (SubscriptionOfferUi) -> Unit,
    /** Marks or unmarks this title. Null hides the button, as on the details screens. */
    onToggleReminder: (() -> Unit)? = null,
    hasReminder: Boolean = false,
) {
    val androidContext = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = gutter, end = gutter, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "facts") {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                title.posterUrl?.let { poster ->
                    AsyncImage(
                        model = poster,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .width(110.dp)
                                .aspectRatio(2f / 3f)
                                .clip(RoundedCornerShape(12.dp)),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title.title,
                        color = BuroTextPrimary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // Each fact dropped when absent, so the line never reads "· · ·" around gaps.
                    val facts =
                        listOfNotNull(
                            // The full date in place of the bare year where the catalogue supplied
                            // one: on a coming-soon title "2026" is the one thing a viewer cannot
                            // act on, and the day is what tells them whether to wait or forget it.
                            title.releaseDate?.toDisplayDate() ?: title.year?.toString(),
                            title.rating?.takeIf { it > 0.0 }?.let { rating -> "★ %.1f".format(rating) },
                            title.runtimeMinutes?.takeIf { it > 0 }?.let { minutes -> "$minutes min" },
                            title.genres.take(3).joinToString(", ").takeIf(String::isNotBlank),
                        ).joinToString("  ·  ")
                    if (facts.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(text = facts, color = BuroTextSecondary, fontSize = 13.sp)
                    }
                }
            }
        }

        // Trailer and reminder together. The trailer id was already being fetched with the page and
        // simply never drawn, so the one screen whose whole job is deciding whether to watch
        // something offered no way to look.
        item(key = "actions") {
            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                title.youtubeTrailerId?.let { trailerId ->
                    BuroButton(
                        onClick = {
                            runCatching {
                                androidContext.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://www.youtube.com/watch?v=$trailerId"),
                                    ),
                                )
                            }
                        },
                        style = BuroButtonStyle.Ghost,
                    ) {
                        Text(stringResource(R.string.details_trailer))
                    }
                }
                // The reminder this page most needed and did not have. A title on the upcoming
                // shelf is precisely the case reminders were built for — it is not in anyone's
                // catalogue yet, so there is no details page to mark it from, and the release date
                // that arrives with it is what turns the mark into "tell me when it is out".
                onToggleReminder?.let { toggle ->
                    BuroButton(onClick = toggle, style = BuroButtonStyle.Ghost) {
                        Icon(
                            if (hasReminder) {
                                Icons.Default.Notifications
                            } else {
                                Icons.Default.NotificationsNone
                            },
                            contentDescription = null,
                            tint = if (hasReminder) BuroAccent else BuroTextPrimary,
                        )
                        // Measured against the longer label, as the film screen does: the two words
                        // differ in width, and a button that resizes on tap reflows the row.
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(R.string.reminder_added),
                                color = Color.Transparent,
                                maxLines = 1,
                            )
                            Text(
                                stringResource(
                                    if (hasReminder) R.string.reminder_added else R.string.reminder_add,
                                ),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        item(key = "available-on") {
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.subscriptions_available_on),
                color = BuroTextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        when {
            isLoading -> item(key = "offers-loading") { LoadingRow() }

            // "We cannot say" — never rendered as "not available anywhere", which is a different
            // and much stronger claim than the data supports.
            unknown || offers.isEmpty() ->
                item(key = "offers-unknown") {
                    Text(
                        text = stringResource(R.string.subscriptions_availability_unknown),
                        color = BuroTextSecondary,
                        fontSize = 13.sp,
                    )
                }

            else ->
                items(offers, key = { offer -> "${offer.providerId}:${offer.reason.name}" }) { offer ->
                    OfferRow(offer = offer, onClick = { onOpenOffer(offer) })
                }
        }

        title.overview?.takeIf(String::isNotBlank)?.let { overview ->
            item(key = "synopsis") {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.subscriptions_synopsis),
                    color = BuroTextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(text = overview, color = BuroTextSecondary, fontSize = 13.sp, lineHeight = 19.sp)
            }
        }

        if (title.cast.isNotEmpty()) {
            item(key = "cast") {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.subscriptions_cast),
                    color = BuroTextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(title.cast, key = { member -> member.name }) { member ->
                        Column(
                            modifier = Modifier.width(78.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier =
                                    Modifier.size(66.dp).clip(CircleShape).background(BuroSurfaceRaised),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (member.photoUrl != null) {
                                    AsyncImage(
                                        model = member.photoUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    // An initial, never a broken-image box: dropping the performer
                                    // would make the strip lie about who is in the film.
                                    Text(
                                        text = member.name.trim().take(1).uppercase(),
                                        color = BuroTextSecondary,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = member.name,
                                color = BuroTextPrimary,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OfferRow(
    offer: SubscriptionOfferUi,
    onClick: () -> Unit,
) {
    FocusSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // This app's own mark on its own row, matching Windows, so the entry the
                    // viewer cares about most is findable at a glance the same way a real
                    // service's mark is.
                    if (offer.isUserLibrary) {
                        BuroMark(size = 22.dp)
                    } else {
                        // Always beside the name rather than instead of it: a logo TMDb has none
                        // for degrades to exactly the previous text-only behaviour, never to a
                        // nameless gap.
                        offer.logoUrl?.let { logo ->
                            Box(
                                modifier =
                                    Modifier
                                        .size(22.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(BuroSurfaceRaised),
                            ) {
                                AsyncImage(
                                    model = logo,
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                    Text(
                        text = offer.providerName,
                        color = if (offer.isUserLibrary) BuroAccent else BuroTextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(offer.reason.labelResource()),
                    color = BuroTextSecondary,
                    fontSize = 12.sp,
                )
                if (offer.requiresAttribution) {
                    // Not translated: it names a company and must read identically everywhere.
                    // JustWatch's terms require this on every item showing their data — an About
                    // screen is explicitly not enough — so it is not droppable to tidy the layout.
                    Spacer(Modifier.height(2.dp))
                    Text(text = WATCH_PROVIDER_ATTRIBUTION, color = BuroTextSecondary, fontSize = 10.sp)
                }
            }
        }
    }
}

private fun OfferReason.labelResource(): Int =
    when (this) {
        OfferReason.IN_YOUR_LIBRARY -> R.string.subscriptions_in_your_library
        OfferReason.INCLUDED_IN_YOUR_SUBSCRIPTION -> R.string.subscriptions_included_in_subscription
        OfferReason.FREE_WITH_ADS -> R.string.subscriptions_free_with_ads
        // The cheapest rental and any other rental read the same to the user, and TMDb returns no
        // prices at all, so there is nothing to distinguish them with.
        OfferReason.CHEAPEST_RENTAL, OfferReason.RENTAL -> R.string.subscriptions_rent
        OfferReason.PURCHASE -> R.string.subscriptions_buy
        OfferReason.REQUIRES_NEW_SUBSCRIPTION -> R.string.subscriptions_requires_subscription
        OfferReason.NOT_AVAILABLE -> R.string.subscriptions_unavailable
    }

@Composable
private fun LoadingBody() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = BuroAccent)
    }
}

@Composable
private fun LoadingRow() {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = BuroAccent)
    }
}

@Composable
private fun EmptyBody(
    message: String,
    gutter: androidx.compose.ui.unit.Dp,
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(horizontal = gutter),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(BuroSurface).padding(20.dp),
        ) {
            Text(text = message, color = BuroTextSecondary, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
}

/**
 * An ISO release date as the viewer's own locale writes it.
 *
 * Falls back to the raw string when the catalogue supplies something that is not a full date — TMDb
 * occasionally returns an empty string or a year alone, and showing that unchanged is better than
 * showing nothing or crashing a shelf over a malformed field.
 */
private fun String.toDisplayDate(): String? {
    val trimmed = trim()
    if (trimmed.isEmpty()) return null
    return runCatching {
        LocalDate.parse(trimmed).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }.getOrDefault(trimmed)
}

/**
 * The card at the end of a rail that opens the whole service.
 *
 * Shaped like a poster rather than styled as a button: it sits in a row of posters, and a control
 * of a different size would break the rhythm of the rail and read as something having gone wrong.
 */
@Composable
private fun SeeMoreCard(onClick: () -> Unit) {
    FocusSurface(
        onClick = onClick,
        modifier = Modifier.width(120.dp),
        shape = RoundedCornerShape(12.dp),
        backgroundColor = BuroSurfaceRaised,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = BuroAccent,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.subscriptions_see_more),
                color = BuroTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * One service's whole catalogue, as a grid.
 *
 * A grid rather than a longer rail: this is for browsing hundreds of titles, and a horizontal row
 * that long is a scroll nobody finishes. The shelf's own twenty are already on screen when it opens,
 * so the page is useful while the rest of the pages arrive.
 */
@Composable
private fun ExpandedServiceGrid(
    expanded: ExpandedServiceUi,
    gutter: androidx.compose.ui.unit.Dp,
    onSelectTitle: (SubscriptionTitleUi) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.subscriptions_all_on, expanded.providerName),
            color = BuroTextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = gutter, vertical = 10.dp),
        )
        if (expanded.isLoading) {
            // Under the heading rather than over the grid: the first twenty titles are already
            // there and worth reading while the rest load.
            Row(
                modifier = Modifier.padding(horizontal = gutter).padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    color = BuroAccent,
                    strokeWidth = 2.dp,
                )
                Text(
                    text = stringResource(R.string.subscriptions_loading),
                    color = BuroTextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 108.dp),
            contentPadding = PaddingValues(start = gutter, end = gutter, bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(expanded.titles, key = { title -> title.externalId }) { title ->
                PosterCard(title = title, onClick = { onSelectTitle(title) })
            }
        }
    }
}

package com.lucasserafin94.iptvburo.ui

import com.lucasserafin94.iptvburo.domain.model.CatalogContentType
import com.lucasserafin94.iptvburo.domain.model.Channel
import com.lucasserafin94.iptvburo.domain.model.ContentIdentity
import com.lucasserafin94.iptvburo.domain.model.ExternalContentId
import com.lucasserafin94.iptvburo.domain.model.LibraryCandidate
import com.lucasserafin94.iptvburo.domain.model.MatchKind
import com.lucasserafin94.iptvburo.domain.model.ExternalTitle
import com.lucasserafin94.iptvburo.domain.model.ExternalTitleKind
import com.lucasserafin94.iptvburo.domain.model.OfferReason
import com.lucasserafin94.iptvburo.domain.model.OfferType
import com.lucasserafin94.iptvburo.domain.model.StreamingOffer
import com.lucasserafin94.iptvburo.metadata.TMDB_NAMESPACE
import com.lucasserafin94.iptvburo.metadata.TMDB_SERIES_NAMESPACE
import com.lucasserafin94.iptvburo.metadata.TmdbDiscoverKind
import com.lucasserafin94.iptvburo.metadata.TmdbServiceShelf
import com.lucasserafin94.iptvburo.metadata.TmdbTitleDetails

/**
 * Translating between the shared discovery domain and what the Android screens render.
 *
 * Kept out of both the view model and the composables: the view model would grow another hundred
 * lines of shape-shuffling, and a composable doing this would make the rules unassertable without
 * rendering. Every function here is pure, so the mapping can be tested directly.
 */

internal fun SubscriptionsKindUi.toDiscoverKind(): TmdbDiscoverKind =
    when (this) {
        SubscriptionsKindUi.MOVIES -> TmdbDiscoverKind.MOVIES
        SubscriptionsKindUi.SERIES -> TmdbDiscoverKind.SERIES
        SubscriptionsKindUi.THIS_WEEK -> TmdbDiscoverKind.THIS_WEEK
        SubscriptionsKindUi.UPCOMING -> TmdbDiscoverKind.UPCOMING
    }

internal fun TmdbServiceShelf.toUi(): ProviderShelfUi =
    ProviderShelfUi(
        providerId = provider.id,
        providerName = provider.displayName,
        tmdbProviderId = tmdbProviderId,
        titles = titles.map(ExternalTitle::toUi),
    )

internal fun ExternalTitle.toUi(): SubscriptionTitleUi =
    SubscriptionTitleUi(
        externalNamespace = id.namespace,
        externalId = id.value,
        title = title,
        year = year,
        releaseDate = releaseDate,
        posterUrl = posterUrl,
        isSeries = kind == ExternalTitleKind.SERIES,
        isDemo = isDemo,
    )

/** Rebuilds the domain title the offers lookup needs from what the row is holding. */
internal fun SubscriptionTitleUi.toExternalTitle(): ExternalTitle =
    ExternalTitle(
        id =
            ExternalContentId(
                namespace = externalNamespace.ifBlank { if (isSeries) TMDB_SERIES_NAMESPACE else TMDB_NAMESPACE },
                value = externalId,
            ),
        title = title,
        kind = if (isSeries) ExternalTitleKind.SERIES else ExternalTitleKind.MOVIE,
        year = year,
        releaseDate = releaseDate,
        posterUrl = posterUrl,
        isDemo = isDemo,
    )

/**
 * Folds TMDb's page onto the row the user pressed.
 *
 * The row's own values win where the page has nothing, so a shelf entry never loses its poster or
 * year because the details call returned a sparser record.
 */
internal fun SubscriptionTitleUi.mergedWith(page: TmdbTitleDetails): SubscriptionTitleUi =
    copy(
        title = page.title.takeIf(String::isNotBlank) ?: title,
        year = page.year ?: year,
        posterUrl = page.posterUrl ?: posterUrl,
        backdropUrl = page.backdropUrl ?: backdropUrl,
        overview = page.overview?.takeIf(String::isNotBlank) ?: overview,
        rating = page.rating?.takeIf { it > 0.0 } ?: rating,
        runtimeMinutes = page.runtimeMinutes?.takeIf { it > 0 } ?: runtimeMinutes,
        genres = page.genres.ifEmpty { genres },
        youtubeTrailerId = page.youtubeTrailerId ?: youtubeTrailerId,
        cast =
            page.cast
                .map { member ->
                    SubscriptionCastUi(
                        name = member.name,
                        character = member.character?.takeIf(String::isNotBlank),
                        photoUrl = member.photoUrl,
                    )
                }.ifEmpty { cast },
    )

/**
 * One offer as a row.
 *
 * [SubscriptionOfferUi.requiresAttribution] is decided here rather than in the composable: JustWatch
 * requires their data credited on every item that shows it, so the obligation travels with the row.
 * It is false only for the user's own library, which the app worked out itself, and for demo rows,
 * which came from nobody and are labelled as invented.
 */
/**
 * A catalogue item as the matcher sees it.
 *
 * The year is taken from the title when the provider did not supply one — playlists routinely write
 * "Filme (2019)" and leave the field empty — because the year is one of the strongest signals the
 * matching policy has for telling two films with the same name apart.
 */
internal fun Channel.toLibraryCandidate(): LibraryCandidate =
    LibraryCandidate(
        localContentId = id,
        title = name,
        year = year ?: ContentIdentity.yearFromTitle(name),
        kind =
            if (contentType == CatalogContentType.SERIES) {
                MatchKind.SERIES
            } else {
                MatchKind.MOVIE
            },
    )

internal fun StreamingOffer.toUi(): SubscriptionOfferUi =
    SubscriptionOfferUi(
        providerId = provider.id,
        providerName = provider.displayName,
        // Null for the user's own library, same as Windows: that row draws the app's own mark
        // instead, so a logo here would never be read anyway.
        logoUrl = if (type == OfferType.USER_LIBRARY) null else provider.logoUrl,
        reason = reasonFor(type),
        isUserLibrary = type == OfferType.USER_LIBRARY,
        requiresAttribution = type != OfferType.USER_LIBRARY,
        webUrl = launchTarget?.webUrl,
        appDeepLink = launchTarget?.appDeepLink,
    )

/**
 * The reason wording for an offer type.
 *
 * No "cheapest" reason is produced: TMDb returns no prices at all, so a cheapest claim would be
 * invented. `BestOfferPolicy` still ranks the list; it simply has no price to rank on.
 */
private fun reasonFor(type: OfferType): OfferReason =
    when (type) {
        OfferType.USER_LIBRARY -> OfferReason.IN_YOUR_LIBRARY
        OfferType.SUBSCRIPTION -> OfferReason.REQUIRES_NEW_SUBSCRIPTION
        OfferType.FREE_WITH_ADS -> OfferReason.FREE_WITH_ADS
        OfferType.RENT -> OfferReason.RENTAL
        OfferType.BUY -> OfferReason.PURCHASE
        OfferType.UNAVAILABLE -> OfferReason.NOT_AVAILABLE
    }

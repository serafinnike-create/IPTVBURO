package com.lucasserafin94.iptvburo.ui.home

import com.lucasserafin94.iptvburo.domain.model.CatalogContentType
import com.lucasserafin94.iptvburo.domain.model.Reminder
import com.lucasserafin94.iptvburo.domain.model.SeasonalCollection
import com.lucasserafin94.iptvburo.domain.model.SeasonalCollections
import com.lucasserafin94.iptvburo.domain.model.HeroCandidate
import com.lucasserafin94.iptvburo.domain.model.HeroSelection
import com.lucasserafin94.iptvburo.ui.ChannelUi
import com.lucasserafin94.iptvburo.ui.dailyEditorialRank
import com.lucasserafin94.iptvburo.ui.localEditorialDay
import com.lucasserafin94.iptvburo.ui.ContinueWatchingUi
import com.lucasserafin94.iptvburo.ui.ProviderShelfUi
import com.lucasserafin94.iptvburo.ui.SubscriptionTitleUi
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import java.util.Calendar
import java.util.Locale

/** Builds a small, stable home document from locally indexed catalog rows. */
/**
 * The rail titles and badges the home screen draws, already resolved.
 *
 * Passed in rather than looked up here: [RealHomeCatalog] is a pure object with no Context, which
 * is what makes it testable — and the labels were hard-coded Portuguese, so the whole home screen
 * stayed Portuguese whatever language the user picked.
 */
data class HomeLabels(
    val continueWatching: String,
    val continueBadge: String,
    val reminders: String,
    val reminderBadge: String,
    val newClassics: String,
    val classicBadge: String,
    val recentlyAdded: String,
    val newBadge: String,
    val topRated: String,
    val topBadge: String,
    val movies: String,
    val movieBadge: String,
    val series: String,
    val seriesBadge: String,
    val heroBadge: String,
    /** Takes the year, so "%1$d releases" reads correctly in every language. */
    val releases: (Int) -> String,
) {
    companion object {
        /**
         * The Portuguese wording the screen shipped with.
         *
         * Only for tests and previews, which have no resources to resolve. The app always passes
         * the user's own language.
         */
        val Fallback =
            HomeLabels(
                continueWatching = "Continue assistindo",
                continueBadge = "CONTINUAR",
                reminders = "Seus lembretes",
                reminderBadge = "LEMBRETE",
                newClassics = "Clássicos que chegaram agora",
                classicBadge = "CLÁSSICO",
                recentlyAdded = "Adicionados recentemente",
                newBadge = "NOVO NA FONTE",
                topRated = "Melhores avaliações",
                topBadge = "★ DESTAQUE",
                movies = "Filmes em destaque",
                movieBadge = "FILME",
                series = "Séries em destaque",
                seriesBadge = "SÉRIE",
                heroBadge = "DESTAQUE",
                releases = { year -> "Lançamentos $year" },
            )
    }
}

object RealHomeCatalog {
    fun section(
        sources: List<HomeSourceSummary>,
        catalogItems: List<ChannelUi>,
        continueWatching: List<ContinueWatchingUi> = emptyList(),
        /**
         * What the profile asked to be reminded about, drawn straight after Continue assistindo.
         *
         * There rather than further down because the two rails answer neighbouring questions —
         * "what was I in the middle of" and "what was I waiting for" — and a reminder that only
         * surfaces in a daily notification is easy to forget having made.
         */
        reminders: List<Reminder> = emptyList(),
        /**
         * Service shelves from the discovery catalogue, drawn after the user's own content.
         *
         * After, deliberately: the playlist is what the user owns and came here for. A row of
         * things they would have to subscribe to elsewhere belongs below that, not above it.
         */
        streamingShelves: List<ProviderShelfUi> = emptyList(),
        /**
         * Real synopses for banner titles, keyed by channel id.
         *
         * Absent entries keep the generic line, which is what every title had before: the plot is
         * not on a catalogue row, so it arrives a moment after the home does.
         */
        synopses: Map<String, String> = emptyMap(),
        /** Rail titles and badges in the user's language. */
        labels: HomeLabels = HomeLabels.Fallback,
    ): HomeSection {
        val continueById = continueWatching.associateBy { it.channel.id }
        val distinct = (catalogItems + continueWatching.map { it.channel }).distinctBy(ChannelUi::id)
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        // What the calendar suggests today: at Christmas the banner should open on Christmas films,
        // which is the whole point of a home screen that changes with the date. The terms are the
        // shared domain's, so the phone and the desktop celebrate the same days.
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val seasonalTerms =
            SeasonalCollections.collectionsFor(today).flatMap(SeasonalCollection::searchTerms)
        val seasonalPicks =
            if (seasonalTerms.isEmpty()) {
                emptyList()
            } else {
                distinct.filter { channel ->
                    val name = channel.name.lowercase(Locale.ROOT)
                    seasonalTerms.any { term -> name.contains(term.lowercase(Locale.ROOT)) }
                }
            }

        // Seasonal first, then this year's releases, then whatever is well rated. Ordered by the
        // day so the rotation is stable within a day and different tomorrow — the same trick the
        // daily rails already use.
        val heroCandidates =
            (
                seasonalPicks +
                    distinct.filter { it.year == currentYear } +
                    distinct.filter { (it.rating ?: 0.0) >= 7.0 } +
                    distinct
            ).asSequence()
                .filter { it.id !in continueById }
                .distinctBy(ChannelUi::id)
                .toList()
                .ifEmpty { distinct.take(1) }

        // The banner opens on what the old single-hero rule chose, and rotates through the rest.
        //
        // Keeping the first slot deterministic matters: a seasonal pick where the calendar has one,
        // otherwise this year's releases. Shuffling the whole list by a daily hash would let an
        // obscure back-catalogue title lead the home screen on a day with new arrivals.
        val leadHero =
            seasonalPicks.firstOrNull { it.id !in continueById }
                ?: heroCandidates.firstOrNull { it.year == currentYear }
                ?: heroCandidates.firstOrNull { (it.rating ?: 0.0) >= 7.0 }
                ?: heroCandidates.firstOrNull()
        val rotationSource =
            (
                listOfNotNull(leadHero) +
                    heroCandidates
                        .filterNot { it.id == leadHero?.id }
                        // The rest rotate in a daily order, so the banner is different tomorrow
                        // without the lead title moving around.
                        .sortedBy { dailyEditorialRank(it.id, localEditorialDay()) }
            ).let { ranked ->
                // Rearranged so the banner is not twenty of the same thing.
                //
                // Ordered by rank alone it fills with whatever the catalogue has most of, and
                // scrolling past twenty titles from the same year and the same shelf teaches
                // nobody what else is in there. The same rule the other two apps use, so the
                // three do not disagree about what a good banner looks like.
                HeroSelection
                    .mixed(
                        ranked.map { channel ->
                            HeroCandidate(
                                id = channel.id,
                                title = channel.name,
                                year = channel.year,
                                rating = channel.rating,
                                categoryIds = listOfNotNull(channel.categoryName),
                                isSeries = channel.contentType == CatalogContentType.SERIES,
                            )
                        },
                        currentYear,
                    ).mapNotNull { candidate -> ranked.firstOrNull { it.id == candidate.id } }
            }.take(HERO_ROTATION_SIZE)

        val heroChannel = rotationSource.firstOrNull() ?: distinct.first()
        val heroRotation =
            rotationSource.ifEmpty { listOf(heroChannel) }.map { channel ->
                val item = channel.toHomeItem(HomeCardFormat.LANDSCAPE, labels.heroBadge)
                item.copy(
                    progress = continueById[channel.id]?.progress,
                    // The title's own plot where it has arrived, trimmed to a couple of lines: a
                    // banner is a reason to press play, and the stock sentence it used to carry
                    // told the viewer to open the title to find out what it was about.
                    synopsis = synopses[channel.id]?.toBannerSynopsis() ?: item.synopsis,
                )
            }
        val hero = heroRotation.first()
        val continueRailItems =
            continueWatching
                .filterNot { it.channel.id == heroChannel.id }
                .distinctBy { it.channel.id }
        val continuedIds = continueWatching.mapTo(mutableSetOf()) { it.channel.id }
        val remaining =
            distinct.filterNot { it.id == heroChannel.id || it.id in continuedIds }.toMutableList()
        val currentReleases = remaining.filter { it.year == currentYear }.take(12)
        remaining.removeAll(currentReleases.toSet())
        val previousReleases = remaining.filter { it.year == currentYear - 1 }.take(12)
        remaining.removeAll(previousReleases.toSet())
        val rails = buildList {
            continueRailItems.takeIf(List<ContinueWatchingUi>::isNotEmpty)?.let { items ->
                add(
                    HomeRail(
                        id = "real:rail:continue-watching",
                        title = labels.continueWatching,
                        kind = HomeRailKind.CONTINUE_WATCHING,
                        cardFormat = HomeCardFormat.LANDSCAPE,
                        items =
                            items.map { entry ->
                                entry.channel
                                    .toHomeItem(HomeCardFormat.LANDSCAPE, labels.continueBadge)
                                    .copy(progress = entry.progress)
                            },
                        isDemonstration = false,
                    ),
                )
            }
            // Straight after Continue assistindo, and built from the reminders themselves rather
            // than from catalogue rows: the whole point of an upcoming title is that it is not in
            // the catalogue yet, so matching these against `distinct` would drop exactly the
            // reminders worth showing.
            reminders.takeIf(List<Reminder>::isNotEmpty)?.let { marked ->
                add(
                    HomeRail(
                        id = "real:rail:reminders",
                        title = labels.reminders,
                        kind = HomeRailKind.REMINDERS,
                        cardFormat = HomeCardFormat.POSTER,
                        items = marked.map { reminder -> reminder.toHomeItem(labels.reminderBadge) },
                        isDemonstration = false,
                    ),
                )
            }
            releaseRail(currentYear, currentReleases, labels)?.let(::add)
            releaseRail(currentYear - 1, previousReleases, labels)?.let(::add)
            val newlyAdded = remaining.filter { it.categoryName == "Adicionado recentemente" }
            val newlyAddedClassics = newlyAdded.filter { (it.year ?: currentYear) <= currentYear - 15 }.take(12)
            newlyAddedClassics.takeIf(List<ChannelUi>::isNotEmpty)?.let { items ->
                add(
                    HomeRail(
                        id = "real:rail:new-classics",
                        title = labels.newClassics,
                        kind = HomeRailKind.EDITORIAL,
                        cardFormat = HomeCardFormat.POSTER,
                        items = items.map { it.toHomeItem(HomeCardFormat.POSTER, labels.classicBadge) },
                        isDemonstration = false,
                    ),
                )
                remaining.removeAll(items.toSet())
            }
            remaining
                .filter { it.categoryName == "Adicionado recentemente" }
                .take(12)
                .takeIf(List<ChannelUi>::isNotEmpty)
                ?.let { items ->
                    add(
                        HomeRail(
                            id = "real:rail:recently-added",
                            title = labels.recentlyAdded,
                            kind = HomeRailKind.EDITORIAL,
                            cardFormat = HomeCardFormat.POSTER,
                            items = items.map { it.toHomeItem(HomeCardFormat.POSTER, labels.newBadge) },
                            isDemonstration = false,
                        ),
                    )
                    remaining.removeAll(items.toSet())
                }
            remaining
                .filter { (it.rating ?: 0.0) > 0.0 }
                .sortedByDescending { it.rating }
                .take(12)
                .takeIf(List<ChannelUi>::isNotEmpty)
                ?.let { items ->
                    add(
                        HomeRail(
                            id = "real:rail:top-rated",
                            title = labels.topRated,
                            kind = HomeRailKind.EDITORIAL,
                            cardFormat = HomeCardFormat.POSTER,
                            items = items.map { it.toHomeItem(HomeCardFormat.POSTER, labels.topBadge) },
                            isDemonstration = false,
                        ),
                    )
                    remaining.removeAll(items.toSet())
                }
            val movies = remaining.filter { it.contentType == CatalogContentType.MOVIE }
            val series = remaining.filter { it.contentType == CatalogContentType.SERIES }
            movies.take(12).takeIf(List<ChannelUi>::isNotEmpty)?.let { items ->
                add(
                    HomeRail(
                        id = "real:rail:movies",
                        title = labels.movies,
                        kind = HomeRailKind.EDITORIAL,
                        cardFormat = HomeCardFormat.POSTER,
                        items = items.map { it.toHomeItem(HomeCardFormat.POSTER, labels.movieBadge) },
                        isDemonstration = false,
                    ),
                )
            }
            series.take(12).takeIf(List<ChannelUi>::isNotEmpty)?.let { items ->
                add(
                    HomeRail(
                        id = "real:rail:series",
                        title = labels.series,
                        kind = HomeRailKind.EDITORIAL,
                        cardFormat = HomeCardFormat.POSTER,
                        items = items.map { it.toHomeItem(HomeCardFormat.POSTER, labels.seriesBadge) },
                        isDemonstration = false,
                    ),
                )
            }
        }
        val railsWithStreaming =
            rails +
                streamingShelves
                    .filter { shelf -> shelf.titles.isNotEmpty() }
                    // Rail ids must be unique inside a section for the same reason item ids must be
                    // unique inside a rail: HomeSection rejects a repeat rather than tolerating it.
                    .distinctBy(ProviderShelfUi::providerId)
                    .map { shelf ->
                        HomeRail(
                            id = "real:rail:streaming:" + shelf.providerId,
                            // The service name as text. Its logo is that company's mark and is
                            // never fetched; the posters are the films' own artwork, which TMDb
                            // serves for exactly this purpose.
                            title = shelf.providerName,
                            kind = HomeRailKind.STREAMING_SERVICE,
                            cardFormat = HomeCardFormat.POSTER,
                            items =
                                // Deduplicated by external id: TMDb returns the same title twice
                                // on a service that lists it under two entries, and HomeRail
                                // rejects duplicate ids — which crashed the whole home screen
                                // rather than dropping one card.
                                shelf.titles.distinctBy(SubscriptionTitleUi::externalId).map { title ->
                                    HomeItem(
                                        id = "streaming:" + shelf.providerId + ":" + title.externalId,
                                        title = title.title,
                                        subtitle = shelf.providerName,
                                        synopsis =
                                            title.overview?.takeIf(String::isNotBlank)
                                                ?: "Abra para ver onde este título pode ser assistido.",
                                        metadata = title.year?.toString().orEmpty().ifBlank { shelf.providerName },
                                        badge = shelf.providerName,
                                        kind = HomeItemKind.CATALOG,
                                        cardFormat = HomeCardFormat.POSTER,
                                        palette = HomeArtworkPalette.AURORA,
                                        remoteArtworkUrl = title.posterUrl,
                                        // Left null on purpose: the rail heading above already
                                        // carries this service's mark, so badging every card with
                                        // the same logo would repeat it a dozen times across one
                                        // shelf and cover a corner of each poster to do it.
                                        categoryName = null,
                                        isDemonstration = false,
                                    )
                                },
                            isDemonstration = false,
                        )
                    }

        return HomeSection(
            id = "real:living-home:${sources.firstOrNull()?.id.orEmpty()}",
            hero = hero,
            rails = railsWithStreaming,
            heroRotation = heroRotation,
        )
    }

    /** Enough for a browsing session without the banner becoming a slideshow nobody can act on. */
    private const val HERO_ROTATION_SIZE = 10

    private fun releaseRail(
        year: Int,
        items: List<ChannelUi>,
        labels: HomeLabels,
    ): HomeRail? =
        items.takeIf(List<ChannelUi>::isNotEmpty)?.let { releases ->
            HomeRail(
                id = "real:rail:releases:$year",
                title = labels.releases(year),
                kind = HomeRailKind.EDITORIAL,
                cardFormat = HomeCardFormat.POSTER,
                items = releases.map { it.toHomeItem(HomeCardFormat.POSTER, year.toString()) },
                isDemonstration = false,
            )
        }

    /**
     * The prefix a reminder card carries in place of a catalogue row id.
     *
     * Read back by the view model to route a press to the reminders page, so it must stay equal to
     * `MainViewModel.REMINDER_ITEM_PREFIX` — the same pairing `streaming:` already relies on. A
     * marked title may have no row at all, which is the normal case for something not released yet,
     * so the id has to say what the card is rather than point at something that can be opened.
     */
    const val REMINDER_ITEM_PREFIX = "reminder:"

    private fun Reminder.toHomeItem(badge: String): HomeItem =
        HomeItem(
            id = REMINDER_ITEM_PREFIX + identity.key,
            title = title,
            // The release date, or the badge only when there is none.
            //
            // This repeated the badge, which was invisible while the caption sat over the artwork
            // and reads as a mistake now that both lines are on screen together: "LEMBRETE" twice,
            // one under the other.
            subtitle = releaseDate?.toString() ?: badge,
            synopsis = "",
            // The release date when the provider gave one, so a card says what it is waiting for.
            // Never a countdown computed here: this object is pure and has no clock, and a number
            // baked in at build time would be wrong by the next day.
            metadata = releaseDate?.toString() ?: badge,
            badge = badge,
            kind = HomeItemKind.CATALOG,
            cardFormat = HomeCardFormat.POSTER,
            palette = HomeArtworkPalette.AURORA,
            remoteArtworkUrl = artworkUrl,
            isDemonstration = false,
        )

    private fun ChannelUi.toHomeItem(
        format: HomeCardFormat,
        badge: String,
    ): HomeItem =
        HomeItem(
            id = id,
            title = name,
            subtitle = categoryName ?: badge,
            synopsis =
                when {
                    categoryName?.startsWith("Lançamento") == true ->
                        "Uma seleção do ano real de lançamento, pronta para abrir na sua ficha completa."
                    categoryName == "Adicionado recentemente" ->
                        "Chegou recentemente à sua fonte. Abra para ver sinopse, elenco e reprodução."
                    else -> "Abra os detalhes para ver sinopse, elenco e opções de reprodução."
                },
            metadata =
                listOfNotNull(
                    year?.toString(),
                    rating?.takeIf { it > 0.0 }?.let { "★ ${"%.1f".format(it)}" },
                ).joinToString("  •  ").ifBlank { badge },
            badge = badge,
            kind = HomeItemKind.CATALOG,
            cardFormat = format,
            palette = HomeArtworkPalette.AURORA,
            remoteArtworkUrl = logoUrl,
            // Carried so the card can badge its streaming service, the way the catalogue grid does.
            categoryName = categoryName,
            isDemonstration = false,
        )

}

/**
 * A plot cut down to what fits under a banner title.
 *
 * Cut at a sentence end where there is one within reach, so the text reads as finished rather than
 * severed; an ellipsis otherwise, which is honest about there being more.
 */
private fun String.toBannerSynopsis(): String {
    val clean = trim().replace(Regex("""\s+"""), " ")
    if (clean.length <= BANNER_SYNOPSIS_LIMIT) return clean
    val cut = clean.take(BANNER_SYNOPSIS_LIMIT)
    val sentenceEnd = cut.lastIndexOfAny(charArrayOf('.', '!', '?'))
    return if (sentenceEnd >= BANNER_SYNOPSIS_LIMIT / 2) {
        cut.take(sentenceEnd + 1)
    } else {
        cut.substringBeforeLast(' ', cut).trimEnd(',', ';', ' ') + "…"
    }
}

/** Two lines at the banner's text size, which is as much as the artwork can carry. */
private const val BANNER_SYNOPSIS_LIMIT = 180

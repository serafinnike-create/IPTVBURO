package com.lucasserafin94.iptvburo.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import com.lucasserafin94.iptvburo.R
import com.lucasserafin94.iptvburo.domain.model.CatalogContentType
import com.lucasserafin94.iptvburo.ui.ChannelUi
import java.util.Calendar

/** Builds a small, stable home document from locally indexed catalog rows. */
object RealHomeCatalog {
    @Composable
    fun section(
        sources: List<HomeSourceSummary>,
        catalogItems: List<ChannelUi>,
    ): HomeSection {
        val distinct = catalogItems.distinctBy(ChannelUi::id)
        val heroChannel = distinct.first()
        val hero = heroChannel.toHomeItem(HomeCardFormat.LANDSCAPE, "DESTAQUE")
        val remaining = distinct.filterNot { it.id == heroChannel.id }.toMutableList()
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val currentReleases = remaining.filter { it.year == currentYear }.take(12)
        remaining.removeAll(currentReleases.toSet())
        val previousReleases = remaining.filter { it.year == currentYear - 1 }.take(12)
        remaining.removeAll(previousReleases.toSet())
        val movies = remaining.filter { it.contentType == CatalogContentType.MOVIE }
        val series = remaining.filter { it.contentType == CatalogContentType.SERIES }
        val rails = buildList {
            sourceRail(sources)?.let(::add)
            releaseRail(currentYear, currentReleases)?.let(::add)
            releaseRail(currentYear - 1, previousReleases)?.let(::add)
            movies.take(12).takeIf(List<ChannelUi>::isNotEmpty)?.let { items ->
                add(
                    HomeRail(
                        id = "real:rail:movies",
                        title = "Filmes em destaque",
                        kind = HomeRailKind.EDITORIAL,
                        cardFormat = HomeCardFormat.POSTER,
                        items = items.map { it.toHomeItem(HomeCardFormat.POSTER, "FILME") },
                        isDemonstration = false,
                    ),
                )
            }
            series.take(12).takeIf(List<ChannelUi>::isNotEmpty)?.let { items ->
                add(
                    HomeRail(
                        id = "real:rail:series",
                        title = "Séries em destaque",
                        kind = HomeRailKind.EDITORIAL,
                        cardFormat = HomeCardFormat.POSTER,
                        items = items.map { it.toHomeItem(HomeCardFormat.POSTER, "SÉRIE") },
                        isDemonstration = false,
                    ),
                )
            }
        }
        return HomeSection(
            id = "real:living-home:${sources.firstOrNull()?.id.orEmpty()}",
            hero = hero,
            rails = rails,
        )
    }

    private fun releaseRail(year: Int, items: List<ChannelUi>): HomeRail? =
        items.takeIf(List<ChannelUi>::isNotEmpty)?.let { releases ->
            HomeRail(
                id = "real:rail:releases:$year",
                title = "Lançamentos $year",
                kind = HomeRailKind.EDITORIAL,
                cardFormat = HomeCardFormat.POSTER,
                items = releases.map { it.toHomeItem(HomeCardFormat.POSTER, year.toString()) },
                isDemonstration = false,
            )
        }

    private fun ChannelUi.toHomeItem(
        format: HomeCardFormat,
        badge: String,
    ): HomeItem =
        HomeItem(
            id = id,
            title = name,
            subtitle = categoryName ?: badge,
            synopsis = "Abra os detalhes para ver sinopse, elenco e opções de reprodução.",
            metadata =
                listOfNotNull(
                    year?.toString(),
                    rating?.let { "★ ${"%.1f".format(it)}" },
                ).joinToString("  •  ").ifBlank { badge },
            badge = badge,
            kind = HomeItemKind.CATALOG,
            cardFormat = format,
            palette = HomeArtworkPalette.AURORA,
            remoteArtworkUrl = logoUrl,
            isDemonstration = false,
        )

    @Composable
    private fun sourceRail(sources: List<HomeSourceSummary>): HomeRail? {
        val unique = sources.distinctBy(HomeSourceSummary::id)
        if (unique.isEmpty()) return null
        return HomeRail(
            id = DemoHomeCatalog.SOURCE_RAIL_ID,
            title = "Suas fontes",
            kind = HomeRailKind.SOURCES,
            cardFormat = HomeCardFormat.LANDSCAPE,
            isDemonstration = false,
            items = unique.mapIndexed { index, source ->
                HomeItem(
                    id = DemoHomeCatalog.sourceItemId(source.id),
                    title = source.name,
                    subtitle = pluralStringResource(
                        R.plurals.buro_home_source_channel_count,
                        source.channelCount,
                        source.channelCount,
                    ),
                    synopsis = "Conteúdo organizado localmente a partir da sua fonte autorizada.",
                    metadata = "FONTE CONECTADA",
                    badge = "BIBLIOTECA",
                    kind = HomeItemKind.SOURCE,
                    cardFormat = HomeCardFormat.LANDSCAPE,
                    palette = HomeArtworkPalette.entries[index % HomeArtworkPalette.entries.size],
                    isDemonstration = false,
                )
            },
        )
    }
}

package com.lucasserafin94.iptvburo.ui.home

import com.lucasserafin94.iptvburo.domain.model.CatalogContentType
import com.lucasserafin94.iptvburo.ui.ChannelUi
import java.util.Calendar

/** Builds a small, stable home document from locally indexed catalog rows. */
object RealHomeCatalog {
    fun section(
        sources: List<HomeSourceSummary>,
        catalogItems: List<ChannelUi>,
    ): HomeSection {
        val distinct = catalogItems.distinctBy(ChannelUi::id)
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val heroChannel =
            distinct.firstOrNull { it.year == currentYear }
                ?: distinct.firstOrNull { (it.rating ?: 0.0) >= 7.0 }
                ?: distinct.first()
        val hero = heroChannel.toHomeItem(HomeCardFormat.LANDSCAPE, "DESTAQUE")
        val remaining = distinct.filterNot { it.id == heroChannel.id }.toMutableList()
        val currentReleases = remaining.filter { it.year == currentYear }.take(12)
        remaining.removeAll(currentReleases.toSet())
        val previousReleases = remaining.filter { it.year == currentYear - 1 }.take(12)
        remaining.removeAll(previousReleases.toSet())
        val rails = buildList {
            releaseRail(currentYear, currentReleases)?.let(::add)
            releaseRail(currentYear - 1, previousReleases)?.let(::add)
            val newlyAdded = remaining.filter { it.categoryName == "Adicionado recentemente" }
            val newlyAddedClassics = newlyAdded.filter { (it.year ?: currentYear) <= currentYear - 15 }.take(12)
            newlyAddedClassics.takeIf(List<ChannelUi>::isNotEmpty)?.let { items ->
                add(
                    HomeRail(
                        id = "real:rail:new-classics",
                        title = "Clássicos que chegaram agora",
                        kind = HomeRailKind.EDITORIAL,
                        cardFormat = HomeCardFormat.POSTER,
                        items = items.map { it.toHomeItem(HomeCardFormat.POSTER, "CLÁSSICO") },
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
                            title = "Adicionados recentemente",
                            kind = HomeRailKind.EDITORIAL,
                            cardFormat = HomeCardFormat.POSTER,
                            items = items.map { it.toHomeItem(HomeCardFormat.POSTER, "NOVO NA FONTE") },
                            isDemonstration = false,
                        ),
                    )
                    remaining.removeAll(items.toSet())
                }
            remaining
                .filter { it.rating != null }
                .sortedByDescending { it.rating }
                .take(12)
                .takeIf(List<ChannelUi>::isNotEmpty)
                ?.let { items ->
                    add(
                        HomeRail(
                            id = "real:rail:top-rated",
                            title = "Melhores avaliações",
                            kind = HomeRailKind.EDITORIAL,
                            cardFormat = HomeCardFormat.POSTER,
                            items = items.map { it.toHomeItem(HomeCardFormat.POSTER, "★ DESTAQUE") },
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
                    rating?.let { "★ ${"%.1f".format(it)}" },
                ).joinToString("  •  ").ifBlank { badge },
            badge = badge,
            kind = HomeItemKind.CATALOG,
            cardFormat = format,
            palette = HomeArtworkPalette.AURORA,
            remoteArtworkUrl = logoUrl,
            isDemonstration = false,
        )

}

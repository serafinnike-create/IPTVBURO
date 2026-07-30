package com.lucasserafin94.iptvburo.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.lucasserafin94.iptvburo.R
import java.util.Calendar

/**
 * Local visual fixtures for the cinematic home prototype.
 *
 * The catalog is text and color metadata only: there are no images, URLs,
 * stream references, trailers, providers, or representations of real works.
 */
object DemoHomeCatalog {
    private const val SOURCE_ITEM_PREFIX = "local:source:"
    const val SECTION_ID = "demo:living-home"
    const val HERO_ID = "demo:hero:quiet-orbit"
    const val CONTINUE_RAIL_ID = "demo:rail:continue"
    const val LIVE_RAIL_ID = "demo:rail:live"
    const val EDITORIAL_RAIL_ID = "demo:rail:editorial"
    const val SOURCE_RAIL_ID = "local:rail:sources"

    @Composable
    fun section(
        sources: List<HomeSourceSummary> = emptyList(),
    ): HomeSection {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val demoBadge = stringResource(R.string.buro_home_demo_badge)
        val demoSynopsis = stringResource(R.string.buro_home_demo_story_synopsis)
        val liveSynopsis = stringResource(R.string.buro_home_demo_live_synopsis)
        val editorialSynopsis = stringResource(R.string.buro_home_demo_editorial_synopsis)
        val demoMetadata = stringResource(
            R.string.buro_home_demo_metadata,
            currentYear,
        )
        val liveMetadata = stringResource(
            R.string.buro_home_demo_live_metadata,
            currentYear,
        )

        val hero = HomeItem(
            id = HERO_ID,
            title = stringResource(R.string.buro_home_hero_title),
            subtitle = stringResource(R.string.buro_home_hero_subtitle),
            synopsis = stringResource(R.string.buro_home_hero_synopsis),
            metadata = stringResource(
                R.string.buro_home_hero_metadata,
                currentYear,
            ),
            badge = demoBadge,
            kind = HomeItemKind.DEMO_STORY,
            cardFormat = HomeCardFormat.LANDSCAPE,
            palette = HomeArtworkPalette.AURORA,
            isDemonstration = true,
        )

        val demoRails = listOf(
            HomeRail(
                id = CONTINUE_RAIL_ID,
                title = stringResource(R.string.buro_home_continue_title),
                kind = HomeRailKind.CONTINUE_WATCHING,
                cardFormat = HomeCardFormat.LANDSCAPE,
                isDemonstration = true,
                items = listOf(
                    demoItem(
                        id = "demo:continue:amber-archive",
                        title = stringResource(R.string.buro_home_continue_amber_title),
                        subtitle = stringResource(R.string.buro_home_continue_amber_subtitle),
                        synopsis = demoSynopsis,
                        metadata = demoMetadata,
                        badge = demoBadge,
                        palette = HomeArtworkPalette.EMBER,
                        progress = 0.68f,
                    ),
                    demoItem(
                        id = "demo:continue:prism-city",
                        title = stringResource(R.string.buro_home_continue_prism_title),
                        subtitle = stringResource(R.string.buro_home_continue_prism_subtitle),
                        synopsis = demoSynopsis,
                        metadata = demoMetadata,
                        badge = demoBadge,
                        palette = HomeArtworkPalette.COBALT,
                        progress = 0.42f,
                    ),
                    demoItem(
                        id = "demo:continue:green-signal",
                        title = stringResource(R.string.buro_home_continue_signal_title),
                        subtitle = stringResource(R.string.buro_home_continue_signal_subtitle),
                        synopsis = demoSynopsis,
                        metadata = demoMetadata,
                        badge = demoBadge,
                        palette = HomeArtworkPalette.FOREST,
                        progress = 0.81f,
                    ),
                    demoItem(
                        id = "demo:continue:violet-map",
                        title = stringResource(R.string.buro_home_continue_map_title),
                        subtitle = stringResource(R.string.buro_home_continue_map_subtitle),
                        synopsis = demoSynopsis,
                        metadata = demoMetadata,
                        badge = demoBadge,
                        palette = HomeArtworkPalette.PLUM,
                        progress = 0.23f,
                    ),
                ),
            ),
            HomeRail(
                id = LIVE_RAIL_ID,
                title = stringResource(R.string.buro_home_live_title),
                kind = HomeRailKind.LIVE_NOW,
                cardFormat = HomeCardFormat.LANDSCAPE,
                isDemonstration = true,
                items = listOf(
                    demoItem(
                        id = "demo:live:north-studio",
                        title = stringResource(R.string.buro_home_live_north_title),
                        subtitle = stringResource(R.string.buro_home_live_north_subtitle),
                        synopsis = liveSynopsis,
                        metadata = liveMetadata,
                        badge = stringResource(R.string.buro_home_demo_live_badge),
                        palette = HomeArtworkPalette.COBALT,
                        progress = 0.31f,
                        kind = HomeItemKind.DEMO_LIVE_STORY,
                    ),
                    demoItem(
                        id = "demo:live:solar-room",
                        title = stringResource(R.string.buro_home_live_solar_title),
                        subtitle = stringResource(R.string.buro_home_live_solar_subtitle),
                        synopsis = liveSynopsis,
                        metadata = liveMetadata,
                        badge = stringResource(R.string.buro_home_demo_live_badge),
                        palette = HomeArtworkPalette.SOLAR,
                        progress = 0.57f,
                        kind = HomeItemKind.DEMO_LIVE_STORY,
                    ),
                    demoItem(
                        id = "demo:live:field-notes",
                        title = stringResource(R.string.buro_home_live_field_title),
                        subtitle = stringResource(R.string.buro_home_live_field_subtitle),
                        synopsis = liveSynopsis,
                        metadata = liveMetadata,
                        badge = stringResource(R.string.buro_home_demo_live_badge),
                        palette = HomeArtworkPalette.FOREST,
                        progress = 0.74f,
                        kind = HomeItemKind.DEMO_LIVE_STORY,
                    ),
                    demoItem(
                        id = "demo:live:violet-stage",
                        title = stringResource(R.string.buro_home_live_stage_title),
                        subtitle = stringResource(R.string.buro_home_live_stage_subtitle),
                        synopsis = liveSynopsis,
                        metadata = liveMetadata,
                        badge = stringResource(R.string.buro_home_demo_live_badge),
                        palette = HomeArtworkPalette.PLUM,
                        progress = 0.46f,
                        kind = HomeItemKind.DEMO_LIVE_STORY,
                    ),
                ),
            ),
            HomeRail(
                id = EDITORIAL_RAIL_ID,
                title = stringResource(
                    R.string.buro_home_editorial_title,
                    currentYear,
                ),
                kind = HomeRailKind.EDITORIAL,
                cardFormat = HomeCardFormat.POSTER,
                isDemonstration = true,
                items = listOf(
                    demoItem(
                        id = "demo:editorial:blue-frequency",
                        title = stringResource(R.string.buro_home_editorial_frequency_title),
                        subtitle = stringResource(R.string.buro_home_editorial_frequency_subtitle),
                        synopsis = editorialSynopsis,
                        metadata = demoMetadata,
                        badge = demoBadge,
                        palette = HomeArtworkPalette.COBALT,
                        cardFormat = HomeCardFormat.POSTER,
                    ),
                    demoItem(
                        id = "demo:editorial:paper-sun",
                        title = stringResource(R.string.buro_home_editorial_sun_title),
                        subtitle = stringResource(R.string.buro_home_editorial_sun_subtitle),
                        synopsis = editorialSynopsis,
                        metadata = demoMetadata,
                        badge = demoBadge,
                        palette = HomeArtworkPalette.SOLAR,
                        cardFormat = HomeCardFormat.POSTER,
                    ),
                    demoItem(
                        id = "demo:editorial:forest-code",
                        title = stringResource(R.string.buro_home_editorial_forest_title),
                        subtitle = stringResource(R.string.buro_home_editorial_forest_subtitle),
                        synopsis = editorialSynopsis,
                        metadata = demoMetadata,
                        badge = demoBadge,
                        palette = HomeArtworkPalette.FOREST,
                        cardFormat = HomeCardFormat.POSTER,
                    ),
                    demoItem(
                        id = "demo:editorial:ember-line",
                        title = stringResource(R.string.buro_home_editorial_ember_title),
                        subtitle = stringResource(R.string.buro_home_editorial_ember_subtitle),
                        synopsis = editorialSynopsis,
                        metadata = demoMetadata,
                        badge = demoBadge,
                        palette = HomeArtworkPalette.EMBER,
                        cardFormat = HomeCardFormat.POSTER,
                    ),
                    demoItem(
                        id = "demo:editorial:soft-axis",
                        title = stringResource(R.string.buro_home_editorial_axis_title),
                        subtitle = stringResource(R.string.buro_home_editorial_axis_subtitle),
                        synopsis = editorialSynopsis,
                        metadata = demoMetadata,
                        badge = demoBadge,
                        palette = HomeArtworkPalette.AURORA,
                        cardFormat = HomeCardFormat.POSTER,
                    ),
                    demoItem(
                        id = "demo:editorial:plum-window",
                        title = stringResource(R.string.buro_home_editorial_window_title),
                        subtitle = stringResource(R.string.buro_home_editorial_window_subtitle),
                        synopsis = editorialSynopsis,
                        metadata = demoMetadata,
                        badge = demoBadge,
                        palette = HomeArtworkPalette.PLUM,
                        cardFormat = HomeCardFormat.POSTER,
                    ),
                ),
            ),
        )

        val sourceRail = sourceRail(sources)
        return HomeSection(
            id = SECTION_ID,
            hero = hero,
            rails = listOfNotNull(sourceRail) + demoRails,
        )
    }

    private fun demoItem(
        id: String,
        title: String,
        subtitle: String,
        synopsis: String,
        metadata: String,
        badge: String,
        palette: HomeArtworkPalette,
        progress: Float? = null,
        kind: HomeItemKind = HomeItemKind.DEMO_STORY,
        cardFormat: HomeCardFormat = HomeCardFormat.LANDSCAPE,
    ) = HomeItem(
        id = id,
        title = title,
        subtitle = subtitle,
        synopsis = synopsis,
        metadata = metadata,
        badge = badge,
        kind = kind,
        cardFormat = cardFormat,
        palette = palette,
        progress = progress,
        isDemonstration = true,
    )

    @Composable
    private fun sourceRail(sources: List<HomeSourceSummary>): HomeRail? {
        val uniqueSources = sources.distinctBy(HomeSourceSummary::id)
        if (uniqueSources.isEmpty()) return null

        val sourceBadge = stringResource(R.string.buro_home_source_badge)
        val sourceSynopsis = stringResource(R.string.buro_home_source_synopsis)
        val palettes = HomeArtworkPalette.entries

        return HomeRail(
            id = SOURCE_RAIL_ID,
            title = stringResource(R.string.buro_home_sources_title),
            kind = HomeRailKind.SOURCES,
            cardFormat = HomeCardFormat.LANDSCAPE,
            isDemonstration = false,
            items = uniqueSources.mapIndexed { index, source ->
                HomeItem(
                    id = sourceItemId(source.id),
                    title = source.name,
                    subtitle = pluralStringResource(
                        R.plurals.buro_home_source_channel_count,
                        source.channelCount,
                        source.channelCount,
                    ),
                    synopsis = sourceSynopsis,
                    metadata = stringResource(R.string.buro_home_source_metadata),
                    badge = sourceBadge,
                    kind = HomeItemKind.SOURCE,
                    cardFormat = HomeCardFormat.LANDSCAPE,
                    palette = palettes[index % palettes.size],
                    isDemonstration = false,
                )
            },
        )
    }

    fun sourceItemId(sourceId: String): String = "$SOURCE_ITEM_PREFIX$sourceId"

    fun sourceIdFromItemId(itemId: String): String? =
        itemId
            .takeIf { it.startsWith(SOURCE_ITEM_PREFIX) }
            ?.removePrefix(SOURCE_ITEM_PREFIX)
            ?.takeIf(String::isNotBlank)
}

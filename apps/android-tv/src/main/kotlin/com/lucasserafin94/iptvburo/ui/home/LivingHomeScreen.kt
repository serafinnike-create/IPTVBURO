package com.lucasserafin94.iptvburo.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.lucasserafin94.iptvburo.R
import com.lucasserafin94.iptvburo.domain.model.Reminder
import com.lucasserafin94.iptvburo.ui.designsystem.rememberProviderIdentity
import com.lucasserafin94.iptvburo.ui.designsystem.ProviderMark
import com.lucasserafin94.iptvburo.ui.ChannelUi
import com.lucasserafin94.iptvburo.ui.ContinueWatchingUi
import com.lucasserafin94.iptvburo.ui.ProviderShelfUi
import com.lucasserafin94.iptvburo.ui.adaptive.BuroWindowClass
import com.lucasserafin94.iptvburo.ui.adaptive.resolveBuroWindowClass
import com.lucasserafin94.iptvburo.ui.components.FocusSurface
import com.lucasserafin94.iptvburo.ui.theme.BuroAccent
import com.lucasserafin94.iptvburo.ui.theme.BuroCanvas
import com.lucasserafin94.iptvburo.ui.theme.BuroGold
import com.lucasserafin94.iptvburo.ui.theme.BuroSurface
import com.lucasserafin94.iptvburo.ui.theme.BuroTextPrimary
import com.lucasserafin94.iptvburo.ui.theme.BuroTextSecondary

@Composable
fun LivingHomeScreen(
    sources: List<HomeSourceSummary>,
    catalogItems: List<ChannelUi> = emptyList(),
    continueWatching: List<ContinueWatchingUi> = emptyList(),
    /** Marked titles, drawn as a rail directly under Continue assistindo. */
    reminders: List<Reminder> = emptyList(),
    /** Service shelves, drawn after the user's own content. Empty until they have loaded. */
    streamingShelves: List<ProviderShelfUi> = emptyList(),
    /** Real synopses for banner titles, keyed by channel id. */
    synopses: Map<String, String> = emptyMap(),
    /**
     * The trailer for a banner title, or null to show the artwork.
     *
     * Asked of the caller rather than decided here: whether a trailer may play depends on failures
     * remembered across the session and on what else is playing, neither of which a screen knows.
     */
    heroTrailerFor: (String) -> String? = { null },
    /** Whether the banner trailer carries sound, and the switch that changes it. */
    bannerTrailerSound: Boolean = false,
    onToggleBannerTrailerSound: (() -> Unit)? = null,
    /** Looks up the trailer for a title the banner has reached. */
    /** Asks for a title's trailer. The year and kind travel with it — TMDb keeps them apart. */
    onLoadHeroTrailer: (id: String, title: String, year: Int?, isSeries: Boolean) -> Unit =
        { _, _, _, _ -> },
    onItemFocused: (String) -> Unit,
    onOpenItem: (String) -> Unit,
    onImportSource: () -> Unit,
    onOpenSources: () -> Unit,
    onOpenSource: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    uiState: LivingHomeUiState = LivingHomeUiState.Ready,
    initialFocusedItemId: String? = null,
    interceptBack: Boolean = true,
) {
    BackHandler(
        enabled = interceptBack,
        onBack = onBack,
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(BuroCanvas, BuroSurface, BuroCanvas),
                ),
            ),
    ) {
        val releasesLabel = stringResource(R.string.home_rail_releases)
        val metrics = HomeLayoutMetrics.resolve(maxWidth, maxHeight)
        // Resolved here, where there are resources, and handed to the catalogue — which is a pure
        // object and has none. Without this the rails stayed Portuguese in every language.
        //
        // Every string is read first and the object built inside `remember`, because a
        // `stringResource` call is itself composable and cannot run inside one. That indirection is
        // what makes `labels` stable: `HomeLabels` is a data class but carries a lambda, so a fresh
        // instance never compares equal to the last, and an unstable `labels` would defeat the memo
        // below and rebuild the whole home screen on every pass.
        val continueWatchingLabel = stringResource(R.string.home_rail_continue)
        val continueBadgeLabel = stringResource(R.string.home_badge_continue)
        val remindersLabel = stringResource(R.string.home_rail_reminders)
        val reminderBadgeLabel = stringResource(R.string.home_badge_reminder)
        val newClassicsLabel = stringResource(R.string.home_rail_new_classics)
        val classicBadgeLabel = stringResource(R.string.home_badge_classic)
        val recentlyAddedLabel = stringResource(R.string.home_rail_recently_added)
        val newBadgeLabel = stringResource(R.string.home_badge_new)
        val topRatedLabel = stringResource(R.string.home_rail_top_rated)
        val topBadgeLabel = stringResource(R.string.home_badge_top)
        val moviesLabel = stringResource(R.string.home_rail_movies)
        val movieBadgeLabel = stringResource(R.string.home_badge_movie)
        val seriesLabel = stringResource(R.string.home_rail_series)
        val seriesBadgeLabel = stringResource(R.string.home_badge_series)
        val heroBadgeLabel = stringResource(R.string.home_badge_hero)
        val labels =
            remember(continueWatchingLabel, releasesLabel) {
                HomeLabels(
                    continueWatching = continueWatchingLabel,
                    continueBadge = continueBadgeLabel,
                    reminders = remindersLabel,
                    reminderBadge = reminderBadgeLabel,
                    newClassics = newClassicsLabel,
                    classicBadge = classicBadgeLabel,
                    recentlyAdded = recentlyAddedLabel,
                    newBadge = newBadgeLabel,
                    topRated = topRatedLabel,
                    topBadge = topBadgeLabel,
                    movies = moviesLabel,
                    movieBadge = movieBadgeLabel,
                    series = seriesLabel,
                    seriesBadge = seriesBadgeLabel,
                    heroBadge = heroBadgeLabel,
                    releases = { year -> releasesLabel.format(year) },
                )
            }
        when (uiState) {
            LivingHomeUiState.Ready -> {
                // Built once per change of input, not once per recomposition.
                //
                // `section` assembles the whole home screen — every rail, the hero rotation, the
                // seasonal picks, a sort over the catalogue — and it was being rebuilt on every
                // recomposition, which on a screen whose whole job is to scroll means rebuilding
                // it continuously while the user drags.
                //
                // Keyed on everything it reads, so a new reminder, a loaded shelf or a change of
                // language still refreshes it.
                val hasOwnContent = catalogItems.isNotEmpty() || continueWatching.isNotEmpty()
                val section =
                    if (!hasOwnContent) {
                        // The demonstration home is itself composable — it reads drawables — so it
                        // cannot be memoised here. It is also tiny and only shown before a source
                        // has been added, so there is nothing to gain.
                        DemoHomeCatalog.section(sources)
                    } else {
                        remember(
                            sources,
                            catalogItems,
                            continueWatching,
                            reminders,
                            streamingShelves,
                            synopses,
                            labels,
                        ) {
                            RealHomeCatalog.section(
                                sources = sources,
                                catalogItems = catalogItems,
                                continueWatching = continueWatching,
                                reminders = reminders,
                                streamingShelves = streamingShelves,
                                synopses = synopses,
                                labels = labels,
                            )
                        }
                    }
                val resolvedInitialFocusedItemId =
                    section.resolveInitialFocusId(initialFocusedItemId)
                ReadyHome(
                    heroTrailerFor = heroTrailerFor,
                    bannerTrailerSound = bannerTrailerSound,
                    onToggleBannerTrailerSound = onToggleBannerTrailerSound,
                    onLoadHeroTrailer = onLoadHeroTrailer,
                    section = section,
                    sourceCount = sources.distinctBy(HomeSourceSummary::id).size,
                    metrics = metrics,
                    initialFocusedItemId = resolvedInitialFocusedItemId,
                    onItemFocused = onItemFocused,
                    onOpenItem = onOpenItem,
                    onOpenSources = onOpenSources,
                    onOpenSource = onOpenSource,
                    showDemonstrationNotice = catalogItems.isEmpty() && continueWatching.isEmpty(),
                )
            }

            LivingHomeUiState.Loading -> HomeLoadingState(metrics)
            LivingHomeUiState.Empty -> HomeEmptyState(
                metrics = metrics,
                onImportSource = onImportSource,
                onOpenSources = onOpenSources,
                onItemFocused = onItemFocused,
            )

            is LivingHomeUiState.Error -> HomeErrorState(
                metrics = metrics,
                message = uiState.message,
                onImportSource = onImportSource,
                onOpenSources = onOpenSources,
                onItemFocused = onItemFocused,
            )
        }
    }
}

@Composable
private fun ReadyHome(
    section: HomeSection,
    sourceCount: Int,
    metrics: HomeLayoutMetrics,
    initialFocusedItemId: String?,
    /** The trailer for the banner title, or null to show the artwork. */
    heroTrailerFor: (String) -> String?,
    bannerTrailerSound: Boolean,
    onToggleBannerTrailerSound: (() -> Unit)?,
    /** Looks up the trailer for a title the banner has reached. */
    onLoadHeroTrailer: (id: String, title: String, year: Int?, isSeries: Boolean) -> Unit,
    onItemFocused: (String) -> Unit,
    onOpenItem: (String) -> Unit,
    onOpenSources: () -> Unit,
    onOpenSource: (String) -> Unit,
    showDemonstrationNotice: Boolean,
) {
    val columnState = rememberLazyListState()
    val initialRailIndex = section.rails.indexOfFirst { rail ->
        rail.items.any { item -> item.id == initialFocusedItemId }
    }
    val heroIndex = if (showDemonstrationNotice) 1 else 0
    val firstRailIndex = heroIndex + 1

    LaunchedEffect(initialFocusedItemId, section.id, showDemonstrationNotice) {
        when {
            initialFocusedItemId == section.hero.id -> columnState.scrollToItem(heroIndex)
            initialRailIndex >= 0 -> columnState.scrollToItem(
                initialRailIndex + firstRailIndex,
            )
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = columnState,
        contentPadding = PaddingValues(
            start = metrics.horizontalPadding,
            top = metrics.topPadding,
            end = metrics.horizontalPadding,
            bottom = 64.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(metrics.sectionSpacing),
    ) {
        if (showDemonstrationNotice) {
            item(key = "home:demonstration-notice") {
                DemonstrationNotice()
            }
        }

        item(key = "home:hero") {
            // The banner cycles through the day's rotation rather than holding one image, as the
            // desktop's does. Ten seconds: long enough to read a title and press play, short enough
            // that somebody who lingers sees more than one thing.
            //
            // Paused while the hero has focus, so a D-pad user reading it is not carried off it
            // mid-sentence.
            var heroIndex by remember(section.id) { mutableIntStateOf(0) }
            var heroFocused by remember(section.id) { mutableStateOf(false) }
            val rotation = section.heroRotation.ifEmpty { listOf(section.hero) }
            LaunchedEffect(section.id, rotation.size, heroFocused) {
                if (rotation.size <= 1 || heroFocused) return@LaunchedEffect
                while (true) {
                    delay(HERO_ROTATION_MILLIS)
                    heroIndex = (heroIndex + 1) % rotation.size
                }
            }
            val heroItem = rotation.getOrNull(heroIndex) ?: section.hero
            // Looked up as the banner reaches each title rather than all twenty up front: most of
            // them are never seen, so that would be twenty requests for one viewing.
            LaunchedEffect(heroItem.id) {
                // The year and kind are not on HomeItem, so the banner asks without them and
                // the lookup falls back to a title-only search. The Descobrir card, which holds the
                // whole channel, passes both — see AppShellScreen.
                onLoadHeroTrailer(heroItem.id, heroItem.title, null, false)
            }
            BuroHero(
                item = heroItem,
                trailerId = heroTrailerFor(heroItem.id),
                trailerSoundOn = bannerTrailerSound,
                onToggleTrailerSound = onToggleBannerTrailerSound,
                sourceCount = sourceCount,
                onItemFocused = { id ->
                    heroFocused = true
                    onItemFocused(id)
                },
                onOpenItem = onOpenItem,
                onOpenSources = onOpenSources,
                // Touch phones must open at the beginning of the hero. Requesting focus
                // on its lower action button makes LazyColumn scroll the title offscreen.
                requestFocus = !metrics.compactPortrait && initialFocusedItemId == section.hero.id,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(metrics.heroHeight),
            )
        }

        items(
            items = section.rails,
            key = HomeRail::id,
        ) { rail ->
            HomeRailRow(
                rail = rail,
                metrics = metrics,
                initialFocusedItemId = initialFocusedItemId,
                onItemFocused = onItemFocused,
                onOpenItem = onOpenItem,
                onOpenSources = onOpenSources,
                onOpenSource = onOpenSource,
            )
        }
    }
}

@Composable
private fun HomeRailRow(
    rail: HomeRail,
    metrics: HomeLayoutMetrics,
    initialFocusedItemId: String?,
    onItemFocused: (String) -> Unit,
    onOpenItem: (String) -> Unit,
    onOpenSources: () -> Unit,
    onOpenSource: (String) -> Unit,
) {
    val rowState = rememberLazyListState()
    val initialItemIndex = rail.items.indexOfFirst { item ->
        item.id == initialFocusedItemId
    }

    LaunchedEffect(initialFocusedItemId, rail.id) {
        if (initialItemIndex >= 0) rowState.scrollToItem(initialItemIndex)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // A service's shelf is headed by its own mark, beside the name.
            //
            // The rail title *is* the service on these shelves — "Netflix", "Disney Plus" — so the
            // same identity that badges the cards below reads it here too, and the heading stops
            // being the one place on the screen that names a service in plain text.
            rememberProviderIdentity(rail.title.takeIf { rail.kind == HomeRailKind.STREAMING_SERVICE })
                ?.let { provider ->
                    ProviderMark(provider = provider, size = 26.dp)
                    Spacer(Modifier.width(10.dp))
                }
            Text(
                text = rail.title,
                color = BuroTextPrimary,
                fontSize = metrics.railTitleSize,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (rail.isDemonstration) {
                DemoRailLabel()
            }
        }
        Spacer(Modifier.height(10.dp))
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(
                start = 12.dp,
                top = 5.dp,
                end = metrics.horizontalPadding,
                bottom = 8.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(metrics.cardSpacing),
        ) {
            items(
                items = rail.items,
                key = HomeItem::id,
            ) { item ->
                val open: (String) -> Unit = { itemId ->
                    if (item.kind == HomeItemKind.SOURCE) {
                        DemoHomeCatalog.sourceIdFromItemId(itemId)
                            ?.let(onOpenSource)
                            ?: onOpenSources()
                    } else {
                        onOpenItem(itemId)
                    }
                }
                when (rail.cardFormat) {
                    HomeCardFormat.POSTER -> BuroPosterCard(
                        item = item,
                        onItemFocused = onItemFocused,
                        onOpenItem = open,
                        width = metrics.posterWidth,
                        requestFocus = item.id == initialFocusedItemId,
                    )

                    HomeCardFormat.LANDSCAPE -> BuroLandscapeCard(
                        item = item,
                        onItemFocused = onItemFocused,
                        onOpenItem = open,
                        width = metrics.landscapeWidth,
                        requestFocus = item.id == initialFocusedItemId,
                    )
                }
            }
        }
    }
}

@Composable
private fun DemonstrationNotice() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(BuroAccent),
        )
        Text(
            text = stringResource(R.string.buro_home_demo_notice),
            color = BuroTextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun DemoRailLabel() {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(BuroGold.copy(alpha = 0.2f))
            .border(
                width = 1.dp,
                color = BuroGold.copy(alpha = 0.5f),
                shape = CircleShape,
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = stringResource(R.string.buro_home_demo_rail_label),
            color = BuroAccent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
        )
    }
}

@Composable
private fun HomeLoadingState(metrics: HomeLayoutMetrics) {
    val description = stringResource(R.string.buro_home_loading)
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .semantics { contentDescription = description },
        contentPadding = PaddingValues(
            horizontal = metrics.horizontalPadding,
            vertical = metrics.topPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(metrics.sectionSpacing),
    ) {
        item(key = "loading:label") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.HourglassTop,
                    contentDescription = null,
                    tint = BuroAccent,
                )
                Text(
                    text = description,
                    color = BuroTextSecondary,
                    fontSize = 16.sp,
                )
            }
        }
        item(key = "loading:hero") {
            SkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(metrics.heroHeight),
            )
        }
        items(
            items = listOf("loading:rail:one", "loading:rail:two"),
            key = { id -> id },
        ) { railId ->
            Column {
                SkeletonBlock(
                    modifier = Modifier
                        .width(220.dp)
                        .height(24.dp),
                )
                Spacer(Modifier.height(12.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(metrics.cardSpacing),
                ) {
                    items(
                        count = 4,
                        key = { index -> "$railId:item:$index" },
                    ) {
                        SkeletonBlock(
                            modifier = Modifier
                                .width(metrics.landscapeWidth)
                                .height(metrics.landscapeWidth * 9f / 16f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SkeletonBlock(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        BuroSurface,
                        BuroGold.copy(alpha = 0.12f),
                        BuroSurface,
                    ),
                ),
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.05f),
                shape = RoundedCornerShape(18.dp),
            ),
    )
}

@Composable
private fun HomeEmptyState(
    metrics: HomeLayoutMetrics,
    onImportSource: () -> Unit,
    onOpenSources: () -> Unit,
    onItemFocused: (String) -> Unit,
) {
    HomeStatePanel(
        metrics = metrics,
        icon = Icons.Default.Inbox,
        title = stringResource(R.string.buro_home_empty_title),
        body = stringResource(R.string.buro_home_empty_body),
        primaryLabel = stringResource(R.string.buro_home_import_source),
        primaryIcon = Icons.Default.Add,
        onPrimary = onImportSource,
        secondaryLabel = stringResource(R.string.buro_home_view_sources),
        onSecondary = onOpenSources,
        onItemFocused = onItemFocused,
    )
}

@Composable
private fun HomeErrorState(
    metrics: HomeLayoutMetrics,
    message: String?,
    onImportSource: () -> Unit,
    onOpenSources: () -> Unit,
    onItemFocused: (String) -> Unit,
) {
    HomeStatePanel(
        metrics = metrics,
        icon = Icons.Default.ErrorOutline,
        title = stringResource(R.string.buro_home_error_title),
        body = message
            ?.takeIf(String::isNotBlank)
            ?: stringResource(R.string.buro_home_error_body),
        primaryLabel = stringResource(R.string.buro_home_view_sources),
        primaryIcon = Icons.Default.FolderOpen,
        onPrimary = onOpenSources,
        secondaryLabel = stringResource(R.string.buro_home_import_source),
        onSecondary = onImportSource,
        onItemFocused = onItemFocused,
    )
}

@Composable
private fun HomeStatePanel(
    metrics: HomeLayoutMetrics,
    icon: ImageVector,
    title: String,
    body: String,
    primaryLabel: String,
    primaryIcon: ImageVector,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit,
    onItemFocused: (String) -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(metrics.horizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(if (metrics.compact) 0.88f else 0.66f)
                .clip(RoundedCornerShape(28.dp))
                .background(BuroSurface.copy(alpha = 0.94f))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(28.dp),
                )
                .heightIn(max = maxHeight)
                .verticalScroll(rememberScrollState())
                .padding(if (metrics.compact) 32.dp else 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(BuroGold.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = BuroAccent,
                    modifier = Modifier.size(36.dp),
                )
            }
            Spacer(Modifier.height(22.dp))
            Text(
                text = title,
                color = BuroTextPrimary,
                fontSize = if (metrics.compact) 26.sp else 32.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = body,
                color = BuroTextSecondary,
                fontSize = 16.sp,
                lineHeight = 23.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(28.dp))
            if (metrics.compactPortrait) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StateAction(
                        label = primaryLabel,
                        icon = primaryIcon,
                        primary = true,
                        onClick = onPrimary,
                        requestFocus = true,
                        onFocused = {
                            onItemFocused(STATE_PRIMARY_ACTION_ID)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    StateAction(
                        label = secondaryLabel,
                        icon = Icons.Default.FolderOpen,
                        primary = false,
                        onClick = onSecondary,
                        onFocused = {
                            onItemFocused(STATE_SECONDARY_ACTION_ID)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StateAction(
                        label = primaryLabel,
                        icon = primaryIcon,
                        primary = true,
                        onClick = onPrimary,
                        requestFocus = true,
                        onFocused = {
                            onItemFocused(STATE_PRIMARY_ACTION_ID)
                        },
                    )
                    StateAction(
                        label = secondaryLabel,
                        icon = Icons.Default.FolderOpen,
                        primary = false,
                        onClick = onSecondary,
                        onFocused = {
                            onItemFocused(STATE_SECONDARY_ACTION_ID)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun StateAction(
    label: String,
    icon: ImageVector,
    primary: Boolean,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
    requestFocus: Boolean = false,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(requestFocus) {
        if (requestFocus) focusRequester.requestFocus()
    }

    FocusSurface(
        onClick = onClick,
        modifier = modifier
            .height(52.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                if (focusState.isFocused) onFocused()
            },
        backgroundColor = if (primary) BuroGold else BuroSurface,
        focusedBackgroundColor = if (primary) BuroGold else BuroSurface,
        selectedBackgroundColor = if (primary) BuroGold else BuroSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (primary) BuroCanvas else BuroGold,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(9.dp))
            Text(
                text = label,
                color = if (primary) BuroCanvas else BuroTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

private data class HomeLayoutMetrics(
    val compact: Boolean,
    val compactPortrait: Boolean,
    val horizontalPadding: Dp,
    val topPadding: Dp,
    val heroHeight: Dp,
    val landscapeWidth: Dp,
    val posterWidth: Dp,
    val sectionSpacing: Dp,
    val cardSpacing: Dp,
    val railTitleSize: androidx.compose.ui.unit.TextUnit,
) {
    companion object {
        fun resolve(maxWidth: Dp, maxHeight: Dp): HomeLayoutMetrics {
            return when (resolveBuroWindowClass(maxWidth.value, maxHeight.value)) {
                BuroWindowClass.CompactPortrait ->
                    HomeLayoutMetrics(
                        compact = true,
                        compactPortrait = true,
                        horizontalPadding = 16.dp,
                        topPadding = 16.dp,
                        heroHeight = 430.dp,
                        landscapeWidth =
                            minOf(304.dp, maxOf(220.dp, maxWidth - 48.dp)),
                        posterWidth =
                            minOf(152.dp, maxOf(128.dp, (maxWidth - 48.dp) * 0.48f)),
                        sectionSpacing = 20.dp,
                        cardSpacing = 12.dp,
                        railTitleSize = 20.sp,
                    )

                BuroWindowClass.CompactLandscape ->
                    HomeLayoutMetrics(
                        compact = true,
                        compactPortrait = false,
                        horizontalPadding = 20.dp,
                        topPadding = 16.dp,
                        heroHeight = 300.dp,
                        landscapeWidth = 236.dp,
                        posterWidth = 138.dp,
                        sectionSpacing = 20.dp,
                        cardSpacing = 12.dp,
                        railTitleSize = 20.sp,
                    )

                BuroWindowClass.Expanded ->
                    HomeLayoutMetrics(
                        compact = false,
                        compactPortrait = false,
                        horizontalPadding = 44.dp,
                        topPadding = 32.dp,
                        heroHeight = 430.dp,
                        landscapeWidth = 304.dp,
                        posterWidth = 176.dp,
                        sectionSpacing = 28.dp,
                        cardSpacing = 18.dp,
                        railTitleSize = 27.sp,
                    )
            }
        }
    }
}

private const val STATE_PRIMARY_ACTION_ID = "home:state:primary"
private const val STATE_SECONDARY_ACTION_ID = "home:state:secondary"

/** How long each banner title holds the screen. */
private const val HERO_ROTATION_MILLIS = 10_000L

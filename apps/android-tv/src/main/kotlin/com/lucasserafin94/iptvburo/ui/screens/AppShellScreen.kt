package com.lucasserafin94.iptvburo.ui.screens


import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.lucasserafin94.iptvburo.BuildConfig
import com.lucasserafin94.iptvburo.R
import com.lucasserafin94.iptvburo.ui.AppContent
import com.lucasserafin94.iptvburo.ui.AppSection
import com.lucasserafin94.iptvburo.ui.AppUiState
import com.lucasserafin94.iptvburo.ui.CategoryUi
import com.lucasserafin94.iptvburo.ui.ChannelUi
import com.lucasserafin94.iptvburo.ui.EpisodeUi
import com.lucasserafin94.iptvburo.ui.ProfileUi
import com.lucasserafin94.iptvburo.ui.localization.AppLocaleController
import com.lucasserafin94.iptvburo.ui.SourceImportMethod
import com.lucasserafin94.iptvburo.ui.SourceUi
import com.lucasserafin94.iptvburo.ui.XtreamImportStageUi
import com.lucasserafin94.iptvburo.ui.components.FocusSurface
import com.lucasserafin94.iptvburo.ui.designsystem.BuroButton
import com.lucasserafin94.iptvburo.ui.designsystem.BuroButtonStyle
import com.lucasserafin94.iptvburo.ui.designsystem.BuroEmptyState
import com.lucasserafin94.iptvburo.ui.designsystem.BuroErrorState
import com.lucasserafin94.iptvburo.ui.designsystem.BuroScreen
import com.lucasserafin94.iptvburo.ui.designsystem.BuroSpacing
import com.lucasserafin94.iptvburo.ui.designsystem.BuroTheme
import com.lucasserafin94.iptvburo.ui.home.DemoHomeCatalog
import com.lucasserafin94.iptvburo.ui.home.DemoStoryScreen
import com.lucasserafin94.iptvburo.ui.home.HomeSourceSummary
import com.lucasserafin94.iptvburo.ui.home.LivingHomeScreen
import com.lucasserafin94.iptvburo.ui.navigation.BuroRibbon
import com.lucasserafin94.iptvburo.ui.security.SecureActivityWindowEffect
import com.lucasserafin94.iptvburo.domain.model.CatalogContentType
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.lucasserafin94.iptvburo.ui.theme.BuroAccent
import com.lucasserafin94.iptvburo.ui.theme.BuroCanvas
import com.lucasserafin94.iptvburo.ui.theme.BuroDanger
import com.lucasserafin94.iptvburo.ui.theme.BuroGold
import com.lucasserafin94.iptvburo.ui.theme.BuroSurface
import com.lucasserafin94.iptvburo.ui.theme.BuroTextPrimary
import com.lucasserafin94.iptvburo.ui.theme.BuroTextSecondary

@Composable
fun AppShellScreen(
    state: AppUiState,
    onSelectSection: (AppSection) -> Unit,
    onImportSource: () -> Unit,
    onImportXtreamSource: (String, String, String, String) -> Unit,
    onCancelXtreamImport: () -> Unit,
    onOpenSource: (SourceUi) -> Unit,
    onOpenCategory: (CategoryUi) -> Unit,
    onOpenChannel: (ChannelUi) -> Unit,
    onPlayMovie: () -> Unit,
    onToggleMovieFavorite: () -> Unit,
    onOpenPerson: (String) -> Unit,
    onOpenEpisode: (EpisodeUi) -> Unit,
    onSelectProfile: (String) -> Unit,
    onCreateProfile: (String, Boolean) -> Unit,
    onRequestProfileSelection: () -> Unit,
    onSelectLanguage: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRetryCatalog: () -> Unit,
    onOpenHomeItem: (String) -> Unit,
    onRememberHomeFocus: (String?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Enabled only after the provider/backend returns an explicit offline entitlement.
    val supportsOfflineVault = false
    val ribbonFocusRequester = remember { FocusRequester() }
    var showXtreamModal by remember { mutableStateOf(false) }
    var xtreamSuccessVersionAtOpen by remember {
        mutableLongStateOf(state.importSuccessVersion)
    }
    var homeContentOwnsBack by remember(state.content) {
        mutableStateOf(state.content == AppContent.Home)
    }
    val selectedRibbonSection =
        if (state.content == AppContent.Settings) {
            AppSection.PROFILE
        } else {
            state.section.takeIf(AppSection::isRibbonDestination)
        }

    Box(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .background(
                Brush.linearGradient(
                    colors = listOf(BuroCanvas, BuroSurface, BuroCanvas),
                ),
            ),
    ) {
        when (val content = state.content) {
            is AppContent.Story -> DemoStoryScreen(
                itemId = content.itemId,
                onImportSource = onImportSource,
                onBack = onBack,
            )

            is AppContent.SeriesDetails -> SeriesDetailsScreen(
                fallbackTitle = content.fallbackTitle,
                details = state.seriesDetails,
                isLoading = state.isSeriesLoading,
                hasError = state.hasSeriesError,
                isResolvingPlayback = state.isResolvingPlayback,
                hasPlaybackError = state.hasPlaybackError,
                onOpenEpisode = onOpenEpisode,
                onDownloadEpisode = {},
                canDownloadOffline = supportsOfflineVault,
                onOpenPerson = onOpenPerson,
                onRetry = onRetryCatalog,
                onBack = onBack,
            )

            is AppContent.MovieDetails -> MovieDetailsScreen(
                fallbackTitle = content.fallbackTitle,
                fallbackArtworkUrl = content.fallbackArtworkUrl,
                details = state.movieDetails,
                isLoading = state.isMovieLoading,
                hasError = state.hasMovieError,
                isResolvingPlayback = state.isResolvingPlayback,
                hasPlaybackError = state.hasPlaybackError,
                onPlay = onPlayMovie,
                isFavorite = content.channelId in state.favoriteIds,
                onToggleFavorite = onToggleMovieFavorite,
                onDownload = {},
                canDownloadOffline = supportsOfflineVault,
                onOpenPerson = onOpenPerson,
                onRetry = onRetryCatalog,
                onBack = onBack,
            )

            is AppContent.Person -> PersonFilmographyScreen(
                personName = content.name,
                movies = state.personMovies,
                onOpenMovie = onOpenChannel,
                onBack = onBack,
            )

            else -> Column(modifier = Modifier.fillMaxSize()) {
                BuroRibbon(
                    selectedSection = selectedRibbonSection,
                    onSelect = onSelectSection,
                    selectedItemFocusRequester = ribbonFocusRequester,
                    onItemFocused = {
                        if (state.content == AppContent.Home) {
                            homeContentOwnsBack = false
                        }
                    },
                    activeProfileName = state.activeProfile?.name,
                    isKidsProfile = state.activeProfile?.isKids == true,
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    when (content) {
                        AppContent.Home -> LivingHomeScreen(
                            sources = state.sources.map(SourceUi::toHomeSummary),
                            catalogItems = state.homeItems,
                            initialFocusedItemId =
                                state.lastFocusedHomeItemId ?: DemoHomeCatalog.HERO_ID,
                            onItemFocused = { itemId ->
                                homeContentOwnsBack = true
                                onRememberHomeFocus(itemId)
                            },
                            onOpenItem = onOpenHomeItem,
                            onImportSource = onImportSource,
                            onOpenSources = {
                                onSelectSection(AppSection.SOURCES)
                            },
                            onOpenSource = { sourceId ->
                                state.sources
                                    .firstOrNull { source -> source.id == sourceId }
                                    ?.let(onOpenSource)
                                    ?: onSelectSection(AppSection.SOURCES)
                            },
                            onBack = {
                                homeContentOwnsBack = false
                                ribbonFocusRequester.requestFocus()
                            },
                            interceptBack = homeContentOwnsBack,
                        )

                        AppContent.Sources -> SourcesContent(
                            sources = state.sources,
                            isImporting = state.isImporting,
                            lastImportedChannelCount = state.lastImportedChannelCount,
                            hasImportError = state.hasImportError,
                            lastImportMethod = state.lastImportMethod,
                            xtreamImportStage = state.xtreamImportStage,
                            onImportSource = onImportSource,
                            onOpenXtream = {
                                xtreamSuccessVersionAtOpen = state.importSuccessVersion
                                showXtreamModal = true
                            },
                            onOpenSource = onOpenSource,
                        )

                        is AppContent.SectionPlaceholder -> SectionPlaceholderContent(
                            section = content.section,
                            onOpenSources = {
                                onSelectSection(AppSection.SOURCES)
                            },
                            onOpenSettings = {
                                onSelectSection(AppSection.SETTINGS)
                            },
                        )

                        is AppContent.Categories -> CategoriesContent(
                            title = content.sourceName,
                            categories = state.categories,
                            contentType = content.contentType,
                            isLoading = state.isCatalogLoading,
                            hasError = state.hasCatalogError,
                            onRetry = onRetryCatalog,
                            onBack = onBack,
                            onOpenCategory = onOpenCategory,
                        )

                        is AppContent.Channels -> ChannelsContent(
                            title =
                                if (content.categoryId == null) {
                                    stringResource(
                                        if (
                                            content.contentType == CatalogContentType.LIVE
                                        ) {
                                            R.string.categories_all
                                        } else {
                                            R.string.categories_all_items
                                        },
                                    )
                                } else {
                                    content.categoryName
                                },
                            subtitle = content.sourceName,
                            channels = state.channels,
                            isLoading = state.isCatalogLoading,
                            isLoadingMore = state.isLoadingMore,
                            hasMore = state.hasMoreChannels,
                            hasError = state.hasCatalogError,
                            isResolvingPlayback = state.isResolvingPlayback,
                            hasPlaybackError = state.hasPlaybackError,
                            onBack = onBack,
                            onOpenChannel = onOpenChannel,
                            onRetry = onRetryCatalog,
                            onLoadMore = onLoadMore,
                        )

                        AppContent.Favorites -> MyBuroContent(
                            favorites = state.favoriteItems,
                            isLoading = state.isCatalogLoading,
                            hasError = state.hasCatalogError,
                            onOpenChannel = onOpenChannel,
                            onRetry = onRetryCatalog,
                            onBack = onBack,
                        )

                        AppContent.Profiles -> ProfilePickerScreen(
                            profiles = state.profiles,
                            onSelect = onSelectProfile,
                            onCreate = onCreateProfile,
                        )

                        AppContent.Settings -> SettingsContent(
                            activeProfile = state.activeProfile,
                            deviceId = state.deviceId,
                            onChangeProfile = onRequestProfileSelection,
                            onSelectLanguage = onSelectLanguage,
                        )
                        is AppContent.Player,
                        is AppContent.Story,
                        is AppContent.SeriesDetails,
                        -> Unit
                    }
                }
            }
        }
    }

    if (showXtreamModal) {
        SecureActivityWindowEffect()
        XtreamSourceDialog(
            isImporting = state.isImporting,
            hasImportError =
                state.hasImportError &&
                    state.lastImportMethod == SourceImportMethod.XTREAM,
            importStage = state.xtreamImportStage,
            importSuccessVersion = state.importSuccessVersion,
            successVersionAtOpen = xtreamSuccessVersionAtOpen,
            onSubmit = onImportXtreamSource,
            onCancelImport = onCancelXtreamImport,
            onDismiss = { showXtreamModal = false },
        )
    }
}

private fun AppSection.isRibbonDestination(): Boolean =
    when (this) {
        AppSection.HOME,
        AppSection.LIVE,
        AppSection.MOVIES,
        AppSection.SERIES,
        AppSection.DISCOVER,
        AppSection.MY_BURO,
        AppSection.SEARCH,
        AppSection.PROFILE,
        -> true

        AppSection.SOURCES,
        AppSection.SETTINGS,
        -> false
    }

private fun SourceUi.toHomeSummary(): HomeSourceSummary =
    HomeSourceSummary(
        id = id,
        name = name,
        channelCount = channelCount,
    )

@Composable
private fun SectionPlaceholderContent(
    section: AppSection,
    onOpenSources: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val sectionLabel = stringResource(section.ribbonLabelResource())
    val opensSettings = section == AppSection.PROFILE
    BuroScreen(
        contentPadding = PaddingValues(
            horizontal = BuroSpacing.Xl,
            vertical = BuroSpacing.Sm,
        ),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val compact = maxHeight < 420.dp
            val colors = BuroTheme.colors
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(if (compact) 0.92f else 0.72f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(
                        R.string.buro_placeholder_title,
                        sectionLabel,
                    ),
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(BuroSpacing.Xs))
                Text(
                    text = stringResource(R.string.buro_placeholder_body),
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    maxLines = if (compact) 2 else 4,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(BuroSpacing.Md))
                BuroButton(
                    onClick =
                        if (opensSettings) {
                            onOpenSettings
                        } else {
                            onOpenSources
                        },
                    style = BuroButtonStyle.Secondary,
                ) {
                    Text(
                        text =
                            stringResource(
                                if (opensSettings) {
                                    R.string.buro_placeholder_settings_action
                                } else {
                                    R.string.buro_placeholder_sources_action
                                },
                            ),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

private fun AppSection.ribbonLabelResource(): Int =
    when (this) {
        AppSection.HOME -> R.string.buro_nav_home
        AppSection.LIVE -> R.string.buro_nav_live
        AppSection.MOVIES -> R.string.buro_nav_movies
        AppSection.SERIES -> R.string.buro_nav_series
        AppSection.DISCOVER -> R.string.buro_nav_discover
        AppSection.MY_BURO -> R.string.buro_nav_my_buro
        AppSection.SEARCH -> R.string.buro_nav_search
        AppSection.PROFILE -> R.string.buro_nav_profile
        AppSection.SOURCES -> R.string.nav_sources
        AppSection.SETTINGS -> R.string.nav_settings
    }

@Composable
private fun Sidebar(
    selected: AppSection,
    onSelect: (AppSection) -> Unit,
) {
    Column(
        modifier = Modifier
            .width(220.dp)
            .fillMaxHeight()
            .background(Color.Black.copy(alpha = 0.22f))
            .padding(horizontal = 18.dp, vertical = 28.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(listOf(BuroAccent, BuroGold))),
                contentAlignment = Alignment.Center,
            ) {
                Text("▶", color = BuroCanvas, fontSize = 20.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("IPTV", color = BuroAccent, fontSize = 11.sp, letterSpacing = 3.sp)
                Text(
                    "BURO",
                    color = BuroTextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                )
            }
        }

        Spacer(Modifier.height(42.dp))

        SidebarItem(
            label = stringResource(R.string.nav_home),
            icon = Icons.Default.Home,
            selected = selected == AppSection.HOME,
            onClick = { onSelect(AppSection.HOME) },
        )
        SidebarItem(
            label = stringResource(R.string.nav_live),
            icon = Icons.Default.LiveTv,
            selected = selected == AppSection.LIVE,
            onClick = { onSelect(AppSection.LIVE) },
        )
        SidebarItem(
            label = stringResource(R.string.nav_sources),
            icon = Icons.Default.Folder,
            selected = selected == AppSection.SOURCES,
            onClick = { onSelect(AppSection.SOURCES) },
        )

        Spacer(Modifier.weight(1f))

        SidebarItem(
            label = stringResource(R.string.nav_settings),
            icon = Icons.Default.Settings,
            selected = selected == AppSection.SETTINGS,
            onClick = { onSelect(AppSection.SETTINGS) },
        )
    }
}

@Composable
private fun SidebarItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FocusSurface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .height(54.dp),
        backgroundColor = if (selected) BuroGold.copy(alpha = 0.24f) else Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) BuroAccent else BuroTextSecondary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(14.dp))
            Text(
                text = label,
                color = if (selected) BuroTextPrimary else BuroTextSecondary,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun HomeContent(
    state: AppUiState,
    onAddSource: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 42.dp, vertical = 34.dp),
    ) {
        Text(
            text = stringResource(R.string.home_welcome),
            color = BuroTextPrimary,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.home_subtitle),
            color = BuroTextSecondary,
            fontSize = 17.sp,
        )
        Spacer(Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            MetricCard(
                value = state.sources.size.toString(),
                label = pluralStringResource(
                    R.plurals.home_sources_count,
                    state.sources.size,
                    state.sources.size,
                ),
                accent = BuroAccent,
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                value = state.sources.sumOf { it.channelCount }.toString(),
                label = pluralStringResource(
                    R.plurals.home_channels_count,
                    state.sources.sumOf { it.channelCount },
                    state.sources.sumOf { it.channelCount },
                ),
                accent = BuroGold,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(24.dp))

        FocusSurface(
            onClick = onAddSource,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            backgroundColor = BuroSurface,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(38.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(30.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(94.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(BuroAccent, BuroGold))),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = BuroCanvas,
                        modifier = Modifier.size(48.dp),
                    )
                }
                Column {
                    Text(
                        text = stringResource(R.string.home_action),
                        color = BuroTextPrimary,
                        fontSize = 27.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.sources_empty_body),
                        color = BuroTextSecondary,
                        fontSize = 17.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    value: String,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(100.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(BuroSurface)
            .padding(22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = value,
            color = accent,
            fontSize = 38.sp,
            fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.width(18.dp))
        Text(
            text = label,
            color = BuroTextSecondary,
            fontSize = 15.sp,
        )
    }
}

@Composable
private fun SourcesContent(
    sources: List<SourceUi>,
    isImporting: Boolean,
    lastImportedChannelCount: Int?,
    hasImportError: Boolean,
    lastImportMethod: SourceImportMethod?,
    xtreamImportStage: XtreamImportStageUi?,
    onImportSource: () -> Unit,
    onOpenXtream: () -> Unit,
    onOpenSource: (SourceUi) -> Unit,
) {
    val initialFocusRequester = remember { FocusRequester() }
    LaunchedEffect(sources.firstOrNull()?.id, isImporting) {
        initialFocusRequester.requestFocus()
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val phonePortrait = maxWidth < 600.dp && maxHeight >= maxWidth
        val horizontalPadding = if (phonePortrait) 16.dp else 42.dp
        val verticalPadding = if (phonePortrait) 18.dp else 34.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = horizontalPadding,
                    vertical = verticalPadding,
                ),
        ) {
            if (phonePortrait) {
                SourcesHeading(
                    lastImportedChannelCount = lastImportedChannelCount,
                    hasImportError = hasImportError,
                    lastImportMethod = lastImportMethod,
                    xtreamImportStage = xtreamImportStage,
                    isImporting = isImporting,
                    compact = true,
                )
                Spacer(Modifier.height(14.dp))
                SourceImportActions(
                    isImporting = isImporting,
                    lastImportMethod = lastImportMethod,
                    onImportSource = onImportSource,
                    onOpenXtream = onOpenXtream,
                    modifier = Modifier.fillMaxWidth(),
                    stack = true,
                    initialFocusRequester =
                        if (sources.isEmpty()) initialFocusRequester else null,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SourcesHeading(
                        lastImportedChannelCount = lastImportedChannelCount,
                        hasImportError = hasImportError,
                        lastImportMethod = lastImportMethod,
                        xtreamImportStage = xtreamImportStage,
                        isImporting = isImporting,
                        compact = false,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(18.dp))
                    SourceImportActions(
                        isImporting = isImporting,
                        lastImportMethod = lastImportMethod,
                        onImportSource = onImportSource,
                        onOpenXtream = onOpenXtream,
                        stack = false,
                        initialFocusRequester =
                            if (sources.isEmpty()) initialFocusRequester else null,
                    )
                }
            }

            Spacer(Modifier.height(if (phonePortrait) 18.dp else 28.dp))

            if (sources.isEmpty()) {
                EmptySources()
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(
                        items = sources,
                        key = { it.id },
                    ) { source ->
                        SourceCard(
                            source = source,
                            onOpenSource = onOpenSource,
                            modifier =
                                if (source.id == sources.first().id) {
                                    Modifier.focusRequester(initialFocusRequester)
                                } else {
                                    Modifier
                                },
                        )
                    }
                }
            }
        }

    }
}

@Composable
private fun SourcesHeading(
    lastImportedChannelCount: Int?,
    hasImportError: Boolean,
    lastImportMethod: SourceImportMethod?,
    xtreamImportStage: XtreamImportStageUi?,
    isImporting: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.sources_title),
            color = BuroTextPrimary,
            fontSize = if (compact) 27.sp else 32.sp,
            fontWeight = FontWeight.Bold,
        )
        if (isImporting) {
            Text(
                text =
                    if (
                        lastImportMethod == SourceImportMethod.XTREAM &&
                        xtreamImportStage != null
                    ) {
                        xtreamImportStage.localizedLabel()
                    } else {
                        stringResource(R.string.sources_importing)
                    },
                color = BuroAccent,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        } else if (lastImportedChannelCount != null || hasImportError) {
            Text(
                text =
                    if (hasImportError) {
                        stringResource(
                            if (lastImportMethod == SourceImportMethod.XTREAM) {
                                R.string.sources_xtream_error
                            } else {
                                R.string.sources_import_error
                            },
                        )
                    } else if (lastImportMethod == SourceImportMethod.XTREAM) {
                        pluralStringResource(
                            R.plurals.sources_xtream_import_success,
                            lastImportedChannelCount ?: 0,
                            lastImportedChannelCount ?: 0,
                        )
                    } else {
                        pluralStringResource(
                            R.plurals.sources_import_success,
                            lastImportedChannelCount ?: 0,
                            lastImportedChannelCount ?: 0,
                        )
                    },
                color = if (hasImportError) BuroDanger else BuroAccent,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun SourceImportActions(
    isImporting: Boolean,
    lastImportMethod: SourceImportMethod?,
    onImportSource: () -> Unit,
    onOpenXtream: () -> Unit,
    stack: Boolean,
    modifier: Modifier = Modifier,
    initialFocusRequester: FocusRequester? = null,
) {
    val fileModifier =
        if (initialFocusRequester == null) {
            Modifier
        } else {
            Modifier.focusRequester(initialFocusRequester)
        }
    val fileButton: @Composable () -> Unit = {
        BuroButton(
            onClick = onImportSource,
            enabled = !isImporting,
            style = BuroButtonStyle.Secondary,
            modifier =
                fileModifier.then(if (stack) Modifier.fillMaxWidth() else Modifier),
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text(
                text =
                    stringResource(
                        if (
                            isImporting &&
                            lastImportMethod == SourceImportMethod.M3U_FILE
                        ) {
                            R.string.sources_importing
                        } else {
                            R.string.sources_file_action
                        },
                    ),
            )
        }
    }
    val xtreamButton: @Composable () -> Unit = {
        BuroButton(
            onClick = onOpenXtream,
            enabled = !isImporting,
            modifier = if (stack) Modifier.fillMaxWidth() else Modifier,
        ) {
            Icon(Icons.Default.LiveTv, contentDescription = null)
            Text(text = stringResource(R.string.sources_xtream_action))
        }
    }

    if (stack) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            fileButton()
            xtreamButton()
        }
    } else {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            fileButton()
            xtreamButton()
        }
    }
}

@Composable
private fun EmptySources() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Folder,
            contentDescription = null,
            tint = BuroAccent,
            modifier = Modifier.size(58.dp),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.sources_empty),
            color = BuroTextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.sources_empty_body),
            color = BuroTextSecondary,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SourceCard(
    source: SourceUi,
    onOpenSource: (SourceUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    FocusSurface(
        onClick = { onOpenSource(source) },
        modifier = modifier
            .fillMaxWidth()
            .height(94.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(BuroGold.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Folder, contentDescription = null, tint = BuroAccent)
            }
            Spacer(Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = source.name,
                    color = BuroTextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.catalog_items_count,
                        source.channelCount,
                        source.channelCount,
                    ),
                    color = BuroTextSecondary,
                    fontSize = 14.sp,
                )
            }
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = BuroAccent)
        }
    }
}

@Composable
private fun CategoriesContent(
    title: String,
    categories: List<CategoryUi>,
    contentType: CatalogContentType?,
    isLoading: Boolean,
    hasError: Boolean,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onOpenCategory: (CategoryUi) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compactPortrait = maxWidth < 600.dp && maxHeight >= maxWidth
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = if (compactPortrait) 16.dp else 42.dp,
                    vertical = if (compactPortrait) 18.dp else 34.dp,
                ),
        ) {
            ScreenHeader(title = title, onBack = onBack)
            Spacer(Modifier.height(if (compactPortrait) 18.dp else 24.dp))

            when {
                isLoading -> CatalogLoadingState()
                hasError -> BuroErrorState(
                    title = stringResource(R.string.catalog_error_title),
                    message = stringResource(R.string.catalog_error_body),
                    actionLabel = stringResource(R.string.common_retry),
                    onAction = onRetry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )

                categories.none { it.channelCount > 0 } -> BuroEmptyState(
                    title = stringResource(R.string.categories_empty),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )

                else -> LazyVerticalGrid(
                    columns =
                        if (compactPortrait) {
                            GridCells.Adaptive(minSize = 150.dp)
                        } else {
                            GridCells.Fixed(3)
                        },
                    contentPadding = PaddingValues(bottom = 28.dp),
                    horizontalArrangement =
                        Arrangement.spacedBy(if (compactPortrait) 10.dp else 16.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(if (compactPortrait) 10.dp else 16.dp),
                ) {
                    items(
                        items = categories.filter { it.channelCount > 0 },
                        key = { it.id ?: "__all__" },
                    ) { category ->
                        FocusSurface(
                            onClick = { onOpenCategory(category) },
                            modifier = Modifier.height(if (compactPortrait) 132.dp else 150.dp),
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                CategoryArtwork(
                                    tile = category.categoryArtworkTile(contentType),
                                    modifier = Modifier.fillMaxSize(),
                                )
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(
                                                        BuroCanvas.copy(alpha = 0.12f),
                                                        BuroCanvas.copy(alpha = 0.58f),
                                                        BuroCanvas.copy(alpha = 0.96f),
                                                    ),
                                                ),
                                            ),
                                )
                                Column(
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .padding(if (compactPortrait) 14.dp else 18.dp),
                                    verticalArrangement = Arrangement.Bottom,
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            imageVector = category.categoryIcon(contentType),
                                            contentDescription = null,
                                            tint = BuroAccent,
                                            modifier = Modifier.size(if (compactPortrait) 22.dp else 26.dp),
                                        )
                                        Spacer(Modifier.weight(1f))
                                        category.providerBadge()?.let { badge ->
                                            Text(
                                                text = badge.monogram,
                                                color = BuroTextPrimary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Black,
                                                modifier =
                                                    Modifier
                                                        .clip(CircleShape)
                                                        .background(BuroCanvas.copy(alpha = 0.68f))
                                                        .padding(horizontal = 9.dp, vertical = 5.dp),
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(7.dp))
                                    Text(
                                        text =
                                            if (category.id == null) {
                                                stringResource(
                                                    if (contentType == CatalogContentType.LIVE) {
                                                        R.string.categories_all
                                                    } else {
                                                        R.string.categories_all_items
                                                    },
                                                )
                                            } else {
                                                category.name
                                            },
                                        color = BuroTextPrimary,
                                        fontSize = if (compactPortrait) 16.sp else 19.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text =
                                            pluralStringResource(
                                                R.plurals.catalog_items_count,
                                                category.channelCount,
                                                category.channelCount,
                                            ),
                                        color = BuroAccent,
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryArtwork(
    tile: CategoryArtworkTile,
    modifier: Modifier = Modifier,
) {
    val atlas = ImageBitmap.imageResource(R.drawable.buro_category_atlas_v1)
    val tileWidth = atlas.width / 3
    val tileHeight = atlas.height / 2
    Image(
        painter =
            BitmapPainter(
                image = atlas,
                srcOffset = IntOffset(tile.column * tileWidth, tile.row * tileHeight),
                srcSize = IntSize(tileWidth, tileHeight),
            ),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Crop,
    )
}

private data class CategoryArtworkTile(val column: Int, val row: Int)

private fun CategoryUi.categoryArtworkTile(contentType: CatalogContentType?): CategoryArtworkTile {
    val normalized = name.lowercase()
    return when {
        "4k" in normalized || "uhd" in normalized || "hevc" in normalized -> CategoryArtworkTile(2, 1)
        "sport" in normalized || "futebol" in normalized -> CategoryArtworkTile(0, 1)
        "infantil" in normalized || "kids" in normalized || "family" in normalized -> CategoryArtworkTile(1, 1)
        contentType == CatalogContentType.LIVE -> CategoryArtworkTile(0, 0)
        contentType == CatalogContentType.SERIES -> CategoryArtworkTile(2, 0)
        else -> CategoryArtworkTile(1, 0)
    }
}

private fun CategoryUi.categoryIcon(contentType: CatalogContentType?): ImageVector {
    val normalized = name.lowercase()
    return when {
        id == null -> Icons.Default.Folder
        "ação" in normalized || "action" in normalized -> Icons.Default.RocketLaunch
        "comédia" in normalized || "comedy" in normalized -> Icons.Default.TheaterComedy
        "infantil" in normalized || "kids" in normalized || "family" in normalized -> Icons.Default.ChildCare
        "sport" in normalized || "futebol" in normalized -> Icons.Default.SportsSoccer
        "document" in normalized || "história" in normalized -> Icons.Default.HistoryEdu
        "lançamento" in normalized || Regex("\b20(2[4-9]|3[0-9])\b").containsMatchIn(normalized) -> Icons.Default.NewReleases
        "internacional" in normalized || "world" in normalized -> Icons.Default.Public
        contentType == CatalogContentType.LIVE -> Icons.Default.LiveTv
        else -> Icons.Default.Movie
    }
}

private data class ProviderBadge(
    val monogram: String,
    val label: String,
)

private fun CategoryUi.providerBadge(): ProviderBadge? {
    if (id == null) return null
    val normalized = name.lowercase()
    return when {
        "netflix" in normalized -> ProviderBadge("N", "Netflix")
        "amazon" in normalized || "prime video" in normalized -> ProviderBadge("AP", "Prime Video")
        "disney" in normalized -> ProviderBadge("D+", "Disney+")
        "globoplay" in normalized -> ProviderBadge("G", "Globoplay")
        "discovery" in normalized -> ProviderBadge("D", "Discovery+")
        Regex("(^|[ |])max([ |]|$)").containsMatchIn(normalized) -> ProviderBadge("M", "Max")
        else -> null
    }
}

@Composable
private fun ChannelsContent(
    title: String,
    subtitle: String,
    channels: List<ChannelUi>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    hasError: Boolean,
    isResolvingPlayback: Boolean,
    hasPlaybackError: Boolean,
    onBack: () -> Unit,
    onOpenChannel: (ChannelUi) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    headerAction: (@Composable () -> Unit)? = null,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compactPortrait = maxWidth < 600.dp && maxHeight >= maxWidth
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = if (compactPortrait) 16.dp else 42.dp,
                    vertical = if (compactPortrait) 18.dp else 34.dp,
                ),
        ) {
            ScreenHeader(title = title, subtitle = subtitle, onBack = onBack)
            headerAction?.let {
                Spacer(Modifier.height(12.dp))
                it()
            }
            Spacer(Modifier.height(if (compactPortrait) 18.dp else 24.dp))

            if (isResolvingPlayback || hasPlaybackError) {
                Text(
                    text =
                        stringResource(
                            if (hasPlaybackError) {
                                R.string.playback_resolve_error
                            } else {
                                R.string.playback_resolving
                            },
                        ),
                    color = if (hasPlaybackError) BuroDanger else BuroAccent,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }

            when {
                isLoading -> CatalogLoadingState()
                hasError && channels.isEmpty() -> BuroErrorState(
                    title = stringResource(R.string.catalog_error_title),
                    message = stringResource(R.string.catalog_error_body),
                    actionLabel = stringResource(R.string.common_retry),
                    onAction = onRetry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )

                channels.isEmpty() -> BuroEmptyState(
                    title = stringResource(R.string.channels_empty),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )

                else -> LazyVerticalGrid(
                    columns =
                        if (compactPortrait) {
                            GridCells.Adaptive(minSize = 150.dp)
                        } else {
                            GridCells.Fixed(4)
                        },
                    contentPadding = PaddingValues(bottom = 30.dp),
                    horizontalArrangement =
                        Arrangement.spacedBy(if (compactPortrait) 10.dp else 14.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(if (compactPortrait) 10.dp else 14.dp),
                ) {
                    items(
                        items = channels,
                        key = { it.id },
                    ) { channel ->
                        ChannelCard(
                            channel = channel,
                            onOpenChannel = onOpenChannel,
                            enabled = !isResolvingPlayback,
                        )
                    }
                    val footerState =
                        resolveChannelFooterState(
                            isLoadingMore = isLoadingMore,
                            hasMore = hasMore,
                            hasError = hasError,
                        )
                    if (footerState.isVisible) {
                        item(
                            key = "channels:load-more",
                            span = { GridItemSpan(maxLineSpan) },
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                BuroButton(
                                    onClick = {
                                        if (footerState.acceptsInput) {
                                            when (footerState.action) {
                                                ChannelFooterAction.LOAD_MORE -> onLoadMore()
                                                ChannelFooterAction.RETRY -> onRetry()
                                                ChannelFooterAction.NONE -> Unit
                                            }
                                        }
                                    },
                                    modifier =
                                        Modifier.semantics {
                                            if (!footerState.acceptsInput) disabled()
                                        },
                                    style = BuroButtonStyle.Secondary,
                                ) {
                                    Text(
                                        text =
                                            stringResource(
                                                when (footerState.action) {
                                                    ChannelFooterAction.RETRY ->
                                                        R.string.common_retry

                                                    ChannelFooterAction.LOAD_MORE ->
                                                        R.string.channels_load_more

                                                    ChannelFooterAction.NONE ->
                                                        R.string.channels_loading_more
                                                },
                                            ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MyBuroContent(
    favorites: List<ChannelUi>,
    isLoading: Boolean,
    hasError: Boolean,
    onOpenChannel: (ChannelUi) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    ChannelsContent(
        title = "Minha BURO",
        subtitle = "Favoritos do perfil ativo",
        channels = favorites,
        isLoading = isLoading,
        isLoadingMore = false,
        hasMore = false,
        hasError = hasError,
        isResolvingPlayback = false,
        hasPlaybackError = false,
        onBack = onBack,
        onOpenChannel = onOpenChannel,
        onRetry = onRetry,
        onLoadMore = {},
        headerAction = null,
    )
}

@Composable
private fun ChannelCard(
    channel: ChannelUi,
    onOpenChannel: (ChannelUi) -> Unit,
    enabled: Boolean,
) {
    val isPoster =
        channel.contentType == CatalogContentType.MOVIE ||
            channel.contentType == CatalogContentType.SERIES ||
            channel.contentType == CatalogContentType.EPISODE
    val artworkRequest =
        channel.logoUrl
            ?.takeIf(String::isNotBlank)
            ?.let { artworkUrl ->
                ImageRequest.Builder(LocalPlatformContext.current)
                    .data(artworkUrl)
                    // Signed provider artwork stays in memory and is never written to Coil's disk cache.
                    .diskCachePolicy(CachePolicy.DISABLED)
                    .build()
            }
    FocusSurface(
        onClick = { onOpenChannel(channel) },
        enabled = enabled,
        modifier = Modifier.aspectRatio(if (isPoster) 2f / 3f else 16f / 9f),
        backgroundColor = BuroSurface,
        focusedBackgroundColor = BuroSurface,
    ) { isFocused ->
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                BuroGold.copy(alpha = 0.48f),
                                BuroAccent.copy(alpha = 0.2f),
                                BuroCanvas,
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = channel.name.firstOrNull()?.uppercase() ?: "▶",
                    color = BuroTextPrimary.copy(alpha = 0.74f),
                    fontSize = if (isPoster) 42.sp else 32.sp,
                    fontWeight = FontWeight.Black,
                )
            }

            artworkRequest?.let { request ->
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    modifier =
                        if (isPoster) {
                            Modifier.fillMaxSize()
                        } else {
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = 28.dp, vertical = 20.dp)
                        },
                    contentScale = if (isPoster) ContentScale.Crop else ContentScale.Fit,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(if (isPoster) 0.56f else 0.72f)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, BuroCanvas.copy(alpha = 0.96f)),
                        ),
                    ),
            )

            Text(
                text = channel.contentType.catalogLabel(),
                color = if (channel.contentType == CatalogContentType.LIVE) BuroAccent else BuroTextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .clip(CircleShape)
                    .background(BuroCanvas.copy(alpha = 0.74f))
                    .padding(horizontal = 9.dp, vertical = 5.dp),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(if (isPoster) 16.dp else 14.dp),
            ) {
                Text(
                    text = channel.name,
                    color = BuroTextPrimary,
                    fontSize = if (isPoster) 17.sp else 15.sp,
                    lineHeight = if (isPoster) 21.sp else 18.sp,
                    fontWeight = if (isFocused) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                channel.categoryName?.let {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = it,
                        color = BuroTextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun CatalogContentType.catalogLabel(): String =
    stringResource(
        when (this) {
            CatalogContentType.LIVE -> R.string.buro_nav_live
            CatalogContentType.MOVIE -> R.string.buro_nav_movies
            CatalogContentType.SERIES,
            CatalogContentType.EPISODE,
            -> R.string.buro_nav_series
            CatalogContentType.UNKNOWN -> R.string.nav_live
        },
    ).uppercase()

@Composable
private fun CatalogLoadingState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(
            color = BuroTheme.colors.brandPrimary,
            trackColor = BuroTheme.colors.elevated,
            strokeWidth = 3.dp,
            modifier = Modifier.size(42.dp),
        )
        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.catalog_loading),
            color = BuroTextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "BURO  •  CATÁLOGO LOCAL",
            color = BuroTextSecondary,
            fontSize = 12.sp,
            letterSpacing = 1.2.sp,
        )
    }
}

@Composable
private fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
) {
    val backFocusRequester = remember(title) { FocusRequester() }
    LaunchedEffect(title) {
        backFocusRequester.requestFocus()
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val compact = maxWidth < 600.dp
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FocusSurface(
                onClick = onBack,
                modifier = Modifier
                    .size(if (compact) 48.dp else 52.dp)
                    .focusRequester(backFocusRequester),
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.common_back),
                        tint = BuroAccent,
                    )
                }
            }
            Spacer(Modifier.width(if (compact) 12.dp else 18.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = BuroTextPrimary,
                    fontSize = if (compact) 24.sp else 30.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                subtitle?.let {
                    Text(
                        text = it,
                        color = BuroTextSecondary,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsContent(
    activeProfile: ProfileUi?,
    deviceId: String?,
    onChangeProfile: () -> Unit,
    onSelectLanguage: (String) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compact = maxHeight < 520.dp || maxWidth < 600.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = if (maxWidth < 600.dp) 16.dp else if (compact) 28.dp else 42.dp,
                    vertical = if (compact) 18.dp else 34.dp,
                ),
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                color = BuroTextPrimary,
                fontSize = if (compact) 28.sp else 32.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(if (compact) 16.dp else 28.dp))

            SettingsRow(
                title = stringResource(
                    R.string.settings_version,
                    BuildConfig.VERSION_NAME,
                ),
                body = stringResource(R.string.settings_legal),
                compact = compact,
            )
            Spacer(Modifier.height(if (compact) 10.dp else 14.dp))
            SettingsRow(
                title = "Licença deste dispositivo",
                body = buildString {
                    append("Device ID: ")
                    append(deviceId ?: "gerando…")
                    append("\nTeste de 7 dias e compra única de € 9,99 serão ativados pelo servidor seguro; nenhuma cobrança está ativa nesta prévia.")
                },
                compact = compact,
            )
            Spacer(Modifier.height(if (compact) 10.dp else 14.dp))
            Text(stringResource(R.string.settings_language), color = BuroTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppLocaleController.supportedLanguages.forEach { language ->
                    FocusSurface(
                        onClick = { onSelectLanguage(language.tag) },
                        modifier = Modifier.weight(1f).height(if (compact) 48.dp else 56.dp),
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(language.displayName, color = BuroTextPrimary, fontSize = if (compact) 12.sp else 14.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(if (compact) 10.dp else 14.dp))
            FocusSurface(onClick = onChangeProfile, modifier = Modifier.fillMaxWidth().height(if (compact) 62.dp else 72.dp)) {
                Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Perfil ativo", color = BuroTextSecondary, modifier = Modifier.weight(1f))
                    Text(activeProfile?.name ?: "Selecionar perfil", color = BuroAccent, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    body: String,
    compact: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(BuroSurface)
            .padding(if (compact) 16.dp else 24.dp),
    ) {
        Text(
            text = title,
            color = BuroTextPrimary,
            fontSize = if (compact) 17.sp else 19.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(if (compact) 4.dp else 6.dp))
        Text(
            text = body,
            color = BuroTextSecondary,
            fontSize = if (compact) 13.sp else 14.sp,
        )
    }
}

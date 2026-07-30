package com.lucasserafin94.iptvburo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
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
import com.lucasserafin94.iptvburo.ui.SourceUi
import com.lucasserafin94.iptvburo.ui.components.FocusSurface
import com.lucasserafin94.iptvburo.ui.designsystem.BuroButton
import com.lucasserafin94.iptvburo.ui.designsystem.BuroButtonStyle
import com.lucasserafin94.iptvburo.ui.designsystem.BuroScreen
import com.lucasserafin94.iptvburo.ui.designsystem.BuroSpacing
import com.lucasserafin94.iptvburo.ui.designsystem.BuroTheme
import com.lucasserafin94.iptvburo.ui.home.DemoHomeCatalog
import com.lucasserafin94.iptvburo.ui.home.DemoStoryScreen
import com.lucasserafin94.iptvburo.ui.home.HomeSourceSummary
import com.lucasserafin94.iptvburo.ui.home.LivingHomeScreen
import com.lucasserafin94.iptvburo.ui.navigation.BuroRibbon
import com.lucasserafin94.iptvburo.ui.theme.Blue
import com.lucasserafin94.iptvburo.ui.theme.Danger
import com.lucasserafin94.iptvburo.ui.theme.Ink
import com.lucasserafin94.iptvburo.ui.theme.InkSoft
import com.lucasserafin94.iptvburo.ui.theme.Muted
import com.lucasserafin94.iptvburo.ui.theme.Surface
import com.lucasserafin94.iptvburo.ui.theme.Teal
import com.lucasserafin94.iptvburo.ui.theme.White

@Composable
fun AppShellScreen(
    state: AppUiState,
    onSelectSection: (AppSection) -> Unit,
    onImportSource: () -> Unit,
    onOpenSource: (SourceUi) -> Unit,
    onOpenCategory: (CategoryUi) -> Unit,
    onOpenChannel: (ChannelUi) -> Unit,
    onOpenHomeItem: (String) -> Unit,
    onRememberHomeFocus: (String?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ribbonFocusRequester = remember { FocusRequester() }
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
            .background(
                Brush.linearGradient(
                    colors = listOf(Ink, InkSoft, Ink),
                ),
            ),
    ) {
        when (val content = state.content) {
            is AppContent.Story -> DemoStoryScreen(
                itemId = content.itemId,
                onImportSource = onImportSource,
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
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    when (content) {
                        AppContent.Home -> LivingHomeScreen(
                            sources = state.sources.map(SourceUi::toHomeSummary),
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
                            onImportSource = onImportSource,
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
                            onBack = onBack,
                            onOpenCategory = onOpenCategory,
                        )

                        is AppContent.Channels -> ChannelsContent(
                            title = content.categoryName,
                            subtitle = content.sourceName,
                            channels = state.channels,
                            onBack = onBack,
                            onOpenChannel = onOpenChannel,
                        )

                        AppContent.Settings -> SettingsContent()
                        is AppContent.Player,
                        is AppContent.Story,
                        -> Unit
                    }
                }
            }
        }
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
                    .background(Brush.linearGradient(listOf(Teal, Blue))),
                contentAlignment = Alignment.Center,
            ) {
                Text("▶", color = Ink, fontSize = 20.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("IPTV", color = Teal, fontSize = 11.sp, letterSpacing = 3.sp)
                Text(
                    "BURO",
                    color = White,
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
        backgroundColor = if (selected) Blue.copy(alpha = 0.24f) else Color.Transparent,
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
                tint = if (selected) Teal else Muted,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(14.dp))
            Text(
                text = label,
                color = if (selected) White else Muted,
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
            color = White,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.home_subtitle),
            color = Muted,
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
                accent = Teal,
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                value = state.sources.sumOf { it.channelCount }.toString(),
                label = pluralStringResource(
                    R.plurals.home_channels_count,
                    state.sources.sumOf { it.channelCount },
                    state.sources.sumOf { it.channelCount },
                ),
                accent = Blue,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(24.dp))

        FocusSurface(
            onClick = onAddSource,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            backgroundColor = Surface,
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
                        .background(Brush.linearGradient(listOf(Teal, Blue))),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = Ink,
                        modifier = Modifier.size(48.dp),
                    )
                }
                Column {
                    Text(
                        text = stringResource(R.string.home_action),
                        color = White,
                        fontSize = 27.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.sources_empty_body),
                        color = Muted,
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
            .background(Surface)
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
            color = Muted,
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
    onImportSource: () -> Unit,
    onOpenSource: (SourceUi) -> Unit,
) {
    val initialFocusRequester = remember { FocusRequester() }
    LaunchedEffect(sources.firstOrNull()?.id, isImporting) {
        initialFocusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 42.dp, vertical = 34.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.sources_title),
                    color = White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (lastImportedChannelCount != null || hasImportError) {
                    Text(
                        text = if (hasImportError) {
                            stringResource(R.string.sources_import_error)
                        } else {
                            pluralStringResource(
                                R.plurals.sources_import_success,
                                lastImportedChannelCount ?: 0,
                                lastImportedChannelCount ?: 0,
                            )
                        },
                        color = if (hasImportError) Danger else Teal,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            Button(
                onClick = onImportSource,
                enabled = !isImporting,
                modifier =
                    if (sources.isEmpty()) {
                        Modifier.focusRequester(initialFocusRequester)
                    } else {
                        Modifier
                    },
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isImporting) {
                        stringResource(R.string.sources_importing)
                    } else {
                        stringResource(R.string.sources_import)
                    },
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        if (sources.isEmpty()) {
            EmptySources(onImportSource)
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

@Composable
private fun EmptySources(onImportSource: () -> Unit) {
    FocusSurface(
        onClick = onImportSource,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(42.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = Teal,
                modifier = Modifier.size(58.dp),
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.sources_empty),
                color = White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.sources_empty_body),
                color = Muted,
                fontSize = 16.sp,
            )
        }
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
                    .background(Blue.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Folder, contentDescription = null, tint = Teal)
            }
            Spacer(Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = source.name,
                    color = White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.home_channels_count,
                        source.channelCount,
                        source.channelCount,
                    ),
                    color = Muted,
                    fontSize = 14.sp,
                )
            }
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Teal)
        }
    }
}

@Composable
private fun CategoriesContent(
    title: String,
    categories: List<CategoryUi>,
    onBack: () -> Unit,
    onOpenCategory: (CategoryUi) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 42.dp, vertical = 34.dp),
    ) {
        ScreenHeader(title = title, onBack = onBack)
        Spacer(Modifier.height(24.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(
                items = categories,
                key = { it.id ?: "__all__" },
            ) { category ->
                FocusSurface(
                    onClick = { onOpenCategory(category) },
                    modifier = Modifier.height(120.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(22.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = category.name,
                            color = White,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(7.dp))
                        Text(
                            text = pluralStringResource(
                                R.plurals.home_channels_count,
                                category.channelCount,
                                category.channelCount,
                            ),
                            color = Teal,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelsContent(
    title: String,
    subtitle: String,
    channels: List<ChannelUi>,
    onBack: () -> Unit,
    onOpenChannel: (ChannelUi) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 42.dp, vertical = 34.dp),
    ) {
        ScreenHeader(title = title, subtitle = subtitle, onBack = onBack)
        Spacer(Modifier.height(24.dp))

        if (channels.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.channels_empty),
                    color = Muted,
                    fontSize = 18.sp,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                contentPadding = PaddingValues(bottom = 30.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(
                    items = channels,
                    key = { it.id },
                ) { channel ->
                    ChannelCard(channel, onOpenChannel)
                }
            }
        }
    }
}

@Composable
private fun ChannelCard(
    channel: ChannelUi,
    onOpenChannel: (ChannelUi) -> Unit,
) {
    FocusSurface(
        onClick = { onOpenChannel(channel) },
        modifier = Modifier.height(142.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Blue, Teal))),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = channel.name.firstOrNull()?.uppercase() ?: "▶",
                    color = Ink,
                    fontWeight = FontWeight.Black,
                )
            }
            Column {
                Text(
                    text = channel.name,
                    color = White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                channel.categoryName?.let {
                    Text(
                        text = it,
                        color = Muted,
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
private fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
) {
    val backFocusRequester = remember(title) { FocusRequester() }
    LaunchedEffect(title) {
        backFocusRequester.requestFocus()
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FocusSurface(
            onClick = onBack,
            modifier = Modifier
                .size(52.dp)
                .focusRequester(backFocusRequester),
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.common_back),
                    tint = Teal,
                )
            }
        }
        Spacer(Modifier.width(18.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Text(
                    text = it,
                    color = Muted,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SettingsContent() {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compact = maxHeight < 520.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = if (compact) 28.dp else 42.dp,
                    vertical = if (compact) 18.dp else 34.dp,
                ),
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                color = White,
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
                title = stringResource(R.string.nav_settings),
                body = stringResource(R.string.settings_language),
                compact = compact,
            )
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
            .background(Surface)
            .padding(if (compact) 16.dp else 24.dp),
    ) {
        Text(
            text = title,
            color = White,
            fontSize = if (compact) 17.sp else 19.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(if (compact) 4.dp else 6.dp))
        Text(
            text = body,
            color = Muted,
            fontSize = if (compact) 13.sp else 14.sp,
        )
    }
}

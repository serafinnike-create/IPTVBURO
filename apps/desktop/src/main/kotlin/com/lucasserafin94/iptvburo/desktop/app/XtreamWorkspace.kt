package com.lucasserafin94.iptvburo.desktop.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lucasserafin94.iptvburo.desktop.DesktopAppState
import com.lucasserafin94.iptvburo.desktop.MovieDetailsStatus
import com.lucasserafin94.iptvburo.desktop.LiveEpgStatus
import com.lucasserafin94.iptvburo.desktop.PersonFilmography
import com.lucasserafin94.iptvburo.desktop.SeriesDetailsStatus
import com.lucasserafin94.iptvburo.desktop.XtreamStatus
import com.lucasserafin94.iptvburo.desktop.model.XtreamPlaybackTarget
import com.lucasserafin94.iptvburo.desktop.ui.BuroColors
import com.lucasserafin94.iptvburo.desktop.ui.BuroInteractiveRow
import com.lucasserafin94.iptvburo.desktop.ui.BuroInteractiveSurface
import com.lucasserafin94.iptvburo.desktop.ui.BuroRadius
import com.lucasserafin94.iptvburo.desktop.ui.BuroRemoteArtwork
import com.lucasserafin94.iptvburo.desktop.ui.BuroScrim
import com.lucasserafin94.iptvburo.desktop.ui.BuroSpacing
import com.lucasserafin94.iptvburo.desktop.ui.DesktopStrings
import com.lucasserafin94.iptvburo.desktop.ui.strings
import com.lucasserafin94.iptvburo.xtream.XtreamCatalogItem
import com.lucasserafin94.iptvburo.xtream.XtreamCategory
import com.lucasserafin94.iptvburo.xtream.XtreamContentType
import com.lucasserafin94.iptvburo.xtream.XtreamEpisode
import com.lucasserafin94.iptvburo.domain.model.ResumeDecision
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Year

@Composable
fun XtreamWorkspace(
    appState: DesktopAppState,
    onOpenExternal: (PendingXtreamExternal) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var detailsOpen by remember { mutableStateOf(false) }
    var personOpen by remember { mutableStateOf(false) }

    LaunchedEffect(appState.xtreamSearchQuery, appState.xtreamContentType) {
        delay(SEARCH_DEBOUNCE_MILLIS)
        appState.applyXtreamSearch()
    }

    LaunchedEffect(appState.selectedXtreamItem?.providerId, detailsOpen) {
        if (!detailsOpen) return@LaunchedEffect
        when (appState.selectedXtreamItem?.contentType) {
            XtreamContentType.MOVIE -> appState.loadSelectedMovieDetails()
            XtreamContentType.SERIES -> appState.loadSelectedSeriesDetails()
            XtreamContentType.LIVE -> appState.loadSelectedLiveEpg()
            else -> Unit
        }
    }

    if (personOpen) {
        val person = appState.selectedPerson
        if (person != null) {
            PersonFilmographyPage(
                person = person,
                onBack = {
                    personOpen = false
                    appState.closePerson()
                },
                onOpenItem = { item ->
                    appState.selectDailyItem(item)
                    personOpen = false
                    appState.closePerson()
                },
            )
            return
        }
    }

    if (detailsOpen && appState.selectedXtreamItem != null) {
        XtreamInternalDetailsPage(
            appState = appState,
            onBack = { detailsOpen = false },
            onOpenExternal = onOpenExternal,
            onOpenPerson = { name ->
                appState.openPerson(name)
                personOpen = true
            },
        )
        return
    }

    val text = strings
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(BuroColors.Canvas)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown || !event.isCtrlPressed) {
                        return@onPreviewKeyEvent false
                    }
                    val destination =
                        when (event.key) {
                            Key.One, Key.NumPad1 -> XtreamContentType.LIVE
                            Key.Two, Key.NumPad2 -> XtreamContentType.MOVIE
                            Key.Three, Key.NumPad3 -> XtreamContentType.SERIES
                            else -> return@onPreviewKeyEvent false
                        }
                    scope.launch { appState.selectXtreamContentType(destination) }
                    true
                },
    ) {
        XtreamToolbar(
            selectedType = appState.xtreamContentType,
            query = appState.xtreamSearchQuery,
            status = appState.xtreamStatus,
            onQueryChange = appState::updateXtreamSearch,
            onTypeSelected = { type ->
                detailsOpen = false
                scope.launch { appState.selectXtreamContentType(type) }
            },
            onDisconnect = appState::disconnectXtream,
            selectedYear = appState.selectedXtreamYear,
            onYearSelected = { year -> scope.launch { appState.selectXtreamYear(year) } },
        )
        XtreamCategoryRail(
            categories = appState.xtreamCategories,
            contentType = appState.xtreamContentType,
            selectedCategoryId = appState.selectedXtreamCategoryId,
            onSelected = { categoryId ->
                detailsOpen = false
                scope.launch { appState.selectXtreamCategory(categoryId) }
            },
        )
        HorizontalDivider(color = BuroColors.BorderSoft)
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            XtreamCatalogGrid(
                appState = appState,
                text = text,
                wide = maxWidth >= 1_280.dp,
                onItemSelected = { providerId ->
                    appState.selectXtreamItem(providerId)
                    detailsOpen = true
                },
                onPreviousPage = {
                    detailsOpen = false
                    scope.launch { appState.previousXtreamPage() }
                },
                onNextPage = {
                    detailsOpen = false
                    scope.launch { appState.nextXtreamPage() }
                },
            )
            if (
                appState.xtreamStatus is XtreamStatus.Connecting ||
                appState.xtreamStatus is XtreamStatus.LoadingCatalog
            ) {
                XtreamLoadingOverlay(status = appState.xtreamStatus)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Toolbar
// ---------------------------------------------------------------------------------------------

@Composable
private fun XtreamToolbar(
    selectedType: XtreamContentType,
    query: String,
    status: XtreamStatus,
    onQueryChange: (String) -> Unit,
    onTypeSelected: (XtreamContentType) -> Unit,
    onDisconnect: () -> Unit,
    selectedYear: Int?,
    onYearSelected: (Int?) -> Unit,
) {
    val text = strings
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = BuroSpacing.GutterCompact, vertical = BuroSpacing.Md),
        verticalArrangement = Arrangement.spacedBy(BuroSpacing.Sm),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Segmented control rather than three filled buttons: only one type can be active, and
            // three competing gold buttons made every state read as "selected".
            Row(
                modifier =
                    Modifier
                        .clip(BuroRadius.Pill)
                        .background(BuroColors.SurfaceRaised)
                        .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                XtreamContentType.entries.forEach { type ->
                    ContentTypeButton(
                        label = type.label(text),
                        selected = type == selectedType,
                        onClick = { onTypeSelected(type) },
                    )
                }
            }
            Spacer(Modifier.width(BuroSpacing.Md))
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f).widthIn(max = 460.dp),
                singleLine = true,
                placeholder = { Text(text.searchCatalog) },
                leadingIcon = { Text("⌕", color = BuroColors.TextSubtle) },
                shape = BuroRadius.Small,
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BuroColors.Primary,
                        unfocusedBorderColor = BuroColors.Border,
                        focusedContainerColor = BuroColors.Surface,
                        unfocusedContainerColor = BuroColors.Surface,
                    ),
            )
            Spacer(Modifier.weight(1f))
            OutlinedButton(
                onClick = onDisconnect,
                shape = BuroRadius.Small,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = BuroColors.TextMuted),
            ) {
                Text(text.endSession, maxLines = 1)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selectedType != XtreamContentType.LIVE) {
                val currentYear = Year.now().value
                Row(horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Xs)) {
                    listOf<Int?>(null, currentYear, currentYear - 1).forEach { year ->
                        FilterChip(
                            label =
                                if (year == null) {
                                    text.allYears
                                } else {
                                    "${text.releasesIn} $year"
                                },
                            selected = selectedYear == year,
                            onClick = { onYearSelected(year) },
                        )
                    }
                }
                Spacer(Modifier.width(BuroSpacing.Md))
            }
            Spacer(Modifier.weight(1f))
            SessionStatusLabel(status = status, text = text)
        }
    }
}

@Composable
private fun SessionStatusLabel(
    status: XtreamStatus,
    text: DesktopStrings,
) {
    val message =
        when (status) {
            XtreamStatus.Connected -> text.sessionActive
            is XtreamStatus.Error -> status.message
            XtreamStatus.Connecting -> text.authenticating
            is XtreamStatus.LoadingCatalog -> text.loadingCatalog
            XtreamStatus.Disconnected -> text.sessionClosed
        }
    val tint = if (status is XtreamStatus.Error) BuroColors.Error else BuroColors.Success
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(tint))
        Spacer(Modifier.width(BuroSpacing.Xs))
        Text(
            text = message,
            color = if (status is XtreamStatus.Error) BuroColors.Error else BuroColors.TextSubtle,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ContentTypeButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    BuroInteractiveRow(
        onClick = onClick,
        selected = selected,
        shape = BuroRadius.Pill,
        contentDescription = label,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = BuroSpacing.Md, vertical = BuroSpacing.Xs),
            color = if (selected) BuroColors.Primary else BuroColors.TextMuted,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    BuroInteractiveRow(
        onClick = onClick,
        selected = selected,
        shape = BuroRadius.Pill,
        contentDescription = label,
    ) {
        Box(
            modifier =
                Modifier
                    .border(
                        width = 1.dp,
                        color =
                            if (selected) {
                                BuroColors.Primary.copy(alpha = 0.55f)
                            } else {
                                BuroColors.BorderSoft
                            },
                        shape = BuroRadius.Pill,
                    ).padding(horizontal = 14.dp, vertical = 7.dp),
        ) {
            Text(
                text = label,
                color = if (selected) BuroColors.Primary else BuroColors.TextMuted,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Categories
// ---------------------------------------------------------------------------------------------

/**
 * Horizontal category rail.
 *
 * This replaced a permanent 220 dp side pane. The GDD rules out both the administrative-panel look
 * and spending fixed width on a menu, and the pane cost the same width on every screen while being
 * used momentarily. Search covers precise lookup; the rail covers browsing.
 */
@Composable
private fun XtreamCategoryRail(
    categories: List<XtreamCategory>,
    contentType: XtreamContentType,
    selectedCategoryId: String?,
    onSelected: (String?) -> Unit,
) {
    val text = strings
    val listState = rememberLazyListState()

    // Jump back to the start when the content type changes, otherwise the rail keeps the scroll
    // offset of a category list that no longer exists.
    LaunchedEffect(contentType) { listState.scrollToItem(0) }

    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding =
            PaddingValues(
                horizontal = BuroSpacing.GutterCompact,
                vertical = BuroSpacing.Xs,
            ),
        horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item(key = "category:all") {
            XtreamCategoryChip(
                label = text.allCategories,
                artworkResource = categoryArtworkResource("", contentType),
                selected = selectedCategoryId == null,
                onClick = { onSelected(null) },
            )
        }
        items(categories, key = XtreamCategory::providerId) { category ->
            XtreamCategoryChip(
                label = category.name,
                artworkResource = categoryArtworkResource(category.name, contentType),
                selected = category.providerId == selectedCategoryId,
                onClick = { onSelected(category.providerId) },
            )
        }
    }
}

@Composable
private fun XtreamCategoryChip(
    label: String,
    artworkResource: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    BuroInteractiveRow(
        onClick = onClick,
        selected = selected,
        shape = BuroRadius.Pill,
        contentDescription = label,
    ) {
        Row(
            modifier =
                Modifier
                    .border(
                        width = 1.dp,
                        color =
                            if (selected) {
                                BuroColors.Primary.copy(alpha = 0.55f)
                            } else {
                                BuroColors.BorderSoft
                            },
                        shape = BuroRadius.Pill,
                    ).padding(start = 4.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(artworkResource),
                contentDescription = null,
                modifier = Modifier.size(26.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(BuroSpacing.Xs))
            Text(
                text = label,
                color = if (selected) BuroColors.Text else BuroColors.TextMuted,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun categoryArtworkResource(
    label: String,
    contentType: XtreamContentType,
): String {
    val normalized = label.lowercase()
    val name =
        when {
            "4k" in normalized || "uhd" in normalized || "hevc" in normalized -> "4k"
            "sport" in normalized || "futebol" in normalized -> "sports"
            "infantil" in normalized || "kids" in normalized || "family" in normalized -> "kids"
            contentType == XtreamContentType.LIVE -> "live"
            contentType == XtreamContentType.SERIES -> "series"
            else -> "cinema"
        }
    return "brand/buro-category-$name.png"
}

// ---------------------------------------------------------------------------------------------
// Grid
// ---------------------------------------------------------------------------------------------

@Composable
private fun XtreamCatalogGrid(
    appState: DesktopAppState,
    text: DesktopStrings,
    wide: Boolean,
    onItemSelected: (String) -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
) {
    val page = appState.xtreamPage
    val live = appState.xtreamContentType == XtreamContentType.LIVE
    val gutter = if (wide) BuroSpacing.GutterWide else BuroSpacing.GutterCompact
    val gridState = rememberLazyGridState()

    // A new page reuses the same list; without this the grid keeps the previous scroll offset and
    // the first row of the new page opens already scrolled past.
    LaunchedEffect(page.pageIndex, appState.xtreamContentType, appState.selectedXtreamCategoryId) {
        gridState.scrollToItem(0)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = gutter,
                        end = gutter,
                        top = BuroSpacing.Md,
                        bottom = BuroSpacing.Xs,
                    ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text.catalog,
                color = BuroColors.Text,
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${page.totalMatches} ${text.items}",
                color = BuroColors.TextSubtle,
                style = MaterialTheme.typography.labelLarge,
            )
        }

        if (page.items.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = text.noMatch,
                    color = BuroColors.TextSubtle,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            LazyVerticalGrid(
                state = gridState,
                modifier = Modifier.weight(1f),
                columns = GridCells.Adaptive(minSize = if (live) 250.dp else 172.dp),
                contentPadding = PaddingValues(horizontal = gutter, vertical = BuroSpacing.Sm),
                horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Md),
                verticalArrangement = Arrangement.spacedBy(BuroSpacing.Lg),
            ) {
                gridItems(
                    items = page.items,
                    key = XtreamCatalogItem::providerId,
                ) { item ->
                    XtreamCatalogCard(
                        item = item,
                        text = text,
                        selected = item.providerId == appState.selectedXtreamItem?.providerId,
                        onClick = { onItemSelected(item.providerId) },
                    )
                }
            }
        }

        if (page.pageCount > 1) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = gutter, vertical = BuroSpacing.Sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                OutlinedButton(
                    onClick = onPreviousPage,
                    enabled = page.hasPrevious,
                    shape = BuroRadius.Small,
                    colors =
                        ButtonDefaults.outlinedButtonColors(contentColor = BuroColors.TextMuted),
                ) {
                    Text("←  ${text.previous}")
                }
                Text(
                    text = "${text.page} ${page.pageIndex + 1} / ${page.pageCount}",
                    modifier = Modifier.padding(horizontal = BuroSpacing.Md),
                    color = BuroColors.TextMuted,
                    style = MaterialTheme.typography.labelLarge,
                )
                OutlinedButton(
                    onClick = onNextPage,
                    enabled = page.hasNext,
                    shape = BuroRadius.Small,
                    colors =
                        ButtonDefaults.outlinedButtonColors(contentColor = BuroColors.TextMuted),
                ) {
                    Text("${text.next}  →")
                }
            }
        }
    }
}

/**
 * Catalogue card.
 *
 * Deliberately the same shape as the Home rail card — artwork, then title and facts underneath —
 * so moving between Home and the catalogue does not feel like moving between two products.
 */
@Composable
private fun XtreamCatalogCard(
    item: XtreamCatalogItem,
    text: DesktopStrings,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val live = item.contentType == XtreamContentType.LIVE
    BuroInteractiveSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = BuroRadius.Medium,
        compact = !live,
        contentDescription = item.name,
        ringColor = if (selected) BuroColors.Primary else BuroColors.Focus,
    ) { state ->
        Column {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(if (live) 16f / 9f else 2f / 3f)
                        .clip(BuroRadius.Medium)
                        .background(BuroColors.SurfaceRaised)
                        .border(
                            width = if (selected) 2.dp else 0.dp,
                            color =
                                if (selected) BuroColors.Primary else androidx.compose.ui.graphics.Color.Transparent,
                            shape = BuroRadius.Medium,
                        ),
            ) {
                BuroRemoteArtwork(
                    artworkUrl = item.artworkUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = if (live) ContentScale.Fit else ContentScale.Crop,
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        XtreamMonogram(item.name, if (live) 52 else 62)
                    }
                }
                item.rating?.takeIf { it > 0.0 }?.let { rating ->
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(BuroSpacing.Xs)
                                .clip(BuroRadius.Pill)
                                .background(BuroColors.Canvas.copy(alpha = 0.78f))
                                .padding(horizontal = BuroSpacing.Xs, vertical = 4.dp),
                    ) {
                        Text(
                            text = "★ %.1f".format(rating),
                            color = BuroColors.Primary,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .fillMaxHeight(0.4f)
                            .alpha(if (state.active) 1f else 0f)
                            .background(BuroScrim.cardFooter()),
                )
            }
            Spacer(Modifier.height(BuroSpacing.Xs))
            Text(
                text = item.name,
                color = if (selected) BuroColors.Primary else BuroColors.Text,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = itemMetadata(item).ifBlank { if (live) text.onAir else "" },
                color = BuroColors.TextSubtle,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun XtreamContentType.label(text: DesktopStrings): String =
    when (this) {
        XtreamContentType.LIVE -> text.live
        XtreamContentType.MOVIE -> text.movies
        XtreamContentType.SERIES -> text.series
    }
@Composable
internal fun XtreamInternalDetailsPage(
    appState: DesktopAppState,
    onBack: () -> Unit,
    onOpenExternal: (PendingXtreamExternal) -> Unit,
    onOpenPerson: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val item = appState.selectedXtreamItem ?: return
    val movie = appState.movieDetailsStatus as? MovieDetailsStatus.Loaded
    val series = appState.seriesDetailsStatus as? SeriesDetailsStatus.Loaded
    val backdrop = movie?.details?.backdropUrls?.firstOrNull()
        ?: movie?.details?.artworkUrl
        ?: series?.details?.backdropUrls?.firstOrNull()
        ?: series?.details?.artworkUrl
        ?: item.artworkUrl
    Box(Modifier.fillMaxSize().background(BuroColors.Canvas)) {
        BuroRemoteArtwork(
            artworkUrl = backdrop,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        ) { Box(Modifier.fillMaxSize().background(BuroColors.Canvas)) }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(BuroColors.Canvas.copy(alpha = 0.3f), BuroColors.Canvas.copy(alpha = 0.82f), BuroColors.Canvas),
                ),
            ),
        )
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(62.dp).background(BuroColors.Canvas.copy(alpha = 0.86f)).padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) {
                    Text("←  ${strings.backToCatalog}", color = BuroColors.Text)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "IPTV BURO",
                    color = BuroColors.Primary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            XtreamItemDetail(
                item = item,
                movieStatus = appState.movieDetailsStatus,
                seriesStatus = appState.seriesDetailsStatus,
                liveEpgStatus = appState.liveEpgStatus,
                onLoadMovie = { scope.launch { appState.loadSelectedMovieDetails() } },
                onLoadSeries = { scope.launch { appState.loadSelectedSeriesDetails() } },
                onOpenTrailer = appState::openPublicTrailer,
                onOpenExternal = onOpenExternal,
                onOpenPerson = onOpenPerson,
                isFavorite = appState.isFavorite(item),
                onToggleFavorite = { appState.toggleFavorite(item) },
                resumeDecisionFor = appState::resumeDecision,
                compact = false,
                modifier = Modifier.weight(1f).widthIn(max = 980.dp).align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
internal fun XtreamItemDetail(
    item: XtreamCatalogItem?,
    movieStatus: MovieDetailsStatus,
    seriesStatus: SeriesDetailsStatus,
    liveEpgStatus: LiveEpgStatus,
    onLoadMovie: () -> Unit,
    onLoadSeries: () -> Unit,
    onOpenTrailer: (String) -> Unit,
    onOpenExternal: (PendingXtreamExternal) -> Unit,
    onOpenPerson: (String) -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    resumeDecisionFor: (XtreamPlaybackTarget) -> ResumeDecision,
    compact: Boolean,
    modifier: Modifier,
) {
    val text = strings
    Box(
        modifier = modifier.fillMaxHeight().padding(if (compact) BuroSpacing.Md else BuroSpacing.Lg),
    ) {
        if (item == null) {
            Text(
                text = text.selectItem,
                color = BuroColors.TextSubtle,
                modifier = Modifier.align(Alignment.Center),
            )
            return@Box
        }
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
            // Left-aligned. The previous centred card put the poster, the title and every action
            // on the vertical axis, which reads as a dialog rather than a page about a title.
            horizontalAlignment = Alignment.Start,
        ) {
            val richArtwork =
                when {
                    movieStatus is MovieDetailsStatus.Loaded ->
                        movieStatus.details.backdropUrls.firstOrNull()
                            ?: movieStatus.details.artworkUrl
                    seriesStatus is SeriesDetailsStatus.Loaded ->
                        seriesStatus.details.backdropUrls.firstOrNull()
                            ?: seriesStatus.details.artworkUrl
                    else -> null
                }
            val posterUrl = item.artworkUrl ?: richArtwork

            // Poster beside the copy on a wide window, stacked when compact. The GDD's "decisão
            // rápida" list has to be readable before any scrolling happens.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                if (!compact) {
                    BuroRemoteArtwork(
                        artworkUrl = posterUrl,
                        contentDescription = item.name,
                        modifier =
                            Modifier
                                .width(208.dp)
                                .aspectRatio(2f / 3f)
                                .clip(BuroRadius.Large)
                                .background(BuroColors.SurfaceRaised),
                        contentScale = ContentScale.Crop,
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            XtreamMonogram(item.name, 86)
                        }
                    }
                    Spacer(Modifier.width(BuroSpacing.Lg))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.name,
                        color = BuroColors.Text,
                        style =
                            if (compact) {
                                MaterialTheme.typography.headlineSmall
                            } else {
                                MaterialTheme.typography.displaySmall
                            },
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(BuroSpacing.Xs))
                    Text(
                        itemMetadata(item).ifBlank { item.contentType.label(text) },
                        color = BuroColors.Accent,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(BuroSpacing.Md))
            if (item.contentType == XtreamContentType.SERIES) {
                SeriesDetailContent(
                    status = seriesStatus,
                    onLoadSeries = onLoadSeries,
                    onOpenTrailer = onOpenTrailer,
                    resumeDecisionForEpisode = { episode ->
                        resumeDecisionFor(
                            XtreamPlaybackTarget.Episode(seriesId = item.providerId, episode = episode),
                        )
                    },
                    onOpenEpisode = { episode, startPositionMillis ->
                        val target = XtreamPlaybackTarget.Episode(
                            seriesId = item.providerId,
                            episode = episode,
                        )
                        onOpenExternal(
                            PendingXtreamExternal(
                                displayName = episode.title,
                                target = target,
                                startPositionMillis = startPositionMillis,
                            ),
                        )
                    },
                )
            } else {
                val mediaTarget = XtreamPlaybackTarget.CatalogItem(
                    providerId = item.providerId,
                    contentType = item.contentType,
                    containerExtension = item.containerExtension,
                )
                // Actions sit on one line at their natural width. Full-width stacked buttons made
                // a page about a film look like a settings form.
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Sm),
                    verticalArrangement = Arrangement.spacedBy(BuroSpacing.Xs),
                ) {
                    Button(
                        onClick = {
                            onOpenExternal(
                                PendingXtreamExternal(
                                    displayName = item.name,
                                    target = mediaTarget,
                                    startPositionMillis =
                                        resumeStartPosition(resumeDecisionFor(mediaTarget)),
                                ),
                            )
                        },
                        modifier = Modifier.height(48.dp),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = BuroColors.Primary,
                                contentColor = BuroColors.OnPrimary,
                            ),
                        shape = BuroRadius.Small,
                        contentPadding = PaddingValues(horizontal = BuroSpacing.Lg),
                    ) {
                        Text(
                            "▶  ${playbackButtonLabel(resumeDecisionFor(mediaTarget))}",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    if (resumeDecisionFor(mediaTarget) is ResumeDecision.ResumeFrom) {
                        OutlinedButton(
                            onClick = {
                                onOpenExternal(
                                    PendingXtreamExternal(
                                        item.name,
                                        mediaTarget,
                                        startPositionMillis = 0L,
                                    ),
                                )
                            },
                            modifier = Modifier.height(48.dp),
                            shape = BuroRadius.Small,
                            colors =
                                ButtonDefaults.outlinedButtonColors(
                                    contentColor = BuroColors.Text,
                                ),
                        ) { Text("Assistir do início") }
                    }
                    if (item.contentType == XtreamContentType.MOVIE) {
                        OutlinedButton(
                            onClick = onToggleFavorite,
                            modifier = Modifier.height(48.dp),
                            shape = BuroRadius.Small,
                            colors =
                                ButtonDefaults.outlinedButtonColors(
                                    contentColor =
                                        if (isFavorite) BuroColors.Primary else BuroColors.Text,
                                ),
                        ) {
                            Text(
                                if (isFavorite) "♥  Nos favoritos" else "♡  Favoritos",
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(BuroSpacing.Lg))
                if (item.contentType == XtreamContentType.LIVE) {
                    LiveEpgContent(liveEpgStatus)
                    Spacer(Modifier.height(18.dp))
                }
                if (item.contentType == XtreamContentType.MOVIE) {
                    MovieDetailContent(
                        status = movieStatus,
                        onRetry = onLoadMovie,
                        onOpenTrailer = onOpenTrailer,
                        onOpenPerson = onOpenPerson,
                    )
                    Spacer(Modifier.height(18.dp))
                }
            }
            Spacer(Modifier.height(BuroSpacing.Sm))
            Text(
                "Player VLC · H.264, H.265/HEVC, AAC, MP4, MKV, HLS",
                color = BuroColors.TextSubtle,
                style = MaterialTheme.typography.bodySmall,
            )
                }
            }
        }
    }
}

@Composable
private fun LiveEpgContent(status: LiveEpgStatus) {
    when (status) {
        LiveEpgStatus.Idle,
        LiveEpgStatus.Loading,
        -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(color = BuroColors.Primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text("Carregando agora e próximo…", color = BuroColors.TextMuted)
        }
        LiveEpgStatus.Unavailable ->
            Text("Guia indisponível; o canal continua acessível.", color = BuroColors.TextSubtle)
        is LiveEpgStatus.Loaded -> {
            if (status.now == null && status.next == null) {
                Text("Sem programação informada pela fonte.", color = BuroColors.TextSubtle)
            } else {
                status.now?.let { program ->
                    Text("AGORA", color = BuroColors.Primary, fontWeight = FontWeight.Black)
                    Text(program.title, color = BuroColors.Text, style = MaterialTheme.typography.titleMedium)
                    program.description?.let { description ->
                        Text(description, color = BuroColors.TextMuted, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
                status.next?.let { program ->
                    Spacer(Modifier.height(12.dp))
                    Text("A SEGUIR", color = BuroColors.TextSubtle, fontWeight = FontWeight.Bold)
                    Text(program.title, color = BuroColors.Text, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun MovieDetailContent(
    status: MovieDetailsStatus,
    onRetry: () -> Unit,
    onOpenTrailer: (String) -> Unit,
    onOpenPerson: (String) -> Unit,
) {
    when (status) {
        MovieDetailsStatus.Idle,
        MovieDetailsStatus.Loading,
        -> {
            CircularProgressIndicator(color = BuroColors.Primary, modifier = Modifier.size(30.dp))
            Spacer(Modifier.height(8.dp))
            Text("Carregando ficha do filme…", color = BuroColors.TextMuted)
        }
        is MovieDetailsStatus.Error -> {
            Text(status.message, color = BuroColors.Error)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onRetry) { Text("Tentar novamente") }
        }
        is MovieDetailsStatus.Loaded -> {
            val details = status.details
            val facts =
                listOfNotNull(
                    details.releaseDate?.let { "Lançamento $it" },
                    details.duration,
                    details.genre,
                    details.country,
                    details.rating?.let { "★ ${"%.1f".format(it)}" },
                )
            if (facts.isNotEmpty()) {
                Text(
                    facts.joinToString("  •  "),
                    color = BuroColors.TextMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
            }
            details.plot?.let {
                Text(it, color = BuroColors.Text, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(14.dp))
            }
            details.director?.let { DetailLine("Direção", it) }
            details.cast?.let { CastButtons(it, onOpenPerson) }
            details.youtubeTrailerId?.let { trailerId ->
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { onOpenTrailer(trailerId) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Assistir ao trailer")
                }
            }
        }
    }
}

@Composable
private fun DetailLine(
    label: String,
    value: String,
) {
    Text(
        "$label  •  $value",
        color = BuroColors.TextMuted,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
    )
}

@Composable
private fun CastButtons(
    rawCast: String,
    onOpenPerson: (String) -> Unit,
) {
    val people =
        remember(rawCast) {
            rawCast
                .split(',', ';', '|')
                .map(String::trim)
                .filter { it.length in 2..100 }
                .distinctBy { name -> name.lowercase() }
                .take(12)
        }
    if (people.isEmpty()) return
    Text(
        "Elenco",
        color = BuroColors.TextMuted,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 5.dp),
    )
    people.chunked(2).forEach { rowPeople ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            rowPeople.forEach { person ->
                OutlinedButton(
                    onClick = { onOpenPerson(person) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(person, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (rowPeople.size == 1) Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(5.dp))
    }
}

@Composable
internal fun PersonFilmographyPage(
    person: PersonFilmography,
    onBack: () -> Unit,
    onOpenItem: (XtreamCatalogItem) -> Unit,
) {
    Column(Modifier.fillMaxSize().background(BuroColors.Canvas)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).background(BuroColors.Surface).padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("← Voltar aos detalhes", color = BuroColors.Text) }
            Spacer(Modifier.weight(1f))
            Text("ELENCO", color = BuroColors.Primary, fontWeight = FontWeight.Black)
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(44.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(92.dp).clip(CircleShape).background(BuroColors.Primary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(person.name.take(2).uppercase(), color = BuroColors.Primary, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.width(20.dp))
                Column {
                    Text(person.name, color = BuroColors.Text, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Foto indisponível na fonte · filmografia confirmada nesta sessão", color = BuroColors.TextMuted)
                }
            }
            Spacer(Modifier.height(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Filmografia confirmada enquanto você navega nesta fonte.",
                    color = BuroColors.TextMuted,
                )
                if (person.items.isEmpty()) {
                    Text("Nenhum outro título confirmado nesta sessão.")
                } else {
                    person.items.take(20).forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onOpenItem(item) }.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BuroRemoteArtwork(
                                artworkUrl = item.artworkUrl,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp, 72.dp).clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop,
                            ) {
                                XtreamMonogram(item.name, 30)
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                item.name,
                                modifier = Modifier.weight(1f),
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
private fun SeriesDetailContent(
    status: SeriesDetailsStatus,
    onLoadSeries: () -> Unit,
    onOpenTrailer: (String) -> Unit,
    resumeDecisionForEpisode: (XtreamEpisode) -> ResumeDecision,
    onOpenEpisode: (XtreamEpisode, Long) -> Unit,
) {
    when (status) {
        SeriesDetailsStatus.Idle -> {
            Button(
                onClick = onLoadSeries,
                modifier = Modifier.fillMaxWidth(),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = BuroColors.Primary,
                        contentColor = BuroColors.OnPrimary,
                    ),
            ) {
                Text("Carregar episódios", fontWeight = FontWeight.Bold)
            }
        }
        SeriesDetailsStatus.Loading -> {
            CircularProgressIndicator(color = BuroColors.Primary, modifier = Modifier.size(30.dp))
            Spacer(Modifier.height(8.dp))
            Text("Carregando episódios…", color = BuroColors.TextMuted)
        }
        is SeriesDetailsStatus.Error -> {
            Text(status.message, color = BuroColors.Error)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onLoadSeries) {
                Text("Tentar novamente")
            }
        }
        is SeriesDetailsStatus.Loaded -> {
            val details = status.details
            val episodes = details.episodes
            val facts =
                listOfNotNull(
                    details.releaseDate,
                    details.genre,
                    details.rating?.let { "★ ${"%.1f".format(it)}" },
                )
            if (facts.isNotEmpty()) {
                Text(
                    facts.joinToString("  •  "),
                    color = BuroColors.TextMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(10.dp))
            }
            details.plot?.let {
                Text(it, color = BuroColors.Text, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
            }
            details.director?.let { DetailLine("Direção", it) }
            details.cast?.let { DetailLine("Elenco", it) }
            details.youtubeTrailerId?.let { trailerId ->
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { onOpenTrailer(trailerId) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Assistir ao trailer")
                }
                Spacer(Modifier.height(10.dp))
            }
            Text(
                "${episodes.size} episódios",
                color = BuroColors.TextMuted,
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.height(8.dp))
            if (episodes.isEmpty()) {
                Text(
                    "O servidor não retornou episódios reproduzíveis.",
                    color = BuroColors.TextSubtle,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    items(episodes, key = XtreamEpisode::providerId) { episode ->
                        val decision = resumeDecisionForEpisode(episode)
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(BuroColors.SurfaceHover)
                                    .clickable { onOpenEpisode(episode, resumeStartPosition(decision)) }
                                    .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "T${episode.seasonNumber} • E${episode.episodeNumber ?: "—"}",
                                color = BuroColors.Primary,
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Spacer(Modifier.width(9.dp))
                            Text(
                                episode.title,
                                color = BuroColors.Text,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (decision is ResumeDecision.ResumeFrom) {
                                Text(
                                    "Continuar ${formatPlaybackTime(decision.positionMs)}",
                                    color = BuroColors.Primary,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                                Spacer(Modifier.width(8.dp))
                                TextButton(onClick = { onOpenEpisode(episode, 0L) }) {
                                    Text("Do início")
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
private fun XtreamMonogram(
    name: String,
    size: Int,
) {
    val initials =
        name
            .trim()
            .split(Regex("\\s+"))
            .take(2)
            .mapNotNull { it.firstOrNull()?.uppercase() }
            .joinToString("")
            .ifEmpty { "TV" }
    Box(
        modifier =
            Modifier
                .size(size.dp)
                .clip(RoundedCornerShape((size / 3).dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            BuroColors.Accent.copy(alpha = 0.32f),
                            BuroColors.Primary.copy(alpha = 0.2f),
                        ),
                    ),
                ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials,
            color = BuroColors.Text,
            fontWeight = FontWeight.Bold,
            style =
                if (size >= 60) {
                    MaterialTheme.typography.headlineMedium
                } else {
                    MaterialTheme.typography.labelLarge
                },
        )
    }
}

@Composable
private fun XtreamPaneDivider() {
    Box(Modifier.width(1.dp).fillMaxHeight().background(BuroColors.BorderSoft))
}

@Composable
private fun XtreamLoadingOverlay(status: XtreamStatus) {
    Box(
        modifier = Modifier.fillMaxSize().background(BuroColors.Canvas.copy(alpha = 0.78f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(BuroColors.SurfaceRaised)
                    .padding(horizontal = 38.dp, vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(color = BuroColors.Primary, modifier = Modifier.size(34.dp))
            Spacer(Modifier.height(15.dp))
            Text(
                if (status is XtreamStatus.Connecting) {
                    "Autenticando com segurança…"
                } else {
                    "Carregando catálogo sob demanda…"
                },
                color = BuroColors.Text,
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                "Catálogo em memória; conexão lembrada com proteção do Windows.",
                color = BuroColors.TextSubtle,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private fun itemMetadata(item: XtreamCatalogItem): String =
    buildList {
        item.year?.let { add(it.toString()) }
        item.rating?.let { add("★ ${"%.1f".format(it)}") }
        item.containerExtension?.uppercase()?.let(::add)
        if (isEmpty()) {
            add(
                when (item.contentType) {
                    XtreamContentType.LIVE -> "Ao vivo"
                    XtreamContentType.MOVIE -> "Filme"
                    XtreamContentType.SERIES -> "Série"
                },
            )
        }
    }.joinToString("  •  ")

class PendingXtreamExternal(
    val displayName: String,
    val target: XtreamPlaybackTarget,
    val startPositionMillis: Long = 0L,
) {
    override fun toString(): String = "PendingXtreamExternal(<redacted>)"
}

private fun resumeStartPosition(decision: ResumeDecision): Long =
    (decision as? ResumeDecision.ResumeFrom)?.positionMs ?: 0L

private fun playbackButtonLabel(decision: ResumeDecision): String =
    when (decision) {
        is ResumeDecision.ResumeFrom -> "Continuar de ${formatPlaybackTime(decision.positionMs)}"
        ResumeDecision.WatchAgain -> "Assistir novamente"
        ResumeDecision.StartFromBeginning -> "Assistir no IPTV BURO"
    }

private fun formatPlaybackTime(positionMs: Long): String {
    val totalSeconds = positionMs.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
}

private const val SEARCH_DEBOUNCE_MILLIS = 280L

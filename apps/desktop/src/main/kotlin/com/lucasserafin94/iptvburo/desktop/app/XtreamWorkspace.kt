package com.lucasserafin94.iptvburo.desktop.app

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.scale
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.focusable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lucasserafin94.iptvburo.desktop.DesktopAppState
import com.lucasserafin94.iptvburo.desktop.DownloadState
import com.lucasserafin94.iptvburo.desktop.MovieDetailsStatus
import com.lucasserafin94.iptvburo.desktop.LiveEpgStatus
import com.lucasserafin94.iptvburo.desktop.PersonFilmography
import com.lucasserafin94.iptvburo.desktop.SeriesDetailsStatus
import com.lucasserafin94.iptvburo.desktop.XtreamStatus
import com.lucasserafin94.iptvburo.desktop.data.contentIdentity
import com.lucasserafin94.iptvburo.desktop.data.episodeContentKey
import com.lucasserafin94.iptvburo.desktop.model.XtreamPlaybackTarget
import com.lucasserafin94.iptvburo.desktop.ui.CategoryBadge
import com.lucasserafin94.iptvburo.desktop.ui.categoryLabel
import com.lucasserafin94.iptvburo.desktop.CatalogLayout
import com.lucasserafin94.iptvburo.desktop.ui.arrowScrollableVertically
import com.lucasserafin94.iptvburo.desktop.ui.edgeScrollable
import com.lucasserafin94.iptvburo.desktop.ui.edgeScrollableGrid
import com.lucasserafin94.iptvburo.desktop.ui.categoryBadgeFor
import com.lucasserafin94.iptvburo.desktop.ui.BuroColors
import com.lucasserafin94.iptvburo.desktop.ui.BuroInteractiveRow
import com.lucasserafin94.iptvburo.desktop.ui.BuroInteractiveSurface
import com.lucasserafin94.iptvburo.desktop.ui.BuroRadius
import com.lucasserafin94.iptvburo.desktop.ui.BuroRemoteArtwork
import com.lucasserafin94.iptvburo.desktop.ui.BuroScrim
import com.lucasserafin94.iptvburo.desktop.ui.BuroSpacing
import com.lucasserafin94.iptvburo.desktop.ui.DesktopStrings
import com.lucasserafin94.iptvburo.desktop.ui.editorialTitle
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
                personOpen = true
                scope.launch { appState.openPerson(name) }
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
            minimumRating = appState.selectedXtreamMinimumRating,
            onMinimumRatingSelected = { rating ->
                scope.launch { appState.selectXtreamMinimumRating(rating) }
            },
        )
        // Hidden in Favourites: the rail filters the provider's catalogue, and a favourites list is
        // the user's own selection across all of it. Picking a category there could only ever
        // narrow it to nothing.
        if (!appState.favoritesOnly) {
        XtreamCategoryRail(
            categories = appState.xtreamCategories,
            contentType = appState.xtreamContentType,
            selectedCategoryId = appState.selectedXtreamCategoryId,
            onSelected = { categoryId ->
                detailsOpen = false
                scope.launch { appState.selectXtreamCategory(categoryId) }
            },
        )
        }
        HorizontalDivider(color = BuroColors.BorderSoft)
        // Weighted, not fillMaxSize. A Column measures an unweighted child against unbounded
        // height, so fillMaxSize here claimed a full screen's height *below* the toolbar and the
        // category rail: the grid was taller than the space it had, its last row was drawn past the
        // bottom of the window, and the wheel had nothing left to scroll.
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
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
            // No blocking panel while the catalogue loads. It sat over the middle of the screen
            // saying something the user could not act on, and it hid the very catalogue they were
            // waiting for. The header carries the same information as a quiet line instead.
            if (
                appState.xtreamStatus is XtreamStatus.Connecting ||
                appState.xtreamStatus is XtreamStatus.LoadingCatalog
            ) {
                Row(
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = BuroSpacing.Sm)
                            .clip(BuroRadius.Pill)
                            .background(BuroColors.Surface.copy(alpha = 0.92f))
                            .padding(horizontal = BuroSpacing.Md, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        color = BuroColors.Primary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(BuroSpacing.Sm))
                    Text(
                        text = text.loadingCatalog,
                        color = BuroColors.TextMuted,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
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
    minimumRating: Double?,
    onMinimumRatingSelected: (Double?) -> Unit,
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
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selectedType != XtreamContentType.LIVE) {
                val currentYear = Year.now().value
                Row(horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Xs)) {
                    FilterChip(
                        label = text.allYears,
                        selected = selectedYear == null,
                        onClick = { onYearSelected(null) },
                    )
                    FilterChip(
                        label = "${text.releasesIn} $currentYear",
                        selected = selectedYear == currentYear,
                        onClick = { onYearSelected(currentYear) },
                    )
                    // A picker rather than one chip per year: a catalogue spans decades, and a row
                    // of chips would either cover a handful of years arbitrarily or run off screen.
                    YearPicker(
                        selectedYear = selectedYear?.takeIf { it != currentYear },
                        currentYear = currentYear,
                        label = text.chooseYear,
                        onSelect = onYearSelected,
                    )
                    // Whole-star thresholds rather than a slider: the provider's ratings are coarse
                    // and "at least four stars" is the question people actually ask.
                    RatingPicker(
                        selected = minimumRating,
                        label = text.chooseRating,
                        anyLabel = text.anyRating,
                        onSelect = onMinimumRatingSelected,
                    )
                }
                Spacer(Modifier.width(BuroSpacing.Md))
            }
            Spacer(Modifier.weight(1f))
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
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun XtreamCategoryRail(
    categories: List<XtreamCategory>,
    contentType: XtreamContentType,
    selectedCategoryId: String?,
    onSelected: (String?) -> Unit,
) {
    val text = strings
    val listState = rememberLazyListState()
    val railFocus = remember { FocusRequester() }
    val railScope = rememberCoroutineScope()

    // Jump back to the start when the content type changes, otherwise the rail keeps the scroll
    // offset of a category list that no longer exists.
    LaunchedEffect(contentType) { listState.scrollToItem(0) }

    // The rail scrolls sideways but showed no sign of it, so the categories past the right edge -
    // and there are usually many - looked as though they did not exist. A scrollbar underneath is
    // both the indication and a way to drag through them.
    Column(modifier = Modifier.fillMaxWidth()) {
    LazyRow(
        state = listState,
        // Left and right arrows move the rail, and hovering is enough: the pointer entering it
        // takes focus, so there is no click needed before the keys do anything.
        modifier =
            Modifier
                .fillMaxWidth()
                .focusRequester(railFocus)
                .focusable()
                .edgeScrollable(listState)
                .onPointerEvent(PointerEventType.Enter) { runCatching { railFocus.requestFocus() } }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val delta =
                        when (event.key) {
                            Key.DirectionRight -> CHIP_SCROLL_PIXELS
                            Key.DirectionLeft -> -CHIP_SCROLL_PIXELS
                            else -> return@onPreviewKeyEvent false
                        }
                    railScope.launch { listState.animateScrollBy(delta) }
                    true
                },
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
                badge = categoryBadgeFor("", contentType),
                selected = selectedCategoryId == null,
                onClick = { onSelected(null) },
            )
        }
        items(categories, key = XtreamCategory::providerId) { category ->
            // The section prefix is dropped from both the label and the badge lookup: with it, every
            // category under Films matched the "filme" rule and got the same clapperboard.
            val label = category.name.categoryLabel()
            XtreamCategoryChip(
                label = label,
                badge = categoryBadgeFor(label, contentType),
                selected = category.providerId == selectedCategoryId,
                onClick = { onSelected(category.providerId) },
            )
        }
    }
    // Explicit colours. The default scrollbar is nearly transparent, which on this near-black
    // surface made it invisible: the rail scrolled, but nothing on screen said so and there was
    // no visible handle to drag, so the categories past the right edge looked unreachable.
    HorizontalScrollbar(
        adapter = rememberScrollbarAdapter(listState),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = BuroSpacing.GutterCompact, vertical = 2.dp),
        style =
            LocalScrollbarStyle.current.copy(
                thickness = 8.dp,
                unhoverColor = BuroColors.BorderSoft,
                hoverColor = BuroColors.Primary,
            ),
    )
    }
}

@Composable
private fun XtreamCategoryChip(
    label: String,
    badge: CategoryBadge,
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
                        // Symmetric now that the leading badge is gone. The 4dp start against 14dp
                        // end was room for an icon that no longer exists, so every label sat left
                        // of centre in its pill.
                    ).padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Just the name. The emoji came from the system font and clashed with everything around
            // it; the dot that replaced them was decoration standing in for information that the
            // word already carries.
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


// ---------------------------------------------------------------------------------------------
// Grid
// ---------------------------------------------------------------------------------------------

/** One row of cards, near enough. Arrow keys should move by a row, not by a pixel. */
private const val ROW_SCROLL_PIXELS = 320f

/** Roughly three chips, so a press makes visible progress along the rail. */
private const val CHIP_SCROLL_PIXELS = 400f

@OptIn(ExperimentalComposeUiApi::class)
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
    val gridFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    // Focus goes to the grid when the page opens, so the arrow keys work without a click first.
    LaunchedEffect(Unit) { runCatching { gridFocus.requestFocus() } }

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
            Spacer(Modifier.width(BuroSpacing.Md))
            // One picker rather than three pills. The layout is chosen once and then forgotten, so
            // it does not earn three permanent slots beside the title.
            LayoutPicker(
                selected = appState.catalogLayout,
                text = text,
                onSelect = appState::selectCatalogLayout,
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
            // A visible scrollbar, because the wheel alone gave no sign that there was anything
            // below: a half-drawn row at the bottom edge is indistinguishable from a grid that
            // refuses to scroll, and the user had no way to tell which they were looking at.
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyVerticalGrid(
                state = gridState,
                // Focusable so the arrow keys reach it. Without focus the key events go nowhere and
                // the grid answers only to the wheel.
                modifier =
                    Modifier
                        .fillMaxSize()
                        .focusRequester(gridFocus)
                        .focusable()
                        .onPointerEvent(PointerEventType.Enter) {
                            runCatching { gridFocus.requestFocus() }
                        }.edgeScrollableGrid(gridState)
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            val rowStep = ROW_SCROLL_PIXELS
                            val delta =
                                when (event.key) {
                                    Key.DirectionDown -> rowStep
                                    Key.DirectionUp -> -rowStep
                                    Key.PageDown -> rowStep * 3
                                    Key.PageUp -> -rowStep * 3
                                    // Left and right change page. With 375 pages, reaching for the
                                    // buttons at the bottom for every one of them is the wrong ask.
                                    Key.DirectionRight -> {
                                        if (page.hasNext) onNextPage()
                                        return@onPreviewKeyEvent page.hasNext
                                    }
                                    Key.DirectionLeft -> {
                                        if (page.hasPrevious) onPreviousPage()
                                        return@onPreviewKeyEvent page.hasPrevious
                                    }
                                    else -> return@onPreviewKeyEvent false
                                }
                            scope.launch { gridState.animateScrollBy(delta) }
                            true
                        },
                // The layout the user picked decides the column width, and the list mode asks for a
                // single very wide column so each title gets a full row.
                columns =
                    GridCells.Adaptive(
                        minSize =
                            when (appState.catalogLayout) {
                                CatalogLayout.POSTER -> if (live) 250.dp else 172.dp
                                CatalogLayout.COMPACT -> if (live) 180.dp else 124.dp
                                CatalogLayout.LIST -> 460.dp
                            },
                    ),
                // Generous bottom padding so the final row clears the pagination bar. With only
                // the symmetric vertical padding the last row sat flush against it and read as
                // cut off, which is indistinguishable from the grid failing to scroll.
                contentPadding =
                    PaddingValues(
                        start = gutter,
                        end = gutter,
                        top = BuroSpacing.Sm,
                        bottom = BuroSpacing.Xxl,
                    ),
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
                        layout = appState.catalogLayout,
                    )
                }
            }
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(gridState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 4.dp),
                // The default is nearly transparent and vanishes against this canvas.
                style =
                    LocalScrollbarStyle.current.copy(
                        thickness = 10.dp,
                        unhoverColor = BuroColors.BorderSoft,
                        hoverColor = BuroColors.Primary,
                    ),
            )
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
    layout: CatalogLayout = CatalogLayout.POSTER,
) {
    val live = item.contentType == XtreamContentType.LIVE
    val title = item.name.editorialTitle()

    // The list mode is a different shape, not a smaller card: artwork beside the name rather than
    // above it, so the title gets the width that makes a list worth choosing.
    if (layout == CatalogLayout.LIST) {
        BuroInteractiveSurface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = BuroRadius.Medium,
            background = BuroColors.Surface,
            contentDescription = title,
            ringColor = if (selected) BuroColors.Primary else BuroColors.Focus,
        ) { _ ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(BuroSpacing.Sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BuroRemoteArtwork(
                    artworkUrl = item.artworkUrl,
                    contentDescription = null,
                    modifier =
                        Modifier
                            .width(if (live) 72.dp else 44.dp)
                            .aspectRatio(if (live) 16f / 9f else 2f / 3f)
                            .clip(BuroRadius.Small)
                            .background(BuroColors.SurfaceRaised),
                    contentScale = if (live) ContentScale.Fit else ContentScale.Crop,
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        XtreamMonogram(title, 24)
                    }
                }
                Spacer(Modifier.width(BuroSpacing.Md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = if (selected) BuroColors.Primary else BuroColors.Text,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val facts =
                        listOfNotNull(
                            item.year?.toString(),
                            item.rating?.takeIf { it > 0.0 }?.let { "★ %.1f".format(it) },
                        ).joinToString("  ·  ")
                    if (facts.isNotBlank()) {
                        Text(
                            text = facts,
                            color = BuroColors.TextSubtle,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        return
    }

    BuroInteractiveSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = BuroRadius.Medium,
        compact = !live,
        contentDescription = title,
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
                        XtreamMonogram(title, if (live) 52 else 62)
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
                text = title,
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
    castPhotoFor: (String) -> String? = { null },
    onRequestCastPhoto: suspend (String) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    // Which trailer is open, if any. Held here so the panel closes when the page does.
    var openTrailerId by remember { mutableStateOf<String?>(null) }
    val item = appState.selectedXtreamItem ?: return
    val movie = appState.movieDetailsStatus as? MovieDetailsStatus.Loaded
    val series = appState.seriesDetailsStatus as? SeriesDetailsStatus.Loaded
    // Only VOD is downloadable. A live stream has no end, so a download would grow until the
    // disk fills.
    val downloadTarget =
        if (item.contentType == XtreamContentType.MOVIE) {
            XtreamPlaybackTarget.CatalogItem(
                providerId = item.providerId,
                contentType = item.contentType,
                containerExtension = item.containerExtension,
                contentKey = item.contentIdentity().key,
            )
        } else {
            null
        }
    val backdrop = movie?.details?.backdropUrls?.firstOrNull()
        ?: movie?.details?.artworkUrl
        ?: series?.details?.backdropUrls?.firstOrNull()
        ?: series?.details?.artworkUrl
        ?: item.artworkUrl
    Box(Modifier.fillMaxSize().background(BuroColors.Canvas)) {
        // Slow Ken Burns drift on the backdrop. The GDD asks for a living detail page; a muted
        // trailer needs a trailer field the provider rarely supplies, and a static backdrop would
        // sit dead behind the copy. A very slow scale reads as depth without competing for
        // attention, and it costs one animated float.
        val drift = rememberInfiniteTransition(label = "backdrop-drift")
        val backdropScale by drift.animateFloat(
            initialValue = 1f,
            targetValue = 1.08f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(22_000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "backdrop-scale",
        )
        // The scale has to be clipped to the page. Scaling an un-clipped composable draws outside
        // its own bounds, so the backdrop grew over the sidebar and the header instead of drifting
        // within its frame.
        Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
            BuroRemoteArtwork(
                artworkUrl = backdrop,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().scale(backdropScale),
                contentScale = ContentScale.Crop,
            ) { Box(Modifier.fillMaxSize().background(BuroColors.Canvas)) }
        }
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
                castPhotoFor = appState::castPhotoFor,
                onRequestCastPhoto = appState::ensureCastPhoto,
                movieStatus = appState.movieDetailsStatus,
                seriesStatus = appState.seriesDetailsStatus,
                liveEpgStatus = appState.liveEpgStatus,
                onLoadMovie = { scope.launch { appState.loadSelectedMovieDetails() } },
                onLoadSeries = { scope.launch { appState.loadSelectedSeriesDetails() } },
                onOpenTrailer = { trailerId -> openTrailerId = trailerId },
                onOpenExternal = onOpenExternal,
                onOpenPerson = onOpenPerson,
                isFavorite = appState.isFavorite(item),
                onToggleFavorite = { appState.toggleFavorite(item) },
                resumeDecisionFor = appState::resumeDecision,
                compact = false,
                modifier = Modifier.weight(1f).widthIn(max = 1_040.dp).align(Alignment.CenterHorizontally),
                downloadState = downloadTarget?.let { appState.downloadState(it.contentKey) },
                onDownload =
                    downloadTarget?.let { target ->
                        {
                            appState.enqueueDownload(
                                target = target,
                                title = item.name.editorialTitle(),
                                artworkUrl = item.artworkUrl,
                            )
                        }
                    },
                onCancelDownload =
                    downloadTarget?.let { target -> { appState.cancelDownload(target.contentKey) } },
                onRemoveDownload =
                    downloadTarget?.let { target -> { appState.deleteDownload(target.contentKey) } },
                episodeDownloadFor = { target -> appState.downloadState(target.contentKey) },
                onDownloadEpisode = { target, displayName ->
                    appState.enqueueDownload(
                        target = target,
                        title = displayName,
                        // Episode stills are often missing; the series poster is the sensible
                        // fallback so the library never shows a blank tile.
                        artworkUrl = target.episode.artworkUrl ?: item.artworkUrl,
                    )
                },
                onCancelEpisodeDownload = { target -> appState.cancelDownload(target.contentKey) },
                onRemoveEpisodeDownload = { target -> appState.deleteDownload(target.contentKey) },
            )
        }

        // Drawn last so it sits over the page. Without this the panel was written, compiled and
        // never rendered - the button set a value nothing read.
        openTrailerId?.let { trailerId ->
            TrailerOverlay(
                youtubeId = trailerId,
                title = item.name.editorialTitle(),
                onClose = { openTrailerId = null },
                // Chromium missing on this machine is not a reason to deny the trailer entirely.
                onFallback = { appState.openPublicTrailer(trailerId) },
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
    castPhotoFor: (String) -> String? = { null },
    onRequestCastPhoto: suspend (String) -> Unit = {},
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    resumeDecisionFor: (XtreamPlaybackTarget) -> ResumeDecision,
    compact: Boolean,
    modifier: Modifier,
    downloadState: DownloadState? = null,
    onDownload: (() -> Unit)? = null,
    onCancelDownload: (() -> Unit)? = null,
    onRemoveDownload: (() -> Unit)? = null,
    episodeDownloadFor: (XtreamPlaybackTarget.Episode) -> DownloadState? = { null },
    onDownloadEpisode: (XtreamPlaybackTarget.Episode, String) -> Unit = { _, _ -> },
    onCancelEpisodeDownload: (XtreamPlaybackTarget.Episode) -> Unit = {},
    onRemoveEpisodeDownload: (XtreamPlaybackTarget.Episode) -> Unit = {},
) {
    val text = strings
    Box(
        // No fillMaxHeight: the caller already weights this panel, and asking for the full height
        // again on top of that is what let the column below run past the window with the last
        // episodes drawn where no scroll could reach them.
        modifier = modifier.padding(if (compact) BuroSpacing.Md else BuroSpacing.Lg),
    ) {
        if (item == null) {
            Text(
                text = text.selectItem,
                color = BuroColors.TextSubtle,
                modifier = Modifier.align(Alignment.Center),
            )
            return@Box
        }
        val detailScroll = rememberScrollState()
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    // No fillMaxHeight before verticalScroll. Forcing the column to the viewport
                    // height and then making it scrollable pins it at that height, so anything
                    // below - the last episodes, the cast - is laid out past the bottom edge and
                    // the scroll never reaches it. A scrolling column must be free to be taller
                    // than what is visible; that is what gives it something to scroll.
                    .verticalScroll(detailScroll)
                    .arrowScrollableVertically(detailScroll),
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
                // One place builds the playback target for an episode, so playback, resume and
                // download all agree on the content key that identifies it.
                val targetForEpisode = { episode: XtreamEpisode ->
                    XtreamPlaybackTarget.Episode(
                        seriesId = item.providerId,
                        episode = episode,
                        contentKey = item.episodeContentKey(episode),
                    )
                }
                SeriesDetailContent(
                    status = seriesStatus,
                    onOpenPerson = onOpenPerson,
                    castPhotoFor = castPhotoFor,
                    onRequestCastPhoto = onRequestCastPhoto,
                    seriesTitle = item.name,
                    isFavorite = isFavorite,
                    onToggleFavorite = onToggleFavorite,
                    onLoadSeries = onLoadSeries,
                    onOpenTrailer = onOpenTrailer,
                    resumeDecisionForEpisode = { episode ->
                        resumeDecisionFor(targetForEpisode(episode))
                    },
                    onOpenEpisode = { episode, startPositionMillis ->
                        onOpenExternal(
                            PendingXtreamExternal(
                                displayName = episode.title,
                                target = targetForEpisode(episode),
                                startPositionMillis = startPositionMillis,
                            ),
                        )
                    },
                    downloadStateForEpisode = { episode -> episodeDownloadFor(targetForEpisode(episode)) },
                    onDownloadEpisode = { episode ->
                        // The library lists downloads from every title together, so an entry named
                        // only "S01E03" would be unidentifiable there.
                        onDownloadEpisode(
                            targetForEpisode(episode),
                            "${item.name.editorialTitle()} · T${episode.seasonNumber}" +
                                "E${episode.episodeNumber ?: "—"}",
                        )
                    },
                    onCancelEpisodeDownload = { episode -> onCancelEpisodeDownload(targetForEpisode(episode)) },
                    onRemoveEpisodeDownload = { episode -> onRemoveEpisodeDownload(targetForEpisode(episode)) },
                    text = text,
                )
            } else {
                val mediaTarget = XtreamPlaybackTarget.CatalogItem(
                    providerId = item.providerId,
                    contentType = item.contentType,
                    containerExtension = item.containerExtension,
                    contentKey = item.contentIdentity().key,
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
                    if (downloadState != null && onDownload != null) {
                        DownloadButton(
                            state = downloadState,
                            text = text,
                            onDownload = onDownload,
                            onCancel = onCancelDownload ?: {},
                            onRemove = onRemoveDownload ?: {},
                        )
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
                        castPhotoFor = castPhotoFor,
                        onRequestCastPhoto = onRequestCastPhoto,
                    )
                    Spacer(Modifier.height(18.dp))
                }
            }
            // Breathing room at the end of the scroll. Without it the last line sits flush against
            // the window edge and looks truncated rather than finished.
            Spacer(Modifier.height(BuroSpacing.Xxl))
                }
            }
        }
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(detailScroll),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            style =
                LocalScrollbarStyle.current.copy(
                    thickness = 10.dp,
                    unhoverColor = BuroColors.BorderSoft,
                    hoverColor = BuroColors.Primary,
                ),
        )
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
    castPhotoFor: (String) -> String? = { null },
    onRequestCastPhoto: suspend (String) -> Unit = {},
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
            details.cast?.let {
                CastButtons(
                    rawCast = it,
                    onOpenPerson = onOpenPerson,
                    photoFor = castPhotoFor,
                    onRequestPhoto = onRequestCastPhoto,
                )
            }
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
    castPhotoFor: (String) -> String? = { null },
    onRequestCastPhoto: suspend (String) -> Unit = {},
    photoFor: (String) -> String?,
    onRequestPhoto: suspend (String) -> Unit,
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
    // Faces, not a list of buttons. The provider sends only names, so each portrait is resolved
    // once from the metadata service and cached; a name it does not know keeps its initials rather
    // than leaving a hole in the row.
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Md),
        verticalArrangement = Arrangement.spacedBy(BuroSpacing.Sm),
    ) {
        people.forEach { person ->
            LaunchedEffect(person) { onRequestPhoto(person) }
            Column(
                modifier =
                    Modifier
                        .width(84.dp)
                        .clip(BuroRadius.Small)
                        .clickable { onOpenPerson(person) }
                        .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BuroRemoteArtwork(
                    artworkUrl = photoFor(person),
                    contentDescription = person,
                    modifier = Modifier.size(72.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(BuroColors.Primary.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text =
                                person
                                    .split(' ')
                                    .take(2)
                                    .mapNotNull { part -> part.firstOrNull()?.uppercase() }
                                    .joinToString(""),
                            color = BuroColors.Primary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = person,
                    color = BuroColors.TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
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
        // Weighted for the space below the header, then free to grow taller than it: fillMaxSize
        // before verticalScroll would pin the column to the viewport and put the filmography that
        // follows past the bottom edge, out of the scroll's reach.
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(44.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                // The photo comes from the metadata service; the provider sends none. The initials
                // stand in when it is not configured or found nobody by that name.
                BuroRemoteArtwork(
                    artworkUrl = person.photoUrl,
                    contentDescription = person.name,
                    modifier = Modifier.size(120.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop,
                ) {
                    Box(
                        Modifier.fillMaxSize().background(BuroColors.Primary.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            person.name.take(2).uppercase(),
                            color = BuroColors.Primary,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                        )
                    }
                }
                Spacer(Modifier.width(20.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        person.name,
                        color = BuroColors.Text,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    person.biography?.let { biography ->
                        Spacer(Modifier.height(BuroSpacing.Sm))
                        Text(
                            text = biography,
                            color = BuroColors.TextMuted,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // The person's full filmography, which the playlist cannot know: it only carries the
            // titles this provider happens to sell.
            if (person.credits.isNotEmpty()) {
                Spacer(Modifier.height(28.dp))
                Text(
                    "Filmografia",
                    color = BuroColors.Text,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(BuroSpacing.Sm))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Sm),
                    verticalArrangement = Arrangement.spacedBy(BuroSpacing.Sm),
                ) {
                    person.credits.forEach { credit ->
                        Column(modifier = Modifier.width(120.dp)) {
                            BuroRemoteArtwork(
                                artworkUrl = credit.posterUrl,
                                contentDescription = credit.title,
                                modifier =
                                    Modifier
                                        .width(120.dp)
                                        .aspectRatio(2f / 3f)
                                        .clip(BuroRadius.Small)
                                        .background(BuroColors.SurfaceRaised),
                                contentScale = ContentScale.Crop,
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    XtreamMonogram(credit.title, 34)
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = credit.title,
                                color = BuroColors.Text,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text =
                                    listOfNotNull(credit.year?.toString(), credit.character)
                                        .joinToString("  ·  "),
                                color = BuroColors.TextSubtle,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Disponível nesta lista",
                    color = BuroColors.Text,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (person.isLoading && person.items.isEmpty()) {
                    // The sweep costs one provider request per film, so it takes a moment. Saying
                    // so beats an empty page that looks like the person has no other work.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            color = BuroColors.Primary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(BuroSpacing.Sm))
                        Text("Procurando no catálogo…", color = BuroColors.TextMuted)
                    }
                } else if (person.items.isEmpty()) {
                    Text("Nenhum outro título encontrado no catálogo.", color = BuroColors.TextMuted)
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
    onOpenPerson: (String) -> Unit,
    castPhotoFor: (String) -> String?,
    onRequestCastPhoto: suspend (String) -> Unit,
    seriesTitle: String,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onLoadSeries: () -> Unit,
    onOpenTrailer: (String) -> Unit,
    resumeDecisionForEpisode: (XtreamEpisode) -> ResumeDecision,
    onOpenEpisode: (XtreamEpisode, Long) -> Unit,
    downloadStateForEpisode: (XtreamEpisode) -> DownloadState?,
    onDownloadEpisode: (XtreamEpisode) -> Unit,
    onCancelEpisodeDownload: (XtreamEpisode) -> Unit,
    onRemoveEpisodeDownload: (XtreamEpisode) -> Unit,
    text: DesktopStrings,
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
            // Portraits, the same as the film page. A series was showing its cast as one line of
            // comma-separated text, which is the raw field the provider sends rather than anything
            // the user can click.
            details.cast?.let {
                CastButtons(
                    rawCast = it,
                    onOpenPerson = onOpenPerson,
                    photoFor = castPhotoFor,
                    onRequestPhoto = onRequestCastPhoto,
                )
            }

            // Actions sit together above the episode list, matching the film page. Previously the
            // trailer was a full-width button buried between the metadata and the episodes, and
            // there was no way to favourite a series from its own page at all.
            Spacer(Modifier.height(BuroSpacing.Md))
            val firstEpisode = episodes.firstOrNull()
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Sm),
                verticalArrangement = Arrangement.spacedBy(BuroSpacing.Xs),
            ) {
                if (firstEpisode != null) {
                    val resumable = episodes.firstOrNull { resumeDecisionForEpisode(it) is ResumeDecision.ResumeFrom }
                    val target = resumable ?: firstEpisode
                    val decision = resumeDecisionForEpisode(target)
                    Button(
                        onClick = { onOpenEpisode(target, resumeStartPosition(decision)) },
                        modifier = Modifier.height(48.dp),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = BuroColors.Primary,
                                contentColor = BuroColors.OnPrimary,
                            ),
                        shape = BuroRadius.Small,
                        contentPadding = PaddingValues(horizontal = BuroSpacing.Lg),
                    ) {
                        val label =
                            if (decision is ResumeDecision.ResumeFrom) {
                                "▶  Continuar T${target.seasonNumber} E${target.episodeNumber ?: "—"}"
                            } else {
                                "▶  Assistir T${target.seasonNumber} E${target.episodeNumber ?: "—"}"
                            }
                        Text(label, fontWeight = FontWeight.Bold)
                    }
                }
                OutlinedButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.height(48.dp),
                    shape = BuroRadius.Small,
                    colors =
                        ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isFavorite) BuroColors.Primary else BuroColors.Text,
                        ),
                ) {
                    Text(
                        if (isFavorite) "♥  Nos favoritos" else "♡  Favoritos",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                details.youtubeTrailerId?.let { trailerId ->
                    OutlinedButton(
                        onClick = { onOpenTrailer(trailerId) },
                        modifier = Modifier.height(48.dp),
                        shape = BuroRadius.Small,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = BuroColors.Text),
                    ) {
                        Text("Trailer", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(BuroSpacing.Lg))

            if (episodes.isEmpty()) {
                Text(
                    "O servidor não retornou episódios reproduzíveis.",
                    color = BuroColors.TextSubtle,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                // Emitted straight into the parent's scrolling column. A LazyColumn here was given a
                // fixed 220dp height because it cannot measure inside a scrollable parent, which
                // showed five of twenty-four episodes and swallowed the wheel that should have
                // scrolled the page.
                val seasons = episodes.groupBy(XtreamEpisode::seasonNumber).toSortedMap()
                var openSeason by remember(details.providerId) {
                    mutableStateOf(seasons.keys.firstOrNull())
                }
                if (seasons.size > 1) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Xs),
                        verticalArrangement = Arrangement.spacedBy(BuroSpacing.Xs),
                    ) {
                        seasons.forEach { (season, seasonEpisodes) ->
                            val selected = season == openSeason
                            BuroInteractiveSurface(
                                onClick = { openSeason = season },
                                shape = BuroRadius.Pill,
                                background =
                                    if (selected) BuroColors.Primary else BuroColors.SurfaceHover,
                            ) { _ ->
                                Text(
                                    text = "T$season  ·  ${seasonEpisodes.size}",
                                    color = if (selected) BuroColors.OnPrimary else BuroColors.TextMuted,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier =
                                        Modifier.padding(
                                            horizontal = BuroSpacing.Md,
                                            vertical = BuroSpacing.Sm,
                                        ),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(BuroSpacing.Md))
                }
                val visible = seasons[openSeason] ?: episodes
                Text(
                    text =
                        if (seasons.size > 1) {
                            "Temporada $openSeason  ·  ${visible.size} episódios"
                        } else {
                            "${episodes.size} episódios"
                        },
                    color = BuroColors.TextMuted,
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.height(BuroSpacing.Sm))
                visible
                    .sortedBy { it.episodeNumber ?: Int.MAX_VALUE }
                    .forEach { episode ->
                        EpisodeRow(
                            episode = episode,
                            decision = resumeDecisionForEpisode(episode),
                            downloadState = downloadStateForEpisode(episode),
                            onOpen = onOpenEpisode,
                            onDownload = { onDownloadEpisode(episode) },
                            onCancelDownload = { onCancelEpisodeDownload(episode) },
                            onRemoveDownload = { onRemoveEpisodeDownload(episode) },
                            text = text,
                        )
                        Spacer(Modifier.height(BuroSpacing.Xs))
                    }
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: XtreamEpisode,
    decision: ResumeDecision,
    downloadState: DownloadState?,
    onOpen: (XtreamEpisode, Long) -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onRemoveDownload: () -> Unit,
    text: DesktopStrings,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(BuroRadius.Small)
                .background(BuroColors.SurfaceHover)
                .clickable { onOpen(episode, resumeStartPosition(decision)) },
    ) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = BuroSpacing.Md, vertical = BuroSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "E${episode.episodeNumber ?: "—"}",
            color = BuroColors.Primary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(40.dp),
        )
        Text(
            text = episode.title.editorialTitle(),
            color = BuroColors.Text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (decision is ResumeDecision.ResumeFrom) {
            Spacer(Modifier.width(BuroSpacing.Sm))
            Text(
                text = formatPlaybackTime(decision.positionMs),
                color = BuroColors.Primary,
                style = MaterialTheme.typography.labelMedium,
            )
            TextButton(onClick = { onOpen(episode, 0L) }) {
                Text("Do início", style = MaterialTheme.typography.labelMedium)
            }
        } else if (decision is ResumeDecision.WatchAgain) {
            Spacer(Modifier.width(BuroSpacing.Sm))
            Text(
                text = "✓ Visto",
                color = BuroColors.Success,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        if (downloadState != null) {
            Spacer(Modifier.width(BuroSpacing.Xs))
            EpisodeDownloadControl(
                state = downloadState,
                onDownload = onDownload,
                onCancel = onCancelDownload,
                onRemove = onRemoveDownload,
                text = text,
            )
        }
    }
    // A thin bar under the row, the length of what has been watched. Scanning the list, the part-
    // watched episode is the one to carry on from, and remembering which it was is not the user's
    // job.
    if (decision is ResumeDecision.ResumeFrom) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(decision.progressPercent.toFloat().coerceIn(0f, 1f))
                    .height(3.dp)
                    .background(BuroColors.Primary),
        )
    }
    }
}

/**
 * Compact download affordance for a single episode.
 *
 * The full [DownloadButton] carries a word label, which does not fit twenty-four times in a list;
 * this keeps the same four states in an icon-sized control.
 */
@Composable
private fun EpisodeDownloadControl(
    state: DownloadState,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
    text: DesktopStrings,
) {
    when (state) {
        is DownloadState.Running -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    // A negative fraction means the server sent no content length, so there is no
                    // percentage to show — only that it is running.
                    text =
                        if (state.fraction < 0f) {
                            text.downloading
                        } else {
                            "${(state.fraction * 100).toInt()}%"
                        },
                    color = BuroColors.Primary,
                    style = MaterialTheme.typography.labelMedium,
                )
                TextButton(onClick = onCancel) {
                    Text(text.cancel, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        DownloadState.Completed ->
            TextButton(onClick = onRemove) {
                Text(
                    text = text.removeDownload,
                    color = BuroColors.Success,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        else ->
            TextButton(onClick = onDownload) {
                Text(
                    text = text.download,
                    color = BuroColors.TextMuted,
                    style = MaterialTheme.typography.labelMedium,
                )
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

/**
 * Download control.
 *
 * One button that reflects the four states rather than a separate control per state, so the action
 * stays in the same place as it changes meaning.
 */
@Composable
private fun DownloadButton(
    state: DownloadState,
    text: DesktopStrings,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
) {
    val label =
        when (state) {
            DownloadState.Idle -> "↓  ${text.download}"
            is DownloadState.Running ->
                if (state.fraction >= 0f) {
                    "${text.downloadInProgress} ${(state.fraction * 100).toInt()}%"
                } else {
                    "${text.downloadInProgress}…"
                }
            DownloadState.Completed -> "✓  ${text.downloaded}"
            DownloadState.Failed -> text.downloadFailed
        }
    val tint =
        when (state) {
            DownloadState.Completed -> BuroColors.Success
            DownloadState.Failed -> BuroColors.Error
            else -> BuroColors.Text
        }
    OutlinedButton(
        onClick =
            when (state) {
                DownloadState.Idle, DownloadState.Failed -> onDownload
                is DownloadState.Running -> onCancel
                DownloadState.Completed -> onRemove
            },
        modifier = Modifier.height(48.dp),
        shape = BuroRadius.Small,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = tint),
    ) {
        Text(label, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/**
 * Dropdown for picking any release year.
 *
 * The toolbar previously offered only the current year and the one before it, so anything older was
 * unreachable from the filter at all. The list runs back forty years, which covers the vast
 * majority of what providers actually carry, and stays scrollable rather than growing the toolbar.
 */
@Composable
private fun YearPicker(
    selectedYear: Int?,
    currentYear: Int,
    label: String,
    onSelect: (Int?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val active = selectedYear != null

    Box {
        BuroInteractiveRow(
            onClick = { expanded = true },
            selected = active,
            shape = BuroRadius.Pill,
            contentDescription = label,
        ) {
            Box(
                modifier = Modifier
                    .border(
                        width = 1.dp,
                        color =
                            if (active) {
                                BuroColors.Primary.copy(alpha = 0.55f)
                            } else {
                                BuroColors.BorderSoft
                            },
                        shape = BuroRadius.Pill,
                    ).padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text(
                    text = if (active) "$selectedYear  ▾" else "$label  ▾",
                    color = if (active) BuroColors.Primary else BuroColors.TextMuted,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(BuroColors.SurfaceRaised)
                .heightIn(max = 360.dp),
        ) {
            // Excludes the current year: it already has its own chip beside this control.
            ((currentYear - 1) downTo (currentYear - 40)).forEach { year ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = year.toString(),
                            color = if (year == selectedYear) BuroColors.Primary else BuroColors.Text,
                        )
                    },
                    onClick = {
                        onSelect(year)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * Filters the catalogue by the provider's own rating.
 *
 * Whole stars only. The ratings are coarse and often absent, so finer thresholds would suggest a
 * precision the data does not have; a title with no rating at all is excluded once a minimum is
 * asked for, rather than being quietly treated as good enough.
 */
/** The catalogue's shape, as one dropdown labelled with the current choice. */
@Composable
private fun LayoutPicker(
    selected: CatalogLayout,
    text: DesktopStrings,
    onSelect: (CatalogLayout) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = { layout: CatalogLayout ->
        when (layout) {
            CatalogLayout.POSTER -> text.layoutPoster
            CatalogLayout.COMPACT -> text.layoutCompact
            CatalogLayout.LIST -> text.layoutList
        }
    }

    Box {
        BuroInteractiveRow(
            onClick = { expanded = true },
            selected = false,
            shape = BuroRadius.Pill,
            contentDescription = label(selected),
        ) {
            Box(
                modifier =
                    Modifier
                        .border(1.dp, BuroColors.BorderSoft, BuroRadius.Pill)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "${label(selected)}  ▾",
                    color = BuroColors.TextMuted,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(BuroColors.SurfaceRaised),
        ) {
            CatalogLayout.entries.forEach { layout ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = label(layout),
                            color = if (layout == selected) BuroColors.Primary else BuroColors.Text,
                        )
                    },
                    onClick = {
                        onSelect(layout)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun RatingPicker(
    selected: Double?,
    label: String,
    anyLabel: String,
    onSelect: (Double?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val active = selected != null

    Box {
        BuroInteractiveRow(
            onClick = { expanded = true },
            selected = active,
            shape = BuroRadius.Pill,
            contentDescription = label,
        ) {
            Box(
                modifier =
                    Modifier
                        .border(
                            width = 1.dp,
                            color =
                                if (active) {
                                    BuroColors.Primary.copy(alpha = 0.55f)
                                } else {
                                    BuroColors.BorderSoft
                                },
                            shape = BuroRadius.Pill,
                        ).padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text(
                    text =
                        if (active) {
                            "★ ${selected!!.toInt()}+  ▾"
                        } else {
                            "$label  ▾"
                        },
                    color = if (active) BuroColors.Primary else BuroColors.TextMuted,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(BuroColors.SurfaceRaised),
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = anyLabel,
                        color = if (selected == null) BuroColors.Primary else BuroColors.Text,
                    )
                },
                onClick = {
                    onSelect(null)
                    expanded = false
                },
            )
            (5 downTo 1).forEach { stars ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "★ $stars+",
                            color =
                                if (selected?.toInt() == stars) BuroColors.Primary else BuroColors.Text,
                        )
                    },
                    onClick = {
                        onSelect(stars.toDouble())
                        expanded = false
                    },
                )
            }
        }
    }
}

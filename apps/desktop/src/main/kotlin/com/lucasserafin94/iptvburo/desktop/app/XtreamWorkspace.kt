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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.lucasserafin94.iptvburo.desktop.playback.MULTIVIEW_MAX_TILES
import com.lucasserafin94.iptvburo.desktop.ui.rememberRestoredGridState
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.focusable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
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
import com.lucasserafin94.iptvburo.desktop.CreditDestination
import com.lucasserafin94.iptvburo.desktop.DesktopAppState
import com.lucasserafin94.iptvburo.desktop.DownloadState
import com.lucasserafin94.iptvburo.desktop.MovieDetailsStatus
import com.lucasserafin94.iptvburo.desktop.LiveEpgStatus
import com.lucasserafin94.iptvburo.desktop.PersonFilmography
import com.lucasserafin94.iptvburo.desktop.platform.CastReceiver
import com.lucasserafin94.iptvburo.desktop.platform.CastTarget
import com.lucasserafin94.iptvburo.domain.model.CastMessage
import com.lucasserafin94.iptvburo.desktop.CastSendState
import com.lucasserafin94.iptvburo.desktop.SeriesDetailsStatus
import com.lucasserafin94.iptvburo.desktop.XtreamStatus
import com.lucasserafin94.iptvburo.desktop.data.contentIdentity
import com.lucasserafin94.iptvburo.desktop.data.episodeContentKey
import com.lucasserafin94.iptvburo.desktop.model.XtreamPlaybackTarget
import com.lucasserafin94.iptvburo.desktop.platform.DesktopPlatformCapabilities
import com.lucasserafin94.iptvburo.desktop.ui.CategoryBadge
import com.lucasserafin94.iptvburo.desktop.ui.CategoryChoice
import com.lucasserafin94.iptvburo.desktop.ui.LocalProviderLogos
import com.lucasserafin94.iptvburo.desktop.ui.ProviderIdentity
import com.lucasserafin94.iptvburo.desktop.ui.providerIdentityForLabel
import com.lucasserafin94.iptvburo.desktop.ui.withLogoFrom
import com.lucasserafin94.iptvburo.desktop.ui.splitCategories
import com.lucasserafin94.iptvburo.desktop.ui.CriticInkDark
import com.lucasserafin94.iptvburo.desktop.ui.CriticMark
import com.lucasserafin94.iptvburo.desktop.ui.CriticMarkImdb
import com.lucasserafin94.iptvburo.desktop.ui.CriticMarkTomatometer
import com.lucasserafin94.iptvburo.desktop.ui.criticMarkMetascore
import com.lucasserafin94.iptvburo.desktop.ui.categoryLabel
import com.lucasserafin94.iptvburo.desktop.CatalogLayout
import com.lucasserafin94.iptvburo.desktop.PersonCredit
import com.lucasserafin94.iptvburo.desktop.ui.arrowScrollableVertically
import com.lucasserafin94.iptvburo.desktop.ui.edgeScrollable
import com.lucasserafin94.iptvburo.desktop.ui.edgeScrollableGrid
import com.lucasserafin94.iptvburo.desktop.ui.categoryBadgeFor
import com.lucasserafin94.iptvburo.metadata.CriticScores
import com.lucasserafin94.iptvburo.metadata.TmdbAudienceScore
import com.lucasserafin94.iptvburo.desktop.ui.BuroColors
import com.lucasserafin94.iptvburo.desktop.ui.CastStrings
import com.lucasserafin94.iptvburo.desktop.ui.BuroInteractiveRow
import com.lucasserafin94.iptvburo.desktop.ui.BuroInteractiveSurface
import com.lucasserafin94.iptvburo.desktop.ui.BuroRadius
import com.lucasserafin94.iptvburo.desktop.ui.BuroSegmentedControl
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
import com.lucasserafin94.iptvburo.domain.model.TitleShareLink
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Year

@Composable
fun XtreamWorkspace(
    appState: DesktopAppState,
    onOpenExternal: (PendingXtreamExternal) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val capabilities = DesktopPlatformCapabilities.current
    var detailsOpen by remember { mutableStateOf(false) }
    var personOpen by remember { mutableStateOf(false) }

    // Built when the tab has no service categories of its own, which is when the filter is missing.
    //
    // Deliberately not on every tab: Ao vivo files channels by service already, and paying for a TMDb
    // index there would buy a worse answer than the provider's own.
    val index = appState.serviceTitleIndex
    val indexedServices =
        remember(index, appState.providerLogos) {
            index.services.mapNotNull { label ->
                val identity = providerIdentityForLabel(label) ?: return@mapNotNull null
                CategoryChoice(
                    id = label,
                    label = "$label (${index.countFor(label)})",
                    provider = identity.withLogoFrom(appState.providerLogos),
                )
            }
        }
    LaunchedEffect(appState.xtreamContentType, appState.xtreamCategories) {
        // Films only for now. It is the tab that asks the question and the one whose categories
        // answer it least often.
        if (appState.xtreamContentType == XtreamContentType.MOVIE) {
            appState.ensureServiceTitleIndex()
        }
    }

    LaunchedEffect(appState.xtreamSearchQuery, appState.xtreamContentType) {
        delay(SEARCH_DEBOUNCE_MILLIS)
        appState.applyXtreamSearch()
    }

    // Opening a title from elsewhere — the "already in your list" row in Assinaturas — arrives as a
    // request here, because whether the details page is showing is this screen's own state. Without
    // this the user landed on the catalogue grid with the item merely selected.
    LaunchedEffect(appState.pendingDetailsRequest) {
        if (appState.consumePendingDetailsRequest() == null) return@LaunchedEffect
        detailsOpen = true
        // Loaded here as well as by the effect below. Both run in the same composition, and that
        // one captured `detailsOpen` as false — it had not been set yet — so the page opened and
        // then sat on "Carregando ficha do filme…" for ever with nothing in flight.
        when (appState.selectedXtreamItem?.contentType) {
            XtreamContentType.MOVIE -> appState.loadSelectedMovieDetails()
            XtreamContentType.SERIES -> appState.loadSelectedSeriesDetails()
            XtreamContentType.LIVE -> appState.loadSelectedLiveEpg()
            else -> Unit
        }
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
                // Closed here too, for the same reason as on the Home screen: `personOpen` is this
                // screen's own flag and decides whether the filmography is drawn, while
                // `selectedPerson` lives in the app state. Clearing only the second left this branch
                // taken with nothing to show, and the press dropped the user back to the catalogue.
                // Closed here too, for the same reason as on the Home screen: `personOpen` is this
                // screen's own flag and decides whether the filmography is drawn, while
                // `selectedPerson` lives in the app state. Clearing only the second left this branch
                // taken with nothing to show, and the press dropped the user back to the catalogue.
                onOpenCredit = { credit ->
                    // The same three flags as on the Home screen, moved together for the same
                    // reason: selecting a title is not showing it, and clearing the shared state
                    // alone leaves this branch drawing nothing.
                    when (appState.openCredit(credit)) {
                        CreditDestination.PLAYLIST_ITEM -> {
                            personOpen = false
                            detailsOpen = true
                        }
                        CreditDestination.SUBSCRIPTIONS -> personOpen = false
                        CreditDestination.NOWHERE -> Unit
                    }
                },
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
            onBack = {
                detailsOpen = false
                // If this title was opened from somewhere else — Descobrir, for instance — going
                // back means returning there, not landing in a catalogue the user was never
                // looking at. Returns false for a title opened from the catalogue itself, where
                // closing the page is the whole of the action.
                appState.closeOpenedTitle()
            },
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
            multiviewCount = if (capabilities.multiviewSupported) appState.multiviewChannelIds.size else 0,
            onOpenMultiview = appState::openMultiview,
            onClearMultiview = appState::clearMultiview,
        )
        // Hidden in Favourites: the rail filters the provider's catalogue, and a favourites list is
        // the user's own selection across all of it. Picking a category there could only ever
        // narrow it to nothing.
        if (!appState.favoritesOnly) {
        XtreamCategorySelectors(
            categories = appState.xtreamCategories,
            contentType = appState.xtreamContentType,
            selectedCategoryId = appState.selectedXtreamCategoryId,
            onSelected = { categoryId ->
                detailsOpen = false
                scope.launch { appState.selectXtreamCategory(categoryId) }
            },
            // Services the playlist does not name, brought from TMDb. Built once per key and region;
            // see ensureServiceTitleIndex for why it asks each service what it carries rather than
            // asking about each film.
            indexedServiceChoices = indexedServices,
            selectedServiceLabel = appState.selectedServiceLabel,
            onServiceSelected = { label ->
                detailsOpen = false
                scope.launch { appState.selectService(label) }
            },
            serviceIndexLoading = appState.serviceIndexLoading,
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

/**
 * The toolbar, composed with only what a multiview test needs.
 *
 * Exists because reading the wiring proved nothing: every link looked correct while the button was
 * demonstrably not working for the user. A test that composes the real control and clicks it tells a
 * broken chain apart from a button nobody can find, which four rounds of reasoning did not.
 */
@Composable
internal fun XtreamToolbarForTesting(
    selectedType: XtreamContentType,
    multiviewCount: Int,
    onOpenMultiview: () -> Unit,
) {
    XtreamToolbar(
        selectedType = selectedType,
        query = "",
        status = XtreamStatus.Disconnected,
        onQueryChange = {},
        onTypeSelected = {},
        onDisconnect = {},
        selectedYear = null,
        onYearSelected = {},
        minimumRating = null,
        onMinimumRatingSelected = {},
        multiviewCount = multiviewCount,
        onOpenMultiview = onOpenMultiview,
    )
}

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
    /**
     * How many channels are queued for the multiview grid, and how to open it.
     *
     * Live only: four films at once is not something anyone wants, while four matches at once is
     * exactly what a second screen normally gets used for.
     */
    multiviewCount: Int = 0,
    onOpenMultiview: () -> Unit = {},
    onClearMultiview: () -> Unit = {},
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
            //
            // The shared component, so the same choice looks the same on every screen that offers
            // it. This was the original, copied out to the design system when continue watching and
            // downloads needed it too.
            BuroSegmentedControl(
                options = XtreamContentType.entries,
                selected = selectedType,
                label = { type -> type.label(text) },
                onSelect = onTypeSelected,
            )
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

        Row(
            verticalAlignment = Alignment.CenterVertically,
            // Scrollable, because a Row does not shrink children that carry no weight: once the
            // filters, the pickers and this chip together exceed the toolbar's width, whatever sits
            // last is simply laid out past the edge of the window and cannot be seen or clicked.
            //
            // That is what hid multiview. Every test passed — in a test there is nothing competing
            // for the space — while on a real screen the button was measured off the edge. Four
            // rounds of reasoning about the wiring found four other bugs and never this one.
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            // Multiview first, before the filters.
            //
            // Last in the row meant first to be pushed off. It is also the only control here that
            // opens a different mode rather than narrowing a list, so it belongs at the start.
            if (selectedType == XtreamContentType.LIVE) {
                FilterChip(
                    label =
                        if (multiviewCount > 0) {
                            "▦  ${text.settingsText.multiviewOpen} ($multiviewCount)"
                        } else {
                            "▦  ${text.settingsText.multiviewHint}"
                        },
                    selected = multiviewCount > 0,
                    onClick = onOpenMultiview,
                )
                if (multiviewCount > 0) {
                    Spacer(Modifier.width(BuroSpacing.Xs))
                    FilterChip(
                        label = text.settingsText.multiviewClear,
                        selected = false,
                        onClick = onClearMultiview,
                    )
                }
                Spacer(Modifier.width(BuroSpacing.Md))
            }

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
            // Multiview, live only and only once something is queued. Shown as a count rather than
            // a plain button so the toolbar says how many channels are waiting — with a cap of
            // four, "3 canais" is the whole state.
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
 * Two selectors: what kind of title, and from which service.
 *
 * This replaced a single horizontal rail. The rail held every category the playlist declares in one
 * sideways-scrolling strip — thirty-odd chips on a real subscription, mixing "Acao" and "Aventura"
 * with "Netflix" and "Amazon" — so both questions were answered in the same place and answering
 * either meant scrolling past the other. Most of the strip sat off the right edge behind a scrollbar
 * that had to be pointed out with a comment.
 *
 * Two closed menus take one line, name the question they answer, and put the services where somebody
 * looking for a service will look. The provider selector draws each service's mark beside its name;
 * see [ProviderIdentity] for why that is a monogram in the brand colour rather than the real logo.
 *
 * ## One at a time, deliberately
 *
 * Choosing a genre clears the service and vice versa, because a title belongs to exactly one
 * category: "Filmes | Netflix" is not also filed under "Filmes | Acao", so asking for both would
 * return an empty grid rather than a narrower one. See [splitCategories] for the longer note.
 */
@Composable
private fun XtreamCategorySelectors(
    categories: List<XtreamCategory>,
    contentType: XtreamContentType,
    selectedCategoryId: String?,
    onSelected: (String?) -> Unit,
    /**
     * Services derived from TMDb, for a playlist whose categories name none.
     *
     * Empty when the playlist already files by service — those categories are the provider's own and
     * are preferred — or when nothing in the library matched.
     */
    indexedServiceChoices: List<CategoryChoice> = emptyList(),
    /** The service label currently filtering the grid, or null for all of them. */
    selectedServiceLabel: String? = null,
    onServiceSelected: (String?) -> Unit = {},
    /** True while the index is being built, so the control says so rather than reading as empty. */
    serviceIndexLoading: Boolean = false,
) {
    val text = strings
    val services = text.shareStrings.serviceCatalogue
    // The services' real marks, when the directory has loaded. Read here and applied to the split so
    // every menu row and every closed selector shows the same badge for the same service.
    val logos = LocalProviderLogos.current
    // Recomputed only when the playlist's categories change, not on every recomposition: this walks
    // every category and runs the provider match on each, and the grid beside it has to stay smooth.
    val split =
        remember(categories, logos) {
            splitCategories(categories).withLogos(logos)
        }

    val genreSelected = selectedCategoryId?.takeIf { id -> !split.isProvider(id) }
    val providerSelected = selectedCategoryId?.takeIf { id -> split.isProvider(id) }

    FlowRow(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = BuroSpacing.GutterCompact, vertical = BuroSpacing.Xs),
        horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Sm),
        verticalArrangement = Arrangement.spacedBy(BuroSpacing.Xs),
    ) {
        CategorySelector(
            title = services.genreSelector,
            anyLabel = services.allGenres,
            choices = split.genres,
            selectedId = genreSelected,
            onSelect = onSelected,
        )
        // Absent only when this playlist genuinely files nothing by service.
        //
        // The gate used to be the reason the selector was missing from Filmes: a list that organises
        // its films by genre — "Filmes | Ação", "Filmes | Drama" — yields no provider categories at
        // all, so the selector vanished on exactly the tab where somebody asks "what is on Netflix".
        // It appeared under Ao vivo, where channels are filed by service, which made it look
        // arbitrary rather than absent.
        //
        // The categories alone cannot answer it, so TMDb is asked instead.
        //
        // `serviceTitleIndex` holds which of the user's own films each service carries, built by
        // asking each service what it has rather than asking about each film — see the note on
        // ServiceTitleIndex for why that direction is the only affordable one. When the playlist does
        // name services in its categories those are used directly, since they are the provider's own
        // filing and cost nothing.
        val indexed = indexedServiceChoices
        when {
            split.hasProviders ->
                CategorySelector(
                    title = services.serviceSelector,
                    anyLabel = services.allServices,
                    choices = split.providers,
                    selectedId = providerSelected,
                    onSelect = onSelected,
                )
            indexed.isNotEmpty() ->
                CategorySelector(
                    title = services.serviceSelector,
                    anyLabel = services.allServices,
                    choices = indexed,
                    selectedId = selectedServiceLabel,
                    onSelect = onServiceSelected,
                )
            else ->
                // Still nothing: no service categories, and TMDb matched none of this library. Saying
                // so is better than hiding the control, which would leave the user wondering whether
                // the feature exists at all.
                ServiceSelectorUnavailable(
                    title = services.serviceSelector,
                    explanation =
                        if (serviceIndexLoading) services.servicesLoading else services.servicesUnavailable,
                )
        }
    }
}

/**
 * The Serviço selector, disabled, when this playlist files nothing by service.
 *
 * Shown rather than hidden. A control that appears on one tab and not another reads as a bug, and
 * hiding it left somebody looking for "only Netflix films" with no way to tell whether the feature
 * was missing, broken, or simply not applicable to their list. Saying why is more use than silence.
 *
 * Not clickable, because there is nothing behind it: the service a film came from is only knowable
 * here if the provider recorded it in the category name.
 */
@Composable
private fun ServiceSelectorUnavailable(
    title: String,
    explanation: String,
) {
    Row(
        modifier =
            Modifier
                .border(1.dp, BuroColors.BorderSoft.copy(alpha = 0.5f), BuroRadius.Pill)
                .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$title: $explanation",
            color = BuroColors.TextSubtle,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * One closed menu: the question, the current answer, and the alternatives.
 *
 * The question stays on the button rather than sitting above it, so the row reads "Genero: Acao" at a
 * glance and the two selectors cannot be mistaken for each other once something is chosen.
 */
@Composable
private fun CategorySelector(
    title: String,
    anyLabel: String,
    choices: List<CategoryChoice>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = choices.firstOrNull { choice -> choice.id == selectedId }
    val active = selected != null

    Box {
        BuroInteractiveRow(
            onClick = { expanded = true },
            selected = active,
            shape = BuroRadius.Pill,
            contentDescription = title + ": " + (selected?.label ?: anyLabel),
        ) {
            Row(
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
                        ).padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                selected?.provider?.let { provider ->
                    ProviderMark(provider = provider)
                    Spacer(Modifier.width(BuroSpacing.Xs))
                }
                Text(
                    text = title + ": " + (selected?.label ?: anyLabel) + "  ▾",
                    color = if (active) BuroColors.Text else BuroColors.TextMuted,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(BuroColors.SurfaceRaised),
        ) {
            // A scrolling list with a visible bar, not the menu's own silent scroll.
            //
            // A DropdownMenu given more items than fit scrolls, but shows nothing to say so: on a
            // real playlist this menu holds dozens of genres, and the ones below the fold looked as
            // though they did not exist. Reported for this selector, and it is the third surface in
            // the app to have had it — the settings panel and the category rail both carry the same
            // explicit-colour scrollbar for the same reason. Compose's default is near-black on a
            // dark surface, so it is drawn and cannot be seen.
            //
            // The bar is also the way to *drag* through a long list, which a scroll-only menu never
            // offers a pointer user.
            val listState = rememberLazyListState()
            // A measured height, not just a ceiling.
            //
            // `DropdownMenu` sizes its popup by asking its content for an intrinsic measurement, and
            // a LazyColumn cannot answer that — it is a SubcomposeLayout, and the request throws
            // outright: "Asking for intrinsic measurements of SubcomposeLayout layouts is not
            // supported". A `heightIn(max = …)` alone leaves the height unresolved, so the menu still
            // asks, and the app dies the first time somebody opens the selector.
            //
            // The row height is known — every row is one line of text with fixed padding — so the
            // list is given a real height computed from how many rows there are, capped at the
            // ceiling. That is a definite size, which is what the error message asks for and what
            // stops the question being asked at all.
            val rowCount = choices.size + 1
            val menuHeight = (MENU_ROW_HEIGHT * rowCount).coerceAtMost(MENU_MAX_HEIGHT)
            Box(modifier = Modifier.height(menuHeight)) {
                LazyColumn(
                    state = listState,
                    // Room for the bar, so it sits beside the labels rather than over them.
                    modifier = Modifier.width(MENU_WIDTH).fillMaxHeight().padding(end = 10.dp),
                ) {
                    item(key = "any") {
                        CategoryMenuRow(
                            label = anyLabel,
                            provider = null,
                            selected = selectedId == null,
                            onClick = {
                                onSelect(null)
                                expanded = false
                            },
                        )
                    }
                    items(choices, key = CategoryChoice::id) { choice ->
                        CategoryMenuRow(
                            label = choice.label,
                            provider = choice.provider,
                            selected = choice.id == selectedId,
                            onClick = {
                                // Selecting from either menu replaces the single category the
                                // catalogue filters by, which is what clears the other selector.
                                onSelect(choice.id)
                                expanded = false
                            },
                        )
                    }
                }
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(listState),
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    style =
                        LocalScrollbarStyle.current.copy(
                            thickness = 8.dp,
                            unhoverColor = BuroColors.BorderSoft,
                            hoverColor = BuroColors.Primary,
                        ),
                )
            }
        }
    }
}

/**
 * One row in a category menu: the service's mark when it has one, then the name.
 *
 * A plain row rather than `DropdownMenuItem`, because the menu's contents are now a LazyColumn and
 * that composable expects to be a direct child of the menu's own column.
 */
@Composable
private fun CategoryMenuRow(
    label: String,
    provider: ProviderIdentity?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = BuroSpacing.Md, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The service's mark, which is the whole point of the second selector: people recognise a
        // mark faster than they read a name.
        provider?.let { identity ->
            ProviderMark(provider = identity)
            Spacer(Modifier.width(BuroSpacing.Sm))
        }
        Text(
            text = label,
            color = if (selected) BuroColors.Primary else BuroColors.Text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Tall enough for about nine rows, past which the list scrolls rather than the menu growing. */
private val MENU_MAX_HEIGHT = 420.dp

/**
 * One row: a line of `bodyMedium` plus the 10dp of padding above and below it.
 *
 * Used to give the menu a definite height, because a LazyColumn cannot be measured intrinsically and
 * DropdownMenu would otherwise ask it to be. Approximate on purpose — it only has to be close enough
 * that a short list is not left with a gap under it, and any error is absorbed by the cap.
 */
private val MENU_ROW_HEIGHT = 44.dp

/**
 * Fixed, so the scrollbar has a column to sit in.
 *
 * A menu that sizes itself to its widest genre also jumps in width as the list is filtered, and the
 * bar would move with it.
 */
private val MENU_WIDTH = 260.dp

/**
 * A service's mark: its monogram on its own colour.
 *
 * Not the real logo, for the reasons set out on [ProviderIdentity] — the wordmarks belong to the
 * services, and a monogram in the brand colour identifies the service without shipping their asset
 * or breaking when a playlist invents a category this app has never seen.
 */
@Composable
private fun ProviderMark(provider: ProviderIdentity) {
    val logo = provider.logoUrl
    if (logo != null) {
        // The service's real mark, fetched like every other image in the app.
        //
        // TMDb distributes the providers' own logos and licenses them for this, which is what makes
        // a genuine Netflix or Prime badge legitimate. The monogram below was what shipped first,
        // and "AP" for Prime Video is not a logo — the point of the selector is that a mark is
        // recognised faster than a name is read, and a two-letter chip does neither.
        //
        // On a light tile, because the marks are drawn for one: several are dark artwork on
        // transparency and would vanish against this canvas.
        Box(
            modifier =
                Modifier
                    .size(width = PROVIDER_MARK_WIDTH, height = PROVIDER_MARK_HEIGHT)
                    .clip(BuroRadius.Small)
                    .background(PROVIDER_MARK_TILE),
            contentAlignment = Alignment.Center,
        ) {
            BuroRemoteArtwork(
                artworkUrl = logo,
                contentDescription = provider.label,
                modifier = Modifier.fillMaxSize().padding(1.dp),
                contentScale = ContentScale.Fit,
            ) {}
        }
        return
    }
    Box(
        modifier =
            Modifier
                .size(width = PROVIDER_MARK_WIDTH, height = PROVIDER_MARK_HEIGHT)
                .clip(BuroRadius.Small)
                .background(provider.colour),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = provider.monogram,
            color = provider.ink,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            softWrap = false,
            maxLines = 1,
        )
    }
}

/**
 * The tile a real logo sits on.
 *
 * Near-white rather than the canvas: TMDb's marks are drawn for a light background, and several are
 * dark glyphs on transparency that disappear entirely against this app's near-black surfaces.
 */
private val PROVIDER_MARK_TILE = Color(0xFFF2F2F2)

/** Wide enough for the longest monogram ("HBO") without the mark reading as a button. */
private val PROVIDER_MARK_WIDTH = 26.dp

/** Matched to the cap height of the label beside it. */
private val PROVIDER_MARK_HEIGHT = 16.dp


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
    // Resumes where this list was left.
    //
    // Opening a title removes this grid from the composition, and a plain rememberLazyGridState goes
    // with it — so pressing back landed at the top, after however far the user had scrolled. This is
    // the app's most repeated action, so that cost was paid constantly.
    //
    // Keyed on content type, category and search so that films, series and live each keep their own
    // place: one shared key would restore the film offset onto the series grid, which looks
    // deliberate and is worse than starting at the top.
    // The Xtream fields, not the local-playlist ones.
    //
    // This read `selectedCategoryId` and `searchQuery`, which belong to the imported-M3U catalogue.
    // In an Xtream session those stay empty for ever, so every category and every search shared one
    // key: the position saved while browsing Ação was restored onto Terror, and a search's offset
    // was restored onto the unfiltered grid. Restoring a position from a different list is worse
    // than starting at the top, because it looks deliberate.
    val gridState = rememberRestoredGridState(
        key = "catalog:${appState.xtreamContentType}:${appState.selectedXtreamCategoryId.orEmpty()}:${appState.xtreamSearchQuery}",
    )
    val gridFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    // Focus goes to the grid when the page opens, so the arrow keys work without a click first.
    var gridFocusAttached by remember { mutableStateOf(false) }
    LaunchedEffect(gridFocusAttached) {
        if (gridFocusAttached) gridFocus.requestFocus()
    }

    // A new page reuses the same list, so without this the grid keeps the previous offset and the
    // first row of the new page opens already scrolled past.
    //
    // Only on a *change*, though. A LaunchedEffect also runs when it is first composed, and this
    // composable is composed again every time the user returns from a title's page — so the effect
    // fired on the way back and scrolled to the top, throwing away the position `RememberedScroll`
    // had just restored. Remembering what was last scrolled for tells a real page turn apart from a
    // re-entry, which the keys alone cannot.
    var lastScrolledList by remember {
        mutableStateOf<Triple<Int, XtreamContentType, String?>?>(null)
    }
    LaunchedEffect(page.pageIndex, appState.xtreamContentType, appState.selectedXtreamCategoryId) {
        val current =
            Triple(page.pageIndex, appState.xtreamContentType, appState.selectedXtreamCategoryId)
        if (lastScrolledList != null && lastScrolledList != current) {
            gridState.scrollToItem(0)
        }
        lastScrolledList = current
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
                        .onGloballyPositioned { gridFocusAttached = true }
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
                        // Adding to multiview, from the grid.
                        //
                        // It used to live only inside a channel's detail page, which meant the
                        // feature could not be found: the toolbar chip that opens the grid appears
                        // only once something is queued, so a user had to open a channel, notice a
                        // button they were not looking for, go back, and repeat. Nobody does that.
                        onToggleMultiview =
                            if (DesktopPlatformCapabilities.current.multiviewSupported &&
                                item.contentType == XtreamContentType.LIVE
                            ) {
                                { appState.toggleMultiviewChannel(item.providerId) }
                            } else {
                                null
                            },
                        inMultiview = item.providerId in appState.multiviewChannelIds,
                        // What this subscription actually sustains, not the app's own cap.
                        //
                        // A provider that allows two simultaneous connections simply stops sending
                        // on the older streams when a third starts — no error, just tiles going
                        // black after about five seconds. Offering a fourth slot the account cannot
                        // use produces a broken-looking grid instead of a clear limit.
                        multiviewFull = appState.multiviewChannelIds.size >= appState.multiviewCapacity,
                        multiviewCapacity = appState.multiviewCapacity,
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
    /** Null when this card cannot be added to multiview — anything that is not a live channel. */
    onToggleMultiview: (() -> Unit)? = null,
    inMultiview: Boolean = false,
    multiviewFull: Boolean = false,
    /** How many tiles the subscription allows, so the message can name the real number. */
    multiviewCapacity: Int = MULTIVIEW_MAX_TILES,
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

                // Add to multiview, on the card itself.
                //
                // Top left, opposite the rating. Always visible once a channel is queued so the set
                // can be seen at a glance across the grid; otherwise only on hover, because a
                // permanent icon on every tile is clutter for the majority who never use this.
                if (onToggleMultiview != null && (state.active || inMultiview)) {
                    BuroInteractiveRow(
                        onClick = onToggleMultiview,
                        selected = inMultiview,
                        enabled = inMultiview || !multiviewFull,
                        shape = BuroRadius.Pill,
                        contentDescription =
                            if (inMultiview) {
                                text.settingsText.multiviewRemove
                            } else if (multiviewFull) {
                                text.settingsText.multiviewFull.format(multiviewCapacity)
                            } else {
                                text.settingsText.multiviewAdd
                            },
                        modifier = Modifier.align(Alignment.TopStart).padding(BuroSpacing.Xs),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .clip(BuroRadius.Pill)
                                    .background(
                                        if (inMultiview) {
                                            BuroColors.Primary
                                        } else {
                                            BuroColors.Canvas.copy(alpha = 0.78f)
                                        },
                                    )
                                    .padding(horizontal = BuroSpacing.Xs, vertical = 4.dp),
                        ) {
                            Text(
                                text = "▦",
                                // Dimmed rather than hidden when four are already queued: a control
                                // that disappears looks broken, while one that is plainly inactive
                                // says the limit has been reached.
                                color =
                                    when {
                                        inMultiview -> BuroColors.OnPrimary
                                        multiviewFull -> BuroColors.TextSubtle
                                        else -> BuroColors.Text
                                    },
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
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
    val capabilities = DesktopPlatformCapabilities.current
    // Which trailer is open, if any. Held here so the panel closes when the page does.
    var openTrailerId by remember { mutableStateOf<String?>(null) }
    // The share sheet, held alongside the trailer for the same reason: leaving the page closes it.
    var shareLink by remember { mutableStateOf<TitleShareLink?>(null) }
    val item = appState.selectedXtreamItem ?: return
    val movie = appState.movieDetailsStatus as? MovieDetailsStatus.Loaded
    val series = appState.seriesDetailsStatus as? SeriesDetailsStatus.Loaded
    // Only VOD is downloadable. A live stream has no end, so a download would grow until the
    // disk fills.
    val downloadTarget =
        if (capabilities.offlineSupported && item.contentType == XtreamContentType.MOVIE) {
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
                // TMDb publishes a separate ladder for backdrops, and this is the image that
                // suffered most from a fixed width: w1280 stretched across a 4K panel is a 3x
                // upscale, and the Ken Burns transform above magnifies the softness further.
                isBackdrop = true,
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
                hasReminder = appState.hasReminder(item),
                onToggleReminder = { appState.toggleReminder(item) },
                audienceScore = appState.audienceScore,
                criticScores = appState.criticScores,
                onShare = {
                    // Built here rather than in the dialog, because this is where the loaded
                    // details are. The poster deliberately comes from the *details* rather than
                    // `item.artworkUrl`: the latter is the provider's own image host, which
                    // TitleShareLink refuses to carry — see its allowlist. When TMDb has not
                    // resolved a poster the share simply goes without one.
                    shareLink =
                        TitleShareLink.of(
                            identity = item.contentIdentity(),
                            title = item.name.editorialTitle(),
                            year = item.year,
                            artworkUrl = movie?.details?.artworkUrl ?: series?.details?.artworkUrl,
                            description = movie?.details?.plot ?: series?.details?.plot,
                        )
                },
                onCast = {
                    // The same link the share button builds — both name a title rather than a
                    // location, which is exactly what the receiving screen needs to find it in its
                    // own catalogue.
                    //
                    // `of` refuses a title it cannot identify, and a null there means there is
                    // nothing a receiver could look up. Nothing opens rather than a sheet that
                    // could only fail.
                    TitleShareLink.of(
                        identity = item.contentIdentity(),
                        title = item.name.editorialTitle(),
                        year = item.year,
                        artworkUrl = movie?.details?.artworkUrl ?: series?.details?.artworkUrl,
                        description = movie?.details?.plot ?: series?.details?.plot,
                    )?.let(appState::startCastTo)
                },
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
                episodeDownloadFor = { target ->
                    if (capabilities.offlineSupported) appState.downloadState(target.contentKey) else null
                },
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
                onToggleMultiview =
                    if (capabilities.multiviewSupported && item.contentType == XtreamContentType.LIVE) {
                        { appState.toggleMultiviewChannel(item.providerId) }
                    } else {
                        null
                    },
                inMultiview = item.providerId in appState.multiviewChannelIds,
                // The subscription's limit, not a literal four. A hardcoded cap here offered a
                // fourth slot to an account that can sustain two, and the extra tiles simply went
                // black — the provider stops sending rather than refusing.
                multiviewFull = appState.multiviewChannelIds.size >= appState.multiviewCapacity,
                multiviewCapacity = appState.multiviewCapacity,
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

        shareLink?.let { link ->
            ShareTitleDialog(
                link = link,
                onDismiss = { shareLink = null },
                onOpenUrl = { url -> appState.openPublicUrl(url) },
            )
        }

        CastSendDialog(
            state = appState.castSendState,
            onSearchAgain = appState::searchForCastTargets,
            onChoose = appState::chooseCastTarget,
            onBack = appState::backToCastTargets,
            onSend = appState::sendToCastTarget,
            onClose = appState::closeCastSend,
            text = strings,
            onConnectToAddress = appState::connectToCastAddress,
            onManualAddressFailed = appState.castManualAddressFailed,
            onClearManualFailure = appState::clearCastManualAddressFailure,
        )
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
    /** Whether this profile asked to be reminded about the title. */
    hasReminder: Boolean = false,
    /** Marks or unmarks the title as one to be reminded about. Null hides the button. */
    onToggleReminder: (() -> Unit)? = null,
    /** The audience score for this title, once TMDb has answered. Null draws no ratings block. */
    audienceScore: TmdbAudienceScore? = null,
    /** The critics' scores, when an OMDb key is set and that service knew the title. */
    criticScores: CriticScores? = null,
    /**
     * Opens the share sheet. Null where sharing makes no sense — a live channel is a schedule
     * rather than a title, and the recipient's own list would have nothing to resolve it to.
     */
    onShare: (() -> Unit)? = null,
    /** Opens the sheet that sends this title to another screen on the network. Null hides it. */
    onCast: (() -> Unit)? = null,
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
    /**
     * Adds or removes this channel from the multiview grid. Null for anything but a live channel.
     */
    onToggleMultiview: (() -> Unit)? = null,
    inMultiview: Boolean = false,
    /** True once four channels are queued, which is as many as the grid holds. */
    multiviewFull: Boolean = false,
    /** How many tiles the subscription allows, so the message can name the real number. */
    multiviewCapacity: Int = MULTIVIEW_MAX_TILES,
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
                    // The same two the film page receives. Series had neither, so the page showed
                    // the provider's own star and nothing else.
                    audienceScore = audienceScore,
                    criticScores = criticScores,
                    onOpenPerson = onOpenPerson,
                    castPhotoFor = castPhotoFor,
                    onRequestCastPhoto = onRequestCastPhoto,
                    seriesTitle = item.name,
                    isFavorite = isFavorite,
                    onToggleFavorite = onToggleFavorite,
                    hasReminder = hasReminder,
                    onToggleReminder = onToggleReminder,
                    onShare = onShare,
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
                        // Beside Favourites, which is the action it most resembles: both mark a
                        // title for later rather than doing anything to it now. A favourite says
                        // "I like this", a reminder says "come back to this one".
                        onToggleReminder?.let { toggle ->
                            OutlinedButton(
                                onClick = toggle,
                                modifier = Modifier.height(48.dp),
                                shape = BuroRadius.Small,
                                colors =
                                    ButtonDefaults.outlinedButtonColors(
                                        contentColor =
                                            if (hasReminder) BuroColors.Primary else BuroColors.Text,
                                    ),
                            ) {
                                Text(
                                    // Outline when unmarked, filled once marked — the same pair
                                    // Favoritos uses beside it. Text glyphs rather than an emoji
                                    // bell: Windows renders emoji in colour at their own width,
                                    // which would make this the one loud button in a row of five.
                                    if (hasReminder) {
                                        "◉  ${text.savedForLater.reminderActive}"
                                    } else {
                                        "○  ${text.savedForLater.reminderAdd}"
                                    },
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                        // Beside Favourites: both are things you do *about* a film rather than
                        // with it, and they belong together after the play actions.
                        onShare?.let { share ->
                            OutlinedButton(
                                onClick = share,
                                modifier = Modifier.height(48.dp),
                                shape = BuroRadius.Small,
                                colors =
                                    ButtonDefaults.outlinedButtonColors(
                                        contentColor = BuroColors.Text,
                                    ),
                            ) {
                                Text("↗  ${text.shareStrings.share}", fontWeight = FontWeight.SemiBold)
                            }
                        }
                        // Sending this title to another screen on the network — a television, a
                        // phone, another computer. Beside Compartilhar because it is the same idea
                        // with a different destination: both hand over *which* title, never a
                        // stream, so the other end plays from its own list and this machine's
                        // credentials stay here.
                        onCast?.let { cast ->
                            OutlinedButton(
                                onClick = cast,
                                modifier = Modifier.height(48.dp),
                                shape = BuroRadius.Small,
                                colors =
                                    ButtonDefaults.outlinedButtonColors(
                                        contentColor = BuroColors.Text,
                                    ),
                            ) {
                                Text("⇥  ${text.shareStrings.cast.castAction}", fontWeight = FontWeight.SemiBold)
                            }
                        }
                        // Beside the other actions, where the series page has always had it.
                        //
                        // This used to sit at the very bottom of the full record, after the plot,
                        // the director and the cast strip, so on a long page it was below the fold
                        // and read as missing. Same button, somewhere it can be found.
                        //
                        // Still only drawn when TMDb actually gave a trailer id for this title:
                        // a button that opens nothing would be worse than its absence.
                        (movieStatus as? MovieDetailsStatus.Loaded)?.details?.youtubeTrailerId?.let { trailerId ->
                            OutlinedButton(
                                onClick = { onOpenTrailer(trailerId) },
                                modifier = Modifier.height(48.dp),
                                shape = BuroRadius.Small,
                                colors =
                                    ButtonDefaults.outlinedButtonColors(
                                        contentColor = BuroColors.Text,
                                    ),
                            ) {
                                Text("▶  Trailer", fontWeight = FontWeight.SemiBold)
                            }
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
                    // Live only. Queueing four films to watch at once is not a thing anyone wants;
                    // four matches at once is what people buy a second screen for, and it is the
                    // one case where running several decoders earns what it costs.
                    if (item.contentType == XtreamContentType.LIVE && onToggleMultiview != null) {
                        OutlinedButton(
                            onClick = onToggleMultiview,
                            enabled = inMultiview || !multiviewFull,
                            shape = BuroRadius.Small,
                            colors =
                                ButtonDefaults.outlinedButtonColors(
                                    contentColor =
                                        if (inMultiview) BuroColors.Primary else BuroColors.Text,
                                ),
                            contentPadding = PaddingValues(horizontal = BuroSpacing.Lg),
                            modifier = Modifier.height(46.dp),
                        ) {
                            Text(
                                text =
                                    when {
                                        inMultiview -> "▦  ${text.settingsText.multiviewRemove}"
                                        multiviewFull -> text.settingsText.multiviewFull.format(multiviewCapacity)
                                        else -> "▦  ${text.settingsText.multiviewAdd}"
                                    },
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
                // Only once marked, and only here — saying it before the button is pressed would
                // argue against pressing it. Said at all because Windows stores the reminder but
                // has nothing that announces one, and a bell that never rings is a broken promise.
                if (hasReminder && onToggleReminder != null) {
                    Spacer(Modifier.height(BuroSpacing.Xs))
                    Text(
                        text = text.savedForLater.reminderNoNotice,
                        color = BuroColors.TextSubtle,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(BuroSpacing.Lg))
                if (item.contentType == XtreamContentType.LIVE) {
                    LiveEpgContent(liveEpgStatus)
                    Spacer(Modifier.height(18.dp))
                }
                if (item.contentType == XtreamContentType.MOVIE) {
                    MovieDetailContent(
                        status = movieStatus,
                        audienceScore = audienceScore,
                        criticScores = criticScores,
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
/**
 * A programme start time as the viewer's own clock shows it.
 *
 * The provider sends epoch seconds in UTC; this renders them in the machine's zone, which is the
 * only reading that answers "when is that on". A missing or unparseable time gives an em dash
 * rather than a wrong hour — the schedule is still useful without one, and a confidently wrong time
 * is worse than an obviously absent one.
 */
private fun Long?.asClockTime(): String =
    this
        ?.let { seconds ->
            runCatching {
                java.time.Instant.ofEpochSecond(seconds)
                    .atZone(java.time.ZoneId.systemDefault())
                    .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
            }.getOrNull()
        }
        ?: "—"

@Composable
private fun LiveEpgContent(status: LiveEpgStatus) {
    val text = strings
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

                // The rest of the day, behind a press.
                //
                // The provider sends several hours of schedule and the screen showed two entries of
                // it; the remainder was parsed and discarded. Collapsed by default because most
                // visits are to answer "what is on now", and the full grid would push the buttons
                // below the fold for a question nobody asked.
                val later = status.schedule.drop(2)
                if (later.isNotEmpty()) {
                    var scheduleOpen by remember(status) { mutableStateOf(false) }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { scheduleOpen = !scheduleOpen }) {
                        Text(
                            text =
                                if (scheduleOpen) {
                                    text.settingsText.epgHideSchedule
                                } else {
                                    text.settingsText.epgShowSchedule.format(later.size)
                                },
                            maxLines = 1,
                        )
                    }
                    if (scheduleOpen) {
                        Spacer(Modifier.height(8.dp))
                        later.forEach { program ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                Text(
                                    text = program.startEpochSeconds.asClockTime(),
                                    color = BuroColors.Primary,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.width(56.dp),
                                )
                                Text(
                                    text = program.title,
                                    color = BuroColors.TextMuted,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MovieDetailContent(
    status: MovieDetailsStatus,
    /** The audience score, once TMDb has answered. Null draws no ratings block at all. */
    audienceScore: TmdbAudienceScore? = null,
    /** The critics' scores, which appear under the audience one when they exist. */
    criticScores: CriticScores? = null,
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
                    // Zero is "nobody has rated this", not a verdict of nought out of ten.
                    //
                    // Providers send 0 for anything unrated, and a title released days ago is
                    // unrated by definition — so a brand-new series showed "★ 0,0" twice over,
                    // which reads as the worst score there is rather than as the absence of one.
                    details.rating?.takeIf { value -> value > 0.0 }?.let { "★ ${"%.1f".format(it)}" },
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
            RatingsBlock(score = audienceScore, critics = criticScores)
            details.director?.let { DetailLine("Direção", it) }
            details.cast?.let {
                CastButtons(
                    rawCast = it,
                    onOpenPerson = onOpenPerson,
                    photoFor = castPhotoFor,
                    onRequestPhoto = onRequestCastPhoto,
                )
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
    // Returns false when this playlist does not carry the title, so the page can say so.
    onOpenCredit: suspend (PersonCredit) -> Unit = {},
) {
    val scope = rememberCoroutineScope()

    /**
     * The credit currently being opened, or null when idle.
     *
     * Searching the playlist for a title sweeps every row of a catalogue that runs to tens of
     * thousands of items. That takes long enough to look like a freeze, and the previous version
     * gave no sign at all — the user pressed a poster and the app appeared to hang.
     */
    var openingCredit by remember { mutableStateOf<String?>(null) }

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
                        Column(
                            modifier =
                                Modifier
                                    .width(120.dp)
                                    .clip(BuroRadius.Small)
                                    .clickable(enabled = openingCredit == null) {
                                        // A credit names a film the playlist may not carry, which is
                                        // the ordinary case rather than a failure: the click opens
                                        // it here if it is here, and asks Assinaturas where it can
                                        // be watched if it is not.
                                        //
                                        // The search sweeps a 41,000-item catalogue, so the card
                                        // says it is working. Without that the app looked frozen —
                                        // and the previous handler then set a value nothing read,
                                        // so nothing happened at all.
                                        scope.launch {
                                            openingCredit = credit.title
                                            try {
                                                onOpenCredit(credit)
                                            } finally {
                                                openingCredit = null
                                            }
                                        }
                                    },
                        ) {
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
                            // The card that was pressed says so while the catalogue is searched.
                            if (openingCredit == credit.title) {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth().height(2.dp),
                                    color = BuroColors.Primary,
                                    trackColor = BuroColors.SurfaceRaised,
                                )
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
                        Text(strings.shareStrings.screens.searchingCatalogue, color = BuroColors.TextMuted)
                    }
                } else if (person.items.isEmpty()) {
                    Text(strings.shareStrings.screens.noFurtherTitles, color = BuroColors.TextMuted)
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
    /** The audience score, once TMDb has answered. Null draws no ratings block at all. */
    audienceScore: TmdbAudienceScore? = null,
    /** The critics' scores, which appear under the audience one when they exist. */
    criticScores: CriticScores? = null,
    onOpenPerson: (String) -> Unit,
    castPhotoFor: (String) -> String?,
    onRequestCastPhoto: suspend (String) -> Unit,
    seriesTitle: String,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    /** Whether this profile asked to be reminded about the series. */
    hasReminder: Boolean = false,
    /** Marks or unmarks the series as one to be reminded about. Null hides the button. */
    onToggleReminder: (() -> Unit)? = null,
    /** Null when the page has no share target; see the parameter of the same name on the detail. */
    onShare: (() -> Unit)?,
    /** Opens the sheet that sends this title to another screen on the network. Null hides it. */
    onCast: (() -> Unit)? = null,
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
            // Which bulk download is waiting to be confirmed, or null. Reset per series so leaving
            // one title and opening another cannot carry a pending confirmation across.
            var pendingBulkDownload by remember(details.providerId) {
                mutableStateOf<BulkDownload?>(null)
            }
            // What the bulk buttons need to know, worked out once per episode list rather than on
            // every recomposition.
            //
            // `downloadStateForEpisode` reaches `DesktopDownloadManager.isDownloaded`, which calls
            // `Files.list` on the downloads folder — one directory listing per episode. Asking it
            // for the whole list on each pass cost about 460 ms for a 1,171-episode series, and the
            // three questions below asked three times over: well over a second of disk I/O on the
            // UI thread every time anything on the page changed. Measured, not guessed.
            //
            // Keyed on the series and the episode list, so switching title recomputes and a page
            // that merely redraws does not. The states themselves can change under this — a
            // download finishing does not immediately remove an episode from the pending list —
            // which is a fair trade: the buttons are about what is worth queueing, and being one
            // interaction stale there costs nothing, while `startDownload` still refuses anything
            // already running.
            val bulkDownloadState =
                remember(details.providerId, episodes) {
                    val offered = episodes.any { episode -> downloadStateForEpisode(episode) != null }
                    val pending =
                        episodes.filter { episode ->
                            downloadStateForEpisode(episode) != DownloadState.Completed
                        }
                    offered to pending
                }
            val downloadsOffered = bulkDownloadState.first
            val pendingEpisodes = bulkDownloadState.second
            val facts =
                listOfNotNull(
                    details.releaseDate,
                    details.genre,
                    // Zero is "nobody has rated this", not a verdict of nought out of ten.
                    //
                    // Providers send 0 for anything unrated, and a title released days ago is
                    // unrated by definition — so a brand-new series showed "★ 0,0" twice over,
                    // which reads as the worst score there is rather than as the absence of one.
                    details.rating?.takeIf { value -> value > 0.0 }?.let { "★ ${"%.1f".format(it)}" },
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
            // In the same place the film page puts it: after the synopsis, before the credits.
            //
            // A series page showed only the provider's own star — no TMDb score, no Tomatometer or
            // Metascore, and no mark saying whose number any of it was. The block was written once
            // and only ever called from the film content, so half the catalogue never saw it.
            RatingsBlock(score = audienceScore, critics = criticScores)
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
                // Beside Favoritos, as on the film page. A series is the case that wants this most:
                // waiting for the next season is exactly "come back to this one later".
                onToggleReminder?.let { toggle ->
                    OutlinedButton(
                        onClick = toggle,
                        modifier = Modifier.height(48.dp),
                        shape = BuroRadius.Small,
                        colors =
                            ButtonDefaults.outlinedButtonColors(
                                contentColor = if (hasReminder) BuroColors.Primary else BuroColors.Text,
                            ),
                    ) {
                        Text(
                            if (hasReminder) {
                                "◉  ${text.savedForLater.reminderActive}"
                            } else {
                                "○  ${text.savedForLater.reminderAdd}"
                            },
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                onShare?.let { share ->
                    OutlinedButton(
                        onClick = share,
                        modifier = Modifier.height(48.dp),
                        shape = BuroRadius.Small,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = BuroColors.Text),
                    ) {
                        Text("↗  ${text.shareStrings.share}", fontWeight = FontWeight.SemiBold)
                    }
                }
                // Same reasoning as on the film page: what is handed over is which title, so the
                // other screen plays from its own list.
                onCast?.let { cast ->
                    OutlinedButton(
                        onClick = cast,
                        modifier = Modifier.height(48.dp),
                        shape = BuroRadius.Small,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = BuroColors.Text),
                    ) {
                        Text("⇥  ${text.shareStrings.cast.castAction}", fontWeight = FontWeight.SemiBold)
                    }
                }
                // Beside Compartilhar, where the whole series can be asked for at once.
                //
                // Only when downloads are available at all — `downloadStateForEpisode` answers null
                // on a build or platform without them, and a button that queues nothing is worse
                // than no button. Hidden too once every episode is already stored, for the same
                // reason: there would be nothing left to fetch.
                // Disabled rather than hidden once every episode is stored, so the row keeps its
                // shape. Still absent entirely where the platform offers no downloads at all —
                // that is a property of the build, not of the title, so it cannot move the row
                // between one series and the next.
                val pendingSeries = pendingEpisodes
                if (downloadsOffered) {
                    OutlinedButton(
                        onClick = { pendingBulkDownload = BulkDownload.WholeSeries(pendingSeries) },
                        enabled = pendingSeries.isNotEmpty(),
                        modifier = Modifier.height(48.dp),
                        shape = BuroRadius.Small,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = BuroColors.Text),
                    ) {
                        Text("⭳  ${text.downloadStrings.downloadSeries}", fontWeight = FontWeight.SemiBold)
                    }
                }
                // Always drawn, disabled when the provider gave no trailer id.
                //
                // Reported as "the trailer button disappeared": it had not been removed, it is
                // simply absent on any title without a trailer id, which moves every button after
                // it and makes each title lay out differently. The Android screens were fixed the
                // same way. A greyed button also says something true that a missing one does not —
                // this title has no trailer.
                val trailerId = details.youtubeTrailerId
                OutlinedButton(
                    onClick = { trailerId?.let(onOpenTrailer) },
                    enabled = trailerId != null,
                    modifier = Modifier.height(48.dp),
                    shape = BuroRadius.Small,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BuroColors.Text),
                ) {
                    Text("Trailer", fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(BuroSpacing.Lg))

            if (episodes.isEmpty()) {
                Text(
                    text.shareStrings.screens.noPlayableEpisodes,
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text =
                            if (seasons.size > 1) {
                                "Temporada $openSeason  ·  ${visible.size} episódios"
                            } else {
                                "${episodes.size} episódios"
                            },
                        color = BuroColors.TextMuted,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                    )
                    // The season the user is actually looking at, which is the one most people
                    // want: the season they are about to start, not the whole run.
                    //
                    // Hidden when every episode of it is already stored, for the same reason the
                    // series button is: a control that would queue nothing is not worth pressing.
                    // Narrowed from the list computed once above rather than asking the disk
                    // again: the season is a subset of the series, so an intersection answers it.
                    val visibleIds = visible.mapTo(HashSet<String>()) { episode -> episode.providerId }
                    val pendingSeason =
                        pendingEpisodes.filter { episode -> episode.providerId in visibleIds }
                    // Per-season, and only where there is more than one season.
                    //
                    // With a single season this button covered exactly what "Baixar série" above
                    // already covers, and it relabelled itself to say so — leaving two buttons
                    // reading "Baixar série" on the same screen, which is what a user reported.
                    // One season means one choice, and the action row already offers it.
                    if (downloadsOffered && pendingSeason.isNotEmpty() && seasons.size > 1) {
                        OutlinedButton(
                            onClick = {
                                pendingBulkDownload = BulkDownload.Season(openSeason, pendingSeason)
                            },
                            shape = BuroRadius.Small,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = BuroColors.Text),
                        ) {
                            Text(
                                text = "⭳  ${text.downloadStrings.downloadSeason.format(openSeason)}",
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(BuroSpacing.Sm))

                // Drawn in pages, not all at once.
                //
                // These rows go straight into the parent's scrolling column rather than a
                // LazyColumn — see the note above for why — which means every episode of the open
                // season is composed and measured whether or not it is on screen. That is fine for
                // a season of twenty and not for One Piece, whose 1,171 episodes across 23 seasons
                // left a season of several hundred rows building on the UI thread: the page simply
                // never appeared.
                //
                // Reset per season and per series, so switching either starts from the top again.
                var shown by remember(details.providerId, openSeason) {
                    mutableStateOf(EPISODE_PAGE_SIZE)
                }
                val ordered = remember(visible) { visible.sortedBy { it.episodeNumber ?: Int.MAX_VALUE } }
                ordered.take(shown).forEach { episode ->
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
                if (shown < ordered.size) {
                    val remaining = ordered.size - shown
                    OutlinedButton(
                        onClick = { shown += EPISODE_PAGE_SIZE },
                        modifier = Modifier.fillMaxWidth(),
                        shape = BuroRadius.Small,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = BuroColors.Text),
                    ) {
                        Text(
                            // The count, so the button says how much is left rather than just
                            // "more" — with 1,171 episodes that difference matters.
                            text = "Mostrar mais $remaining",
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            // Asked before anything is queued. This is the one control on the page that can start
            // eighty transfers and fill a disk, and it sits beside buttons that do something small
            // and instant.
            pendingBulkDownload?.let { pending ->
                BulkDownloadDialog(
                    episodeCount = pending.episodes.size,
                    seasonNumber = (pending as? BulkDownload.Season)?.number,
                    onConfirm = {
                        // Queued in playing order, because most of these wait rather than run: a
                        // viewer who starts watching before the last file lands gets the beginning
                        // first. Each one goes through the ordinary single-episode path, so the
                        // transfer limit and the late, in-memory URL resolution are unchanged.
                        pending.episodes
                            .sortedWith(
                                compareBy(
                                    { it.seasonNumber },
                                    { it.episodeNumber ?: Int.MAX_VALUE },
                                ),
                            ).forEach(onDownloadEpisode)
                        pendingBulkDownload = null
                    },
                    onDismiss = { pendingBulkDownload = null },
                    text = text,
                )
            }
        }
    }
}

/**
 * Choosing a screen and entering its code.
 *
 * Three steps, one at a time, because the code belongs to a particular screen: asking for it before
 * one is chosen would be asking for a number the user cannot see yet.
 *
 * The success wording says **sent**, never *playing*. A receiver answers a wrong code with silence,
 * so this machine genuinely cannot tell a mistyped code from a screen that stopped listening, and
 * saying "playing" would state as fact something it does not know.
 */
@Composable
internal fun CastSendDialog(
    state: CastSendState,
    onSearchAgain: () -> Unit,
    onChoose: (CastTarget) -> Unit,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onClose: () -> Unit,
    text: DesktopStrings,
    /** Reaches a screen by typed address, offered only when the search found nothing. */
    onConnectToAddress: (String) -> Unit = {},
    /** Whether the last typed address reached nothing, so the field can say so. */
    onManualAddressFailed: Boolean = false,
    onClearManualFailure: () -> Unit = {},
) {
    if (state == CastSendState.Idle) return
    val strings = text.shareStrings.cast

    AlertDialog(
        onDismissRequest = onClose,
        containerColor = BuroColors.Surface,
        title = { Text(strings.castTitle, color = BuroColors.Text, fontWeight = FontWeight.Bold) },
        text = {
            when (state) {
                CastSendState.Idle -> Unit

                CastSendState.Searching ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Text(
                            text = strings.castSearching,
                            color = BuroColors.TextMuted,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }

                is CastSendState.Found ->
                    if (state.targets.isEmpty()) {
                        // An empty result is ordinary rather than broken: plenty of home routers
                        // keep wifi and ethernet apart and drop the broadcast. Saying so is more
                        // useful than an error, because the fix is on the router and not here.
                        //
                        // But saying so was *all* this did, which left the household with a dialog
                        // offering only a search that would keep failing. Confirmed on a real
                        // network: the broadcast went unanswered while the phone sat on the same
                        // wifi listening, and a probe straight to its address replied at once. The
                        // field below is the way through, and it is offered only here — typing an
                        // address is a fallback, not how this is meant to be used.
                        Column {
                            Text(strings.castNoneFound, color = BuroColors.TextMuted)
                            Spacer(Modifier.height(BuroSpacing.Md))
                            CastManualAddressField(
                                strings = strings,
                                failed = onManualAddressFailed,
                                onConnect = onConnectToAddress,
                                onEdited = onClearManualFailure,
                            )
                        }
                    } else {
                        Column {
                            state.targets.forEach { target ->
                                BuroInteractiveSurface(
                                    onClick = { onChoose(target) },
                                    shape = BuroRadius.Small,
                                    background = BuroColors.SurfaceHover,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                ) { _ ->
                                    Column(Modifier.padding(12.dp)) {
                                        Text(target.displayName, color = BuroColors.Text)
                                        Text(
                                            target.address,
                                            color = BuroColors.TextMuted,
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                }
                            }
                        }
                    }

                is CastSendState.NeedsCode -> {
                    var code by remember(state.target) { mutableStateOf("") }
                    Column {
                        Text(
                            strings.castCodePrompt.format(state.target.displayName),
                            color = BuroColors.Text,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            strings.castCodeHint,
                            color = BuroColors.TextMuted,
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = code,
                            // Filtered as it is typed rather than validated on submit: the code is
                            // four digits and nothing else, so a letter is a keystroke to ignore
                            // rather than an error to report.
                            onValueChange = { typed ->
                                code = typed.filter(Char::isDigit).take(CastMessage.PAIRING_CODE_LENGTH)
                            },
                            singleLine = true,
                            isError = state.badCode,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (state.badCode) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                strings.castCodeInvalid,
                                color = BuroColors.TextMuted,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { onSend(code) },
                            // Enabled only on a complete code, so the button cannot send something
                            // the receiver will discard without saying anything.
                            enabled = code.length == CastMessage.PAIRING_CODE_LENGTH,
                            colors = ButtonDefaults.buttonColors(containerColor = BuroColors.Primary),
                        ) {
                            Text(strings.castSend, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                is CastSendState.Sending ->
                    Text(strings.castSending.format(state.target.displayName), color = BuroColors.TextMuted)

                is CastSendState.Sent ->
                    Text(strings.castSent.format(state.target.displayName), color = BuroColors.Text)

                is CastSendState.Failed ->
                    Text(strings.castFailed.format(state.target.displayName), color = BuroColors.Text)
            }
        },
        confirmButton = {
            when (state) {
                is CastSendState.Found ->
                    OutlinedButton(
                        onClick = onSearchAgain,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = BuroColors.Text),
                    ) {
                        Text(strings.castSearchAgain, fontWeight = FontWeight.SemiBold)
                    }

                is CastSendState.NeedsCode, is CastSendState.Failed ->
                    OutlinedButton(
                        onClick = onBack,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = BuroColors.Text),
                    ) {
                        Text(strings.castChooseAnother, fontWeight = FontWeight.SemiBold)
                    }

                else -> Unit
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onClose,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = BuroColors.Text),
            ) {
                Text(text.close, fontWeight = FontWeight.SemiBold)
            }
        },
    )
}

/**
 * A bulk download waiting to be confirmed.
 *
 * Carries the episodes themselves rather than a season number to look up later, so what the dialog
 * counts and what the confirmation queues cannot drift apart. Already-stored episodes are filtered
 * out before this is built, which is what makes the promised number the real one.
 */
private sealed interface BulkDownload {
    val episodes: List<XtreamEpisode>

    data class WholeSeries(override val episodes: List<XtreamEpisode>) : BulkDownload

    data class Season(val number: Int?, override val episodes: List<XtreamEpisode>) : BulkDownload
}

/**
 * Asks before queueing a season or a whole series.
 *
 * Deliberately plain: a question, the count, and two buttons. A season can be dozens of gigabytes,
 * and the count is on its own line because the number is the whole decision — twelve episodes and
 * eighty are very different answers to "is there room on this disk".
 */
@Composable
private fun BulkDownloadDialog(
    episodeCount: Int,
    seasonNumber: Int?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    text: DesktopStrings,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BuroColors.Surface,
        title = {
            Text(
                text =
                    if (seasonNumber != null) {
                        text.downloadStrings.downloadSeasonConfirmTitle.format(seasonNumber)
                    } else {
                        text.downloadStrings.downloadSeriesConfirmTitle
                    },
                color = BuroColors.Text,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(
                text = text.downloadStrings.downloadConfirmBody.format(episodeCount),
                color = BuroColors.TextMuted,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = BuroColors.Primary),
            ) {
                Text(text.downloadStrings.downloadConfirmAction, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = BuroColors.Text),
            ) {
                Text(text.cancel, fontWeight = FontWeight.SemiBold)
            }
        },
    )
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
        // Zero means unrated, not nought out of ten. Providers send 0 for anything nobody has
        // scored, so a title released days ago carried "★ 0,0" beside its year — the worst possible
        // verdict, printed for a series that simply has not been reviewed yet.
        item.rating?.takeIf { value -> value > 0.0 }?.let { add("★ ${"%.1f".format(it)}") }
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
 * Episodes drawn before the "show more" button appears.
 *
 * Chosen to cover an ordinary season in one go — most run to twenty-something — so the paging is
 * invisible for almost every series and only shows up where it is needed. The rows are composed
 * eagerly inside a scrolling column, so this is the number that decides whether the page appears
 * at all on something like One Piece.
 */
private const val EPISODE_PAGE_SIZE = 40

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
                        // On `selected` rather than on `active`, so the compiler proves the value
                        // is there instead of a `!!` asserting it. The two conditions are the same
                        // today; a `!!` is a crash waiting for the day they are not.
                        if (selected != null) {
                            "★ ${selected.toInt()}+  ▾"
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

/**
 * Typing a screen's address, for a network where the search finds nothing.
 *
 * Refused before any packet leaves when the text is not a private IPv4 address — see
 * `CastReceiver.isPlausibleHost`. Checking here as well as there means a typo is answered at once
 * rather than after a probe times out, and the message says what is wrong instead of "nothing
 * answered", which would send somebody looking at their router for a mistyped digit.
 */
@Composable
private fun CastManualAddressField(
    strings: CastStrings,
    failed: Boolean,
    onConnect: (String) -> Unit,
    onEdited: () -> Unit,
) {
    var address by remember { mutableStateOf("") }
    val plausible = remember(address) { CastReceiver.isPlausibleHost(address.trim()) }

    Text(strings.castManualTitle, color = BuroColors.Text, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(BuroSpacing.Xs))
    Text(
        strings.castManualHint,
        color = BuroColors.TextMuted,
        style = MaterialTheme.typography.labelSmall,
    )
    Spacer(Modifier.height(BuroSpacing.Sm))
    OutlinedTextField(
        value = address,
        onValueChange = { typed ->
            // Trimmed as it is typed: an address pasted from the other screen commonly arrives with
            // a trailing space, and refusing it for that would look like the address is wrong.
            address = typed.trim().take(15)
            onEdited()
        },
        label = { Text(strings.castManualLabel) },
        singleLine = true,
        isError = failed,
        modifier = Modifier.fillMaxWidth(),
    )
    if (failed) {
        Spacer(Modifier.height(BuroSpacing.Xs))
        Text(
            strings.castManualInvalid,
            color = BuroColors.TextMuted,
            style = MaterialTheme.typography.labelSmall,
        )
    }
    Spacer(Modifier.height(BuroSpacing.Sm))
    BuroInteractiveRow(
        onClick = { onConnect(address.trim()) },
        selected = false,
        enabled = plausible,
        shape = BuroRadius.Small,
    ) { _ ->
        Text(
            strings.castManualConnect,
            color = if (plausible) BuroColors.Text else BuroColors.TextMuted,
            modifier = Modifier.padding(horizontal = BuroSpacing.Md, vertical = BuroSpacing.Sm),
        )
    }
}

/**
 * What people thought of the title, when enough of them said so.
 *
 * One score, shown with the mark of the service that produced it.
 *
 * The model is the phone app's row of a tomato and a bucket of popcorn. The tomato is deliberately
 * not used here: those are Rotten Tomatoes' Tomatometer and Popcornmeter, computed from critics'
 * reviews and sold through a paid Fandango licence, and this number is TMDb's own users voting. The
 * two disagree routinely — the same film can be 80% on one and 68% on the other — so a tomato drawn
 * beside a TMDb figure would not be a decoration, it would be a score attributed to a company that
 * never gave it. The mark shown is therefore TMDb's, which is whose score this is.
 *
 * Absent when nobody has voted. TMDb answers with 0.0 and a count of zero for titles it holds but
 * nobody rated, and "0%" reads as a verdict rather than as the absence of one.
 */
@Composable
private fun RatingsBlock(
    score: TmdbAudienceScore?,
    critics: CriticScores? = null,
) {
    val text = strings.shareStrings.ratings
    val average = score?.average?.takeIf { value -> value > 0.0 } ?: return
    val votes = score.voteCount
    if (votes < MINIMUM_VOTES) return

    Column(modifier = Modifier.padding(bottom = 14.dp)) {
        Text(
            text = text.title,
            color = BuroColors.TextMuted,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            // A lettered chip, for the same reason the critics' scores below use one.
            //
            // This slot used to fetch a URL from TMDb's image CDN held in a constant called
            // TMDB_MARK_URL and documented as "TMDb's own mark". It was not: that path is a *watch
            // provider* logo, and the file behind it is Netflix's wordmark. So the panel drew the
            // Netflix logo beside the words "Nota TMDb" — a score credited on screen to a company
            // that had no part in producing it, which is precisely the misattribution the comment
            // here claimed to be avoiding.
            //
            // TMDb's real logo is an SVG on their website behind a content-hashed path; it is not on
            // the image CDN, this app's loader draws bitmaps, and the hash changes when they deploy.
            // Letters in their brand colour say whose number it is and cannot silently become
            // somebody else's.
            Box(
                modifier =
                    Modifier
                        .size(width = SCORE_MARK_WIDTH, height = SCORE_MARK_HEIGHT)
                        .clip(BuroRadius.Small)
                        .background(TMDB_BRAND_TEAL),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "TMDb",
                    color = CriticInkDark,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    softWrap = false,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(BuroSpacing.Sm))
            // As a percentage, which is how people read a score at a glance — TMDb publishes out of
            // ten, and "76%" lands faster than "7,6".
            Text(
                text = "${(average * 10).toInt()}%",
                color = BuroColors.Primary,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(BuroSpacing.Sm))
            Column {
                Text(
                    text = text.source,
                    color = BuroColors.Text,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    // The count is what separates a score worth reading from one three people gave.
                    text = text.votes.format(formatVotes(votes)),
                    color = BuroColors.TextSubtle,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        // Absent unless an OMDb key is configured and that service had something to say, so the
        // panel is exactly as it was before for everyone who has not set one up.
        if (critics != null && critics.hasAny) {
            Spacer(Modifier.height(BuroSpacing.Md))
            Text(
                text = text.critics,
                color = BuroColors.TextMuted,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Lg)) {
                critics.tomatometer?.let { percent ->
                    CriticScore("$percent%", CriticMarkTomatometer, "Tomatometer")
                }
                critics.metascore?.let { percent ->
                    CriticScore("$percent%", criticMarkMetascore(percent), "Metascore")
                }
                critics.imdbRating?.let { rating ->
                    CriticScore("%.1f".format(rating), CriticMarkImdb, "IMDb")
                }
            }
        }
    }
}

/**
 * One critic's verdict, under the name of whoever reached it.
 *
 * A lettered chip in the company's colour rather than their logo. Rotten Tomatoes' tomato and
 * Metacritic's shield are licensed images with no public address to fetch them from — unlike TMDb's,
 * which the block above uses — and copying either into this repository is the thing the project does
 * not do.
 *
 * The chip used to be a plain coloured dot, which was reported as the icons being missing: three
 * bullets in three colours identify nothing to somebody who has not learnt the code. Two or three
 * letters on the brand colour is as far as this can honestly go — it reads as the source at a
 * glance, and IMDb's own mark is lettering on yellow, so that one lands almost exactly right.
 */
@Composable
private fun CriticScore(
    value: String,
    /** Which company's measure this is, and how it identifies itself. */
    mark: CriticMark,
    source: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier =
                Modifier
                    .size(width = CRITIC_CHIP_WIDTH, height = CRITIC_CHIP_HEIGHT)
                    .clip(BuroRadius.Small)
                    .background(mark.accent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = mark.initials,
                color = mark.ink,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                // A brand's short form is never worth breaking, and the chip is deliberately tight
                // around it.
                softWrap = false,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(BuroSpacing.Sm))
        Column {
            Text(
                text = value,
                color = BuroColors.Text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                // Never translated: these are the companies' own names for their own measures.
                text = source,
                color = BuroColors.TextSubtle,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

// The colours and letters themselves live in `ui/CriticMark.kt`, beside the other identity rules the
// design system owns, so the Metascore bands can be tested without standing up this whole screen.

/** Wide enough for three letters ("IMDb", "RT", "MC") without the chip becoming a button. */
private val CRITIC_CHIP_WIDTH = 34.dp

/** Matched to the cap height of the score beside it, so the chip reads as its label. */
private val CRITIC_CHIP_HEIGHT = 18.dp

/** The teal of TMDb's own wordmark, which is the half of their gradient that reads on this canvas. */
private val TMDB_BRAND_TEAL = Color(0xFF01B4E4)

/** Wide enough for "TMDb" without the chip reading as a button. */
private val SCORE_MARK_WIDTH = 44.dp

/** Matched to the score's cap height, so the mark reads as a label on it. */
private val SCORE_MARK_HEIGHT = 22.dp

/** Thousands as "1,2 mil": an exact five-digit count is noise beside the score it qualifies. */
private fun formatVotes(votes: Int): String =
    if (votes >= 1_000) "%.1f mil".format(votes / 1_000.0) else votes.toString()

/**
 * Below this a score is one opinion rather than a verdict.
 *
 * TMDb will happily report 10.0 from two votes, and a page announcing 100% on that basis is worse
 * than one saying nothing.
 */
private const val MINIMUM_VOTES = 20

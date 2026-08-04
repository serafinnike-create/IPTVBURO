package com.lucasserafin94.iptvburo.desktop.app

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lucasserafin94.iptvburo.desktop.DailyHomeStatus
import com.lucasserafin94.iptvburo.desktop.DesktopAppState
import com.lucasserafin94.iptvburo.desktop.DesktopContinueWatchingEntry
import com.lucasserafin94.iptvburo.desktop.data.contentIdentity
import com.lucasserafin94.iptvburo.desktop.model.XtreamPlaybackTarget
import com.lucasserafin94.iptvburo.desktop.ui.BuroColors
import com.lucasserafin94.iptvburo.desktop.ui.BuroInteractiveSurface
import com.lucasserafin94.iptvburo.desktop.ui.BuroMotion
import com.lucasserafin94.iptvburo.desktop.ui.BuroRadius
import com.lucasserafin94.iptvburo.desktop.ui.BuroRemoteArtwork
import com.lucasserafin94.iptvburo.desktop.ui.BuroScrim
import com.lucasserafin94.iptvburo.desktop.ui.BuroSpacing
import com.lucasserafin94.iptvburo.desktop.ui.arrowScrollable
import com.lucasserafin94.iptvburo.desktop.ui.edgeScrollable
import com.lucasserafin94.iptvburo.desktop.ui.edgeScrollableVertically
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.foundation.lazy.rememberLazyListState
import com.lucasserafin94.iptvburo.desktop.ui.DesktopStrings
import com.lucasserafin94.iptvburo.desktop.ui.editorialTitle
import com.lucasserafin94.iptvburo.desktop.ui.strings
import com.lucasserafin94.iptvburo.xtream.XtreamCatalogItem
import com.lucasserafin94.iptvburo.xtream.XtreamContentType
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Layout constants resolved once per window size.
 *
 * The gutter is shared by rail titles and the first card of every rail. Before this existed the two
 * used different paddings, so every rail read as misaligned against its own heading.
 */
private data class HomeMetrics(
    val gutter: Dp,
    val heroHeight: Dp,
    val posterWidth: Dp,
    val landscapeWidth: Dp,
    val cardSpacing: Dp,
    val railSpacing: Dp,
    val wide: Boolean,
) {
    companion object {
        fun resolve(
            maxWidth: Dp,
            maxHeight: Dp,
        ): HomeMetrics {
            val wide = maxWidth >= 1_280.dp
            // The GDD asks for a hero of roughly 42–58% of usable height. Clamping keeps it
            // cinematic on a 4K panel without swallowing a 768 px laptop screen whole.
            val hero = (maxHeight * 0.52f).coerceIn(300.dp, 560.dp)
            return HomeMetrics(
                gutter = if (wide) BuroSpacing.GutterWide else BuroSpacing.GutterCompact,
                heroHeight = hero,
                posterWidth = if (wide) 176.dp else 150.dp,
                landscapeWidth = if (wide) 300.dp else 250.dp,
                cardSpacing = if (wide) BuroSpacing.Md else BuroSpacing.Sm,
                railSpacing = if (wide) BuroSpacing.Xl else BuroSpacing.Lg,
                wide = wide,
            )
        }
    }
}

/** About one shelf, so an arrow press moves between rails rather than by a few pixels. */
private const val HOME_SCROLL_PIXELS = 300f

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun XtreamDailyHome(
    appState: DesktopAppState,
    onOpenExternal: (PendingXtreamExternal) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val text = strings
    var today by remember { mutableStateOf(LocalDate.now()) }
    var detailsOpen by remember { mutableStateOf(false) }
    var personOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            val current = LocalDate.now()
            if (current != today) today = current
            delay(60_000)
        }
    }
    LaunchedEffect(
        today,
        appState.xtreamSummary?.sourceId,
        appState.activeProfileId,
        // Included so "Atualizar listas" can ask for a rebuild: the source id is unchanged by a
        // refresh, so without this key the effect would never re-run.
        appState.dailyHomeRevision,
    ) {
        appState.loadDailyHome(today)
    }
    LaunchedEffect(appState.selectedXtreamItem?.providerId, detailsOpen) {
        if (!detailsOpen) return@LaunchedEffect
        when (appState.selectedXtreamItem?.contentType) {
            XtreamContentType.MOVIE -> appState.loadSelectedMovieDetails()
            XtreamContentType.SERIES -> appState.loadSelectedSeriesDetails()
            XtreamContentType.LIVE -> appState.loadSelectedLiveEpg()
            null -> Unit
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

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(BuroColors.Canvas)) {
        val metrics = HomeMetrics.resolve(maxWidth, maxHeight)
        val openDetails: (XtreamCatalogItem) -> Unit = { item ->
            appState.selectDailyItem(item)
            detailsOpen = true
        }

        when (val status = appState.dailyHomeStatus) {
            DailyHomeStatus.Idle,
            DailyHomeStatus.Loading,
            -> HomeSkeleton(metrics, text)

            is DailyHomeStatus.Error ->
                HomeError(status.message, text) { scope.launch { appState.loadDailyHome(today) } }

            is DailyHomeStatus.Loaded -> {
                val snapshot = status.snapshot
                val homeState = rememberLazyListState()
                val homeScope = rememberCoroutineScope()
                val homeFocus = remember { FocusRequester() }
                LaunchedEffect(Unit) { runCatching { homeFocus.requestFocus() } }
                Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = homeState,
                    // The shelves were always here; there was simply no way to reach them. The page
                    // scrolled only by wheel, with nothing on screen saying so, so the film and
                    // series rails below the fold looked like they had never loaded at all.
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .focusRequester(homeFocus)
                            .focusable()
                            .onPointerEvent(PointerEventType.Enter) {
                                runCatching { homeFocus.requestFocus() }
                            }.edgeScrollableVertically(homeState)
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                val delta =
                                    when (event.key) {
                                        Key.DirectionDown -> HOME_SCROLL_PIXELS
                                        Key.DirectionUp -> -HOME_SCROLL_PIXELS
                                        Key.PageDown -> HOME_SCROLL_PIXELS * 3
                                        Key.PageUp -> -HOME_SCROLL_PIXELS * 3
                                        else -> return@onPreviewKeyEvent false
                                    }
                                homeScope.launch { homeState.animateScrollBy(delta) }
                                true
                            },
                    verticalArrangement = Arrangement.spacedBy(metrics.railSpacing),
                ) {
                    item(key = "daily-hero") {
                        DailyHero(
                            item = snapshot.hero,
                            date = snapshot.date,
                            metrics = metrics,
                            text = text,
                            onDetails = openDetails,
                            onPlay = { item ->
                                onOpenExternal(
                                    PendingXtreamExternal(
                                        displayName = item.name.editorialTitle(),
                                        target =
                                            XtreamPlaybackTarget.CatalogItem(
                                                providerId = item.providerId,
                                                contentType = item.contentType,
                                                containerExtension = item.containerExtension,
                                                contentKey = item.contentIdentity().key,
                                            ),
                                    ),
                                )
                            },
                        )
                    }
                    if (appState.continueWatchingEntries.isNotEmpty()) {
                        item(key = "continue-watching") {
                            ContinueWatchingRow(
                                entries = appState.continueWatchingEntries,
                                metrics = metrics,
                                text = text,
                            ) { entry -> openDetails(entry.item) }
                        }
                    }
                    // Above the daily picks: the seasonal rail is the reason the home screen looks
                    // different today, so burying it under the everyday rows defeats the point.
                    snapshot.seasonal?.let { seasonal ->
                        item(key = "seasonal-${seasonal.collection.id}") {
                            SeasonalRow(
                                // Resolved here rather than when the snapshot was built, so
                                // switching language retitles the rail without paging again.
                                title = seasonal.collection.title(appState.language.tag),
                                items = seasonal.items,
                                metrics = metrics,
                                text = text,
                                onClick = openDetails,
                            )
                        }
                    }
                    item(key = "daily-movies") {
                        DailyRow(text.moviesForToday, snapshot.movies, metrics, text, openDetails)
                    }
                    item(key = "daily-series") {
                        DailyRow(text.seriesToExplore, snapshot.series, metrics, text, openDetails)
                    }
                    item(key = "daily-live") {
                        DailyRow(text.liveNow, snapshot.live, metrics, text, openDetails)
                    }
                    item(key = "home-tail") { Spacer(Modifier.height(metrics.railSpacing)) }
                }
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(homeState),
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
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Hero
// ---------------------------------------------------------------------------------------------

@Composable
private fun DailyHero(
    item: XtreamCatalogItem?,
    date: LocalDate,
    metrics: HomeMetrics,
    text: DesktopStrings,
    onDetails: (XtreamCatalogItem) -> Unit,
    onPlay: (XtreamCatalogItem) -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth().height(metrics.heroHeight).background(BuroColors.Surface)) {
        if (item != null) {
            BuroRemoteArtwork(
                artworkUrl = item.artworkUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            ) { HeroArtFallback() }
        } else {
            HeroArtFallback()
        }

        // Two passes: horizontal protects the copy column, vertical anchors the hero to the rail
        // underneath so the composition does not float.
        Box(Modifier.fillMaxSize().background(BuroScrim.heroHorizontal()))
        Box(Modifier.fillMaxSize().background(BuroScrim.heroVertical()))

        Column(
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(horizontal = metrics.gutter)
                    .widthIn(max = if (metrics.wide) 620.dp else 520.dp),
            verticalArrangement = Arrangement.spacedBy(BuroSpacing.Sm),
        ) {
            HeroEyebrow(
                "${text.dailySelection} · " +
                    "${date.dayOfMonth.pad2()}/${date.monthValue.pad2()}",
            )
            Text(
                text = item?.name?.editorialTitle() ?: text.heroFallbackTitle,
                color = BuroColors.Text,
                style =
                    if (metrics.wide) {
                        MaterialTheme.typography.displayLarge
                    } else {
                        MaterialTheme.typography.displaySmall
                    },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            item?.heroFacts(text)?.takeIf(String::isNotBlank)?.let { facts ->
                Text(
                    text = facts,
                    color = BuroColors.Accent,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = text.heroSubtitle,
                color = BuroColors.TextMuted,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            item?.let { selected ->
                Spacer(Modifier.height(BuroSpacing.Xxs))
                Row(horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Sm)) {
                    // Series need an episode before anything can play, so the direct Play action
                    // is only offered for content that resolves to a single stream.
                    if (selected.contentType != XtreamContentType.SERIES) {
                        Button(
                            onClick = { onPlay(selected) },
                            shape = BuroRadius.Small,
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = BuroColors.Primary,
                                    contentColor = BuroColors.OnPrimary,
                                ),
                            contentPadding =
                                PaddingValues(horizontal = BuroSpacing.Lg, vertical = 14.dp),
                        ) {
                            Text("▶  ${text.watchNow}", fontWeight = FontWeight.Bold)
                        }
                    }
                    OutlinedButton(
                        onClick = { onDetails(selected) },
                        shape = BuroRadius.Small,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = BuroColors.Text),
                        contentPadding =
                            PaddingValues(horizontal = BuroSpacing.Lg, vertical = 14.dp),
                    ) {
                        Text(text.details, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroArtFallback() {
    Box(
        Modifier.fillMaxSize().background(
            Brush.linearGradient(
                listOf(
                    BuroColors.SurfaceRaised,
                    BuroColors.Primary.copy(alpha = 0.16f),
                    BuroColors.Canvas,
                ),
            ),
        ),
    )
}

@Composable
private fun HeroEyebrow(label: String) {
    Box(
        modifier =
            Modifier
                .clip(BuroRadius.Pill)
                .background(BuroColors.Primary.copy(alpha = 0.14f))
                .border(1.dp, BuroColors.Primary.copy(alpha = 0.38f), BuroRadius.Pill)
                .padding(horizontal = 13.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            color = BuroColors.Primary,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Rails
// ---------------------------------------------------------------------------------------------

@Composable
private fun DailyRow(
    title: String,
    items: List<XtreamCatalogItem>,
    metrics: HomeMetrics,
    text: DesktopStrings,
    onClick: (XtreamCatalogItem) -> Unit,
) {
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(BuroSpacing.Sm)) {
        RailHeader(title, "${items.size} ${text.options}", metrics)
        val railState = rememberLazyListState()
        LazyRow(
            state = railState,
            // Arrow keys move the rail the pointer is over: with eighteen titles per shelf, the
            // ones past the right edge were reachable only by dragging.
            modifier = Modifier.fillMaxWidth().arrowScrollable(railState).edgeScrollable(railState),
            horizontalArrangement = Arrangement.spacedBy(metrics.cardSpacing),
            // contentPadding rather than Spacer items: the first card now lines up with the rail
            // title, and scrollToItem indices stay meaningful.
            // Vertical padding is not decorative: a lazy row clips to its bounds, so without room
            // for the hover scale the top and bottom of a lifted card get sliced off.
            contentPadding = PaddingValues(horizontal = metrics.gutter, vertical = BuroSpacing.Sm),
        ) {
            items(items, key = { "${it.contentType}:${it.providerId}" }) { item ->
                DailyCard(item, metrics, text, onClick)
            }
        }
    }
}

/**
 * The calendar-driven rail.
 *
 * Identical to [DailyRow] apart from its badge, which says why the rail is there at all — without
 * it "Especial de Natal" reads as an ordinary category the user never asked for. The empty guard is
 * kept even though the snapshot already drops empty collections: nothing about this row is worth a
 * heading with no titles under it.
 */
@Composable
private fun SeasonalRow(
    title: String,
    items: List<XtreamCatalogItem>,
    metrics: HomeMetrics,
    text: DesktopStrings,
    onClick: (XtreamCatalogItem) -> Unit,
) {
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(BuroSpacing.Sm)) {
        RailHeader(title, text.seasonalBadge, metrics)
        val railState = rememberLazyListState()
        LazyRow(
            state = railState,
            modifier = Modifier.fillMaxWidth().arrowScrollable(railState).edgeScrollable(railState),
            horizontalArrangement = Arrangement.spacedBy(metrics.cardSpacing),
            contentPadding = PaddingValues(horizontal = metrics.gutter, vertical = BuroSpacing.Sm),
        ) {
            items(items, key = { "seasonal:${it.contentType}:${it.providerId}" }) { item ->
                DailyCard(item, metrics, text, onClick)
            }
        }
    }
}

@Composable
private fun RailHeader(
    title: String,
    trailing: String?,
    metrics: HomeMetrics,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = metrics.gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = BuroColors.Text,
            style =
                if (metrics.wide) {
                    MaterialTheme.typography.headlineSmall
                } else {
                    MaterialTheme.typography.titleLarge
                },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            Text(
                text = trailing,
                color = BuroColors.TextSubtle,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun DailyCard(
    item: XtreamCatalogItem,
    metrics: HomeMetrics,
    text: DesktopStrings,
    onClick: (XtreamCatalogItem) -> Unit,
) {
    val live = item.contentType == XtreamContentType.LIVE
    val width = if (live) metrics.landscapeWidth else metrics.posterWidth
    val artHeight = if (live) width * 9f / 16f else width * 3f / 2f
    val title = item.name.editorialTitle()

    BuroInteractiveSurface(
        onClick = { onClick(item) },
        modifier = Modifier.width(width),
        shape = BuroRadius.Medium,
        compact = !live,
        contentDescription = title,
    ) { state ->
        Column {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(artHeight)
                        .clip(BuroRadius.Medium)
                        .background(BuroColors.SurfaceRaised),
            ) {
                BuroRemoteArtwork(
                    artworkUrl = item.artworkUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    // Live logos are rarely 16:9 and get badly cropped; posters are authored 2:3
                    // and should fill the frame.
                    contentScale = if (live) ContentScale.Fit else ContentScale.Crop,
                ) { CardArtFallback(title) }

                if (live) {
                    LiveBadge(text, Modifier.align(Alignment.TopStart).padding(BuroSpacing.Xs))
                }
                item.rating?.takeIf { it > 0.0 }?.let { rating ->
                    RatingChip(
                        rating,
                        Modifier.align(Alignment.TopEnd).padding(BuroSpacing.Xs),
                    )
                }
                // The footer scrim only appears on hover so resting rails stay clean, matching the
                // GDD rule that art stays dominant until the user shows intent.
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(artHeight * 0.4f)
                            .alpha(if (state.active) 1f else 0f)
                            .background(BuroScrim.cardFooter()),
                )
            }
            Spacer(Modifier.height(BuroSpacing.Xs))
            Text(
                text = title,
                color = BuroColors.Text,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.cardFacts(text),
                color = BuroColors.TextSubtle,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ContinueWatchingRow(
    entries: List<DesktopContinueWatchingEntry>,
    metrics: HomeMetrics,
    text: DesktopStrings,
    onClick: (DesktopContinueWatchingEntry) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(BuroSpacing.Sm)) {
        RailHeader(text.continueWatching, null, metrics)
        val railState = rememberLazyListState()
        LazyRow(
            state = railState,
            // Arrow keys move the rail the pointer is over: with eighteen titles per shelf, the
            // ones past the right edge were reachable only by dragging.
            modifier = Modifier.fillMaxWidth().arrowScrollable(railState).edgeScrollable(railState),
            horizontalArrangement = Arrangement.spacedBy(metrics.cardSpacing),
            // Vertical padding is not decorative: a lazy row clips to its bounds, so without room
            // for the hover scale the top and bottom of a lifted card get sliced off.
            contentPadding = PaddingValues(horizontal = metrics.gutter, vertical = BuroSpacing.Sm),
        ) {
            items(
                items = entries,
                key = { "${it.progress.identity.contentType}:${it.progress.identity.contentId}" },
            ) { entry ->
                val title = entry.item.name.editorialTitle()
                val fraction = entry.progress.progressPercent.toFloat().coerceIn(0f, 1f)
                BuroInteractiveSurface(
                    onClick = { onClick(entry) },
                    modifier = Modifier.width(metrics.landscapeWidth),
                    shape = BuroRadius.Medium,
                    contentDescription = title,
                ) {
                    Column {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(metrics.landscapeWidth * 9f / 16f)
                                    .clip(BuroRadius.Medium)
                                    .background(BuroColors.SurfaceRaised),
                        ) {
                            BuroRemoteArtwork(
                                artworkUrl = entry.item.artworkUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            ) { CardArtFallback(title) }
                            Box(
                                Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .background(BuroColors.Canvas.copy(alpha = 0.7f)),
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(fraction)
                                        .fillMaxHeight()
                                        .background(BuroColors.Primary),
                                )
                            }
                        }
                        Spacer(Modifier.height(BuroSpacing.Xs))
                        Text(
                            text = title,
                            color = BuroColors.Text,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val identity = entry.progress.identity
                        Text(
                            text =
                                identity.seasonNumber?.let { season ->
                                    "T$season · E${identity.episodeNumber ?: "—"}"
                                } ?: "${(fraction * 100).toInt()}% ${text.watched}",
                            color = BuroColors.TextSubtle,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Small parts
// ---------------------------------------------------------------------------------------------

/**
 * Placeholder for content the provider ships without artwork.
 *
 * A single grey letter reads as a broken image. Tinting the gradient from the title makes each
 * placeholder distinct and deliberate, and the title is repeated inside the frame so the card is
 * still identifiable at a glance.
 */
@Composable
private fun CardArtFallback(title: String) {
    val tint = remember(title) { placeholderTint(title) }
    Box(
        modifier =
            Modifier.fillMaxSize().background(
                Brush.linearGradient(
                    listOf(
                        tint.copy(alpha = 0.55f),
                        BuroColors.SurfaceRaised,
                        BuroColors.Canvas,
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(BuroSpacing.Sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(BuroSpacing.Xs),
        ) {
            Text(
                text = title.firstOrNull()?.uppercase() ?: "B",
                color = BuroColors.Text.copy(alpha = 0.85f),
                style = MaterialTheme.typography.displaySmall,
            )
            Text(
                text = title,
                color = BuroColors.TextMuted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

/** Stable hue per title, so the same item always gets the same placeholder. */
private fun placeholderTint(title: String): Color {
    val palette =
        listOf(
            Color(0xFF2E3A59),
            Color(0xFF4A2E3A),
            Color(0xFF2E4A3F),
            Color(0xFF473A2E),
            Color(0xFF3A2E4A),
            Color(0xFF2E4550),
        )
    return palette[(title.hashCode().toUInt() % palette.size.toUInt()).toInt()]
}

@Composable
private fun LiveBadge(
    text: DesktopStrings,
    modifier: Modifier,
) {
    Row(
        modifier =
            modifier
                .clip(BuroRadius.Pill)
                .background(BuroColors.Canvas.copy(alpha = 0.78f))
                .padding(horizontal = BuroSpacing.Xs, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(BuroColors.Error))
        Spacer(Modifier.width(6.dp))
        Text(text.onAir, color = BuroColors.Text, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun RatingChip(
    rating: Double,
    modifier: Modifier,
) {
    Box(
        modifier =
            modifier
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

// ---------------------------------------------------------------------------------------------
// States
// ---------------------------------------------------------------------------------------------

/**
 * Skeleton shaped like the real home rather than a centred spinner.
 *
 * Holding the final layout while data loads stops the whole screen jumping when the catalogue
 * arrives, which is the difference between "loading" and "broken" at a glance.
 */
@Composable
private fun HomeSkeleton(
    metrics: HomeMetrics,
    text: DesktopStrings,
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val pulse by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.85f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(900, easing = BuroMotion.EnterEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "skeleton-pulse",
    )

    Column(
        modifier = Modifier.fillMaxSize().background(BuroColors.Canvas),
        verticalArrangement = Arrangement.spacedBy(metrics.railSpacing),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(metrics.heroHeight)
                    .background(BuroColors.SurfaceRaised.copy(alpha = pulse)),
            contentAlignment = Alignment.CenterStart,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = metrics.gutter),
                verticalArrangement = Arrangement.spacedBy(BuroSpacing.Sm),
            ) {
                SkeletonBar(200.dp, 20.dp, pulse)
                SkeletonBar(440.dp, 44.dp, pulse)
                SkeletonBar(360.dp, 18.dp, pulse)
                Text(
                    text = text.organizingToday,
                    color = BuroColors.TextSubtle,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        repeat(2) { index ->
            Column(
                modifier = Modifier.padding(horizontal = metrics.gutter),
                verticalArrangement = Arrangement.spacedBy(BuroSpacing.Sm),
            ) {
                SkeletonBar(240.dp, 22.dp, pulse)
                Row(horizontalArrangement = Arrangement.spacedBy(metrics.cardSpacing)) {
                    repeat(if (metrics.wide) 6 else 4) {
                        SkeletonBar(
                            width = metrics.posterWidth,
                            height = metrics.posterWidth * 3f / 2f,
                            alpha = pulse,
                        )
                    }
                }
            }
            if (index == 0) Spacer(Modifier.height(BuroSpacing.Xxs))
        }
    }
}

@Composable
private fun SkeletonBar(
    width: Dp,
    height: Dp,
    alpha: Float,
) {
    Box(
        modifier =
            Modifier
                .width(width)
                .height(height)
                .clip(BuroRadius.Small)
                .background(BuroColors.SurfaceHover.copy(alpha = alpha)),
    )
}

@Composable
private fun HomeError(
    message: String,
    text: DesktopStrings,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().background(BuroColors.Canvas),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = 520.dp)
                    .clip(BuroRadius.Large)
                    .background(BuroColors.Surface)
                    .border(1.dp, BuroColors.BorderSoft, BuroRadius.Large)
                    .padding(BuroSpacing.Xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(BuroSpacing.Md),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(BuroColors.Error.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("!", color = BuroColors.Error, style = MaterialTheme.typography.headlineMedium)
            }
            Text(
                text = message,
                color = BuroColors.TextMuted,
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(
                onClick = onRetry,
                shape = BuroRadius.Small,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = BuroColors.Primary,
                        contentColor = BuroColors.OnPrimary,
                    ),
            ) {
                Text(text.tryAgain, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Formatting
// ---------------------------------------------------------------------------------------------

private fun Int.pad2(): String = toString().padStart(2, '0')

private fun XtreamCatalogItem.heroFacts(text: DesktopStrings): String =
    listOfNotNull(
        when (contentType) {
            XtreamContentType.LIVE -> text.onAir
            XtreamContentType.MOVIE -> text.movies
            XtreamContentType.SERIES -> text.series
        },
        year?.toString(),
        rating?.takeIf { it > 0.0 }?.let { "★ %.1f".format(it) },
    ).joinToString("  ·  ")

private fun XtreamCatalogItem.cardFacts(text: DesktopStrings): String =
    if (contentType == XtreamContentType.LIVE) {
        text.onAir
    } else {
        listOfNotNull(
            year?.toString(),
            rating?.takeIf { it > 0.0 }?.let { "★ %.1f".format(it) },
        ).joinToString(" · ")
    }

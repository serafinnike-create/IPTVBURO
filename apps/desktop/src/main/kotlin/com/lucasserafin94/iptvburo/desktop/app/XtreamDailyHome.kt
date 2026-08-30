package com.lucasserafin94.iptvburo.desktop.app

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.lucasserafin94.iptvburo.domain.model.BannerTrailer
import com.lucasserafin94.iptvburo.desktop.playback.TrailerBrowser
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lucasserafin94.iptvburo.desktop.CreditDestination
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
import com.lucasserafin94.iptvburo.desktop.ui.DesktopStrings
import com.lucasserafin94.iptvburo.desktop.ui.arrowScrollable
import com.lucasserafin94.iptvburo.desktop.ui.edgeScrollable
import com.lucasserafin94.iptvburo.desktop.ui.edgeScrollableVertically
import com.lucasserafin94.iptvburo.desktop.ui.editorialTitle
import com.lucasserafin94.iptvburo.desktop.ui.strings
import com.lucasserafin94.iptvburo.domain.model.ExternalTitle
import com.lucasserafin94.iptvburo.domain.model.ExternalTitleDetails
import com.lucasserafin94.iptvburo.domain.model.OfferType
import com.lucasserafin94.iptvburo.domain.model.StreamingOffer
import com.lucasserafin94.iptvburo.metadata.TmdbServiceShelf
import com.lucasserafin94.iptvburo.metadata.WATCH_PROVIDER_ATTRIBUTION
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

/** How long each banner title holds before the next one. */
private const val HERO_ROTATION_MILLIS = 10_000L

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
    // The streaming rails, loaded once and reused by the Assinaturas area. Keyed on the region so
    // changing it rebuilds them; the call itself no-ops when they are already loaded, so returning
    // to the home screen does not re-hit the network.
    LaunchedEffect(appState.streamingRegion) {
        appState.loadStreamingShelves()
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
                // The local flag has to fall with the shared state.
                //
                // `personOpen` lives in this screen and decides whether the filmography is drawn at
                // all; `selectedPerson` lives in the app state. Clearing only the second left this
                // branch still taken with nothing to show, so the page fell through to Home — which
                // is exactly what a press on a credit did: the actor's screen vanished and the user
                // was returned to the start.
                onOpenCredit = { credit ->
                    // Three flags decide what is on screen, and all three have to move together:
                    // `personOpen` and `detailsOpen` belong to this screen, `selectedPerson` to the
                    // app state. Clearing only the shared one left this branch taken with nothing
                    // to draw; selecting the title without `detailsOpen` left the user on the Home
                    // with the right film selected and no page showing it.
                    when (appState.openCredit(credit)) {
                        CreditDestination.PLAYLIST_ITEM -> {
                            personOpen = false
                            detailsOpen = true
                        }
                        // Assinaturas is a destination of its own, so this screen steps aside
                        // entirely rather than opening a details page over it.
                        CreditDestination.SUBSCRIPTIONS -> personOpen = false
                        // Nothing could be done. The filmography stays open rather than dropping
                        // the user somewhere they did not ask to go.
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
            onBack = { detailsOpen = false },
            onOpenExternal = onOpenExternal,
            onOpenPerson = { name ->
                personOpen = true
                scope.launch { appState.openPerson(name) }
            },
            // The same three flags the credit handler moves, for the same reason: selecting a
            // title is not showing it, and the destination decides which screen is right. A title
            // that is not in this playlist goes to Assinaturas rather than opening an empty page.
            onOpenSimilar = { credit ->
                when (appState.openCredit(credit)) {
                    CreditDestination.PLAYLIST_ITEM -> {
                        personOpen = false
                        detailsOpen = true
                    }
                    CreditDestination.SUBSCRIPTIONS -> {
                        personOpen = false
                        detailsOpen = false
                    }
                    CreditDestination.NOWHERE -> Unit
                }
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
            // Idle is not loading. The effect above is keyed on the profile, the source and the
            // refresh counter — never on the status — so a load cancelled mid-flight resets to Idle
            // and nothing runs again. Drawn as a skeleton, that is a home screen pretending to
            // build itself for ever. The film details and the live guide had the same defect.
            DailyHomeStatus.Idle -> {
                // Retried only while there is a catalogue to build from.
                //
                // With no subscription open, the load returns at once and leaves Idle behind, so
                // this effect went round again on every recomposition — the app working steadily
                // at a home it could not build. The welcome screen above already covers that case,
                // so there is nothing to draw here; the skeleton simply must not keep retrying.
                val hasCatalogue = (appState.xtreamSummary?.loadedItemCount ?: 0) > 0
                if (hasCatalogue) {
                    LaunchedEffect(Unit) { appState.loadDailyHome(today) }
                }
                HomeSkeleton(metrics, text)
            }

            DailyHomeStatus.Loading -> HomeSkeleton(metrics, text)

            is DailyHomeStatus.Error ->
                HomeError(status.message, text) { scope.launch { appState.loadDailyHome(today) } }

            is DailyHomeStatus.Loaded -> {
                val snapshot = status.snapshot
                val homeState = rememberLazyListState()
                val homeScope = rememberCoroutineScope()
                val homeFocus = remember { FocusRequester() }
                var homeFocusAttached by remember { mutableStateOf(false) }
                LaunchedEffect(homeFocusAttached) {
                    if (homeFocusAttached) homeFocus.requestFocus()
                }
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
                            .onGloballyPositioned { homeFocusAttached = true }
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
                        // The banner cycles through the day's rotation rather than holding one
                        // image. Ten seconds is long enough to read the title and press play, short
                        // enough that a user who lingers sees more than one thing on the largest
                        // surface in the app.
                        val rotation = snapshot.heroRotation.ifEmpty { listOfNotNull(snapshot.hero) }
                        var heroIndex by remember(snapshot.date, snapshot.sourceId) { mutableStateOf(0) }
                        LaunchedEffect(rotation.size) {
                            if (rotation.size <= 1) return@LaunchedEffect
                            while (true) {
                                delay(HERO_ROTATION_MILLIS)
                                heroIndex = (heroIndex + 1) % rotation.size
                            }
                        }
                        val heroItem = rotation.getOrNull(heroIndex) ?: snapshot.hero
                        // Fetched as the banner reaches each title rather than all five up front:
                        // four of them may never be seen if the user moves on.
                        LaunchedEffect(heroItem?.contentType, heroItem?.providerId) {
                            heroItem?.let(appState::loadHeroSynopsis)
                            // The trailer too, on the same terms: only for a title the banner has
                            // actually reached. The rotation holds twenty and most are never seen.
                            heroItem?.let(appState::loadHeroTrailer)
                        }
                        DailyHero(
                            item = heroItem,
                            synopsis = heroItem?.let(appState::heroSynopsisFor),
                            trailerId = heroItem?.let(appState::heroTrailerFor),
                            onTrailerFailed = {
                                heroItem?.let(appState::rememberHeroTrailerFailure)
                            },
                            // Scrolling away stops the sound and the picture: the viewer has moved
                            // on from the banner, so it is not what they are looking at.
                            scrolling = homeState.isScrollInProgress,
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
                    // Read once. This is a computed property that resolves every entry against the
                    // catalogue and applies the parental policy to each, so asking twice — once to
                    // decide whether to draw the row and once to fill it — did all of that work
                    // twice on every recomposition of the home screen.
                    val resumable = appState.continueWatchingEntries
                    if (resumable.isNotEmpty()) {
                        item(key = "continue-watching") {
                            ContinueWatchingRow(
                                entries = resumable,
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
                    // This year's releases, above the daily picks. What is new is what the user
                    // most often came to see, and burying it under a rotating selection meant
                    // scrolling past today's shuffle to find it.
                    if (snapshot.releasesThisYear.isNotEmpty()) {
                        item(key = "releases-year-movies") {
                            DailyRow(
                                "${text.releasesIn} ${snapshot.date.year}",
                                snapshot.releasesThisYear,
                                metrics,
                                text,
                                openDetails,
                            )
                        }
                    }
                    if (snapshot.seriesThisYear.isNotEmpty()) {
                        item(key = "releases-year-series") {
                            DailyRow(
                                "${text.series} · ${snapshot.date.year}",
                                snapshot.seriesThisYear,
                                metrics,
                                text,
                                openDetails,
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
                    // What is new on the streaming services, below the user's own library.
                    //
                    // Under rather than above deliberately: these are titles the user cannot play
                    // here — clicking one leads out to the service — so they must not displace the
                    // content they actually have. Absent entirely until the shelves have loaded, so
                    // the home screen never shows an empty heading while waiting on the network.
                    items(
                        items = appState.streamingShelves.filter { shelf -> shelf.titles.isNotEmpty() },
                        key = { shelf -> "streaming-${shelf.provider.id}" },
                    ) { shelf ->
                        StreamingServiceRow(
                            shelf = shelf,
                            metrics = metrics,
                            text = text,
                            onClick = { title -> appState.openStreamingTitle(title) },
                        )
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

/**
 * The banner's trailer, drawn behind the title.
 *
 * With sound, because a trailer without it is a moving poster. It stops when the viewer scrolls
 * away and when they leave the screen — both handled by the caller withdrawing it, which disposes
 * the browser here.
 *
 * When Chromium cannot start, [onFailed] is called and nothing is drawn: the artwork underneath is
 * already on screen, so the banner simply stays as it was. A black rectangle on the opening screen
 * would read as a broken app, which is the whole reason this is guarded rather than optimistic.
 */
@Composable
private fun HeroTrailer(
    youtubeId: String,
    modifier: Modifier = Modifier,
    onFailed: () -> Unit,
) {
    val browser = remember(youtubeId) { TrailerBrowser() }
    DisposableEffect(browser) { onDispose { browser.dispose() } }

    val panel =
        remember(youtubeId) {
            runCatching {
                browser.createComponent(youtubeId = youtubeId, autoplay = true, muted = false)
            }.getOrNull()
        }

    if (panel == null) {
        DisposableEffect(youtubeId) {
            onFailed()
            onDispose { }
        }
        return
    }

    SwingPanel(factory = { panel }, modifier = modifier)
}

@Composable
private fun DailyHero(
    item: XtreamCatalogItem?,
    date: LocalDate,
    metrics: HomeMetrics,
    text: DesktopStrings,
    /** The film's own description, when it has been fetched. Null falls back to the fixed line. */
    synopsis: String?,
    onDetails: (XtreamCatalogItem) -> Unit,
    onPlay: (XtreamCatalogItem) -> Unit,
    /**
     * The trailer to play behind the title, or null to show the artwork.
     *
     * Null covers every reason not to play: no trailer, one that already failed, or something the
     * viewer chose already playing. The banner does not decide any of that — see BannerTrailer.
     */
    trailerId: String? = null,
    /** Told when the trailer will not start, so the next rotation shows artwork immediately. */
    onTrailerFailed: () -> Unit = {},
    /** True while the viewer is scrolling, which stops the sound and the picture. */
    scrolling: Boolean = false,
) {
    Box(modifier = Modifier.fillMaxWidth().height(metrics.heroHeight).background(BuroColors.Surface)) {
        // The trailer plays behind the title, and the artwork is what shows whenever it cannot.
        //
        // Started only after the banner has held this title for a moment: it rotates on its own,
        // so beginning the instant a title appears would open and abandon a video per rotation.
        var settled by remember(item?.providerId, trailerId) { mutableStateOf(false) }
        LaunchedEffect(item?.providerId, trailerId) {
            settled = false
            if (trailerId != null) {
                delay(BannerTrailer.SETTLE_MILLIS)
                settled = true
            }
        }
        val playing = trailerId != null && settled && !scrolling

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
        if (playing && trailerId != null) {
            HeroTrailer(
                youtubeId = trailerId,
                modifier = Modifier.fillMaxSize(),
                onFailed = onTrailerFailed,
            )
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
            // The film's own description when it has arrived, the fixed line about the daily
            // selection until then. The largest block of text on the home screen used to say
            // nothing about the title it sat under.
            Text(
                text = synopsis?.takeIf(String::isNotBlank) ?: text.heroSubtitle,
                color = BuroColors.TextMuted,
                style = MaterialTheme.typography.bodyLarge,
                // Three lines for a real synopsis, which needs the room; the fixed line fits in two.
                maxLines = if (synopsis.isNullOrBlank()) 2 else 3,
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
            // Position in the key: a provider filing a stream under two categories sends the
            // same id twice, and a lazy layout throws when two items share a key.
            itemsIndexed(items, key = { index, it -> "$index:${it.contentType}:${it.providerId}" }) { _, item ->
                DailyCard(item, metrics, text, onClick)
            }
        }
    }
}

/**
 * What is new on one streaming service.
 *
 * Built from the same rail shape as [DailyRow] so the home screen reads as one page, but the cards
 * lead somewhere different: these titles are not in the user's library and cannot be played here.
 * Selecting one opens the Assinaturas area, which says where it can actually be watched.
 *
 * The heading carries the service's name and, when TMDb lists one, its mark — the same pairing the
 * Assinaturas shelves use, so a service looks the same on both screens.
 */
@Composable
private fun StreamingServiceRow(
    shelf: TmdbServiceShelf,
    metrics: HomeMetrics,
    text: DesktopStrings,
    onClick: (ExternalTitle) -> Unit,
) {
    if (shelf.titles.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(BuroSpacing.Sm)) {
        RailHeader(
            shelf.provider.displayName,
            WATCH_PROVIDER_ATTRIBUTION,
            metrics,
            logoUrl = shelf.provider.logoUrl,
        )
        val railState = rememberLazyListState()
        LazyRow(
            state = railState,
            modifier = Modifier.fillMaxWidth().arrowScrollable(railState).edgeScrollable(railState),
            horizontalArrangement = Arrangement.spacedBy(metrics.cardSpacing),
            contentPadding = PaddingValues(horizontal = metrics.gutter, vertical = BuroSpacing.Sm),
        ) {
            items(shelf.titles, key = { title -> title.id.key }) { title ->
                // The same card the Assinaturas shelves use, so a title looks identical wherever it
                // appears. The offer names this rail's service, which is what puts the mark in the
                // poster's corner; the full listings are fetched when the title is opened.
                ProviderShelfCard(
                    details =
                        ExternalTitleDetails(
                            title = title,
                            offers =
                                listOf(
                                    StreamingOffer(
                                        provider = shelf.provider,
                                        type = OfferType.SUBSCRIPTION,
                                    ),
                                ),
                        ),
                    // Real listings, so no DEMO badge — it would say invented about genuine data.
                    showDemoBadge = title.isDemo,
                    text = text,
                    onClick = { onClick(title) },
                )
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
            itemsIndexed(
                items,
                key = { index, it -> "seasonal:$index:${it.contentType}:${it.providerId}" },
            ) { _, item ->
                DailyCard(item, metrics, text, onClick)
            }
        }
    }
}

/** Big enough for a wordmark beside a headline, small enough to stay subordinate to it. */
private val RAIL_LOGO_SIZE = 24.dp

@Composable
private fun RailHeader(
    title: String,
    trailing: String?,
    metrics: HomeMetrics,
    logoUrl: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = metrics.gutter),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Sm),
    ) {
        // Only the streaming rails pass one. A mark is laid on a light tile because most are drawn
        // for white backgrounds and would disappear into this one.
        logoUrl?.let { logo ->
            Box(
                modifier =
                    Modifier
                        .size(RAIL_LOGO_SIZE)
                        .clip(BuroRadius.Small)
                        .background(BuroColors.Canvas),
            ) {
                BuroRemoteArtwork(
                    artworkUrl = logo,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(2.dp),
                    contentScale = ContentScale.Fit,
                ) {}
            }
        }
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

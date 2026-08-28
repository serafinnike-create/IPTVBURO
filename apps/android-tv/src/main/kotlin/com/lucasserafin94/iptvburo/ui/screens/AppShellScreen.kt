package com.lucasserafin94.iptvburo.ui.screens


import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.lucasserafin94.iptvburo.domain.model.QrCode
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.BackHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.runtime.CompositionLocalProvider
import com.lucasserafin94.iptvburo.ui.designsystem.LocalProviderLogos
import androidx.compose.foundation.horizontalScroll
import com.lucasserafin94.iptvburo.ui.designsystem.ProviderIdentity
import com.lucasserafin94.iptvburo.ui.designsystem.ProviderMark
import com.lucasserafin94.iptvburo.ui.designsystem.providerIdentityFor
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.lucasserafin94.iptvburo.BuildConfig
import kotlinx.coroutines.delay
import com.lucasserafin94.iptvburo.R
import com.lucasserafin94.iptvburo.ui.BootStageUi
import com.lucasserafin94.iptvburo.ui.AppContent
import com.lucasserafin94.iptvburo.ui.formatDownloadRate
import com.lucasserafin94.iptvburo.domain.model.CatalogueFilter
import com.lucasserafin94.iptvburo.domain.model.CatalogueLayout
import com.lucasserafin94.iptvburo.ui.AppSection
import com.lucasserafin94.iptvburo.ui.AppUiState
import com.lucasserafin94.iptvburo.data.licensing.RedeemFailure
import com.lucasserafin94.iptvburo.ui.LicenseUiState
import com.lucasserafin94.iptvburo.ui.RedemptionUi
import com.lucasserafin94.iptvburo.ui.CategoryUi
import com.lucasserafin94.iptvburo.domain.model.Reminder
import com.lucasserafin94.iptvburo.ui.ChannelUi
import com.lucasserafin94.iptvburo.ui.ContinueWatchingUi
import com.lucasserafin94.iptvburo.ui.DownloadEntryUi
import com.lucasserafin94.iptvburo.ui.DownloadStateUi
import com.lucasserafin94.iptvburo.ui.EpisodeUi
import com.lucasserafin94.iptvburo.ui.episodeDownloadKey
import com.lucasserafin94.iptvburo.ui.movieDownloadKey
import com.lucasserafin94.iptvburo.ui.ParentalMessage
import com.lucasserafin94.iptvburo.ui.PersonCreditUi
import com.lucasserafin94.iptvburo.ui.ProfileUi
import com.lucasserafin94.iptvburo.ui.ShareRequestUi
import com.lucasserafin94.iptvburo.ui.cast.CastSheet
import com.lucasserafin94.iptvburo.ui.cast.CastTarget
import com.lucasserafin94.iptvburo.ui.cast.CastUiState
import com.lucasserafin94.iptvburo.ui.localization.AppLocaleController
import com.lucasserafin94.iptvburo.ui.SourceImportMethod
import com.lucasserafin94.iptvburo.ui.SourceUi
import com.lucasserafin94.iptvburo.ui.SubscriptionOfferUi
import com.lucasserafin94.iptvburo.ui.ProviderShelfUi
import com.lucasserafin94.iptvburo.ui.SubscriptionTitleUi
import com.lucasserafin94.iptvburo.ui.SubscriptionsKindUi
import com.lucasserafin94.iptvburo.ui.StalkerFailureUi
import com.lucasserafin94.iptvburo.ui.XtreamImportStageUi
import com.lucasserafin94.iptvburo.ui.WatchHistoryUi
import com.lucasserafin94.iptvburo.data.diagnostics.ConnectionTester
import com.lucasserafin94.iptvburo.ui.components.FocusSurface
import com.lucasserafin94.iptvburo.ui.designsystem.BuroMarqueeText
import com.lucasserafin94.iptvburo.ui.designsystem.BuroButton
import com.lucasserafin94.iptvburo.ui.designsystem.BuroButtonStyle
import com.lucasserafin94.iptvburo.ui.designsystem.BuroEmptyState
import com.lucasserafin94.iptvburo.ui.designsystem.BuroErrorState
import com.lucasserafin94.iptvburo.ui.designsystem.BuroScreen
import com.lucasserafin94.iptvburo.ui.designsystem.categoryBadgeFor
import com.lucasserafin94.iptvburo.ui.designsystem.BuroSpacing
import com.lucasserafin94.iptvburo.ui.designsystem.BuroTheme
import com.lucasserafin94.iptvburo.ui.home.DemoHomeCatalog
import com.lucasserafin94.iptvburo.ui.home.DemoStoryScreen
import com.lucasserafin94.iptvburo.ui.home.HomeSourceSummary
import com.lucasserafin94.iptvburo.ui.home.LivingHomeScreen
import com.lucasserafin94.iptvburo.ui.navigation.BuroRibbon
import com.lucasserafin94.iptvburo.ui.capabilities.AndroidPlatformCapabilities
import com.lucasserafin94.iptvburo.ui.security.SecureActivityWindowEffect
import com.lucasserafin94.iptvburo.domain.model.CacheBudget
import com.lucasserafin94.iptvburo.domain.model.CacheFillProgress
import com.lucasserafin94.iptvburo.domain.model.CatalogContentType
import com.lucasserafin94.iptvburo.domain.model.NotificationCentre
import com.lucasserafin94.iptvburo.domain.model.ContentIdentity
import com.lucasserafin94.iptvburo.domain.model.ContentKind
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.lucasserafin94.iptvburo.ui.theme.BuroAccent
import com.lucasserafin94.iptvburo.ui.theme.BuroCanvas
import com.lucasserafin94.iptvburo.ui.theme.BuroDanger
import com.lucasserafin94.iptvburo.ui.theme.BuroFieldColors
import com.lucasserafin94.iptvburo.ui.theme.BuroGold
import com.lucasserafin94.iptvburo.ui.theme.BuroSurface
import com.lucasserafin94.iptvburo.ui.theme.BuroSurfaceRaised
import com.lucasserafin94.iptvburo.ui.theme.BuroTextPrimary
import com.lucasserafin94.iptvburo.ui.theme.BuroTextSecondary

@Composable
fun AppShellScreen(
    state: AppUiState,
    onSelectSection: (AppSection) -> Unit,
    onImportSource: () -> Unit,
    onImportXtreamSource: (String, String, String, String) -> Unit,
    onCancelXtreamImport: () -> Unit,
    onImportStalkerSource: (String, String, String, String, String) -> Unit,
    onCancelStalkerImport: () -> Unit,
    onOpenSource: (SourceUi) -> Unit,
    /** Whether every configured list is browsed as one catalogue. */
    onToggleMergeSources: (Boolean) -> Unit = {},
    onOpenCategory: (CategoryUi) -> Unit,
    onOpenChannel: (ChannelUi) -> Unit,
    /** Runs a catalogue search. Called once the field settles, not per keystroke. */
    onSearch: (String) -> Unit,
    onPlayMovie: () -> Unit,
    onToggleMovieFavorite: () -> Unit,
    /** Drops a title from the favourites list itself, without opening it first. */
    onToggleChannelFavorite: (ChannelUi) -> Unit,
    /** Marks or unmarks whichever details page is open. */
    onToggleReminder: () -> Unit,
    /** Drops one reminder from the reminders page, by identity rather than by row. */
    onRemoveReminder: (ContentIdentity) -> Unit,
    onOpenReminder: (Reminder) -> Unit = {},
    /** Turns the daily notice on or off. The marks themselves are kept either way. */
    onSetReminderNotify: (Boolean) -> Unit,
    /** Moves the daily notice to another hour. */
    onSetReminderTime: (Int, Int) -> Unit,
    /** Sends the open title to the system share sheet. */
    onShareTitle: (ShareRequestUi) -> Unit,
    /** Opens the sheet that sends the open title to a screen on the same network. */
    onCastTitle: () -> Unit,
    /** Keeps the top Descobrir card, which files it as a favourite. */
    onDiscoverKeep: (ChannelUi) -> Unit,
    /** Skips the top Descobrir card. Remembered for this session only. */
    onDiscoverSkip: (ChannelUi) -> Unit,
    /** Deals a fresh hand once the deck runs out. */
    onDiscoverDealAgain: () -> Unit,
    /** Starts listening for a computer, and publishes the code it must be told. */
    onStartCastReceiver: () -> Unit,
    onStopCastReceiver: () -> Unit,
    /** Opens one streaming service's whole catalogue, from the end of its shelf. */
    onSubscriptionSeeMore: (ProviderShelfUi) -> Unit,
    onSubscriptionCloseExpanded: () -> Unit,
    /** Opening the bell marks everything in it read. */
    onMarkNotificationsRead: () -> Unit,
    onDismissNotification: (String) -> Unit,
    onClearNotifications: () -> Unit,
    /** How much artwork this device may keep, and the download that fills it. */
    onChooseCacheBudget: (Int) -> Unit,
    onDeclineCacheOffer: () -> Unit,
    onStartCacheFill: () -> Unit,
    onStopCacheFill: () -> Unit,
    onRefreshCacheFill: () -> Unit,
    onClearCache: () -> Unit,
    onCastSearchAgain: () -> Unit,
    /** Reaches a screen by typed address when the search found nothing. */
    onCastConnectToAddress: suspend (String) -> Boolean = { false },
    onCastChoose: (CastTarget) -> Unit,
    onCastBack: () -> Unit,
    onCastSend: (String) -> Unit,
    onCastClose: () -> Unit,
    onOpenPerson: (String) -> Unit,
    /** Opens a filmography entry on the "where to watch" page. */
    onOpenPersonCredit: (PersonCreditUi) -> Unit,
    onOpenProviderShortcut: (com.lucasserafin94.iptvburo.data.discovery.DiscoveredProvider) -> Unit,
    onRequestCastPhotos: (List<String>) -> Unit,
    onCatalogueFilterChange: (CatalogueFilter) -> Unit,
    onCatalogueLayoutChange: (CatalogueLayout) -> Unit,
    onOpenEpisode: (EpisodeUi) -> Unit,
    onDownloadMovie: () -> Unit,
    onDownloadEpisode: (EpisodeUi) -> Unit,
    /** Queues a whole season, after the screen has confirmed the count with the user. */
    onDownloadSeason: (Int) -> Unit,
    /** Queues every episode of the open series, likewise confirmed first. */
    onDownloadSeries: () -> Unit,
    onCancelDownload: (String) -> Unit,
    onDeleteDownload: (String) -> Unit,
    onPlayDownload: (String) -> Unit,
    onSelectProfile: (String) -> Unit,
    onCreateProfile: (name: String, isKids: Boolean, sourceId: String?) -> Unit,
    availableAvatars: List<String>,
    onUpdateProfile: (
        id: String, name: String, avatarKey: String, isKids: Boolean, photoUri: String?, clearPhoto: Boolean,
    ) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onSelectProfileSource: (profileId: String, sourceId: String?) -> Unit,
    onRequestProfileSelection: () -> Unit,
    onSaveTmdbKey: (String) -> Unit,
    onOpenPurchase: (String) -> Unit,
    onRedeemLicense: (String) -> Unit,
    onSaveSharedTmdbKey: (String) -> Unit,
    onSaveCriticsKey: (String) -> Unit = {},
    criticsKeyConfigured: Boolean = false,
    onSelectSubscriptionKind: (SubscriptionsKindUi) -> Unit,
    onSelectSubscriptionRegion: (String) -> Unit,
    onOpenSubscriptionTitle: (SubscriptionTitleUi) -> Unit,
    onCloseSubscriptionTitle: () -> Unit,
    onOpenSubscriptionOffer: (SubscriptionOfferUi) -> Unit,
    onSelectLanguage: (String) -> Unit,
    /** The channel lock, subtitles and category visibility, all of which settings owns. */
    guard: CatalogueGuardUi,
    /** Answers the PIN prompt when a locked category was opened. */
    onSubmitParentalPin: (String) -> Unit,
    onDismissParentalPrompt: () -> Unit,
    onLoadMore: () -> Unit,
    onRetryCatalog: () -> Unit,
    onRefreshCatalog: () -> Unit,
    /**
     * Runs the connection test, or null where the app cannot measure yet.
     *
     * A suspending lambda rather than a result, so the screen owns when it runs and can show the
     * work happening — the measurement deliberately takes several seconds.
     */
    onRunDiagnostics: (suspend () -> ConnectionTester.Report?)? = null,
    onOpenHomeItem: (String) -> Unit,
    onRememberHomeFocus: (String?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ribbonFocusRequester = remember { FocusRequester() }
    var showXtreamModal by remember { mutableStateOf(false) }

    /** The connection test, opened from the top bar beside the refresh button. */
    var diagnosticsOpen by remember { mutableStateOf(false) }
    var xtreamSuccessVersionAtOpen by remember {
        mutableLongStateOf(state.importSuccessVersion)
    }
    var showStalkerModal by remember { mutableStateOf(false) }
    var showMobileNavigation by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val compactNavigation = configuration.screenWidthDp < 600
    val offlineSupported = AndroidPlatformCapabilities.offlineSupported(configuration)
    var stalkerSuccessVersionAtOpen by remember {
        mutableLongStateOf(state.importSuccessVersion)
    }
    var homeContentOwnsBack by remember(state.content) {
        mutableStateOf(state.content == AppContent.Home)
    }
    val selectedRibbonSection = state.section.takeIf { section ->
        section.isRibbonDestination(offlineSupported)
    }

    /**
     * Where the catalogue list was scrolled to, kept across opening a title.
     *
     * Hoisted above the `when` below on purpose. `ChannelsContent` leaves composition entirely when
     * a film's details replace it, so a `rememberLazyGridState` inside it would be thrown away —
     * which is why returning from a title used to drop the viewer back at the top of the list after
     * they had scrolled a long way down.
     *
     * Keyed by the list's identity rather than remembered once: a different category is a different
     * list, and carrying one list's offset into another would land somebody in the middle of
     * something they had not scrolled at all.
     */
    // The catalogue's derived lists, computed once per change rather than once per frame.
    //
    // These are `get()` properties on the state, so every read recomputes them: `visibleChannels`
    // builds a map and two list copies, and the genre and year pickers walk every loaded item and
    // split its genre string. All three are read on each recomposition of the catalogue — which is
    // continuous while somebody scrolls a list of thousands.
    val visibleChannels =
        remember(state.channels, state.catalogueFilter) { state.visibleChannels }
    val catalogueGenres = remember(state.channels) { state.availableCatalogueGenres }
    val catalogueYears = remember(state.channels) { state.availableCatalogueYears }

    val channelsKey = (state.content as? AppContent.Channels)?.let { channels ->
        "${channels.sourceId}:${channels.categoryId}:${channels.contentType}"
    }
    val rememberedChannelsGridState = rememberSaveable(channelsKey, saver = LazyGridState.Saver) {
        LazyGridState()
    }

    BackHandler(enabled = showMobileNavigation) { showMobileNavigation = false }

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

            is AppContent.SeriesDetails -> {
                val seriesTitle = state.seriesDetails?.title ?: content.fallbackTitle
                SeriesDetailsScreen(
                    offlineSupported = offlineSupported,
                    fallbackTitle = content.fallbackTitle,
                    categoryName = content.categoryName,
                    providerName = state.openTitleProviderName,
                    providerLogoUrl = state.openTitleProviderLogoUrl,
                    criticScores = state.openTitleCriticScores,
                    details = state.seriesDetails,
                    isLoading = state.isSeriesLoading,
                    hasError = state.hasSeriesError,
                    isResolvingPlayback = state.isResolvingPlayback,
                    hasPlaybackError = state.hasPlaybackError,
                    onOpenEpisode = onOpenEpisode,
                    onDownloadEpisode = onDownloadEpisode,
                    // Null where offline storage is unavailable, which hides the buttons
                    // rather than showing ones that would refuse.
                    onDownloadSeason = onDownloadSeason.takeIf { offlineSupported },
                    onDownloadSeries = onDownloadSeries.takeIf { offlineSupported },
                    onCancelEpisodeDownload = { episode ->
                        onCancelDownload(episodeDownloadKey(seriesTitle, episode))
                    },
                    onDeleteEpisodeDownload = { episode ->
                        onDeleteDownload(episodeDownloadKey(seriesTitle, episode))
                    },
                    downloadStateOf = { episode ->
                        state.downloads[episodeDownloadKey(seriesTitle, episode)]
                            ?: DownloadStateUi.Idle
                    },
                    isFavorite = content.channelId in state.favoriteIds,
                    // Hidden for a series reached without a catalogue row: there would be nothing
                    // to file, and a button that silently does nothing is worse than no button.
                    onToggleFavorite =
                        onToggleMovieFavorite.takeIf { content.channelId.isNotBlank() },
                    // Offered even for a series with no catalogue row, unlike favouriting above: a
                    // reminder is stored against the identity, which is built from the title, so
                    // there is something to file either way.
                    hasReminder =
                        reminderKeyOf(
                            ContentKind.SERIES,
                            seriesTitle,
                            state.seriesDetails?.releaseDate,
                        ) in state.reminderKeys,
                    onToggleReminder = onToggleReminder,
                    onCast = onCastTitle,
                    onShare = {
                        onShareTitle(
                            ShareRequestUi(
                                kind = ContentKind.SERIES,
                                title = seriesTitle,
                                year = state.seriesDetails?.releaseDate?.let(::yearFromReleaseDate),
                                artworkUrl = state.seriesDetails?.artworkUrl,
                                description = state.seriesDetails?.plot,
                            ),
                        )
                    },
                    episodeProgress = state.episodeProgress,
                    castPhotos = state.castPhotos,
                    onRequestCastPhotos = onRequestCastPhotos,
                    similarTitles = state.openTitleSimilarTitles,
                    onOpenSimilarTitle = onOpenPersonCredit,
                    onOpenPerson = onOpenPerson,
                    onRetry = onRetryCatalog,
                    onBack = onBack,
                )
            }

            is AppContent.MovieDetails -> {
                val movieKey =
                    movieDownloadKey(state.movieDetails?.title ?: content.fallbackTitle)
                MovieDetailsScreen(
                    watchedFraction = state.openTitleProgress,
                    castPhotos = state.castPhotos,
                    onRequestCastPhotos = onRequestCastPhotos,
                    offlineSupported = offlineSupported,
                    fallbackTitle = content.fallbackTitle,
                    fallbackArtworkUrl = content.fallbackArtworkUrl,
                    categoryName = content.categoryName,
                    providerName = state.openTitleProviderName,
                    providerLogoUrl = state.openTitleProviderLogoUrl,
                    criticScores = state.openTitleCriticScores,
                    details = state.movieDetails,
                    isLoading = state.isMovieLoading,
                    hasError = state.hasMovieError,
                    isResolvingPlayback = state.isResolvingPlayback,
                    hasPlaybackError = state.hasPlaybackError,
                    onPlay = onPlayMovie,
                    isFavorite = content.channelId in state.favoriteIds,
                    onToggleFavorite = onToggleMovieFavorite,
                    hasReminder =
                        reminderKeyOf(
                            ContentKind.MOVIE,
                            state.movieDetails?.title ?: content.fallbackTitle,
                            state.movieDetails?.releaseDate,
                        ) in state.reminderKeys,
                    onToggleReminder = onToggleReminder,
                    onCast = onCastTitle,
                    onShare = {
                        val movieTitle = state.movieDetails?.title ?: content.fallbackTitle
                        onShareTitle(
                            ShareRequestUi(
                                kind = ContentKind.MOVIE,
                                title = movieTitle,
                                year =
                                    state.movieDetails?.releaseDate?.let(::yearFromReleaseDate)
                                        ?: ContentIdentity.yearFromTitle(movieTitle),
                                artworkUrl = state.movieDetails?.artworkUrl,
                                description = state.movieDetails?.plot,
                            ),
                        )
                    },
                    onDownload = onDownloadMovie,
                    onCancelDownload = { onCancelDownload(movieKey) },
                    onDeleteDownload = { onDeleteDownload(movieKey) },
                    downloadState = state.downloads[movieKey] ?: DownloadStateUi.Idle,
                    similarTitles = state.openTitleSimilarTitles,
                    onOpenSimilarTitle = onOpenPersonCredit,
                    onOpenPerson = onOpenPerson,
                    onRetry = onRetryCatalog,
                    onBack = onBack,
                )
            }

            is AppContent.Person -> PersonFilmographyScreen(
                personName = content.name,
                movies = state.personMovies,
                details = state.personDetails,
                onOpenMovie = onOpenChannel,
                onOpenCredit = onOpenPersonCredit,
                onBack = onBack,
            )

            else -> Column(modifier = Modifier.fillMaxSize()) {
                if (compactNavigation) {
                    MobileAppBar(
                        section = state.section,
                        profile = state.activeProfile,
                        onOpenMenu = { showMobileNavigation = true },
                        onOpenProfiles = { onSelectSection(AppSection.PROFILE) },
                        // Null until there is a profile: the centre is per profile, and a bell on a
                        // boot screen would be counting somebody else's news.
                        notifications = state.notifications.takeIf { state.activeProfile != null },
                        onMarkNotificationsRead = onMarkNotificationsRead,
                        onDismissNotification = onDismissNotification,
                        onClearNotifications = onClearNotifications,
                        // Home only. The catalogue screens carry their own refresh in the header,
                        // and a second one in the bar would be two buttons for one action.
                        onRefresh =
                            onRefreshCatalog.takeIf {
                                state.content == AppContent.Home && state.sources.isNotEmpty()
                            },
                        // The shelves are the slowest part of a home refresh and the last to
                        // finish, so their flag is the honest one to spin on.
                        isRefreshing = state.subscriptions.isLoading || state.isCatalogLoading,
                        // Only once there is something to measure against; before that the test
                        // could say nothing the boot screen has not already said.
                        onOpenDiagnostics =
                            onRunDiagnostics?.let { { diagnosticsOpen = true } },
                    )
                } else {
                    BuroRibbon(
                        offlineSupported = offlineSupported,
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
                        subscriptionsVisible = state.subscriptions.capability.isVisible,
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    // Provided once so every badge below — home rails, category tiles, catalogue
                    // cards — draws the service's real mark without the map being threaded through
                    // each composable on the way there.
                    CompositionLocalProvider(LocalProviderLogos provides state.providerLogos) {
                    when (content) {
                        AppContent.Home -> LivingHomeScreen(
                            sources = state.sources.map(SourceUi::toHomeSummary),
                            catalogItems = state.homeItems,
                            continueWatching = state.continueWatching,
                            reminders = state.reminders,
                            streamingShelves = state.subscriptions.shelves,
                            synopses = state.heroSynopses,
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
                            mergeSources = state.mergeEverySource,
                            onToggleMergeSources = onToggleMergeSources,
                            isImporting = state.isImporting,
                            lastImportedChannelCount = state.lastImportedChannelCount,
                            hasImportError = state.hasImportError,
                            lastImportMethod = state.lastImportMethod,
                            stalkerFailure = state.stalkerFailure,
                            xtreamImportStage = state.xtreamImportStage,
                            downloadBytesPerSecond = state.downloadBytesPerSecond,
                            onImportSource = onImportSource,
                            onOpenXtream = {
                                xtreamSuccessVersionAtOpen = state.importSuccessVersion
                                showXtreamModal = true
                            },
                            onOpenStalker = {
                                stalkerSuccessVersionAtOpen = state.importSuccessVersion
                                showStalkerModal = true
                            },
                            onOpenSource = onOpenSource,
                        )

                        // Search has a real screen now; the other placeholder sections still show
                        // the "coming soon" card.
                        is AppContent.SectionPlaceholder ->
                            if (content.section == AppSection.SEARCH) {
                                SearchContent(
                                    results = state.searchResults,
                                    isSearching = state.isSearching,
                                    onQueryChange = onSearch,
                                    onOpenChannel = onOpenChannel,
                                    onBack = onBack,
                                )
                            } else {
                                SectionPlaceholderContent(
                                    section = content.section,
                                    onOpenSources = {
                                        onSelectSection(AppSection.SOURCES)
                                    },
                                    onOpenSettings = {
                                        onSelectSection(AppSection.SETTINGS)
                                    },
                                )
                            }

                        is AppContent.Categories -> CategoriesContent(
                            title = content.sourceName,
                            categories = state.categories,
                            contentType = content.contentType,
                            isLoading = state.isCatalogLoading,
                            hasError = state.hasCatalogError,
                            onRetry = onRetryCatalog,
                            onRefresh = onRefreshCatalog,
                            onBack = onBack,
                            onOpenCategory = onOpenCategory,
                            providerLogos = state.providerLogos,
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
                            // Filtered, not raw: the bar above says what is being shown, and the
                            // grid has to agree with it.
                            channels = visibleChannels,
                            layout = state.catalogueLayout,
                            // Only on "every title" for Filmes/Séries: a category already narrows
                            // to one thing, and a Netflix shortcut inside "Ação" would suggest a
                            // wider catalogue than that screen is showing.
                            headerAction =
                                if (content.categoryId == null &&
                                    content.contentType in
                                        setOf(CatalogContentType.MOVIE, CatalogContentType.SERIES) &&
                                    state.discoveredProviders.isNotEmpty()
                                ) {
                                    {
                                        ProviderShortcutRow(
                                            providers = state.discoveredProviders,
                                            onOpenProvider = onOpenProviderShortcut,
                                        )
                                    }
                                } else {
                                    null
                                },
                            filterBar = {
                                CatalogueFilterBar(
                                    filter = state.catalogueFilter,
                                    layout = state.catalogueLayout,
                                    genres = catalogueGenres,
                                    years = catalogueYears,
                                    onFilterChange = onCatalogueFilterChange,
                                    onLayoutChange = onCatalogueLayoutChange,
                                )
                            },
                            isLoading = state.isCatalogLoading,
                            isLoadingMore = state.isLoadingMore,
                            hasMore = state.hasMoreChannels,
                            hasError = state.hasCatalogError,
                            isResolvingPlayback = state.isResolvingPlayback,
                            hasPlaybackError = state.hasPlaybackError,
                            onBack = onBack,
                            onOpenChannel = onOpenChannel,
                            onRetry = onRetryCatalog,
                            onRefresh = onRefreshCatalog,
                            onLoadMore = onLoadMore,
                            gridState = rememberedChannelsGridState,
                            // Filmes, Séries and TV ao vivo each list one kind, so the cards there
                            // drop the label the section header already carries.
                            impliedContentType = content.contentType,
                            providerLogos = state.providerLogos,
                        )

                        AppContent.Favorites -> MyBuroContent(
                            onRemoveFavorite = onToggleChannelFavorite,
                            favorites = state.favoriteItems,
                            continueWatching = state.continueWatching,
                            history = state.watchHistory,
                            isLoading = state.isCatalogLoading,
                            hasError = state.hasCatalogError,
                            onOpenChannel = onOpenChannel,
                            onRetry = onRetryCatalog,
                            onBack = onBack,
                        )

                        AppContent.Downloads -> {
                            if (offlineSupported) {
                                DownloadsContent(
                                    entries = state.downloadEntries,
                                    onPlayDownload = onPlayDownload,
                                    onCancelDownload = onCancelDownload,
                                    onDeleteDownload = onDeleteDownload,
                                )
                            }
                        }

                        // One screen for both: they differ only in which list they draw and what
                        // each row says about it, so a second copy would be the same code twice.
                        AppContent.ContinueWatching -> WatchlistContent(
                            title = stringResource(R.string.nav_continue_watching),
                            subtitle = stringResource(R.string.my_buro_continue_status),
                            rows = state.continueWatching.map { entry ->
                                WatchlistRow(entry.channel, entry.progress, null)
                            },
                            emptyMessage = stringResource(R.string.continue_watching_empty),
                            onOpenChannel = onOpenChannel,
                            onBack = onBack,
                        )

                        AppContent.History -> WatchlistContent(
                            title = stringResource(R.string.nav_history),
                            subtitle = stringResource(R.string.my_buro_subtitle),
                            rows = state.watchHistory.map { entry ->
                                WatchlistRow(entry.channel, entry.progress, entry.completed)
                            },
                            emptyMessage = stringResource(R.string.history_empty),
                            onOpenChannel = onOpenChannel,
                            onBack = onBack,
                        )

                        AppContent.Discover -> DiscoverScreen(
                            deck = state.discoverDeck,
                            detailsFor = { channel ->
                                DiscoverCardDetails(
                                    // The synopsis the home rail already fetched for its banner,
                                    // reused rather than fetched again: it is the same title, and
                                    // one metadata call per card would be fifteen per hand.
                                    plot = state.heroSynopses[channel.id],
                                    genres = channel.categoryName?.let(::listOf).orEmpty(),
                                    rating = channel.rating,
                                    year = channel.year,
                                )
                            },
                            isLoading = state.isDiscoverLoading,
                            hasCatalogue = state.sources.isNotEmpty(),
                            onKeep = onDiscoverKeep,
                            onSkip = onDiscoverSkip,
                            onDealAgain = onDiscoverDealAgain,
                            onBack = onBack,
                            dealtCount = state.discoverDealtCount,
                        )

                        AppContent.Reminders -> RemindersScreen(
                            reminders = state.reminders,
                            notify = state.reminderNotify,
                            time = state.reminderTime,
                            onSetNotify = onSetReminderNotify,
                            onSetTime = onSetReminderTime,
                            onRemove = onRemoveReminder,
                            onOpen = onOpenReminder,
                            onBack = onBack,
                        )

                        AppContent.Subscriptions -> SubscriptionsScreen(
                            state = state.subscriptions,
                            onSelectKind = onSelectSubscriptionKind,
                            onSelectTitle = onOpenSubscriptionTitle,
                            onBackToShelves = onCloseSubscriptionTitle,
                            onOpenOffer = onOpenSubscriptionOffer,
                            onSeeMore = onSubscriptionSeeMore,
                            onCloseExpanded = onSubscriptionCloseExpanded,
                            onToggleReminder = onToggleReminder,
                            // Keyed by identity like every other reminder, so a title marked here
                            // and the same film later imported into a playlist are one mark.
                            hasReminder =
                                state.subscriptions.selected?.let { selected ->
                                    // The year straight from the shelf rather than parsed out of a
                                    // release date: this row carries it as a number already, and
                                    // the view model marks with exactly the same one.
                                    ContentIdentity.of(
                                        if (selected.isSeries) {
                                            ContentKind.SERIES
                                        } else {
                                            ContentKind.MOVIE
                                        },
                                        selected.title,
                                        selected.year,
                                    ).key in state.reminderKeys
                                } == true,
                        )

                        AppContent.Profiles -> ProfilePickerScreen(
                            profiles = state.profiles,
                            onSelect = onSelectProfile,
                            onCreate = onCreateProfile,
                            onOpenSettings = { onSelectSection(AppSection.SETTINGS) },
                            avatars = availableAvatars,
                            onUpdateProfile = onUpdateProfile,
                            onDeleteProfile = onDeleteProfile,
                            sources = state.sources,
                            onSelectProfileSource = onSelectProfileSource,
                            onAddSource = { onSelectSection(AppSection.SOURCES) },
                        )

                        AppContent.Settings -> SettingsContent(
                            activeProfile = state.activeProfile,
                            deviceId = state.deviceId,
                            license = state.license,
                            tmdbKeyConfigured = state.tmdbKeyConfigured,
                            onChangeProfile = onRequestProfileSelection,
                            onSaveTmdbKey = onSaveTmdbKey,
                            onOpenPurchase = onOpenPurchase,
                            onRedeemLicense = onRedeemLicense,
                            redemption = state.redemption,
                            sharedTmdbKeyConfigured = state.sharedTmdbKeyConfigured,
                            onSaveSharedTmdbKey = onSaveSharedTmdbKey,
                            onSaveCriticsKey = onSaveCriticsKey,
                            criticsKeyConfigured = state.criticsKeyConfigured,
                            onSelectLanguage = onSelectLanguage,
                            guard = guard,
                            castReceiverCode = state.castReceiverCode,
                            isCastReceiverOn = state.isCastReceiverOn,
                            onStartCastReceiver = onStartCastReceiver,
                            onStopCastReceiver = onStopCastReceiver,
                            cacheBudget = state.cacheBudget,
                            cacheBytesUsed = state.cacheBytesUsed,
                            cacheProgress = state.cacheProgress,
                            onChooseCacheBudget = onChooseCacheBudget,
                            onStartCacheFill = onStartCacheFill,
                            onStopCacheFill = onStopCacheFill,
                            onRefreshCacheFill = onRefreshCacheFill,
                            onClearCache = onClearCache,
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
        if (compactNavigation && showMobileNavigation) {
            MobileNavigationDrawer(
                selected = state.section,
                profile = state.activeProfile,
                license = state.license,
                onOpenLicense = {
                    showMobileNavigation = false
                    onSelectSection(AppSection.SETTINGS)
                },
                subscriptionsVisible = state.subscriptions.capability.isVisible,
                offlineSupported = offlineSupported,
                onSelect = { section ->
                    showMobileNavigation = false
                    onSelectSection(section)
                },
                onDismiss = { showMobileNavigation = false },
            )
        }
    }

    // The first-run cache offer.
    //
    // Held back until a profile is chosen and the catalogue is ready: asking somebody to size a
    // download of "your list" before they have one is asking about nothing, and the estimate would
    // be drawn from an empty library. By the time the app is browsable the question is concrete.
    //
    // Not dismissible by tapping away: both answers are real and are remembered, so a stray tap
    // outside must not be recorded as a decision — the viewer has to pick one or the other.
    // The connection test, over whatever the viewer was on. In a Dialog for the same reason the
    // cast sheet is: it is a short errand that should sit above the page it started from.
    onRunDiagnostics?.let { runTest ->
        if (diagnosticsOpen) {
            androidx.compose.ui.window.Dialog(onDismissRequest = { diagnosticsOpen = false }) {
                DiagnosticsDialog(
                    runTest = runTest,
                    onClose = { diagnosticsOpen = false },
                )
            }
        }
    }

    if (state.cacheChoicePending && state.activeProfile != null && state.bootStage == BootStageUi.READY) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = {},
            properties =
                androidx.compose.ui.window.DialogProperties(
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false,
                ),
        ) {
            CacheFirstRunOffer(
                onAccept = onChooseCacheBudget,
                onDecline = onDeclineCacheOffer,
            )
        }
    }

    // The cast sheet, whenever it is not Idle.
    //
    // In a Dialog rather than inline on the details page: discovery, choosing a screen and typing a
    // code is a short conversation that should sit above the page it was started from, and it must
    // survive the details page scrolling underneath it.
    if (state.cast != CastUiState.Idle) {
        androidx.compose.ui.window.Dialog(onDismissRequest = onCastClose) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(BuroSurfaceRaised, RoundedCornerShape(20.dp)),
            ) {
                CastSheet(
                    state = state.cast,
                    onSearchAgain = onCastSearchAgain,
                    onChoose = onCastChoose,
                    onBack = onCastBack,
                    onSend = onCastSend,
                    onClose = onCastClose,
                    onConnectToAddress = onCastConnectToAddress,
                )
            }
        }
    }

    // Above everything, including the drawer: the whole point is that the category behind it does
    // not open until the PIN is answered.
    state.pendingUnlock?.let { pending ->
        ParentalUnlockDialog(
            categoryName = pending.categoryName,
            wrong = state.parentalMessage == ParentalMessage.WRONG_PIN,
            onSubmit = onSubmitParentalPin,
            onDismiss = onDismissParentalPrompt,
        )
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
            // Shown on the form itself, which is where somebody who cannot fill it in gives up.
            deviceCode = state.deviceId.orEmpty(),
        )
    }

    if (showStalkerModal) {
        SecureActivityWindowEffect()
        StalkerSourceDialog(
            isImporting = state.isImporting,
            hasImportError =
                state.hasImportError &&
                    state.lastImportMethod == SourceImportMethod.STALKER,
            failure = state.stalkerFailure,
            importStage = state.xtreamImportStage,
            importSuccessVersion = state.importSuccessVersion,
            successVersionAtOpen = stalkerSuccessVersionAtOpen,
            onSubmit = onImportStalkerSource,
            onCancelImport = onCancelStalkerImport,
            onDismiss = { showStalkerModal = false },
        )
    }
}

private fun AppSection.isRibbonDestination(offlineSupported: Boolean): Boolean =
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

        AppSection.DOWNLOADS -> offlineSupported

        // Selectable whenever it is reachable at all. Whether it is reachable is the ribbon's
        // decision, taken from the capability, so this must not re-decide it from a stale flag.
        AppSection.CONTINUE_WATCHING,
        AppSection.HISTORY,
        AppSection.REMINDERS,
        AppSection.SUBSCRIPTIONS,
        AppSection.SOURCES,
        AppSection.SETTINGS,
        -> true
    }

@Composable
private fun MobileAppBar(
    section: AppSection,
    profile: ProfileUi?,
    onOpenMenu: () -> Unit,
    onOpenProfiles: () -> Unit,
    /** What the bell holds. Null draws no bell, which is what a profile-less boot wants. */
    notifications: NotificationCentre? = null,
    onMarkNotificationsRead: () -> Unit = {},
    onDismissNotification: (String) -> Unit = {},
    onClearNotifications: () -> Unit = {},
    /** Null where the screen already carries its own refresh, so there is never one twice. */
    onRefresh: (() -> Unit)? = null,
    /** Shows a spinner in place of the icon while the rails are being rebuilt. */
    isRefreshing: Boolean = false,
    /** Opens the connection test. Null on screens that carry their own header. */
    onOpenDiagnostics: (() -> Unit)? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(60.dp)
                .background(BuroCanvas.copy(alpha = 0.94f))
                .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FocusSurface(
            onClick = onOpenMenu,
            modifier = Modifier.size(44.dp),
            backgroundColor = Color.Transparent,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Menu,
                    contentDescription = stringResource(R.string.mobile_menu_open),
                    tint = BuroTextPrimary,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "IPTV BURO",
                color = BuroGold,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
            )
            Text(
                text = stringResource(section.ribbonLabelResource()),
                color = BuroTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Refreshing the playlist from the top bar, as the desktop does. The catalogue screens
        // already had their own refresh, but the home — where the app opens and where a stale
        // playlist is first noticed — had no way to ask for one without going into a category.
        onRefresh?.let { refresh ->
            // Held for a moment even when the work finishes at once. Cached shelves and a warm
            // catalogue can complete in well under a second, and a spinner that appears and
            // vanishes between frames reads as nothing having happened at all.
            var pressedAt by remember { mutableStateOf(0L) }
            var holding by remember { mutableStateOf(false) }
            LaunchedEffect(pressedAt) {
                if (pressedAt == 0L) return@LaunchedEffect
                holding = true
                delay(REFRESH_FEEDBACK_MILLIS)
                holding = false
            }
            val showSpinner = isRefreshing || holding

            FocusSurface(
                onClick = {
                    pressedAt = System.currentTimeMillis()
                    refresh()
                },
                modifier = Modifier.size(42.dp),
                backgroundColor = Color.Transparent,
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    // A spinner while it works. The button gave no sign it had been pressed, so a
                    // refresh over a catalogue of tens of thousands of items read as a dead button.
                    if (showSpinner) {
                        CircularProgressIndicator(
                            color = BuroGold,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp),
                        )
                    } else {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.common_refresh),
                            tint = BuroTextPrimary,
                        )
                    }
                }
            }
        }
        // The connection test, beside the refresh button.
        //
        // The two answer the same complaint from opposite ends: "my list is wrong" and "the picture
        // keeps freezing". Somebody who cannot tell which of the two they have will try both, and
        // both are here.
        onOpenDiagnostics?.let { openDiagnostics ->
            FocusSurface(
                onClick = openDiagnostics,
                modifier = Modifier.size(42.dp),
                backgroundColor = Color.Transparent,
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.NetworkCheck,
                        contentDescription = stringResource(R.string.diagnostics_action),
                        tint = BuroTextPrimary,
                    )
                }
            }
        }
        // Beside the profile rather than in the drawer: the bell says whether there is anything to
        // look at, and a bell nobody can see says nothing at all.
        notifications?.let { centre ->
            NotificationBell(
                centre = centre,
                onMarkAllRead = onMarkNotificationsRead,
                onDismiss = onDismissNotification,
                onClearAll = onClearNotifications,
            )
        }
        profile?.let { selectedProfile ->
            FocusSurface(
                onClick = onOpenProfiles,
                modifier = Modifier.size(42.dp),
                backgroundColor = Color.Transparent,
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(if (selectedProfile.isKids) BuroAccent else BuroGold),
                    contentAlignment = Alignment.Center,
                ) {
                    // The chosen photo, where there is one. Somebody who set their own face
                    // expects to see it here as well as on the profile tile; an initial in its
                    // place reads as the app having forgotten the choice.
                    if (selectedProfile.photoUri != null) {
                        AsyncImage(
                            model = selectedProfile.photoUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text(
                            text = selectedProfile.name.firstOrNull()?.uppercase() ?: "B",
                            color = BuroCanvas,
                            fontWeight = FontWeight.Black,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MobileNavigationDrawer(
    selected: AppSection,
    profile: ProfileUi?,
    license: LicenseUiState,
    onOpenLicense: () -> Unit,
    subscriptionsVisible: Boolean,
    offlineSupported: Boolean,
    onSelect: (AppSection) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.66f))
                .clickable(onClick = onDismiss),
    ) {
        Column(
            modifier =
                Modifier
                    .width(310.dp)
                    .fillMaxHeight()
                    .background(BuroCanvas)
                    .clickable(onClick = {})
                    .padding(horizontal = 16.dp, vertical = 18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "IPTV BURO",
                        color = BuroGold,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                    )
                    Text(
                        text = profile?.name.orEmpty(),
                        color = BuroTextSecondary,
                        fontSize = 13.sp,
                    )
                }
                FocusSurface(
                    onClick = onDismiss,
                    modifier = Modifier.size(42.dp),
                    backgroundColor = Color.Transparent,
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.mobile_menu_close),
                            tint = BuroTextPrimary,
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            // How long is left, and the way to buy more. The desktop has carried this since
            // ADR-004; without it the only sign of an expiring licence on Android was being
            // locked out one morning.
            LicenseChip(license = license, onClick = onOpenLicense)
            Spacer(Modifier.height(14.dp))
            // Scrolls, because the list is longer than a phone screen.
            //
            // Reported as "Configurações não aparece": the drawer was a plain Column, so every
            // destination past the bottom edge was simply unreachable — Perfis was the last one
            // visible and Settings sat below it with no way to get there. The list grew past the
            // screen when Pesquisa, Continuar assistindo, Histórico and Assinaturas were added,
            // and nothing complained because a Column silently clips what does not fit.
            //
            // `weight(1f)` first, so the scrolling area is what is left after the header and the
            // licence chip rather than the whole height: those two must stay put while the
            // destinations move under them.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                mobileDestinations(subscriptionsVisible, offlineSupported).forEach { destination ->
                    MobileDrawerItem(
                        label = stringResource(destination.section.ribbonLabelResource()),
                        icon = destination.icon,
                        selected = selected == destination.section,
                        onClick = { onSelect(destination.section) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MobileDrawerItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FocusSurface(
        onClick = onClick,
        selected = selected,
        modifier = Modifier.fillMaxWidth().height(50.dp).padding(vertical = 2.dp),
        backgroundColor = Color.Transparent,
        selectedBackgroundColor = BuroGold.copy(alpha = 0.22f),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) BuroGold else BuroTextSecondary,
                modifier = Modifier.size(21.dp),
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

private data class MobileDestination(val section: AppSection, val icon: ImageVector)

private fun mobileDestinations(
    subscriptionsVisible: Boolean,
    offlineSupported: Boolean,
): List<MobileDestination> =
    buildList {
        add(MobileDestination(AppSection.HOME, Icons.Default.Home))
        // Second, straight after Home: searching is how anyone finds a specific title in a
        // catalogue of tens of thousands, and the destination existed with no way in.
        add(MobileDestination(AppSection.SEARCH, Icons.Default.Search))
        add(MobileDestination(AppSection.LIVE, Icons.Default.LiveTv))
        add(MobileDestination(AppSection.MOVIES, Icons.Default.Movie))
        add(MobileDestination(AppSection.SERIES, Icons.Default.VideoLibrary))
        // Above Meu BURO rather than below: Descobrir is what fills the favourites list, so it
        // belongs beside the catalogue it draws from rather than after the shelf it feeds.
        add(MobileDestination(AppSection.DISCOVER, Icons.Default.Explore))
        add(MobileDestination(AppSection.MY_BURO, Icons.Default.Favorite))
        add(MobileDestination(AppSection.CONTINUE_WATCHING, Icons.Default.PlayCircle))
        add(MobileDestination(AppSection.HISTORY, Icons.Default.History))
        // Beside Histórico rather than inside Settings: the destination answers "what did I mark",
        // which is a library question, and the daily-notice controls ride along on that page
        // because the only reason to open it is the same list they act on.
        add(MobileDestination(AppSection.REMINDERS, Icons.Default.Notifications))
        // The phone drawer is the primary navigation on a small screen, so a destination missing
        // here is missing outright — this is where Assinaturas has to appear once it is real.
        if (subscriptionsVisible) {
            add(MobileDestination(AppSection.SUBSCRIPTIONS, Icons.Default.VideoLibrary))
        }
        if (offlineSupported) {
            add(MobileDestination(AppSection.DOWNLOADS, Icons.Default.Folder))
        }
        add(MobileDestination(AppSection.SOURCES, Icons.Default.Router))
        add(MobileDestination(AppSection.PROFILE, Icons.Default.Person))
        add(MobileDestination(AppSection.SETTINGS, Icons.Default.Settings))
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
        AppSection.CONTINUE_WATCHING -> R.string.nav_continue_watching
        AppSection.HISTORY -> R.string.nav_history
        AppSection.REMINDERS -> R.string.nav_reminders
        AppSection.SUBSCRIPTIONS -> R.string.nav_subscriptions
        AppSection.DOWNLOADS -> R.string.buro_nav_downloads
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
            label = stringResource(R.string.buro_nav_live),
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
    /** Whether every configured list is browsed as one catalogue. */
    mergeSources: Boolean,
    onToggleMergeSources: (Boolean) -> Unit,
    isImporting: Boolean,
    lastImportedChannelCount: Int?,
    hasImportError: Boolean,
    lastImportMethod: SourceImportMethod?,
    stalkerFailure: StalkerFailureUi?,
    xtreamImportStage: XtreamImportStageUi?,
    downloadBytesPerSecond: Long?,
    onImportSource: () -> Unit,
    onOpenXtream: () -> Unit,
    onOpenStalker: () -> Unit,
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
                    stalkerFailure = stalkerFailure,
                    xtreamImportStage = xtreamImportStage,
                    isImporting = isImporting,
                    compact = true,
                    downloadBytesPerSecond = downloadBytesPerSecond,
                )
                Spacer(Modifier.height(14.dp))
                SourceImportActions(
                    isImporting = isImporting,
                    lastImportMethod = lastImportMethod,
                    onImportSource = onImportSource,
                    onOpenXtream = onOpenXtream,
                    onOpenStalker = onOpenStalker,
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
                        stalkerFailure = stalkerFailure,
                        xtreamImportStage = xtreamImportStage,
                        isImporting = isImporting,
                        compact = false,
                        downloadBytesPerSecond = downloadBytesPerSecond,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(18.dp))
                    SourceImportActions(
                        isImporting = isImporting,
                        lastImportMethod = lastImportMethod,
                        onImportSource = onImportSource,
                        onOpenXtream = onOpenXtream,
                        onOpenStalker = onOpenStalker,
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
                // Only with more than one list: with a single one there is nothing to merge, and
                // the switch would be a question about nothing.
                if (sources.size > 1) {
                    Column(modifier = Modifier.padding(bottom = 14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.merge_sources_title),
                                color = BuroTextPrimary,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = mergeSources,
                                onCheckedChange = onToggleMergeSources,
                                // Material's default switch is purple, which is not a colour this
                                // app uses anywhere.
                                colors =
                                    SwitchDefaults.colors(
                                        checkedThumbColor = BuroGold,
                                        checkedTrackColor = BuroGold.copy(alpha = 0.35f),
                                        uncheckedThumbColor = BuroTextSecondary,
                                        uncheckedTrackColor = BuroSurfaceRaised,
                                    ),
                            )
                        }
                        Text(
                            text = stringResource(R.string.merge_sources_help),
                            color = BuroTextSecondary,
                            fontSize = 12.sp,
                        )
                    }
                }
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
    stalkerFailure: StalkerFailureUi?,
    xtreamImportStage: XtreamImportStageUi?,
    isImporting: Boolean,
    compact: Boolean,
    downloadBytesPerSecond: Long? = null,
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
                        lastImportMethod != SourceImportMethod.M3U_FILE &&
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
            // The speed, on its own line, while bytes are actually arriving.
            //
            // A catalogue of tens of thousands of titles takes long enough that the stage name
            // alone cannot say whether it is working or stuck. Absent rather than zero when there
            // is nothing to report, which is the case between requests and while the rows are
            // being written to the database — that part is local work, and a network rate there
            // would be a fiction.
            formatDownloadRate(downloadBytesPerSecond)?.let { rate ->
                Text(
                    text = stringResource(R.string.download_rate, rate),
                    color = BuroTextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        } else if (lastImportedChannelCount != null || hasImportError) {
            Text(
                text =
                    if (hasImportError) {
                        stringResource(
                            when (lastImportMethod) {
                                SourceImportMethod.XTREAM -> R.string.sources_xtream_error
                                SourceImportMethod.STALKER ->
                                    stalkerFailure.messageResource()

                                else -> R.string.sources_import_error
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
    onOpenStalker: () -> Unit,
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
    val stalkerButton: @Composable () -> Unit = {
        BuroButton(
            onClick = onOpenStalker,
            enabled = !isImporting,
            style = BuroButtonStyle.Secondary,
            modifier = if (stack) Modifier.fillMaxWidth() else Modifier,
        ) {
            Icon(Icons.Default.Router, contentDescription = null)
            Text(
                text =
                    stringResource(
                        if (
                            isImporting &&
                            lastImportMethod == SourceImportMethod.STALKER
                        ) {
                            R.string.sources_stalker_connecting
                        } else {
                            R.string.sources_stalker_action
                        },
                    ),
            )
        }
    }

    if (stack) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            fileButton()
            xtreamButton()
            stalkerButton()
        }
    } else {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            fileButton()
            xtreamButton()
            stalkerButton()
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
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onOpenCategory: (CategoryUi) -> Unit,
    /** The services' official logos, by service name. Empty until the directory loads. */
    providerLogos: Map<String, String> = emptyMap(),
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
            ScreenHeader(title = title, onBack = onBack, onRefresh = onRefresh)
            Spacer(Modifier.height(if (compactPortrait) 18.dp else 24.dp))

            // The services this list actually holds, as a row of marks to jump straight to one.
            //
            // Built from the categories rather than from a fixed list of services: a provider that
            // files nothing under Netflix should not offer a Netflix shortcut that leads nowhere.
            //
            // Expect this to be empty under Filmes on many playlists. Measured on a real 30,000
            // title list: every film category is a genre — "Filmes | Terror", "Filmes | Acao" —
            // while the platform catalogues are all filed under series. An empty row there is the
            // honest answer, not a fault.
            val platforms =
                remember(categories, providerLogos) {
                    categories
                        .mapNotNull { category ->
                            category.id ?: return@mapNotNull null
                            providerIdentityFor(category.name)?.let { identity ->
                                identity.copy(logoUrl = providerLogos[identity.label]) to category
                            }
                        }
                        .distinctBy { (identity, _) -> identity.label }
                }
            if (platforms.isNotEmpty()) {
                PlatformShortcuts(
                    platforms = platforms,
                    compact = compactPortrait,
                    onOpenCategory = onOpenCategory,
                )
                Spacer(Modifier.height(if (compactPortrait) 16.dp else 22.dp))
            }

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
                            // Glyph and tint come from the category's own wording. Every card used
                            // to draw the same accent-tinted icon over one of six shared atlas
                            // tiles, so a grid of twenty categories told the user nothing.
                            val badge =
                                categoryBadgeFor(
                                    label = if (category.id == null) "" else category.name,
                                    contentType = contentType,
                                )
                            Box(modifier = Modifier.fillMaxSize()) {
                                CategoryArtwork(
                                    artworkUrl = category.artworkUrl,
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
                                        Box(
                                            modifier =
                                                Modifier
                                                    .size(if (compactPortrait) 30.dp else 34.dp)
                                                    .clip(CircleShape)
                                                    .background(badge.tint.copy(alpha = 0.20f)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = badge.glyph,
                                                fontSize = if (compactPortrait) 15.sp else 17.sp,
                                            )
                                        }
                                        Spacer(Modifier.weight(1f))
                                        providerIdentityFor(category.name.takeIf { category.id != null })
                                            ?.let { provider ->
                                                // The service's real mark when the directory has
                                                // one; its monogram on the brand colour until then.
                                                ProviderMark(
                                                    provider =
                                                        provider.copy(
                                                            logoUrl = providerLogos[provider.label],
                                                        ),
                                                    size = if (compactPortrait) 26.dp else 30.dp,
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
                                        color = badge.tint,
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
    artworkUrl: String?,
    tile: CategoryArtworkTile,
    modifier: Modifier = Modifier,
) {
    if (!artworkUrl.isNullOrBlank()) {
        AsyncImage(
            model =
                ImageRequest.Builder(LocalPlatformContext.current)
                    .data(artworkUrl)
                    // Provider artwork may contain a short-lived URL. It must not survive in a
                    // disk cache after the source is disconnected.
                    .build(),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
        return
    }
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
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    headerAction: (@Composable () -> Unit)? = null,
    /** Filter state and the pickers that change it. Absent on screens with nothing to narrow. */
    filterBar: (@Composable () -> Unit)? = null,
    layout: CatalogueLayout = CatalogueLayout.POSTER,
    /** Hoisted by the caller so the scroll position survives opening a title. */
    gridState: LazyGridState,
    /**
     * What this whole screen is a list of, when it is a list of one thing.
     *
     * Passed down to the cards so they can drop a label the header has already given.
     */
    impliedContentType: CatalogContentType? = null,
    /** The services' official logos, by service name. */
    providerLogos: Map<String, String> = emptyMap(),
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
            ScreenHeader(title = title, subtitle = subtitle, onBack = onBack, onRefresh = onRefresh)
            headerAction?.let {
                Spacer(Modifier.height(12.dp))
                it()
            }
            filterBar?.let {
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
                    state = gridState,
                    // The chosen layout decides the column width: posters are browsed, compact is
                    // scanned, and a list gives the name the room where the name is the meaning.
                    columns =
                        when (layout) {
                            CatalogueLayout.LIST -> GridCells.Fixed(1)
                            // Fixed, not adaptive: on a 360dp phone an adaptive 104dp still
                            // resolved to two columns once padding was taken out, so Compact
                            // looked exactly like Posters and the choice appeared to do nothing.
                            CatalogueLayout.COMPACT ->
                                if (compactPortrait) {
                                    GridCells.Fixed(3)
                                } else {
                                    GridCells.Fixed(6)
                                }
                            CatalogueLayout.POSTER ->
                                if (compactPortrait) {
                                    GridCells.Adaptive(minSize = 150.dp)
                                } else {
                                    GridCells.Fixed(4)
                                }
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
                            layout = layout,
                            impliedContentType = impliedContentType,
                            providerLogos = providerLogos,
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
    /** Drops a title from favourites, from the list itself rather than from its details page. */
    onRemoveFavorite: ((ChannelUi) -> Unit)? = null,
    favorites: List<ChannelUi>,
    continueWatching: List<ContinueWatchingUi>,
    history: List<WatchHistoryUi>,
    isLoading: Boolean,
    hasError: Boolean,
    onOpenChannel: (ChannelUi) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 20.dp)) {
        ScreenHeader(
            title = stringResource(R.string.my_buro_title),
            subtitle = stringResource(R.string.my_buro_subtitle),
            onBack = onBack,
            onRefresh = onRetry,
        )
        Spacer(Modifier.height(18.dp))
        when {
            isLoading -> CatalogLoadingState()
            hasError && favorites.isEmpty() ->
                BuroErrorState(
                    title = stringResource(R.string.catalog_error_title),
                    message = stringResource(R.string.catalog_error_body),
                    actionLabel = stringResource(R.string.common_retry),
                    onAction = onRetry,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            favorites.isEmpty() ->
                BuroEmptyState(
                    // Favourites-specific now: the old wording spoke about watching things,
                    // which belonged to the sections that have moved out.
                    title = stringResource(R.string.favorites_empty),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            else -> {
                // Films, series and live channels all land in one list, and with a few hundred
                // favourites finding the series you wanted meant scrolling past every film. The
                // filter is offered only for the kinds actually present, so it never shows a tab
                // that leads to an empty screen.
                var kind by remember { mutableStateOf<CatalogContentType?>(null) }
                val presentKinds =
                    remember(favorites) {
                        listOf(
                            CatalogContentType.MOVIE,
                            CatalogContentType.SERIES,
                            CatalogContentType.LIVE,
                        ).filter { candidate -> favorites.any { it.contentType == candidate } }
                    }
                val shown =
                    remember(favorites, kind) {
                        kind?.let { selected ->
                            favorites.filter { it.contentType == selected }
                        } ?: favorites
                    }

                if (presentKinds.size > 1) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item(key = "kind-all") {
                            FavoriteKindChip(
                                label = stringResource(R.string.catalogue_filter_all),
                                selected = kind == null,
                                onClick = { kind = null },
                            )
                        }
                        items(presentKinds, key = { it.name }) { candidate ->
                            FavoriteKindChip(
                                label = stringResource(candidate.favoriteKindLabel()),
                                selected = kind == candidate,
                                onClick = { kind = candidate },
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                LazyColumn(
                    contentPadding = PaddingValues(bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Continue watching and History are destinations of their own now. Repeating
                    // them here made Favourites a third copy of both, so the same title appeared in
                    // three places and the screen no longer said what it was for.
                    item("favorites-title") { LibrarySectionTitle(stringResource(R.string.my_buro_favorites)) }
                    items(shown, key = { "favorite:${it.id}" }) { movie ->
                        MyBuroMediaRow(
                            channel = movie,
                            progress = null,
                            status = movie.categoryName.orEmpty(),
                            onOpen = { onOpenChannel(movie) },
                            onRemove = onRemoveFavorite?.let { remove -> { remove(movie) } },
                        )
                    }
                }
            }
        }
    }
}

/** Which kind of favourite is being shown. Reuses the navigation labels rather than new wording. */
private fun CatalogContentType.favoriteKindLabel(): Int =
    when (this) {
        CatalogContentType.MOVIE -> R.string.buro_nav_movies
        CatalogContentType.SERIES -> R.string.buro_nav_series
        else -> R.string.buro_nav_live
    }

@Composable
private fun FavoriteKindChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FocusSurface(
        onClick = onClick,
        selected = selected,
        shape = RoundedCornerShape(50),
        contentAlignment = Alignment.Center,
        modifier = Modifier.height(38.dp),
    ) {
        Text(
            text = label,
            color = if (selected) BuroAccent else BuroTextSecondary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun LibrarySectionTitle(label: String) {
    Text(
        label,
        color = BuroTextPrimary,
        fontSize = 19.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
    )
}

@Composable
private fun MyBuroMediaRow(
    channel: ChannelUi,
    progress: Float?,
    status: String,
    onOpen: () -> Unit,
    /**
     * Drops this title from favourites, offered only where that is what the list is.
     *
     * Null on Continue assistindo and Histórico, which describe what happened rather than what was
     * chosen — there is nothing to undo there. Reported as missing from Favourites, where the only
     * way to unfavourite something was to open it and find the heart: obvious once you know it,
     * and invisible until then, on the one screen whose whole job is managing that list.
     */
    onRemove: (() -> Unit)? = null,
) {
    FocusSurface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().height(96.dp),
        backgroundColor = BuroSurface,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CatalogArtwork(
                url = channel.logoUrl,
                label = channel.name,
                modifier = Modifier.width(58.dp).height(76.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    channel.name,
                    color = BuroTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (status.isNotBlank()) {
                    Text(status, color = BuroTextSecondary, fontSize = 12.sp, maxLines = 1)
                }
                progress?.let { value ->
                    Spacer(Modifier.height(7.dp))
                    Box(
                        Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(BuroCanvas),
                    ) {
                        Box(
                            Modifier.fillMaxWidth(value.coerceIn(0f, 1f)).fillMaxHeight()
                                .background(BuroAccent),
                        )
                    }
                }
            }
            onRemove?.let { remove ->
                Spacer(Modifier.width(6.dp))
                // Its own focus target inside the row, so a remote or a keyboard can reach it —
                // the row itself opens the title, and a control that only a finger can hit would
                // be missing on a television.
                FocusSurface(
                    onClick = remove,
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    backgroundColor = Color.Transparent,
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        // A broken heart rather than a bin: this removes a mark, it does not delete
                        // the title, and a bin on a row of films suggests otherwise.
                        imageVector = Icons.Default.HeartBroken,
                        contentDescription = stringResource(R.string.favorites_remove),
                        tint = BuroTextSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CatalogArtwork(
    url: String?,
    label: String,
    modifier: Modifier = Modifier,
) {
    if (!url.isNullOrBlank()) {
        AsyncImage(
            model =
                ImageRequest.Builder(LocalPlatformContext.current)
                    .data(url)
                    .build(),
            contentDescription = null,
            modifier = modifier.clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier.clip(RoundedCornerShape(8.dp)).background(BuroAccent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label.firstOrNull()?.uppercase() ?: "B",
                color = BuroAccent,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

/**
 * The offline library.
 *
 * A top-level ribbon destination, so it carries no back affordance: the ribbon is the way out and a
 * back button here would compete with it for the first focus.
 */
@Composable
private fun DownloadsContent(
    entries: List<DownloadEntryUi>,
    onPlayDownload: (String) -> Unit,
    onCancelDownload: (String) -> Unit,
    onDeleteDownload: (String) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compactPortrait = maxWidth < 600.dp && maxHeight >= maxWidth
        val colors = BuroTheme.colors
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = if (compactPortrait) 16.dp else 42.dp,
                    vertical = if (compactPortrait) 18.dp else 34.dp,
                ),
        ) {
            Text(
                text = stringResource(R.string.downloads_title),
                color = colors.textPrimary,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(if (compactPortrait) 14.dp else 20.dp))

            // Films, series and a compact toggle, matching the desktop's catalogue header. A list
            // of offline copies grows to whatever was kept for a journey, and telling a downloaded
            // series apart from a downloaded film meant reading every title.
            var episodesOnly by remember { mutableStateOf<Boolean?>(null) }
            var compactRows by remember { mutableStateOf(false) }
            val hasFilms = remember(entries) { entries.any { !it.isEpisode } }
            val hasEpisodes = remember(entries) { entries.any(DownloadEntryUi::isEpisode) }
            val shown =
                remember(entries, episodesOnly) {
                    episodesOnly?.let { wantEpisodes ->
                        entries.filter { it.isEpisode == wantEpisodes }
                    } ?: entries
                }

            if (entries.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (hasFilms && hasEpisodes) {
                        item(key = "downloads-all") {
                            FavoriteKindChip(
                                label = stringResource(R.string.catalogue_filter_all),
                                selected = episodesOnly == null,
                                onClick = { episodesOnly = null },
                            )
                        }
                        item(key = "downloads-movies") {
                            FavoriteKindChip(
                                label = stringResource(R.string.buro_nav_movies),
                                selected = episodesOnly == false,
                                onClick = { episodesOnly = false },
                            )
                        }
                        item(key = "downloads-series") {
                            FavoriteKindChip(
                                label = stringResource(R.string.buro_nav_series),
                                selected = episodesOnly == true,
                                onClick = { episodesOnly = true },
                            )
                        }
                    }
                    item(key = "downloads-compact") {
                        FavoriteKindChip(
                            label = stringResource(R.string.catalogue_layout_compact),
                            selected = compactRows,
                            onClick = { compactRows = !compactRows },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            if (entries.isEmpty()) {
                BuroEmptyState(
                    title = stringResource(R.string.downloads_empty_title),
                    message = stringResource(R.string.downloads_empty_body),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 30.dp),
                    verticalArrangement = Arrangement.spacedBy(BuroSpacing.Xs),
                ) {
                    items(
                        items = shown,
                        key = DownloadEntryUi::contentKey,
                    ) { entry ->
                        DownloadEntryRow(
                            entry = entry,
                            compact = compactRows,
                            onPlay = { onPlayDownload(entry.contentKey) },
                            onCancel = { onCancelDownload(entry.contentKey) },
                            onDelete = { onDeleteDownload(entry.contentKey) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * One offline copy.
 *
 * The whole row is the focus target and its click is the row's only action, because a D-pad remote
 * has no way to reach a second control inside a row without turning every entry into a two-stop
 * journey. Which action that is follows the state: cancel while work is in flight, remove once
 * there is a file to remove.
 */
@Composable
private fun DownloadEntryRow(
    entry: DownloadEntryUi,
    /** Drops the cover and tightens the row, so more of the list fits on one screen. */
    compact: Boolean,
    onPlay: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = BuroTheme.colors
    val state = entry.state
    val isRunning = state is DownloadStateUi.Running
    val isCompleted = state == DownloadStateUi.Completed
    val actionLabel = stringResource(
        when {
            isRunning -> R.string.download_cancel
            isCompleted -> R.string.download_watch
            else -> R.string.download_delete
        },
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FocusSurface(
            onClick = when {
                isRunning -> onCancel
                isCompleted -> onPlay
                else -> onDelete
            },
            modifier = Modifier.weight(1f),
            backgroundColor = colors.surface,
            focusedBackgroundColor = colors.elevated,
        ) { _ ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = if (compact) 8.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The cover, where the title has one. A list of names alone is hard to scan when
                // several episodes of the same series are queued together — which is exactly why
                // compact drops it: the trade is recognisability for how much fits on screen.
                entry.artworkUrl?.takeIf { !compact }?.let { artwork ->
                    AsyncImage(
                        model = artwork,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(44.dp)
                            .height(64.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                    Spacer(Modifier.width(12.dp))
                }
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.title,
                        color = colors.textPrimary,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(BuroSpacing.Xs))
                    Text(
                        text = actionLabel,
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Spacer(Modifier.height(6.dp))

                if (state is DownloadStateUi.Running) {
                    DownloadProgressBar(fraction = state.fraction)
                    Spacer(Modifier.height(6.dp))
                }

                Text(
                    // The rate joins the status on one line: "Baixando… 42%  ·  8,4 MB/s". Two
                    // separate lines for one fact would push the row taller for no gain.
                    text =
                        buildString {
                            append(state.label())
                            (state as? DownloadStateUi.Running)?.bytesPerSecond?.let { rate ->
                                append("  ·  ")
                                append(formatTransferRate(rate))
                            }
                        },
                    color =
                        when (state) {
                            DownloadStateUi.Completed -> colors.success
                            DownloadStateUi.Failed -> colors.error
                            else -> colors.textSecondary
                        },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            }
        }
        if (isCompleted) {
            FocusSurface(
                onClick = onDelete,
                modifier = Modifier.size(54.dp),
                backgroundColor = colors.surface,
                focusedBackgroundColor = colors.elevated,
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.download_delete),
                        tint = colors.textSecondary,
                    )
                }
            }
        }
    }
}

/**
 * Progress track for a running download.
 *
 * A negative [fraction] means the server never sent a content length. The track is then left empty
 * rather than animated, because a moving bar would claim a position the app cannot measure; the
 * row's label carries the honest "downloading, size unknown" wording instead.
 */
@Composable
private fun DownloadProgressBar(fraction: Float) {
    val colors = BuroTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(CircleShape)
            .background(colors.canvas),
    ) {
        if (fraction >= 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(colors.brandPrimary),
            )
        }
    }
}

@Composable
private fun ChannelCard(
    channel: ChannelUi,
    onOpenChannel: (ChannelUi) -> Unit,
    enabled: Boolean,
    /**
     * How much room this card gets.
     *
     * The card used to impose a 2:3 poster ratio whatever the grid asked for, so the layout picker
     * changed the number of columns and nothing else: LIST drew one enormous poster per screen,
     * and COMPACT was indistinguishable from POSTER. The shape has to follow the choice.
     */
    layout: CatalogueLayout = CatalogueLayout.POSTER,
    /**
     * What the surrounding screen already says everything here is.
     *
     * Inside Filmes every card is a film, so stamping "FILMES" on all of them tells the viewer
     * something the header told them already and covers a corner of artwork to do it. Null for a
     * mixed list — search results, a source's whole catalogue — where the label is the only thing
     * distinguishing a film from a channel.
     */
    impliedContentType: CatalogContentType? = null,
    /** The services' official logos, by service name. Empty until the directory loads. */
    providerLogos: Map<String, String> = emptyMap(),
) {
    val isPoster =
        channel.contentType == CatalogContentType.MOVIE ||
            channel.contentType == CatalogContentType.SERIES ||
            channel.contentType == CatalogContentType.EPISODE
    // An episode inside a series list is still worth marking, so only an exact match is redundant.
    val showsTypeBadge = channel.contentType != impliedContentType
    val artworkRequest =
        channel.logoUrl
            ?.takeIf(String::isNotBlank)
            ?.let { artworkUrl ->
                ImageRequest.Builder(LocalPlatformContext.current)
                    .data(artworkUrl)
                    // Signed provider artwork stays in memory and is never written to Coil's disk cache.
                    .build()
            }
    FocusSurface(
        onClick = { onOpenChannel(channel) },
        enabled = enabled,
        modifier =
            when (layout) {
                // A row, not a poster: the name is what carries the meaning here, so the artwork
                // shrinks to a thumbnail and the row stays short enough to scan a dozen at once.
                CatalogueLayout.LIST -> Modifier.fillMaxWidth().height(96.dp)
                else -> Modifier.aspectRatio(if (isPoster) 2f / 3f else 16f / 9f)
            },
        backgroundColor = BuroSurface,
        focusedBackgroundColor = BuroSurface,
    ) { isFocused ->
        // A row reads left to right: thumbnail, then the name with room to be read. Overlaying the
        // title on a wide image the way a poster does would leave the name floating over whatever
        // happened to be in the middle of the artwork.
        if (layout == CatalogueLayout.LIST) {
            Row(
                modifier = Modifier.fillMaxSize().padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(if (isPoster) 2f / 3f else 16f / 9f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(BuroCanvas),
                    contentAlignment = Alignment.Center,
                ) {
                    if (artworkRequest != null) {
                        AsyncImage(
                            model = artworkRequest,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Text(
                            text = channel.name.firstOrNull()?.uppercase() ?: "▶",
                            color = BuroTextPrimary.copy(alpha = 0.74f),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = channel.name,
                        color = BuroTextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    channel.categoryName?.let { category ->
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = category,
                            color = BuroTextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            return@FocusSurface
        }

        // Artwork above, caption below, matching the home screen.
        //
        // The overlay version ran a gradient over the bottom half of every poster and wrote the
        // title across it — on the grid whose whole purpose is browsing by cover. The caption has
        // its own room now and the artwork is whole.
        Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
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

            // The streaming service wins the corner when the category names one.
            //
            // Inside "Series | Netflix" every card is Netflix, so the service badge is the useful
            // thing to show and the type label is the redundant one — the reverse of a mixed list.
            val provider =
                providerIdentityFor(channel.categoryName)
                    ?.let { identity -> identity.copy(logoUrl = providerLogos[identity.label]) }
            if (provider != null) {
                ProviderMark(
                    provider = provider,
                    size = 28.dp,
                    modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
                )
            } else if (showsTypeBadge) {
                Text(
                    text = channel.contentType.catalogLabel(),
                    color =
                        if (channel.contentType == CatalogContentType.LIVE) {
                            BuroAccent
                        } else {
                            BuroTextPrimary
                        },
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
            }

        }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 7.dp, start = 2.dp, end = 2.dp, bottom = 2.dp),
            ) {
                // One line, which scrolls itself while this card is focused or pressed.
                //
                // The caption has fixed room, so a two-line title would push the category out. A
                // long name is held to one line and reveals the rest by scrolling — "Desastre
                // Total: Festival Astrow…" is how a grid of covers stops being tellable apart.
                BuroMarqueeText(
                    text = channel.name,
                    active = isFocused,
                    color = BuroTextPrimary,
                    fontSize = if (isPoster) 15.sp else 14.sp,
                    lineHeight = if (isPoster) 18.sp else 17.sp,
                    fontWeight = if (isFocused) FontWeight.Bold else FontWeight.SemiBold,
                )
                // Dropped when it merely repeats the name, which some providers do: a category
                // line identical to the title was invisible over the artwork and reads as a fault
                // once the two sit one under the other.
                channel.categoryName
                    ?.takeIf { category -> !category.equals(channel.name, ignoreCase = true) }
                    ?.let {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = it,
                            color = BuroTextSecondary,
                            fontSize = 11.sp,
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
            CatalogContentType.UNKNOWN -> R.string.buro_nav_live
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

// Internal rather than private so the reminders page can use the same header: every full-screen
// destination in this package wears one, and a second copy would drift from this one.
@Composable
internal fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    onRefresh: (() -> Unit)? = null,
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
            onRefresh?.let { refresh ->
                Spacer(Modifier.width(if (compact) 8.dp else 12.dp))
                FocusSurface(
                    onClick = refresh,
                    modifier = Modifier.size(if (compact) 48.dp else 52.dp),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.catalog_refresh),
                            tint = BuroAccent,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsContent(
    activeProfile: ProfileUi?,
    deviceId: String?,
    license: LicenseUiState,
    tmdbKeyConfigured: Boolean,
    onChangeProfile: () -> Unit,
    onSaveTmdbKey: (String) -> Unit,
    onOpenPurchase: (String) -> Unit,
    onRedeemLicense: (String) -> Unit,
    /** Outcome of the last key attempt, so the licence card can report it. */
    redemption: RedemptionUi,
    sharedTmdbKeyConfigured: Boolean,
    /** Whether an OMDb key is stored, which is what makes the critics' row possible. */
    criticsKeyConfigured: Boolean = false,
    onSaveCriticsKey: (String) -> Unit = {},
    onSaveSharedTmdbKey: (String) -> Unit,
    onSelectLanguage: (String) -> Unit,
    guard: CatalogueGuardUi,
    /** This device's pairing code, once one has been minted. */
    castReceiverCode: String?,
    /** Whether the sockets are open right now. */
    isCastReceiverOn: Boolean,
    onStartCastReceiver: () -> Unit,
    onStopCastReceiver: () -> Unit,
    /** How much artwork this device keeps, and what the fill is doing about it. */
    cacheBudget: CacheBudget,
    cacheBytesUsed: Long,
    cacheProgress: CacheFillProgress,
    onChooseCacheBudget: (Int) -> Unit,
    onStartCacheFill: () -> Unit,
    onStopCacheFill: () -> Unit,
    onRefreshCacheFill: () -> Unit,
    onClearCache: () -> Unit,
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

            val groupGap = if (compact) 26.dp else 34.dp
            val cardGap = if (compact) 10.dp else 14.dp

            // Grouped rather than listed. The screen was one column of unlabelled cards and finding
            // a setting meant reading every one of them; the headings let the eye skip a whole
            // group at once. The order follows how often a setting is touched: what changes
            // playback first, then keys, then other devices, then the things set once.
            SettingsGroup(
                title = stringResource(R.string.settings_group_playback),
                subtitle = stringResource(R.string.settings_group_playback_hint),
                compact = compact,
            ) {
                Column {
                    // Subtitles, the channel lock and the category list, matching Windows.
                    CatalogueGuardSections(guard = guard, compact = compact)
                    Spacer(Modifier.height(cardGap))
                    CacheSettingsCard(
                        budget = cacheBudget,
                        bytesUsed = cacheBytesUsed,
                        progress = cacheProgress,
                        onChooseBudget = onChooseCacheBudget,
                        onStartFill = onStartCacheFill,
                        onStopFill = onStopCacheFill,
                        onRefreshFill = onRefreshCacheFill,
                        onClearCache = onClearCache,
                        compact = compact,
                    )
                }
            }
            Spacer(Modifier.height(groupGap))

            SettingsGroup(
                title = stringResource(R.string.settings_group_metadata),
                subtitle = stringResource(R.string.settings_group_metadata_hint),
                compact = compact,
            ) {
                Column {
                    TmdbSettingsCard(
                        profileId = activeProfile?.id,
                        configured = tmdbKeyConfigured,
                        profileName = activeProfile?.name,
                        sharedConfigured = sharedTmdbKeyConfigured,
                        compact = compact,
                        onSave = onSaveTmdbKey,
                        onSaveSharedKey = onSaveSharedTmdbKey,
                        onChooseProfile = onChangeProfile,
                    )
                    Spacer(Modifier.height(cardGap))
                    CriticsSettingsCard(
                        configured = criticsKeyConfigured,
                        compact = compact,
                        onSave = onSaveCriticsKey,
                    )
                }
            }
            Spacer(Modifier.height(groupGap))

            SettingsGroup(
                title = stringResource(R.string.settings_group_devices),
                subtitle = stringResource(R.string.settings_group_devices_hint),
                compact = compact,
            ) {
                CastReceiveCard(
                    code = castReceiverCode,
                    listening = isCastReceiverOn,
                    onStart = onStartCastReceiver,
                    onStop = onStopCastReceiver,
                    compact = compact,
                )
            }
            Spacer(Modifier.height(groupGap))

            SettingsGroup(
                title = stringResource(R.string.settings_group_account),
                subtitle = stringResource(R.string.settings_group_account_hint),
                compact = compact,
            ) {
                Column {
                    DeviceLicenceCard(
                        onOpenPurchase = onOpenPurchase,
                        onRedeemKey = onRedeemLicense,
                        deviceId = deviceId,
                        license = license,
                        redemption = redemption,
                        compact = compact,
                    )
                    Spacer(Modifier.height(cardGap))
                    SettingsRow(
                        title =
                            stringResource(
                                R.string.settings_version,
                                BuildConfig.VERSION_NAME,
                            ),
                        body = stringResource(R.string.settings_legal),
                        compact = compact,
                    )
                }
            }
            Spacer(Modifier.height(groupGap))

            Text(
                text = stringResource(R.string.settings_language_title).uppercase(),
                color = BuroGold,
                fontSize = if (compact) 12.sp else 13.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.2.sp,
            )
            Text(
                text = stringResource(R.string.settings_language),
                color = BuroTextSecondary,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(10.dp))
            // One row per language rather than four squeezed side by side: "Português (Brasil)"
            // wrapped to two lines and the four buttons became a wall of broken words. A column
            // also leaves room to mark which one is in use.
            val currentLanguage = AppLocaleController.selectedLanguageTag(LocalContext.current)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppLocaleController.supportedLanguages.forEach { language ->
                    val selected = language.tag == currentLanguage
                    FocusSurface(
                        onClick = { onSelectLanguage(language.tag) },
                        selected = selected,
                        modifier = Modifier.fillMaxWidth().height(if (compact) 50.dp else 56.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = language.displayName,
                                color = if (selected) BuroAccent else BuroTextPrimary,
                                fontSize = 14.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.weight(1f),
                            )
                            if (selected) {
                                Text(
                                    text = stringResource(R.string.settings_language_current),
                                    color = BuroAccent,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(if (compact) 10.dp else 14.dp))
            FocusSurface(onClick = onChangeProfile, modifier = Modifier.fillMaxWidth().height(if (compact) 62.dp else 72.dp)) {
                Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.settings_active_profile), color = BuroTextSecondary, modifier = Modifier.weight(1f))
                    Text(
                        text = activeProfile?.name ?: stringResource(R.string.settings_choose_profile),
                        color = BuroAccent,
                        fontWeight = FontWeight.Bold,
                    )
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

/**
 * Receiving a title from a computer on the same network.
 *
 * The other half of "Enviar à tela", which until now only worked one way: the phone could find a
 * computer and send to it, but nothing here ever answered, so a computer searching found an empty
 * network and the person holding the phone had no code to type.
 *
 * Started from this card rather than automatically, and stopped when the screen closes — a phone
 * that listened in the background would need a foreground service and its permanent notification,
 * to receive something whose only effect is opening a page in an app that has to be brought
 * forward anyway.
 */
@Composable
private fun CastReceiveCard(
    code: String?,
    listening: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    compact: Boolean,
) {
    // Stopped when the card leaves composition, so the socket never outlives the screen showing
    // its code — including on a back press, which runs no explicit handler.
    DisposableEffect(Unit) { onDispose { onStop() } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(BuroSurface)
            .padding(if (compact) 16.dp else 24.dp),
    ) {
        Text(
            text = stringResource(R.string.cast_receive_title),
            color = BuroTextPrimary,
            fontSize = if (compact) 17.sp else 19.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(if (compact) 4.dp else 6.dp))
        Text(
            text = stringResource(R.string.cast_receive_hint),
            color = BuroTextSecondary,
            fontSize = if (compact) 13.sp else 14.sp,
        )
        Spacer(Modifier.height(if (compact) 10.dp else 14.dp))

        if (listening && code != null) {
            Text(
                text = stringResource(R.string.cast_receive_code, code),
                color = BuroGold,
                fontSize = if (compact) 26.sp else 30.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.cast_receive_waiting),
                color = BuroTextSecondary,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(if (compact) 10.dp else 14.dp))
            BuroButton(onClick = onStop, style = BuroButtonStyle.Ghost) {
                Text(stringResource(R.string.cast_receive_stop))
            }
        } else {
            // Listening asked for and refused: a bind can fail on a network that forbids it, and
            // saying so beats a button that looks pressed and does nothing.
            if (listening) {
                Text(
                    text = stringResource(R.string.cast_receive_failed),
                    color = BuroTextSecondary,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(8.dp))
            }
            BuroButton(onClick = onStart, style = BuroButtonStyle.Ghost) {
                Icon(Icons.Default.Cast, contentDescription = null)
                Text(stringResource(R.string.cast_receive_action))
            }
        }
    }
}

@Composable
private fun TmdbSettingsCard(
    profileId: String?,
    profileName: String?,
    configured: Boolean,
    sharedConfigured: Boolean,
    compact: Boolean,
    onSave: (String) -> Unit,
    onSaveSharedKey: (String) -> Unit,
    onChooseProfile: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(BuroSurface)
                .padding(if (compact) 16.dp else 22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.tmdb_settings_title),
                    color = BuroTextPrimary,
                    fontSize = if (compact) 17.sp else 19.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text =
                        stringResource(
                            if (configured) {
                                R.string.tmdb_settings_configured
                            } else {
                                R.string.tmdb_settings_not_configured
                            },
                        ),
                    color = if (configured) BuroAccent else BuroTextSecondary,
                    fontSize = 12.sp,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        // Getting a key is free and quick, but it runs across four pages of somebody else's site
        // and asks questions a viewer has no answer to. Reported as the key being impossible to
        // obtain, when what was happening is that people stopped at the form.
        var showGuide by remember { mutableStateOf(false) }
        FocusSurface(
            onClick = { showGuide = true },
            shape = RoundedCornerShape(50),
            contentAlignment = Alignment.Center,
            modifier = Modifier.height(42.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Icon(
                    Icons.Default.HelpOutline,
                    contentDescription = null,
                    tint = BuroGold,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.tmdb_guide_button),
                    color = BuroGold,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        if (showGuide) {
            TmdbKeyGuideSheet(onDismiss = { showGuide = false })
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.tmdb_settings_body),
            color = BuroTextSecondary,
            fontSize = 13.sp,
            lineHeight = 19.sp,
        )
        Spacer(Modifier.height(12.dp))

        // One field, not two.
        //
        // There were two: a household key and a per-profile one. They take the same TMDb key and
        // most people need exactly one, so the card asked the same question twice and the answer
        // to "where do I paste it?" was genuinely unclear. The household key is the one that
        // works for everybody, so that is the one on show.
        //
        // The per-profile key is still reachable below, because it has a real use — TMDb
        // rate-limits per key, so a heavy viewer may want their own quota — but it is an answer
        // to a question almost nobody asks, and it now sits behind a disclosure rather than in
        // front of everyone.
        MetadataKeyField(
            label = stringResource(R.string.tmdb_settings_shared_label),
            hint = stringResource(R.string.tmdb_settings_shared_hint),
            configured = sharedConfigured,
            compact = compact,
            onSave = onSaveSharedKey,
        )

        if (profileId != null) {
            Spacer(Modifier.height(12.dp))
            var showProfileKey by remember(profileId) { mutableStateOf(configured) }
            FocusSurface(
                onClick = { showProfileKey = !showProfileKey },
                modifier = Modifier.fillMaxWidth().height(if (compact) 44.dp else 48.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.tmdb_settings_advanced),
                        color = BuroTextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (showProfileKey) "\u2212" else "+",
                        color = BuroTextSecondary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
            if (showProfileKey) {
                Spacer(Modifier.height(12.dp))
                MetadataKeyField(
                    label =
                        stringResource(
                            R.string.tmdb_settings_profile_label,
                            profileName.orEmpty(),
                        ),
                    hint = stringResource(R.string.tmdb_settings_profile_hint),
                    configured = configured,
                    compact = compact,
                    onSave = onSave,
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.tmdb_settings_attribution),
            color = BuroTextSecondary.copy(alpha = 0.72f),
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun LicenseUiState.statusLabel(): String =
    when (this) {
        is LicenseUiState.Allowed -> {
            val days = daysRemaining
            when {
                offline -> stringResource(R.string.license_status_offline)
                days == null -> stringResource(R.string.license_status_active)
                isTrial ->
                    pluralStringResource(
                        R.plurals.license_status_trial_days,
                        days.toInt(),
                        days,
                    )
                else ->
                    pluralStringResource(
                        R.plurals.license_status_active_days,
                        days.toInt(),
                        days,
                    )
            }
        }
        is LicenseUiState.Blocked -> stringResource(R.string.license_state_unavailable)
        is LicenseUiState.Checking,
        LicenseUiState.NotChecked,
        -> stringResource(R.string.license_checking)
    }

/**
 * Device licence card.
 *
 * Mirrors the activation flow users know from other IPTV players — the app shows a code, the user
 * enters it on the portal to pay — with one deliberate difference recorded in ADR-004: the code is
 * derived from a Keystore EC key and an installation UUID, **not** from the MAC address.
 *
 * MAC would be the familiar choice, and it is the wrong one here. Android randomises the Wi-Fi MAC
 * per network from Android 10, so a licence bound to it would break when the user changes network,
 * and apps cannot read the hardware MAC at all since Android 6. A key-derived code is stable and
 * cannot be typed into someone else's device to clone a paid licence.
 *
 * The card links only to the public presentation page. Checkout and the final activation remain
 * server-authoritative; the app never receives card data or trusts a browser-side payment result.
 */
@Composable
private fun DeviceLicenceCard(
    onOpenPurchase: (String) -> Unit,
    onRedeemKey: (String) -> Unit,
    deviceId: String?,
    license: LicenseUiState,
    /** What the last key attempt did. Without this the card redeemed in complete silence. */
    redemption: RedemptionUi,
    compact: Boolean,
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    var activationKey by remember { mutableStateOf("") }
    val portal = stringResource(R.string.license_portal)
    val portalLanguage = stringResource(R.string.license_portal_language)

    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(2_000)
            copied = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(BuroSurface)
            .border(1.dp, BuroGold.copy(alpha = 0.28f), RoundedCornerShape(20.dp))
            .padding(if (compact) 16.dp else 22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.license_title),
                color = BuroTextPrimary,
                fontSize = if (compact) 17.sp else 19.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(BuroGold.copy(alpha = 0.16f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = license.statusLabel(),
                    color = BuroGold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.license_device_code),
            color = BuroTextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(6.dp))
        // Monospace and widely letter-spaced: this code gets read aloud and retyped on another
        // device, so glyph-by-glyph legibility matters more than compactness.
        Text(
            text = deviceId ?: stringResource(R.string.license_generating),
            color = BuroGold,
            fontSize = if (compact) 22.sp else 27.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 2.sp,
        )

        if (deviceId != null) {
            Spacer(Modifier.height(12.dp))
            FocusSurface(
                onClick = {
                    clipboard.setText(AnnotatedString(deviceId))
                    copied = true
                },
                modifier = Modifier.height(if (compact) 44.dp else 50.dp),
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 18.dp).fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(
                            if (copied) R.string.license_copied else R.string.license_copy,
                        ),
                        color = if (copied) BuroGold else BuroTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        if (deviceId != null) {
            Spacer(Modifier.height(18.dp))
            // Typing a 12-character code on a TV remote is painful, so the QR lets the user open
            // the activation page already filled in from their phone.
            ActivationQr(
                url = "https://$portal?code=$deviceId&lang=$portalLanguage",
                compact = compact,
            )
        }

        if (deviceId != null) {
            Spacer(Modifier.height(14.dp))
            // The QR is for somebody holding a second device. On the phone itself the page is one
            // tap away, and until now there was no way to reach it without retyping the address.
            FocusSurface(
                onClick = { onOpenPurchase(deviceId) },
                modifier = Modifier.fillMaxWidth().height(if (compact) 48.dp else 54.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.license_gate_purchase),
                    color = BuroGold,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(10.dp))
            // Redeeming a key that was handed out. The activation screen has always had this; the
            // settings card did not, so anybody already inside the app had nowhere to type it.
            OutlinedTextField(
                value = activationKey,
                onValueChange = { activationKey = it.take(64) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { androidx.compose.material3.Text(stringResource(R.string.license_gate_key_label)) },
                placeholder = { androidx.compose.material3.Text(stringResource(R.string.license_gate_key_hint)) },
                colors = BuroFieldColors,
            )
            Spacer(Modifier.height(8.dp))
            FocusSurface(
                onClick = {
                    onRedeemKey(activationKey)
                    // The field is *not* cleared here any more. It used to be wiped the instant the
                    // button was pressed, so a key that failed had to be typed again from scratch,
                    // with no message saying what had gone wrong. It clears on success below.
                },
                enabled = activationKey.isNotBlank() && redemption != RedemptionUi.Working,
                modifier = Modifier.fillMaxWidth().height(if (compact) 46.dp else 52.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text =
                        stringResource(
                            if (redemption == RedemptionUi.Working) {
                                R.string.license_gate_working
                            } else {
                                R.string.license_gate_redeem
                            },
                        ),
                    color = BuroTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            // What happened. This card previously said nothing at all — it called redeem, wiped the
            // field and left the user staring at an unchanged screen, which is exactly how a key
            // that was never even sent looked identical to one that was refused.
            when (redemption) {
                RedemptionUi.Idle, RedemptionUi.Working -> Unit

                is RedemptionUi.Activated -> {
                    LaunchedEffect(redemption) { activationKey = "" }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text =
                            redemption.daysRemaining
                                ?.let { days ->
                                    pluralStringResource(
                                        R.plurals.license_redeem_activated_days,
                                        days.toInt(),
                                        days.toInt(),
                                    )
                                }
                                ?: stringResource(R.string.license_redeem_activated),
                        color = BuroGold,
                        fontSize = 13.sp,
                    )
                }

                is RedemptionUi.Failed -> {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text =
                            stringResource(
                                when (redemption.reason) {
                                    RedeemFailure.UNKNOWN_KEY -> R.string.license_gate_key_unknown
                                    RedeemFailure.DEVICE_CODE_NOT_KEY ->
                                        R.string.license_gate_key_is_device_code
                                    RedeemFailure.ALREADY_USED -> R.string.license_gate_key_in_use
                                    RedeemFailure.EXPIRED -> R.string.license_gate_key_expired
                                    RedeemFailure.UNREACHABLE -> R.string.license_gate_key_offline
                                    RedeemFailure.NOT_REGISTERED ->
                                        R.string.license_gate_key_not_registered
                                    RedeemFailure.REFUSED -> R.string.license_gate_redeem_failed
                                },
                            ),
                        color = BuroDanger,
                        fontSize = 13.sp,
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.license_how_title),
            color = BuroTextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        listOf(
            stringResource(R.string.license_step_1, portal),
            stringResource(R.string.license_step_2),
            stringResource(R.string.license_step_3),
        ).forEach { step ->
            Text(
                text = step,
                color = BuroTextSecondary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.license_privacy),
            color = BuroTextSecondary.copy(alpha = 0.75f),
            fontSize = 11.sp,
            lineHeight = 16.sp,
        )
    }
}

/**
 * Activation QR.
 *
 * Drawn straight onto a Canvas from the bit matrix rather than rasterised to a Bitmap: at this size
 * a bitmap would either be blurry or waste memory, and the matrix is a few hundred booleans.
 *
 * A quiet zone of four modules is mandatory — without it many phone cameras will not lock on, which
 * looks like a broken code rather than a missing margin.
 */
@Composable
private fun ActivationQr(
    url: String,
    compact: Boolean,
) {
    val matrix = remember(url) { QrCode.encode(url) } ?: return
    val side = if (compact) 132.dp else 156.dp

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(side)
                .clip(RoundedCornerShape(10.dp))
                // White background, not the app's dark surface: readers expect dark modules on a
                // light field and many fail on an inverted code.
                .background(Color.White)
                .padding(8.dp),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val quiet = 4
                val modules = matrix.size + quiet * 2
                val cell = size.minDimension / modules
                for (y in 0 until matrix.size) {
                    for (x in 0 until matrix.size) {
                        if (!matrix[x, y]) continue
                        drawRect(
                            color = Color.Black,
                            topLeft = Offset((x + quiet) * cell, (y + quiet) * cell),
                            size = Size(cell, cell),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.license_qr_hint),
                color = BuroTextSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }
    }
}

/**
 * A transfer rate as a person reads it.
 *
 * Binary units, because that is what storage and every other download indicator on the device uses;
 * showing decimal megabytes here would disagree with the file size the system reports afterwards.
 */
internal fun formatTransferRate(bytesPerSecond: Long): String {
    val kb = 1024.0
    val mb = kb * 1024.0
    return when {
        bytesPerSecond >= mb -> "%.1f MB/s".format(bytesPerSecond / mb)
        bytesPerSecond >= kb -> "%.0f kB/s".format(bytesPerSecond / kb)
        else -> "$bytesPerSecond B/s"
    }
}

/**
 * One row of a watch list: the title, how far into it the viewer is, and whether it is finished.
 *
 * [completed] is null for Continue watching, where every entry is by definition unfinished and
 * saying so on each row would be noise.
 */
internal data class WatchlistRow(
    val channel: ChannelUi,
    val progress: Float,
    val completed: Boolean?,
)

/**
 * Continue watching and History, which are the same screen with different contents.
 *
 * Both were previously sections inside Favourites. They are destinations of their own here because
 * that is where the desktop puts them, and because "what was I half-way through" is a question
 * people arrive with rather than stumble upon.
 */
/**
 * Searching the imported catalogue.
 *
 * The destination was already in the navigation and answered with the "coming soon" card, so there
 * was no way to look anything up by name at all — on a catalogue of tens of thousands of items that
 * is the difference between a library and a list you scroll.
 *
 * Only what is already imported is searched: no external index, no request to the provider. It
 * works with no connection, and the query never leaves the device.
 *
 * "Nothing matched" and "say what you are looking for" are told apart deliberately. Showing the
 * first before anyone has typed reads as a search that is already broken.
 */
@Composable
private fun SearchContent(
    results: List<ChannelUi>,
    isSearching: Boolean,
    onQueryChange: (String) -> Unit,
    onOpenChannel: (ChannelUi) -> Unit,
    onBack: () -> Unit,
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
            ScreenHeader(
                title = stringResource(R.string.buro_nav_search),
                subtitle = stringResource(R.string.search_subtitle),
                onBack = onBack,
            )
            Spacer(Modifier.height(if (compactPortrait) 14.dp else 20.dp))

            // Held locally and published on a delay. Typing has to feel immediate, while the
            // database should see one query for a typed title rather than one per character — on a
            // catalogue of forty thousand rows that difference is felt.
            var typed by remember { mutableStateOf("") }
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it.take(80) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { androidx.compose.material3.Text(stringResource(R.string.search_label)) },
                placeholder = {
                    androidx.compose.material3.Text(stringResource(R.string.search_hint))
                },
                colors = BuroFieldColors,
            )
            LaunchedEffect(typed) {
                delay(SEARCH_DEBOUNCE_MILLIS)
                onQueryChange(typed)
            }

            Spacer(Modifier.height(14.dp))

            when {
                typed.isBlank() ->
                    BuroEmptyState(
                        title = stringResource(R.string.search_idle),
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )

                isSearching && results.isEmpty() ->
                    BuroEmptyState(
                        title = stringResource(R.string.search_working),
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )

                results.isEmpty() ->
                    BuroEmptyState(
                        title = stringResource(R.string.search_empty),
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )

                else ->
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(results, key = { channel -> channel.id }) { channel ->
                            MyBuroMediaRow(
                                channel = channel,
                                progress = null,
                                // What the result *is*, not which folder it came from.
                                //
                                // This printed categoryName, which the search maps from the
                                // catalogue's category *id* — so every row was subtitled with a
                                // raw UUID. The kind is the useful distinction anyway: it answers
                                // "is this the film or the series" for a name that is both.
                                status = stringResource(channel.contentType.favoriteKindLabel()),
                                onOpen = { onOpenChannel(channel) },
                            )
                        }
                    }
            }
        }
    }
}

/**
 * How long the search field settles before the catalogue is queried.
 *
 * Long enough that typing a title runs one query rather than one per letter; short enough that the
 * results read as a response to typing rather than to stopping.
 */
private const val SEARCH_DEBOUNCE_MILLIS = 300L

@Composable
private fun WatchlistContent(
    title: String,
    subtitle: String,
    rows: List<WatchlistRow>,
    emptyMessage: String,
    onOpenChannel: (ChannelUi) -> Unit,
    onBack: () -> Unit,
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
            Spacer(Modifier.height(if (compactPortrait) 16.dp else 22.dp))

            // The same filter Favourites carries. Films and series land in one list here too, and
            // with a full list finding the series you left half-watched meant scrolling past every
            // film. Offered only for the kinds present, so it never leads to an empty screen.
            var kind by remember { mutableStateOf<CatalogContentType?>(null) }
            val presentKinds =
                remember(rows) {
                    listOf(
                        CatalogContentType.MOVIE,
                        CatalogContentType.SERIES,
                        CatalogContentType.LIVE,
                    ).filter { candidate -> rows.any { it.channel.contentType == candidate } }
                }
            val shown =
                remember(rows, kind) {
                    kind?.let { selected ->
                        rows.filter { it.channel.contentType == selected }
                    } ?: rows
                }
            if (presentKinds.size > 1) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item(key = "watchlist-kind-all") {
                        FavoriteKindChip(
                            label = stringResource(R.string.catalogue_filter_all),
                            selected = kind == null,
                            onClick = { kind = null },
                        )
                    }
                    items(presentKinds, key = { it.name }) { candidate ->
                        FavoriteKindChip(
                            label = stringResource(candidate.favoriteKindLabel()),
                            selected = kind == candidate,
                            onClick = { kind = candidate },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            if (rows.isEmpty()) {
                BuroEmptyState(
                    title = emptyMessage,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(shown, key = { row -> row.channel.id }) { row ->
                        MyBuroMediaRow(
                            channel = row.channel,
                            progress = row.progress,
                            status =
                                when (row.completed) {
                                    null -> stringResource(R.string.my_buro_continue_status)
                                    true -> stringResource(R.string.my_buro_watched)
                                    false -> stringResource(R.string.my_buro_in_progress)
                                },
                            onOpen = { onOpenChannel(row.channel) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * How long this device is licensed for, and the way to extend it.
 *
 * Deliberately always present rather than appearing near expiry: a countdown someone has watched go
 * from two years to sixty days is information; the same number arriving unannounced at three days is
 * a scare. Pressing it opens Settings, which owns the device code, the QR and the activation field.
 *
 * The urgent colour is reserved for the last stretch. Used earlier it would train the eye to ignore
 * it by the time it matters.
 */
@Composable
private fun LicenseChip(
    license: LicenseUiState,
    onClick: () -> Unit,
) {
    val colors = BuroTheme.colors
    val daysLeft = (license as? LicenseUiState.Allowed)?.daysRemaining
    val urgent = license is LicenseUiState.Blocked || (daysLeft != null && daysLeft <= LICENSE_URGENT_DAYS)

    FocusSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(58.dp),
        backgroundColor = colors.surface,
        focusedBackgroundColor = colors.elevated,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Default.WorkspacePremium,
                contentDescription = null,
                tint = if (urgent) colors.error else BuroGold,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = license.statusLabel(),
                    color = if (urgent) colors.error else BuroTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.license_chip_action),
                    color = BuroTextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Where the countdown starts reading as urgent rather than as background information. */
private const val LICENSE_URGENT_DAYS = 7L

/**
 * One TMDB key: its current state, a field to replace it, and a way to remove it.
 *
 * Shared by the household key and the per-profile key, because they differ only in what they are
 * called and where they are stored — duplicating the field would let the two drift apart in how
 * they mask input or report success.
 */
@Composable
private fun MetadataKeyField(
    label: String,
    hint: String,
    configured: Boolean,
    compact: Boolean,
    onSave: (String) -> Unit,
) {
    var apiKey by remember(label) { mutableStateOf("") }
    if (apiKey.isNotEmpty()) {
        // The key is a secret. Masking protects against shoulder-surfing; FLAG_SECURE also keeps it
        // out of screenshots and the recents thumbnail while the field holds text.
        SecureActivityWindowEffect()
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                color = BuroTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                // Neutral wording: this field is reused for the OMDb key, and the TMDb phrasing
                // "configured for this profile" claimed the OMDb key belonged to a profile — which
                // is both wrong and confusing, since it is stored for the whole device.
                text =
                    stringResource(
                        if (configured) R.string.metadata_key_saved else R.string.metadata_key_absent,
                    ),
                color = if (configured) BuroAccent else BuroTextSecondary,
                fontSize = 11.sp,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(text = hint, color = BuroTextSecondary, fontSize = 12.sp, lineHeight = 17.sp)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it.take(512) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            placeholder = { androidx.compose.material3.Text(stringResource(R.string.tmdb_settings_key_hint)) },
            colors = BuroFieldColors,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FocusSurface(
                onClick = {
                    onSave(apiKey)
                    apiKey = ""
                },
                enabled = apiKey.isNotBlank(),
                modifier = Modifier.weight(1f).height(if (compact) 44.dp else 50.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.tmdb_settings_save),
                    color = BuroTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (configured) {
                FocusSurface(
                    onClick = {
                        onSave("")
                        apiKey = ""
                    },
                    modifier = Modifier.weight(1f).height(if (compact) 44.dp else 50.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.tmdb_settings_clear),
                        color = BuroTextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/**
 * How long the refresh button keeps spinning after it is pressed.
 *
 * Long enough to be seen and to read as "it is working"; short enough not to pretend the app is
 * still busy once it plainly is not.
 */
private const val REFRESH_FEEDBACK_MILLIS = 900L

/**
 * The year out of a provider's release date.
 *
 * Providers write the field as `YYYY-MM-DD`, and the leading four characters are the only part any
 * of them agree on — some send just the year, some a full date, some a localised string. Reading
 * the prefix is what the metadata lookup already does, so a share and a TMDb query cannot disagree
 * about which year a title is from.
 *
 * A value that does not start with a plausible year yields null rather than a wrong number: the
 * year narrows a shared link to the right remake, so a guess is worse than its absence.
 */
internal fun yearFromReleaseDate(releaseDate: String): Int? =
    releaseDate.trim().take(4).toIntOrNull()?.takeIf { it in PLAUSIBLE_RELEASE_YEARS }

private val PLAUSIBLE_RELEASE_YEARS = 1_888..2_100

/**
 * The key a reminder for this title would be stored under.
 *
 * Built the same way the view model builds it when marking, so the button reads its own state back
 * rather than being told: the two must agree, and deriving both from [ContentIdentity] is what
 * keeps them agreeing when the title or the year changes as the full record loads.
 */
private fun reminderKeyOf(
    kind: ContentKind,
    title: String,
    releaseDate: String?,
): String {
    val year = releaseDate?.let(::yearFromReleaseDate)
        ?: if (kind == ContentKind.MOVIE) ContentIdentity.yearFromTitle(title) else null
    return ContentIdentity.of(kind, title, year).key
}

/**
 * The OMDb key, which is what fills the critics' row on a title's page.
 *
 * Its own card rather than a second field on the TMDb one: they are different services with
 * different keys and different limits, and somebody who has pasted one should not have to wonder
 * whether they have pasted the other.
 *
 * Optional throughout. Without a key the app behaves exactly as it did — the TMDb score and the
 * audience count still show — and the Tomatometer, IMDb and Metascore meters are simply absent
 * rather than empty.
 */
@Composable
private fun CriticsSettingsCard(
    configured: Boolean,
    compact: Boolean,
    onSave: (String) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(BuroSurface)
                .padding(if (compact) 16.dp else 22.dp),
    ) {
        Text(
            text = stringResource(R.string.critics_settings_title),
            color = BuroTextPrimary,
            fontSize = if (compact) 17.sp else 19.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text =
                stringResource(
                    if (configured) {
                        R.string.critics_settings_configured
                    } else {
                        R.string.critics_settings_absent
                    },
                ),
            color = if (configured) BuroAccent else BuroTextSecondary,
            fontSize = if (compact) 13.sp else 14.sp,
        )
        Spacer(Modifier.height(if (compact) 10.dp else 14.dp))
        MetadataKeyField(
            label = stringResource(R.string.critics_settings_field),
            hint = stringResource(R.string.critics_settings_hint),
            configured = configured,
            compact = compact,
            onSave = onSave,
        )
    }
}

/**
 * The Netflix/Prime/… row above the filter bar on Filmes and Séries.
 *
 * Unlike [PlatformShortcuts], which only ever finds a service when a provider's own category name
 * happens to match one, this reads TMDb's real directory ([DiscoveredProvider]) — so it draws
 * regardless of how the IPTV source organises its categories, which for most playlists is by
 * genre and never names a service at all.
 *
 * Sized for a normal window rather than reading `compactPortrait` from its caller: this is passed
 * as a plain lambda into [ChannelsContent]'s `headerAction` slot, which invokes it from inside its
 * own `BoxWithConstraints` — a size read at the call site here would be measuring the wrong box.
 */
@Composable
private fun ProviderShortcutRow(
    providers: List<com.lucasserafin94.iptvburo.data.discovery.DiscoveredProvider>,
    onOpenProvider: (com.lucasserafin94.iptvburo.data.discovery.DiscoveredProvider) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        providers.forEach { provider ->
            val identity =
                providerIdentityFor(provider.label)?.copy(logoUrl = provider.logoUrl)
                    ?: ProviderIdentity(
                        monogram = provider.label.take(1).uppercase(),
                        label = provider.label,
                        colour = BuroSurfaceRaised,
                        logoUrl = provider.logoUrl,
                    )
            FocusSurface(
                onClick = { onOpenProvider(provider) },
                modifier = Modifier.height(56.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxHeight().padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProviderMark(provider = identity, size = 30.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = identity.label,
                        color = BuroTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * A row of streaming services, each opening that service's own list.
 *
 * The categories already hold this — "Series | Netflix", "Series | Max" — but they sit among
 * dozens of genre folders, so finding one meant scrolling past everything else. This lifts them to
 * the top as marks, which is how somebody looking for "what is on Netflix" actually thinks.
 *
 * Scrolls sideways rather than wrapping: the number of services depends on the playlist, and a
 * wrapping row would push the catalogue itself off the screen on a provider that carries many.
 */
@Composable
private fun PlatformShortcuts(
    platforms: List<Pair<ProviderIdentity, CategoryUi>>,
    compact: Boolean,
    onOpenCategory: (CategoryUi) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        platforms.forEach { (identity, category) ->
            FocusSurface(
                onClick = { onOpenCategory(category) },
                modifier = Modifier.height(if (compact) 54.dp else 60.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxHeight().padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProviderMark(provider = identity, size = if (compact) 28.dp else 32.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = identity.label,
                        color = BuroTextPrimary,
                        fontSize = if (compact) 13.sp else 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

package com.lucasserafin94.iptvburo.desktop.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.LocalPlatformContext
import com.lucasserafin94.iptvburo.desktop.DesktopAppState
import com.lucasserafin94.iptvburo.desktop.DesktopDestination
import com.lucasserafin94.iptvburo.desktop.DownloadEntry
import com.lucasserafin94.iptvburo.desktop.DownloadState
import com.lucasserafin94.iptvburo.desktop.ImportStatus
import com.lucasserafin94.iptvburo.desktop.OnboardingStep
import com.lucasserafin94.iptvburo.desktop.ShareLinkOutcome
import com.lucasserafin94.iptvburo.desktop.XtreamStatus
import com.lucasserafin94.iptvburo.desktop.data.PlatformContextHolder
import com.lucasserafin94.iptvburo.desktop.download.DISPLAY_LOCALE
import com.lucasserafin94.iptvburo.desktop.download.DownloadRateTracker
import com.lucasserafin94.iptvburo.desktop.download.formatDuration
import com.lucasserafin94.iptvburo.desktop.download.formatRate
import com.lucasserafin94.iptvburo.desktop.license.LicenseStatus
import com.lucasserafin94.iptvburo.desktop.model.DesktopSourceKind
import com.lucasserafin94.iptvburo.desktop.model.DesktopSourceSummary
import com.lucasserafin94.iptvburo.desktop.model.XtreamPlaybackTarget
import com.lucasserafin94.iptvburo.desktop.playback.MultiviewTile
import com.lucasserafin94.iptvburo.desktop.data.contentIdentity
import com.lucasserafin94.iptvburo.desktop.model.PlaybackReadiness
import com.lucasserafin94.iptvburo.desktop.model.playbackReadiness
import com.lucasserafin94.iptvburo.desktop.platform.DesktopPlatformCapabilities
import com.lucasserafin94.iptvburo.desktop.platform.ExternalOpenResult
import com.lucasserafin94.iptvburo.desktop.platform.chooseImageFile
import com.lucasserafin94.iptvburo.desktop.platform.chooseLocalPlaylist
import com.lucasserafin94.iptvburo.desktop.platform.chooseM3uFile
import com.lucasserafin94.iptvburo.desktop.platform.openStreamingOfferExternally
import com.lucasserafin94.iptvburo.desktop.platform.openUriExternally
import com.lucasserafin94.iptvburo.desktop.playback.DesktopPlaybackRequest
import com.lucasserafin94.iptvburo.desktop.playback.DesktopPlayerOverlay
import com.lucasserafin94.iptvburo.desktop.playback.MultiviewOverlay
import com.lucasserafin94.iptvburo.desktop.security.XtreamLoginInput
import com.lucasserafin94.iptvburo.desktop.ui.BURO_AVATARS
import com.lucasserafin94.iptvburo.desktop.ui.BuroColors
import com.lucasserafin94.iptvburo.desktop.ui.BuroDesktopTheme
import com.lucasserafin94.iptvburo.desktop.ui.BuroInteractiveRow
import com.lucasserafin94.iptvburo.desktop.ui.BuroInteractiveSurface
import com.lucasserafin94.iptvburo.desktop.ui.BuroMark
import com.lucasserafin94.iptvburo.desktop.ui.BuroProfileAvatar
import com.lucasserafin94.iptvburo.desktop.ui.BuroRadius
import com.lucasserafin94.iptvburo.desktop.ui.BuroRemoteArtwork
import com.lucasserafin94.iptvburo.desktop.ui.BuroSegmentedControl
import com.lucasserafin94.iptvburo.desktop.ui.BuroSpacing
import com.lucasserafin94.iptvburo.desktop.ui.DesktopStrings
import com.lucasserafin94.iptvburo.desktop.ui.LocalDesktopStrings
import com.lucasserafin94.iptvburo.desktop.ui.LocalProviderLogos
import com.lucasserafin94.iptvburo.desktop.ui.strings
import com.lucasserafin94.iptvburo.xtream.XtreamEpgProgram
import com.lucasserafin94.iptvburo.desktop.update.DESKTOP_VERSION
import com.lucasserafin94.iptvburo.desktop.update.DesktopRelease
import com.lucasserafin94.iptvburo.desktop.update.GitHubReleaseUpdater
import com.lucasserafin94.iptvburo.desktop.update.UpdateCheckResult
import com.lucasserafin94.iptvburo.desktop.user.DesktopLanguage
import com.lucasserafin94.iptvburo.desktop.user.DesktopProfile
import com.lucasserafin94.iptvburo.domain.model.CacheFillProgress
import com.lucasserafin94.iptvburo.domain.model.CacheFillState
import com.lucasserafin94.iptvburo.domain.model.Category
import com.lucasserafin94.iptvburo.domain.model.Channel
import com.lucasserafin94.iptvburo.domain.model.ContentIdentity
import com.lucasserafin94.iptvburo.domain.model.ContentKind
import com.lucasserafin94.iptvburo.domain.model.ExternalTitleDetails
import com.lucasserafin94.iptvburo.domain.model.NotificationCentre
import com.lucasserafin94.iptvburo.domain.model.OfferType
import com.lucasserafin94.iptvburo.domain.model.ProviderDeepLinks
import com.lucasserafin94.iptvburo.domain.model.StreamingOffer
import com.lucasserafin94.iptvburo.metadata.TmdbStreamingCatalogue
import com.lucasserafin94.iptvburo.xtream.XtreamContentType
import java.awt.Frame
import kotlinx.coroutines.launch

/** Where TMDb issues the personal API key this app asks for. */
/** Where an existing account manages its keys. The guide links here for the second visit onward. */
internal const val TMDB_API_SETTINGS_URL = "https://www.themoviedb.org/settings/api"

@Composable
fun DesktopApp(
    appState: DesktopAppState,
    ownerWindow: Frame?,
    isFullScreen: Boolean,
    onToggleFullScreen: () -> Unit,
    isCompact: Boolean,
    onToggleCompact: () -> Unit,
    onExitForUpdate: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val capabilities = DesktopPlatformCapabilities.current
    val visibleDestination =
        when (appState.destination) {
            DesktopDestination.DOWNLOADS ->
                if (capabilities.offlineSupported) appState.destination else DesktopDestination.HOME
            DesktopDestination.MUSIC ->
                if (capabilities.audioSupported) appState.destination else DesktopDestination.HOME
            else -> appState.destination
        }
    var pendingExternalChannel by remember { mutableStateOf<Channel?>(null) }
    // Coil's context, published for the cache to build its own requests with.
    //
    // Set here rather than inside SingletonImageLoader.setSafe, and the difference is the whole
    // reason the cache stored nothing: setSafe takes a *lazy* factory, so its body does not run
    // until something asks for the loader. Warming an image asked for the context first, found it
    // null, and returned — so a fill of twenty-nine thousand posters "completed" without a single
    // byte being fetched, and the directory was never even created. Composition happens before any
    // of that, and this is the same context Coil itself is handed.
    PlatformContextHolder.context = LocalPlatformContext.current

    var externalOpenResult by remember { mutableStateOf<ExternalOpenResult?>(null) }
    var activePlayback by remember { mutableStateOf<DesktopPlaybackRequest?>(null) }
    var showXtreamLogin by remember { mutableStateOf(false) }
    var showRemoteSource by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }

    /** The connection test, opened from the header beside the refresh button. */
    var diagnosticsOpen by remember { mutableStateOf(false) }

    /** Whether the TMDb key walkthrough is showing, over the settings window. */
    var tmdbGuideOpen by remember { mutableStateOf(false) }
    var omdbGuideOpen by remember { mutableStateOf(false) }
    // Not persisted: collapsing is something a user does to see more of one screen, not a standing
    // preference, and a sidebar that stayed hidden across restarts would look like a missing menu.
    var sidebarCollapsed by remember { mutableStateOf(false) }
    var showProfileGate by remember { mutableStateOf(false) }
    // The purchase details, opened from the countdown chip. The same screen the gate shows, reached
    // before being locked out rather than only afterwards.
    var showLicenseDetails by remember { mutableStateOf(false) }
    // Which profile the editor is open for, or null when it is closed. Held as an id rather than as
    // the profile so an edit that renames it does not leave a stale copy on screen.
    var editingProfile by remember { mutableStateOf<String?>(null) }
    // Keyed on the text so a language change rebuilds it: the failure message reaches the user.
    val updaterText = strings.shareStrings.screens
    val releaseUpdater = remember(updaterText) { GitHubReleaseUpdater(text = updaterText) }
    var updateBusy by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf<String?>(null) }
    var updateProgress by remember { mutableStateOf(0f) }
    // Bytes per second and seconds left, measured the same way content downloads are.
    var updateBytesPerSecond by remember { mutableStateOf(0L) }
    var updateSecondsRemaining by remember { mutableStateOf<Long?>(null) }
    // The same smoothing the download list uses: a raw rate over one buffer swings wildly, and a
    // running average over the whole transfer keeps reporting a healthy speed after a stall.
    val updateRateTracker = remember { DownloadRateTracker() }
    var updateRelease by remember { mutableStateOf<DesktopRelease?>(null) }
    var updateReadyToRestart by remember { mutableStateOf(false) }
    // Owned here rather than inside the setup screen, which is replaced by the connecting and
    // failure screens: state held there was discarded, so a wrong password emptied the whole form
    // and hid which field was actually wrong.
    val setupDraft = remember { AccountSetupDraft() }
    // Asked for from the playlist form, which is where somebody who cannot fill in three fields
    // actually gives up. The profile gate behind it has its own.
    var setupShowingDeviceCode by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            pendingExternalChannel = null
            appState.clearSensitiveData()
        }
    }

    // Once per launch, not once per screen. `Unit` rather than a changing key because a licence is a
    // property of the machine and the launch, and re-asking on every navigation would be a network
    // round trip the customer pays for in latency.
    LaunchedEffect(Unit) {
        appState.checkLicense()
        // A connection a reseller set up for this machine, if there is one. Almost every launch
        // finds nothing, so this is silent; it runs after the licence check because the two share
        // the same server and there is no reason to open two connections at once.
        appState.collectProvisionedSource()
    }

    // The services' own marks, fetched once and read by every badge below through a
    // CompositionLocal. Off the main thread, failure-silent, and never blocking: the selectors work
    // with monograms until it lands.
    LaunchedEffect(appState.metadataApiKey, appState.streamingRegion) {
        appState.ensureProviderLogos()
    }

    BuroDesktopTheme {
        CompositionLocalProvider(
            LocalDesktopStrings provides DesktopStrings.of(appState.language),
            LocalProviderLogos provides appState.providerLogos,
        ) {
        // The licence check, inside the theme and the strings so the blocking screen is styled and
        // translated, and before everything else so a blocked app never composes a catalogue it is
        // not allowed to show.
        //
        // The first check runs off the main thread and the app waits on it. That wait is deliberate:
        // showing the catalogue first and blocking a moment later would mean an expired licence gets
        // a usable app for as long as the network takes.
        val licenseStatus = appState.licenseStatus
        if (licenseStatus != null && !licenseStatus.allowsUse) {
            LicenseGate(
                status = licenseStatus,
                client = appState.licenseClient,
                onRechecked = appState::onLicenseRechecked,
                onKeyRedeemed = appState::rememberActivationKey,
                activationKey = appState.activationKey,
                onQuit = onExitForUpdate,
                languageTag = appState.language.tag,
                backdropPosters = appState.backdropPosters,
            )
            return@CompositionLocalProvider
        }

        val text = strings
        fun checkAndDownloadUpdate() {
            if (updateBusy) return
            scope.launch {
                updateBusy = true
                updateMessage = text.checkingUpdate
                updateProgress = 0f
                updateBytesPerSecond = 0L
                updateSecondsRemaining = null
                // A previous attempt's samples would otherwise be averaged into this one's first
                // reading, which is how a resumed download reports a speed it is not achieving.
                updateRateTracker.forget(UPDATE_RATE_KEY)
                updateRelease = null
                updateReadyToRestart = false
                when (val result = releaseUpdater.check()) {
                    UpdateCheckResult.UpToDate -> {
                        updateMessage = text.upToDate
                        updateBusy = false
                    }
                    is UpdateCheckResult.Failed -> {
                        updateMessage = result.userMessage
                        updateBusy = false
                    }
                    is UpdateCheckResult.Available -> {
                        updateMessage = null
                        updateRelease = result.release
                        // downloadAndLaunch performs I/O on Dispatchers.IO. Marshal progress back
                        // through the composition scope instead of mutating snapshot state there.
                        releaseUpdater
                            .downloadAndLaunch(result.release) { fraction, bytesRead ->
                                // Measured on the I/O thread, where the bytes actually land, so the
                                // interval is the transfer's own and not the composition's.
                                val rate = updateRateTracker.observe(UPDATE_RATE_KEY, bytesRead)
                                val total = result.release.sizeBytes
                                val remaining =
                                    if (total > 0L && rate > 0L && bytesRead in 0 until total) {
                                        (total - bytesRead) / rate
                                    } else {
                                        null
                                    }
                                scope.launch {
                                    updateProgress = fraction
                                    updateBytesPerSecond = rate
                                    updateSecondsRemaining = remaining
                                }
                            }.onSuccess {
                                // The installer waits for this process to leave. Keep the choice in
                                // the user's hands instead of making the window disappear abruptly.
                                updateProgress = 1f
                                updateReadyToRestart = true
                                updateBusy = false
                            }.onFailure {
                                updateRelease = null
                                updateMessage = text.updateFailed
                                updateBusy = false
                            }
                    }
                }
            }
        }

        Surface(
            modifier =
                Modifier
                    .fillMaxSize()
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                        // Full screen outside the player, which previously only the player offered.
                        //
                        // F11 toggles, as it does in a browser. Esc only leaves, never enters, so
                        // whoever turns it on always has a key that gets them back — full screen
                        // removes the title bar along with the frame, and without this the way out
                        // would be Alt+F4.
                        if (event.key == Key.F11) {
                            onToggleFullScreen()
                            return@onPreviewKeyEvent true
                        }
                        if (event.key == Key.Escape && isFullScreen) {
                            onToggleFullScreen()
                            return@onPreviewKeyEvent true
                        }

                        // Down closes a title opened from elsewhere and returns there, which for a
                        // card opened from Descobrir means going back to the deck. Here rather
                        // than on that screen because by the time there is a page to close, that
                        // screen is no longer the one showing.
                        //
                        // Only swallowed when it actually closed something: a down press with no
                        // title open has to keep scrolling the list under it.
                        if (event.key == Key.DirectionDown && appState.closeOpenedTitle()) {
                            return@onPreviewKeyEvent true
                        }

                        if (
                            !appState.isXtreamSelected ||
                            !event.isCtrlPressed
                        ) {
                            return@onPreviewKeyEvent false
                        }
                        val destination =
                            when (event.key) {
                                Key.One, Key.NumPad1 -> XtreamContentType.LIVE
                                Key.Two, Key.NumPad2 -> XtreamContentType.MOVIE
                                Key.Three, Key.NumPad3 -> XtreamContentType.SERIES
                                else -> return@onPreviewKeyEvent false
                            }
                        scope.launch { appState.openCatalog(destination) }
                        true
                    },
            color = BuroColors.Canvas,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // Hidden while the profile gate is up on a first run.
                    //
                    // Every item in it — Filmes, Series, Guia — leads nowhere until somebody is
                    // watching, and the gate centres inside the Row beside it, so the first screen
                    // of a new install was a full menu the viewer cannot use with the one question
                    // that matters pushed off-centre next to it.
                    if (appState.activeProfile != null) {
                        SourceSidebar(
                            sources = appState.sourceSummaries,
                            selectedSourceId = appState.selectedSourceId,
                            onSourceSelected = appState::selectSource,
                            destination = visibleDestination,
                            catalogType = appState.xtreamContentType,
                            downloads = if (capabilities.offlineSupported) appState.downloadEntries else emptyList(),
                            onCancelDownload = appState::cancelDownload,
                            onHome = appState::openHome,
                            onSearch = appState::openSearch,
                            onProfiles = { showProfileGate = true },
                            onSettings = { settingsOpen = true },
                            activeProfileName = appState.activeProfile?.name,
                            onMovies = {
                                scope.launch { appState.openCatalog(XtreamContentType.MOVIE) }
                            },
                            onSeries = {
                                scope.launch { appState.openCatalog(XtreamContentType.SERIES) }
                            },
                            onLive = {
                                scope.launch { appState.openCatalog(XtreamContentType.LIVE) }
                            },
                            onGuide = { scope.launch { appState.openGuide() } },
                            onFavorites = { scope.launch { appState.setFavoritesOnly(true) } },
                            onReminders = appState::openReminders,
                            onDiscover = appState::openDiscovery,
                            onConnectXtream = { showXtreamLogin = true },
                            mergeSources = appState.mergeAllSources,
                            // Applied on the spot rather than stored for the next launch: the switch
                            // used to change a preference and nothing visible, which reads as a dead
                            // button.
                            onToggleMergeSources = { enabled ->
                                scope.launch { appState.applyMergeAllSources(enabled) }
                            },
                            onRenameSource = appState::renameSavedSource,
                            onRemoveSource = appState::removeSavedSource,
                            onImportM3u = {
                                chooseLocalPlaylist(ownerWindow)?.let { path ->
                                    scope.launch { appState.importLocalPlaylist(path) }
                                }
                            },
                            onAddRemoteSource = { showRemoteSource = true },
                            onContinueWatching = appState::openContinueWatching,
                            onHistory = appState::openHistory,
                            onDownloads = appState::openDownloads,
                            hasOffline = capabilities.offlineSupported,
                            // Shown whenever music is released, not only once a playlist is loaded.
                            //
                            // Hiding it until a library existed meant a profile without an M3U had no
                            // way to learn the feature was there, and nothing said why: the section was
                            // simply absent, indistinguishable from not being built. The workspace
                            // explains what to add instead.
                            hasMusic = capabilities.audioSupported,
                            onMusic = appState::openMusic,
                            hasSubscriptions = appState.streamingDiscoveryCapability.isVisible,
                            onSubscriptions = appState::openSubscriptions,
                            collapsed = sidebarCollapsed,
                            onToggleCollapsed = { sidebarCollapsed = !sidebarCollapsed },
                        )
                    }
                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        TopBar(
                            channelCount = appState.selectedSourceItemCount,
                            sourceCount = appState.sourceSummaries.size,
                            activeProfile = appState.activeProfile,
                            // Read through photoRevision so writing or clearing a photo repaints
                            // the chip; the file path itself never changes.
                            activeProfilePhoto =
                                appState.photoRevision.let { appState.photoFor(appState.activeProfileId) },
                            language = appState.language,
                            // Opening the picker must not clear the active profile. It used to
                            // call selectProfile(null), which left no way back if you opened it
                            // by accident.
                            onChangeProfile = { showProfileGate = true },
                            onSelectLanguage = appState::updateLanguage,
                            updateBusy = updateBusy,
                            updateMessage = updateMessage,
                            sessionActive = appState.isXtreamSelected,
                            onEndSession = appState::disconnectXtream,
                            catalogRefreshing = appState.xtreamStatus is XtreamStatus.LoadingCatalog,
                            onRefreshCatalog = { scope.launch { appState.refreshCatalog() } },
                            onOpenDiagnostics = { diagnosticsOpen = true },
                            metadataApiKey = appState.metadataApiKey,
                            onMetadataApiKeyChange = appState::updateMetadataApiKey,
                            streamingRegion = appState.streamingRegion,
                            onSelectRegion = appState::changeStreamingRegion,
                            uses24HourClock = appState.uses24HourClock,
                            cacheProgress = appState.cacheProgress,
                            onPauseCacheFill = appState::pauseCacheFill,
                            onResumeCacheFill = appState::resumeCacheFill,
                            onCancelCacheFill = appState::cancelCacheFill,
                            notifications = appState.notifications,
                            onNotificationsOpened = appState::markNotificationsRead,
                            onDismissNotification = appState::removeNotification,
                            onClearNotifications = appState::clearNotifications,
                            licenseStatus = appState.licenseStatus,
                            onOpenPurchase = { showLicenseDetails = true },
                            onUpdate = ::checkAndDownloadUpdate,
                        )

                        HorizontalDivider(color = BuroColors.BorderSoft)
                        // The content screen must be weighted. A Column measures an unweighted
                        // child with unbounded height, so the Home's LazyColumn believed it had
                        // infinite space: it laid every rail out at once, overflowed past the
                        // window and never became scrollable.
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        // Music is checked before the source, because it is fed by its own playlist
                        // and does not need the video provider to be connected at all.
                        if (visibleDestination == DesktopDestination.MUSIC) {
                            MusicWorkspace(
                                appState = appState,
                                onPlay = { request -> activePlayback = request },
                                ownerWindow = ownerWindow,
                            )
                        } else if (visibleDestination == DesktopDestination.SUBSCRIPTIONS) {
                            // Like Music, checked before the source: it answers where a title can be
                            // watched elsewhere, which does not need the video provider connected.
                            SubscriptionsWorkspace(
                                capability = appState.streamingDiscoveryCapability,
                                ranking = appState.streamingOffers,
                                // The shelves arrive as services-with-titles; the screen groups by
                                // provider itself, and every title carries its own offers, so this
                                // flattens to the shape it already expects. A title on two services
                                // appears twice here and is regrouped onto both shelves.
                                titles =
                                    appState.streamingShelves
                                        .also { shelves ->
                                            println("[streaming] screen sees ${shelves.size} shelves")
                                        }.flatMap { shelf ->
                                        shelf.titles.map { title ->
                                            ExternalTitleDetails(
                                                title = title,
                                                offers =
                                                    listOf(
                                                        StreamingOffer(
                                                            provider = shelf.provider,
                                                            type = OfferType.SUBSCRIPTION,
                                                            launchTarget =
                                                                ProviderDeepLinks.bestTargetFor(
                                                                    shelf.provider.id,
                                                                    title.title,
                                                                ),
                                                        ),
                                                    ),
                                            )
                                        }
                                    },
                                // Opening a shelf title fetches where it can actually be watched;
                                // the shelf itself only knows which service it came from.
                                onSelectTitle = { details -> appState.openStreamingTitle(details.title) },
                                kind = appState.streamingKind,
                                onSelectKind = appState::selectStreamingKind,
                                loadFailed = appState.streamingLoadFailed,
                                keyRejected = appState.streamingKeyRejected,
                                onRetry = { appState.loadStreamingShelves(force = true) },
                                page = appState.streamingPage,
                                // A title is open when one has been selected, which is not the same
                                // as it having somewhere to watch — an upcoming film has nowhere by
                                // definition, and inferring one from the other kept its page shut.
                                titleOpen = appState.selectedStreamingTitle != null,
                                // The screen knows the provider; the state needs the shelf, because
                                // only that carries the TMDb id the wider list is fetched by.
                                onSeeMore = { provider ->
                                    appState.streamingShelves
                                        .firstOrNull { shelf -> shelf.provider.id == provider.id }
                                        ?.let(appState::openServiceCatalogue)
                                },
                                expandedService = appState.expandedService,
                                expandedLoading = appState.expandedServiceLoading,
                                onCloseExpanded = appState::closeServiceCatalogue,
                                onOpenTrailerExternally = { id -> appState.openPublicTrailer(id) },
                                // An upcoming film has no catalogue row, so it is named the only
                                // way it can be: by what it is. The same identity the film will
                                // have once it actually turns up in a playlist.
                                hasReminderFor = { title, year ->
                                    appState.hasReminder(
                                        ContentIdentity.of(ContentKind.MOVIE, title, year),
                                    )
                                },
                                onToggleReminder = { title, year, poster ->
                                    appState.toggleReminder(
                                        identity = ContentIdentity.of(ContentKind.MOVIE, title, year),
                                        // TMDb's own title, which is already the clean one.
                                        title = title,
                                        year = year,
                                        artworkUrl = poster,
                                    )
                                },
                                // Clearing the ranking is what returns the screen to its shelves,
                                // which is exactly what opening the area already does.
                                onBackToShelves = { appState.openSubscriptions() },
                                onOpenOffer = { ranked ->
                                    // The user's own copy plays here rather than being handed to the
                                    // system: it is their file, and there is no external destination
                                    // on a USER_LIBRARY offer to hand over anyway.
                                    if (ranked.offer.type == OfferType.USER_LIBRARY) {
                                        // To the title's own page, not straight into playback: the
                                        // user came here to find out about the film, and the
                                        // synopsis and cast are on that page.
                                        appState.openInLibrary()
                                    } else {
                                        ranked.offer.launchTarget?.let(::openStreamingOfferExternally)
                                    }
                                },
                            )
                        } else if (!appState.hasSelectedSource) {
                            EmptyLibrary(
                                onImport = {
                                    chooseLocalPlaylist(ownerWindow)?.let { path ->
                                        scope.launch { appState.importLocalPlaylist(path) }
                                    }
                                },
                                onConnectXtream = { showXtreamLogin = true },
                            )
                        } else if (visibleDestination == DesktopDestination.CONTINUE) {
                            ContinueWatchingWorkspace(
                                entries = appState.continueWatchingEntries,
                                onResume = { entry ->
                                    activePlayback =
                                        appState.prepareXtreamPlayback(
                                            entry.playbackTarget(),
                                            entry.item.name,
                                            entry.progress.positionMs,
                                        )
                                },
                                onRestart = { entry ->
                                    activePlayback =
                                        appState.prepareXtreamPlayback(
                                            entry.playbackTarget(),
                                            entry.item.name,
                                            0L,
                                        )
                                },
                                onForget = appState::forgetProgress,
                            )
                        } else if (visibleDestination == DesktopDestination.SEARCH) {
                            SearchWorkspace(
                                query = appState.globalSearchQuery,
                                results = appState.globalSearchResults,
                                onQueryChange = appState::updateGlobalSearch,
                                onOpenItem = { item ->
                                    // The whole route, not just the selection.
                                    //
                                    // This called `selectDailyItem` alone, which sets the title but
                                    // leaves the destination on SEARCH — and the loaders that fetch a
                                    // film's details live in XtreamWorkspace, which only composes for
                                    // CATALOG. So the page opened, showed "Carregando ficha do
                                    // filme…", and nothing ever ran: no request in flight, no error,
                                    // no way back except leaving the screen.
                                    //
                                    // `openTitle` is the same route Lembretes and "já está na sua
                                    // lista" take, and it exists because this is the third caller to
                                    // get the sequence wrong by hand.
                                    appState.openTitle(item)
                                },
                                text = strings,
                            )
                        } else if (visibleDestination == DesktopDestination.HISTORY) {
                            // Covers rather than the Continue watching rows. History answers "have
                            // I seen this?", which is recognition, and a wall of posters answers it
                            // at a glance where a list has to be read line by line.
                            HistoryGallery(
                                entries = appState.historyEntries,
                                onResume = { entry ->
                                    activePlayback =
                                        appState.prepareXtreamPlayback(
                                            entry.playbackTarget(),
                                            entry.item.name,
                                            entry.progress.positionMs,
                                        )
                                },
                                onForget = appState::forgetHistoryEntry,
                                onClearAll = appState::clearHistory,
                            )
                        } else if (visibleDestination == DesktopDestination.GUIDE) {
                            LiveGuideScreen(
                                channels = appState.guideChannels,
                                focusedChannelId = appState.guideFocusedChannelId,
                                scheduleFor = appState::guideScheduleFor,
                                isLoading = appState::guideIsLoading,
                                onFocusChannel = { channelId ->
                                    scope.launch { appState.focusGuideChannel(channelId) }
                                },
                                onWatch = { channel ->
                                    // Plays where it stands rather than opening a details page.
                                    //
                                    // From a guide the viewer has already read what is on and
                                    // pressed Watch; sending them to a page about the channel to
                                    // press play again is a step that answers nothing.
                                    //
                                    // Built the same way every other play builds it, so the
                                    // progress identity and the buffer decision are the shared
                                    // ones rather than a second opinion.
                                    activePlayback =
                                        appState.prepareXtreamPlayback(
                                            XtreamPlaybackTarget.CatalogItem(
                                                providerId = channel.providerId,
                                                contentType = channel.contentType,
                                                containerExtension = channel.containerExtension,
                                                contentKey = channel.contentIdentity().key,
                                            ),
                                            channel.name,
                                        )
                                },
                                previewRequestFor = { channel ->
                                    // Built through the shared route, so the preview asks for a
                                    // stream exactly as every other screen does — and the guide
                                    // itself never holds a credentialed address.
                                    appState
                                        .prepareXtreamPlayback(
                                            XtreamPlaybackTarget.CatalogItem(
                                                providerId = channel.providerId,
                                                contentType = channel.contentType,
                                                containerExtension = channel.containerExtension,
                                                contentKey = channel.contentIdentity().key,
                                            ),
                                            channel.name,
                                        )
                                        ?.let { request ->
                                            MultiviewTile(
                                                providerId = channel.providerId,
                                                request = request,
                                                title = channel.name,
                                            )
                                        }
                                },
                                strings = text,
                                nowEpochSeconds = rememberGuideClock(),
                            )
                        } else if (visibleDestination == DesktopDestination.DISCOVER) {
                            DiscoveryScreen(
                                deck = appState.discoveryDeck,
                                loading = appState.discoveryLoading,
                                synopsisFor = appState::discoverySynopsis,
                                genresFor = appState::discoveryGenres,
                                onDecide = appState::decideDiscovery,
                                // The same route every other "open this title" takes, so the details
                                // loaders actually run — see openTitle.
                                onOpenDetails = appState::openTitle,
                                onAnother = appState::loadDiscoveryDeck,
                                // The banner's own lookup, so a title with a trailer there has one
                                // here rather than the two screens disagreeing about the same film.
                                trailerFor = appState::heroTrailerFor,
                                // The synopsis as well as the trailer: the card needs something
                                // to read, and nothing else on this screen fetches one.
                                onNeedTrailer = { item ->
                                    appState.loadHeroTrailer(item)
                                    appState.loadHeroSynopsis(item)
                                },
                                onTrailerFailed = appState::rememberHeroTrailerFailure,
                                onPassOver = appState::passOverDiscovery,
                                soundOn = appState.bannerTrailerSound,
                                onToggleSound = appState::toggleBannerTrailerSound,
                            )
                        } else if (visibleDestination == DesktopDestination.REMINDERS) {
                            RemindersGallery(
                                reminders = appState.reminders,
                                // Resolved once per entry: an upcoming film has no catalogue row,
                                // and the row is what makes an entry openable at all.
                                onOpen = { reminder ->
                                    appState.catalogItemForReminder(reminder)?.let { item ->
                                        { appState.openReminder(item) }
                                    }
                                },
                                onRemove = appState::removeReminder,
                                announced = appState.remindersAnnounced,
                                onAnnouncedChange = appState::announceReminders,
                                hour = appState.reminderHour,
                                onHourChange = appState::chooseReminderHour,
                                notice = appState.reminderNotice,
                                onDismissNotice = appState::dismissReminderNotice,
                            )
                        } else if (visibleDestination == DesktopDestination.DOWNLOADS) {
                            DownloadsWorkspace(
                                entries = appState.downloadEntries,
                                onPlay = { key ->
                                    activePlayback = appState.prepareOfflinePlayback(key)
                                    if (activePlayback == null) {
                                        // The file was removed outside the app; refresh so the row
                                        // disappears instead of failing again on the next click.
                                        appState.refreshDownloadStates()
                                    }
                                },
                                onCancel = appState::cancelDownload,
                                onDelete = appState::deleteDownload,
                            )
                        } else if (appState.cacheChoicePending && appState.isXtreamSelected &&
                            visibleDestination == DesktopDestination.HOME &&
                            appState.activeProfile != null
                        ) {
                            // Offered here rather than in the setup steps, because the estimate
                            // needs a loaded catalogue to be worth anything: "about 4 GB" is only
                            // useful once the app knows how large this library actually is.
                            //
                            // Shown once. Choosing — including choosing zero — answers the question,
                            // and a panel that returned after being answered would be nagging.
                            //
                            // And only once somebody is watching. The profile gate draws over this
                            // same area on a first run, so without the active-profile check the
                            // very first screen of a new install was this panel and "Quem esta
                            // assistindo?" painted through each other, both unreadable.
                            CacheFirstRunPanel(appState = appState)
                        } else if (appState.isXtreamSelected && visibleDestination == DesktopDestination.HOME) {
                            XtreamDailyHome(
                                appState = appState,
                                onOpenExternal = { pending ->
                                    activePlayback =
                                        appState.prepareXtreamPlayback(
                                            pending.target,
                                            pending.displayName,
                                            pending.startPositionMillis,
                                        )
                                    if (activePlayback == null) externalOpenResult = ExternalOpenResult.Failed
                                },
                            )
                        } else if (appState.isXtreamSelected) {
                            XtreamWorkspace(
                                appState = appState,
                                onOpenExternal = { pending ->
                                    activePlayback =
                                        appState.prepareXtreamPlayback(
                                            pending.target,
                                            pending.displayName,
                                            pending.startPositionMillis,
                                        )
                                    if (activePlayback == null) externalOpenResult = ExternalOpenResult.Failed
                                },
                            )
                        } else {
                            CatalogWorkspace(
                                appState = appState,
                                onOpenExternal = { pendingExternalChannel = it },
                            )
                        }
                        }
                    }
                }

                ImportStatusBanner(
                    status = appState.importStatus,
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(24.dp),
                    onDismiss = appState::dismissStatus,
                )
                XtreamStatusBanner(
                    status = appState.xtreamStatus,
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 24.dp, bottom = 90.dp),
                    onDismiss = appState::dismissStatus,
                )

                // Above the catalogue and below the player: a locked category must be answered
                // before its titles appear, but a film already playing is not interrupted by it.
                appState.pendingPinCategory?.let { category ->
                    Box(
                        modifier = Modifier.fillMaxSize().background(BuroColors.Scrim),
                        contentAlignment = Alignment.Center,
                    ) {
                        ParentalUnlockDialog(
                            categoryName = category.name,
                            onSubmit = { pin -> appState.submitPendingPin(pin) },
                            onDismiss = appState::dismissPinPrompt,
                        )
                    }
                }

                // Opening multiview closes the single player.
                //
                // They are alternatives, not layers. Left running, the single player drew on top —
                // so pressing "watch together" while a channel was playing changed nothing visible,
                // and the feature looked broken. It also freed a decoder before four more start,
                // which on a laptop is the difference between four streams and a stutter.
                LaunchedEffect(appState.multiviewOpen) {
                    if (appState.multiviewOpen) activePlayback = null
                }

                // Above the catalogue, below the single-title player: opening one channel full
                // screen from inside the grid should replace it rather than draw underneath.
                if (capabilities.multiviewSupported && appState.multiviewOpen) {
                    MultiviewOverlay(
                        tiles = appState.multiviewTiles(),
                        onClose = {
                            // Closing the overlay must also restore the window frame. Previously the
                            // catalogue was left trapped in borderless full screen after Multiview.
                            if (isFullScreen) onToggleFullScreen()
                            appState.closeMultiview()
                        },
                        onRemoveTile = appState::toggleMultiviewChannel,
                        queuedCount = appState.multiviewChannelIds.size,
                        // The same full-screen switch the single-title player has. Four matches at
                        // once is exactly when somebody wants the whole monitor.
                        isFullScreen = isFullScreen,
                        onToggleFullScreen = onToggleFullScreen,
                    )
                }

                activePlayback?.let { request ->
                    DesktopPlayerOverlay(
                        onFindAlternative = appState::alternativePlayback,
                        request = request,
                        onCheckpoint = { positionMs, durationMs ->
                            appState.checkpointPlayback(request, positionMs, durationMs)
                        },
                        onEnded = { durationMs -> appState.completePlayback(request, durationMs) },
                        isFullScreen = isFullScreen,
                        onToggleFullScreen = onToggleFullScreen,
                        isCompact = isCompact,
                        onToggleCompact = onToggleCompact,
                        isFavorite = appState.playingIsFavorite,
                        onToggleFavorite = appState::togglePlayingFavorite,
                        subtitleStyle = appState.subtitleStyle,
                        audioOutput = appState.audioOutput,
                        onSelectAudioOutput = { mode, positionMillis ->
                            // The engine has to be rebuilt, because VLC constructs its audio chain
                            // with the rest of the pipeline. Restarting it from zero would throw the
                            // viewer back to the opening titles, so the request is rebuilt carrying
                            // the position playback had reached.
                            //
                            // That position comes from the overlay's live snapshot rather than from
                            // the stored checkpoint: the checkpoint is written on disposal, which
                            // has not happened yet at this point, and is otherwise only refreshed
                            // every twelve seconds. Falling back to the stored value when the engine
                            // has not reported a position yet, which is the only case it is better.
                            val resumeAt =
                                positionMillis.takeIf { it > 0L } ?: appState.lastCheckpointMillis(request)
                            appState.selectAudioOutput(mode)
                            activePlayback = request.copy(startPositionMillis = resumeAt)
                        },
                        onClose = {
                            if (isFullScreen) onToggleFullScreen()
                            activePlayback = null
                        },
                    )
                }
                // Language comes before the profile gate: the profile screen is itself translated,
                // so asking "who's watching?" in the wrong language defeats the point.
                updateRelease?.let { release ->
                    UpdateOverlay(
                        release = release,
                        progress = updateProgress,
                        bytesPerSecond = updateBytesPerSecond,
                        secondsRemaining = updateSecondsRemaining,
                        readyToRestart = updateReadyToRestart,
                        onRestart = onExitForUpdate,
                        onDismiss = { updateRelease = null },
                    )
                }
                // The splash sits over everything while the session is restored and the lists are
                // fetched, so the first thing the user sees is complete rather than half-filled.
                // Above the onboarding branch: a returning user must not glimpse a setup screen
                // between launching the app and their catalogue appearing.
                //
                // Not while the language has yet to be chosen: on a true first run there is no
                // session to restore, so the splash would flash by in Portuguese — a language the
                // user has not picked — in front of the screen that asks them to pick one.
                if (appState.isStarting && !appState.needsLanguageSetup) {
                    SplashScreen(
                        message = appState.startupMessage.ifBlank { text.loadingCatalog },
                        progress = appState.startupProgress,
                        detail = appState.startupDetail,
                        beatAtMillis = appState.startupBeatAt,
                        backdropPosters = appState.backdropPosters,
                        isFirstRun = appState.isFirstStartup,
                    )
                }
                // First run is an ordered sequence — language, copyright, account, connection — and
                // exactly one step is on screen at a time.
                val step = appState.onboarding
                if (appState.needsLanguageSetup) {
                    LanguageSetupGate(
                        current = appState.language,
                        onSelect = { option ->
                            appState.updateLanguage(option)
                            appState.advanceOnboardingAfterLanguage()
                        },
                    )
                } else if (step is OnboardingStep.Terms) {
                    TermsGate(text = text, onAccept = appState::acceptTerms)
                } else if (step is OnboardingStep.Account) {
                    AccountSetupGate(
                        text = text,
                        // The revision is read so this recomposes when a list is renamed or
                        // removed: savedSources reads the store directly and holds no state of
                        // its own, so without it the row would keep its old name on screen.
                        savedSources = appState.savedSourcesRevision.let { appState.savedSources() },
                        onRenameSaved = appState::renameSavedSource,
                        onRemoveSaved = appState::removeSavedSource,
                        mergeSources = appState.mergeAllSources,
                        onToggleMergeSources = appState::updateMergeAllSources,
                        deviceCode = appState.deviceCode,
                        onShowDeviceCode = { setupShowingDeviceCode = true },
                        // Hoisted above the step branch so a failed connection returns to a form
                        // that still holds everything the user typed.
                        draft = setupDraft,
                        photo = appState.pendingProfilePhoto,
                        onPickPhoto = {
                            chooseImageFile(ownerWindow, text.avatarChoosePhotoTitle)
                                ?.let(appState::choosePendingPhoto)
                        },
                        onClearPhoto = { appState.choosePendingPhoto(null) },
                        // Dismissable only once a profile exists: during first-run setup there is
                        // nothing behind to go back to, so the step stays modal.
                        onDismiss =
                            if (appState.activeProfile != null) {
                                appState::cancelAddingProfile
                            } else {
                                null
                            },
                        onPickMusicPlaylist = { chooseM3uFile(ownerWindow, text.musicPlaylistTitle) },
                        onCreate = {
                            profileName, avatarIndex, listLabel, server, username, password,
                            musicPlaylist, metadataKey,
                            ->
                            scope.launch {
                                appState.completeSetup(
                                    profileName = profileName,
                                    avatarIndex = avatarIndex,
                                    listLabel = listLabel,
                                    input =
                                        XtreamLoginInput(
                                            server.toCharArray(),
                                            username.toCharArray(),
                                            password.toCharArray(),
                                        ),
                                    musicPlaylistPath = musicPlaylist,
                                    metadataKey = metadataKey,
                                )
                            }
                        },
                        onUseSaved = { profileName, avatarIndex, sourceId, musicPlaylist, metadataKey ->
                            scope.launch {
                                appState.completeSetupWithSavedSource(
                                    profileName = profileName,
                                    avatarIndex = avatarIndex,
                                    sourceId = sourceId,
                                    musicPlaylistPath = musicPlaylist,
                                    metadataKey = metadataKey,
                                )
                            }
                        },
                    )
                } else if (step is OnboardingStep.Connecting) {
                    ConnectingGate(text = text)
                } else if (step is OnboardingStep.Failed) {
                    SetupFailedGate(
                        text = text,
                        message = step.message,
                        onRetry = appState::retrySetup,
                    )
                } else if (appState.activeProfile == null && appState.profiles.size == 1) {
                    // One profile is not a question worth asking.
                    //
                    // "Quem está assistindo?" earns its place in a household — it is how a Kids
                    // profile stays separate from an adult's — and is pure ceremony for somebody
                    // who lives alone: a screen with one face on it, in the way of the app, every
                    // single launch. With two or more the gate appears as before, and it is always
                    // reachable from the sidebar for anyone who wants to switch.
                    val only = appState.profiles.first().id
                    LaunchedEffect(only) { appState.selectProfileAndRefresh(only) }
                } else if (appState.activeProfile == null || showProfileGate) {
                    DesktopProfileGate(
                        profiles = appState.profiles,
                        // Read through photoRevision so a replaced photo repaints the tiles.
                        photoFor = { id -> appState.photoRevision.let { appState.photoFor(id) } },
                        onSelect = { profileId ->
                            showProfileGate = false
                            scope.launch { appState.selectProfileAndRefresh(profileId) }
                        },
                        onAddProfile = appState::startAddingProfile,
                        onEditProfile = { profileId -> editingProfile = profileId },
                        onDelete = appState::deleteProfile,
                        onReset = appState::resetEverything,
                        deviceCode = appState.deviceCode,
                        // Dismissable only when a profile is already active. On first launch there
                        // is nothing to fall back to, so the gate stays modal.
                        onDismiss =
                            if (appState.activeProfile != null) {
                                { showProfileGate = false }
                            } else {
                                null
                            },
                    )
                }

                // Drawn outside the step chain so it sits over the form rather than replacing
                // it: somebody reading out their code is in the middle of filling those fields in.
                if (setupShowingDeviceCode) {
                    DeviceCodeDialog(appState.deviceCode) { setupShowingDeviceCode = false }
                }

                // Purchase details, opened from the countdown chip while the app still works. The
                // same content as the blocking screen — device code, QR, price — but reached by
                // choice rather than by being locked out.
                if (showLicenseDetails) {
                    appState.licenseStatus?.let { status ->
                        LicenseGate(
                            status = status,
                            client = appState.licenseClient,
                            onRechecked = { rechecked ->
                                appState.onLicenseRechecked(rechecked)
                                // Closed on a result that lets the app run: a customer who just paid
                                // should land back in the app, not on the screen that asked them to.
                                if (rechecked.allowsUse) showLicenseDetails = false
                            },
                            onKeyRedeemed = appState::rememberActivationKey,
                            activationKey = appState.activationKey,
                            onQuit = { showLicenseDetails = false },
                            languageTag = appState.language.tag,
                            backdropPosters = appState.backdropPosters,
                            // Opened by choice, from the countdown, while the app still works. There
                            // is always a way back. The blocking use above passes nothing, because
                            // closing it would reveal an app the customer may not use.
                            onDismiss = { showLicenseDetails = false },
                        )
                    }
                }

                // Editing a profile. Composed outside the gate's `else if` so it survives the gate
                // being dismissed, and keyed on the profile so a deleted one cannot leave a dialog
                // editing something that no longer exists.
                appState.profiles.firstOrNull { it.id == editingProfile }?.let { profile ->
                    ProfileEditorDialog(
                        profile = profile,
                        musicPath = profile.musicPlaylistPath?.let(java.nio.file.Path::of),
                        photo = appState.photoRevision.let { appState.photoFor(profile.id) },
                        // Null when the profile has no playlist of its own, which the dialog renders
                        // as "use whichever is connected" rather than as an empty field.
                        sourceLabel =
                            profile.sourceId?.let { id ->
                                appState.savedSources().firstOrNull { it.id == id }?.label
                            },
                        onSave = { name, isKids, avatarIndex ->
                            appState.updateProfile(profile.id, name, isKids, avatarIndex)
                            editingProfile = null
                        },
                        onChangeSource = {
                            // Leaves for the account screen, which owns credentials and connection.
                            editingProfile = null
                            appState.startEditingProfileSource(profile.id)
                        },
                        onChooseMusic = {
                            val chosen =
                                chooseM3uFile(ownerWindow, text.settingsText.profileMusicChoose)
                            if (chosen != null) {
                                scope.launch { appState.setMusicPlaylist(profile.id, chosen) }
                            }
                        },
                        onClearMusic = {
                            scope.launch { appState.setMusicPlaylist(profile.id, null) }
                        },
                        onPickPhoto = {
                            // Applied straight to the profile rather than staged: unlike creation,
                            // this profile already exists, so there is nothing to hold it against.
                            chooseImageFile(ownerWindow, text.avatarChoosePhotoTitle)
                                ?.let { chosen -> appState.setProfilePhoto(profile.id, chosen) }
                        },
                        onClearPhoto = { appState.setProfilePhoto(profile.id, null) },
                        onDismiss = { editingProfile = null },
                    )
                }

                // The longest wait in the app's life: the account has just been created and the
                // whole catalogue is being read for the first time. The small overlay used here
                // says "authenticating" over an empty library and explains none of that, so on a
                // first run the full preparation screen is shown instead.
                val preparingFirstCatalogue =
                    appState.isFirstStartup &&
                        !appState.isStarting &&
                        (
                            appState.xtreamStatus is XtreamStatus.Connecting ||
                                appState.xtreamStatus is XtreamStatus.LoadingCatalog
                        )
                if (preparingFirstCatalogue) {
                    // Names what is being fetched rather than saying "carregando" and nothing else.
                    //
                    // A catalogue of forty thousand titles takes long enough that a bare spinner
                    // reads as a hang; "Carregando filmes…" is the same wait with the app visibly
                    // working through something. The stage message wins when the loader has one,
                    // since it is more specific still.
                    val loadingWhat =
                        (appState.xtreamStatus as? XtreamStatus.LoadingCatalog)?.let { loading ->
                            when (loading.contentType) {
                                XtreamContentType.MOVIE -> text.movies
                                XtreamContentType.SERIES -> text.series
                                XtreamContentType.LIVE -> text.live
                            }
                        }
                    SplashScreen(
                        message =
                            appState.startupMessage.ifBlank {
                                loadingWhat?.let { what -> "${text.loadingCatalog.trimEnd('…', '.')} · $what" }
                                    ?: text.loadingCatalog
                            },
                        progress = appState.startupProgress,
                        detail = appState.startupDetail,
                        beatAtMillis = appState.startupBeatAt,
                        backdropPosters = appState.backdropPosters,
                        isFirstRun = true,
                    )
                }

                AnimatedVisibility(
                    // Not while starting: the splash already says this, and two panels stacked
                    // saying the same thing is what the user kept seeing over their catalogue.
                    visible =
                        !appState.isStarting &&
                            !preparingFirstCatalogue &&
                            (
                                appState.importStatus is ImportStatus.Loading ||
                                    appState.xtreamStatus is XtreamStatus.Connecting
                            ),
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    LoadingOverlay(
                        message =
                            if (appState.xtreamStatus is XtreamStatus.Connecting) {
                                text.authenticating
                            } else {
                                text.organizingPlaylist
                            },
                    )
                }
            }

            if (showRemoteSource) {
                RemoteSourceDialog(
                    onDismiss = { showRemoteSource = false },
                    onConnect = { url, user, password ->
                        scope.launch { appState.importRemotePlaylist(url, user, password) }
                    },
                )
            }
            if (showXtreamLogin) {
                XtreamLoginDialog(
                    onDismiss = { showXtreamLogin = false },
                    onConnect = { input, listLabel ->
                        scope.launch { appState.connectXtream(input, listLabel) }
                    },
                    onProtocolChosen = appState::useStalkerForNextConnection,
                )
            }

            if (diagnosticsOpen) {
                DiagnosticsDialog(
                    text = strings,
                    // Built per opening rather than held: it keeps nothing between runs, and a
                    // stored one would pin the repository it was built against after a sign-out.
                    runner = appState.newDiagnosticsRunner(),
                    onClose = { diagnosticsOpen = false },
                )
            }

            if (settingsOpen) {
                SettingsDialog(
                    appState = appState,
                    onDismiss = { settingsOpen = false },
                    updateBusy = updateBusy,
                    onUpdate = ::checkAndDownloadUpdate,
                    sessionActive = appState.isXtreamSelected,
                    onEndSession = appState::disconnectXtream,
                    catalogRefreshing = appState.xtreamStatus is XtreamStatus.LoadingCatalog,
                    onRefreshCatalog = { scope.launch { appState.refreshCatalog() } },
                    onOpenTmdbSettings = { openUriExternally(java.net.URI(TMDB_API_SETTINGS_URL)) },
                    onOpenTmdbGuide = { tmdbGuideOpen = true },
                    onOpenOmdbSite = { openUriExternally(java.net.URI(OMDB_API_KEY_URL)) },
                    onOpenAdultKeySite = {
                        openUriExternally(java.net.URI(ADULT_METADATA_KEY_URL))
                    },
                    onOpenOmdbGuide = { omdbGuideOpen = true },
                )
            }

            // Over the settings window rather than replacing it: the field being explained is
            // behind this, and sending the user back through the settings tree after reading six
            // steps would lose them the place they were about to paste into.
            if (tmdbGuideOpen) {
                TmdbKeyGuideDialog(
                    onDismiss = { tmdbGuideOpen = false },
                    onOpenSite = { url -> openUriExternally(java.net.URI(url)) },
                )
            }

            // Same arrangement for the critics' key, for the same reason.
            if (omdbGuideOpen) {
                OmdbKeyGuideDialog(
                    onDismiss = { omdbGuideOpen = false },
                    onOpenSite = { url -> openUriExternally(java.net.URI(url)) },
                )
            }

            pendingExternalChannel?.let { channel ->
                ExternalPlaybackDialog(
                    channel = channel,
                    onDismiss = { pendingExternalChannel = null },
                    onConfirm = {
                        activePlayback = appState.prepareLocalPlayback(channel)
                        if (activePlayback == null) externalOpenResult = ExternalOpenResult.Failed
                        pendingExternalChannel = null
                    },
                )
            }

            externalOpenResult
                ?.takeIf { it != ExternalOpenResult.Opened }
                ?.let { result ->
                    AlertDialog(
                        onDismissRequest = { externalOpenResult = null },
                        confirmButton = {
                            TextButton(onClick = { externalOpenResult = null }) {
                                Text(strings.understood)
                            }
                        },
                        title = { Text(strings.shareStrings.screens.externalOpenFailed) },
                        text = {
                            Text(
                                if (result == ExternalOpenResult.NotSupported) {
                                    strings.shareStrings.screens.externalNoDefaultApp
                                } else {
                                    strings.shareStrings.screens.externalRefused
                                },
                            )
                        },
                    )
                }

            // A shared link that this list cannot satisfy.
            //
            // Only the miss is reported. When the title *is* found the app has already navigated to
            // it, and a dialog confirming what is plainly on screen would be one click of noise
            // between the user and the film they were sent.
            (appState.shareLinkOutcome as? ShareLinkOutcome.NotInYourList)?.let { outcome ->
                AlertDialog(
                    onDismissRequest = { appState.clearShareLinkOutcome() },
                    confirmButton = {
                        TextButton(onClick = { appState.clearShareLinkOutcome() }) {
                            Text(strings.understood)
                        }
                    },
                    title = { Text(strings.shareStrings.shareNotFoundTitle) },
                    text = { Text("${outcome.title}\n\n${strings.shareStrings.shareNotFoundBody}") },
                )
            }
        }
        }
    }
}

@Composable
private fun SourceSidebar(
    sources: List<DesktopSourceSummary>,
    selectedSourceId: String?,
    onSourceSelected: (String) -> Unit,
    destination: DesktopDestination,
    catalogType: XtreamContentType,
    downloads: List<DownloadEntry>,
    onCancelDownload: (String) -> Unit,
    onHome: () -> Unit,
    onSearch: () -> Unit,
    /** Opens the profile picker, which is otherwise only reachable at launch. */
    onProfiles: () -> Unit,
    onSettings: () -> Unit,
    /** The active profile's name, shown on the item instead of a generic word. */
    activeProfileName: String?,
    onMovies: () -> Unit,
    onSeries: () -> Unit,
    onLive: () -> Unit,
    /** Opens the guide: channels one side, what is on them the other. */
    onGuide: () -> Unit = {},
    onFavorites: () -> Unit,
    onReminders: () -> Unit,
    onDiscover: () -> Unit,
    /** Connects an Xtream account. */
    onConnectXtream: () -> Unit,
    /**
     * Whether every configured list is browsed as one catalogue.
     *
     * Offered here because this is where somebody adds a second subscription — the profile form
     * is not, and a switch only there might as well not exist.
     */
    mergeSources: Boolean = false,
    onToggleMergeSources: (Boolean) -> Unit = {},
    /**
     * Renames and forgets a saved subscription, from beside the list itself.
     *
     * Both were reachable only from the profile form. Somebody looking at a list that stopped
     * answering is here, not there, and a row that says "not answering" with no way to remove it
     * reads as a fault in the app rather than a dead subscription.
     */
    onRenameSource: (sourceId: String, label: String) -> Unit = { _, _ -> },
    onRemoveSource: (sourceId: String) -> Unit = {},
    /** Imports an M3U playlist from a file. */
    onImportM3u: () -> Unit,
    onAddRemoteSource: () -> Unit,
    onContinueWatching: () -> Unit,
    onHistory: () -> Unit,
    onDownloads: () -> Unit,
    hasOffline: Boolean,
    hasMusic: Boolean,
    onMusic: () -> Unit,
    hasSubscriptions: Boolean,
    onSubscriptions: () -> Unit,
    collapsed: Boolean,
    onToggleCollapsed: () -> Unit,
) {
    val text = strings

    // The list waiting on a yes before it is forgotten.
    //
    // Confirmed rather than removed on the click: forgetting a subscription throws away its stored
    // password too, and the button sits inches from the one that opens the list.
    var removingSource by remember { mutableStateOf<DesktopSourceSummary?>(null) }

    /**
     * Whether the list of subscriptions is folded away.
     *
     * Open by default, which is what the sidebar has always done. Somebody with ten lists can fold
     * them; somebody with two is not made to unfold anything to see them.
     */
    var sourcesFolded by remember { mutableStateOf(false) }

    /** The dead lists waiting on a yes before they are all forgotten. */
    var removingOffline by remember { mutableStateOf<List<DesktopSourceSummary>>(emptyList()) }

    // Collapsed: a narrow strip carrying the name vertically and the control to bring it back. The
    // content beside it gets the width, which on a shelf of posters is another card and a half.
    if (collapsed) {
        CollapsedSidebar(onExpand = onToggleCollapsed)
        return
    }

    if (removingOffline.isNotEmpty()) {
        val doomedList = removingOffline
        AlertDialog(
            onDismissRequest = { removingOffline = emptyList() },
            confirmButton = {
                TextButton(
                    onClick = {
                        doomedList.forEach { source -> onRemoveSource(source.id) }
                        removingOffline = emptyList()
                    },
                ) {
                    Text(text.shareStrings.screens.setupRemoveList, color = BuroColors.Primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { removingOffline = emptyList() }) { Text(text.cancel) }
            },
            text = {
                // Named rather than counted: forgetting a list throws away its stored password, and
                // "seven lists" is not something somebody can check before agreeing to it.
                Text(
                    text.shareStrings.screens.setupRemoveListConfirm
                        .format(doomedList.joinToString(", ") { it.name }),
                )
            },
        )
    }

    val doomed = removingSource
    if (doomed != null) {
        AlertDialog(
            onDismissRequest = { removingSource = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemoveSource(doomed.id)
                        removingSource = null
                    },
                ) {
                    Text(text.shareStrings.screens.setupRemoveList, color = BuroColors.Primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { removingSource = null }) { Text(text.cancel) }
            },
            text = {
                Text(text.shareStrings.screens.setupRemoveListConfirm.format(doomed.name))
            },
        )
    }

    Column(
        modifier =
            Modifier
                .width(248.dp)
                .fillMaxHeight()
                .background(BuroColors.Surface)
                .padding(horizontal = 18.dp, vertical = 22.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Brand()
            Text(
                text = "‹",
                color = BuroColors.TextMuted,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.clickable(onClick = onToggleCollapsed).padding(BuroSpacing.Xs),
            )
        }
        Spacer(Modifier.height(22.dp))

        // Everything below the brand scrolls.
        //
        // This list is fifteen destinations plus the sources section, and it was laid out in a plain
        // Column of fixed height: on a 1536x816 screen — an ordinary laptop, and the machine this was
        // reported on — Assinaturas, Perfil and Configurações fall below the bottom edge with no way
        // to reach them. Not clipped-but-scrollable: there was no scroll at all, so the settings
        // screen was simply unreachable from the sidebar.
        //
        // `weight(1f)` and never `fillMaxHeight` on the scrolling child: an unweighted child is
        // measured against unbounded height, which lays the whole list out past the window and
        // scrolls nothing. That is the same mistake ScrollableSettingsUiTest was written for, and
        // this is the third surface in the app to have made it.
        // And it says so.
        //
        // Scrolling without an indicator is the same as not scrolling, from the user's side: the
        // sources section and the buttons that add a playlist sit below the fold on a short window,
        // and with nothing on screen to suggest there is more, they were reported as hidden — only
        // findable by pressing F11 to make the window tall enough to show everything at once.
        //
        // Explicit colours because Compose's default scrollbar is near-black on this near-black
        // surface: drawn, and invisible. The same style the settings panel and the category menu
        // carry, for the same reason.
        val navScroll = rememberScrollState()
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(navScroll)
                    // Room for the bar, so it sits beside the rows rather than over their labels.
                    .padding(end = 8.dp),
        ) {
        SectionLabel(text.library)
        Spacer(Modifier.height(10.dp))
        NavigationItem(
            label = text.home,
            icon = Icons.Default.Home,
            selected = destination == DesktopDestination.HOME,
            onClick = onHome,
        )
        // Directly under Início, where the phone has it: search is how someone who already knows
        // the title's name gets to it, so it belongs before the browsing destinations rather than
        // buried among them.
        NavigationItem(
            label = text.search,
            icon = Icons.Default.Search,
            selected = destination == DesktopDestination.SEARCH,
            onClick = onSearch,
        )
        // After Pesquisa and before the browsing destinations: search is for somebody who knows
        // what they want, Descobrir is for somebody who does not — the two belong together at the
        // top, ahead of the shelves you browse when you already have an idea.
        NavigationItem(
            label = text.shareStrings.discovery.title,
            icon = Icons.Default.Explore,
            selected = destination == DesktopDestination.DISCOVER,
            onClick = onDiscover,
        )
        Spacer(Modifier.height(12.dp))
        NavigationItem(
            label = text.movies,
            icon = Icons.Default.Movie,
            selected =
                destination == DesktopDestination.CATALOG &&
                    catalogType == XtreamContentType.MOVIE,
            onClick = onMovies,
        )
        NavigationItem(
            label = text.series,
            icon = Icons.Default.VideoLibrary,
            selected =
                destination == DesktopDestination.CATALOG &&
                    catalogType == XtreamContentType.SERIES,
            onClick = onSeries,
        )
        NavigationItem(
            label = text.shareStrings.screens.guideTitle,
            icon = Icons.Default.LiveTv,
            selected = destination == DesktopDestination.GUIDE,
            onClick = onGuide,
        )
        NavigationItem(
            label = text.live,
            icon = Icons.Default.LiveTv,
            selected =
                destination == DesktopDestination.CATALOG &&
                    catalogType == XtreamContentType.LIVE,
            onClick = onLive,
        )
        if (hasSubscriptions) {
            NavigationItem(
                label = text.subscriptions,
                icon = Icons.Default.Subscriptions,
                selected = destination == DesktopDestination.SUBSCRIPTIONS,
                onClick = onSubscriptions,
            )
        }

        Spacer(Modifier.height(12.dp))
        NavigationItem(
            label = text.continueWatching,
            icon = Icons.Default.PlayCircle,
            selected = destination == DesktopDestination.CONTINUE,
            onClick = onContinueWatching,
        )
        NavigationItem(
            label = text.settingsText.historyTitle,
            icon = Icons.Default.History,
            selected = destination == DesktopDestination.HISTORY,
            onClick = onHistory,
        )
        NavigationItem(
            label = text.savedForLater.favorites,
            icon = Icons.Default.Favorite,
            selected = destination == DesktopDestination.FAVORITES,
            onClick = onFavorites,
        )
        // Straight after Favoritos, the other list of titles marked for later. Always present
        // rather than hidden while empty: a destination that appears only once you have used it
        // cannot be found by someone looking for where their marks went.
        NavigationItem(
            label = text.savedForLater.remindersTitle,
            icon = Icons.Default.Notifications,
            selected = destination == DesktopDestination.REMINDERS,
            onClick = onReminders,
        )
        if (hasOffline) {
            NavigationItem(
                label = text.downloads,
                icon = Icons.Default.Folder,
                selected = destination == DesktopDestination.DOWNLOADS,
                onClick = onDownloads,
            )
        }
        // Last, after Downloads, and only when the user actually supplied a music playlist. An
        // entry leading to an empty section is worse than no entry at all.
        if (hasMusic) {
            NavigationItem(
                label = text.music,
                icon = Icons.Default.LibraryMusic,
                selected = destination == DesktopDestination.MUSIC,
                onClick = onMusic,
            )
        }
        // Account and settings, after the content destinations and separated from them.
        //
        // Someone walking this list is looking for something to watch; who they are signed in as
        // and how the app behaves are a different kind of question, so they sit at the end rather
        // than among the shelves.
        Spacer(Modifier.height(14.dp))
        NavigationItem(
            // Named for where it goes, like every other row in this list, and nothing else. The
            // active profile's name was shown here — first as the label, then as a subtitle — and
            // both read as the row belonging to that person rather than leading to the profiles.
            // Who is watching is answered by the profile screen this opens.
            label = text.profile,
            icon = Icons.Default.Person,
            selected = false,
            onClick = onProfiles,
        )
        NavigationItem(
            label = text.settings,
            icon = Icons.Default.Settings,
            selected = false,
            onClick = onSettings,
        )

        // The source list used to own the whole remaining column even with one source. It is a
        // rarely-used switch, so it now takes only the height it needs and the section disappears
        // entirely when there is nothing to switch between.
        // Always here, even with one source — and that is the fix rather than a preference.
        //
        // The section used to appear only with two or more, on the reasoning that switching between
        // one thing is pointless. True, but it took the way to *add* a second with it: somebody
        // with a single playlist had nowhere in the app to connect another, because the buttons
        // that do it live on the empty-library screen they will never see again.
        Spacer(Modifier.height(28.dp))
        // The heading folds the list away.
        //
        // Ten subscriptions is the documented maximum and every one of them takes a row, which
        // pushes everything below off the sidebar. Folded, the section is two lines; the count
        // stays visible either way, so nothing is hidden without saying how much.
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(BuroRadius.Small)
                    .clickable(enabled = sources.size > 1) { sourcesFolded = !sourcesFolded }
                    .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SectionLabel(text.sources)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = sources.size.toString(),
                    color = BuroColors.TextSubtle,
                    style = MaterialTheme.typography.labelLarge,
                )
                if (sources.size > 1) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (sourcesFolded) "▸" else "▾",
                        color = BuroColors.TextSubtle,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        // One list is still a list.
        //
        // The rows were shown only from two upwards, on the reasoning that there is nothing to
        // switch between — but the rows are also the only place to rename or forget a list, so
        // somebody with a single subscription could do neither. Reported as "tem uma fonte, porem
        // nao consigo editar ela".
        if (sources.isNotEmpty() && !sourcesFolded) {
            // A plain Column, not a LazyColumn.
            //
            // The sidebar now scrolls as a whole, and a LazyColumn inside a `verticalScroll` is a
            // nested scrollable on the same axis — Compose cannot measure that and throws. Nobody
            // hit it because the branch needs two sources and most people have one, which is the
            // worst kind of latent crash: it waits for the user who connects a second playlist.
            //
            // Laziness bought nothing here anyway. This is a handful of rows, not a catalogue.
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                sources.forEach { source ->
                    SourceItem(
                        source = source,
                        selected = source.id == selectedSourceId,
                        onClick = { onSourceSelected(source.id) },
                        // Renaming and forgetting a list, here rather than only on the profile
                        // form. This is where somebody looking at a list that stopped answering
                        // actually is, and a row marked "not answering" that cannot be removed
                        // reads as a fault in the app.
                        //
                        // Only for saved subscriptions: an imported M3U has no stored credentials
                        // to forget, and its row is the catalogue itself.
                        onRename =
                            if (source.kind == DesktopSourceKind.XTREAM_SESSION) {
                                { label -> onRenameSource(source.id, label) }
                            } else {
                                null
                            },
                        onRemove =
                            if (source.kind == DesktopSourceKind.XTREAM_SESSION) {
                                { removingSource = source }
                            } else {
                                null
                            },
                        strings = text,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        // Forgetting every dead list at once.
        //
        // A subscription that expired or moved leaves a row that will never work again, and after a
        // few of those the sidebar is mostly dead entries. Removing them one at a time is a
        // confirmation each, for a decision that is really one decision.
        val offline = sources.filter { !it.isWorking && it.kind == DesktopSourceKind.XTREAM_SESSION }
        if (offline.size > 1 && !sourcesFolded) {
            BuroInteractiveRow(
                onClick = { removingOffline = offline },
                selected = false,
                shape = BuroRadius.Small,
                contentDescription = text.shareStrings.screens.mergeSourcesRemoveOffline.format(offline.size),
            ) {
                Text(
                    text = text.shareStrings.screens.mergeSourcesRemoveOffline.format(offline.size),
                    color = BuroColors.TextSubtle,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
        }
        // Outside the fold, on purpose.
        //
        // Folding the rows must not take the switch with it: a merge control that disappears when
        // the lists are tidied away is the "no switch anywhere" that was reported in the first
        // place.
        if (sources.size > 1) {
            // Here, beside the lists themselves.
            //
            // It was offered only on the profile form, which is not where somebody adds a second
            // subscription — they use these buttons. Reported exactly that way: no switch was
            // visible, so the two lists were expected to add up on their own.
            BuroInteractiveRow(
                onClick = { onToggleMergeSources(!mergeSources) },
                selected = false,
                shape = BuroRadius.Small,
                contentDescription = text.shareStrings.screens.mergeSourcesTitle,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (mergeSources) "◉" else "○",
                        color = BuroColors.Primary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(Modifier.width(8.dp))
                    // A Column, not the rest of the Row: the label and the note are two lines of
                    // text, and a Row would squeeze the second into one letter per line — the
                    // sidebar has done exactly that before.
                    Column {
                        Text(
                            text = text.shareStrings.screens.mergeSourcesTitle,
                            color = BuroColors.TextMuted,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 2,
                        )
                        // Said here rather than nowhere. The switch cannot rebuild the open
                        // catalogue, so without this it looks like a button that does nothing.
                        Text(
                            text = text.shareStrings.screens.mergeSourcesRestart,
                            color = BuroColors.TextSubtle,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 2,
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        // Both ways in, named for what they are rather than hidden behind one "+" that would make
        // somebody guess which kind of account they are about to add.
        NavigationItem(
            label = text.connectXtream,
            icon = Icons.Default.AddCircle,
            selected = false,
            onClick = onConnectXtream,
        )
        NavigationItem(
            label = text.importM3u,
            icon = Icons.Default.PlaylistAdd,
            selected = false,
            onClick = onImportM3u,
        )
        // The third way in: the same M3U, but read off the box that holds the media rather than
        // copied to this machine first.
        NavigationItem(
            label = text.shareStrings.remoteSource.title,
            icon = Icons.Default.Dns,
            selected = false,
            onClick = onAddRemoteSource,
        )

            // Breathing room under the last row, so it does not sit flush against the window edge
            // when the list is scrolled to the bottom.
            Spacer(Modifier.height(BuroSpacing.Md))
        }
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(navScroll),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                style =
                    LocalScrollbarStyle.current.copy(
                        thickness = 6.dp,
                        unhoverColor = BuroColors.BorderSoft.copy(alpha = 0.24f),
                        hoverColor = BuroColors.Primary,
                    ),
            )
        }
    }
}

/**
 * The sidebar reduced to a strip: the mark, the name written downwards, and the way back.
 *
 * The letters are stacked rather than rotated. Compose can rotate a Text, but a rotated glyph on a
 * strip this narrow renders soft and reads worse than the plain characters — and the name is short
 * enough that stacking is legible.
 */
@Composable
private fun CollapsedSidebar(onExpand: () -> Unit) {
    val text = strings
    Column(
        modifier =
            Modifier
                .width(56.dp)
                .fillMaxHeight()
                .background(BuroColors.Surface)
                .clickable(onClick = onExpand)
                .padding(vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Drawn rather than loaded: the PNG carries an opaque black square, which showed as a dark
        // tile around the ring on every surface that is not exactly the same black.
        BuroMark(size = 32.dp)
        Spacer(Modifier.height(20.dp))

        Text(
            // One letter per line. Built from the letters alone so no stray separator is stacked.
            text = "IPTVBURO".toCharArray().joinToString("\n"),
            color = BuroColors.TextMuted,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            lineHeight = 14.sp,
        )

        Spacer(Modifier.weight(1f))
        // The affordance, at the bottom where a collapse control usually lives. The whole strip is
        // clickable too — a 56dp target is easy to miss if only the arrow works.
        Text(
            text = "›",
            color = BuroColors.Primary,
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = text.settingsText.expandSidebar,
            color = BuroColors.TextSubtle,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            lineHeight = 11.sp,
        )
    }
}

@Composable
private fun Brand() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // The real mark, drawn rather than loaded — see BuroMark. The window and taskbar icon still
        // use the bitmap, since AWT cannot take a composable, so the app is recognisable by one
        // shape everywhere it appears.
        BuroMark(size = 42.dp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                "IPTV BURO",
                color = BuroColors.Text,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                "DESKTOP",
                color = BuroColors.Primary,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = BuroColors.TextSubtle,
        style = MaterialTheme.typography.labelLarge,
    )
}

@Composable
private fun NavigationItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    /**
     * The destination's own mark.
     *
     * Null keeps the plain dot this row used to draw for everything, which is what any caller that
     * has no icon to offer should get rather than a gap where one would sit.
     */
    icon: ImageVector? = null,
) {
    BuroInteractiveRow(
        onClick = onClick,
        selected = selected,
        modifier = Modifier.fillMaxWidth(),
        contentDescription = label,
    ) { state ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The colour is the whole effect, and it is the same rule the label follows: the mark
            // is dim while the row is idle and lights up when it is selected or hovered. A
            // monochrome icon tinted this way gives the sidebar its rhythm without any artwork —
            // which is exactly how the phone's bar reads.
            val markColour =
                when {
                    selected -> BuroColors.Primary
                    state.active -> BuroColors.TextMuted
                    else -> BuroColors.TextSubtle
                }
            if (icon == null) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(markColour))
            } else {
                Icon(
                    imageVector = icon,
                    // Null: the row already carries the label as its content description, and a
                    // screen reader announcing the same name twice is noise.
                    contentDescription = null,
                    tint = markColour,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = label,
                color = if (selected || state.active) BuroColors.Text else BuroColors.TextMuted,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/**
 * One small action on a source row.
 *
 * A plain clickable box rather than a TextButton: a button carries a 48dp minimum touch target on
 * each side, and two of those in a 248dp sidebar left the list's name as one letter and an ellipsis.
 */
@Composable
private fun SourceRowAction(
    glyph: String,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            glyph,
            color = BuroColors.TextSubtle,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun SourceItem(
    source: DesktopSourceSummary,
    selected: Boolean,
    onClick: () -> Unit,
    /** Null for a list with no stored name to change, which keeps the row as it was. */
    onRename: ((String) -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    strings: DesktopStrings,
) {
    var renaming by remember(source.id) { mutableStateOf(false) }
    var draft by remember(source.id) { mutableStateOf(source.name) }
    val interactions = remember(source.id) { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()

    // Edited in place rather than in a dialog: a dialog would cover the other names at exactly the
    // moment the point is telling them apart.
    if (renaming && onRename != null) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = {
                    // A blank name is refused by the store rather than saved, so the row would
                    // silently keep its old one. Closing anyway would look like it was accepted.
                    if (draft.isNotBlank()) {
                        onRename(draft)
                        renaming = false
                    }
                },
                contentPadding = PaddingValues(horizontal = 6.dp),
            ) {
                Text("✓", color = BuroColors.Primary)
            }
            TextButton(
                onClick = {
                    draft = source.name
                    renaming = false
                },
                contentPadding = PaddingValues(horizontal = 6.dp),
            ) {
                Text("✕", color = BuroColors.TextMuted)
            }
        }
        return
    }

    BuroInteractiveRow(
        onClick = onClick,
        selected = selected,
        modifier = Modifier.fillMaxWidth(),
        contentDescription = source.name,
    ) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .hoverable(interactions)
                .padding(horizontal = 11.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(if (selected) BuroColors.Primary.copy(alpha = 0.18f) else BuroColors.SurfaceRaised),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (source.kind == DesktopSourceKind.XTREAM_SESSION) "XT" else "M3U",
                color = if (selected) BuroColors.Primary else BuroColors.TextSubtle,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                source.name,
                color = BuroColors.Text,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // A list that did not answer says so, ahead of any count.
            //
            // Only a merge can show one: browsing a list at a time, a list that fails never opens.
            // Without this the row looks like every other, and a catalogue quietly missing a
            // subscription's titles is indistinguishable from one that never had them.
            if (!source.isWorking) {
                Text(
                    // Not the full "%s did not answer" sentence: the name is already the line
                    // above, and repeating it in a narrow sidebar wraps to three lines saying
                    // one thing.
                    strings.shareStrings.screens.mergeSourcesOffline,
                    color = BuroColors.Warning,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            } else if (source.itemCount > 0) {
                // Zero means "not counted", not "empty".
                //
                // Merged subscriptions have no per-list count to show — the whole point is that the
                // catalogue is one thing — and "0 itens" under every list would read as a load that
                // failed. Nothing is the honest answer.
                Text(
                    "${source.itemCount} ${strings.items}",
                    color = BuroColors.TextSubtle,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        // Rename and forget, on the row the pointer is over or the one that is open.
        //
        // Shown on every row at once, two buttons at their minimum touch size left almost nothing
        // of a 248dp sidebar for the name itself: "VISTA" and "BURO" came out as "VI…" and "B…".
        // The name is what the row is for.
        //
        // Hover rather than selection alone, because a list that stopped answering has to be
        // removable without being opened first — opening it is the thing that does not work.
        if ((onRename != null || onRemove != null) && (hovered || selected)) {
            if (onRename != null) {
                SourceRowAction(glyph = "✎") {
                    draft = source.name
                    renaming = true
                }
            }
            if (onRemove != null) {
                SourceRowAction(glyph = "🗑", onClick = onRemove)
            }
        }
    }
    }
}


@Composable
private fun TopBar(
    channelCount: Int,
    sourceCount: Int,
    activeProfile: DesktopProfile?,
    activeProfilePhoto: java.nio.file.Path?,
    language: DesktopLanguage,
    onChangeProfile: () -> Unit,
    onSelectLanguage: (DesktopLanguage) -> Unit,
    updateBusy: Boolean,
    updateMessage: String?,
    onUpdate: () -> Unit,
    sessionActive: Boolean,
    onEndSession: () -> Unit,
    catalogRefreshing: Boolean,
    onRefreshCatalog: () -> Unit,
    /** Opens the connection test, beside the button that refreshes the lists. */
    onOpenDiagnostics: () -> Unit,
    metadataApiKey: String,
    onMetadataApiKeyChange: (String) -> Unit,
    streamingRegion: String,
    onSelectRegion: (String) -> Unit,
    uses24HourClock: Boolean,
    /** The artwork fill, drawn in place of the counts while it runs. */
    cacheProgress: CacheFillProgress,
    onPauseCacheFill: () -> Unit,
    onResumeCacheFill: () -> Unit,
    onCancelCacheFill: () -> Unit,
    /** What the bell holds for the profile that is watching. */
    notifications: NotificationCentre,
    onNotificationsOpened: () -> Unit,
    onDismissNotification: (String) -> Unit,
    onClearNotifications: () -> Unit,
    /** Null while the first check is still in flight, which is when there is nothing to say. */
    licenseStatus: LicenseStatus?,
    onOpenPurchase: () -> Unit,
) {
    val text = strings
    // A Row does not shrink unweighted children: once their intrinsic widths exceed the space they
    // simply overflow off-screen. The trailing controls are therefore dropped by priority as the
    // window narrows instead of being allowed to run past the right edge.
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(74.dp)) {
        // Thresholds are the width of the header itself, not the window: the sidebar has already
        // been subtracted by the time this measures. Measured against the longest translation.
        val showPrivacyPill = maxWidth >= 1_060.dp
        val showUpdateLabel = maxWidth >= 860.dp
        val showProfile = maxWidth >= 700.dp

        Row(
            modifier = Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = 26.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text.yourLibrary,
                        color = BuroColors.Text,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // Beside the library title rather than buried in the settings menu: this acts on
                    // what is on screen, so it belongs next to the thing it refreshes.
                    if (sessionActive) {
                        Spacer(Modifier.width(BuroSpacing.Sm))
                        BuroInteractiveRow(
                            onClick = { if (!catalogRefreshing) onRefreshCatalog() },
                            selected = false,
                            shape = BuroRadius.Pill,
                            contentDescription = text.refreshCatalog,
                        ) {
                            Row(
                                modifier =
                                    Modifier.padding(horizontal = BuroSpacing.Sm, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // A spinner rather than an ellipsis. Refreshing a large catalogue
                                // takes long enough that a static "…" is indistinguishable from a
                                // button that did nothing at all.
                                if (catalogRefreshing) {
                                    CircularProgressIndicator(
                                        color = BuroColors.Primary,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(14.dp),
                                    )
                                } else {
                                    Text(
                                        text = "⟳",
                                        color = BuroColors.Primary,
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text =
                                        if (catalogRefreshing) text.loadingCatalog else text.refreshCatalog,
                                    color =
                                        if (catalogRefreshing) BuroColors.Primary else BuroColors.TextMuted,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                    // Beside the refresh button, because the two answer the same complaint from
                    // opposite ends: "my list is wrong" and "my picture keeps freezing". Somebody
                    // who cannot tell which of the two they have will try both, and both are here.
                    Spacer(Modifier.width(BuroSpacing.Sm))
                    BuroInteractiveRow(
                        onClick = onOpenDiagnostics,
                        selected = false,
                        shape = BuroRadius.Pill,
                        contentDescription = text.shareStrings.screens.diagnosticsAction,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = BuroSpacing.Sm, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "◉",
                                color = BuroColors.Primary,
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = text.shareStrings.screens.diagnosticsAction,
                                color = BuroColors.TextMuted,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                            )
                        }
                    }
                }
                // Version and counts share one quiet line so the header carries a single strong
                // element instead of five competing pills. The full version is shown, including the
                // pre-release suffix: "v0.2.0" for a 0.2.0-alpha.5 build made bug reports ambiguous.
                // While artwork is downloading, that line becomes the progress: same row, same
                // height, no second bar pushing the app down.
                //
                // It replaces the counts rather than joining them because the two say the same kind
                // of thing about the same library, and the one that changes is the one worth
                // reading. The counts come back the moment it finishes.
                if (cacheProgress.state != CacheFillState.IDLE) {
                    HeaderCacheProgress(
                        progress = cacheProgress,
                        onPause = onPauseCacheFill,
                        onResume = onResumeCacheFill,
                        onCancel = onCancelCacheFill,
                    )
                } else {
                    Text(
                        updateMessage
                            ?: "$sourceCount ${text.sourcesCount}  ·  $channelCount ${text.items}  ·  " +
                            "IPTV BURO v$DESKTOP_VERSION",
                        color = BuroColors.TextSubtle,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // How long is left, and how to buy.
            //
            // Shown here rather than only at the moment of blocking, because a customer who
            // discovers their trial is over by finding the app locked has been ambushed. Absent
            // entirely once a paid licence has more than a month to run: a permanent countdown on
            // something already paid for reads as nagging.
            licenseStatus?.let { status ->
                LicenseChip(
                    status = status,
                    languageTag = language.tag,
                    onOpenPurchase = onOpenPurchase,
                )
                Spacer(Modifier.width(BuroSpacing.Md))
            }
            // The clock, then who is watching, and nothing else. Four loose language buttons plus
            // an update button plus two status pills once made this read as a debug toolbar; what
            // is left is the two things worth glancing at, with every destination in the sidebar.
            // Clock before the profile chip: the eye lands on the right of a header looking for
            // status, and the time is the thing most often wanted there.
            HeaderClock(uses24Hour = uses24HourClock, languageTag = language.tag)
            Spacer(Modifier.width(BuroSpacing.Md))
            if (showProfile) {
                ProfileChip(
                    name = activeProfile?.name ?: text.profile,
                    avatarIndex = activeProfile?.avatarIndex ?: 0,
                    photo = activeProfilePhoto,
                    onClick = onChangeProfile,
                )
                Spacer(Modifier.width(BuroSpacing.Xs))
                // To the right of the profile, where a notice about *this* viewer belongs: the bell
                // holds one profile's news, so it reads as part of who is watching rather than as a
                // property of the window.
                NotificationBell(
                    centre = notifications,
                    onOpened = onNotificationsOpened,
                    onDismiss = onDismissNotification,
                    onClearAll = onClearNotifications,
                )
            }
            // No gear here any more. Settings moved into the sidebar as a named row, and this
            // header button opened the very same dialog — two affordances for one destination,
            // one of them an unlabelled glyph sitting where the window controls are.
        }
    }
}

/**
 * Gear menu holding language and app settings.
 *
 * These were previously spread across the header as individual controls. Collapsing them behind one
 * affordance leaves the header carrying only what changes often — the library summary and who is
 * watching.
 */
/**
 * A heading in the settings menu, with a line explaining what it controls.
 *
 * The explanation is not decoration: "Idioma" and "Região" are near-synonyms in Portuguese, and a
 * user reasonably read them as the same setting listed twice.
 */
@Composable
private fun SettingsSectionLabel(
    label: String,
    hint: String,
) {
    Column(
        modifier = Modifier.padding(horizontal = BuroSpacing.Md, vertical = BuroSpacing.Xs).width(320.dp),
    ) {
        Text(
            text = label,
            color = BuroColors.TextMuted,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = hint,
            color = BuroColors.TextSubtle,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/**
 * One selectable value in the settings menu, as a pill.
 *
 * Chips rather than full-width menu rows: nine of those overflowed the screen on a laptop, and a
 * DropdownMenu scrolls with no visible indicator, so everything below simply looked missing. Four
 * languages and five regions fit in two short rows this way.
 */
@Composable
private fun SettingsChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    BuroInteractiveRow(
        onClick = onClick,
        selected = selected,
        shape = BuroRadius.Pill,
        contentDescription = label,
    ) { state ->
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = BuroSpacing.Sm, vertical = 6.dp),
            color =
                when {
                    selected -> BuroColors.Primary
                    state.active -> BuroColors.Text
                    else -> BuroColors.TextMuted
                },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/**
 * A country's name for the region picker.
 *
 * Written out rather than taken from `Locale.getDisplayCountry`, which returns the name in the JVM's
 * own locale and would put German country names in front of a Portuguese-speaking user. The list is
 * short and fixed, so a lookup is both correct and simpler than plumbing a locale through.
 */
/**
 * The line under a running download's bar.
 *
 * A percentage alone cannot answer the two questions a waiting user actually has: is it still
 * moving, and how long. So the speed and the remaining time join it — "17% · 4.2 MB/s · 12 min" —
 * and a stalled transfer becomes visible instead of looking like a slow one.
 *
 * Each part is dropped when it is unknown rather than shown as a zero: a server that sends no
 * length gives no percentage and no estimate, and inventing either would be worse than the silence.
 */
private fun downloadProgressLabel(
    state: DownloadState.Running,
    inProgressLabel: String,
): String {
    val parts = mutableListOf<String>()
    parts += if (state.fraction >= 0f) "${(state.fraction * 100).toInt()}%" else inProgressLabel
    if (state.bytesPerSecond > 0) parts += formatRate(state.bytesPerSecond)
    // Only once the rate has settled: an estimate from the first second of a transfer swings between
    // minutes and hours and reads as broken.
    state.secondsRemaining?.takeIf { it > 0 }?.let { seconds -> parts += formatDuration(seconds) }
    return parts.joinToString(" · ")
}

private fun regionName(code: String): String =
    when (code) {
        "BR" -> "Brasil"
        "PT" -> "Portugal"
        "US" -> "Estados Unidos"
        "DE" -> "Alemanha"
        "IT" -> "Itália"
        else -> code
    }

/** Compact segmented control. Four loose text buttons read as debug UI at this size. */
@Composable
private fun LanguagePicker(
    language: DesktopLanguage,
    onSelect: (DesktopLanguage) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .clip(BuroRadius.Pill)
                .background(BuroColors.SurfaceRaised)
                .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DesktopLanguage.entries.forEach { option ->
            val active = option == language
            BuroInteractiveRow(
                onClick = { onSelect(option) },
                selected = active,
                shape = BuroRadius.Pill,
                contentDescription = option.tag,
            ) {
                Text(
                    text = option.tag.substringBefore('-').uppercase(),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    color = if (active) BuroColors.Primary else BuroColors.TextSubtle,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun ProfileChip(
    name: String,
    avatarIndex: Int,
    photo: java.nio.file.Path?,
    onClick: () -> Unit,
) {
    BuroInteractiveRow(
        onClick = onClick,
        selected = false,
        shape = BuroRadius.Pill,
        contentDescription = name,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The face the user picked, not an initial. Choosing one and then seeing a letter makes
            // the choice feel ignored.
            ProfileFace(avatarIndex = avatarIndex, photo = photo, size = 26.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = name,
                color = BuroColors.Text,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(6.dp))
        }
    }
}

@Composable
private fun StatusPill(
    text: String,
    color: Color,
) {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(100.dp))
                .background(color.copy(alpha = 0.12f))
                .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(7.dp))
        Text(text, color = color, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun EmptyLibrary(
    onImport: () -> Unit,
    onConnectXtream: () -> Unit,
) {
    val text = strings
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp).clip(RoundedCornerShape(24.dp)),
    ) {
        Image(
            painter = painterResource("brand/buro-nocturne-hero.png"),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            alignment = Alignment.CenterEnd,
            contentScale = ContentScale.Crop,
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            0f to BuroColors.Canvas.copy(alpha = 0.98f),
                            0.48f to BuroColors.Canvas.copy(alpha = 0.82f),
                            0.78f to BuroColors.Canvas.copy(alpha = 0.18f),
                            1f to Color.Transparent,
                        ),
                    )
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                BuroColors.Canvas.copy(alpha = 0.08f),
                                Color.Transparent,
                                BuroColors.Canvas.copy(alpha = 0.52f),
                            ),
                        ),
                    ),
        )
        Column(
            horizontalAlignment = Alignment.Start,
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(horizontal = 58.dp, vertical = 44.dp)
                    .widthIn(max = 610.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(BuroColors.Primary.copy(alpha = 0.14f))
                        .border(
                            1.dp,
                            BuroColors.Primary.copy(alpha = 0.36f),
                            RoundedCornerShape(100.dp),
                        )
                        .padding(horizontal = 13.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text.emptyBadge,
                    color = BuroColors.Primary,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Spacer(Modifier.height(22.dp))
            Text(
                text.emptyHeadline,
                color = BuroColors.Text,
                style = MaterialTheme.typography.displaySmall,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text.emptyBody,
                color = BuroColors.TextMuted,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(26.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onConnectXtream,
                    shape = RoundedCornerShape(12.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = BuroColors.Primary,
                            contentColor = BuroColors.OnPrimary,
                        ),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
                ) {
                    Text(text.connectXtream, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onImport,
                    shape = RoundedCornerShape(12.dp),
                    colors =
                        ButtonDefaults.outlinedButtonColors(
                            contentColor = BuroColors.Text,
                        ),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                ) {
                    Text(text.importM3u, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text.credentialsStayLocal,
                color = BuroColors.TextSubtle,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun CatalogWorkspace(
    appState: DesktopAppState,
    onOpenExternal: (Channel) -> Unit,
) {
    val text = strings
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = appState.searchQuery,
                onValueChange = appState::updateSearch,
                modifier = Modifier.width(360.dp),
                singleLine = true,
                placeholder = { Text(text.searchChannel) },
                leadingIcon = {
                    Text("⌕", color = BuroColors.TextSubtle)
                },
                shape = RoundedCornerShape(12.dp),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BuroColors.Primary,
                        unfocusedBorderColor = BuroColors.Border,
                        focusedContainerColor = BuroColors.Surface,
                        unfocusedContainerColor = BuroColors.Surface,
                    ),
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${appState.visibleChannels.size} ${text.results}",
                color = BuroColors.TextSubtle,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.width(12.dp))
            OutlinedButton(
                onClick = appState::forgetSelectedSource,
                shape = RoundedCornerShape(10.dp),
                colors =
                    ButtonDefaults.outlinedButtonColors(
                        contentColor = BuroColors.TextMuted,
                    ),
            ) {
                Text(text.forgetSource)
            }
        }
        HorizontalDivider(color = BuroColors.BorderSoft)
        // Weighted, not fillMaxSize: an unweighted Column child is measured against unbounded
        // height, so fillMaxSize would claim a whole screen below the header and push the list off
        // the bottom of the window.
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val compact = maxWidth < 1_050.dp
            Row(modifier = Modifier.fillMaxSize()) {
                CategoryPane(
                    categories = appState.categories,
                    selectedCategoryId = appState.selectedCategoryId,
                    onCategorySelected = appState::selectCategory,
                    modifier = Modifier.width(if (compact) 190.dp else 230.dp),
                )
                PaneDivider()
                ChannelPane(
                    channels = appState.visibleChannels,
                    selectedChannelId = appState.selectedChannel?.id,
                    onChannelSelected = appState::selectChannel,
                    modifier = Modifier.width(if (compact) 300.dp else 360.dp),
                )
                if (!compact) {
                    PaneDivider()
                    ChannelDetail(
                        channel = appState.selectedChannel,
                        onOpenExternal = onOpenExternal,
                        modifier = Modifier.weight(1f),
                        nowAndNext = appState.selectedChannelNowAndNext,
                    )
                } else {
                    PaneDivider()
                    CompactChannelDetail(
                        channel = appState.selectedChannel,
                        onOpenExternal = onOpenExternal,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryPane(
    categories: List<Category>,
    selectedCategoryId: String?,
    onCategorySelected: (String?) -> Unit,
    modifier: Modifier,
) {
    Column(modifier = modifier.fillMaxHeight().padding(16.dp)) {
        SectionLabel("CATEGORIAS")
        Spacer(Modifier.height(12.dp))
        // Weighted: an unweighted Column child is measured against unbounded height, so a long
        // category list would run past the window instead of scrolling.
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                CategoryItem(
                    label = "Todos os canais",
                    selected = selectedCategoryId == null,
                    onClick = { onCategorySelected(null) },
                )
            }
            items(categories, key = Category::id) { category ->
                CategoryItem(
                    label = category.name,
                    selected = category.id == selectedCategoryId,
                    onClick = { onCategorySelected(category.id) },
                )
            }
        }
    }
}

@Composable
private fun CategoryItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(9.dp))
                .background(if (selected) BuroColors.Primary.copy(alpha = 0.11f) else Color.Transparent)
                .clickable(onClick = onClick)
                .padding(horizontal = 11.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Box(Modifier.size(3.dp, 20.dp).clip(CircleShape).background(BuroColors.Primary))
            Spacer(Modifier.width(9.dp))
        }
        Text(
            label,
            color = if (selected) BuroColors.Text else BuroColors.TextMuted,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ChannelPane(
    channels: List<Channel>,
    selectedChannelId: String?,
    onChannelSelected: (String) -> Unit,
    modifier: Modifier,
) {
    Column(modifier = modifier.fillMaxHeight().padding(16.dp)) {
        SectionLabel("CANAIS")
        Spacer(Modifier.height(12.dp))
        if (channels.isEmpty()) {
            Text(
                strings.shareStrings.screens.noChannelMatches,
                color = BuroColors.TextSubtle,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(10.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                items(channels, key = Channel::id) { channel ->
                    ChannelItem(
                        channel = channel,
                        selected = channel.id == selectedChannelId,
                        onClick = { onChannelSelected(channel.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelItem(
    channel: Channel,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(11.dp))
                .background(if (selected) BuroColors.SurfaceHover else Color.Transparent)
                .clickable(onClick = onClick)
                .padding(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BuroRemoteArtwork(
            artworkUrl = channel.logoUri,
            contentDescription = channel.name,
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Fit,
        ) {
            ChannelMonogram(channel.name, 42)
        }
        Spacer(Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                channel.name,
                color = BuroColors.Text,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (channel.requestHeaders.isEmpty()) "Pronto para abrir" else "Requer cabeçalhos",
                color =
                    if (channel.requestHeaders.isEmpty()) {
                        BuroColors.TextSubtle
                    } else {
                        BuroColors.Warning
                    },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (selected) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(BuroColors.Primary))
        }
    }
}

@Composable
private fun ChannelDetail(
    channel: Channel?,
    onOpenExternal: (Channel) -> Unit,
    modifier: Modifier,
    /**
     * What is on now and next, when the playlist named a guide.
     *
     * Both halves are null for a list without one, and the block is then not drawn at all — an
     * empty "Agora —" would read as a channel that is off air rather than as a list that carries
     * no schedule.
     */
    nowAndNext: Pair<XtreamEpgProgram?, XtreamEpgProgram?> = null to null,
) {
    Box(
        modifier = modifier.fillMaxHeight().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (channel == null) {
            Text(strings.selectChannel, color = BuroColors.TextSubtle)
            return@Box
        }
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                BuroColors.SurfaceRaised,
                                BuroColors.Surface,
                            ),
                        ),
                    ).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BuroRemoteArtwork(
                artworkUrl = channel.logoUri,
                contentDescription = channel.name,
                modifier = Modifier
                    .width(196.dp)
                    .height(124.dp)
                    .clip(RoundedCornerShape(20.dp)),
                contentScale = ContentScale.Fit,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    ChannelMonogram(channel.name, 88)
                }
            }
            Spacer(Modifier.height(22.dp))
            Text(
                channel.name,
                color = BuroColors.Text,
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            PlaybackStatus(channel)
            if (nowAndNext.first != null || nowAndNext.second != null) {
                Spacer(Modifier.height(18.dp))
                ChannelGuideBlock(nowAndNext)
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { onOpenExternal(channel) },
                shape = RoundedCornerShape(12.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = BuroColors.Primary,
                        contentColor = BuroColors.OnPrimary,
                    ),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text("Abrir externamente", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "A URL permanece oculta e só é enviada após sua confirmação.",
                color = BuroColors.TextSubtle,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** Now and next for a playlist channel, in the two lines the pane has room for. */
@Composable
private fun ChannelGuideBlock(nowAndNext: Pair<XtreamEpgProgram?, XtreamEpgProgram?>) {
    val labels = strings.shareStrings.screens
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        nowAndNext.first?.let { program ->
            GuideLine(labels.guideNow, program.title, BuroColors.Text)
        }
        nowAndNext.second?.let { program ->
            Spacer(Modifier.height(6.dp))
            GuideLine(labels.guideNext, program.title, BuroColors.TextSubtle)
        }
    }
}

@Composable
private fun GuideLine(
    label: String,
    title: String,
    titleColor: androidx.compose.ui.graphics.Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label.uppercase(),
            color = BuroColors.TextSubtle,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            title,
            color = titleColor,
            style = MaterialTheme.typography.bodyMedium,
            // The pane is narrow and a programme title can be long; weight(1f) is what keeps the
            // title from squeezing the label down to one letter per line.
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CompactChannelDetail(
    channel: Channel?,
    onOpenExternal: (Channel) -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.fillMaxHeight().padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (channel == null) {
            Text(strings.selectChannel, color = BuroColors.TextSubtle)
            return@Column
        }
        BuroRemoteArtwork(
            artworkUrl = channel.logoUri,
            contentDescription = channel.name,
            modifier = Modifier
                .width(142.dp)
                .height(92.dp)
                .clip(RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Fit,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                ChannelMonogram(channel.name, 62)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            channel.name,
            color = BuroColors.Text,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        PlaybackStatus(channel)
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = { onOpenExternal(channel) },
            shape = RoundedCornerShape(11.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = BuroColors.Primary,
                    contentColor = BuroColors.OnPrimary,
                ),
        ) {
            Text("Abrir", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PlaybackStatus(channel: Channel) {
    val requiresHeaders =
        channel.playbackReadiness() == PlaybackReadiness.EXTERNAL_MAY_MISS_HEADERS
    Text(
        text =
            if (requiresHeaders) {
                strings.shareStrings.screens.externalHeadersWarning
            } else {
                strings.shareStrings.screens.externalAddressValid
            },
        color = if (requiresHeaders) BuroColors.Warning else BuroColors.Success,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun ChannelMonogram(
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
private fun PaneDivider() {
    Box(Modifier.width(1.dp).fillMaxHeight().background(BuroColors.BorderSoft))
}

@Composable
private fun ImportStatusBanner(
    status: ImportStatus,
    modifier: Modifier,
    onDismiss: () -> Unit,
) {
    val content =
        when (status) {
            ImportStatus.Idle, ImportStatus.Loading -> null
            is ImportStatus.Success ->
                if (status.warningCount > 0) {
                    "${status.channelCount} canais importados • ${status.warningCount} avisos tratados"
                } else {
                    "${status.channelCount} canais importados com sucesso"
                }
            is ImportStatus.Error -> status.message
        }
    if (content != null) {
        Row(
            modifier =
                modifier
                    .widthIn(max = 480.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(BuroColors.SurfaceHover)
                    .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (status is ImportStatus.Error) BuroColors.Error else BuroColors.Success,
                    ),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                content,
                color = BuroColors.Text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismiss) {
                Text(strings.close)
            }
        }
    }
}

@Composable
private fun XtreamStatusBanner(
    status: XtreamStatus,
    modifier: Modifier,
    onDismiss: () -> Unit,
) {
    val message = (status as? XtreamStatus.Error)?.message ?: return
    Row(
        modifier =
            modifier
                .widthIn(max = 480.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(BuroColors.SurfaceHover)
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(BuroColors.Error))
        Spacer(Modifier.width(10.dp))
        Text(
            message,
            color = BuroColors.Text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDismiss) {
            Text(strings.close)
        }
    }
}

@Composable
private fun LoadingOverlay(message: String) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(BuroColors.Canvas.copy(alpha = 0.76f)),
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
            CircularProgressIndicator(
                color = BuroColors.Primary,
                strokeWidth = 3.dp,
                modifier = Modifier.size(34.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                message,
                color = BuroColors.Text,
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                strings.noSensitiveData,
                color = BuroColors.TextSubtle,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ExternalPlaybackDialog(
    channel: Channel,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val requiresHeaders =
        channel.playbackReadiness() == PlaybackReadiness.EXTERNAL_MAY_MISS_HEADERS
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !requiresHeaders,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = BuroColors.Primary,
                        contentColor = BuroColors.OnPrimary,
                    ),
            ) {
                Text("Assistir agora", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        },
        title = { Text("Reproduzir no IPTV BURO?") },
        text = {
            Text(
                if (requiresHeaders) {
                    strings.shareStrings.screens.headersUnsupported
                } else {
                    "O vídeo será aberto no player VLC integrado, com suporte a H.264, H.265/HEVC, AAC, MP4, MKV e HLS."
                },
            )
        },
    )
}

@Composable
private fun DesktopProfileGate(
    profiles: List<DesktopProfile>,
    photoFor: (String) -> java.nio.file.Path?,
    onSelect: (String?) -> Unit,
    onAddProfile: () -> Unit,
    onEditProfile: (String) -> Unit,
    onDelete: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: (() -> Unit)?,
    /**
     * This machine's code, shown on request.
     *
     * Offered here, before any playlist exists, because the code is how a seller finds this
     * install to configure it remotely — and the person who needs that most is exactly the one who
     * has not managed to configure anything. It was previously reachable only through the licence
     * screen, which opens either when the trial has run out or from the countdown chip, so during
     * the free days the customer who needed help had no way to find it.
     */
    deviceCode: String,
) {
    var newName by remember { mutableStateOf("") }
    var kids by remember { mutableStateOf(false) }
    var avatar by remember { mutableStateOf(0) }
    var confirmingReset by remember { mutableStateOf(false) }
    var showingDeviceCode by remember { mutableStateOf(false) }
    // Which profile is one tap away from being removed. Held here so tapping a second profile
    // cancels the first, rather than arming two at once.
    var confirmingDelete by remember { mutableStateOf<String?>(null) }
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                // Opaque when there is no way out of it, the same rule the onboarding steps follow.
                //
                // The scrim is 76% and reads as a dialog over something. On a first run there is
                // nothing behind this to look at yet, and the home screen showed straight through
                // it: "IPTV BURO" landed on top of a film title and both became unreadable. With a
                // profile already active the gate is a switcher over a library the viewer knows,
                // and seeing it dimmed underneath is the point.
                .background(if (onDismiss == null) BuroColors.Canvas else BuroColors.Scrim),
        contentAlignment = Alignment.Center,
    ) {
        if (onDismiss != null) {
            BuroInteractiveRow(
                onClick = onDismiss,
                selected = false,
                shape = CircleShape,
                contentDescription = strings.close,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(BuroSpacing.Lg),
            ) {
                Box(
                    modifier = Modifier.size(44.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "✕",
                        color = BuroColors.TextMuted,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
            }
        }
        Column(
            modifier = Modifier.widthIn(max = 820.dp).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("IPTV BURO", color = BuroColors.Primary, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(12.dp))
            Text(strings.whoIsWatching, color = BuroColors.Text, style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                profiles.forEach { profile ->
                    BuroInteractiveSurface(
                        onClick = { onSelect(profile.id) },
                        modifier = Modifier.width(130.dp),
                        shape = BuroRadius.Large,
                        background = BuroColors.SurfaceRaised,
                        activeBackground = BuroColors.SurfaceHover,
                        contentDescription = profile.name,
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = BuroSpacing.Md),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            ProfileFace(
                                avatarIndex = profile.avatarIndex,
                                photo = photoFor(profile.id),
                                size = 64.dp,
                            )
                            Spacer(Modifier.height(BuroSpacing.Xs))
                            Text(
                                text = profile.name,
                                color = BuroColors.Text,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (profile.isKids) {
                                Text(
                                    text = "KIDS",
                                    color = BuroColors.Primary,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            Spacer(Modifier.height(BuroSpacing.Xs))
                            // Stacked, not side by side.
                            //
                            // The tile is 130dp wide. Two buttons in a row left each about 55dp, so
                            // "Confirmar?" wrapped onto three lines and stretched the tile out of
                            // shape. One per line gives each the full width and keeps every tile the
                            // same height whatever state it is in.
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                // Editing is always available, including for the last profile: a
                                // household with one profile is exactly who needs to change its
                                // playlist without being made to create a second one first.
                                TextButton(
                                    onClick = { onEditProfile(profile.id) },
                                    contentPadding = PaddingValues(horizontal = BuroSpacing.Xs, vertical = 2.dp),
                                ) {
                                    Text(
                                        text = "✎ ${strings.settingsText.profileEdit}",
                                        color = BuroColors.TextMuted,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                    )
                                }

                                // Removal, unlike editing, needs more than one profile: taking the
                                // last one away would leave this gate with nothing to choose. Two
                                // taps, because deleting a profile discards its favourites.
                                if (profiles.size > 1) {
                                    TextButton(
                                        onClick = {
                                            if (confirmingDelete == profile.id) {
                                                onDelete(profile.id)
                                                confirmingDelete = null
                                            } else {
                                                confirmingDelete = profile.id
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = BuroSpacing.Xs, vertical = 2.dp),
                                    ) {
                                        Text(
                                            text =
                                                if (confirmingDelete == profile.id) {
                                                    strings.confirmRemoveProfile
                                                } else {
                                                    strings.removeProfile
                                                },
                                            color =
                                                if (confirmingDelete == profile.id) {
                                                    BuroColors.Error
                                                } else {
                                                    BuroColors.TextSubtle
                                                },
                                            style = MaterialTheme.typography.labelSmall,
                                            // One line always. The confirmation is longer than the
                                            // label it replaces, and wrapping it is what deformed
                                            // the tile.
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
            if (profiles.size < 5) {
                Spacer(Modifier.height(24.dp))
                // Avatar is chosen before the name so the row reads left to right as one action.
                // Wrapped rather than in one line: sixteen circles overflow the gate's width.
                FlowRow(
                    modifier = Modifier.width(360.dp),
                    horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Xs),
                    verticalArrangement = Arrangement.spacedBy(BuroSpacing.Xs),
                ) {
                    BURO_AVATARS.forEachIndexed { index, option ->
                        BuroInteractiveRow(
                            onClick = { avatar = index },
                            selected = avatar == index,
                            shape = CircleShape,
                            contentDescription = option.id,
                        ) {
                            Box(
                                modifier = Modifier.size(44.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                BuroProfileAvatar(index = index, size = 38.dp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(BuroSpacing.Sm))
                // Adding a profile goes through the account step, where a playlist can be reused or
                // a new one added. The gate used to create a name-only profile, which then had no
                // playlist of its own and no way to be given one.
                Button(onClick = onAddProfile) { Text(strings.addProfile) }
            }

            Spacer(Modifier.height(BuroSpacing.Xl))
            // Reset is deliberately two-step. It erases profiles, favourites and progress, and
            // there is no undo.
            if (confirmingReset) {
                Text(
                    text = strings.resetWarning,
                    color = BuroColors.TextMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(BuroSpacing.Xs))
                Row(horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Sm)) {
                    TextButton(onClick = { confirmingReset = false }) { Text(strings.cancel) }
                    Button(
                        onClick = {
                            confirmingReset = false
                            onReset()
                        },
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = BuroColors.Error,
                                contentColor = BuroColors.OnPrimary,
                            ),
                    ) { Text(strings.resetConfirm, fontWeight = FontWeight.Bold) }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { showingDeviceCode = true }) {
                        Text(
                            text = strings.shareStrings.screens.deviceCodeAction,
                            color = BuroColors.TextSubtle,
                        )
                    }
                    TextButton(onClick = { confirmingReset = true }) {
                        Text(strings.resetSettings, color = BuroColors.TextSubtle)
                    }
                }
            }
        }

        if (showingDeviceCode) {
            DeviceCodeDialog(deviceCode) { showingDeviceCode = false }
        }
    }
}

/**
 * This machine's code, with a way to copy it.
 *
 * One composable rather than one per screen: it is offered from the profile gate and again from the
 * playlist form, and two copies would drift — the likeliest drift being one of them quietly losing
 * the line that says what the code is for, which is the half that makes it useful.
 */
@Composable
private fun DeviceCodeDialog(
    deviceCode: String,
    onDismiss: () -> Unit,
) {
    var copied by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    copyToClipboard(deviceCode)
                    copied = true
                },
            ) {
                Text(
                    text = if (copied) strings.licenseText.copied else "⧉",
                    color = if (copied) BuroColors.Success else BuroColors.Primary,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.understood) }
        },
        title = { Text(strings.shareStrings.screens.deviceCodeAction) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = deviceCode,
                    color = BuroColors.Primary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.height(BuroSpacing.Sm))
                // Without this the code is a string of characters that means nothing to whoever
                // is reading it off the screen.
                Text(
                    text = strings.shareStrings.screens.deviceCodeHelp,
                    color = BuroColors.TextSubtle,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        },
    )
}

/**
 * First-run language step.
 *
 * Each option is written in its own language rather than translated into the current one, because
 * the user cannot read the current one yet — that is the whole reason this screen exists.
 */
@Composable
private fun LanguageSetupGate(
    current: DesktopLanguage,
    onSelect: (DesktopLanguage) -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().background(BuroColors.Canvas),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 560.dp).padding(BuroSpacing.Xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "IPTV BURO",
                color = BuroColors.Primary,
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(Modifier.height(BuroSpacing.Sm))
            Text(
                text = "Idioma · Language · Sprache · Lingua",
                color = BuroColors.Text,
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(BuroSpacing.Xl))
            DesktopLanguage.entries.forEach { option ->
                BuroInteractiveRow(
                    onClick = { onSelect(option) },
                    selected = option == current,
                    modifier = Modifier.fillMaxWidth(),
                    shape = BuroRadius.Medium,
                    contentDescription = option.nativeName(),
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = BuroSpacing.Lg, vertical = BuroSpacing.Md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = option.tag.substringBefore('-').uppercase(),
                            color = BuroColors.Primary,
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Spacer(Modifier.width(BuroSpacing.Md))
                        Text(
                            text = option.nativeName(),
                            color = BuroColors.Text,
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                }
                Spacer(Modifier.height(BuroSpacing.Xs))
            }
        }
    }
}

private fun DesktopLanguage.nativeName(): String =
    when (this) {
        DesktopLanguage.PORTUGUESE_BRAZIL -> "Português (Brasil)"
        DesktopLanguage.ENGLISH -> "English"
        DesktopLanguage.SPANISH -> "Español"
        DesktopLanguage.GERMAN -> "Deutsch"
        DesktopLanguage.ITALIAN -> "Italiano"
    }

/**
 * One download in the sidebar list.
 *
 * Progress is shown as a bar rather than a spinner because a film download runs for minutes and a
 * spinner conveys nothing about whether it is advancing.
 */
@Composable
private fun DownloadRow(
    entry: DownloadEntry,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(BuroRadius.Small)
            .background(BuroColors.SurfaceRaised)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = entry.title,
                color = BuroColors.Text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            val state = entry.state
            if (state is DownloadState.Running) {
                Text(
                    text = "✕",
                    color = BuroColors.TextSubtle,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.clickable(onClick = onCancel).padding(start = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        when (val state = entry.state) {
            is DownloadState.Running -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(BuroColors.Canvas),
                ) {
                    // A negative fraction means the server sent no length; an indeterminate bar
                    // would be a lie about progress, so the bar is left empty and the label says so.
                    if (state.fraction >= 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(state.fraction.coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .background(BuroColors.Primary),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = downloadProgressLabel(state, strings.downloadInProgress),
                    color = BuroColors.TextSubtle,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            DownloadState.Completed ->
                Text(
                    text = "✓ ${strings.downloaded}",
                    color = BuroColors.Success,
                    style = MaterialTheme.typography.bodySmall,
                )
            DownloadState.Failed ->
                Text(
                    text = strings.downloadFailed,
                    color = BuroColors.Error,
                    style = MaterialTheme.typography.bodySmall,
                )
            DownloadState.Idle ->
                Text(
                    text = strings.downloadPaused,
                    color = BuroColors.TextSubtle,
                    style = MaterialTheme.typography.bodySmall,
                )
        }
    }
}

/** Which kind of download to list. */
private enum class DownloadFilter {
    ALL,
    MOVIES,
    SERIES,
}

/**
 * How tightly the download rows are packed.
 *
 * Two rather than the catalogue's three: a download row carries a progress bar, a cancel and a
 * delete, so it cannot collapse into a poster tile with nowhere to put them. Compact is the same
 * row with less air and smaller artwork — enough to see a finished season at a glance.
 */
private enum class DownloadDensity {
    COMFORTABLE,
    COMPACT,
}

/**
 * How many downloads before a search box earns its place.
 *
 * Below this the list fits on screen and a search field is furniture; above it, finding one episode
 * among a season means scrolling past everything else.
 */
private const val SEARCHABLE_DOWNLOAD_COUNT = 8

/**
 * The rate tracker's key for the installer download.
 *
 * A constant because there is only ever one update in flight, and the tracker keys by string so it
 * can measure several content downloads at once.
 */
private const val UPDATE_RATE_KEY = "app-update"

/**
 * Offline library.
 *
 * The point of downloading is watching without the provider, so every completed row plays straight
 * from disk and never touches the network. Before this screen existed a download reached 100% and
 * then did nothing: the file was on disk with no way to reach it.
 */
@Composable
private fun DownloadsWorkspace(
    entries: List<DownloadEntry>,
    onPlay: (String) -> Unit,
    onCancel: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val text = strings
    Column(modifier = Modifier.fillMaxSize().padding(BuroSpacing.Lg)) {
        Text(
            text = text.downloads,
            color = BuroColors.Text,
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(BuroSpacing.Md))

        if (entries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = text.downloadsEmptyTitle,
                        color = BuroColors.Text,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.height(BuroSpacing.Xs))
                    Text(
                        text = text.downloadsEmptyBody,
                        color = BuroColors.TextSubtle,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            return@Column
        }

        // Films apart from series, and a way to find one title among many.
        //
        // A download list grows without ever being tidied — every episode of a series lands beside
        // every film — and past a screenful it stops being somewhere you can find anything.
        var filter by remember { mutableStateOf(DownloadFilter.ALL) }
        var query by remember { mutableStateOf("") }
        var density by remember { mutableStateOf(DownloadDensity.COMFORTABLE) }

        // The kind is read off the content key, which is `movie:…` or `series:…` by construction.
        // Derived rather than stored: a second field would be one more thing to keep in step with
        // the identity that already carries the answer.
        val kindOf = { entry: DownloadEntry -> entry.contentKey.substringBefore(':') }
        val kinds = entries.map(kindOf).toSet()

        val visible =
            entries.filter { entry ->
                val matchesKind =
                    when (filter) {
                        DownloadFilter.ALL -> true
                        DownloadFilter.MOVIES -> kindOf(entry) == "movie"
                        DownloadFilter.SERIES -> kindOf(entry) == "series"
                    }
                matchesKind && (query.isBlank() || entry.title.contains(query.trim(), ignoreCase = true))
            }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Always shown, not only when the list already holds both kinds.
            //
            // Hiding it behind a mix was a misjudgement: a customer with four films sees no control
            // at all, cannot tell the feature exists, and reasonably reports it missing. A selector
            // that is present and shows the same list twice costs nothing; one that appears only
            // under a condition the user cannot see reads as a broken build.
            BuroSegmentedControl(
                options = DownloadFilter.entries,
                selected = filter,
                label = { option ->
                    when (option) {
                        DownloadFilter.ALL -> text.allItems
                        DownloadFilter.MOVIES -> text.movies
                        DownloadFilter.SERIES -> text.series
                    }
                },
                onSelect = { chosen -> filter = chosen },
            )
            Spacer(Modifier.width(BuroSpacing.Md))

            // Always offered, for the same reason as the selector above. A search box that appears
            // only past some threshold is a control the user has to discover by accident.
            OutlinedTextField(
                value = query,
                onValueChange = { value -> query = value },
                modifier = Modifier.weight(1f).widthIn(max = 420.dp),
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
            Spacer(Modifier.width(BuroSpacing.Md))

            // Roomy rows or tight ones, the same choice the catalogue offers.
            //
            // A download row carries a progress bar and two buttons, so it cannot become a wall of
            // posters — compact here means the same row with less air and smaller artwork, which is
            // what makes a finished season scannable rather than a page of scrolling.
            BuroSegmentedControl(
                options = listOf(DownloadDensity.COMFORTABLE, DownloadDensity.COMPACT),
                selected = density,
                label = { option ->
                    when (option) {
                        DownloadDensity.COMFORTABLE -> text.layoutList
                        DownloadDensity.COMPACT -> text.layoutCompact
                    }
                },
                onSelect = { chosen -> density = chosen },
            )
        }
        Spacer(Modifier.height(BuroSpacing.Md))

        // A filter that matches nothing says so, rather than showing an empty screen that looks
        // like the downloads were lost.
        if (visible.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = text.downloadsNoMatch,
                    color = BuroColors.TextSubtle,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            return@Column
        }

        // Weighted so the list scrolls within the remaining space. Unweighted, a Column gives its
        // child unbounded height and the lazy list lays every row out at once, running past the
        // window instead of scrolling.
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(BuroSpacing.Xs),
        ) {
            items(visible, key = { it.contentKey }) { entry ->
                DownloadLibraryRow(
                    entry = entry,
                    onPlay = { onPlay(entry.contentKey) },
                    onCancel = { onCancel(entry.contentKey) },
                    onDelete = { onDelete(entry.contentKey) },
                    compact = density == DownloadDensity.COMPACT,
                )
            }
        }
    }
}

@Composable
private fun DownloadLibraryRow(
    entry: DownloadEntry,
    onPlay: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    /**
     * Tighter rows, for scanning a long list.
     *
     * The controls stay: a download row has a progress bar, a cancel and a delete, and a density
     * that removed them would be a different screen rather than a denser one. Only the padding and
     * the artwork shrink.
     */
    compact: Boolean = false,
) {
    val text = strings
    val stored = entry.state == DownloadState.Completed

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(BuroRadius.Medium)
            .background(BuroColors.Surface)
            .padding(if (compact) BuroSpacing.Xs else BuroSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BuroRemoteArtwork(
            artworkUrl = entry.artworkUrl,
            contentDescription = entry.title,
            modifier =
                Modifier
                    .width(if (compact) 34.dp else 54.dp)
                    .aspectRatio(2f / 3f)
                    .clip(BuroRadius.Small)
                    .background(BuroColors.SurfaceRaised),
            contentScale = ContentScale.Crop,
        ) {
            // A download made before the sidecar existed has no poster; the row still needs to read
            // as a title rather than as an empty box.
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = entry.title.take(1).uppercase(),
                    color = BuroColors.TextSubtle,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        Spacer(Modifier.width(BuroSpacing.Md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                color = BuroColors.Text,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            when (val state = entry.state) {
                is DownloadState.Running -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(BuroColors.Canvas),
                    ) {
                        // A negative fraction means the server gave no length. Leaving the bar
                        // empty is honest; an indeterminate animation would imply measured progress.
                        if (state.fraction >= 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(state.fraction.coerceIn(0f, 1f))
                                    .fillMaxHeight()
                                    .clip(CircleShape)
                                    .background(BuroColors.Primary),
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = downloadProgressLabel(state, text.downloadInProgress),
                        color = BuroColors.TextSubtle,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                DownloadState.Completed ->
                    Text(
                        text = "✓ ${text.downloaded}",
                        color = BuroColors.Success,
                        style = MaterialTheme.typography.bodySmall,
                    )
                DownloadState.Failed ->
                    Text(
                        text = text.downloadFailed,
                        color = BuroColors.Error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                DownloadState.Idle ->
                    Text(
                        text = text.downloadPaused,
                        color = BuroColors.TextSubtle,
                        style = MaterialTheme.typography.bodySmall,
                    )
            }
        }

        Spacer(Modifier.width(BuroSpacing.Md))
        if (stored) {
            Button(
                onClick = onPlay,
                shape = BuroRadius.Small,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = BuroColors.Primary,
                        contentColor = BuroColors.OnPrimary,
                    ),
            ) {
                Text("▶  ${text.watchNow}", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(BuroSpacing.Xs))
            OutlinedButton(
                onClick = onDelete,
                shape = BuroRadius.Small,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = BuroColors.TextMuted),
            ) {
                Text(text.removeDownload, maxLines = 1)
            }
        } else if (entry.state is DownloadState.Running) {
            OutlinedButton(
                onClick = onCancel,
                shape = BuroRadius.Small,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = BuroColors.TextMuted),
            ) {
                Text(text.cancel, maxLines = 1)
            }
        } else {
            OutlinedButton(
                onClick = onDelete,
                shape = BuroRadius.Small,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = BuroColors.TextMuted),
            ) {
                Text(text.removeDownload, maxLines = 1)
            }
        }
    }
}

/**
 * Update progress and restart prompt.
 *
 * Previously the app called exitApplication the moment the installer launched, so the window
 * vanished with no warning and no visible progress — indistinguishable from a crash. The installer
 * still needs the app closed to replace its files, but the user decides when.
 */
@Composable
private fun UpdateOverlay(
    release: DesktopRelease,
    progress: Float,
    /** Current speed, or zero before the first measurable interval. */
    bytesPerSecond: Long,
    /** Seconds left at the current speed, or null while it cannot honestly be known. */
    secondsRemaining: Long?,
    readyToRestart: Boolean,
    onRestart: () -> Unit,
    onDismiss: () -> Unit,
) {
    val text = strings
    Box(
        modifier = Modifier.fillMaxSize().background(BuroColors.Scrim),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .clip(BuroRadius.Large)
                .background(BuroColors.SurfaceRaised)
                .border(1.dp, BuroColors.BorderSoft, BuroRadius.Large)
                .padding(BuroSpacing.Xl),
        ) {
            Text(
                text = release.displayName,
                color = BuroColors.Text,
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(BuroSpacing.Md))

            if (readyToRestart) {
                Text(
                    text = text.updateReadyBody,
                    color = BuroColors.TextMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(BuroSpacing.Lg))
                Row(horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Sm)) {
                    Button(
                        onClick = onRestart,
                        shape = BuroRadius.Small,
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = BuroColors.Primary,
                                contentColor = BuroColors.OnPrimary,
                            ),
                    ) {
                        Text(text.updateRestartNow, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = onDismiss) { Text(text.updateLater) }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(BuroColors.Canvas),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(BuroColors.Primary),
                    )
                }
                Spacer(Modifier.height(BuroSpacing.Xs))
                // The size in megabytes as well as the percentage.
                //
                // This is a ~290 MB download: on a slow line a bar that moves a percent a minute
                // is indistinguishable from one that has stalled, and "45% de 286 MB" is what tells
                // the user it is working and roughly how much longer it will take.
                val totalMegabytes = release.sizeBytes / 1_048_576.0
                val doneMegabytes = totalMegabytes * progress.coerceIn(0f, 1f)
                // Speed and remaining time join the line once they mean something. Both are
                // omitted rather than shown as a placeholder: an estimate from the first moments of
                // a transfer swings between seconds and hours, and a number that jumps like that is
                // worse than no number at all. This is the same rule the download list follows.
                val progressLine =
                    buildList {
                        add(
                            if (release.sizeBytes > 0) {
                                "${text.downloading}  ${(progress * 100).toInt()}%  " +
                                    "(${"%.0f".format(DISPLAY_LOCALE, doneMegabytes)} / ${"%.0f".format(DISPLAY_LOCALE, totalMegabytes)} MB)"
                            } else {
                                "${text.downloading}  ${(progress * 100).toInt()}%"
                            },
                        )
                        if (bytesPerSecond > 0L) add(formatRate(bytesPerSecond))
                        secondsRemaining?.takeIf { it > 0L }?.let { add(formatDuration(it)) }
                    }.joinToString("  ·  ")
                Text(
                    text = progressLine,
                    color = BuroColors.TextSubtle,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

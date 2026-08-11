package com.lucasserafin94.iptvburo.desktop.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.awt.Frame
import com.lucasserafin94.iptvburo.desktop.DesktopAppState
import com.lucasserafin94.iptvburo.desktop.DesktopDestination
import com.lucasserafin94.iptvburo.desktop.OnboardingStep
import com.lucasserafin94.iptvburo.desktop.security.XtreamLoginInput
import com.lucasserafin94.iptvburo.desktop.DownloadEntry
import com.lucasserafin94.iptvburo.desktop.DownloadState
import com.lucasserafin94.iptvburo.desktop.ImportStatus
import com.lucasserafin94.iptvburo.desktop.XtreamStatus
import com.lucasserafin94.iptvburo.desktop.model.DesktopSourceKind
import com.lucasserafin94.iptvburo.desktop.model.DesktopSourceSummary
import com.lucasserafin94.iptvburo.desktop.model.PlaybackReadiness
import com.lucasserafin94.iptvburo.desktop.model.playbackReadiness
import com.lucasserafin94.iptvburo.desktop.platform.ExternalOpenResult
import com.lucasserafin94.iptvburo.desktop.platform.DesktopPlatformCapabilities
import com.lucasserafin94.iptvburo.desktop.platform.chooseLocalPlaylist
import com.lucasserafin94.iptvburo.desktop.platform.chooseM3uFile
import com.lucasserafin94.iptvburo.desktop.platform.openChannelExternally
import com.lucasserafin94.iptvburo.desktop.license.LicenseStatus
import com.lucasserafin94.iptvburo.desktop.playback.DesktopPlaybackRequest
import com.lucasserafin94.iptvburo.desktop.playback.MultiviewOverlay
import com.lucasserafin94.iptvburo.desktop.playback.DesktopPlayerOverlay
import com.lucasserafin94.iptvburo.desktop.ui.BuroColors
import com.lucasserafin94.iptvburo.desktop.ui.BuroMark
import com.lucasserafin94.iptvburo.desktop.ui.BuroDesktopTheme
import com.lucasserafin94.iptvburo.desktop.ui.BuroInteractiveRow
import com.lucasserafin94.iptvburo.desktop.ui.BuroInteractiveSurface
import com.lucasserafin94.iptvburo.desktop.ui.BuroRadius
import com.lucasserafin94.iptvburo.desktop.ui.BuroRemoteArtwork
import com.lucasserafin94.iptvburo.desktop.ui.BuroSegmentedControl
import com.lucasserafin94.iptvburo.desktop.ui.BuroSpacing
import com.lucasserafin94.iptvburo.desktop.ui.DesktopStrings
import com.lucasserafin94.iptvburo.desktop.ui.LocalDesktopStrings
import com.lucasserafin94.iptvburo.desktop.ui.strings
import com.lucasserafin94.iptvburo.metadata.TmdbStreamingCatalogue
import com.lucasserafin94.iptvburo.desktop.download.formatDuration
import com.lucasserafin94.iptvburo.desktop.download.formatRate
import com.lucasserafin94.iptvburo.desktop.platform.openStreamingOfferExternally
import com.lucasserafin94.iptvburo.domain.model.Category
import com.lucasserafin94.iptvburo.domain.model.Channel
import com.lucasserafin94.iptvburo.domain.model.ExternalTitleDetails
import com.lucasserafin94.iptvburo.domain.model.OfferType
import com.lucasserafin94.iptvburo.domain.model.ProviderDeepLinks
import com.lucasserafin94.iptvburo.domain.model.StreamingOffer
import com.lucasserafin94.iptvburo.xtream.XtreamContentType
import com.lucasserafin94.iptvburo.desktop.user.DesktopLanguage
import com.lucasserafin94.iptvburo.desktop.user.DesktopProfile
import com.lucasserafin94.iptvburo.desktop.platform.chooseImageFile
import com.lucasserafin94.iptvburo.desktop.ui.BURO_AVATARS
import com.lucasserafin94.iptvburo.desktop.ui.BuroProfileAvatar
import com.lucasserafin94.iptvburo.desktop.update.DESKTOP_VERSION
import com.lucasserafin94.iptvburo.desktop.platform.openUriExternally
import com.lucasserafin94.iptvburo.desktop.update.DesktopRelease
import com.lucasserafin94.iptvburo.desktop.update.GitHubReleaseUpdater
import com.lucasserafin94.iptvburo.desktop.update.UpdateCheckResult
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
    var externalOpenResult by remember { mutableStateOf<ExternalOpenResult?>(null) }
    var activePlayback by remember { mutableStateOf<DesktopPlaybackRequest?>(null) }
    var showXtreamLogin by remember { mutableStateOf(false) }
    var parentalOpen by remember { mutableStateOf(false) }

    /** Whether the TMDb key walkthrough is showing, over the settings window. */
    var tmdbGuideOpen by remember { mutableStateOf(false) }
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
    val releaseUpdater = remember { GitHubReleaseUpdater() }
    var updateBusy by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf<String?>(null) }
    var updateProgress by remember { mutableStateOf(0f) }
    var updateRelease by remember { mutableStateOf<DesktopRelease?>(null) }
    var updateReadyToRestart by remember { mutableStateOf(false) }
    // Owned here rather than inside the setup screen, which is replaced by the connecting and
    // failure screens: state held there was discarded, so a wrong password emptied the whole form
    // and hid which field was actually wrong.
    val setupDraft = remember { AccountSetupDraft() }

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
    }

    BuroDesktopTheme {
        CompositionLocalProvider(
            LocalDesktopStrings provides DesktopStrings.of(appState.language),
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
                onQuit = onExitForUpdate,
                languageTag = appState.language.tag,
                backdropPosters = appState.backdropPosters,
            )
            return@CompositionLocalProvider
        }

        val text = strings
        Surface(
            modifier =
                Modifier
                    .fillMaxSize()
                    .onPreviewKeyEvent { event ->
                        if (
                            !appState.isXtreamSelected ||
                            event.type != KeyEventType.KeyDown ||
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
                    SourceSidebar(
                        sources = appState.sourceSummaries,
                        selectedSourceId = appState.selectedSourceId,
                        onSourceSelected = appState::selectSource,
                        destination = visibleDestination,
                        catalogType = appState.xtreamContentType,
                        downloads = if (capabilities.offlineSupported) appState.downloadEntries else emptyList(),
                        onCancelDownload = appState::cancelDownload,
                        onHome = appState::openHome,
                        onMovies = {
                            scope.launch { appState.openCatalog(XtreamContentType.MOVIE) }
                        },
                        onSeries = {
                            scope.launch { appState.openCatalog(XtreamContentType.SERIES) }
                        },
                        onLive = {
                            scope.launch { appState.openCatalog(XtreamContentType.LIVE) }
                        },
                        onFavorites = { scope.launch { appState.setFavoritesOnly(true) } },
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
                            metadataApiKey = appState.metadataApiKey,
                            onMetadataApiKeyChange = appState::updateMetadataApiKey,
                            streamingRegion = appState.streamingRegion,
                            onSelectRegion = appState::changeStreamingRegion,
                            onOpenParental = { parentalOpen = true },
                            uses24HourClock = appState.uses24HourClock,
                            licenseStatus = appState.licenseStatus,
                            onOpenPurchase = { showLicenseDetails = true },
                            onUpdate = {
                                if (!updateBusy) {
                                    scope.launch {
                                        updateBusy = true
                                        updateMessage = text.checkingUpdate
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
                                                updateProgress = 0f
                                                updateRelease = result.release
                                                // Progress is reported so a multi-hundred-megabyte
                                                // download does not look like a hang.
                                                releaseUpdater
                                                    .downloadAndLaunch(result.release) { fraction ->
                                                        updateProgress = fraction
                                                    }.onSuccess {
                                                        // The installer is running; the app must
                                                        // close for it to replace these files. The
                                                        // user presses the button when ready rather
                                                        // than having the window vanish under them.
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
                            },
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
                                page = appState.streamingPage,
                                onOpenTrailerExternally = { id -> appState.openPublicTrailer(id) },
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
                        savedSources = appState.savedSources(),
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
                    SplashScreen(
                        message = appState.startupMessage.ifBlank { text.loadingCatalog },
                        progress = appState.startupProgress,
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

            if (showXtreamLogin) {
                XtreamLoginDialog(
                    onDismiss = { showXtreamLogin = false },
                    onConnect = { input ->
                        scope.launch { appState.connectXtream(input) }
                    },
                )
            }

            if (parentalOpen) {
                SettingsDialog(
                    appState = appState,
                    onDismiss = { parentalOpen = false },
                    updateBusy = updateBusy,
                    onUpdate = {
                        if (!updateBusy) {
                            scope.launch {
                                updateBusy = true
                                updateMessage = text.checkingUpdate
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
                                        updateRelease = result.release
                                        updateBusy = false
                                    }
                                }
                            }
                        }
                    },
                    sessionActive = appState.isXtreamSelected,
                    onEndSession = appState::disconnectXtream,
                    catalogRefreshing = appState.xtreamStatus is XtreamStatus.LoadingCatalog,
                    onRefreshCatalog = { scope.launch { appState.refreshCatalog() } },
                    onOpenTmdbSettings = { openUriExternally(java.net.URI(TMDB_API_SETTINGS_URL)) },
                    onOpenTmdbGuide = { tmdbGuideOpen = true },
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
                        title = { Text("Não foi possível abrir") },
                        text = {
                            Text(
                                if (result == ExternalOpenResult.NotSupported) {
                                    "Este sistema não oferece um aplicativo padrão para abrir o canal."
                                } else {
                                    "O aplicativo externo recusou o endereço. Nenhum dado foi copiado."
                                },
                            )
                        },
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
    onMovies: () -> Unit,
    onSeries: () -> Unit,
    onLive: () -> Unit,
    onFavorites: () -> Unit,
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

    // Collapsed: a narrow strip carrying the name vertically and the control to bring it back. The
    // content beside it gets the width, which on a shelf of posters is another card and a half.
    if (collapsed) {
        CollapsedSidebar(onExpand = onToggleCollapsed)
        return
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
        Spacer(Modifier.height(30.dp))
        SectionLabel(text.library)
        Spacer(Modifier.height(10.dp))
        NavigationItem(
            label = text.home,
            selected = destination == DesktopDestination.HOME,
            onClick = onHome,
        )
        NavigationItem(
            label = text.movies,
            selected =
                destination == DesktopDestination.CATALOG &&
                    catalogType == XtreamContentType.MOVIE,
            onClick = onMovies,
        )
        NavigationItem(
            label = text.series,
            selected =
                destination == DesktopDestination.CATALOG &&
                    catalogType == XtreamContentType.SERIES,
            onClick = onSeries,
        )
        NavigationItem(
            label = text.live,
            selected =
                destination == DesktopDestination.CATALOG &&
                    catalogType == XtreamContentType.LIVE,
            onClick = onLive,
        )
        NavigationItem(
            label = text.continueWatching,
            selected = destination == DesktopDestination.CONTINUE,
            onClick = onContinueWatching,
        )
        NavigationItem(
            label = text.settingsText.historyTitle,
            selected = destination == DesktopDestination.HISTORY,
            onClick = onHistory,
        )
        NavigationItem(
            label = text.favorites,
            selected = destination == DesktopDestination.FAVORITES,
            onClick = onFavorites,
        )
        if (hasOffline) {
            NavigationItem(
                label = text.downloads,
                selected = destination == DesktopDestination.DOWNLOADS,
                onClick = onDownloads,
            )
        }
        // Last, after Downloads, and only when the user actually supplied a music playlist. An
        // entry leading to an empty section is worse than no entry at all.
        if (hasMusic) {
            NavigationItem(
                label = text.music,
                selected = destination == DesktopDestination.MUSIC,
                onClick = onMusic,
            )
        }
        // Only while something can actually answer "where can I watch this" — currently the demo
        // catalogue. With nothing behind it the entry disappears rather than opening a dead screen.
        if (hasSubscriptions) {
            NavigationItem(
                label = text.subscriptions,
                selected = destination == DesktopDestination.SUBSCRIPTIONS,
                onClick = onSubscriptions,
            )
        }

        // The source list used to own the whole remaining column even with one source. It is a
        // rarely-used switch, so it now takes only the height it needs and the section disappears
        // entirely when there is nothing to switch between.
        if (sources.size > 1) {
            Spacer(Modifier.height(28.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SectionLabel(text.sources)
                Text(
                    text = sources.size.toString(),
                    color = BuroColors.TextSubtle,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier.heightIn(max = 220.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(sources, key = DesktopSourceSummary::id) { source ->
                    SourceItem(
                        source = source,
                        selected = source.id == selectedSourceId,
                        onClick = { onSourceSelected(source.id) },
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))
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
) {
    BuroInteractiveRow(
        onClick = onClick,
        selected = selected,
        modifier = Modifier.fillMaxWidth(),
        contentDescription = label,
    ) { state ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            selected -> BuroColors.Primary
                            state.active -> BuroColors.TextMuted
                            else -> BuroColors.TextSubtle
                        },
                    ),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = label,
                color = if (selected || state.active) BuroColors.Text else BuroColors.TextMuted,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun SourceItem(
    source: DesktopSourceSummary,
    selected: Boolean,
    onClick: () -> Unit,
) {
    BuroInteractiveRow(
        onClick = onClick,
        selected = selected,
        modifier = Modifier.fillMaxWidth(),
        contentDescription = source.name,
    ) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 10.dp),
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
            Text(
                "${source.itemCount} ${strings.items}",
                color = BuroColors.TextSubtle,
                style = MaterialTheme.typography.bodyMedium,
            )
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
    metadataApiKey: String,
    onMetadataApiKeyChange: (String) -> Unit,
    streamingRegion: String,
    onSelectRegion: (String) -> Unit,
    onOpenParental: () -> Unit,
    uses24HourClock: Boolean,
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
                }
                // Version and counts share one quiet line so the header carries a single strong
                // element instead of five competing pills. The full version is shown, including the
                // pre-release suffix: "v0.2.0" for a 0.2.0-alpha.5 build made bug reports ambiguous.
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
            // Profile first, then a single settings entry. Four loose language buttons plus an
            // update button plus two status pills made the header read as a debug toolbar; the
            // language belongs inside settings, where a user looks for it once and never again.
            // Before the profile chip: the eye lands on the right of a header looking for status,
            // and the time is the thing most often wanted there.
            HeaderClock(uses24Hour = uses24HourClock, languageTag = language.tag)
            Spacer(Modifier.width(BuroSpacing.Md))
            if (showProfile) {
                ProfileChip(
                    name = activeProfile?.name ?: text.profile,
                    avatarIndex = activeProfile?.avatarIndex ?: 0,
                    photo = activeProfilePhoto,
                    onClick = onChangeProfile,
                )
                Spacer(Modifier.width(BuroSpacing.Sm))
            }
            // Opens the settings dialog directly. There used to be a DropdownMenu here holding
            // half the settings, with the other half behind an item inside it — and a
            // DropdownMenu scrolls its content without ever drawing a scrollbar, so everything
            // below its fold was reported as missing. One screen, one scrollbar.
            BuroInteractiveRow(
                onClick = onOpenParental,
                selected = false,
                shape = CircleShape,
                contentDescription = text.settings,
            ) {
                Box(modifier = Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = "⚙",
                        color = BuroColors.TextMuted,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
            }
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
                "Nenhum canal corresponde ao filtro.",
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
                "O canal exige cabeçalhos HTTP; um navegador comum pode não reproduzi-lo."
            } else {
                "Endereço válido para um aplicativo externo."
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
                    "Este canal exige cabeçalhos HTTP que o player Windows atual ainda não consegue aplicar. A reprodução foi desativada para não apresentar um botão que falhará."
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
) {
    var newName by remember { mutableStateOf("") }
    var kids by remember { mutableStateOf(false) }
    var avatar by remember { mutableStateOf(0) }
    var confirmingReset by remember { mutableStateOf(false) }
    // Which profile is one tap away from being removed. Held here so tapping a second profile
    // cancels the first, rather than arming two at once.
    var confirmingDelete by remember { mutableStateOf<String?>(null) }
    Box(
        modifier = Modifier.fillMaxSize().background(BuroColors.Scrim),
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
                TextButton(onClick = { confirmingReset = true }) {
                    Text(strings.resetSettings, color = BuroColors.TextSubtle)
                }
            }
        }
    }
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
                Text(
                    text = "${text.downloading}  ${(progress * 100).toInt()}%",
                    color = BuroColors.TextSubtle,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

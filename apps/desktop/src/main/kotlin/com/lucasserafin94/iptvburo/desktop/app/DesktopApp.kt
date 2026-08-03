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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
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
import java.awt.Frame
import com.lucasserafin94.iptvburo.desktop.DesktopAppState
import com.lucasserafin94.iptvburo.desktop.DesktopDestination
import com.lucasserafin94.iptvburo.desktop.DownloadEntry
import com.lucasserafin94.iptvburo.desktop.DownloadState
import com.lucasserafin94.iptvburo.desktop.ImportStatus
import com.lucasserafin94.iptvburo.desktop.XtreamStatus
import com.lucasserafin94.iptvburo.desktop.model.DesktopSourceKind
import com.lucasserafin94.iptvburo.desktop.model.DesktopSourceSummary
import com.lucasserafin94.iptvburo.desktop.model.PlaybackReadiness
import com.lucasserafin94.iptvburo.desktop.model.playbackReadiness
import com.lucasserafin94.iptvburo.desktop.platform.ExternalOpenResult
import com.lucasserafin94.iptvburo.desktop.platform.chooseLocalPlaylist
import com.lucasserafin94.iptvburo.desktop.platform.openChannelExternally
import com.lucasserafin94.iptvburo.desktop.playback.DesktopPlaybackRequest
import com.lucasserafin94.iptvburo.desktop.playback.DesktopPlayerOverlay
import com.lucasserafin94.iptvburo.desktop.ui.BuroColors
import com.lucasserafin94.iptvburo.desktop.ui.BuroDesktopTheme
import com.lucasserafin94.iptvburo.desktop.ui.BuroInteractiveRow
import com.lucasserafin94.iptvburo.desktop.ui.BuroInteractiveSurface
import com.lucasserafin94.iptvburo.desktop.ui.BuroRadius
import com.lucasserafin94.iptvburo.desktop.ui.BuroRemoteArtwork
import com.lucasserafin94.iptvburo.desktop.ui.BuroSpacing
import com.lucasserafin94.iptvburo.desktop.ui.DesktopStrings
import com.lucasserafin94.iptvburo.desktop.ui.LocalDesktopStrings
import com.lucasserafin94.iptvburo.desktop.ui.strings
import com.lucasserafin94.iptvburo.domain.model.Category
import com.lucasserafin94.iptvburo.domain.model.Channel
import com.lucasserafin94.iptvburo.xtream.XtreamContentType
import com.lucasserafin94.iptvburo.desktop.user.DesktopLanguage
import com.lucasserafin94.iptvburo.desktop.user.DesktopProfile
import com.lucasserafin94.iptvburo.desktop.user.PROFILE_AVATARS
import com.lucasserafin94.iptvburo.desktop.update.DESKTOP_VERSION
import com.lucasserafin94.iptvburo.desktop.update.DesktopRelease
import com.lucasserafin94.iptvburo.desktop.update.GitHubReleaseUpdater
import com.lucasserafin94.iptvburo.desktop.update.UpdateCheckResult
import kotlinx.coroutines.launch

@Composable
fun DesktopApp(
    appState: DesktopAppState,
    ownerWindow: Frame?,
    isFullScreen: Boolean,
    onToggleFullScreen: () -> Unit,
    onExitForUpdate: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var pendingExternalChannel by remember { mutableStateOf<Channel?>(null) }
    var externalOpenResult by remember { mutableStateOf<ExternalOpenResult?>(null) }
    var activePlayback by remember { mutableStateOf<DesktopPlaybackRequest?>(null) }
    var showXtreamLogin by remember { mutableStateOf(false) }
    var showProfileGate by remember { mutableStateOf(false) }
    val releaseUpdater = remember { GitHubReleaseUpdater() }
    var updateBusy by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf<String?>(null) }
    var updateProgress by remember { mutableStateOf(0f) }
    var updateRelease by remember { mutableStateOf<DesktopRelease?>(null) }
    var updateReadyToRestart by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            pendingExternalChannel = null
            appState.clearSensitiveData()
        }
    }

    BuroDesktopTheme {
        CompositionLocalProvider(
            LocalDesktopStrings provides DesktopStrings.of(appState.language),
        ) {
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
                        destination = appState.destination,
                        catalogType = appState.xtreamContentType,
                        downloads = appState.downloadEntries,
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
                        onDownloads = appState::openDownloads,
                    )
                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        TopBar(
                            channelCount = appState.selectedSourceItemCount,
                            sourceCount = appState.sourceSummaries.size,
                            activeProfile = appState.activeProfile,
                            language = appState.language,
                            // Opening the picker must not clear the active profile. It used to
                            // call selectProfile(null), which left no way back if you opened it
                            // by accident.
                            onChangeProfile = { showProfileGate = true },
                            onSelectLanguage = appState::updateLanguage,
                            updateBusy = updateBusy,
                            updateMessage = updateMessage,
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
                        if (!appState.hasSelectedSource) {
                            EmptyLibrary(
                                onImport = {
                                    chooseLocalPlaylist(ownerWindow)?.let { path ->
                                        scope.launch { appState.importLocalPlaylist(path) }
                                    }
                                },
                                onConnectXtream = { showXtreamLogin = true },
                            )
                        } else if (appState.destination == DesktopDestination.DOWNLOADS) {
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
                        } else if (appState.isXtreamSelected && appState.destination == DesktopDestination.HOME) {
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

                activePlayback?.let { request ->
                    DesktopPlayerOverlay(
                        request = request,
                        onCheckpoint = { positionMs, durationMs ->
                            appState.checkpointPlayback(request, positionMs, durationMs)
                        },
                        onEnded = { durationMs -> appState.completePlayback(request, durationMs) },
                        isFullScreen = isFullScreen,
                        onToggleFullScreen = onToggleFullScreen,
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
                if (appState.needsLanguageSetup) {
                    LanguageSetupGate(
                        current = appState.language,
                        onSelect = appState::updateLanguage,
                    )
                } else if (appState.activeProfile == null || showProfileGate) {
                    DesktopProfileGate(
                        profiles = appState.profiles,
                        onSelect = { profileId ->
                            showProfileGate = false
                            scope.launch { appState.selectProfileAndRefresh(profileId) }
                        },
                        onCreate = appState::createProfile,
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

                AnimatedVisibility(
                    visible =
                        appState.importStatus is ImportStatus.Loading ||
                            appState.xtreamStatus is XtreamStatus.Connecting,
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
    onDownloads: () -> Unit,
) {
    val text = strings
    Column(
        modifier =
            Modifier
                .width(248.dp)
                .fillMaxHeight()
                .background(BuroColors.Surface)
                .padding(horizontal = 18.dp, vertical = 22.dp),
    ) {
        Brand()
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
            label = text.favorites,
            selected = destination == DesktopDestination.FAVORITES,
            onClick = onFavorites,
        )
        NavigationItem(
            label = text.downloads,
            selected = destination == DesktopDestination.DOWNLOADS,
            onClick = onDownloads,
        )

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

@Composable
private fun Brand() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // The real mark rather than a letter in a rounded square. The same asset is the window and
        // taskbar icon, so the app is recognisable by one shape everywhere it appears.
        Image(
            painter = painterResource("brand/buro-mark-512.png"),
            contentDescription = null,
            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Fit,
        )
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
    language: DesktopLanguage,
    onChangeProfile: () -> Unit,
    onSelectLanguage: (DesktopLanguage) -> Unit,
    updateBusy: Boolean,
    updateMessage: String?,
    onUpdate: () -> Unit,
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
                Text(
                    text.yourLibrary,
                    color = BuroColors.Text,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Version and counts share one quiet line so the header carries a single strong
                // element instead of five competing pills.
                Text(
                    updateMessage
                        ?: "$sourceCount ${text.sourcesCount}  ·  $channelCount ${text.items}  ·  " +
                        "v${DESKTOP_VERSION.substringBefore('-')}",
                    color = BuroColors.TextSubtle,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Profile first, then a single settings entry. Four loose language buttons plus an
            // update button plus two status pills made the header read as a debug toolbar; the
            // language belongs inside settings, where a user looks for it once and never again.
            Spacer(Modifier.width(BuroSpacing.Md))
            if (showProfile) {
                ProfileChip(
                    name = activeProfile?.name ?: text.profile,
                    avatar =
                        activeProfile
                            ?.let { PROFILE_AVATARS.getOrNull(it.avatarIndex) }
                            ?: PROFILE_AVATARS.first(),
                    onClick = onChangeProfile,
                )
                Spacer(Modifier.width(BuroSpacing.Sm))
            }
            SettingsMenu(
                language = language,
                onSelectLanguage = onSelectLanguage,
                updateBusy = updateBusy,
                onUpdate = onUpdate,
                text = text,
            )
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
@Composable
private fun SettingsMenu(
    language: DesktopLanguage,
    onSelectLanguage: (DesktopLanguage) -> Unit,
    updateBusy: Boolean,
    onUpdate: () -> Unit,
    text: DesktopStrings,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        BuroInteractiveRow(
            onClick = { expanded = true },
            selected = expanded,
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

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(BuroColors.SurfaceRaised),
        ) {
            Text(
                text = text.languageLabel,
                modifier = Modifier.padding(horizontal = BuroSpacing.Md, vertical = BuroSpacing.Xs),
                color = BuroColors.TextSubtle,
                style = MaterialTheme.typography.labelSmall,
            )
            DesktopLanguage.entries.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.nativeName(),
                            color =
                                if (option == language) BuroColors.Primary else BuroColors.Text,
                        )
                    },
                    trailingIcon = {
                        if (option == language) {
                            Text("✓", color = BuroColors.Primary)
                        }
                    },
                    onClick = {
                        onSelectLanguage(option)
                        expanded = false
                    },
                )
            }
            HorizontalDivider(color = BuroColors.BorderSoft)
            DropdownMenuItem(
                text = {
                    Text(
                        text = if (updateBusy) "…" else text.checkUpdate,
                        color = BuroColors.Text,
                    )
                },
                enabled = !updateBusy,
                onClick = {
                    onUpdate()
                    expanded = false
                },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = "v${DESKTOP_VERSION.substringBefore('-')}",
                        color = BuroColors.TextSubtle,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                enabled = false,
                onClick = {},
            )
        }
    }
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
    avatar: String,
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
            Box(
                modifier =
                    Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(BuroColors.Primary.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                // The avatar the user picked, not an initial. Choosing a face and then seeing a
                // letter makes the choice feel ignored.
                Text(text = avatar, style = MaterialTheme.typography.labelLarge)
            }
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
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
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
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
            LazyColumn(verticalArrangement = Arrangement.spacedBy(5.dp)) {
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
    onSelect: (String?) -> Unit,
    onCreate: (String, Boolean, Int) -> Unit,
    onReset: () -> Unit,
    onDismiss: (() -> Unit)?,
) {
    var newName by remember { mutableStateOf("") }
    var kids by remember { mutableStateOf(false) }
    var avatar by remember { mutableStateOf(0) }
    var confirmingReset by remember { mutableStateOf(false) }
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
                            Text(
                                text = PROFILE_AVATARS.getOrElse(profile.avatarIndex) { PROFILE_AVATARS.first() },
                                style = MaterialTheme.typography.displaySmall,
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
                        }
                    }
                }
            }
            if (profiles.size < 5) {
                Spacer(Modifier.height(24.dp))
                // Avatar is chosen before the name so the row reads left to right as one action.
                Row(horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Xs)) {
                    PROFILE_AVATARS.forEachIndexed { index, glyph ->
                        BuroInteractiveRow(
                            onClick = { avatar = index },
                            selected = avatar == index,
                            shape = CircleShape,
                            contentDescription = glyph,
                        ) {
                            Box(
                                modifier = Modifier.size(44.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(glyph, style = MaterialTheme.typography.headlineSmall)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(BuroSpacing.Sm))
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it.take(24) },
                    label = { Text(strings.newProfile) },
                    singleLine = true,
                    modifier = Modifier.width(360.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { kids = !kids }) { Text(if (kids) "${strings.kidsProfile} ✓" else strings.adultProfile) }
                    Button(
                        onClick = {
                            if (newName.isNotBlank()) {
                                onCreate(newName, kids, avatar)
                                newName = ""
                            }
                        },
                    ) { Text(strings.addProfile) }
                }
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
                    text =
                        if (state.fraction >= 0f) {
                            "${(state.fraction * 100).toInt()}%"
                        } else {
                            strings.downloadInProgress
                        },
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

        LazyColumn(verticalArrangement = Arrangement.spacedBy(BuroSpacing.Xs)) {
            items(entries, key = { it.contentKey }) { entry ->
                DownloadLibraryRow(
                    entry = entry,
                    onPlay = { onPlay(entry.contentKey) },
                    onCancel = { onCancel(entry.contentKey) },
                    onDelete = { onDelete(entry.contentKey) },
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
) {
    val text = strings
    val stored = entry.state == DownloadState.Completed

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(BuroRadius.Medium)
            .background(BuroColors.Surface)
            .padding(BuroSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
                        text =
                            if (state.fraction >= 0f) {
                                "${(state.fraction * 100).toInt()}%"
                            } else {
                                text.downloadInProgress
                            },
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

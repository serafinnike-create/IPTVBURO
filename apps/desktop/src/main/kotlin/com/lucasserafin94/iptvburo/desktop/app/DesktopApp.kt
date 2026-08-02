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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.lucasserafin94.iptvburo.desktop.ui.BuroRemoteArtwork
import com.lucasserafin94.iptvburo.domain.model.Category
import com.lucasserafin94.iptvburo.domain.model.Channel
import com.lucasserafin94.iptvburo.xtream.XtreamContentType
import com.lucasserafin94.iptvburo.desktop.user.DesktopLanguage
import com.lucasserafin94.iptvburo.desktop.user.DesktopProfile
import com.lucasserafin94.iptvburo.desktop.update.DESKTOP_VERSION
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
    val releaseUpdater = remember { GitHubReleaseUpdater() }
    var updateBusy by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            pendingExternalChannel = null
            appState.clearSensitiveData()
        }
    }

    BuroDesktopTheme {
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
                        onImport = {
                            chooseLocalPlaylist(ownerWindow)?.let { path ->
                                scope.launch { appState.importLocalPlaylist(path) }
                            }
                        },
                        onConnectXtream = { showXtreamLogin = true },
                        language = appState.language,
                        destination = appState.destination,
                        onHome = appState::openHome,
                        onLive = {
                            scope.launch { appState.openCatalog(XtreamContentType.LIVE) }
                        },
                        onFavorites = { scope.launch { appState.setFavoritesOnly(true) } },
                    )
                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        TopBar(
                            channelCount = appState.selectedSourceItemCount,
                            sourceCount = appState.sourceSummaries.size,
                            activeProfile = appState.activeProfile,
                            language = appState.language,
                            onChangeProfile = { appState.selectProfile(null) },
                            onSelectLanguage = appState::updateLanguage,
                            updateBusy = updateBusy,
                            updateMessage = updateMessage,
                            onUpdate = {
                                if (!updateBusy) {
                                    scope.launch {
                                        updateBusy = true
                                        updateMessage = "Verificando atualização…"
                                        when (val result = releaseUpdater.check()) {
                                            UpdateCheckResult.UpToDate -> {
                                                updateMessage = "Você já está na versão mais recente."
                                                updateBusy = false
                                            }
                                            is UpdateCheckResult.Failed -> {
                                                updateMessage = result.userMessage
                                                updateBusy = false
                                            }
                                            is UpdateCheckResult.Available -> {
                                                updateMessage = "Baixando ${result.release.displayName}…"
                                                releaseUpdater.downloadAndLaunch(result.release)
                                                    .onSuccess {
                                                        updateMessage = "Instalador verificado. Atualizando…"
                                                        onExitForUpdate()
                                                    }.onFailure {
                                                        updateMessage = "A atualização não pôde ser instalada."
                                                        updateBusy = false
                                                    }
                                            }
                                        }
                                    }
                                }
                            },
                        )
                        HorizontalDivider(color = BuroColors.BorderSoft)
                        if (!appState.hasSelectedSource) {
                            EmptyLibrary(
                                onImport = {
                                    chooseLocalPlaylist(ownerWindow)?.let { path ->
                                        scope.launch { appState.importLocalPlaylist(path) }
                                    }
                                },
                                onConnectXtream = { showXtreamLogin = true },
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
                if (appState.activeProfile == null) {
                    DesktopProfileGate(
                        profiles = appState.profiles,
                        onSelect = { profileId -> scope.launch { appState.selectProfileAndRefresh(profileId) } },
                        onCreate = appState::createProfile,
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
                                "Autenticando e preparando o catálogo…"
                            } else {
                                "Organizando sua playlist…"
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
                                Text("Entendi")
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

@Composable
private fun SourceSidebar(
    sources: List<DesktopSourceSummary>,
    selectedSourceId: String?,
    onSourceSelected: (String) -> Unit,
    onImport: () -> Unit,
    onConnectXtream: () -> Unit,
    language: DesktopLanguage,
    destination: DesktopDestination,
    onHome: () -> Unit,
    onLive: () -> Unit,
    onFavorites: () -> Unit,
) {
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
        SectionLabel(desktopText(language, "library"))
        Spacer(Modifier.height(10.dp))
        NavigationItem(desktopText(language, "home"), selected = destination == DesktopDestination.HOME, onClick = onHome)
        NavigationItem(desktopText(language, "live"), selected = destination == DesktopDestination.CATALOG, onClick = onLive)
        NavigationItem(desktopText(language, "favorites"), selected = destination == DesktopDestination.FAVORITES, onClick = onFavorites)
        Spacer(Modifier.height(28.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SectionLabel(desktopText(language, "sources"))
            Text(
                text = sources.size.toString(),
                color = BuroColors.TextSubtle,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier.weight(1f),
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
        PrivacyNote()
        Spacer(Modifier.height(14.dp))
        OutlinedButton(
            onClick = onConnectXtream,
            modifier = Modifier.fillMaxWidth().height(42.dp),
            shape = RoundedCornerShape(12.dp),
            colors =
                ButtonDefaults.outlinedButtonColors(
                    contentColor = BuroColors.Primary,
                ),
        ) {
            Text(desktopText(language, "connect"), fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onImport,
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = RoundedCornerShape(12.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = BuroColors.Primary,
                    contentColor = Color(0xFF03201D),
                ),
        ) {
            Text("+  ${desktopText(language, "import")}", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Brand() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier =
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(BuroColors.Primary, BuroColors.Accent),
                        ),
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "B",
                color = Color(0xFF071019),
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.titleLarge,
            )
        }
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
    val background = if (selected) BuroColors.SurfaceHover else Color.Transparent
    val foreground = if (selected) BuroColors.Text else BuroColors.TextMuted
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(background)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(if (selected) BuroColors.Primary else BuroColors.TextSubtle),
        )
        Spacer(Modifier.width(12.dp))
        Text(label, color = foreground, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun SourceItem(
    source: DesktopSourceSummary,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val background = if (selected) BuroColors.SurfaceHover else Color.Transparent
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(background)
                .clickable(onClick = onClick)
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
            Text(
                "${source.itemCount} itens",
                color = BuroColors.TextSubtle,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun PrivacyNote() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(BuroColors.Primary.copy(alpha = 0.08f))
                .padding(11.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .padding(top = 5.dp)
                .size(7.dp)
                .clip(CircleShape)
                .background(BuroColors.Success),
        )
        Spacer(Modifier.width(9.dp))
        Text(
            "Cofre protegido\nCredenciais cifradas pelo Windows",
            color = BuroColors.TextMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
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
    Row(
        modifier = Modifier.fillMaxWidth().height(74.dp).padding(horizontal = 26.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                desktopText(language, "your_library"),
                color = BuroColors.Text,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                updateMessage ?: "$sourceCount fontes  •  $channelCount itens",
                color = BuroColors.TextSubtle,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        OutlinedButton(onClick = onUpdate, enabled = !updateBusy) {
            Text(if (updateBusy) "Aguarde…" else "Verificar atualização")
        }
        Spacer(Modifier.width(8.dp))
        StatusPill("v$DESKTOP_VERSION", BuroColors.TextSubtle)
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onChangeProfile) {
            Text(activeProfile?.name ?: desktopText(language, "profile"), color = BuroColors.Text)
        }
        DesktopLanguage.entries.forEach { option ->
            TextButton(onClick = { onSelectLanguage(option) }) {
                Text(
                    option.tag.substringBefore('-').uppercase(),
                    color = if (option == language) BuroColors.Primary else BuroColors.TextSubtle,
                    fontWeight = if (option == language) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
        StatusPill("BURO DESKTOP", BuroColors.Accent)
        Spacer(Modifier.width(10.dp))
        StatusPill("LOCAL & PRIVADO", BuroColors.Success)
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
                    "BURO NOCTURNE  •  BIBLIOTECA PRIVADA",
                    color = BuroColors.Primary,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Spacer(Modifier.height(22.dp))
            Text(
                "Toda a sua biblioteca.\nSem ruído.",
                color = BuroColors.Text,
                style = MaterialTheme.typography.displaySmall,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Importe sua fonte autorizada e deixe o IPTV BURO organizar canais, filmes e séries numa experiência única em todas as telas.",
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
                            contentColor = Color(0xFF08110F),
                        ),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
                ) {
                    Text("Conectar Xtream", fontWeight = FontWeight.Bold)
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
                    Text("Importar M3U", fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "A fonte é reconectada com o cofre protegido deste usuário.",
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
                placeholder = { Text("Buscar canal…") },
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
                "${appState.visibleChannels.size} resultados",
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
                Text("Esquecer fonte")
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
            Text("Selecione um canal", color = BuroColors.TextSubtle)
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
                        contentColor = Color(0xFF03201D),
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
            Text("Selecione um canal", color = BuroColors.TextSubtle)
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
                    contentColor = Color(0xFF03201D),
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
                Text("Fechar")
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
            Text("Fechar")
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
                "Nenhum dado sensível será salvo.",
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
                        contentColor = Color(0xFF03201D),
                    ),
            ) {
                Text("Assistir agora", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
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
    onCreate: (String, Boolean) -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    var kids by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xF207090C)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 820.dp).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("IPTV BURO", color = BuroColors.Primary, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(12.dp))
            Text("Quem está assistindo?", color = BuroColors.Text, style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                profiles.forEach { profile ->
                    Button(
                        onClick = { onSelect(profile.id) },
                        modifier = Modifier.width(130.dp).height(96.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BuroColors.SurfaceRaised),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (profile.isKids) "KIDS" else "BURO", color = BuroColors.Primary, fontWeight = FontWeight.Black)
                            Text(profile.name, color = BuroColors.Text, maxLines = 1)
                        }
                    }
                }
            }
            if (profiles.size < 5) {
                Spacer(Modifier.height(24.dp))
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it.take(24) },
                    label = { Text("Novo perfil") },
                    singleLine = true,
                    modifier = Modifier.width(360.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { kids = !kids }) { Text(if (kids) "Perfil infantil ✓" else "Perfil adulto") }
                    Button(onClick = { if (newName.isNotBlank()) { onCreate(newName, kids); newName = "" } }) { Text("Adicionar") }
                }
            }
        }
    }
}

private fun desktopText(language: DesktopLanguage, key: String): String {
    val values = when (language) {
        DesktopLanguage.PORTUGUESE_BRAZIL -> mapOf("library" to "BIBLIOTECA", "home" to "Início", "live" to "Ao vivo", "favorites" to "Favoritos", "sources" to "FONTES", "connect" to "Conectar Xtream", "import" to "Importar M3U", "your_library" to "Sua biblioteca", "profile" to "Perfil")
        DesktopLanguage.ENGLISH -> mapOf("library" to "LIBRARY", "home" to "Home", "live" to "Live TV", "favorites" to "Favorites", "sources" to "SOURCES", "connect" to "Connect Xtream", "import" to "Import M3U", "your_library" to "Your library", "profile" to "Profile")
        DesktopLanguage.GERMAN -> mapOf("library" to "BIBLIOTHEK", "home" to "Start", "live" to "Live-TV", "favorites" to "Favoriten", "sources" to "QUELLEN", "connect" to "Xtream verbinden", "import" to "M3U importieren", "your_library" to "Deine Bibliothek", "profile" to "Profil")
        DesktopLanguage.ITALIAN -> mapOf("library" to "LIBRERIA", "home" to "Home", "live" to "TV in diretta", "favorites" to "Preferiti", "sources" to "FONTI", "connect" to "Connetti Xtream", "import" to "Importa M3U", "your_library" to "La tua libreria", "profile" to "Profilo")
    }
    return values.getValue(key)
}

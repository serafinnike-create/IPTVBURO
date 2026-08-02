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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
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
import com.lucasserafin94.iptvburo.desktop.ui.BuroRemoteArtwork
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

    Column(
        modifier =
            Modifier
                .fillMaxSize()
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
        HorizontalDivider(color = BuroColors.BorderSoft)
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val compact = maxWidth < 1_350.dp
            Row(modifier = Modifier.fillMaxSize()) {
                XtreamCategoryPane(
                    categories = appState.xtreamCategories,
                    contentType = appState.xtreamContentType,
                    selectedCategoryId = appState.selectedXtreamCategoryId,
                    onSelected = { categoryId ->
                        detailsOpen = false
                        scope.launch { appState.selectXtreamCategory(categoryId) }
                    },
                    modifier = Modifier.width(if (compact) 190.dp else 220.dp),
                )
                XtreamPaneDivider()
                XtreamItemsPane(
                    appState = appState,
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
                    modifier = Modifier.weight(1f),
                )
            }
            if (
                appState.xtreamStatus is XtreamStatus.Connecting ||
                appState.xtreamStatus is XtreamStatus.LoadingCatalog
            ) {
                XtreamLoadingOverlay(status = appState.xtreamStatus)
            }
        }
    }
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
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            XtreamContentType.entries.forEach { type ->
                ContentTypeButton(
                    contentType = type,
                    selected = type == selectedType,
                    onClick = { onTypeSelected(type) },
                )
                Spacer(Modifier.width(8.dp))
            }
            Spacer(Modifier.weight(1f))
            OutlinedButton(
                onClick = onDisconnect,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = BuroColors.TextMuted),
            ) {
                Text("Encerrar sessão")
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.width(420.dp),
                singleLine = true,
                placeholder = { Text("Buscar neste catálogo…") },
                leadingIcon = { Text("⌕", color = BuroColors.TextSubtle) },
                shape = RoundedCornerShape(12.dp),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BuroColors.Primary,
                        unfocusedBorderColor = BuroColors.Border,
                        focusedContainerColor = BuroColors.Surface,
                        unfocusedContainerColor = BuroColors.Surface,
                    ),
            )
            Spacer(Modifier.width(14.dp))
            Text(
                when (status) {
                    XtreamStatus.Connected -> "Sessão ativa • conexão protegida no Windows"
                    is XtreamStatus.Error -> status.message
                    XtreamStatus.Connecting -> "Autenticando…"
                    is XtreamStatus.LoadingCatalog -> "Carregando catálogo…"
                    XtreamStatus.Disconnected -> "Sessão encerrada"
                },
                color = if (status is XtreamStatus.Error) BuroColors.Error else BuroColors.TextSubtle,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selectedType != XtreamContentType.LIVE) {
            Spacer(Modifier.height(10.dp))
            val currentYear = Year.now().value
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf<Int?>(null, currentYear, currentYear - 1).forEach { year ->
                    OutlinedButton(
                        onClick = { onYearSelected(year) },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (selectedYear == year) BuroColors.Primary else BuroColors.TextMuted,
                        ),
                    ) {
                        Text(if (year == null) "Todos os anos" else "Lançamentos $year")
                    }
                }
            }
        }
    }
}

@Composable
private fun ContentTypeButton(
    contentType: XtreamContentType,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val label =
        when (contentType) {
            XtreamContentType.LIVE -> "Ao vivo"
            XtreamContentType.MOVIE -> "Filmes"
            XtreamContentType.SERIES -> "Séries"
        }
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        colors =
            ButtonDefaults.buttonColors(
                containerColor =
                    if (selected) {
                        BuroColors.Primary
                    } else {
                        BuroColors.SurfaceRaised
                    },
                contentColor =
                    if (selected) {
                        Color(0xFF03201D)
                    } else {
                        BuroColors.TextMuted
                    },
            ),
    ) {
        Text(label, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun XtreamCategoryPane(
    categories: List<XtreamCategory>,
    contentType: XtreamContentType,
    selectedCategoryId: String?,
    onSelected: (String?) -> Unit,
    modifier: Modifier,
) {
    Column(modifier = modifier.fillMaxHeight().padding(16.dp)) {
        Text(
            "CATEGORIAS",
            color = BuroColors.TextSubtle,
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item {
                XtreamCategoryItem(
                    label = "Todos",
                    artworkResource = categoryArtworkResource("", contentType),
                    selected = selectedCategoryId == null,
                    onClick = { onSelected(null) },
                )
            }
            items(categories, key = XtreamCategory::providerId) { category ->
                XtreamCategoryItem(
                    label = category.name,
                    artworkResource = categoryArtworkResource(category.name, contentType),
                    selected = category.providerId == selectedCategoryId,
                    onClick = { onSelected(category.providerId) },
                )
            }
        }
    }
}

@Composable
private fun XtreamCategoryItem(
    label: String,
    artworkResource: String,
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
        Image(
            painter = painterResource(artworkResource),
            contentDescription = null,
            modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(9.dp))
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

@Composable
private fun XtreamItemsPane(
    appState: DesktopAppState,
    onItemSelected: (String) -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier = modifier.fillMaxHeight().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "CATÁLOGO",
                color = BuroColors.TextSubtle,
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${appState.xtreamPage.totalMatches} itens",
                color = BuroColors.TextSubtle,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(12.dp))
        if (appState.xtreamPage.items.isEmpty()) {
            Text(
                "Nenhum item corresponde ao filtro.",
                color = BuroColors.TextSubtle,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(10.dp),
            )
        } else {
            LazyVerticalGrid(
                modifier = Modifier.weight(1f),
                columns = GridCells.Adaptive(minSize = 150.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                gridItems(
                    items = appState.xtreamPage.items,
                    key = XtreamCatalogItem::providerId,
                ) { item ->
                    XtreamCatalogCard(
                        item = item,
                        selected = item.providerId == appState.selectedXtreamItem?.providerId,
                        onClick = { onItemSelected(item.providerId) },
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            OutlinedButton(
                onClick = onPreviousPage,
                enabled = appState.xtreamPage.hasPrevious,
            ) {
                Text("Anterior")
            }
            Text(
                "${appState.xtreamPage.pageIndex + 1} / ${appState.xtreamPage.pageCount}",
                color = BuroColors.TextMuted,
                style = MaterialTheme.typography.labelLarge,
            )
            OutlinedButton(
                onClick = onNextPage,
                enabled = appState.xtreamPage.hasNext,
            ) {
                Text("Próxima")
            }
        }
    }
}

@Composable
private fun XtreamCatalogCard(
    item: XtreamCatalogItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val posterLike = item.contentType != XtreamContentType.LIVE
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(if (posterLike) 2f / 3f else 16f / 10f)
                .clip(RoundedCornerShape(14.dp))
                .background(BuroColors.SurfaceRaised)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) BuroColors.Primary else BuroColors.BorderSoft,
                    shape = RoundedCornerShape(14.dp),
                )
                .clickable(onClick = onClick)
    ) {
        BuroRemoteArtwork(
            artworkUrl = item.artworkUrl,
            contentDescription = item.name,
            modifier = Modifier.fillMaxSize(),
            contentScale =
                if (item.contentType == XtreamContentType.LIVE) {
                    ContentScale.Fit
                } else {
                    ContentScale.Crop
                },
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                XtreamMonogram(item.name, if (posterLike) 62 else 52)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(if (posterLike) 0.55f else 0.72f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, BuroColors.Canvas.copy(alpha = 0.97f)),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(13.dp),
        ) {
            Text(
                item.name,
                color = BuroColors.Text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                itemMetadata(item),
                color = BuroColors.TextSubtle,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(BuroColors.Primary),
            )
        }
    }
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
                TextButton(onClick = onBack) { Text("← Voltar ao catálogo", color = BuroColors.Text) }
                Spacer(Modifier.weight(1f))
                Text("IPTV BURO", color = BuroColors.Primary, fontWeight = FontWeight.Black)
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
    Box(
        modifier = modifier.fillMaxHeight().padding(if (compact) 16.dp else 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (item == null) {
            Text("Selecione um item", color = BuroColors.TextSubtle)
            return@Box
        }
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(BuroColors.SurfaceRaised, BuroColors.Surface),
                        ),
                    ).verticalScroll(rememberScrollState())
                    .padding(if (compact) 18.dp else 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
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
            val posterLike = item.contentType != XtreamContentType.LIVE && richArtwork == null
            BuroRemoteArtwork(
                artworkUrl = richArtwork ?: item.artworkUrl,
                contentDescription = item.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(
                        max =
                            if (posterLike) {
                                if (compact) 118.dp else 154.dp
                            } else {
                                if (compact) 260.dp else 390.dp
                            },
                    )
                    .height(
                        if (posterLike) {
                            if (compact) 177.dp else 231.dp
                        } else {
                            if (compact) 150.dp else 190.dp
                        },
                    )
                    .clip(RoundedCornerShape(18.dp)),
                contentScale = if (posterLike) ContentScale.Crop else ContentScale.Fit,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    XtreamMonogram(item.name, if (compact) 64 else 86)
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                item.name,
                color = BuroColors.Text,
                style =
                    if (compact) {
                        MaterialTheme.typography.titleLarge
                    } else {
                        MaterialTheme.typography.headlineMedium
                    },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                itemMetadata(item),
                color = BuroColors.TextMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(20.dp))
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
                Button(
                    onClick = {
                        onOpenExternal(
                            PendingXtreamExternal(
                                displayName = item.name,
                                target = mediaTarget,
                                startPositionMillis = resumeStartPosition(resumeDecisionFor(mediaTarget)),
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = BuroColors.Primary,
                            contentColor = Color(0xFF03201D),
                        ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(playbackButtonLabel(resumeDecisionFor(mediaTarget)), fontWeight = FontWeight.Bold)
                }
                if (resumeDecisionFor(mediaTarget) is ResumeDecision.ResumeFrom) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            onOpenExternal(PendingXtreamExternal(item.name, mediaTarget, startPositionMillis = 0L))
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) { Text("Assistir do início") }
                }
                if (item.contentType == XtreamContentType.MOVIE) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        Text(if (isFavorite) "♥ Nos favoritos" else "♡ Adicionar aos favoritos", fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(18.dp))
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
            Spacer(Modifier.height(12.dp))
            Text(
                "Player VLC integrado para H.264, H.265/HEVC, AAC, MP4, MKV e HLS.",
                color = BuroColors.TextSubtle,
                style = MaterialTheme.typography.bodyMedium,
            )
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
                        contentColor = Color(0xFF03201D),
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

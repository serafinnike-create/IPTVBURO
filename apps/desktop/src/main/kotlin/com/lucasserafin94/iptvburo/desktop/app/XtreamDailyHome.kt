package com.lucasserafin94.iptvburo.desktop.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.lucasserafin94.iptvburo.desktop.DailyHomeStatus
import com.lucasserafin94.iptvburo.desktop.DesktopAppState
import com.lucasserafin94.iptvburo.desktop.model.XtreamPlaybackTarget
import com.lucasserafin94.iptvburo.desktop.ui.BuroColors
import com.lucasserafin94.iptvburo.desktop.ui.BuroRemoteArtwork
import com.lucasserafin94.iptvburo.xtream.XtreamCatalogItem
import com.lucasserafin94.iptvburo.xtream.XtreamContentType
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun XtreamDailyHome(
    appState: DesktopAppState,
    onOpenExternal: (PendingXtreamExternal) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var today by remember { mutableStateOf(LocalDate.now()) }
    var detailsOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            val current = LocalDate.now()
            if (current != today) today = current
            delay(60_000)
        }
    }
    LaunchedEffect(today, appState.xtreamSummary?.sourceId, appState.activeProfileId) {
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

    when (val status = appState.dailyHomeStatus) {
        DailyHomeStatus.Idle,
        DailyHomeStatus.Loading,
        -> HomeLoading()
        is DailyHomeStatus.Error -> HomeError(status.message) { scope.launch { appState.loadDailyHome(today) } }
        is DailyHomeStatus.Loaded -> {
            val snapshot = status.snapshot
            LazyColumn(
                modifier = Modifier.fillMaxSize().background(BuroColors.Canvas),
                verticalArrangement = Arrangement.spacedBy(26.dp),
            ) {
                item(key = "daily-hero") {
                    DailyHero(
                        item = snapshot.hero,
                        date = snapshot.date,
                        onClick = { item ->
                            appState.selectDailyItem(item)
                            detailsOpen = true
                        },
                    )
                }
                item(key = "daily-movies") {
                    DailyRow("Filmes escolhidos para hoje", snapshot.movies) { item ->
                        appState.selectDailyItem(item)
                        detailsOpen = true
                    }
                }
                item(key = "daily-series") {
                    DailyRow("Séries para continuar explorando", snapshot.series) { item ->
                        appState.selectDailyItem(item)
                        detailsOpen = true
                    }
                }
                item(key = "daily-live") {
                    DailyRow("Ao vivo agora", snapshot.live) { item ->
                        appState.selectDailyItem(item)
                        detailsOpen = true
                    }
                }
                item { Spacer(Modifier.height(26.dp)) }
            }
        }
    }

    if (detailsOpen && appState.selectedXtreamItem != null) {
        DialogWindow(
            onCloseRequest = { detailsOpen = false },
            title = appState.selectedXtreamItem?.name ?: "Detalhes",
            state = rememberDialogState(width = 900.dp, height = 720.dp),
            resizable = true,
        ) {
            Box(Modifier.fillMaxSize().background(BuroColors.Canvas)) {
                XtreamItemDetail(
                    item = appState.selectedXtreamItem,
                    movieStatus = appState.movieDetailsStatus,
                    seriesStatus = appState.seriesDetailsStatus,
                    liveEpgStatus = appState.liveEpgStatus,
                    onLoadMovie = { scope.launch { appState.loadSelectedMovieDetails() } },
                    onLoadSeries = { scope.launch { appState.loadSelectedSeriesDetails() } },
                    onOpenTrailer = appState::openPublicTrailer,
                    onOpenExternal = onOpenExternal,
                    onOpenPerson = appState::openPerson,
                    isFavorite = appState.selectedXtreamItem?.let(appState::isFavorite) == true,
                    onToggleFavorite = { appState.selectedXtreamItem?.let(appState::toggleFavorite) },
                    compact = false,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun DailyHero(item: XtreamCatalogItem?, date: LocalDate, onClick: (XtreamCatalogItem) -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().height(330.dp).background(BuroColors.Surface),
    ) {
        if (item != null) {
            BuroRemoteArtwork(
                artworkUrl = item.artworkUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            ) { Box(Modifier.fillMaxSize().background(BuroColors.SurfaceRaised)) }
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    listOf(BuroColors.Canvas, BuroColors.Canvas.copy(alpha = 0.9f), Color.Transparent),
                ),
            ),
        )
        Column(
            modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 40.dp).fillMaxWidth(0.55f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("SELEÇÃO DIÁRIA · ${date.dayOfMonth.toString().padStart(2, '0')}/${date.monthValue.toString().padStart(2, '0')}", color = BuroColors.Primary, fontWeight = FontWeight.Black)
            Text(item?.name?.editorialDisplayTitle() ?: "Sua biblioteca está pronta", color = BuroColors.Text, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("Uma seleção diferente a cada dia, organizada sem misturar toda a biblioteca na mesma tela.", color = BuroColors.TextMuted, style = MaterialTheme.typography.bodyLarge)
            item?.let { selected ->
                Button(onClick = { onClick(selected) }, colors = ButtonDefaults.buttonColors(containerColor = BuroColors.Primary, contentColor = BuroColors.Canvas)) {
                    Text("Ver detalhes", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun DailyRow(title: String, items: List<XtreamCatalogItem>, onClick: (XtreamCatalogItem) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 34.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = BuroColors.Text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("${items.size} opções", color = BuroColors.TextSubtle)
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            item { Spacer(Modifier.width(22.dp)) }
            items(items, key = { "${it.contentType}:${it.providerId}" }) { item -> DailyCard(item, onClick) }
            item { Spacer(Modifier.width(22.dp)) }
        }
    }
}

@Composable
private fun DailyCard(item: XtreamCatalogItem, onClick: (XtreamCatalogItem) -> Unit) {
    val poster = item.contentType != XtreamContentType.LIVE
    Column(
        modifier = Modifier.width(if (poster) 150.dp else 230.dp).clickable { onClick(item) },
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        BuroRemoteArtwork(
            artworkUrl = item.artworkUrl,
            contentDescription = item.name,
            modifier = Modifier.fillMaxWidth().height(if (poster) 225.dp else 130.dp).clip(RoundedCornerShape(14.dp)).background(BuroColors.SurfaceRaised),
            contentScale = if (poster) ContentScale.Crop else ContentScale.Fit,
        ) { Box(Modifier.fillMaxSize().background(BuroColors.SurfaceRaised)) }
        Text(item.name.editorialDisplayTitle(), color = BuroColors.Text, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(if (item.contentType == XtreamContentType.LIVE) "AO VIVO" else listOfNotNull(item.year?.toString(), item.rating?.let { "★ %.1f".format(it) }).joinToString(" · "), color = BuroColors.TextSubtle, maxLines = 1)
    }
}

private fun String.editorialDisplayTitle(): String =
    replace(Regex("\\s*\\[[^]]{1,12}]\\s*"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

@Composable
private fun HomeLoading() = Box(Modifier.fillMaxSize().background(BuroColors.Canvas), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        CircularProgressIndicator(color = BuroColors.Primary)
        Text("Organizando a seleção de hoje…", color = BuroColors.TextMuted)
    }
}

@Composable
private fun HomeError(message: String, onRetry: () -> Unit) = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(message, color = BuroColors.TextMuted)
        Button(onClick = onRetry) { Text("Tentar novamente") }
    }
}

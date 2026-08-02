package com.lucasserafin94.iptvburo.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.lucasserafin94.iptvburo.ui.MovieDetailsUi
import com.lucasserafin94.iptvburo.ui.components.FocusSurface
import com.lucasserafin94.iptvburo.ui.designsystem.BuroButton
import com.lucasserafin94.iptvburo.ui.designsystem.BuroButtonStyle
import com.lucasserafin94.iptvburo.ui.designsystem.BuroErrorState
import com.lucasserafin94.iptvburo.ui.theme.Danger
import com.lucasserafin94.iptvburo.ui.theme.Ink
import com.lucasserafin94.iptvburo.ui.theme.Muted
import com.lucasserafin94.iptvburo.ui.theme.Surface
import com.lucasserafin94.iptvburo.ui.theme.Teal
import com.lucasserafin94.iptvburo.ui.theme.White

@Composable
internal fun MovieDetailsScreen(
    fallbackTitle: String,
    fallbackArtworkUrl: String?,
    details: MovieDetailsUi?,
    isLoading: Boolean,
    hasError: Boolean,
    isResolvingPlayback: Boolean,
    hasPlaybackError: Boolean,
    onPlay: () -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onDownload: () -> Unit,
    canDownloadOffline: Boolean,
    onOpenPerson: (String) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val platformContext = LocalPlatformContext.current
    val androidContext = LocalContext.current
    val backdropUrl = details?.backdropUrl ?: details?.artworkUrl ?: fallbackArtworkUrl
    val backdropRequest =
        remember(backdropUrl, platformContext) {
            backdropUrl?.let { url ->
                ImageRequest.Builder(platformContext)
                    .data(url)
                    .diskCachePolicy(CachePolicy.DISABLED)
                    .build()
            }
        }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(Ink)) {
        val portrait = maxWidth < 600.dp && maxHeight >= maxWidth
        val horizontalPadding = if (portrait) 18.dp else 52.dp
        val heroHeight = if (portrait) 310.dp else 390.dp
        val contentWidth = if (portrait) maxWidth else maxWidth * 0.62f
        val backFocusRequester = remember(fallbackTitle) { FocusRequester() }
        LaunchedEffect(fallbackTitle) { backFocusRequester.requestFocus() }

        backdropRequest?.let { request ->
            AsyncImage(
                model = request,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(heroHeight),
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
            )
        }
        MutedTrailerBackdrop(
            youtubeId = details?.youtubeTrailerId,
            modifier = Modifier.fillMaxWidth().height(heroHeight),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0x22090A0D), Color(0xAA090A0D), Ink),
                        endY = if (portrait) 760f else 900f,
                    ),
                ),
        )
        if (!portrait) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(Ink, Color(0xE6090A0D), Color.Transparent),
                            endX = 1_250f,
                        ),
                    ),
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = horizontalPadding,
                    top = 20.dp,
                    end = horizontalPadding,
                    bottom = 54.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item("back") {
                FocusSurface(
                    onClick = onBack,
                    modifier = Modifier.size(50.dp).focusRequester(backFocusRequester),
                    backgroundColor = Color(0xA6111319),
                    focusedBackgroundColor = Surface,
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar",
                            tint = White,
                        )
                    }
                }
            }

            item("hero-space") { Spacer(Modifier.height(if (portrait) 150.dp else 160.dp)) }

            item("title") {
                Column(modifier = Modifier.width(contentWidth)) {
                    Text(
                        text = details?.title ?: fallbackTitle,
                        color = White,
                        fontSize = if (portrait) 30.sp else 44.sp,
                        lineHeight = if (portrait) 35.sp else 49.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val facts =
                        listOfNotNull(
                            details?.releaseDate,
                            details?.duration,
                            details?.genre,
                            details?.rating?.let { "★ ${"%.1f".format(it)}" },
                        )
                    if (facts.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = facts.joinToString("  •  "),
                            color = Teal,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            if (hasPlaybackError || isResolvingPlayback) {
                item("playback-state") {
                    Text(
                        text = if (hasPlaybackError) "Não foi possível abrir este vídeo." else "Preparando reprodução…",
                        color = if (hasPlaybackError) Danger else Teal,
                    )
                }
            }

            item("actions") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BuroButton(
                        onClick = onPlay,
                        enabled = !isLoading && !isResolvingPlayback,
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Text("Assistir")
                    }
                    BuroButton(
                        onClick = onToggleFavorite,
                        enabled = !isLoading,
                        style = BuroButtonStyle.Secondary,
                    ) {
                        Icon(
                            if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = null,
                            tint = if (isFavorite) Color(0xFFE46C7A) else White,
                        )
                        Text(if (isFavorite) "Na Minha BURO" else "Minha BURO")
                    }
                    if (canDownloadOffline) {
                        BuroButton(
                            onClick = onDownload,
                            enabled = !isLoading && !isResolvingPlayback,
                            style = BuroButtonStyle.Secondary,
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Text("Baixar")
                        }
                    }
                    details?.youtubeTrailerId?.let { trailerId ->
                        BuroButton(
                            onClick = {
                                val uri = Uri.parse("https://www.youtube.com/watch?v=$trailerId")
                                runCatching {
                                    androidContext.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                }
                            },
                            style = BuroButtonStyle.Secondary,
                        ) {
                            Text("Trailer")
                        }
                    }
                }
            }

            when {
                isLoading -> item("loading") { Text("Carregando ficha completa…", color = Muted) }
                hasError -> item("error") {
                    BuroErrorState(
                        title = "Ficha indisponível",
                        message = "A fonte não respondeu com os detalhes deste filme.",
                        actionLabel = "Tentar novamente",
                        onAction = onRetry,
                    )
                }
                details != null -> {
                    details.plot?.takeIf(String::isNotBlank)?.let { plot ->
                        item("plot") {
                            Text(
                                text = plot,
                                color = White,
                                fontSize = if (portrait) 16.sp else 18.sp,
                                lineHeight = if (portrait) 24.sp else 28.sp,
                                modifier = Modifier.width(contentWidth),
                            )
                        }
                    }
                    details.cast?.toCastNames()?.takeIf(List<String>::isNotEmpty)?.let { cast ->
                        item("cast") {
                            Column(modifier = Modifier.width(contentWidth)) {
                                Text(
                                    text = "Elenco",
                                    color = White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(Modifier.height(9.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(cast, key = { it.lowercase() }) { actor ->
                                        BuroButton(
                                            onClick = { onOpenPerson(actor) },
                                            style = BuroButtonStyle.Secondary,
                                        ) {
                                            Text(actor, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item("credits") {
                        Column(
                            modifier =
                                Modifier
                                    .width(contentWidth)
                                    .background(Color(0xB2111319), RoundedCornerShape(18.dp))
                                    .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(9.dp),
                        ) {
                            DetailFact("Direção", details.director)
                            DetailFact("Gênero", details.genre)
                            DetailFact("País", details.country)
                            DetailFact("Lançamento", details.releaseDate)
                        }
                    }
                }
            }
        }
    }
}

private fun String.toCastNames(): List<String> =
    split(Regex("[,;]|\\s/\\s"))
        .map(String::trim)
        .filter { it.length in 2..100 }
        .distinctBy(String::lowercase)
        .take(24)

@Composable
private fun DetailFact(
    label: String,
    value: String?,
) {
    if (!value.isNullOrBlank()) {
        Text(
            text = "$label  •  $value",
            color = Muted,
            fontSize = 15.sp,
            lineHeight = 22.sp,
        )
    }
}

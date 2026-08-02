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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Download
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import com.lucasserafin94.iptvburo.R
import com.lucasserafin94.iptvburo.ui.EpisodeUi
import com.lucasserafin94.iptvburo.ui.SeriesDetailsUi
import com.lucasserafin94.iptvburo.ui.components.FocusSurface
import com.lucasserafin94.iptvburo.ui.designsystem.BuroErrorState
import com.lucasserafin94.iptvburo.ui.designsystem.BuroButton
import com.lucasserafin94.iptvburo.ui.designsystem.BuroButtonStyle
import com.lucasserafin94.iptvburo.ui.theme.Blue
import com.lucasserafin94.iptvburo.ui.theme.Danger
import com.lucasserafin94.iptvburo.ui.theme.Ink
import com.lucasserafin94.iptvburo.ui.theme.Muted
import com.lucasserafin94.iptvburo.ui.theme.Surface
import com.lucasserafin94.iptvburo.ui.theme.Teal
import com.lucasserafin94.iptvburo.ui.theme.White

@Composable
internal fun SeriesDetailsScreen(
    fallbackTitle: String,
    details: SeriesDetailsUi?,
    isLoading: Boolean,
    hasError: Boolean,
    isResolvingPlayback: Boolean,
    hasPlaybackError: Boolean,
    onOpenEpisode: (EpisodeUi) -> Unit,
    onDownloadEpisode: (EpisodeUi) -> Unit,
    canDownloadOffline: Boolean,
    onOpenPerson: (String) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(Ink)) {
        val portrait = maxWidth < 600.dp && maxHeight >= maxWidth
        val columns = if (portrait) 1 else 2
        val padding = if (portrait) 16.dp else 42.dp
        val backFocusRequester = remember(fallbackTitle) { FocusRequester() }
        var expandedSeason by remember(details?.title) { mutableStateOf<Int?>(null) }
        LaunchedEffect(fallbackTitle) {
            backFocusRequester.requestFocus()
        }
        val platformContext = LocalPlatformContext.current
        val androidContext = LocalContext.current
        val heroUrl = details?.backdropUrl ?: details?.artworkUrl
        val heroRequest =
            remember(heroUrl, platformContext) {
                heroUrl?.let { url ->
                    ImageRequest.Builder(platformContext)
                        .data(url)
                        .diskCachePolicy(CachePolicy.DISABLED)
                        .build()
                }
            }
        heroRequest?.let { request ->
            AsyncImage(
                model = request,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(if (portrait) 300.dp else 360.dp),
                contentScale = ContentScale.Crop,
            )
        }
        MutedTrailerBackdrop(
            youtubeId = details?.youtubeTrailerId,
            modifier = Modifier.fillMaxWidth().height(if (portrait) 300.dp else 360.dp),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0x3308090A), Color(0xD908090A), Ink),
                        endY = 820f,
                    ),
                ),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = padding,
                    top = if (portrait) 18.dp else 30.dp,
                    end = padding,
                    bottom = 42.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "series:header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FocusSurface(
                        onClick = onBack,
                        modifier = Modifier
                            .size(50.dp)
                            .focusRequester(backFocusRequester),
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.common_back),
                                tint = Teal,
                            )
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = details?.title ?: fallbackTitle,
                            color = White,
                            fontSize = if (portrait) 25.sp else 31.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(R.string.series_episodes_title),
                            color = Teal,
                            fontSize = 14.sp,
                        )
                    }
                }
            }

            if (details != null) {
                item(key = "series:hero-space") {
                    Spacer(Modifier.height(if (portrait) 100.dp else 130.dp))
                }
                item(key = "series:facts") {
                    val facts =
                        listOfNotNull(
                            details.releaseDate,
                            details.genre,
                            details.rating?.let { "★ ${"%.1f".format(it)}" },
                        )
                    if (facts.isNotEmpty()) {
                        Text(
                            text = facts.joinToString("  •  "),
                            color = Teal,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                details.youtubeTrailerId?.let { trailerId ->
                    item(key = "series:trailer") {
                        BuroButton(
                            onClick = {
                                runCatching {
                                    androidContext.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse("https://www.youtube.com/watch?v=$trailerId"),
                                        ),
                                    )
                                }
                            },
                            style = BuroButtonStyle.Secondary,
                        ) {
                            Text("Assistir ao trailer")
                        }
                    }
                }
            }

            if (isResolvingPlayback || hasPlaybackError) {
                item(key = "series:playback-status") {
                    Text(
                        text =
                            stringResource(
                                if (hasPlaybackError) {
                                    R.string.playback_resolve_error
                                } else {
                                    R.string.playback_resolving
                                },
                            ),
                        color = if (hasPlaybackError) Danger else Teal,
                        fontSize = 14.sp,
                    )
                }
            }

            when {
                isLoading -> {
                    item(key = "series:loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.series_loading),
                                color = Muted,
                                fontSize = 17.sp,
                            )
                        }
                    }
                }

                hasError -> {
                    item(key = "series:error") {
                        BuroErrorState(
                            title = stringResource(R.string.series_error_title),
                            message = stringResource(R.string.series_error_body),
                            actionLabel = stringResource(R.string.common_retry),
                            onAction = onRetry,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                details == null || details.episodes.isEmpty() -> {
                    item(key = "series:empty") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.series_empty),
                                color = Muted,
                                fontSize = 17.sp,
                            )
                        }
                    }
                }

                else -> {
                    details.plot?.takeIf(String::isNotBlank)?.let { plot ->
                        item(key = "series:plot") {
                            Text(
                                text = plot,
                                color = Muted,
                                fontSize = 16.sp,
                                lineHeight = 23.sp,
                            )
                        }
                    }
                    if (!details.director.isNullOrBlank()) {
                        item(key = "series:credits") {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xB2111214), RoundedCornerShape(18.dp))
                                        .padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text("Direção  •  ${details.director}", color = Muted, fontSize = 15.sp)
                            }
                        }
                    }
                    details.cast?.toCastNames()?.takeIf(List<String>::isNotEmpty)?.let { cast ->
                        item(key = "series:cast") {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text("Elenco", color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(9.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(cast, key = { it.lowercase() }) { actor ->
                                        CastPersonChip(
                                            name = actor,
                                            onClick = { onOpenPerson(actor) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    details.episodes
                        .groupBy(EpisodeUi::seasonNumber)
                        .toSortedMap()
                        .forEach { (season, episodes) ->
                            item(key = "series:season:$season") {
                                FocusSurface(
                                    onClick = { expandedSeason = if (expandedSeason == season) null else season },
                                    modifier = Modifier.fillMaxWidth().height(64.dp),
                                    backgroundColor = Surface,
                                ) {
                                    Row(
                                        Modifier.fillMaxSize().padding(horizontal = 18.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = stringResource(R.string.series_season, season),
                                            color = White,
                                            fontSize = 21.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(
                                            text = "${episodes.size} episódios  ${if (expandedSeason == season) "▲" else "▼"}",
                                            color = Teal,
                                            fontSize = 14.sp,
                                        )
                                    }
                                }
                            }
                            if (expandedSeason == season) {
                                items(
                                    items = episodes.chunked(columns),
                                    key = { row -> row.joinToString(":") { it.id } },
                                ) { rowEpisodes ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        rowEpisodes.forEach { episode ->
                                            EpisodeCard(
                                                episode = episode,
                                                onClick = { onOpenEpisode(episode) },
                                                onDownload = { onDownloadEpisode(episode) },
                                                canDownloadOffline = canDownloadOffline,
                                                enabled = !isResolvingPlayback,
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                        repeat(columns - rowEpisodes.size) {
                                            Spacer(Modifier.weight(1f))
                                        }
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
private fun EpisodeCard(
    episode: EpisodeUi,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    canDownloadOffline: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    FocusSurface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(98.dp),
        backgroundColor = Surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(width = 86.dp, height = 56.dp).clip(RoundedCornerShape(10.dp))
                    .background(Brush.linearGradient(listOf(Blue, Teal))),
                contentAlignment = Alignment.Center,
            ) {
                episode.artworkUrl?.let { artwork ->
                    AsyncImage(
                        model = ImageRequest.Builder(LocalPlatformContext.current).data(artwork).diskCachePolicy(CachePolicy.DISABLED).build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = White)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text =
                        if (episode.episodeNumber == null) {
                            stringResource(
                                R.string.series_episode_season_only,
                                episode.seasonNumber,
                            )
                        } else {
                            stringResource(
                                R.string.series_episode_number,
                                episode.seasonNumber,
                                episode.episodeNumber,
                            )
                        },
                    color = Teal,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = episode.title,
                    color = White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (canDownloadOffline) {
                FocusSurface(
                    onClick = onDownload,
                    enabled = enabled,
                    modifier = Modifier.size(44.dp),
                    backgroundColor = Ink.copy(alpha = 0.55f),
                    focusedBackgroundColor = Teal,
                ) { focused ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Baixar episódio",
                            tint = if (focused) Ink else Teal,
                        )
                    }
                }
            }
        }
    }
}

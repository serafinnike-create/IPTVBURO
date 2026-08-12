package com.lucasserafin94.iptvburo.ui.screens


import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Share
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
import com.lucasserafin94.iptvburo.ui.DownloadStateUi
import com.lucasserafin94.iptvburo.ui.EpisodeUi
import com.lucasserafin94.iptvburo.ui.SeriesDetailsUi
import com.lucasserafin94.iptvburo.ui.capabilities.AndroidPlatformCapabilities
import com.lucasserafin94.iptvburo.ui.components.FocusSurface
import com.lucasserafin94.iptvburo.ui.designsystem.BuroErrorState
import com.lucasserafin94.iptvburo.ui.designsystem.BuroButton
import com.lucasserafin94.iptvburo.ui.designsystem.BuroButtonStyle
import com.lucasserafin94.iptvburo.ui.theme.BuroAccent
import com.lucasserafin94.iptvburo.ui.theme.BuroCanvas
import com.lucasserafin94.iptvburo.ui.theme.BuroDanger
import com.lucasserafin94.iptvburo.ui.theme.BuroGold
import com.lucasserafin94.iptvburo.ui.theme.BuroSurface
import com.lucasserafin94.iptvburo.ui.theme.BuroTextPrimary
import com.lucasserafin94.iptvburo.ui.theme.BuroTextSecondary

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
    onCancelEpisodeDownload: (EpisodeUi) -> Unit,
    onDeleteEpisodeDownload: (EpisodeUi) -> Unit,
    downloadStateOf: (EpisodeUi) -> DownloadStateUi,
    onOpenPerson: (String) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    isFavorite: Boolean = false,
    /** Null only where there is no series to favourite, which keeps the button honest. */
    onToggleFavorite: (() -> Unit)? = null,
    /** Sends this series to the system share sheet. Null hides the button entirely. */
    onShare: (() -> Unit)? = null,
    /** How far into each episode the viewer is, keyed by episode id. */
    episodeProgress: Map<String, Float> = emptyMap(),
    /** Actor photos already looked up, keyed by lower-cased name. */
    castPhotos: Map<String, String?> = emptyMap(),
    onRequestCastPhotos: (List<String>) -> Unit = {},
    offlineSupported: Boolean = AndroidPlatformCapabilities.offlineSupported,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(BuroCanvas)) {
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
                        listOf(Color(0x3308090A), Color(0xD908090A), BuroCanvas),
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
                                tint = BuroAccent,
                            )
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = details?.title ?: fallbackTitle,
                            color = BuroTextPrimary,
                            fontSize = if (portrait) 25.sp else 31.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(R.string.series_episodes_title),
                            color = BuroAccent,
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
                            color = BuroAccent,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                // Favourite and trailer together, the same pair a film gets. A series was the only
                // kind of title with no way to favourite it from its own page, which meant the one
                // thing people return to weekly was the one thing they could not mark.
                item(key = "series:actions") {
                    // FlowRow for the same reason the film screen uses one: in a plain Row the
                    // second button is laid out past the right edge on a phone and cannot be
                    // reached by any gesture.
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        onToggleFavorite?.let { toggle ->
                            BuroButton(onClick = toggle, style = BuroButtonStyle.Secondary) {
                                Icon(
                                    if (isFavorite) {
                                        Icons.Default.Favorite
                                    } else {
                                        Icons.Default.FavoriteBorder
                                    },
                                    contentDescription = null,
                                    tint = if (isFavorite) Color(0xFFE46C7A) else BuroTextPrimary,
                                )
                                // The longer label drawn invisibly underneath, so toggling does not
                                // resize the button and reflow the row it sits in.
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = stringResource(R.string.details_favorite_added),
                                        color = Color.Transparent,
                                        maxLines = 1,
                                    )
                                    Text(
                                        stringResource(
                                            if (isFavorite) {
                                                R.string.details_favorite_added
                                            } else {
                                                R.string.details_favorite_add
                                            },
                                        ),
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                        onShare?.let { share ->
                            BuroButton(onClick = share, style = BuroButtonStyle.Secondary) {
                                Icon(Icons.Default.Share, contentDescription = null)
                                Text(stringResource(R.string.details_share))
                            }
                        }
                        details.youtubeTrailerId?.let { trailerId ->
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
                                Text(stringResource(R.string.details_trailer))
                            }
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
                        color = if (hasPlaybackError) BuroDanger else BuroAccent,
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
                                color = BuroTextSecondary,
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
                                color = BuroTextSecondary,
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
                                color = BuroTextSecondary,
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
                                Text("Direção  •  ${details.director}", color = BuroTextSecondary, fontSize = 15.sp)
                            }
                        }
                    }
                    details.cast?.toCastNames()?.takeIf(List<String>::isNotEmpty)?.let { cast ->
                        item(key = "series:cast") {
                            // The same lookup a film does. Without it a series showed initials in
                            // grey circles where the film beside it showed faces — the photos were
                            // available all along and simply never asked for here.
                            LaunchedEffect(cast) { onRequestCastPhotos(cast) }
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = stringResource(R.string.details_cast),
                                    color = BuroTextPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(Modifier.height(9.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(cast, key = { it.lowercase() }) { actor ->
                                        CastPersonChip(
                                            name = actor,
                                            onClick = { onOpenPerson(actor) },
                                            photoUrl = castPhotos[actor.lowercase()],
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
                                    backgroundColor = BuroSurface,
                                ) {
                                    Row(
                                        Modifier.fillMaxSize().padding(horizontal = 18.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = stringResource(R.string.series_season, season),
                                            color = BuroTextPrimary,
                                            fontSize = 21.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(
                                            text = "${episodes.size} episódios  ${if (expandedSeason == season) "▲" else "▼"}",
                                            color = BuroAccent,
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
                                                onCancelDownload = { onCancelEpisodeDownload(episode) },
                                                onDeleteDownload = { onDeleteEpisodeDownload(episode) },
                                                downloadState = downloadStateOf(episode),
                                                showDownloadAction = offlineSupported,
                                                enabled = !isResolvingPlayback,
                                                progress = episodeProgress[episode.id] ?: 0f,
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
    onCancelDownload: () -> Unit,
    onDeleteDownload: () -> Unit,
    downloadState: DownloadStateUi,
    showDownloadAction: Boolean,
    enabled: Boolean,
    /** How far into this episode the viewer is, 0..1. Zero means never started. */
    progress: Float,
    modifier: Modifier = Modifier,
) {
    FocusSurface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(98.dp),
        backgroundColor = BuroSurface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(width = 86.dp, height = 56.dp).clip(RoundedCornerShape(10.dp))
                    .background(Brush.linearGradient(listOf(BuroGold, BuroAccent))),
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
                // A tick over the thumbnail once it has been watched to the end, and a bar along
                // its foot while it is part-way. A season runs to twenty episodes and a series to
                // hundreds; without a mark on the row, remembering where you stopped is the
                // viewer's problem to solve.
                if (progress >= WATCHED_THRESHOLD) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(BuroCanvas.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = stringResource(R.string.series_episode_watched),
                            tint = BuroGold,
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = BuroTextPrimary,
                    )
                    if (progress > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .align(Alignment.BottomStart)
                                .background(BuroCanvas.copy(alpha = 0.7f)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress)
                                    .fillMaxSize()
                                    .background(BuroGold),
                            )
                        }
                    }
                }
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
                    color = BuroAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = episode.title,
                    color = BuroTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (showDownloadAction) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FocusSurface(
                        onClick =
                            when (downloadState) {
                                is DownloadStateUi.Running -> onCancelDownload
                                // Cancelling while it prepares: a second tap means "stop", not
                                // "start again", exactly as it does once bytes are moving.
                                DownloadStateUi.Preparing -> onCancelDownload
                                DownloadStateUi.Completed -> onDeleteDownload
                                DownloadStateUi.Idle,
                                DownloadStateUi.Failed,
                                -> onDownload
                            },
                        enabled = enabled,
                        modifier = Modifier.size(44.dp),
                        backgroundColor = BuroCanvas.copy(alpha = 0.55f),
                        focusedBackgroundColor = BuroAccent,
                    ) { focused ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector =
                                    when (downloadState) {
                                        DownloadStateUi.Completed -> Icons.Default.DownloadDone
                                        else -> Icons.Default.Download
                                    },
                                contentDescription =
                                    when (downloadState) {
                                        DownloadStateUi.Idle -> stringResource(R.string.download_episode)
                                        else -> downloadState.label()
                                    },
                                tint =
                                    when {
                                        focused -> BuroCanvas
                                        downloadState == DownloadStateUi.Failed -> BuroDanger
                                        else -> BuroAccent
                                    },
                            )
                        }
                    }
                    if (downloadState !is DownloadStateUi.Idle) {
                        Text(
                            text = downloadState.label(),
                            color = if (downloadState == DownloadStateUi.Failed) BuroDanger else BuroAccent,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** Watched enough to be treated as finished; the last few per cent are usually credits. */
private const val WATCHED_THRESHOLD = 0.92f

package com.lucasserafin94.iptvburo.ui.screens


import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PlayCircle
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.lucasserafin94.iptvburo.metadata.CriticScores
import com.lucasserafin94.iptvburo.R
import com.lucasserafin94.iptvburo.ui.designsystem.providerIdentityFor
import com.lucasserafin94.iptvburo.ui.DownloadStateUi
import com.lucasserafin94.iptvburo.ui.EpisodeUi
import com.lucasserafin94.iptvburo.ui.SeriesDetailsUi
import com.lucasserafin94.iptvburo.ui.capabilities.AndroidPlatformCapabilities
import com.lucasserafin94.iptvburo.ui.components.FocusSurface
import com.lucasserafin94.iptvburo.ui.designsystem.BuroAction
import com.lucasserafin94.iptvburo.ui.designsystem.BuroActionBar
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
    /** The category this series was filed under, which names its streaming service. */
    categoryName: String? = null,
    /** What the critics said, from OMDb. Null when no key is set. */
    criticScores: CriticScores? = null,
    /** The service TMDb says this title streams on, when the category names none. */
    providerName: String? = null,
    /** The service's official logo from TMDb, when the lookup found one. */
    providerLogoUrl: String? = null,
    details: SeriesDetailsUi?,
    isLoading: Boolean,
    hasError: Boolean,
    isResolvingPlayback: Boolean,
    hasPlaybackError: Boolean,
    onOpenEpisode: (EpisodeUi) -> Unit,
    onDownloadEpisode: (EpisodeUi) -> Unit,
    /** Queues every episode of one season. Null where offline storage is unavailable. */
    onDownloadSeason: ((Int) -> Unit)? = null,
    /** Queues every episode of every season. Null where offline storage is unavailable. */
    onDownloadSeries: (() -> Unit)? = null,
    onCancelEpisodeDownload: (EpisodeUi) -> Unit,
    onDeleteEpisodeDownload: (EpisodeUi) -> Unit,
    downloadStateOf: (EpisodeUi) -> DownloadStateUi,
    onOpenPerson: (String) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    isFavorite: Boolean = false,
    /** Null only where there is no series to favourite, which keeps the button honest. */
    onToggleFavorite: (() -> Unit)? = null,
    /** Whether this series is already marked for a reminder. */
    hasReminder: Boolean = false,
    /** Marks or unmarks this series for a reminder. Null hides the button entirely. */
    onToggleReminder: (() -> Unit)? = null,
    /** Sends this series to the system share sheet. Null hides the button entirely. */
    onShare: (() -> Unit)? = null,
    /** Opens the sheet that sends this title to a screen on the same network. Null hides it. */
    onCast: (() -> Unit)? = null,
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

        // A bulk download waiting to be confirmed, or null.
        //
        // Keyed to the title so opening another series cannot leave a dialogue behind that would
        // then queue the wrong show.
        var pendingBulkDownload by remember(details?.title) {
            mutableStateOf<BulkDownload?>(null)
        }
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
                    // The score has moved into the strip below, as on a film's page.
                    val facts = listOfNotNull(details.releaseDate, details.genre)
                    if (facts.isNotEmpty()) {
                        Text(
                            text = facts.joinToString("  •  "),
                            color = BuroAccent,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    // The category first, then what TMDb says.
                    //
                    // Most playlists file films by genre, so the category names no service and the
                    // badge would be missing on exactly the titles people ask about. TMDb knows
                    // where a title streams, and that answer already arrives with the logo.
                    val provider =
                        (providerIdentityFor(categoryName) ?: providerIdentityFor(providerName))
                            ?.copy(logoUrl = providerLogoUrl)
                    if (details.rating != null || provider != null) {
                        Spacer(Modifier.height(if (portrait) 12.dp else 14.dp))
                        RatingStrip(
                            rating = details.rating,
                            critics = criticScores,
                            provider = provider,
                            compact = portrait,
                        )
                    }
                }
                // Favourite and trailer together, the same pair a film gets. A series was the only
                // kind of title with no way to favourite it from its own page, which meant the one
                // thing people return to weekly was the one thing they could not mark.
                item(key = "series:actions") {
                    // One compact bar of glyphs, matching the film screen.
                    //
                    // These were six labelled pills in a FlowRow, which on a phone in portrait ate
                    // several lines before the episode list began. Every action is small, instant
                    // and taken rarely — the shape an icon suits — and none of them moved behind a
                    // menu, so nothing became harder to find than it was.
                    val trailerId = details.youtubeTrailerId
                    val anythingLeftToDownload =
                        details.episodes.any { episode ->
                            downloadStateOf(episode) != DownloadStateUi.Completed
                        }
                    BuroActionBar(
                        actions =
                            buildList {
                                // Always present, disabled when there is no toggle: a slot that
                                // appears only under its condition moves every action after it, and
                                // that is what made the row differ from one series to the next.
                                add(
                                    BuroAction(
                                        icon =
                                            if (isFavorite) {
                                                Icons.Default.Favorite
                                            } else {
                                                Icons.Default.FavoriteBorder
                                            },
                                        label =
                                            stringResource(
                                                if (isFavorite) {
                                                    R.string.details_favorite_added
                                                } else {
                                                    R.string.details_favorite_add
                                                },
                                            ),
                                        onClick = { onToggleFavorite?.invoke() },
                                        enabled = onToggleFavorite != null,
                                        active = isFavorite,
                                        activeTint = Color(0xFFE46C7A),
                                    ),
                                )
                                add(
                                    BuroAction(
                                        icon =
                                            if (hasReminder) {
                                                Icons.Default.Notifications
                                            } else {
                                                Icons.Default.NotificationsNone
                                            },
                                        label =
                                            stringResource(
                                                if (hasReminder) {
                                                    R.string.reminder_added
                                                } else {
                                                    R.string.reminder_add
                                                },
                                            ),
                                        onClick = { onToggleReminder?.invoke() },
                                        enabled = onToggleReminder != null,
                                        active = hasReminder,
                                        activeTint = BuroAccent,
                                    ),
                                )
                                // Downloading the lot stays confirmed rather than immediate: this is
                                // the one action here that can start eighty transfers and fill a
                                // phone, and it now sits among glyphs that do something small.
                                add(
                                    BuroAction(
                                        icon = Icons.Default.Download,
                                        label = stringResource(R.string.series_download_all),
                                        onClick = { pendingBulkDownload = BulkDownload.WholeSeries },
                                        enabled = onDownloadSeries != null && anythingLeftToDownload,
                                    ),
                                )
                                add(
                                    BuroAction(
                                        icon = Icons.Default.PlayCircle,
                                        label = stringResource(R.string.details_trailer),
                                        onClick = {
                                            trailerId?.let { id ->
                                                runCatching {
                                                    androidContext.startActivity(
                                                        Intent(
                                                            Intent.ACTION_VIEW,
                                                            Uri.parse(
                                                                "https://www.youtube.com/watch?v=$id",
                                                            ),
                                                        ),
                                                    )
                                                }
                                            }
                                        },
                                        enabled = trailerId != null,
                                    ),
                                )
                                add(
                                    BuroAction(
                                        icon = Icons.Default.Share,
                                        label = stringResource(R.string.details_share),
                                        onClick = { onShare?.invoke() },
                                        enabled = onShare != null,
                                    ),
                                )
                                add(
                                    BuroAction(
                                        icon = Icons.Default.Cast,
                                        label = stringResource(R.string.cast_action),
                                        onClick = { onCast?.invoke() },
                                        enabled = onCast != null,
                                    ),
                                )
                            },
                    )
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
                            // One season at a time, which is what most people actually want: the
                            // season they are about to start, not the whole run. Placed in the
                            // header so it is reachable without expanding, and confirmed for the
                            // same reason the series button is.
                            onDownloadSeason?.takeIf {
                                episodes.any { episode ->
                                    downloadStateOf(episode) != DownloadStateUi.Completed
                                }
                            }?.let {
                                item(key = "series:season:$season:download") {
                                    BuroButton(
                                        onClick = {
                                            pendingBulkDownload =
                                                BulkDownload.Season(season, episodes.size)
                                        },
                                        style = BuroButtonStyle.Secondary,
                                        modifier = Modifier.padding(top = 8.dp),
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null)
                                        Text(stringResource(R.string.series_download_season, season))
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

        // Confirming a bulk download.
        //
        // Drawn last so it sits above the list. The count is in the question rather than in a
        // separate line, because the number is the whole decision: twelve episodes and eighty are
        // very different answers to "is there room on this phone".
        pendingBulkDownload?.let { pending ->
            // Counts what will actually be fetched, not what the season contains: episodes already
            // on disk are skipped by the download itself, so including them here would promise
            // eighty transfers and start three. The two numbers have to come from the same rule.
            val episodeCount =
                details
                    ?.episodes
                    .orEmpty()
                    .filter { episode ->
                        when (pending) {
                            BulkDownload.WholeSeries -> true
                            is BulkDownload.Season -> episode.seasonNumber == pending.number
                        }
                    }
                    .count { episode -> downloadStateOf(episode) != DownloadStateUi.Completed }
            BulkDownloadDialog(
                episodeCount = episodeCount,
                seasonNumber = (pending as? BulkDownload.Season)?.number,
                onConfirm = {
                    when (pending) {
                        BulkDownload.WholeSeries -> onDownloadSeries?.invoke()
                        is BulkDownload.Season -> onDownloadSeason?.invoke(pending.number)
                    }
                    pendingBulkDownload = null
                },
                onDismiss = { pendingBulkDownload = null },
            )
        }
    }
}

/**
 * Asks before queueing a season or a whole series.
 *
 * Deliberately plain: a question, the count, and two buttons. The cancel action is the one a stray
 * tap outside the dialogue takes, because the expensive choice should never be the accidental one.
 */
@Composable
private fun BulkDownloadDialog(
    episodeCount: Int,
    seasonNumber: Int?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color(0xCC08090A))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .padding(24.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(BuroSurface)
                    .padding(22.dp)
                    // Swallows taps on the card itself, so pressing the text does not dismiss.
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
        ) {
            Text(
                text =
                    seasonNumber
                        ?.let { stringResource(R.string.series_download_season_confirm_title, it) }
                        ?: stringResource(R.string.series_download_all_confirm_title),
                color = BuroTextPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text =
                    pluralStringResource(
                        R.plurals.series_download_confirm_body,
                        episodeCount,
                        episodeCount,
                    ),
                color = BuroTextSecondary,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(18.dp))
            // A Row with equal weights, not a FlowRow.
            //
            // The two buttons do not fit the card's width side by side, so a FlowRow wrapped them
            // and Cancelar ended up alone on a line above Baixar, reading as a link rather than a
            // choice. Sharing the width keeps them side by side at any size, and keeps the safe
            // option as visibly a button as the expensive one.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BuroButton(
                    onClick = onDismiss,
                    style = BuroButtonStyle.Secondary,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.common_cancel))
                }
                BuroButton(onClick = onConfirm, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    // One line, always. Equal weights leave each button about half the card, and
                    // the icon eats into that, so "Baixar" was wrapping into "Baix / ar".
                    Text(stringResource(R.string.download_action), maxLines = 1)
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
                        model = ImageRequest.Builder(LocalPlatformContext.current).data(artwork).build(),
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

/**
 * A bulk download the user has asked for but not yet confirmed.
 *
 * Confirmed rather than immediate because these are the only buttons on the screen that can start
 * dozens of transfers and fill a phone's storage, and they sit beside buttons that do something
 * small and instant. The count travels with the request so the dialogue can state it — "download
 * twelve episodes?" is a question someone can answer, "download the season?" is not.
 */
private sealed interface BulkDownload {
    data object WholeSeries : BulkDownload

    data class Season(val number: Int, val episodeCount: Int) : BulkDownload
}

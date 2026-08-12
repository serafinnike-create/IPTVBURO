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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import com.lucasserafin94.iptvburo.ui.MovieDetailsUi
import com.lucasserafin94.iptvburo.ui.capabilities.AndroidPlatformCapabilities
import com.lucasserafin94.iptvburo.ui.components.FocusSurface
import com.lucasserafin94.iptvburo.ui.designsystem.BuroButton
import com.lucasserafin94.iptvburo.ui.designsystem.BuroButtonStyle
import com.lucasserafin94.iptvburo.ui.designsystem.BuroErrorState
import com.lucasserafin94.iptvburo.ui.theme.BuroAccent
import com.lucasserafin94.iptvburo.ui.theme.BuroCanvas
import com.lucasserafin94.iptvburo.ui.theme.BuroDanger
import com.lucasserafin94.iptvburo.ui.theme.BuroSurface
import com.lucasserafin94.iptvburo.ui.theme.BuroTextPrimary
import com.lucasserafin94.iptvburo.ui.theme.BuroTextSecondary

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
    /** Sends this title to the system share sheet. Null hides the button entirely. */
    onShare: (() -> Unit)? = null,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onDeleteDownload: () -> Unit,
    downloadState: DownloadStateUi,
    onOpenPerson: (String) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    /** How far the viewer already is, 0..1. Null when unwatched, which draws no bar at all. */
    watchedFraction: Float? = null,
    /** Photos already resolved, by lower-cased name. Absent means not looked up yet. */
    castPhotos: Map<String, String?> = emptyMap(),
    onRequestCastPhotos: (List<String>) -> Unit = {},
    offlineSupported: Boolean = AndroidPlatformCapabilities.offlineSupported,
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

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(BuroCanvas)) {
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
                        listOf(Color(0x22090A0D), Color(0xAA090A0D), BuroCanvas),
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
                            listOf(BuroCanvas, Color(0xE6090A0D), Color.Transparent),
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
                    focusedBackgroundColor = BuroSurface,
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = BuroTextPrimary,
                        )
                    }
                }
            }

            item("hero-space") { Spacer(Modifier.height(if (portrait) 150.dp else 160.dp)) }

            item("title") {
                Column(modifier = Modifier.width(contentWidth)) {
                    Text(
                        text = details?.title ?: fallbackTitle,
                        color = BuroTextPrimary,
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
                            color = BuroAccent,
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
                        color = if (hasPlaybackError) BuroDanger else BuroAccent,
                    )
                }
            }

            watchedFraction?.let { fraction ->
                item("progress") {
                    Column(modifier = Modifier.width(contentWidth)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(BuroTextPrimary.copy(alpha = 0.18f)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(BuroAccent),
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.details_watched_percent, (fraction * 100f).toInt()),
                            color = BuroTextSecondary,
                            fontSize = 12.sp,
                        )
                    }
                }
            }

            item("actions") {
                // FlowRow, not Row. On a phone the first two buttons consume the full width, and
                // in a plain Row everything after them was laid out past the right edge: Baixar and
                // Trailer existed, were reported missing, and could not be reached by any gesture
                // because the row did not scroll either. Wrapping keeps every action on screen at
                // any width, and still forms a single line where there is room.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
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
                            tint = if (isFavorite) Color(0xFFE46C7A) else BuroTextPrimary,
                        )
                        // Both labels measured, the longer one drawn invisibly underneath. The two
                        // words differ in width ("Favoritar" / "Favoritado"), so the button
                        // resized on every tap and the FlowRow reflowed — the button appeared to
                        // jump to the next line at the moment it was pressed.
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
                    if (offlineSupported) {
                        BuroButton(
                            onClick =
                                when (downloadState) {
                                    is DownloadStateUi.Running -> onCancelDownload
                                    DownloadStateUi.Preparing -> onCancelDownload
                                    DownloadStateUi.Completed -> onDeleteDownload
                                    DownloadStateUi.Idle,
                                    DownloadStateUi.Failed,
                                    -> onDownload
                                },
                            enabled = !isLoading && !isResolvingPlayback,
                            style = BuroButtonStyle.Secondary,
                        ) {
                            Icon(
                                imageVector =
                                    when (downloadState) {
                                        DownloadStateUi.Completed -> Icons.Default.DownloadDone
                                        else -> Icons.Default.Download
                                    },
                                contentDescription = null,
                                tint =
                                    when (downloadState) {
                                        DownloadStateUi.Failed -> BuroDanger
                                        else -> BuroTextPrimary
                                    },
                            )
                            Text(downloadState.label())
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
                            Text(stringResource(R.string.details_trailer))
                        }
                    }
                    // Beside Trailer, and deliberately outside the block above: a film with no
                    // trailer still has something to share, and nesting this inside that `let`
                    // would hide sharing on every title the provider gave no trailer id for.
                    //
                    // Last in the row rather than beside Favoritar, where it was: five buttons do
                    // not fit one line on a phone, so the FlowRow wrapped and Compartilhar landed
                    // on a second line that reads as the button being absent.
                    onShare?.let { share ->
                        BuroButton(
                            onClick = share,
                            // Enabled while the full record is still loading: the title and year
                            // come from the catalogue row, so a share is complete without it.
                            style = BuroButtonStyle.Secondary,
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null)
                            Text(stringResource(R.string.details_share))
                        }
                    }
                }
            }

            when {
                isLoading -> item("loading") { Text("Carregando ficha completa…", color = BuroTextSecondary) }
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
                                color = BuroTextPrimary,
                                fontSize = if (portrait) 16.sp else 18.sp,
                                lineHeight = if (portrait) 24.sp else 28.sp,
                                modifier = Modifier.width(contentWidth),
                            )
                        }
                    }
                    details.cast?.toCastNames()?.takeIf(List<String>::isNotEmpty)?.let { cast ->
                        item("cast") {
                            // Asked for once the names are known, not on every recomposition: the
                            // view model caches hits and misses alike, so a redraw costs nothing.
                            LaunchedEffect(cast) { onRequestCastPhotos(cast) }
                            Column(modifier = Modifier.width(contentWidth)) {
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

/**
 * Human label for an offline copy.
 *
 * A provider that omits `Content-Length` leaves the fraction negative; that reads as an unbounded
 * "downloading" rather than as a wrong percentage.
 */
@Composable
internal fun DownloadStateUi.label(): String =
    when (this) {
        DownloadStateUi.Idle -> stringResource(R.string.download_action)
        DownloadStateUi.Preparing -> stringResource(R.string.download_preparing)
        is DownloadStateUi.Running ->
            if (fraction < 0f) {
                stringResource(R.string.download_running_unknown)
            } else {
                stringResource(R.string.download_running, (fraction * 100f).toInt())
            }

        DownloadStateUi.Completed -> stringResource(R.string.download_completed)
        DownloadStateUi.Failed -> stringResource(R.string.download_failed)
    }

@Composable
private fun DetailFact(
    label: String,
    value: String?,
) {
    if (!value.isNullOrBlank()) {
        Text(
            text = "$label  •  $value",
            color = BuroTextSecondary,
            fontSize = 15.sp,
            lineHeight = 22.sp,
        )
    }
}

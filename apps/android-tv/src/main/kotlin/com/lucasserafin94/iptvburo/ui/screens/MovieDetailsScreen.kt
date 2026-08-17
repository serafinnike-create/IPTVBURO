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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Cast
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
import coil3.request.ImageRequest
import com.lucasserafin94.iptvburo.metadata.CriticScores
import com.lucasserafin94.iptvburo.R
import com.lucasserafin94.iptvburo.ui.designsystem.providerIdentityFor
import com.lucasserafin94.iptvburo.ui.DownloadStateUi
import com.lucasserafin94.iptvburo.ui.MovieDetailsUi
import com.lucasserafin94.iptvburo.ui.capabilities.AndroidPlatformCapabilities
import com.lucasserafin94.iptvburo.ui.components.FocusSurface
import com.lucasserafin94.iptvburo.ui.designsystem.BuroAction
import com.lucasserafin94.iptvburo.ui.designsystem.BuroActionBar
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
    /**
     * The category this title was filed under, which is the only clue to its streaming service.
     *
     * Null when it came from somewhere with no category — a share link, say — and the platform
     * badge is simply left off rather than guessed at.
     */
    categoryName: String?,
    /** What the critics said, from OMDb. Null when no key is set. */
    criticScores: CriticScores? = null,
    /** The service TMDb says this title streams on, when the category names none. */
    providerName: String? = null,
    /** The service's official logo from TMDb, when the lookup found one. */
    providerLogoUrl: String? = null,
    details: MovieDetailsUi?,
    isLoading: Boolean,
    hasError: Boolean,
    isResolvingPlayback: Boolean,
    hasPlaybackError: Boolean,
    onPlay: () -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    /** Whether this title is already marked for a reminder. */
    hasReminder: Boolean = false,
    /** Marks or unmarks this title for a reminder. Null hides the button entirely. */
    onToggleReminder: (() -> Unit)? = null,
    /** Sends this title to the system share sheet. Null hides the button entirely. */
    onShare: (() -> Unit)? = null,
    /** Opens the sheet that sends this title to a screen on the same network. Null hides it. */
    onCast: (() -> Unit)? = null,
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
                    // The score has moved out of this line and into the strip below, where it is
                    // read as a figure rather than as one more fact among four.
                    val facts =
                        listOfNotNull(
                            details?.releaseDate,
                            details?.duration,
                            details?.genre,
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

                    // The category first, then what TMDb says.
                    //
                    // Most playlists file films by genre, so the category names no service and the
                    // badge would be missing on exactly the titles people ask about. TMDb knows
                    // where a title streams, and that answer already arrives with the logo.
                    val provider =
                        (providerIdentityFor(categoryName) ?: providerIdentityFor(providerName))
                            ?.copy(logoUrl = providerLogoUrl)
                    if (details?.rating != null || provider != null) {
                        Spacer(Modifier.height(if (portrait) 12.dp else 14.dp))
                        RatingStrip(
                            rating = details?.rating,
                            voteCount = details?.voteCount,
                            critics = criticScores,
                            provider = provider,
                            compact = portrait,
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
                // Assistir keeps its full labelled button; everything else is a glyph in the bar
                // below it.
                //
                // The six secondary actions were pill buttons in a FlowRow, each carrying its own
                // word. In portrait that filled three lines before the synopsis started, so the page
                // opened on a wall of controls rather than on the film. They are all small, instant
                // actions taken rarely — the shape an icon suits.
                //
                // Nothing moved behind a menu: hiding Compartilhar or Trailer under a "⋮" would save
                // the same space and cost the user any clue they exist.
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    BuroButton(
                        onClick = onPlay,
                        enabled = !isLoading && !isResolvingPlayback,
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Text("Assistir")
                    }

                    val trailerId = details?.youtubeTrailerId
                    BuroActionBar(
                        actions =
                            buildList {
                                add(
                                    BuroAction(
                                        icon =
                                            if (isFavorite) {
                                                Icons.Default.Favorite
                                            } else {
                                                Icons.Default.FavoriteBorder
                                            },
                                        // The label follows the state, as the pill's did: the button
                                        // says what the title *is*, not what pressing would do.
                                        label =
                                            stringResource(
                                                if (isFavorite) {
                                                    R.string.details_favorite_added
                                                } else {
                                                    R.string.details_favorite_add
                                                },
                                            ),
                                        onClick = onToggleFavorite,
                                        enabled = !isLoading,
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
                                        // A reminder is about a title rather than a stream, so it
                                        // stays available while the full record loads.
                                        enabled = onToggleReminder != null,
                                        active = hasReminder,
                                        activeTint = BuroAccent,
                                    ),
                                )
                                if (offlineSupported) {
                                    add(
                                        BuroAction(
                                            icon =
                                                when (downloadState) {
                                                    DownloadStateUi.Completed -> Icons.Default.DownloadDone
                                                    else -> Icons.Default.Download
                                                },
                                            label = downloadState.label(),
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
                                            active = downloadState == DownloadStateUi.Completed,
                                            activeTint =
                                                if (downloadState == DownloadStateUi.Failed) {
                                                    BuroDanger
                                                } else {
                                                    null
                                                },
                                        ),
                                    )
                                }
                                // Always present, disabled when the provider gave no trailer id.
                                // Reported from a phone: the buttons "behave differently on every
                                // film" — because a slot that appears only under its condition moves
                                // everything after it.
                                add(
                                    BuroAction(
                                        icon = Icons.Default.PlayCircle,
                                        label = stringResource(R.string.details_trailer),
                                        onClick = {
                                            trailerId?.let { id ->
                                                val uri =
                                                    Uri.parse("https://www.youtube.com/watch?v=$id")
                                                runCatching {
                                                    androidContext.startActivity(
                                                        Intent(Intent.ACTION_VIEW, uri),
                                                    )
                                                }
                                            }
                                        },
                                        enabled = trailerId != null,
                                    ),
                                )
                                // Enabled while the full record loads: the title and year come from
                                // the catalogue row, so a share is complete without it.
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

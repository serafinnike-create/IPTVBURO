package com.lucasserafin94.iptvburo.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.lucasserafin94.iptvburo.R
import com.lucasserafin94.iptvburo.ui.components.FocusSurface
import com.lucasserafin94.iptvburo.ui.theme.Blue
import com.lucasserafin94.iptvburo.ui.theme.Ink
import com.lucasserafin94.iptvburo.ui.theme.Muted
import com.lucasserafin94.iptvburo.ui.theme.Surface
import com.lucasserafin94.iptvburo.ui.theme.Teal
import com.lucasserafin94.iptvburo.ui.theme.White

@Composable
fun BuroHero(
    item: HomeItem,
    sourceCount: Int,
    onItemFocused: (String) -> Unit,
    onOpenItem: (String) -> Unit,
    onOpenSources: () -> Unit,
    modifier: Modifier = Modifier,
    requestFocus: Boolean = false,
) {
    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Ink)
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.09f),
                shape = RoundedCornerShape(28.dp),
            ),
    ) {
        val phonePortrait = maxWidth < 600.dp
        val compact = maxWidth < 900.dp
        val horizontalPadding =
            when {
                phonePortrait -> 18.dp
                compact -> 28.dp
                else -> 48.dp
            }
        val titleSize =
            when {
                phonePortrait -> 30.sp
                compact -> 36.sp
                else -> 50.sp
            }
        val bodySize = if (phonePortrait) 14.sp else if (compact) 15.sp else 18.sp

        if (item.remoteArtworkUrl != null) {
            RemoteHomeArtwork(
                url = item.remoteArtworkUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Image(
                painter = painterResource(R.drawable.buro_nocturne_hero),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                alignment = Alignment.CenterEnd,
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colorStops =
                                arrayOf(
                                    0f to Ink.copy(alpha = 0.98f),
                                    0.42f to Ink.copy(alpha = 0.84f),
                                    0.72f to Ink.copy(alpha = 0.26f),
                                    1f to Ink.copy(alpha = 0.08f),
                                ),
                        ),
                    )
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Ink.copy(alpha = 0.14f),
                                Color.Transparent,
                                Ink.copy(alpha = 0.48f),
                            ),
                        ),
                    ),
        )

        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(
                    when {
                        phonePortrait -> 1f
                        compact -> 0.74f
                        else -> 0.62f
                    },
                )
                .padding(
                    start = horizontalPadding,
                    end = if (phonePortrait) horizontalPadding else 0.dp,
                    top = if (compact) 28.dp else 40.dp,
                    bottom = if (compact) 28.dp else 40.dp,
                ),
            verticalArrangement = Arrangement.Center,
        ) {
            DemoBadge(text = item.badge)
            Spacer(Modifier.height(14.dp))
            Text(
                text = item.title,
                color = White,
                fontSize = titleSize,
                lineHeight = titleSize * 1.03f,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = item.metadata,
                color = Teal,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(if (compact) 10.dp else 14.dp))
            Text(
                text = item.synopsis,
                color = White.copy(alpha = 0.86f),
                fontSize = bodySize,
                lineHeight = bodySize * 1.35f,
                maxLines = if (phonePortrait) 3 else if (compact) 2 else 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(if (compact) 18.dp else 24.dp))
            val sourcesLabel =
                if (sourceCount == 0) {
                    stringResource(R.string.buro_home_view_sources)
                } else {
                    stringResource(
                        R.string.buro_home_view_sources_count,
                        sourceCount,
                    )
                }
            if (phonePortrait) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    HeroAction(
                        label = if (item.isDemonstration) stringResource(R.string.buro_home_view_story) else "Ver detalhes",
                        icon = Icons.Default.Info,
                        primary = true,
                        onClick = { onOpenItem(item.id) },
                        onFocused = { onItemFocused(item.id) },
                        requestFocus = requestFocus,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    HeroAction(
                        label = sourcesLabel,
                        icon = Icons.Default.FolderOpen,
                        primary = false,
                        onClick = onOpenSources,
                        onFocused = { onItemFocused(item.id) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HeroAction(
                        label = if (item.isDemonstration) stringResource(R.string.buro_home_view_story) else "Ver detalhes",
                        icon = Icons.Default.Info,
                        primary = true,
                        onClick = { onOpenItem(item.id) },
                        onFocused = { onItemFocused(item.id) },
                        requestFocus = requestFocus,
                    )
                    HeroAction(
                        label = sourcesLabel,
                        icon = Icons.Default.FolderOpen,
                        primary = false,
                        onClick = onOpenSources,
                        onFocused = { onItemFocused(item.id) },
                    )
                }
            }
        }
    }
}

@Composable
fun BuroPosterCard(
    item: HomeItem,
    onItemFocused: (String) -> Unit,
    onOpenItem: (String) -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 168.dp,
    requestFocus: Boolean = false,
) {
    require(item.cardFormat == HomeCardFormat.POSTER) {
        "BuroPosterCard only accepts poster items."
    }
    BuroArtworkCard(
        item = item,
        onItemFocused = onItemFocused,
        onOpenItem = onOpenItem,
        modifier = modifier
            .width(width)
            .aspectRatio(2f / 3f),
        requestFocus = requestFocus,
        compactTitle = false,
    )
}

@Composable
fun BuroLandscapeCard(
    item: HomeItem,
    onItemFocused: (String) -> Unit,
    onOpenItem: (String) -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 292.dp,
    requestFocus: Boolean = false,
) {
    require(item.cardFormat == HomeCardFormat.LANDSCAPE) {
        "BuroLandscapeCard only accepts landscape items."
    }
    BuroArtworkCard(
        item = item,
        onItemFocused = onItemFocused,
        onOpenItem = onOpenItem,
        modifier = modifier
            .width(width)
            .aspectRatio(16f / 9f),
        requestFocus = requestFocus,
        compactTitle = true,
    )
}

@Composable
fun BuroHomeProgress(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val safeProgress = progress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .height(5.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.22f))
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = safeProgress,
                    range = 0f..1f,
                )
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(safeProgress)
                .background(Brush.horizontalGradient(listOf(Teal, Blue))),
        )
    }
}

@Composable
internal fun BuroStaticArtwork(
    item: HomeItem,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.09f),
                shape = RoundedCornerShape(24.dp),
            ),
    ) {
        ArtworkFallback(
            item = item,
            isFocused = false,
            compactTitle = item.cardFormat == HomeCardFormat.LANDSCAPE,
        )
    }
}

@Composable
private fun BuroArtworkCard(
    item: HomeItem,
    onItemFocused: (String) -> Unit,
    onOpenItem: (String) -> Unit,
    modifier: Modifier,
    requestFocus: Boolean,
    compactTitle: Boolean,
) {
    val focusRequester = androidx.compose.runtime.remember(item.id) { FocusRequester() }
    val openDescription = if (item.kind == HomeItemKind.SOURCE) {
        stringResource(R.string.buro_home_a11y_open_source, item.title)
    } else {
        stringResource(R.string.buro_home_a11y_open_story, item.title)
    }

    LaunchedEffect(requestFocus, item.id) {
        if (requestFocus) focusRequester.requestFocus()
    }

    FocusSurface(
        onClick = { onOpenItem(item.id) },
        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                if (focusState.isFocused) onItemFocused(item.id)
            }
            .semantics {
                contentDescription = openDescription
            },
        backgroundColor = Color.Transparent,
    ) { isFocused ->
        ArtworkFallback(
            item = item,
            isFocused = isFocused,
            compactTitle = compactTitle,
        )
    }
}

@Composable
private fun ArtworkFallback(
    item: HomeItem,
    isFocused: Boolean,
    compactTitle: Boolean,
) {
    val palette = item.palette.colors()
    val artworkResource = item.artwork.drawableResource()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        palette.first,
                        palette.second,
                        Ink,
                    ),
                ),
            ),
    ) {
        if (item.remoteArtworkUrl != null) {
            RemoteHomeArtwork(
                url = item.remoteArtworkUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else if (artworkResource != null) {
            Image(
                painter = painterResource(artworkResource),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(palette.first.copy(alpha = if (isFocused) 0.04f else 0.1f)),
            )
        } else {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 18.dp, end = 18.dp)
                    .size(if (compactTitle) 64.dp else 76.dp)
                    .rotate(18f)
                    .border(
                        width = if (isFocused) 3.dp else 2.dp,
                        color = Color.White.copy(alpha = if (isFocused) 0.32f else 0.17f),
                        shape = RoundedCornerShape(22.dp),
                    ),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(if (compactTitle) 78.dp else 92.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (isFocused) 0.13f else 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = item.title.firstOrNull()?.uppercase() ?: "B",
                    color = White.copy(alpha = 0.82f),
                    fontSize = if (compactTitle) 34.sp else 42.sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(if (compactTitle) 0.66f else 0.54f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Ink.copy(alpha = 0.97f)),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(
                    start = if (compactTitle) 16.dp else 18.dp,
                    end = if (compactTitle) 16.dp else 18.dp,
                    bottom = if (compactTitle) 14.dp else 18.dp,
                ),
        ) {
            Text(
                text = item.badge,
                color = Teal,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.7.sp,
                maxLines = 1,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = item.title,
                color = White,
                fontSize = if (compactTitle) 17.sp else 19.sp,
                lineHeight = if (compactTitle) 20.sp else 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = if (compactTitle) 1 else 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!compactTitle) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = item.subtitle,
                    color = Muted,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            item.progress?.let { progress ->
                Spacer(Modifier.height(9.dp))
                BuroHomeProgress(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun RemoteHomeArtwork(
    url: String,
    contentDescription: String?,
    modifier: Modifier,
    contentScale: ContentScale,
) {
    val context = LocalPlatformContext.current
    val request =
        remember(url, context) {
            ImageRequest.Builder(context)
                .data(url)
                .diskCachePolicy(CachePolicy.DISABLED)
                .build()
        }
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
    )
}

@Composable
private fun HeroAction(
    label: String,
    icon: ImageVector,
    primary: Boolean,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
    requestFocus: Boolean = false,
) {
    val focusRequester = androidx.compose.runtime.remember { FocusRequester() }
    LaunchedEffect(requestFocus) {
        if (requestFocus) focusRequester.requestFocus()
    }

    FocusSurface(
        onClick = onClick,
        modifier = modifier
            .height(52.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                if (focusState.isFocused) onFocused()
            },
        backgroundColor = if (primary) Teal else Surface.copy(alpha = 0.88f),
        focusedBackgroundColor = if (primary) Teal else Surface,
        selectedBackgroundColor = if (primary) Teal else Surface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (primary) Ink else Teal,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(9.dp))
            Text(
                text = label,
                color = if (primary) Ink else White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun DemoBadge(text: String) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(Ink.copy(alpha = 0.62f))
            .border(
                width = 1.dp,
                color = Teal.copy(alpha = 0.5f),
                shape = CircleShape,
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            color = Teal,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
    }
}

internal fun HomeArtworkPalette.colors(): Pair<Color, Color> = when (this) {
    HomeArtworkPalette.AURORA -> Color(0xFF126E75) to Color(0xFF293B85)
    HomeArtworkPalette.COBALT -> Color(0xFF173C7A) to Color(0xFF251A56)
    HomeArtworkPalette.EMBER -> Color(0xFF8B3D2F) to Color(0xFF39214F)
    HomeArtworkPalette.FOREST -> Color(0xFF1C624D) to Color(0xFF173247)
    HomeArtworkPalette.PLUM -> Color(0xFF6B326E) to Color(0xFF26245C)
    HomeArtworkPalette.SOLAR -> Color(0xFF8C6524) to Color(0xFF713545)
}

private fun HomeArtwork?.drawableResource(): Int? =
    when (this) {
        HomeArtwork.PAPER_SUN -> R.drawable.buro_paper_sun
        HomeArtwork.FOREST_SIGNAL -> R.drawable.buro_forest_signal
        null -> null
    }

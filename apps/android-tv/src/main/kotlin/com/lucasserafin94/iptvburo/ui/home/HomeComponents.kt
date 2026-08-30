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
import coil3.request.ImageRequest
import com.lucasserafin94.iptvburo.R
import com.lucasserafin94.iptvburo.ui.screens.MutedTrailerBackdrop
import com.lucasserafin94.iptvburo.ui.designsystem.ProviderMark
import com.lucasserafin94.iptvburo.ui.designsystem.rememberProviderIdentity
import com.lucasserafin94.iptvburo.ui.designsystem.providerIdentityFor
import com.lucasserafin94.iptvburo.ui.designsystem.BuroMarqueeText
import com.lucasserafin94.iptvburo.ui.components.FocusSurface
import com.lucasserafin94.iptvburo.ui.theme.BuroAccent
import com.lucasserafin94.iptvburo.ui.theme.BuroCanvas
import com.lucasserafin94.iptvburo.ui.theme.BuroGold
import com.lucasserafin94.iptvburo.ui.theme.BuroSurface
import com.lucasserafin94.iptvburo.ui.theme.BuroTextPrimary
import com.lucasserafin94.iptvburo.ui.theme.BuroTextSecondary

@Composable
fun BuroHero(
    item: HomeItem,
    sourceCount: Int,
    onItemFocused: (String) -> Unit,
    onOpenItem: (String) -> Unit,
    onOpenSources: () -> Unit,
    modifier: Modifier = Modifier,
    requestFocus: Boolean = false,
    /**
     * The trailer to play behind the title, or null to show the artwork.
     *
     * Null covers every reason not to play — no trailer, one that already failed, something the
     * viewer chose already playing. The banner does not decide any of that; see BannerTrailer.
     */
    trailerId: String? = null,
) {
    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(BuroCanvas)
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
        // The trailer plays over the artwork, which stays underneath as the fallback.
        //
        // MutedTrailerBackdrop removes itself from the composition when the embed fails rather than
        // fading to nothing, so a trailer that will not load leaves the poster exactly as it was —
        // no black rectangle on the opening screen. It also waits before appearing, which is what
        // keeps the rotation from opening a video per title.
        if (trailerId != null) {
            MutedTrailerBackdrop(
                youtubeId = trailerId,
                modifier = Modifier.fillMaxSize(),
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
                                    0f to BuroCanvas.copy(alpha = 0.98f),
                                    0.42f to BuroCanvas.copy(alpha = 0.84f),
                                    0.72f to BuroCanvas.copy(alpha = 0.26f),
                                    1f to BuroCanvas.copy(alpha = 0.08f),
                                ),
                        ),
                    )
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                BuroCanvas.copy(alpha = 0.14f),
                                Color.Transparent,
                                BuroCanvas.copy(alpha = 0.48f),
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
                color = BuroTextPrimary,
                fontSize = titleSize,
                lineHeight = titleSize * 1.03f,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = item.metadata,
                color = BuroAccent,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(if (compact) 10.dp else 14.dp))
            Text(
                text = item.synopsis,
                color = BuroTextPrimary.copy(alpha = 0.86f),
                fontSize = bodySize,
                lineHeight = bodySize * 1.35f,
                maxLines = if (phonePortrait) 3 else if (compact) 2 else 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(if (compact) 18.dp else 24.dp))
            // One action, not two. The banner advertises a title, so the only thing it should offer
            // is that title — "Ver fontes" was source administration sitting on the most prominent
            // surface in the app, where the desktop puts Watch. Sources remain reachable from the
            // navigation, which is where managing them belongs.
            HeroAction(
                label =
                    if (item.isDemonstration) {
                        stringResource(R.string.buro_home_view_story)
                    } else {
                        stringResource(R.string.buro_home_view_details)
                    },
                icon = Icons.Default.Info,
                primary = true,
                onClick = { onOpenItem(item.id) },
                onFocused = { onItemFocused(item.id) },
                requestFocus = requestFocus,
                modifier = if (phonePortrait) Modifier.fillMaxWidth() else Modifier,
            )
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
            // The poster's own ratio plus the caption beneath it, rather than the ratio alone.
            //
            // FocusSurface propagates its minimum constraints to the content, so a card sized only
            // by its artwork gives the Column inside exactly the artwork's height — and the caption
            // is laid out past the bottom edge, where it is clipped. That is precisely what
            // happened on the first attempt at this: clean posters, no captions anywhere.
            .height(width * 3f / 2f + POSTER_CAPTION_HEIGHT),
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
            .height(width * 9f / 16f + LANDSCAPE_CAPTION_HEIGHT),
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
                // Solid gold, matching the Windows progress bar. The ivory-to-gold gradient read as
                // a different component on each platform.
                .background(BuroGold),
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
    // Artwork above, caption below, rather than text laid across the poster.
    //
    // The overlay version ran a gradient over the bottom half of every card — 54% of a poster, 66%
    // of a landscape one — and put the title, subtitle and metadata on top of it. On a home screen
    // built out of cover art, that is most of the art covered by writing. The caption sits under
    // the image now: the poster is whole, and the words are still right beside it.
    Column(modifier = Modifier.fillMaxWidth()) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(if (compactTitle) 16f / 9f else 2f / 3f)
            .clip(RoundedCornerShape(if (compactTitle) 18.dp else 20.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        palette.first,
                        palette.second,
                        BuroCanvas,
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
                    color = BuroTextPrimary.copy(alpha = 0.82f),
                    fontSize = if (compactTitle) 34.sp else 42.sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }

        // The streaming service, in its own colour, on the artwork it belongs to.
        //
        // Only where the category names one — providers file platform catalogues as
        // "Series | Netflix" and everything else by genre, so most rails show nothing here.
        rememberProviderIdentity(item.categoryName)?.let { provider ->
            ProviderMark(
                provider = provider,
                size = 26.dp,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
            )
        }

        // Progress stays on the artwork: it describes the image it sits on, and a bar under the
        // caption would read as belonging to the text.
        item.progress?.let { progress ->
            BuroHomeProgress(
                progress = progress,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            )
        }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, start = 2.dp, end = 2.dp),
        ) {
            Text(
                text = item.badge,
                color = BuroAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.7.sp,
                maxLines = 1,
            )
            Spacer(Modifier.height(4.dp))
            // One line, which scrolls itself while this card is the one being looked at.
            //
            // Two lines were fine when the caption floated over the artwork and could take the room
            // it needed. In a fixed-height caption a two-line title pushes the subtitle and
            // metadata past the bottom — so the title is held to one line, and the part that would
            // have been lost to an ellipsis is revealed by scrolling instead.
            BuroMarqueeText(
                text = item.title,
                active = isFocused,
                color = BuroTextPrimary,
                fontSize = if (compactTitle) 17.sp else 19.sp,
                lineHeight = if (compactTitle) 20.sp else 22.sp,
                fontWeight = FontWeight.Bold,
            )
            if (!compactTitle) {
                // The caption lines, with repeats dropped.
                //
                // A card's badge, subtitle and metadata are built from whatever the catalogue
                // supplied, and for some rails two of them land on the same value — a reminder with
                // no release date showed "LEMBRETE" three times in a column, and a film with only a
                // year showed "2026" twice. Invisible while the caption sat over the artwork and
                // plainly wrong once the lines are stacked in the open.
                val extraLines =
                    listOf(item.subtitle, item.metadata)
                        .map(String::trim)
                        .filter { line -> line.isNotBlank() && !line.equals(item.badge, ignoreCase = true) }
                        .distinctBy { line -> line.lowercase() }
                extraLines.firstOrNull()?.let { subtitle ->
                Spacer(Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    color = BuroTextSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                }
                extraLines.getOrNull(1)?.let { metadata ->
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = metadata,
                        color = BuroTextPrimary.copy(alpha = 0.82f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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

    // Gold, not ivory. The Windows primary button is gold, and a product whose main call to action
    // changes brand colour between platforms does not read as one product.
    //
    // Translucent at rest, though, and solid the moment it is focused or pressed. A filled gold
    // slab sits on top of the banner's own artwork and hides the part of the poster directly behind
    // it — on a phone, where the banner is most of the screen, that is a sizeable bite out of the
    // one image the home page is built around. At this alpha the brand colour still reads as a
    // button while the picture shows through; focus restores the full fill, so the control is at
    // its most legible exactly when somebody is aiming at it.
    FocusSurface(
        onClick = onClick,
        modifier = modifier
            .height(52.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                if (focusState.isFocused) onFocused()
            },
        backgroundColor =
            if (primary) BuroGold.copy(alpha = HERO_ACTION_REST_ALPHA) else BuroSurface.copy(alpha = 0.88f),
        focusedBackgroundColor = if (primary) BuroGold else BuroSurface,
        selectedBackgroundColor = if (primary) BuroGold else BuroSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (primary) BuroCanvas else BuroGold,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(9.dp))
            Text(
                text = label,
                color = if (primary) BuroCanvas else BuroTextPrimary,
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
            .background(BuroCanvas.copy(alpha = 0.62f))
            .border(
                width = 1.dp,
                color = BuroAccent.copy(alpha = 0.5f),
                shape = CircleShape,
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            color = BuroAccent,
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

/**
 * How solid the banner's call to action is when nothing is focused.
 *
 * High enough that the dark label on top keeps its contrast — the text is canvas-coloured, so a
 * thin wash of gold over a bright poster would leave it unreadable — and low enough that the
 * artwork behind the button is still visible rather than covered by a slab. Focus and press paint
 * the full colour, so the control is at its most legible when it is being aimed at.
 */
private const val HERO_ACTION_REST_ALPHA = 0.72f

/**
 * Height reserved under a poster for its caption.
 *
 * Fixed rather than measured, so every card in a rail is the same height whatever its title runs
 * to — a row where each card ended at a different point would read as broken rather than as
 * variable. Enough for the badge, two lines of title, the subtitle and the metadata line.
 */
private val POSTER_CAPTION_HEIGHT = 94.dp

/** The same, for a landscape card: badge and one line of title, so it needs less. */
private val LANDSCAPE_CAPTION_HEIGHT = 52.dp

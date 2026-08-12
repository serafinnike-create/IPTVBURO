package com.lucasserafin94.iptvburo.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.size.Scale
import com.lucasserafin94.iptvburo.metadata.TmdbImageSizes

/**
 * Displays source-provided artwork without persisting signed or credential-bearing URLs to disk.
 * The caller owns the branded fallback, which remains visible while loading or after an error.
 *
 * ## Why this measures itself
 *
 * The artwork is fetched at a width chosen for the space it actually occupies, in real pixels. Every
 * TMDb URL in the app used to carry a width picked when the URL was built — `w342` for a poster,
 * `w1280` for a backdrop — which is right on a 1080p panel and soft on a 4K one, where the same
 * poster is drawn across 496 pixels and the backdrop across 3840.
 *
 * Coil is also told the target size. Without it, it decodes at the image's own resolution and lets
 * Compose scale the result, which costs memory for a sharpness it then throws away.
 *
 * Nothing here enlarges a provider's own artwork: [TmdbImageSizes] leaves a non-TMDb URL untouched,
 * because it has no size ladder to choose from.
 */
@Composable
fun BuroRemoteArtwork(
    artworkUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    /**
     * Whether this is a wide backdrop rather than a poster.
     *
     * TMDb publishes a different ladder for each, and asking for a poster width on a backdrop path
     * returns nothing at all.
     */
    isBackdrop: Boolean = false,
    fallback: @Composable BoxScope.() -> Unit,
) {
    val context = LocalPlatformContext.current

    // The measured width, in device pixels. Zero until the first layout pass, which is the one case
    // where no size decision can be made — the URL is then used exactly as given.
    var widthPx by remember(artworkUrl) { mutableStateOf(0) }

    val request =
        remember(artworkUrl, context, widthPx, isBackdrop) {
            artworkUrl
                ?.takeIf(String::isNotBlank)
                ?.let { url ->
                    ImageRequest.Builder(context)
                        .data(TmdbImageSizes.resizedForWidth(url, widthPx, isBackdrop))
                        .diskCachePolicy(CachePolicy.DISABLED)
                        .apply {
                            // Decode straight to the size being drawn rather than to the image's
                            // own. Sharper on a dense display and smaller in memory on every one.
                            if (widthPx > 0) {
                                // Both arguments have to be Dimension: `size` overloads on
                                // (Int, Int) and (Dimension, Dimension), and does not mix them.
                                size(coil3.size.Dimension(widthPx), coil3.size.Dimension.Undefined)
                                scale(if (contentScale == ContentScale.Crop) Scale.FILL else Scale.FIT)
                            }
                        }.build()
                }
        }

    Box(modifier = modifier.onSizeChanged { size -> widthPx = size.width }) {
        fallback()
        request?.let {
            AsyncImage(
                model = it,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        }
    }
}

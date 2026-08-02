package com.lucasserafin94.iptvburo.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest

/**
 * Displays source-provided artwork without persisting signed or credential-bearing URLs to disk.
 * The caller owns the branded fallback, which remains visible while loading or after an error.
 */
@Composable
fun BuroRemoteArtwork(
    artworkUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    fallback: @Composable BoxScope.() -> Unit,
) {
    val context = LocalPlatformContext.current
    val request =
        remember(artworkUrl, context) {
            artworkUrl
                ?.takeIf(String::isNotBlank)
                ?.let { url ->
                    ImageRequest.Builder(context)
                        .data(url)
                        .diskCachePolicy(CachePolicy.DISABLED)
                        .build()
                }
        }
    Box(modifier = modifier) {
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

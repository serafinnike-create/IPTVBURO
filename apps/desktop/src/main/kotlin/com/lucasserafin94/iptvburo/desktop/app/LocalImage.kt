package com.lucasserafin94.iptvburo.desktop.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Draws a picture already stored on disk by the app.
 *
 * Decoding happens off the UI thread and is keyed on the path and its last-modified time, so
 * replacing a profile photo refreshes what is shown without the file name having to change.
 *
 * [fallback] is drawn while decoding and whenever the file cannot be read: a photo that was deleted
 * outside the app must not leave an empty hole where a profile used to be.
 */
@Composable
fun LocalImage(
    path: Path,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    fallback: @Composable BoxScope.() -> Unit,
) {
    val modified =
        remember(path) {
            runCatching { Files.getLastModifiedTime(path).toMillis() }.getOrDefault(0L)
        }
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path, modified) {
        value =
            withContext(Dispatchers.IO) {
                runCatching {
                    ImageIO.read(path.toFile())?.toComposeImageBitmap()
                }.getOrNull()
            }
    }

    Box(modifier = modifier) {
        val image = bitmap
        if (image == null) {
            fallback()
        } else {
            Image(
                bitmap = image,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        }
    }
}

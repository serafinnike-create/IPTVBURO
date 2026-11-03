package com.lucasserafin94.iptvburo.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap

/**
 * A picture shipped inside the app, as a bitmap.
 *
 * A bitmap rather than painterResource's painter because only the bitmap form of Image takes a
 * filter quality, and the full-window pictures need the high one: they are enlarged to fill the
 * screen, and bilinear filtering showed every step of the enlargement.
 */
@Composable
fun rememberBundledBitmap(path: String): ImageBitmap =
    remember(path) {
        val bytes =
            requireNotNull(Thread.currentThread().contextClassLoader.getResourceAsStream(path)) {
                "Missing bundled picture"
            }.use { it.readAllBytes() }
        org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
    }

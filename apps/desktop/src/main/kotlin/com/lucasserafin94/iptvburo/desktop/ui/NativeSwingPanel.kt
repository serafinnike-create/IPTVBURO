package com.lucasserafin94.iptvburo.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import java.awt.Component

/**
 * The density Windows reports, before the app's own interface size is applied on top.
 *
 * Null outside the app root, where the two are the same thing.
 */
val LocalSystemDensity = staticCompositionLocalOf<Density?> { null }

/**
 * A [SwingPanel] that stays where its slot is when the interface is scaled.
 *
 * SwingPanel converts its layout position and size from pixels into AWT units using the density in
 * scope. With the interface scaled to 130%, that density was 1.3 times the real one, so every
 * native surface — the video above all — came out at three quarters of its slot, pinned to the
 * top-left corner. The slot is laid out at the interface's scale; the panel inside it is given the
 * real density, so the conversion to AWT matches the window again.
 */
@Composable
fun <T : Component> NativeSwingPanel(
    factory: () -> T,
    modifier: Modifier = Modifier,
    background: Color = Color.White,
    update: (T) -> Unit = {},
) {
    Box(modifier) {
        val systemDensity = LocalSystemDensity.current ?: LocalDensity.current
        CompositionLocalProvider(LocalDensity provides systemDensity) {
            SwingPanel(
                background = background,
                factory = factory,
                modifier = Modifier.fillMaxSize(),
                update = update,
            )
        }
    }
}

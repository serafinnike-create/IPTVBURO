package com.lucasserafin94.iptvburo.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.awt.Component

/**
 * The density Windows reports, before the app's own interface size is applied on top.
 *
 * Null outside the app root, where the two are the same thing.
 */
val LocalSystemDensity = staticCompositionLocalOf<Density?> { null }

/**
 * Whether a panel is open over the screens: settings, the profile switcher, an update, a sign-in.
 *
 * A native surface — the player's video, the guide preview, the home banner's trailer browser — is
 * a heavyweight window of its own and is always drawn above Compose, whatever the order says. The
 * banner's trailer covered the settings dialog and stayed there through closing and reopening it.
 * While this is true every native surface shrinks out of the way: not removed, so a trailer keeps
 * playing behind and is simply shown again when the panel closes.
 */
val LocalNativeSurfacesCovered = compositionLocalOf { false }

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
        val covered = LocalNativeSurfacesCovered.current
        CompositionLocalProvider(LocalDensity provides systemDensity) {
            SwingPanel(
                background = background,
                factory = factory,
                // Shrunk to a pixel rather than hidden. Hiding the component left SwingPanel's own
                // native container in place — a white rectangle still over the dialog — and taking
                // it out of composition would destroy the surface, which for VLC means a black
                // picture for good. A pixel keeps everything alive behind the panel, and the
                // full size comes back when it closes.
                modifier = if (covered) Modifier.size(1.dp) else Modifier.fillMaxSize(),
                update = update,
            )
        }
    }
}

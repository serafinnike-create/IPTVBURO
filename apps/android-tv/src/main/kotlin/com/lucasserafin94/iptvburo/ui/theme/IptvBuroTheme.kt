package com.lucasserafin94.iptvburo.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import com.lucasserafin94.iptvburo.ui.designsystem.BuroPerformanceTier
import com.lucasserafin94.iptvburo.ui.designsystem.BuroTheme
import com.lucasserafin94.iptvburo.ui.designsystem.BuroUiPreferences
import com.lucasserafin94.iptvburo.ui.designsystem.ProvideBuroDesignSystem

/*
 * Screen-level colour shorthands.
 *
 * These read the scheme from `BuroTheme.colors`, which `ProvideBuroDesignSystem` resolves through
 * `resolveBuroColorScheme(preferences)`. They used to be top-level `val`s captured from the default
 * scheme at class-init, which meant the high-contrast and reduced-transparency preferences were
 * applied to the scheme but never reached a single screen that used them.
 *
 * The names are semantic. The previous `Teal` and `Blue` aliases pointed at the ivory and gold
 * brand colours respectively, so reading a screen told you nothing about what it would paint.
 */
val BuroCanvas: Color
    @Composable @ReadOnlyComposable get() = BuroTheme.colors.canvas

val BuroSurface: Color
    @Composable @ReadOnlyComposable get() = BuroTheme.colors.surface

val BuroSurfaceRaised: Color
    @Composable @ReadOnlyComposable get() = BuroTheme.colors.elevated

/** Ivory secondary brand colour, used for supporting accents and secondary CTAs. */
val BuroAccent: Color
    @Composable @ReadOnlyComposable get() = BuroTheme.colors.brandSecondary

/** Gold primary brand colour. Reserved for focus, progress and brand moments. */
val BuroGold: Color
    @Composable @ReadOnlyComposable get() = BuroTheme.colors.brandPrimary

val BuroTextPrimary: Color
    @Composable @ReadOnlyComposable get() = BuroTheme.colors.textPrimary

val BuroTextSecondary: Color
    @Composable @ReadOnlyComposable get() = BuroTheme.colors.textSecondary

val BuroDanger: Color
    @Composable @ReadOnlyComposable get() = BuroTheme.colors.error

@Composable
fun IptvBuroTheme(
    uiPreferences: BuroUiPreferences = BuroUiPreferences.SafeDefaults,
    autoPerformanceTier: BuroPerformanceTier = BuroPerformanceTier.Balanced,
    content: @Composable () -> Unit,
) {
    ProvideBuroDesignSystem(
        preferences = uiPreferences,
        autoPerformanceTier = autoPerformanceTier,
    ) {
        val colors = BuroTheme.colors
        MaterialTheme(
            colorScheme =
                darkColorScheme(
                    primary = colors.brandPrimary,
                    onPrimary = colors.onBrand,
                    secondary = colors.brandSecondary,
                    onSecondary = colors.onBrand,
                    background = colors.canvas,
                    onBackground = colors.textPrimary,
                    surface = colors.surface,
                    onSurface = colors.textPrimary,
                    surfaceVariant = colors.elevated,
                    onSurfaceVariant = colors.textSecondary,
                    border = colors.borderSubtle,
                    error = colors.error,
                    onError = colors.onBrand,
                    scrim = colors.overlay,
                ),
            shapes = BuroTheme.shapes,
            typography = BuroTheme.typography,
            content = content,
        )
    }
}

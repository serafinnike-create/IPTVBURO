package com.lucasserafin94.iptvburo.ui.theme

import androidx.compose.runtime.Composable
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import com.lucasserafin94.iptvburo.ui.designsystem.BuroColors
import com.lucasserafin94.iptvburo.ui.designsystem.BuroPerformanceTier
import com.lucasserafin94.iptvburo.ui.designsystem.BuroTheme
import com.lucasserafin94.iptvburo.ui.designsystem.BuroUiPreferences
import com.lucasserafin94.iptvburo.ui.designsystem.ProvideBuroDesignSystem

// Compatibility aliases for existing screens. New UI should consume BuroTheme semantic tokens.
val Ink = BuroColors.Canvas
val InkSoft = BuroColors.Surface
val Surface = BuroColors.Surface
val SurfaceRaised = BuroColors.Elevated
val Teal = BuroColors.BrandSecondary
val Blue = BuroColors.BrandPrimary
val White = BuroColors.TextPrimary
val Muted = BuroColors.TextSecondary
val Danger = BuroColors.Error

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

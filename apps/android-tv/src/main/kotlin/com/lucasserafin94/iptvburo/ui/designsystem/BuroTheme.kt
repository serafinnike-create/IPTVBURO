package com.lucasserafin94.iptvburo.ui.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.tv.material3.Shapes
import androidx.tv.material3.Typography

val LocalBuroUiPreferences =
    staticCompositionLocalOf {
        BuroUiPreferences.SafeDefaults
    }

val LocalBuroPerformanceTier =
    staticCompositionLocalOf {
        BuroPerformanceTier.Balanced
    }

val LocalBuroMotionPolicy =
    staticCompositionLocalOf {
        resolveMotionPolicy(
            performanceTier = BuroPerformanceTier.Balanced,
            preferences = BuroUiPreferences.SafeDefaults,
        )
    }

val LocalBuroColors =
    staticCompositionLocalOf {
        BuroColors.DefaultScheme
    }

object BuroTheme {
    val colors: BuroColorScheme
        @Composable
        @ReadOnlyComposable
        get() = LocalBuroColors.current

    val preferences: BuroUiPreferences
        @Composable
        @ReadOnlyComposable
        get() = LocalBuroUiPreferences.current

    val performanceTier: BuroPerformanceTier
        @Composable
        @ReadOnlyComposable
        get() = LocalBuroPerformanceTier.current

    val motion: BuroMotionPolicy
        @Composable
        @ReadOnlyComposable
        get() = LocalBuroMotionPolicy.current

    val typography: Typography
        get() = BuroTvTypography

    val shapes: Shapes
        get() = BuroTvShapes
}

@Composable
internal fun ProvideBuroDesignSystem(
    preferences: BuroUiPreferences,
    autoPerformanceTier: BuroPerformanceTier,
    content: @Composable () -> Unit,
) {
    val resolvedTier = preferences.performanceTier.resolve(autoPerformanceTier)
    val colors = resolveBuroColorScheme(preferences)
    val motion = resolveMotionPolicy(resolvedTier, preferences)

    CompositionLocalProvider(
        LocalBuroUiPreferences provides preferences,
        LocalBuroPerformanceTier provides resolvedTier,
        LocalBuroMotionPolicy provides motion,
        LocalBuroColors provides colors,
        content = content,
    )
}

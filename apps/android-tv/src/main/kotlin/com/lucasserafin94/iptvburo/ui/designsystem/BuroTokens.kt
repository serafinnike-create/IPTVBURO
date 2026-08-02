package com.lucasserafin94.iptvburo.ui.designsystem

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Shapes
import androidx.tv.material3.Typography

@Immutable
data class BuroColorScheme(
    val canvas: Color,
    val surface: Color,
    val elevated: Color,
    val overlay: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val borderSubtle: Color,
    val focus: Color,
    val brandPrimary: Color,
    val brandSecondary: Color,
    val onBrand: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
)

object BuroColors {
    val Canvas = Color(0xFF08090A)
    val Surface = Color(0xFF111214)
    val Elevated = Color(0xFF1A1C1F)
    val Overlay = Color(0xB8050609)
    val TextPrimary = Color(0xFFF4F1EA)
    val TextSecondary = Color(0xFFB8B4AC)
    val TextMuted = Color(0xFF85827C)
    val BorderSubtle = Color(0x1AFFFFFF)
    val Focus = Color(0xFFF6F7FA)
    val BrandPrimary = Color(0xFFD6A956)
    val BrandSecondary = Color(0xFFE7E2D8)
    val OnBrand = Color(0xFF08090A)
    val Success = Color(0xFF4ED59B)
    val Warning = Color(0xFFF3BD56)
    val Error = Color(0xFFFF6B6B)

    val DefaultScheme =
        BuroColorScheme(
            canvas = Canvas,
            surface = Surface,
            elevated = Elevated,
            overlay = Overlay,
            textPrimary = TextPrimary,
            textSecondary = TextSecondary,
            textMuted = TextMuted,
            borderSubtle = BorderSubtle,
            focus = Focus,
            brandPrimary = BrandPrimary,
            brandSecondary = BrandSecondary,
            onBrand = OnBrand,
            success = Success,
            warning = Warning,
            error = Error,
        )

    val HighContrastScheme =
        DefaultScheme.copy(
            canvas = Color.Black,
            surface = Color(0xFF090A0D),
            elevated = Color(0xFF17191F),
            overlay = Color(0xF2050609),
            textSecondary = Color(0xFFE2E4EA),
            textMuted = Color(0xFFC5C8D0),
            borderSubtle = Color(0x8AFFFFFF),
            brandPrimary = Color(0xFFF0C877),
            brandSecondary = Color(0xFFFFFFFF),
        )
}

fun resolveBuroColorScheme(
    preferences: BuroUiPreferences,
): BuroColorScheme {
    val base =
        if (preferences.highContrast) {
            BuroColors.HighContrastScheme
        } else {
            BuroColors.DefaultScheme
        }
    return if (preferences.reducedTransparency) {
        base.copy(overlay = Color(0xFF050609))
    } else {
        base
    }
}

object BuroSpacing {
    val None = 0.dp
    val Xs = 8.dp
    val Sm = 16.dp
    val Md = 24.dp
    val Lg = 32.dp
    val Xl = 48.dp
    val Xxl = 64.dp
    val Xxxl = 80.dp

    val ScreenHorizontal = Xxl
    val ScreenVertical = Xl
}

object BuroShapes {
    val Small = RoundedCornerShape(8.dp)
    val Medium = RoundedCornerShape(16.dp)
    val Large = RoundedCornerShape(24.dp)
    val ExtraLarge = RoundedCornerShape(32.dp)
    val Pill = RoundedCornerShape(percent = 50)
}

val BuroTvShapes =
    Shapes(
        extraSmall = BuroShapes.Small,
        small = BuroShapes.Small,
        medium = BuroShapes.Medium,
        large = BuroShapes.Large,
        extraLarge = BuroShapes.ExtraLarge,
    )

object BuroFocusTokens {
    const val DefaultScale = 1f
    const val CardScale = 1.045f
    const val CompactScale = 1.06f
    const val PressedScale = 0.985f

    val RingWidth = 2.dp
    val HighContrastRingWidth = 3.dp
    val SelectedRingWidth = 2.dp
    val DisabledRingWidth = 1.dp
}

object BuroMotionTokens {
    const val MicroFastMillis = 80
    const val FocusMillis = 160
    const val NavigationMillis = 240
    const val CinematicMillis = 460
    const val SkeletonPulseMillis = 1_000

    val EnterEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val ExitEasing = CubicBezierEasing(0.4f, 0f, 0.6f, 1f)
}

object BuroOpacity {
    const val Disabled = 0.52f
    const val AmbientBrand = 0.12f
    const val AmbientSurface = 0.72f
    const val SelectedContainer = 0.24f
    const val SkeletonMinimum = 0.52f
    const val SkeletonMaximum = 0.92f
    const val SkeletonStatic = 0.72f
    const val ErrorContainer = 0.14f
}

object BuroComponentSizes {
    val ButtonMinHeight = 56.dp
    val IconButtonMinSize = 56.dp
    val ChipMinHeight = 40.dp
    val ProgressHeight = 8.dp
    val StateGlyph = 80.dp
    val StateMaxWidth = 640.dp
}

private val TvFontFamily = FontFamily.SansSerif

val BuroTvTypography =
    Typography(
        displayLarge =
            TextStyle(
                fontFamily = TvFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 60.sp,
                lineHeight = 68.sp,
                letterSpacing = (-0.5).sp,
            ),
        displayMedium =
            TextStyle(
                fontFamily = TvFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 52.sp,
                lineHeight = 60.sp,
            ),
        displaySmall =
            TextStyle(
                fontFamily = TvFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 44.sp,
                lineHeight = 52.sp,
            ),
        headlineLarge =
            TextStyle(
                fontFamily = TvFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 40.sp,
                lineHeight = 48.sp,
            ),
        headlineMedium =
            TextStyle(
                fontFamily = TvFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 30.sp,
                lineHeight = 38.sp,
            ),
        headlineSmall =
            TextStyle(
                fontFamily = TvFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 26.sp,
                lineHeight = 34.sp,
            ),
        titleLarge =
            TextStyle(
                fontFamily = TvFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 24.sp,
                lineHeight = 32.sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = TvFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 20.sp,
                lineHeight = 28.sp,
            ),
        titleSmall =
            TextStyle(
                fontFamily = TvFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 18.sp,
                lineHeight = 26.sp,
            ),
        bodyLarge =
            TextStyle(
                fontFamily = TvFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 20.sp,
                lineHeight = 30.sp,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = TvFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 18.sp,
                lineHeight = 27.sp,
            ),
        bodySmall =
            TextStyle(
                fontFamily = TvFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 15.sp,
                lineHeight = 22.sp,
            ),
        labelLarge =
            TextStyle(
                fontFamily = TvFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 17.sp,
                lineHeight = 24.sp,
            ),
        labelMedium =
            TextStyle(
                fontFamily = TvFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                lineHeight = 22.sp,
            ),
        labelSmall =
            TextStyle(
                fontFamily = TvFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                lineHeight = 20.sp,
            ),
    )

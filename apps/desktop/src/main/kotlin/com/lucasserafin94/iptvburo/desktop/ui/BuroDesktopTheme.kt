package com.lucasserafin94.iptvburo.desktop.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * BURO Nocturne palette, mirroring `packages/design-tokens/tokens.json`.
 *
 * Every colour a component paints must come from here. Literal `Color(0x…)` values inside screens
 * drift away from the token contract, which is how the three different "on gold" colours appeared
 * before this file owned [OnPrimary].
 */
object BuroColors {
    val Canvas = Color(0xFF08090A)
    val Surface = Color(0xFF111214)
    val SurfaceRaised = Color(0xFF1A1C1F)
    val SurfaceHover = Color(0xFF24262A)
    val Border = Color(0xFF3A3C40)
    val BorderSoft = Color(0x1AFFFFFF)
    val Primary = Color(0xFFD6A956)
    val PrimaryStrong = Color(0xFFF0C877)
    val Accent = Color(0xFFE7E2D8)
    val Text = Color(0xFFF4F1EA)
    val TextMuted = Color(0xFFB8B4AC)
    val TextSubtle = Color(0xFF85827C)
    val Success = Color(0xFF4ED59B)
    val Warning = Color(0xFFF3BD56)
    val Error = Color(0xFFFF6B6B)

    /** Canonical foreground for anything painted on [Primary] or [PrimaryStrong]. */
    val OnPrimary = Canvas

    /** Focus ring colour. Kept near-white so it reads on artwork of any hue. */
    val Focus = Color(0xFFF6F7FA)

    /** Veil for modal surfaces and the loading overlay. */
    val Scrim = Color(0xC2050609)
}

/** 8 px base spacing scale from the GDD grid section. */
object BuroSpacing {
    val Xxs = 4.dp
    val Xs = 8.dp
    val Sm = 12.dp
    val Md = 16.dp
    val Lg = 24.dp
    val Xl = 32.dp
    val Xxl = 48.dp

    /**
     * Single source of truth for the left edge of every screen-level element. Rail titles and the
     * first card of a rail must both use it, otherwise the rail reads as visually broken.
     */
    val GutterCompact = 28.dp
    val GutterWide = 44.dp
}

object BuroRadius {
    val Small = RoundedCornerShape(8.dp)
    val Medium = RoundedCornerShape(14.dp)
    val Large = RoundedCornerShape(20.dp)
    val Hero = RoundedCornerShape(28.dp)
    val Pill = RoundedCornerShape(percent = 50)
}

/** Motion durations from `tokens.json`, split by the GDD's three movement layers. */
object BuroMotion {
    const val MicroMillis = 80
    const val FocusMillis = 160
    const val NavigationMillis = 240
    const val CinematicMillis = 460

    val EnterEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val ExitEasing = CubicBezierEasing(0.4f, 0f, 0.6f, 1f)
}

/** Focus/hover physics. Small cards travel further than large ones so the motion reads evenly. */
object BuroInteraction {
    const val RestScale = 1f
    const val CardHoverScale = 1.045f
    const val CompactHoverScale = 1.06f
    const val PressedScale = 0.985f

    val RingWidth = 2.dp
}

/**
 * Scrim applied over hero artwork.
 *
 * Two stacked gradients rather than one flat overlay: the horizontal pass protects the text column
 * while leaving the right side of the art visible, and the vertical pass anchors the composition so
 * the hero does not float against the rail below it.
 */
object BuroScrim {
    fun heroHorizontal(): Brush =
        Brush.horizontalGradient(
            0f to BuroColors.Canvas.copy(alpha = 0.97f),
            0.38f to BuroColors.Canvas.copy(alpha = 0.86f),
            0.68f to BuroColors.Canvas.copy(alpha = 0.34f),
            1f to Color.Transparent,
        )

    fun heroVertical(): Brush =
        Brush.verticalGradient(
            0f to BuroColors.Canvas.copy(alpha = 0.42f),
            0.45f to Color.Transparent,
            1f to BuroColors.Canvas.copy(alpha = 0.92f),
        )

    /** Gradient that lets a card title sit on top of artwork without a solid bar. */
    fun cardFooter(): Brush =
        Brush.verticalGradient(
            0f to Color.Transparent,
            1f to BuroColors.Canvas.copy(alpha = 0.94f),
        )
}

private val BuroScheme =
    darkColorScheme(
        primary = BuroColors.Primary,
        onPrimary = BuroColors.OnPrimary,
        secondary = BuroColors.Accent,
        onSecondary = BuroColors.OnPrimary,
        background = BuroColors.Canvas,
        onBackground = BuroColors.Text,
        surface = BuroColors.Surface,
        onSurface = BuroColors.Text,
        surfaceVariant = BuroColors.SurfaceRaised,
        onSurfaceVariant = BuroColors.TextMuted,
        outline = BuroColors.Border,
        outlineVariant = BuroColors.BorderSoft,
        scrim = BuroColors.Scrim,
        error = BuroColors.Error,
        onError = BuroColors.OnPrimary,
    )

private val Sans = FontFamily.SansSerif

private val BuroTypography =
    androidx.compose.material3.Typography(
        displayLarge =
            TextStyle(
                fontFamily = Sans,
                fontWeight = FontWeight.Black,
                fontSize = 54.sp,
                lineHeight = 58.sp,
                letterSpacing = (-1).sp,
            ),
        displayMedium =
            TextStyle(
                fontFamily = Sans,
                fontWeight = FontWeight.Black,
                fontSize = 44.sp,
                lineHeight = 48.sp,
                letterSpacing = (-0.6).sp,
            ),
        displaySmall =
            TextStyle(
                fontFamily = Sans,
                fontWeight = FontWeight.Bold,
                fontSize = 36.sp,
                lineHeight = 42.sp,
                letterSpacing = (-0.3).sp,
            ),
        headlineLarge =
            TextStyle(
                fontFamily = Sans,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                lineHeight = 34.sp,
            ),
        headlineMedium =
            TextStyle(
                fontFamily = Sans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                lineHeight = 30.sp,
            ),
        headlineSmall =
            TextStyle(
                fontFamily = Sans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                lineHeight = 26.sp,
            ),
        titleLarge =
            TextStyle(
                fontFamily = Sans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                lineHeight = 24.sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = Sans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                lineHeight = 20.sp,
            ),
        titleSmall =
            TextStyle(
                fontFamily = Sans,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 19.sp,
            ),
        bodyLarge =
            TextStyle(
                fontFamily = Sans,
                fontWeight = FontWeight.Normal,
                fontSize = 15.sp,
                lineHeight = 22.sp,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = Sans,
                fontWeight = FontWeight.Normal,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            ),
        bodySmall =
            TextStyle(
                fontFamily = Sans,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            ),
        labelLarge =
            TextStyle(
                fontFamily = Sans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            ),
        labelMedium =
            TextStyle(
                fontFamily = Sans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.4.sp,
            ),
        labelSmall =
            TextStyle(
                fontFamily = Sans,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                letterSpacing = 0.8.sp,
            ),
    )

@Composable
fun BuroDesktopTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BuroScheme,
        typography = BuroTypography,
        content = content,
    )
}

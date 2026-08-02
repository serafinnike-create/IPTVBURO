package com.lucasserafin94.iptvburo.desktop.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

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
}

private val BuroScheme =
    darkColorScheme(
        primary = BuroColors.Primary,
        onPrimary = BuroColors.Canvas,
        secondary = BuroColors.Accent,
        background = BuroColors.Canvas,
        onBackground = BuroColors.Text,
        surface = BuroColors.Surface,
        onSurface = BuroColors.Text,
        surfaceVariant = BuroColors.SurfaceRaised,
        onSurfaceVariant = BuroColors.TextMuted,
        outline = BuroColors.Border,
        error = BuroColors.Error,
    )

@Composable
fun BuroDesktopTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BuroScheme,
        typography =
            MaterialTheme.typography.copy(
                displaySmall =
                    TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 36.sp,
                        lineHeight = 42.sp,
                    ),
                headlineMedium =
                    TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 24.sp,
                        lineHeight = 30.sp,
                    ),
                titleLarge =
                    TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                        lineHeight = 24.sp,
                    ),
                bodyLarge =
                    TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Normal,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                    ),
                bodyMedium =
                    TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Normal,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    ),
                labelLarge =
                    TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    ),
            ),
        content = content,
    )
}

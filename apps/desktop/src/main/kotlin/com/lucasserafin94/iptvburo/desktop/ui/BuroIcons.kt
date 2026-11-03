package com.lucasserafin94.iptvburo.desktop.ui

import androidx.compose.material.icons.rounded.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.rounded.Theaters
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Share
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The app's icons, by meaning.
 *
 * One family — Material Symbols Rounded, softer than the filled set and closer to the app's night
 * palette — named for what each does rather than what it depicts. Screens ask for `Subtitles`, not
 * for a glyph; changing how subtitles look is then a one-line change here.
 *
 * Replaces text characters used as icons (▶ ❚❚ ⛶ ♥ ⌕ ⧉…). Those were drawn by whichever Windows
 * fallback font happened to have them, so their weights and sizes never matched, a few rendered as
 * empty boxes on some machines, and a screen reader read them out as the raw character.
 */
object BuroIcons {
    val Back: ImageVector = Icons.AutoMirrored.Rounded.ArrowBack
    val Play: ImageVector = Icons.Rounded.PlayArrow
    val Pause: ImageVector = Icons.Rounded.Pause
    val Rewind10: ImageVector = Icons.Rounded.Replay10
    val Forward30: ImageVector = Icons.Rounded.Forward30
    val Next: ImageVector = Icons.Rounded.SkipNext
    val VolumeHigh: ImageVector = Icons.AutoMirrored.Rounded.VolumeUp
    val VolumeLow: ImageVector = Icons.AutoMirrored.Rounded.VolumeDown
    val VolumeOff: ImageVector = Icons.AutoMirrored.Rounded.VolumeOff
    val FullScreen: ImageVector = Icons.Rounded.Fullscreen
    val ExitFullScreen: ImageVector = Icons.Rounded.FullscreenExit
    val MiniPlayer: ImageVector = Icons.Rounded.PictureInPictureAlt
    val Favorite: ImageVector = Icons.Rounded.Favorite
    val NotFavorite: ImageVector = Icons.Rounded.FavoriteBorder
    val Speed: ImageVector = Icons.Rounded.Speed
    val Aspect: ImageVector = Icons.Rounded.AspectRatio
    val Subtitles: ImageVector = Icons.Rounded.Subtitles
    val Audio: ImageVector = Icons.Rounded.Audiotrack
    val Speakers: ImageVector = Icons.Rounded.Speaker
    val Brightness: ImageVector = Icons.Rounded.BrightnessMedium
    val Settings: ImageVector = Icons.Rounded.Settings
    val Shortcuts: ImageVector = Icons.Rounded.Keyboard
    val Live: ImageVector = Icons.Rounded.LiveTv
    val Channels: ImageVector = Icons.Rounded.Tv
    val Multiview: ImageVector = Icons.Rounded.GridView
    val Search: ImageVector = Icons.Rounded.Search
    val Copy: ImageVector = Icons.Rounded.ContentCopy
    val Close: ImageVector = Icons.Rounded.Close
    val Check: ImageVector = Icons.Rounded.Check
    val Download: ImageVector = Icons.Rounded.Download
    val Rating: ImageVector = Icons.Rounded.Star
    val Share: ImageVector = Icons.Rounded.Share
    val Cast: ImageVector = Icons.Rounded.Cast
    val Trailer: ImageVector = Icons.Rounded.Theaters
    val FirstPage: ImageVector = Icons.Rounded.KeyboardDoubleArrowLeft
}

/**
 * An icon followed by its label, for buttons that carry both.
 *
 * Replaces labels that began with a text character standing in for an icon ("▶  Assistir",
 * "↗  Compartilhar"); the icon takes the button's content colour, so it matches the text.
 */
@Composable
fun IconLabel(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontWeight: androidx.compose.ui.text.font.FontWeight? = null,
    iconSize: Dp = BuroIconSize.Small,
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (color == Color.Unspecified) androidx.compose.material3.LocalContentColor.current else color,
            modifier = Modifier.size(iconSize),
        )
        androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
        Text(text = text, color = color, fontWeight = fontWeight)
    }
}

/** The sizes icons come in. Anything else is a one-off, and one-offs are what made the set uneven. */
object BuroIconSize {
    val Small: Dp = 18.dp
    val Medium: Dp = 24.dp
    val Large: Dp = 32.dp
}

/**
 * An icon button that always says what it does.
 *
 * The label is required: it is the tooltip a mouse user sees on hover, with the shortcut beside it
 * when there is one, and what a screen reader reads. A bare glyph told neither of them anything.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BuroIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Color.White.copy(alpha = 0.92f),
    size: Dp = BuroIconSize.Medium,
    /** A key that does the same thing, shown after the label in the tooltip. */
    shortcut: String? = null,
    emphasised: Boolean = false,
) {
    TooltipArea(
        tooltip = {
            Text(
                text = if (shortcut != null) "$label  ($shortcut)" else label,
                color = BuroColors.Text,
                fontSize = 12.sp,
                modifier =
                    Modifier
                        .background(BuroColors.SurfaceRaised, RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        },
        delayMillis = 450,
        // Beside the pointer rather than above it. Above, a tooltip on the player's bar landed over
        // the video — a native window nothing can be drawn over — and was never seen.
        tooltipPlacement = TooltipPlacement.CursorPoint(offset = DpOffset(18.dp, (-10).dp)),
        modifier = modifier,
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            colors =
                if (emphasised) {
                    IconButtonDefaults.iconButtonColors(
                        containerColor = BuroColors.Primary.copy(alpha = 0.22f),
                        contentColor = BuroColors.Primary,
                    )
                } else {
                    IconButtonDefaults.iconButtonColors(contentColor = tint)
                },
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(if (emphasised) size + 8.dp else size),
            )
        }
    }
}

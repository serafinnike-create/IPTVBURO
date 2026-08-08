package com.lucasserafin94.iptvburo.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text

/**
 * The IPTV BURO mark, drawn rather than loaded.
 *
 * It was a 512×512 PNG used at every size from 24dp in the Assinaturas header to 132dp on the
 * splash. Two things were wrong with that:
 *
 * - **It carried an opaque black square.** The mark is a ring, but the file's background was solid,
 *   so on any surface that is not exactly the same black it showed as a dark tile around the ring
 *   instead of floating on the page.
 * - **It was raster.** At 132dp on a 150%-scaled display that is roughly 200 physical pixels from a
 *   512px source — survivable — but the ring's edge is the one thing a resample softens, and a soft
 *   edge is the whole silhouette here.
 *
 * A ring and a letter are two drawing commands. Drawn, they are exact at any size, transparent
 * where they should be, and cost no decode, no bitmap and no file in the installer.
 */
@Composable
fun BuroMark(
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            // Proportions taken from the original artwork, so the mark is recognisably the same:
            // the ring occupies the outer sixth, and its inner edge clears the letter.
            val stroke = this.size.minDimension * RING_THICKNESS
            val radius = (this.size.minDimension - stroke) / 2f
            drawCircle(
                // A gradient across the ring rather than a flat gold. The original had one, and it
                // is what keeps the mark from reading as a plain outline at small sizes.
                brush =
                    Brush.linearGradient(
                        colors = listOf(BuroColors.Primary, BuroColors.PrimaryStrong, BuroColors.Primary),
                        start = Offset(0f, 0f),
                        end = Offset(this.size.width, this.size.height),
                    ),
                radius = radius,
                center = center,
                style = Stroke(width = stroke),
            )
        }
        Text(
            text = "B",
            color = BuroColors.Text,
            style =
                TextStyle(
                    // Sized from the mark rather than fixed, so the letter keeps its place inside
                    // the ring at every size this is drawn at.
                    fontSize = (size.value * LETTER_SCALE).sp,
                    fontWeight = FontWeight.Bold,
                ),
        )
    }
}

/** Fraction of the mark's width taken by the ring. Measured from the original artwork. */
private const val RING_THICKNESS = 0.11f

/** The letter's height as a fraction of the mark, chosen to clear the ring's inner edge. */
private const val LETTER_SCALE = 0.52f

/** The taskbar and window icon still need a bitmap; AWT cannot take a composable. */
internal const val BURO_ICON_RESOURCE = "brand/buro-mark-512.png"

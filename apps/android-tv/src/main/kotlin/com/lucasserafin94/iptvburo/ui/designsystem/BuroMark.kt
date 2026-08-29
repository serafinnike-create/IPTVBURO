package com.lucasserafin94.iptvburo.ui.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.lucasserafin94.iptvburo.ui.theme.BuroAccent
import com.lucasserafin94.iptvburo.ui.theme.BuroGold
import com.lucasserafin94.iptvburo.ui.theme.BuroTextPrimary

/**
 * The IPTV BURO mark, drawn rather than loaded — the Android counterpart of the desktop's own
 * `BuroMark`. A ring and a letter are two drawing commands: exact at any size, transparent where
 * they should be, and needing no image asset shipped in the APK for what is otherwise a 24dp badge
 * next to a service logo.
 *
 * Used on Assinaturas to mark the user's own library row, matching the Windows row it sits beside
 * conceptually — the entry a viewer cares about most should be findable at a glance, the same way
 * a real service's mark is.
 */
@Composable
fun BuroMark(
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val ring = BuroGold
    val ringStrong = BuroAccent
    val letter = BuroTextPrimary
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            // Proportions taken from the original artwork, so the mark is recognisably the same:
            // the ring occupies the outer sixth, and its inner edge clears the letter.
            val stroke = this.size.minDimension * RING_THICKNESS
            val radius = (this.size.minDimension - stroke) / 2f
            drawCircle(
                // A gradient across the ring rather than a flat gold, which is what keeps the mark
                // from reading as a plain outline at small sizes.
                brush =
                    Brush.linearGradient(
                        colors = listOf(ring, ringStrong, ring),
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
            color = letter,
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

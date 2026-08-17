package com.lucasserafin94.iptvburo.ui.designsystem

import android.content.res.Configuration
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.basicMarquee
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.tv.material3.Text

/**
 * A single line of text that scrolls itself when it does not fit.
 *
 * Card captions sit under the artwork now, in a caption of fixed height, so a long title had only
 * one way to end: an ellipsis a few characters in. "Desastre Total: Festiv…" tells the viewer almost
 * nothing, and on a grid of covers the title is how they tell two similar posters apart.
 *
 * ## Why it scrolls on its own on a phone
 *
 * The first version scrolled only the focused or pressed card, which is right on a television and
 * useless on a phone: a grid of posters is tapped, never focused, and a tap *opens the film* — the
 * card is pressed for a fraction of a second on its way somewhere else. Captions therefore never
 * moved on the device most people use, and every long title stayed cut off.
 *
 * So on a touch screen every long caption scrolls by itself. That is a real cost — several captions
 * moving at once is busier than one — and it is paid because the alternative was a title nobody
 * could read. It is bounded: [MARQUEE_ITERATIONS] passes and it settles, rather than looping under
 * the poster for as long as the screen is open.
 *
 * On a television, where the remote gives one card focus at a time and focus lasts, only the focused
 * caption scrolls — which is both calmer and exactly what somebody with a remote expects.
 */
@Composable
fun BuroMarqueeText(
    text: String,
    /** Whether this card is the focused one. Decides the behaviour on a television only. */
    active: Boolean,
    color: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
) {
    val isTelevision =
        LocalConfiguration.current.uiMode and Configuration.UI_MODE_TYPE_MASK ==
            Configuration.UI_MODE_TYPE_TELEVISION
    // A television scrolls what is focused; a touch screen scrolls everything, because nothing on
    // it is ever focused for long enough to read.
    val scrolling = if (isTelevision) active else true

    Text(
        text = text,
        color = color,
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontWeight = fontWeight,
        maxLines = 1,
        // Clip rather than Ellipsis while scrolling: an ellipsis on a moving line would sit in the
        // middle of the text as it passes, which reads as a rendering fault. A caption that fits
        // never moves, so it never shows one either.
        overflow = if (scrolling) TextOverflow.Clip else TextOverflow.Ellipsis,
        modifier =
            if (scrolling) {
                modifier.basicMarquee(
                    iterations = MARQUEE_ITERATIONS,
                    initialDelayMillis = MARQUEE_INITIAL_DELAY_MILLIS,
                    repeatDelayMillis = MARQUEE_REPEAT_DELAY_MILLIS,
                    animationMode = MarqueeAnimationMode.Immediately,
                )
            } else {
                modifier
            },
    )
}

/**
 * Passes before the caption settles back to the start.
 *
 * Enough to read a long title twice at an unhurried pace. Unbounded looping is what makes this kind
 * of motion tiring on a screen somebody is browsing rather than reading.
 */
private const val MARQUEE_ITERATIONS = 4

/** A beat before the first pass, so a grid does not start moving the instant it is drawn. */
private const val MARQUEE_INITIAL_DELAY_MILLIS = 1_200

/** And a longer beat between passes, so the start of the title can be read at rest. */
private const val MARQUEE_REPEAT_DELAY_MILLIS = 2_000

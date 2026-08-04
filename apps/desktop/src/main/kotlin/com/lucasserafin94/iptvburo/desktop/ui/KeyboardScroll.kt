package com.lucasserafin94.iptvburo.desktop.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import kotlinx.coroutines.launch

/**
 * Lets the left and right arrows move a horizontal rail the pointer is over.
 *
 * Focus follows the pointer rather than a click. A row of posters is something you point at, and
 * requiring a click first would mean the first arrow press opens a film instead of scrolling.
 *
 * [step] is in pixels: roughly a card and a half, so one press makes visible progress without
 * skipping past what the user was looking at.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Modifier.arrowScrollable(
    state: LazyListState,
    step: Float = DEFAULT_RAIL_STEP,
): Modifier {
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    return this
        .focusRequester(focus)
        .focusable()
        .onPointerEvent(PointerEventType.Enter) { runCatching { focus.requestFocus() } }
        .onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val delta =
                when (event.key) {
                    Key.DirectionRight -> step
                    Key.DirectionLeft -> -step
                    else -> return@onPreviewKeyEvent false
                }
            scope.launch { state.animateScrollBy(delta) }
            true
        }
}

private const val DEFAULT_RAIL_STEP = 420f

/**
 * Lets the up and down arrows move a scrolling column the pointer is over.
 *
 * Same reasoning as the rail: pointing at a panel is enough to aim the keys at it, and the details
 * page is long enough - twenty-four episodes, then the cast - that reaching for the wheel every
 * time is the wrong ask.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Modifier.arrowScrollableVertically(
    state: ScrollState,
    step: Float = DEFAULT_COLUMN_STEP,
): Modifier {
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    return this
        .focusRequester(focus)
        .focusable()
        .onPointerEvent(PointerEventType.Enter) { runCatching { focus.requestFocus() } }
        .onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val delta =
                when (event.key) {
                    Key.DirectionDown -> step
                    Key.DirectionUp -> -step
                    Key.PageDown -> step * 3
                    Key.PageUp -> -step * 3
                    else -> return@onPreviewKeyEvent false
                }
            scope.launch { state.animateScrollBy(delta) }
            true
        }
}

/** About one episode row, so a press moves by something the eye can follow. */
private const val DEFAULT_COLUMN_STEP = 160f

/**
 * Scrolls a rail while the pointer rests near its left or right edge.
 *
 * For someone using only a mouse: reaching the far end of a shelf otherwise means dragging, or
 * finding a scrollbar that is deliberately thin. Hovering the edge is how a map or a timeline
 * behaves, and it costs nothing when the pointer is anywhere else.
 *
 * [edgeFraction] is how much of the width counts as the edge; [speed] is pixels per tick.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Modifier.edgeScrollable(
    state: LazyListState,
    edgeFraction: Float = 0.12f,
    speed: Float = 26f,
): Modifier {
    val scope = rememberCoroutineScope()
    var width by remember { mutableStateOf(0) }
    // Positive scrolls right, negative left, zero rests. Held rather than acted on directly so one
    // loop owns the motion: a scroll per pointer event would stutter and fight the animation.
    var direction by remember { mutableStateOf(0f) }

    LaunchedEffect(direction) {
        if (direction == 0f) return@LaunchedEffect
        while (true) {
            state.scrollBy(direction * speed)
            withFrameNanos { }
        }
    }

    return this
        .onSizeChanged { size -> width = size.width }
        .onPointerEvent(PointerEventType.Move) { event ->
            val x = event.changes.firstOrNull()?.position?.x ?: return@onPointerEvent
            val edge = width * edgeFraction
            direction =
                when {
                    width <= 0 -> 0f
                    x < edge -> -1f
                    x > width - edge -> 1f
                    else -> 0f
                }
        }.onPointerEvent(PointerEventType.Exit) { direction = 0f }
}

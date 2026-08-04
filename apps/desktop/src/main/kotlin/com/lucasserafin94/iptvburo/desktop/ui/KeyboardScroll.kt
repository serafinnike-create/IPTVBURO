package com.lucasserafin94.iptvburo.desktop.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
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

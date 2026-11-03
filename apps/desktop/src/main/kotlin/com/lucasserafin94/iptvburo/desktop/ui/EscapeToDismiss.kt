package com.lucasserafin94.iptvburo.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent

/**
 * Escape closes the panel this is called from, as it does every other panel in the app.
 *
 * Listened for process-wide because these panels hold no focus of their own: a key handler on them
 * never heard the key. Only the panel opened last answers, so a dialog opened over the profile
 * switcher closes alone rather than taking the switcher with it.
 */
@Composable
fun EscapeToDismiss(onDismiss: () -> Unit) {
    val currentDismiss by rememberUpdatedState(onDismiss)
    DisposableEffect(Unit) {
        val handler: () -> Unit = { currentDismiss() }
        EscapeStack.push(handler)
        onDispose { EscapeStack.remove(handler) }
    }
}

private object EscapeStack {
    private val handlers = ArrayDeque<() -> Unit>()
    private var installed = false

    private val dispatcher =
        KeyEventDispatcher { event ->
            if (event.id != KeyEvent.KEY_PRESSED || event.keyCode != KeyEvent.VK_ESCAPE) return@KeyEventDispatcher false
            val top = synchronized(handlers) { handlers.lastOrNull() } ?: return@KeyEventDispatcher false
            top()
            true
        }

    fun push(handler: () -> Unit) {
        synchronized(handlers) {
            handlers.addLast(handler)
            if (!installed) {
                KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(dispatcher)
                installed = true
            }
        }
    }

    fun remove(handler: () -> Unit) {
        synchronized(handlers) {
            handlers.remove(handler)
            if (handlers.isEmpty() && installed) {
                KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(dispatcher)
                installed = false
            }
        }
    }
}

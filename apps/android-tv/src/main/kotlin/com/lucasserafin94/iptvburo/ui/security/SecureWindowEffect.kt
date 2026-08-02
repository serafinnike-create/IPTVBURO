package com.lucasserafin94.iptvburo.ui.security

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Temporarily protects the host Activity while sensitive credentials are on screen.
 *
 * The lease remembers whether this UI added [WindowManager.LayoutParams.FLAG_SECURE],
 * so disposing the dialog never clears a flag that was already owned by the Activity.
 */
@Composable
internal fun SecureActivityWindowEffect() {
    val context = LocalView.current.context
    val window = remember(context) { context.findActivity()?.window }

    DisposableEffect(window) {
        if (window == null) {
            onDispose { }
        } else {
            val lease =
                secureWindowFlagLease(
                    wasSecure =
                        window.attributes.flags and
                            WindowManager.LayoutParams.FLAG_SECURE != 0,
                )
            if (lease.addOnEnter) {
                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            onDispose {
                if (lease.clearOnExit) {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }
    }
}

internal data class SecureWindowFlagLease(
    val addOnEnter: Boolean,
    val clearOnExit: Boolean,
)

internal fun secureWindowFlagLease(wasSecure: Boolean): SecureWindowFlagLease =
    SecureWindowFlagLease(
        addOnEnter = !wasSecure,
        clearOnExit = !wasSecure,
    )

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        val base = current.baseContext
        if (base === current) return null
        current = base
    }
    return current as? Activity
}

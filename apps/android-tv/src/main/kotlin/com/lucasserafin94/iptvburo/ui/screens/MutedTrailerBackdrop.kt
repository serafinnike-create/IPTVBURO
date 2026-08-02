package com.lucasserafin94.iptvburo.ui.screens

import android.graphics.Color
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay

/** Official YouTube embed used only for source-provided trailer IDs; playback starts muted. */
@Composable
internal fun MutedTrailerBackdrop(
    youtubeId: String?,
    modifier: Modifier = Modifier,
) {
    val safeId = youtubeId?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{6,32}")) } ?: return
    var visible by remember(safeId) { mutableStateOf(false) }
    var pageReady by remember(safeId) { mutableStateOf(false) }
    var webView by remember(safeId) { mutableStateOf<WebView?>(null) }
    LaunchedEffect(safeId) {
        delay(TRAILER_DELAY_MILLIS)
        visible = true
    }
    DisposableEffect(safeId) {
        onDispose {
            webView?.stopLoading()
            webView?.destroy()
            webView = null
        }
    }
    if (!visible) return
    AndroidView(
        modifier = modifier.alpha(if (pageReady) 0.86f else 0f),
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(Color.TRANSPARENT)
                isFocusable = false
                isFocusableInTouchMode = false
                isClickable = false
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = false
                settings.mediaPlaybackRequiresUserGesture = false
                webViewClient =
                    object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            if (url?.startsWith("https://www.youtube-nocookie.com/") == true) {
                                pageReady = true
                            }
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?,
                        ) {
                            if (request?.isForMainFrame == true) pageReady = false
                        }
                    }
                loadUrl(
                    "https://www.youtube-nocookie.com/embed/$safeId" +
                        "?autoplay=1&mute=1&controls=0&playsinline=1&rel=0&loop=1&playlist=$safeId",
                )
                webView = this
            }
        },
    )
}

private const val TRAILER_DELAY_MILLIS = 2_000L

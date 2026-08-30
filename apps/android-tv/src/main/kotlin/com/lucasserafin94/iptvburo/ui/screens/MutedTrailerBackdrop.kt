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
    /**
     * Whether the trailer should carry sound.
     *
     * It always *starts* muted regardless — no engine lets a video autoplay with audio, and asking
     * for sound up front means it does not start at all, leaving a play button over a still frame.
     * So the sound is raised once the embed reports itself playing, which is the point at which the
     * autoplay has already been granted and the volume is no longer a new request.
     */
    soundOn: Boolean = false,
) {
    val safeId = youtubeId?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{6,32}")) } ?: return
    var visible by remember(safeId) { mutableStateOf(false) }
    var pageReady by remember(safeId) { mutableStateOf(false) }
    /** A trailer that cannot load is dropped entirely: the artwork behind it is the better answer. */
    var failed by remember(safeId) { mutableStateOf(false) }
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

    // Fully transparent until the embed reports itself ready, and **removed from the composition**
    // rather than merely faded when it fails.
    //
    // A WebView paints its own white page background before any content arrives, and this one sits
    // on top of the artwork. Left in the tree at alpha 0 it still occupied the hero, and any state
    // where the alpha did not reach 0 — a load that half-succeeded, a redirect the old readiness
    // check did not recognise — showed as a white sheet over the whole backdrop. That is the
    // permanent white the user reported.
    if (failed) return

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
                // The only channel the page has back into the app, and it carries one bit: the
                // player started. Nothing is read from the page, so no page content can reach app
                // state through it.
                val host = this
                addJavascriptInterface(
                    object {
                        @android.webkit.JavascriptInterface
                        fun onPlaying() {
                            // The bridge is called on a WebView worker thread; Compose state must
                            // be touched from the main thread.
                            host.post {
                                pageReady = true
                                // Raised here and nowhere earlier: the engine has just granted the
                                // autoplay, so turning the volume up is not a fresh request it can
                                // refuse. Asked for up front, the trailer would not have started.
                                if (soundOn) host.evaluateJavascript(TRAILER_RAISE_SOUND, null)
                            }
                        }
                    },
                    TRAILER_BRIDGE,
                )
                webViewClient =
                    object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            // A finished page is not a playing video. The embed shows a white
                            // consent or error page under exactly the same "finished" signal, and
                            // revealing it laid a white sheet over the artwork — the fault the
                            // user saw. Only the player reporting that it is actually playing
                            // counts, which the IFrame API tells us via onStateChange.
                            if (url == null || url == "about:blank") return
                            view?.evaluateJavascript(TRAILER_READY_PROBE, null)
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?,
                        ) {
                            if (request?.isForMainFrame == true) {
                                pageReady = false
                                failed = true
                            }
                        }
                    }
                // `enablejsapi` is what makes the player emit state changes; without it the page
                // loads but never says whether it is playing.
                loadUrl(
                    "https://www.youtube-nocookie.com/embed/$safeId" +
                        "?autoplay=1&mute=1&controls=0&playsinline=1&rel=0&loop=1" +
                        "&playlist=$safeId&enablejsapi=1",
                )
                webView = this
            }
        },
    )
}

private const val TRAILER_DELAY_MILLIS = 2_000L

/** Name the page sees for the one-bit bridge back into the app. */
private const val TRAILER_BRIDGE = "BuroTrailer"

/**
 * Turns the sound up on a trailer that is already playing.
 *
 * Run only from the playing callback. The embed always loads muted because that is the only way it
 * loads at all; by the time the player reports PLAYING the autoplay has been granted, and raising
 * the volume afterwards is not something the engine refuses.
 */
private val TRAILER_RAISE_SOUND =
    """
    (function () {
      var frame = document.querySelector('iframe') || window;
      var target = frame.contentWindow || frame;
      target.postMessage('{"event":"command","func":"unMute","args":[]}', '*');
      target.postMessage('{"event":"command","func":"setVolume","args":[100]}', '*');
    })();
    """.trimIndent()

/**
 * Reports the embed as ready only once it is genuinely playing.
 *
 * Listens for YouTube's PLAYING state (`1`) rather than trusting page load, because the consent and
 * unavailable-video pages both finish loading and are white. Polls as a fallback: on a page where
 * the IFrame API never attaches, `postMessage` produces no listener, and a trailer that silently
 * never reveals itself is the correct failure — the artwork stays.
 */
private val TRAILER_READY_PROBE =
    """
    (function () {
      if (window.__buroTrailerProbe) return;
      window.__buroTrailerProbe = true;
      window.addEventListener('message', function (event) {
        try {
          var data = JSON.parse(event.data);
          if (data && data.event === 'onStateChange' && data.info === 1) {
            $TRAILER_BRIDGE.onPlaying();
          }
        } catch (ignored) {}
      });
      var frame = document.querySelector('iframe') || window;
      var target = frame.contentWindow || frame;
      target.postMessage('{"event":"listening"}', '*');
      setInterval(function () {
        target.postMessage('{"event":"listening"}', '*');
      }, 1000);
    })();
    """.trimIndent()

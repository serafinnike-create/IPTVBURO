package com.lucasserafin94.iptvburo.ui.screens

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.lucasserafin94.iptvburo.R
import com.lucasserafin94.iptvburo.domain.model.WebNavigation
import com.lucasserafin94.iptvburo.domain.model.WebServiceBrand
import com.lucasserafin94.iptvburo.domain.model.WebServicePolicy
import com.lucasserafin94.iptvburo.ui.designsystem.BuroButton
import com.lucasserafin94.iptvburo.ui.designsystem.BuroButtonStyle
import com.lucasserafin94.iptvburo.ui.theme.BuroCanvas
import com.lucasserafin94.iptvburo.ui.theme.BuroTextPrimary
import com.lucasserafin94.iptvburo.ui.theme.BuroTextSecondary

/**
 * A web service's own page — GoDorama — inside BURO.
 *
 * The user signs in on the service's page with their own account; nothing here reads, fills or
 * stores the password, and nothing reads the page's content back into the app. The service plays
 * its own episodes and keeps its own history. What BURO decides is only which addresses load here
 * ([WebServicePolicy.navigation]): the service's own pages stay, other https pages go to the system
 * browser, anything else is not opened.
 *
 * Back walks the page's own history first and leaves the screen only once there is none, so a
 * remote's Back behaves the way it does in the rest of the app.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun WebServiceScreen(
    brand: WebServiceBrand,
    clearSessionFirst: Boolean,
    onClose: () -> Unit,
    onSignedOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var webView by remember(brand.id) { mutableStateOf<WebView?>(null) }
    var failed by remember(brand.id) { mutableStateOf(false) }
    /** The page's own full-screen player, when it asks for one. */
    var fullScreenView by remember(brand.id) { mutableStateOf<View?>(null) }
    var fullScreenCallback by remember(brand.id) { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    DisposableEffect(brand.id) {
        onDispose {
            webView?.stopLoading()
            webView?.destroy()
            webView = null
        }
    }

    BackHandler {
        val live = webView
        when {
            fullScreenView != null -> fullScreenCallback?.onCustomViewHidden()
            live != null && live.canGoBack() -> live.goBack()
            else -> onClose()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BuroCanvas),
    ) {
        if (fullScreenView == null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BuroButton(onClick = onClose, style = BuroButtonStyle.Secondary) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    Text(text = stringResource(R.string.web_service_close))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = brand.displayName,
                        color = BuroTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.web_service_history_note, brand.displayName),
                        color = BuroTextSecondary,
                        fontSize = 12.sp,
                    )
                }
                BuroButton(
                    onClick = {
                        clearWebSession {
                            onSignedOut()
                            webView?.loadUrl(brand.homeUrl)
                        }
                    },
                    style = BuroButtonStyle.Secondary,
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
                    Text(text = stringResource(R.string.web_service_sign_out))
                }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (failed) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(R.string.web_service_load_error, brand.displayName),
                        color = BuroTextPrimary,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    BuroButton(onClick = {
                        failed = false
                        webView?.reload()
                    }) {
                        Text(text = stringResource(R.string.web_service_retry))
                    }
                }
            }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    val container = FrameLayout(context)
                    val view =
                        WebView(context).apply {
                            layoutParams =
                                FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                )
                            // The service is a web app: it needs scripts and its own storage to
                            // keep the user signed in and to remember where they stopped.
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.mediaPlaybackRequiresUserGesture = true
                            // Nothing on disk outside the view's own storage, and no file access.
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.setSupportMultipleWindows(false)
                            settings.javaScriptCanOpenWindowsAutomatically = false
                            isFocusable = true
                            isFocusableInTouchMode = true
                            CookieManager.getInstance().setAcceptCookie(true)
                            // Third-party cookies stay off: the service needs only its own.
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                            webViewClient =
                                object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView,
                                        request: WebResourceRequest,
                                    ): Boolean {
                                        if (!request.isForMainFrame) return false
                                        return when (val decision = WebServicePolicy.navigation(brand, request.url.toString())) {
                                            WebNavigation.StayInside -> false
                                            is WebNavigation.OpenOutside -> {
                                                openOutside(view, decision.url)
                                                true
                                            }
                                            WebNavigation.Block -> true
                                        }
                                    }

                                    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                        failed = false
                                    }

                                    override fun onReceivedError(
                                        view: WebView,
                                        request: WebResourceRequest,
                                        error: WebResourceError,
                                    ) {
                                        // Only the page itself. A missing cover is the page's
                                        // business and must not replace the whole screen.
                                        if (request.isForMainFrame) failed = true
                                    }
                                }
                            webChromeClient =
                                object : WebChromeClient() {
                                    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                                        fullScreenView = view
                                        fullScreenCallback = callback
                                        container.addView(
                                            view,
                                            FrameLayout.LayoutParams(
                                                ViewGroup.LayoutParams.MATCH_PARENT,
                                                ViewGroup.LayoutParams.MATCH_PARENT,
                                            ),
                                        )
                                    }

                                    override fun onHideCustomView() {
                                        fullScreenView?.let(container::removeView)
                                        fullScreenView = null
                                        fullScreenCallback = null
                                    }
                                }
                        }
                    container.addView(view)
                    webView = view
                    if (clearSessionFirst) {
                        clearWebSession { view.loadUrl(brand.homeUrl) }
                    } else {
                        view.loadUrl(brand.homeUrl)
                    }
                    view.requestFocus()
                    container
                },
            )
        }
    }
}

/**
 * Clears the browser view's cookies and site storage, then runs [then] on the main thread.
 *
 * The whole jar rather than the service's cookies alone: Android offers no per-site removal, and
 * the only other user of the view's cookies is the muted trailer, which keeps none worth having.
 */
private fun clearWebSession(then: () -> Unit) {
    WebStorage.getInstance().deleteAllData()
    CookieManager.getInstance().removeAllCookies {
        CookieManager.getInstance().flush()
        then()
    }
}

private fun openOutside(
    view: WebView,
    url: String,
) {
    try {
        view.context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (_: ActivityNotFoundException) {
        // A television often has no browser at all. Nothing else is safe to do with the address.
    }
}

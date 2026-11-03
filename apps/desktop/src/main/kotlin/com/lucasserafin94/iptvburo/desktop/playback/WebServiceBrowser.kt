package com.lucasserafin94.iptvburo.desktop.playback

import com.lucasserafin94.iptvburo.desktop.platform.openUriExternally
import com.lucasserafin94.iptvburo.desktop.security.StoredWebCookie
import com.lucasserafin94.iptvburo.desktop.security.WebServiceSessionStore
import com.lucasserafin94.iptvburo.domain.model.WebNavigation
import com.lucasserafin94.iptvburo.domain.model.WebServiceBrand
import com.lucasserafin94.iptvburo.domain.model.WebServicePolicy
import java.awt.BorderLayout
import java.awt.Color
import java.net.URI
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JPanel
import javax.swing.SwingUtilities
import org.cef.CefClient
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLifeSpanHandlerAdapter
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.network.CefCookie
import org.cef.network.CefCookieManager
import org.cef.network.CefRequest

/**
 * A web service's own page — GoDorama — in the app, on the same embedded Chromium as the trailers.
 *
 * The user signs in on the service's page with their own account. Nothing here reads, fills or
 * stores a password, and nothing reads the page's content back into the app; the page plays its own
 * episodes and the service keeps its own history. What this class decides is which addresses load
 * ([WebServicePolicy.navigation]) and whose cookies the engine holds ([WebServiceSessionStore]).
 *
 * Windows only for now: the engine ships in the Windows installer. Where it does not start, the app
 * opens the service in the system browser instead (see DesktopAppState.openWebService).
 */
class WebServiceBrowser(
    private val brand: WebServiceBrand,
    private val profileId: String,
    private val sessions: WebServiceSessionStore = WebServiceSessionStore(),
) {
    private val disposed = AtomicBoolean(false)
    private var client: CefClient? = null
    private var browser: CefBrowser? = null

    /** The panel showing the service, or null when the engine is unavailable. */
    fun createComponent(): JPanel? =
        runCatching {
            val app = TrailerBrowser.engine() ?: return null
            restoreSession()
            val cefClient = app.createClient().also { client = it }
            cefClient.addRequestHandler(
                object : CefRequestHandlerAdapter() {
                    override fun onBeforeBrowse(
                        browser: CefBrowser?,
                        frame: CefFrame?,
                        request: CefRequest?,
                        userGesture: Boolean,
                        isRedirect: Boolean,
                    ): Boolean {
                        // Frames inside the page are the page's business; only what the window
                        // itself navigates to is decided here.
                        if (frame?.isMain != true) return false
                        val url = request?.url ?: return true
                        return when (val decision = WebServicePolicy.navigation(brand, url)) {
                            WebNavigation.StayInside -> false
                            is WebNavigation.OpenOutside -> {
                                openOutside(decision.url)
                                true
                            }
                            WebNavigation.Block -> true
                        }
                    }
                },
            )
            cefClient.addLifeSpanHandler(
                object : CefLifeSpanHandlerAdapter() {
                    // No second window, ever: the service's own pages load in this one, anything
                    // else goes to the system browser or nowhere.
                    override fun onBeforePopup(
                        browser: CefBrowser?,
                        frame: CefFrame?,
                        targetUrl: String?,
                        targetFrameName: String?,
                    ): Boolean {
                        val url = targetUrl ?: return true
                        when (val decision = WebServicePolicy.navigation(brand, url)) {
                            WebNavigation.StayInside -> browser?.loadURL(url)
                            is WebNavigation.OpenOutside -> openOutside(decision.url)
                            WebNavigation.Block -> Unit
                        }
                        return true
                    }
                },
            )
            val cefBrowser = cefClient.createBrowser(brand.homeUrl, false, false).also { browser = it }
            JPanel(BorderLayout()).apply {
                background = Color.BLACK
                add(cefBrowser.uiComponent.apply { background = Color.BLACK }, BorderLayout.CENTER)
            }
        }.getOrElse { error ->
            // The type only: the message is not ours to predict, and this is a page with a session.
            println("[web-service] could not open the embedded page: ${error::class.simpleName}")
            null
        }

    /** Signs out: forgets the saved session, clears the engine's cookies and reloads the start page. */
    fun signOut() {
        sessions.clear(profileId, brand)
        deleteEngineCookies()
        browser?.loadURL(brand.homeUrl)
    }

    /**
     * Saves the session and closes the page.
     *
     * The cookies are read before the browser goes away, and the engine's copies are then removed,
     * so the next profile to open the service starts from nothing but its own.
     */
    fun dispose() {
        if (!disposed.compareAndSet(false, true)) return
        val manager = CefCookieManager.getGlobalManager()
        if (manager != null) {
            val collected = mutableListOf<StoredWebCookie>()
            val visiting =
                manager.visitUrlCookies(brand.homeUrl, true) { cookie, count, total, _ ->
                    collected += cookie.toStored()
                    if (count + 1 >= total) {
                        sessions.save(profileId, brand, collected.toList())
                        deleteEngineCookies()
                    }
                    true
                }
            // No visit at all means no cookie for the service: nobody is signed in.
            if (!visiting) deleteEngineCookies()
        }
        browser?.let { live ->
            runCatching { live.stopLoad() }
            runCatching { live.close(true) }
        }
        browser = null
        runCatching { client?.dispose() }
        client = null
    }

    private fun restoreSession() {
        val manager = CefCookieManager.getGlobalManager() ?: return
        deleteEngineCookies()
        sessions.load(profileId, brand).forEach { stored ->
            val host = stored.domain.trimStart('.')
            manager.setCookie("https://$host${stored.path.ifBlank { "/" }}", stored.toCef())
        }
    }

    private fun deleteEngineCookies() {
        val manager = CefCookieManager.getGlobalManager() ?: return
        brand.allowedHosts.forEach { host ->
            manager.deleteCookies("https://$host/", "")
            manager.deleteCookies("https://www.$host/", "")
        }
    }

    private fun openOutside(url: String) {
        SwingUtilities.invokeLater { runCatching { openUriExternally(URI(url)) } }
    }

    companion object {
        /** Whether the page can be shown in the app on this machine. */
        fun isAvailable(): Boolean = TrailerBrowser.isAvailable()
    }
}

private fun CefCookie.toStored(): StoredWebCookie =
    StoredWebCookie(
        name = name.orEmpty(),
        value = value.orEmpty(),
        domain = domain.orEmpty(),
        path = path.orEmpty(),
        secure = secure,
        httpOnly = httponly,
        expiresEpochMillis = if (hasExpires) expires?.time else null,
    )

private fun StoredWebCookie.toCef(): CefCookie {
    val now = Date()
    return CefCookie(
        name,
        value,
        domain,
        path.ifBlank { "/" },
        secure,
        httpOnly,
        now,
        now,
        expiresEpochMillis != null,
        expiresEpochMillis?.let(::Date),
    )
}

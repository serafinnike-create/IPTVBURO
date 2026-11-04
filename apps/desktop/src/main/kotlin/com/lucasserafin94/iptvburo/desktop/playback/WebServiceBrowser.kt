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
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefLoadHandlerAdapter
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

    /** Edge WebView2, where the service plays in it on this machine; see [WebView2Page]. */
    private val webView2: WebView2Page? =
        if (WebView2Page.usableFor(brand)) WebView2Page(brand, profileId) else null

    /** The panel showing the service, or null when the engine is unavailable. */
    fun createComponent(): JPanel? {
        webView2?.let { page -> return page.createComponent() }
        return createChromiumComponent()
    }

    private fun createChromiumComponent(): JPanel? =
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
                        val decision = WebServicePolicy.navigation(brand, url)
                        trace(
                            "navigate ${request.method} ${WebServicePolicy.loggablePage(url)} " +
                                "redirect=$isRedirect gesture=$userGesture -> ${decision.label()}",
                        )
                        return when (decision) {
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
                        val decision = WebServicePolicy.navigation(brand, url)
                        trace("popup ${WebServicePolicy.loggablePage(url)} -> ${decision.label()}")
                        when (decision) {
                            WebNavigation.StayInside -> browser?.loadURL(url)
                            is WebNavigation.OpenOutside -> openOutside(decision.url)
                            WebNavigation.Block -> Unit
                        }
                        return true
                    }
                },
            )
            cefClient.addLoadHandler(
                object : CefLoadHandlerAdapter() {
                    // Saved after every page the service finishes loading, not only when the window
                    // closes: signing in is a page load, and a user who then quits the app with the
                    // window still open must find themselves still signed in next time.
                    override fun onLoadEnd(
                        browser: CefBrowser?,
                        frame: CefFrame?,
                        httpStatusCode: Int,
                    ) {
                        if (frame?.isMain != true) return
                        trace("loaded ${WebServicePolicy.loggablePage(frame.url.orEmpty())} status=$httpStatusCode")
                        if (!disposed.get()) saveSession()
                    }

                    override fun onLoadError(
                        browser: CefBrowser?,
                        frame: CefFrame?,
                        errorCode: CefLoadHandler.ErrorCode?,
                        errorText: String?,
                        failedUrl: String?,
                    ) {
                        if (frame?.isMain != true) return
                        trace("load failed ${WebServicePolicy.loggablePage(failedUrl.orEmpty())} error=$errorCode")
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

    private val ownerKey: String get() = "$profileId/${brand.id}"

    /** Signs out: forgets the saved session, clears the engine's cookies and reloads the start page. */
    fun signOut() {
        webView2?.let { page ->
            page.signOut()
            return
        }
        sessions.clear(profileId, brand)
        deleteEngineCookies("signed out")
        browser?.loadURL(brand.homeUrl)
    }

    /**
     * Saves the session and closes the page.
     *
     * The engine keeps its copies of the cookies. Removing them here, and putting them back from
     * disk on the next open, is what signed the viewer out: the removal ran asynchronously and could
     * land after the next open had already restored them, and a cookie the save had missed was gone
     * for good. The engine's cookies are replaced only when a *different* profile opens the service
     * (see [restoreSession]).
     */
    fun dispose() {
        if (!disposed.compareAndSet(false, true)) return
        webView2?.let { page ->
            page.dispose()
            return
        }
        saveSession()
        browser?.let { live ->
            runCatching { live.stopLoad() }
            runCatching { live.close(true) }
        }
        browser = null
        runCatching { client?.dispose() }
        client = null
    }

    /**
     * Reads the service's cookies from the engine and seals them for this profile, for the next
     * time the app starts.
     *
     * Every cookie the engine holds for the service's hosts, whatever its path: asking by the start
     * page's address returned only the cookies that page would be sent, and a sign-in cookie scoped
     * to another path was never saved. Skipped once another profile owns the engine's cookies, so a
     * late visit can never file one profile's session under another.
     */
    private fun saveSession() {
        val manager = CefCookieManager.getGlobalManager() ?: return
        val collected = mutableListOf<StoredWebCookie>()
        manager.visitAllCookies { cookie, count, total, _ ->
            val candidate = cookie.toStored()
            if (WebServiceSessionStore.belongsTo(candidate, brand)) collected += candidate
            if (count + 1 >= total && engineCookieOwner == ownerKey && collected.isNotEmpty()) {
                sessions.save(profileId, brand, collected.toList())
                trace("saved ${collected.size} cookies (${collected.cookieNames()})")
            }
            true
        }
        // No visit, or nothing for the service: nothing is saved over the stored session. A page
        // that loaded before its cookies arrived must not sign the user out.
    }

    /**
     * Gives the engine this profile's cookies, unless it already holds them.
     *
     * The same profile coming back finds its cookies exactly where it left them — nothing is
     * removed, nothing re-added. Only a different profile, or the first open since the app started,
     * clears the service's cookies and puts this profile's saved ones in.
     */
    private fun restoreSession() {
        val manager = CefCookieManager.getGlobalManager() ?: return
        if (engineCookieOwner == ownerKey) {
            trace("restore skipped: the engine already holds this profile's cookies")
            return
        }
        engineCookieOwner = ownerKey
        deleteEngineCookies("restore")
        val saved = sessions.load(profileId, brand)
        trace("restore ${saved.size} saved cookies (${saved.cookieNames()})")
        // Host-only, the way the site set them: an empty domain is how the engine is told so.
        WebServiceSessionStore.forRestore(saved).forEach { stored ->
            manager.setCookie("https://${stored.domain}${stored.path}", stored.toCef(domain = ""))
        }
    }

    private fun deleteEngineCookies(reason: String) {
        trace("engine cookies cleared: $reason")
        val manager = CefCookieManager.getGlobalManager() ?: return
        brand.allowedHosts.forEach { host ->
            manager.deleteCookies("https://$host/", "")
            manager.deleteCookies("https://www.$host/", "")
        }
    }

    /**
     * One line of the sign-in's path through the log, always written: a sign-in that "goes through
     * and then shows the page as if nobody signed in" can only be followed from the outside this
     * way. Addresses are reduced to host and path ([WebServicePolicy.loggablePage]); cookies are
     * counted and named, never their values.
     */
    private fun trace(line: String) {
        println("[web-service] $line")
    }

    private fun List<StoredWebCookie>.cookieNames(): String =
        joinToString(",") { cookie -> cookie.name.take(MAX_COOKIE_NAME) }.ifEmpty { "none" }

    private fun WebNavigation.label(): String =
        when (this) {
            WebNavigation.StayInside -> "inside"
            is WebNavigation.OpenOutside -> "system browser"
            WebNavigation.Block -> "blocked"
        }

    private fun openOutside(url: String) {
        SwingUtilities.invokeLater { runCatching { openUriExternally(URI(url)) } }
    }

    companion object {
        /** Whether the page can be shown in the app on this machine. */
        fun isAvailable(): Boolean = TrailerBrowser.isAvailable()

        /**
         * Whether [brand] plays inside the app here: only through WebView2, because the embedded
         * Chromium has no licensed video codecs and the service refuses to play in it.
         */
        fun playsInApp(brand: WebServiceBrand): Boolean = WebView2Page.usableFor(brand)

        /**
         * Whose GoDorama cookies the process-wide engine holds now, as "profile/service".
         *
         * One engine serves every profile, so this is what keeps one adult out of another's account
         * without throwing away the cookies of the profile that simply came back.
         */
        @Volatile
        private var engineCookieOwner: String? = null

        private const val MAX_COOKIE_NAME = 32
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

private fun StoredWebCookie.toCef(domain: String = this.domain): CefCookie {
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

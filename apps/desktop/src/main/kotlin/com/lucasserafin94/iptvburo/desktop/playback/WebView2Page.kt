package com.lucasserafin94.iptvburo.desktop.playback

import ca.weblite.webview.PopupDisposition
import ca.weblite.webview.WebViewNavigationEvent
import ca.weblite.webview.WebViewNavigationHandler
import ca.weblite.webview.WebViewPermissionHandler
import ca.weblite.webview.WebViewPermissionRequest
import ca.weblite.webview.WebViewPopupEvent
import ca.weblite.webview.WebViewPopupHandler
import ca.weblite.webview.swing.WebViewComponent
import com.lucasserafin94.iptvburo.desktop.DesktopStorage
import com.lucasserafin94.iptvburo.desktop.platform.openUriExternally
import com.lucasserafin94.iptvburo.domain.model.WebNavigation
import com.lucasserafin94.iptvburo.domain.model.WebServiceBrand
import com.lucasserafin94.iptvburo.domain.model.WebServicePolicy
import com.lucasserafin94.iptvburo.domain.model.WebServices
import com.sun.jna.Platform
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinReg
import java.awt.BorderLayout
import java.awt.Color
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JPanel
import javax.swing.SwingUtilities

/**
 * GoDorama inside the app on Windows, through Microsoft Edge WebView2.
 *
 * The embedded Chromium the trailers use is the open-source build: no licensed video codecs, no
 * Widevine, and GoDorama refuses to play there. WebView2 is the Edge engine Windows itself ships,
 * with both, so the episodes play inside BURO's own window.
 *
 * Each BURO profile gets its own WebView2 data folder, which keeps its sign-in between launches and
 * keeps one adult out of another's account. Nothing in that folder is read by BURO; the engine owns
 * it. The page's addresses are held to the same rules as before ([WebServicePolicy.navigation]):
 * the service's own pages load here, other pages open in the system browser, nothing else opens,
 * no popup window is ever created, and the page is refused the camera and microphone.
 *
 * Only for the services listed in [IN_APP_SERVICES].
 */
internal class WebView2Page(
    private val brand: WebServiceBrand,
    profileId: String,
) {
    private val dataFolder: Path = dataFolderFor(profileId, brand.id)
    private var view: WebViewComponent? = null

    /** The panel showing the service, or null when WebView2 could not be created. */
    fun createComponent(): JPanel? =
        runCatching {
            Files.createDirectories(dataFolder)
            // Read by the engine when this view is created, so it is set immediately before — the
            // view of another profile created a moment ago keeps the folder it was created with.
            Kernel32.INSTANCE.SetEnvironmentVariable(USER_DATA_FOLDER_VARIABLE, dataFolder.toString())
            val created = WebViewComponent.create()
            created.setDefaultContextMenuEnabled(false)
            created.setNavigationHandler(
                WebViewNavigationHandler { event: WebViewNavigationEvent ->
                    when (val decision = WebServicePolicy.navigation(brand, event.url().orEmpty())) {
                        WebNavigation.StayInside -> true
                        is WebNavigation.OpenOutside -> {
                            openOutside(decision.url)
                            false
                        }
                        WebNavigation.Block -> false
                    }
                },
            )
            created.setPopupHandler(
                object : WebViewPopupHandler {
                    // No second window, ever: the service's own pages load in this one, anything
                    // else goes to the system browser or nowhere.
                    override fun popupDisposition(event: WebViewPopupEvent): PopupDisposition {
                        val target = event.targetUrl().orEmpty()
                        when (val decision = WebServicePolicy.navigation(brand, target)) {
                            WebNavigation.StayInside -> SwingUtilities.invokeLater { view?.setUrl(target) }
                            is WebNavigation.OpenOutside -> openOutside(decision.url)
                            WebNavigation.Block -> Unit
                        }
                        return PopupDisposition.BLOCK
                    }
                },
            )
            created.setPermissionHandler(WebViewPermissionHandler { request: WebViewPermissionRequest -> request.deny() })
            created.setUrl(brand.homeUrl)
            view = created
            JPanel(BorderLayout()).apply {
                background = Color.BLACK
                add(created, BorderLayout.CENTER)
            }
        }.getOrElse { error ->
            // The type only: the message is not ours to predict, and this is a page with a session.
            println("[web-service] WebView2 could not open the page: ${error::class.simpleName}")
            null
        }

    /** Signs out through the service's own sign-out page, which clears its session for this profile. */
    fun signOut() {
        val signOut = runCatching { URI(brand.homeUrl).resolve(SIGN_OUT_PATH).toString() }.getOrNull() ?: return
        view?.setUrl(signOut)
    }

    fun dispose() {
        view?.let { live -> runCatching { live.dispose() } }
        view = null
    }

    private fun openOutside(url: String) {
        SwingUtilities.invokeLater { runCatching { openUriExternally(URI(url)) } }
    }

    companion object {
        /**
         * The services that open inside the app.
         *
         * GoDorama only. A service is added here deliberately, one at a time, rather than every
         * registered service inheriting an in-app window by default.
         */
        internal val IN_APP_SERVICES: Set<String> = setOf(WebServices.GODORAMA.id)

        private const val USER_DATA_FOLDER_VARIABLE = "WEBVIEW2_USER_DATA_FOLDER"
        private const val SIGN_OUT_PATH = "/logout"

        /** The WebView2 runtime's id, under which its installer registers its version. */
        private const val RUNTIME_CLIENT_KEY = "{F3017226-FE2A-4295-8BDF-00C3A9A7E4C5}"

        /** Whether [brand] opens inside the app on this machine. */
        fun usableFor(brand: WebServiceBrand): Boolean =
            brand.id in IN_APP_SERVICES && Platform.isWindows() && libraryPresent && runtimeInstalled

        private val libraryPresent: Boolean by lazy {
            runCatching { Class.forName("ca.weblite.webview.swing.WebViewComponent") }.isSuccess
        }

        /**
         * Whether the Edge WebView2 runtime is installed, machine-wide or for this user.
         *
         * Present on every current Windows 11 and on Windows 10 with an up-to-date Edge. Checked
         * rather than assumed, so a machine without it falls back to the Edge window instead of
         * showing an empty panel.
         */
        private val runtimeInstalled: Boolean by lazy {
            runCatching {
                val machine = "SOFTWARE\\WOW6432Node\\Microsoft\\EdgeUpdate\\Clients\\$RUNTIME_CLIENT_KEY"
                val user = "Software\\Microsoft\\EdgeUpdate\\Clients\\$RUNTIME_CLIENT_KEY"
                hasVersion(WinReg.HKEY_LOCAL_MACHINE, machine) || hasVersion(WinReg.HKEY_CURRENT_USER, user)
            }.getOrDefault(false)
        }

        private fun hasVersion(
            root: WinReg.HKEY,
            key: String,
        ): Boolean =
            Advapi32Util.registryKeyExists(root, key) &&
                Advapi32Util.registryValueExists(root, key, "pv") &&
                Advapi32Util.registryGetStringValue(root, key, "pv").let { it.isNotBlank() && it != "0.0.0.0" }

        /**
         * One folder per BURO profile and service. Apart from the Edge-window profiles, whose
         * format is the browser's and not the engine's.
         */
        internal fun dataFolderFor(
            profileId: String,
            serviceId: String,
        ): Path {
            val safe = { value: String -> value.replace(Regex("[^A-Za-z0-9-]"), "_").take(64) }
            return DesktopStorage.localData().resolve("webview2-profiles").resolve("${safe(profileId)}-${safe(serviceId)}")
        }
    }
}

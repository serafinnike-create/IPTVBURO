package com.lucasserafin94.iptvburo.desktop.playback

import java.net.URI
import javax.swing.SwingUtilities

/**
 * Tells the app when a profile signs in to a web service, or out of it.
 *
 * Home shows a service's tile only to a profile that is signed in — the way a streaming service
 * appears once you have an account with it. The page knows when that happens; the app's state is
 * where the tile is decided. This carries the one fact across, on the interface thread.
 */
object WebServiceSignIns {
    /** Set once by the app's state. Receives the profile, the service, and whether now signed in. */
    @Volatile
    var listener: ((profileId: String, serviceId: String, signedIn: Boolean) -> Unit)? = null

    fun report(
        profileId: String,
        serviceId: String,
        signedIn: Boolean,
    ) {
        val target = listener ?: return
        SwingUtilities.invokeLater { target(profileId, serviceId, signedIn) }
    }
}

/**
 * Recognises a completed sign-in from the pages the service goes through.
 *
 * BURO never sees the form or the password. What it does see is where the page goes: the service's
 * sign-in page, then — once the account is accepted — one of its other pages. A sign-in that fails
 * stays on the sign-in page and is not counted.
 */
internal class SignInWatcher {
    private var onSignInPage = false

    /** Feeds one page the service loaded. True when this page completes a sign-in. */
    fun observe(url: String): Boolean {
        val path = runCatching { URI(url).path.orEmpty() }.getOrDefault("").lowercase().trimEnd('/')
        val isSignInPage = SIGN_IN_PATHS.any { segment -> path == "/$segment" || path.startsWith("/$segment/") }
        if (isSignInPage) {
            onSignInPage = true
            return false
        }
        val completed = onSignInPage
        onSignInPage = false
        return completed
    }

    private companion object {
        val SIGN_IN_PATHS = listOf("login", "entrar", "signin", "sign-in")
    }
}

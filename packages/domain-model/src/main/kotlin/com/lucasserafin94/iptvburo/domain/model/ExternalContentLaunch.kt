package com.lucasserafin94.iptvburo.domain.model

/**
 * How a title on someone else's service gets opened.
 *
 * BURO hands the user over to the official app or the service's own page and stops there. It never
 * plays a protected stream in its own player, never resolves an internal media URL, never reuses a
 * session cookie and never automates a login — the app is a signpost, not a way in. Everything in
 * this file exists to make that boundary something the type system enforces rather than something a
 * future caller has to remember.
 */
/** What a caller is allowed to do with a launch request. */
sealed interface LaunchDecision {
    /** Open the provider's own app. */
    data class OpenApp(val uri: String) : LaunchDecision

    /** Open the provider's page in the system browser — not inside BURO. */
    data class OpenWeb(val url: String) : LaunchDecision

    /** Nothing safe to open. [reason] is for the user, not for a log. */
    data class Unavailable(val reason: LaunchRefusal) : LaunchDecision
}

enum class LaunchRefusal {
    /**
     * Neither address survived checking.
     *
     * [ExternalLaunchTarget] already rejects a target with no addresses at all, so reaching this
     * means every address it did carry was refused for one of the reasons below.
     */
    NO_TARGET,

    /** The address was not a plain web or app link — a file path, a raw stream, javascript:. */
    UNSUPPORTED_SCHEME,

    /** The address looked like a media stream rather than a page. */
    LOOKS_LIKE_A_STREAM,

    /** The address carried credentials or a token. */
    CARRIES_CREDENTIALS,
}

/**
 * Turns a [ExternalLaunchTarget] into something a platform layer may act on.
 *
 * Pure and platform-free on purpose: the desktop and Android shells each know how to open a URI,
 * but neither should be deciding what is safe to open.
 */
object ExternalContentLauncher {
    /** Schemes that can only ever open a page or an installed app. */
    private val WEB_SCHEMES = setOf("https")

    /**
     * Extensions and markers that say "this is the video itself".
     *
     * If one of these appears, the address is a stream rather than a page, which means something
     * upstream tried to hand BURO a playable URL. That is exactly what must not happen, so it is
     * refused rather than quietly opened.
     */
    private val STREAM_MARKERS =
        listOf(".m3u8", ".mpd", ".ts?", ".ts", ".mp4", ".mkv", "/manifest", "widevine", "playready")

    /** Query keys that would carry a session out of the app. */
    private val CREDENTIAL_KEYS =
        listOf("token=", "access_token=", "auth=", "session=", "password=", "cookie=", "signature=", "sig=")

    /**
     * Prefers the provider's app over its website: a deep link lands on the title, a web page often
     * lands on a login wall. Falls back to the web address, and refuses when neither is usable.
     */
    fun decide(target: ExternalLaunchTarget): LaunchDecision {
        val appUri = target.appDeepLink?.takeIf { it.isNotBlank() }
        val appRefusal = appUri?.let { uri -> refuse(uri, allowCustomScheme = true) }
        if (appUri != null && appRefusal == null) return LaunchDecision.OpenApp(appUri)

        // A bad app link is not fatal on its own — the web address may still be fine — but if there
        // is no web address either, the app link's reason is the useful one to report.
        val web =
            target.webUrl?.takeIf { it.isNotBlank() }
                ?: return LaunchDecision.Unavailable(appRefusal ?: LaunchRefusal.NO_TARGET)
        return when (val refusal = refuse(web, allowCustomScheme = false)) {
            null -> LaunchDecision.OpenWeb(web)
            else -> LaunchDecision.Unavailable(refusal)
        }
    }

    /** The reason [address] must not be opened, or null when it is safe. */
    private fun refuse(
        address: String,
        allowCustomScheme: Boolean,
    ): LaunchRefusal? {
        val scheme = address.substringBefore(":", missingDelimiterValue = "").lowercase()
        if (scheme.isBlank()) return LaunchRefusal.UNSUPPORTED_SCHEME

        val schemeAllowed =
            scheme in WEB_SCHEMES ||
                // A provider's own registered scheme can only reach that provider's app,
                // and only when the app is installed. http is excluded deliberately, even though
                // browsers accept it: there is no reason to send a user anywhere in the clear.
                (allowCustomScheme && scheme !in FORBIDDEN_SCHEMES && scheme.all { it.isLetterOrDigit() || it in "+-." })
        if (!schemeAllowed) return LaunchRefusal.UNSUPPORTED_SCHEME

        val lowered = address.lowercase()
        if (STREAM_MARKERS.any { marker -> marker in lowered }) return LaunchRefusal.LOOKS_LIKE_A_STREAM
        if (CREDENTIAL_KEYS.any { key -> key in lowered }) return LaunchRefusal.CARRIES_CREDENTIALS
        // user:password@host, the other way a credential travels in a URL.
        if ("@" in address.substringAfter("//", "").substringBefore("/")) return LaunchRefusal.CARRIES_CREDENTIALS

        return null
    }

    /** Schemes that execute or read locally and can never be a title on a streaming service. */
    private val FORBIDDEN_SCHEMES =
        setOf("javascript", "data", "file", "jar", "vbscript", "about", "blob", "content", "rtsp", "rtmp", "udp", "srt")
}

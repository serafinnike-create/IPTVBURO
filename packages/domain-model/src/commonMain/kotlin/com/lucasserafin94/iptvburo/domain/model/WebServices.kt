package com.lucasserafin94.iptvburo.domain.model

/**
 * Services a user subscribes to separately and watches on the service's own website, opened inside
 * BURO in a browser view of its own.
 *
 * Not a source. Nothing here is imported, parsed or played by BURO's player: the service's page
 * plays its own episodes, the user signs in on that page with their own account, and the service
 * keeps its own history. BURO only decides who may see the entry point and which addresses may load
 * inside the view — which is all the service's terms allow, since they forbid automated access to
 * anything but the site itself.
 *
 * Unlike [ExternalContentLauncher], which hands a title over to the system browser, the page here
 * stays inside BURO. That is a deliberate difference: the user bought this service to watch it in
 * BURO, and an isolated browser view the user signs in to themselves reuses no session and automates
 * no login.
 */
enum class ViewerKind { ADULT, KIDS, GUEST }

/** What BURO can honestly do with a web service. The UI draws only what is listed. */
enum class WebServiceCapability {
    /** The service's own page opens inside BURO. */
    EMBEDDED_PAGE,

    /** The service keeps what was watched in the user's own account, on its side. */
    ACCOUNT_HISTORY,

    /** BURO can draw the service's titles in its own rails. Needs an official feed. */
    NATIVE_RAILS,

    /** BURO's own player can play the service's episodes. */
    NATIVE_PLAYBACK,

    /** Episodes can be kept for offline viewing. */
    OFFLINE_DOWNLOAD,
}

data class WebServiceBrand(
    /** Stable key, stored in preferences. Never rename one that has shipped. */
    val id: String,
    val displayName: String,
    /** Where the view opens. Always https. */
    val homeUrl: String,
    /** Hosts that load inside the view; their subdomains are included. */
    val allowedHosts: Set<String>,
    /** The age the service's own terms ask for. 0 when it asks for none. */
    val minimumAge: Int,
    val capabilities: Set<WebServiceCapability>,
    /**
     * Whether turning the service on asks "Tenho 18 anos ou mais" first.
     *
     * Separate from [minimumAge], which keeps the service away from Kids and Guest whatever this
     * says: the confirmation is a product choice the owner can make, the age in the service's own
     * terms is not.
     */
    val asksAgeConfirmation: Boolean = minimumAge > 0,
) {
    init {
        require(id.isNotBlank()) { "A web service needs an id" }
        require(homeUrl.startsWith("https://")) { "A web service opens over https only" }
        require(allowedHosts.isNotEmpty()) { "A web service needs at least one host" }
    }

    fun supports(capability: WebServiceCapability): Boolean = capability in capabilities

    /** The brand's name only, so a stray log line never carries anything else. */
    override fun toString(): String = "WebServiceBrand($id)"
}

object WebServices {
    /**
     * Short vertical drama series, watched on godorama.com.
     *
     * Embedded page and account history only. Its terms (godorama.com/termos) allow watching "pelo
     * site ou pelo app instalado a partir dele", forbid robots, scripts and automated access, and
     * forbid downloading — so there are no native rails, no native playback and no offline copies
     * until the service offers an official feed.
     *
     * Its terms (1 October 2026) say the service is for adults, so it stays off Kids and Guest
     * profiles. The owner reports the catalogue is general audience now and asked for no 18+
     * question, so an adult profile turns it on without one.
     */
    val GODORAMA =
        WebServiceBrand(
            id = "godorama",
            displayName = "GoDorama",
            homeUrl = "https://godorama.com/",
            allowedHosts = setOf("godorama.com"),
            minimumAge = 18,
            capabilities = setOf(WebServiceCapability.EMBEDDED_PAGE, WebServiceCapability.ACCOUNT_HISTORY),
            asksAgeConfirmation = false,
        )

    val all: List<WebServiceBrand> = listOf(GODORAMA)

    fun byId(id: String?): WebServiceBrand? = all.firstOrNull { brand -> brand.id == id }
}

/** What the browser view does with an address the page tries to open. */
sealed interface WebNavigation {
    /** One of the service's own pages: load it in the view. */
    data object StayInside : WebNavigation

    /** An ordinary https page somewhere else: hand it to the system browser. */
    data class OpenOutside(val url: String) : WebNavigation

    /** Anything else — another scheme, credentials in the address. Not opened anywhere. */
    data object Block : WebNavigation
}

object WebServicePolicy {
    /**
     * Whether [viewer] may see [brand] at all — in Sources, on Home, anywhere.
     *
     * A service whose own terms ask for an age is for the adult profile only. Guest is left out as
     * well as Kids: a guest is whoever picked up the remote, and nobody vouched for their age.
     */
    fun isOfferedTo(
        brand: WebServiceBrand,
        viewer: ViewerKind,
    ): Boolean = brand.minimumAge == 0 || viewer == ViewerKind.ADULT

    /**
     * The services to draw for this viewer, in catalogue order.
     *
     * Filtered here and not only in the screen, so a profile switched to Kids after a service was
     * turned on loses it everywhere at once, while the stored choice survives for when it changes
     * back.
     */
    fun visibleServices(
        enabledIds: Set<String>,
        viewer: ViewerKind,
    ): List<WebServiceBrand> =
        WebServices.all.filter { brand -> brand.id in enabledIds && isOfferedTo(brand, viewer) }

    /** Decides a top-level navigation. Sub-resources (images, video) are the page's business. */
    fun navigation(
        brand: WebServiceBrand,
        url: String,
    ): WebNavigation {
        val address = parse(url) ?: return WebNavigation.Block
        if (address.scheme != "https" || address.hasUserInfo) return WebNavigation.Block
        val inside = brand.allowedHosts.any { host -> address.host == host || address.host.endsWith(".$host") }
        return if (inside) WebNavigation.StayInside else WebNavigation.OpenOutside(url)
    }

    /**
     * The part of an address that is safe to log: scheme and host.
     *
     * The service's addresses carry signed, expiring parameters, and its pages carry the user's
     * session — neither belongs in a log.
     */
    fun loggable(url: String): String = parse(url)?.let { "${it.scheme}://${it.host}" } ?: "<unparsable>"

    private class Address(val scheme: String, val host: String, val hasUserInfo: Boolean)

    private fun parse(url: String): Address? {
        val trimmed = url.trim()
        val schemeEnd = trimmed.indexOf("://")
        if (schemeEnd <= 0) return null
        val scheme = trimmed.substring(0, schemeEnd).lowercase()
        val rest = trimmed.substring(schemeEnd + 3)
        val authority = rest.takeWhile { it != '/' && it != '?' && it != '#' }
        val hasUserInfo = '@' in authority
        val hostAndPort = authority.substringAfterLast('@')
        val host = hostAndPort.substringBefore(':').lowercase().trimEnd('.')
        if (host.isBlank()) return null
        return Address(scheme, host, hasUserInfo)
    }
}

package com.lucasserafin94.iptvburo.desktop.license

/**
 * Where the licence server lives, and where customers are sent to buy.
 *
 * ## Changing the domain
 *
 * Every address the product uses is derived from [DOMAIN]. Moving from the free
 * `iptvburo.workers.dev` to a purchased domain is this one line plus a rebuild — the purchase page,
 * the QR code and the validation endpoint all follow.
 *
 * Two things must be done together when it changes:
 *
 * 1. this constant, and
 * 2. the Cloudflare Worker's route, so the old address stops answering.
 *
 * Leaving the old address serving is worse than it sounds: builds already installed keep talking to
 * it, so it has to answer for as long as any of them are in use. Plan a domain change as "the old
 * one redirects for a year", not as a switch.
 *
 * ## Why a purchased domain matters commercially
 *
 * `iptvburo.workers.dev` reads as a developer address, and it is the address printed on the screen
 * that asks someone for money. The comparable products in this market — iboplayer.pro and its
 * peers — all use a short bought domain, and a customer deciding whether to enter card details
 * notices the difference. The free address works and costs nothing; it costs conversions instead.
 */
object LicenseEndpoints {
    /**
     * The one value to change.
     *
     * Currently the free Cloudflare address. Replace with the purchased domain — `iptvburo.app`,
     * `iptvburo.com`, whichever — and everything below follows.
     */
    const val DOMAIN = "iptvburo.iptvburo.workers.dev"

    /** Asks the server what this device is entitled to. Called at every launch. */
    const val VALIDATE = "https://$DOMAIN/v1/validate"

    /** Registers a device the server has never seen, which is what starts a trial. */
    const val REGISTER = "https://$DOMAIN/v1/register"

    /** Redeems a key issued by hand — the "a friend asked to try it" path. */
    const val REDEEM = "https://$DOMAIN/v1/redeem"

    /**
     * Describes a key without spending it, so the screen can say what it is before redeeming.
     *
     * Separate from [REDEEM] because looking must never be a side effect of asking: a customer
     * checking whether they typed the right code should not consume the key by doing so.
     */
    const val KEY_INFO = "https://$DOMAIN/v1/key-info"

    /**
     * What this machine would actually be charged.
     *
     * Asked rather than worked out locally. The app once decided currency from the operating
     * system's locale, which follows where somebody is *from*; the charge follows where the request
     * comes *from*. A Brazilian Windows in Portugal showed R$99,90 in the app and €9,90 on the
     * payment page — the price changing at the moment of clicking, which is where trust is cheapest
     * to lose.
     */
    const val PRICE = "https://$DOMAIN/v1/price"

    /**
     * The page a locked customer is sent to, with their device already filled in.
     *
     * The device id travels in the URL so the purchase page does not have to ask them to type it:
     * a fourteen-character code typed by hand is a support ticket waiting to happen, and the whole
     * point of the QR code is that a phone can open this without any typing at all.
     */
    fun purchaseUrl(deviceId: String, language: String? = null): String =
        buildString {
            append("https://$DOMAIN/comprar?device=$deviceId")
            // The app's chosen language, not the browser's.
            //
            // Someone running the app in English on a Portuguese Windows would otherwise land on a
            // Portuguese payment page, because the browser announces the system language. The app
            // knows what the customer actually chose, and hesitation at the payment step is the
            // expensive kind.
            //
            // A phone that scans the QR code sends its own browser's language, which is why this
            // has to travel in the URL rather than in a header.
            if (language != null) append("&lang=$language")
        }

    /**
     * The server's public signing key.
     *
     * Pinned in the binary rather than fetched, which is the point: a client that downloads the key
     * it will verify against can be pointed at any server at all — redirect the domain, serve your
     * own key, issue your own licences. With the key compiled in, redirection achieves nothing
     * because the forged answers do not verify.
     *
     * This is the public half and belongs in the binary: it verifies signatures and cannot produce
     * them. The private half lives only as a Worker secret. If this value ever needs replacing, the
     * old one must keep verifying until installed builds have updated, so treat a key rotation the
     * same way as a domain change — an overlap, not a switch.
     */
    const val SERVER_PUBLIC_KEY = "MCowBQYDK2VwAyEAXm01dKxc4kXNYaSYnVL0isza1EnYn+nYjyfNhnWoILw="

    /** Whether this build has a key to verify against at all. */
    val isConfigured: Boolean
        get() = SERVER_PUBLIC_KEY.isNotBlank()
}

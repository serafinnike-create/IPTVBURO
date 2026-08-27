package com.lucasserafin94.iptvburo.desktop.license

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.security.SecureRandom
import java.time.Duration
import java.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * A connection an operator set up for this machine, waiting to be applied.
 *
 * Held in memory and never written anywhere by this class. The password reaches disk only where
 * every other provider password already does — the app's own credential store — and only once the
 * viewer has accepted it.
 */
class ProvisionedSource(
    /**
     * The connection, or null when the seller sent none.
     *
     * Null is a real case: a seller whose customer already has a working list may send only a
     * TMDb key, or only a new name for it. Reported as "nao deixa eu enviar so api tmdb preciso
     * enviar tudo". The three travel together or not at all — half a credential never connects.
     */
    val server: CharArray?,
    val username: CharArray?,
    val password: CharArray?,
    /**
     * A TMDb key the seller set up as well, or null when they sent none.
     *
     * Null means "leave alone", never "clear": a seller replacing a provider address that went
     * down sends the three connection fields and nothing else, and that must not wipe a key the
     * viewer configured themselves. The server omits the field rather than sending null for the
     * same reason.
     */
    val metadataKey: CharArray? = null,
    /** An OMDb key, on the same terms. */
    val criticsKey: CharArray? = null,
    /**
     * What to call this list, or null when the seller named none.
     *
     * Not a credential, so it is a plain String: it is drawn on screen, which is the whole point
     * of it. Null falls back to naming the list after its host, which is what the app did before
     * a seller could choose — and that reads as the server's own name rather than the one their
     * customer was sold.
     */
    val listLabel: String? = null,
) {
    /**
     * Wipes the credentials.
     *
     * The reason these are char arrays and not strings: a String cannot be cleared, so a password
     * put in one stays in the heap until the collector happens to reclaim it — and until then it
     * is in any crash dump the machine writes. The store this is handed to takes char arrays for
     * the same reason, so nothing here converts.
     */
    fun clear() {
        server?.fill('\u0000')
        username?.fill('\u0000')
        password?.fill('\u0000')
        metadataKey?.fill('\u0000')
        criticsKey?.fill('\u0000')
    }

    /** Never the credentials. This type ends up in a log by accident, which is the point. */
    override fun toString(): String = "ProvisionedSource(server=<redacted>, username=<redacted>)"
}

/**
 * Collects a connection a reseller applied for this machine.
 *
 * The situation this serves: someone buys a list and cannot set it up — an address, a username and
 * a password, typed by someone who did not choose to become a system administrator. They read the
 * code the app shows on screen and send it to whoever sold them the list, who fills in a form on
 * their own panel. The connection arrives here the next time the app opens.
 *
 * **Reading out the code is the consent.** Nothing can be sent to a machine whose owner has not
 * read its code off their own screen and passed it on. Someone using this app with a different
 * operator is never reached, and no pairing screen is needed to guarantee that.
 *
 * Almost every call returns nothing at all. That is the ordinary case — every launch of every
 * machine that no operator has ever configured — so it must be cheap and completely silent.
 */
class ProvisioningClient(
    private val identityProvider: DeviceIdentityProvider,
    private val claimUrl: String = LicenseEndpoints.PROVISIONING_CLAIM,
    private val confirmUrl: String = LicenseEndpoints.PROVISIONING_CONFIRM,
    private val http: OkHttpClient =
        OkHttpClient.Builder()
            // Short. This runs at startup and must never be why the app is slow to open: a server
            // that does not answer promptly means no configuration this launch, not a wait.
            .connectTimeout(Duration.ofSeconds(5))
            .readTimeout(Duration.ofSeconds(10))
            .build(),
) {
    /**
     * What is waiting for this machine, or null.
     *
     * Null covers everything: nothing waiting, no network, a server that refused, an answer that
     * did not parse. None of them is worth a message — the viewer did not ask for anything, and an
     * error about a feature they have never heard of would only be alarming.
     */
    fun claim(): ProvisionedSource? {
        val answer = post(claimUrl) ?: return null
        val source = answer.getAsJsonObject("source") ?: return null
        val server = source.string("server")
        val username = source.string("username")
        val password = source.string("password")

        // All three or none. A delivery may legitimately carry only a key or only a name, for a
        // customer whose list already works; but an address with no password is not a partial
        // delivery, it is a list that will never open.
        val credentials =
            when {
                server != null && username != null && password != null -> true
                server == null && username == null && password == null -> false
                else -> return null
            }

        if (credentials) {
            // Checked here as well as on the server. This value becomes a request the app makes
            // with the viewer's credentials attached, and the server that vouched for it is not
            // the one this app answers to.
            if (!server!!.startsWith("http://", ignoreCase = true) &&
                !server.startsWith("https://", ignoreCase = true)
            ) {
                return null
            }
            // Credentials embedded in the address would be a second copy of the password
            // travelling somewhere it would be logged.
            if (CREDENTIALS_IN_URL.containsMatchIn(server)) return null
        }

        val metadataKey = source.string("metadataKey")?.takeIf(::looksLikeApiKey)
        val criticsKey = source.string("criticsKey")?.takeIf(::looksLikeApiKey)
        val listLabel = source.string("listLabel")?.take(MAX_LIST_LABEL)

        // Something has to have arrived. An answer carrying an empty source is not a delivery, and
        // building one from it would have the app confirm and erase a delivery it never applied.
        if (!credentials && metadataKey == null && criticsKey == null && listLabel == null) {
            return null
        }

        return ProvisionedSource(
            server = server?.toCharArray(),
            username = username?.toCharArray(),
            password = password?.toCharArray(),
            // Checked above. These go into a URL the app builds, so anything outside the
            // alphabet these keys use is a mis-paste rather than a key. Trimmed and capped because
            // the name is drawn in a row of its own.
            metadataKey = metadataKey?.toCharArray(),
            criticsKey = criticsKey?.toCharArray(),
            listLabel = listLabel,
        )
    }

    /**
     * Reports that the connection was applied, so the server can erase it.
     *
     * Called only after the source is actually saved. Confirming at the moment of delivery would
     * mean a customer whose app closed in between is left with no list and nothing waiting for
     * them — and no way to ask for it again short of telephoning the seller.
     */
    fun confirmApplied() {
        post(confirmUrl)
    }

    /**
     * Reports that the connection could not be applied, so the panel can show why.
     *
     * The seller is the one who can act on this: a wrong password typed into their form is
     * invisible to them otherwise, and their customer can only report that "it did not work".
     */
    fun reportFailure(errorCode: String) {
        post(confirmUrl) { body -> body.addProperty("errorCode", errorCode.take(MAX_ERROR_CODE)) }
    }

    private fun post(
        url: String,
        extra: (JsonObject) -> Unit = {},
    ): JsonObject? {
        val identity = runCatching { identityProvider.getOrCreate() }.getOrNull() ?: return null
        val nonce = freshNonce()
        val body =
            JsonObject().apply {
                addProperty("deviceId", identity.deviceId)
                addProperty("nonce", nonce)
                // Bound to this action, so a proof captured from a launch check cannot be replayed
                // to fetch someone's provider credentials.
                addProperty("proof", identity.proof(DeviceProofAction.PROVISIONING, nonce))
                addProperty("installationId", identity.installationId)
                addProperty("publicKey", identity.publicKeyDerBase64)
                extra(this)
            }

        return runCatching {
            http
                .newCall(
                    Request.Builder().url(url).post(body.toString().toRequestBody(JSON)).build(),
                ).execute()
                .use { response ->
                    // 204 is the common answer and means nothing is waiting.
                    if (!response.isSuccessful || response.code == NO_CONTENT) return@use null
                    JsonParser.parseString(response.body.string()).asJsonObject
                }
        }.getOrNull()
    }

    /**
     * Whether a value is shaped like an API key at all.
     *
     * A key with a space or a quote in it is a mis-paste, and putting it in a request URL would
     * produce a malformed address rather than a failed lookup. Long enough for TMDb's v4 token,
     * which is 239 characters.
     */
    private fun looksLikeApiKey(value: String): Boolean =
        value.length <= MAX_API_KEY_LENGTH && API_KEY.matches(value)

    private fun JsonObject.string(name: String): String? =
        runCatching { get(name)?.takeIf { it.isJsonPrimitive }?.asString }
            .getOrNull()
            ?.trim()
            ?.takeIf(String::isNotEmpty)

    private fun freshNonce(): String {
        val bytes = ByteArray(NONCE_BYTES)
        RANDOM.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /** Never the endpoints or the identity. */
    override fun toString(): String = "ProvisioningClient()"

    private companion object {
        const val NONCE_BYTES = 16
        const val NO_CONTENT = 204
        const val MAX_ERROR_CODE = 64
        val JSON = "application/json; charset=utf-8".toMediaType()
        val RANDOM = SecureRandom()

        const val MAX_API_KEY_LENGTH = 400
        const val MAX_LIST_LABEL = 60
        val API_KEY = Regex("^[A-Za-z0-9._-]+$")

        /** `scheme://user:pass@host` — a password in the address. */
        val CREDENTIALS_IN_URL = Regex("""^https?://[^/\s]*@""", RegexOption.IGNORE_CASE)
    }
}

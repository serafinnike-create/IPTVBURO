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
data class ProvisionedSource(
    val server: String,
    val username: String,
    val password: String,
) {
    /** Never the credentials. This type ends up in logs by accident, which is the point of the rule. */
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
        val server = source.string("server") ?: return null
        val username = source.string("username") ?: return null
        val password = source.string("password") ?: return null

        // Checked here as well as on the server. This value becomes a request the app makes with
        // the viewer's credentials attached, and the server that vouched for it is not the one
        // this app answers to.
        if (!server.startsWith("http://", ignoreCase = true) &&
            !server.startsWith("https://", ignoreCase = true)
        ) {
            return null
        }
        // Credentials embedded in the address would be a second copy of the password travelling
        // somewhere it would be logged.
        if (CREDENTIALS_IN_URL.containsMatchIn(server)) return null

        return ProvisionedSource(server = server, username = username, password = password)
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

        /** `scheme://user:pass@host` — a password in the address. */
        val CREDENTIALS_IN_URL = Regex("""^https?://[^/\s]*@""", RegexOption.IGNORE_CASE)
    }
}

package com.lucasserafin94.iptvburo.sync

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.security.SecureRandom
import java.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Signs sync requests with the device's own key.
 *
 * Supplied by each platform, because the key lives in each platform's own vault — Android Keystore,
 * Windows DPAPI — and never leaves it. The action signed is always `sync`, so a proof taken from a
 * sync call cannot be replayed to validate a licence or claim a playlist, and vice versa.
 */
interface SyncProver {
    val deviceId: String

    /** A signature over `iptvburo-device-proof-v1\nsync\n<deviceId>\n<nonce>`. */
    fun proof(nonce: String): String
}

/** Why a sync call did not succeed, in terms a screen can act on. */
enum class SyncFailure {
    /** The device has no licence in force. Sync is part of what the licence buys. */
    LICENCE_REQUIRED,

    /** This device is not in a household yet. */
    NOT_IN_GROUP,

    /** The invite code is wrong or has expired. */
    UNKNOWN_CODE,

    /** The household already has its maximum number of devices. */
    GROUP_FULL,

    /** No connection, or the server could not be reached. Worth trying again later. */
    NETWORK,

    /** The server refused the request for any other reason. */
    REJECTED,
}

sealed interface SyncResult<out T> {
    data class Success<T>(val value: T) : SyncResult<T>

    data class Failure(val reason: SyncFailure) : SyncResult<Nothing>
}

data class SyncStatus(val inGroup: Boolean, val members: Int, val licensed: Boolean)

data class SyncInvite(val code: String, val expiresAtIso: String)

data class SyncPage(val revision: Long, val hasMore: Boolean, val records: List<SyncRecord>)

/** The licence server's sync API. Blocking; call it off the main thread. */
class SyncClient(
    private val http: OkHttpClient,
    private val baseUrl: String,
    private val prover: SyncProver,
) {
    fun status(): SyncResult<SyncStatus> =
        call("status") { json ->
            SyncStatus(
                inGroup = json.get("inGroup")?.asBoolean == true,
                members = json.get("members")?.asInt ?: 0,
                licensed = json.get("licensed")?.asBoolean == true,
            )
        }

    fun invite(): SyncResult<SyncInvite> =
        call("invite") { json -> SyncInvite(json.get("code").asString, json.get("expiresAt").asString) }

    fun join(code: String): SyncResult<Int> =
        call("join", { addProperty("code", code) }) { json -> json.get("members")?.asInt ?: 0 }

    fun leave(): SyncResult<Unit> = call("leave") { }

    fun exchange(since: Long, records: List<SyncRecord>): SyncResult<SyncPage> =
        call(
            "exchange",
            {
                addProperty("since", since)
                add(
                    "records",
                    JsonArray().apply {
                        records.forEach { record ->
                            add(
                                JsonObject().apply {
                                    addProperty("key", record.key)
                                    addProperty("updatedAt", record.updatedAtMillis)
                                    addProperty("deleted", record.deleted)
                                    addProperty("data", record.data)
                                },
                            )
                        }
                    },
                )
            },
        ) { json ->
            SyncPage(
                revision = json.get("revision")?.asLong ?: since,
                hasMore = json.get("hasMore")?.asBoolean == true,
                records =
                    json.getAsJsonArray("records")?.mapNotNull { element ->
                        runCatching {
                            val item = element.asJsonObject
                            SyncRecord(
                                key = item.get("key").asString,
                                updatedAtMillis = item.get("updatedAt").asLong,
                                deleted = item.get("deleted").asBoolean,
                                data = item.get("data")?.takeUnless { it.isJsonNull }?.asString.orEmpty(),
                            )
                        }.getOrNull()
                    }.orEmpty(),
            )
        }

    private fun <T> call(
        action: String,
        fill: JsonObject.() -> Unit = {},
        parse: (JsonObject) -> T,
    ): SyncResult<T> {
        val nonce = freshNonce()
        val body =
            runCatching {
                JsonObject().apply {
                    addProperty("deviceId", prover.deviceId)
                    addProperty("nonce", nonce)
                    addProperty("proof", prover.proof(nonce))
                    fill()
                }
            }.getOrElse { return SyncResult.Failure(SyncFailure.REJECTED) }
        val request =
            Request.Builder()
                .url("${baseUrl.trimEnd('/')}/v1/sync/$action")
                .post(body.toString().toRequestBody(JSON))
                .build()
        return try {
            http.newCall(request).execute().use { response ->
                val text = response.body.string()
                val json = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull()
                if (response.isSuccessful && json != null) {
                    runCatching { SyncResult.Success(parse(json)) }
                        .getOrElse { SyncResult.Failure(SyncFailure.REJECTED) }
                } else {
                    SyncResult.Failure(failureFor(response.code, json?.get("error")?.takeUnless { it.isJsonNull }?.asString))
                }
            }
        } catch (_: java.io.IOException) {
            SyncResult.Failure(SyncFailure.NETWORK)
        }
    }

    private fun failureFor(status: Int, error: String?): SyncFailure =
        when (error) {
            "licence_required" -> SyncFailure.LICENCE_REQUIRED
            "not_in_group" -> SyncFailure.NOT_IN_GROUP
            "unknown_code", "bad_code" -> SyncFailure.UNKNOWN_CODE
            "group_full" -> SyncFailure.GROUP_FULL
            else -> if (status == 429 || status >= 500) SyncFailure.NETWORK else SyncFailure.REJECTED
        }

    private companion object {
        val JSON = "application/json".toMediaType()
        val random = SecureRandom()

        /** 16 random bytes as 22 base64url characters, the shape the server accepts. */
        fun freshNonce(): String {
            val bytes = ByteArray(16).also(random::nextBytes)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }
    }
}

package com.lucasserafin94.iptvburo.stalker

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Client for Stalker / Ministra portals, the middleware behind "MAC + portal" IPTV subscriptions.
 *
 * The protocol differs from Xtream in one way that shapes this whole class: the credential is the
 * **device MAC**, sent as a cookie, and every call needs a short-lived bearer token obtained from a
 * handshake. The portal also expects the request to look like a MAG set-top box; portals routinely
 * reject a generic HTTP client, so the STB `User-Agent` and `Referer` below are load-bearing, not
 * cosmetic.
 *
 * Nothing here writes the portal URL, MAC or token to logs or exception messages.
 */
class StalkerClient(
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val timeZone: String = "Europe/London",
) {
    /**
     * Performs the handshake and returns a session token.
     *
     * Portals disagree about where the endpoint lives, so both known layouts are tried before
     * giving up. Guessing wrong here surfaces to the user as "wrong MAC", which is why the fallback
     * exists rather than a single hardcoded path.
     */
    fun handshake(credentials: StalkerCredentials): StalkerSession {
        val mac = requireMac(credentials.macAddress)
        var lastFailure: StalkerClientException? = null

        for (base in candidateBases(credentials.portalUrl)) {
            val url =
                base.newBuilder()
                    .addQueryParameter("type", "stb")
                    .addQueryParameter("action", "handshake")
                    .addQueryParameter("token", "")
                    .addQueryParameter("JsHttpRequest", "1-xml")
                    .build()
            val result =
                runCatching { requestJson(url, credentials.portalUrl, mac, token = null) }
                    .getOrElse { error ->
                        lastFailure = error as? StalkerClientException
                            ?: StalkerClientException(StalkerFailureReason.NETWORK, "handshake failed", error)
                        continue
                    }
            val token =
                result.getAsJsonObject("js")?.get("token")?.takeIf { !it.isJsonNull }?.asString
            if (!token.isNullOrBlank()) {
                return StalkerSession(
                    token = token,
                    expiresAtEpochMillis = System.currentTimeMillis() + TOKEN_LIFETIME_MILLIS,
                )
            }
            lastFailure =
                StalkerClientException(
                    StalkerFailureReason.UNAUTHORISED,
                    "portal returned no token",
                )
        }
        throw lastFailure
            ?: StalkerClientException(StalkerFailureReason.NETWORK, "portal unreachable")
    }

    /** Reads subscription state. Also the cheapest way to confirm the token still works. */
    fun account(
        credentials: StalkerCredentials,
        session: StalkerSession,
    ): StalkerAccount {
        val js = call(credentials, session, "stb", "get_main_info").getAsJsonObject("js")
            ?: throw StalkerClientException(StalkerFailureReason.MALFORMED, "missing js")
        val blocked = js.optString("blocked")?.let { it == "1" || it.equals("true", true) } ?: false
        return StalkerAccount(
            authenticated = true,
            expiryDate = js.optString("phone") ?: js.optString("end_date"),
            tariffPlan = js.optString("tariff_plan"),
            blocked = blocked,
        )
    }

    fun categories(
        credentials: StalkerCredentials,
        session: StalkerSession,
        contentType: StalkerContentType,
    ): List<StalkerCategory> {
        val action = if (contentType == StalkerContentType.LIVE) "get_genres" else "get_categories"
        val payload = call(credentials, session, contentType.portalType(), action)
        val array = payload.get("js")?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
        return array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val id = obj.optString("id") ?: return@mapNotNull null
            val title = obj.optString("title") ?: obj.optString("name") ?: return@mapNotNull null
            // Portals include an "All" pseudo-category; it duplicates the unfiltered list.
            if (id == "*" || id.isBlank()) return@mapNotNull null
            StalkerCategory(providerId = id, name = title, contentType = contentType)
        }
    }

    /** One page of a category. [page] is 1-based, matching the portal. */
    fun page(
        credentials: StalkerCredentials,
        session: StalkerSession,
        contentType: StalkerContentType,
        categoryId: String?,
        page: Int,
    ): StalkerPage {
        val paramKey = if (contentType == StalkerContentType.LIVE) "genre" else "category"
        val payload =
            call(
                credentials = credentials,
                session = session,
                type = contentType.portalType(),
                action = "get_ordered_list",
                extraParams =
                    buildMap {
                        put(paramKey, categoryId ?: "*")
                        put("p", page.coerceAtLeast(1).toString())
                        put("sortby", "added")
                    },
            )
        val js = payload.getAsJsonObject("js") ?: return StalkerPage(emptyList(), 0)
        val data = js.get("data")?.takeIf { it.isJsonArray }?.asJsonArray ?: return StalkerPage(emptyList(), 0)
        val total = js.optString("total_items")?.toIntOrNull() ?: data.size()
        val items =
            data.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val id = obj.optString("id") ?: return@mapNotNull null
                val name = obj.optString("name") ?: obj.optString("o_name") ?: return@mapNotNull null
                StalkerCatalogItem(
                    providerId = id,
                    name = name,
                    contentType = contentType,
                    categoryId = obj.optString("category_id") ?: obj.optString("genre_id"),
                    artworkUrl = obj.optString("screenshot_uri") ?: obj.optString("cover"),
                    year = obj.optString("year")?.take(4)?.toIntOrNull(),
                    rating = obj.optString("rating_imdb")?.toDoubleOrNull(),
                    command = obj.optString("cmd"),
                )
            }
        return StalkerPage(items = items, totalItems = total)
    }

    /**
     * Resolves a playable URL for [item].
     *
     * The portal answers with a command string that usually carries an `ffmpeg ` prefix and
     * sometimes extra arguments; the real URL is the first `http` token. Handing the raw command
     * to a player would fail, so it is unwrapped here.
     *
     * The returned URL embeds a single-use play token and must not be cached.
     */
    fun resolvePlaybackUrl(
        credentials: StalkerCredentials,
        session: StalkerSession,
        item: StalkerCatalogItem,
    ): String {
        val command = item.command
            ?: throw StalkerClientException(StalkerFailureReason.MALFORMED, "item has no command")
        val payload =
            call(
                credentials = credentials,
                session = session,
                type = item.contentType.portalType(),
                action = "create_link",
                extraParams = mapOf("cmd" to command, "forced_storage" to "0", "disable_ad" to "0"),
            )
        val resolved =
            payload.getAsJsonObject("js")?.optString("cmd")
                ?: throw StalkerClientException(StalkerFailureReason.MALFORMED, "no playback command")
        return extractUrl(resolved)
            ?: throw StalkerClientException(StalkerFailureReason.MALFORMED, "command has no url")
    }

    // -----------------------------------------------------------------------------------------

    private fun call(
        credentials: StalkerCredentials,
        session: StalkerSession,
        type: String,
        action: String,
        extraParams: Map<String, String> = emptyMap(),
    ): JsonObject {
        val mac = requireMac(credentials.macAddress)
        var lastFailure: StalkerClientException? = null
        for (base in candidateBases(credentials.portalUrl)) {
            val builder =
                base.newBuilder()
                    .addQueryParameter("type", type)
                    .addQueryParameter("action", action)
            extraParams.forEach { (key, value) -> builder.addQueryParameter(key, value) }
            builder.addQueryParameter("JsHttpRequest", "1-xml")
            val result =
                runCatching { requestJson(builder.build(), credentials.portalUrl, mac, session.token) }
                    .getOrElse { error ->
                        lastFailure = error as? StalkerClientException
                            ?: StalkerClientException(StalkerFailureReason.NETWORK, "request failed", error)
                        continue
                    }
            return result
        }
        throw lastFailure ?: StalkerClientException(StalkerFailureReason.NETWORK, "portal unreachable")
    }

    private fun requestJson(
        url: HttpUrl,
        portalUrl: String,
        mac: String,
        token: String?,
    ): JsonObject {
        val encodedMac = URLEncoder.encode(mac, StandardCharsets.UTF_8)
        val encodedZone = URLEncoder.encode(timeZone, StandardCharsets.UTF_8)
        val cookie =
            buildString {
                append("mac=").append(encodedMac)
                append("; stb_lang=en; timezone=").append(encodedZone)
                if (!token.isNullOrBlank()) append("; token=").append(token)
            }
        val request =
            Request.Builder()
                .url(url)
                .header("User-Agent", STB_USER_AGENT)
                .header("X-User-Agent", "Model: MAG250; Link: WiFi")
                .header("Referer", referer(portalUrl))
                .header("Accept", "*/*")
                .header("Cookie", cookie)
                .apply { if (!token.isNullOrBlank()) header("Authorization", "Bearer $token") }
                .build()

        val body =
            try {
                httpClient.newCall(request).execute().use { response ->
                    when {
                        response.code == 401 || response.code == 403 ->
                            throw StalkerClientException(
                                StalkerFailureReason.UNAUTHORISED,
                                "portal rejected the device",
                            )
                        !response.isSuccessful ->
                            throw StalkerClientException(
                                StalkerFailureReason.NETWORK,
                                "portal returned ${response.code}",
                            )
                        else -> response.body?.string().orEmpty()
                    }
                }
            } catch (io: IOException) {
                // The message can contain the portal host, so it is not propagated.
                throw StalkerClientException(StalkerFailureReason.NETWORK, "portal unreachable")
            }

        return runCatching { JsonParser.parseString(body).asJsonObject }
            .getOrElse {
                throw StalkerClientException(StalkerFailureReason.MALFORMED, "portal returned non-JSON")
            }
    }

    /**
     * Endpoint layouts seen in the wild.
     *
     * `/portal.php` is the modern one and `/server/load.php` the legacy Stalker path; a portal
     * answers on exactly one of them.
     */
    private fun candidateBases(portalUrl: String): List<HttpUrl> {
        val root = portalUrl.trim().trimEnd('/')
        val normalised = if (root.startsWith("http", ignoreCase = true)) root else "http://$root"
        val parsed = normalised.toHttpUrlOrNull()
            ?: throw StalkerClientException(StalkerFailureReason.MALFORMED, "invalid portal address")

        // A pasted URL may already point at the endpoint; honour it before guessing.
        val explicit = parsed.takeIf { it.encodedPath.endsWith(".php") }
        val origin = parsed.newBuilder().encodedPath("/").build()
        return listOfNotNull(
            explicit,
            origin.resolve("portal.php"),
            origin.resolve("stalker_portal/server/load.php"),
            origin.resolve("server/load.php"),
        ).distinct()
    }

    private fun referer(portalUrl: String): String {
        val root = portalUrl.trim().trimEnd('/')
        val normalised = if (root.startsWith("http", ignoreCase = true)) root else "http://$root"
        val parsed = normalised.toHttpUrlOrNull() ?: return normalised
        return parsed.newBuilder().encodedPath("/c/").build().toString()
    }

    private fun requireMac(raw: String): String =
        StalkerMacAddress.normalise(raw)
            ?: throw StalkerClientException(StalkerFailureReason.UNAUTHORISED, "invalid MAC address")

    internal companion object {
        /**
         * Portals fingerprint the client and reject anything that is not a set-top box, so this
         * string is required for the request to succeed at all.
         */
        const val STB_USER_AGENT =
            "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) " +
                "MAG200 stbapp ver: 2 rev: 250 Safari/533.3"

        /** Portals do not advertise a lifetime; ten minutes keeps a session fresh cheaply. */
        const val TOKEN_LIFETIME_MILLIS = 10 * 60 * 1_000L

        /** Pulls the stream URL out of `ffmpeg http://host/path extra args`. */
        fun extractUrl(command: String): String? =
            command
                .split(' ')
                .firstOrNull { token -> token.startsWith("http://") || token.startsWith("https://") }
    }
}

private fun StalkerContentType.portalType(): String =
    when (this) {
        StalkerContentType.LIVE -> "itv"
        StalkerContentType.MOVIE -> "vod"
        StalkerContentType.SERIES -> "series"
    }

/** Reads a field as a string regardless of whether the portal encoded it as a number. */
private fun JsonObject.optString(name: String): String? =
    get(name)
        ?.takeIf { !it.isJsonNull && it.isJsonPrimitive }
        ?.asString
        ?.takeIf { it.isNotBlank() }

package com.lucasserafin94.iptvburo.webdav

import java.time.Duration
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * A WebDAV share, as this app needs to read it.
 *
 * WebDAV is HTTP with a handful of extra verbs; the only one needed to browse a share is
 * `PROPFIND`. That is why this package has no WebDAV library behind it — OkHttp already speaks the
 * protocol, and a dependency would buy locking, versioning and property writes that a media player
 * has no use for.
 *
 * ## Credentials
 *
 * The username and password live in [WebDavCredentials] and are attached per request as a Basic
 * header. They are never written into a URL, never returned in an entry, and never printed: an
 * href travels as a path, and the address that can actually fetch a file is assembled at the last
 * moment inside [downloadUrl].
 *
 * That matters because Real-Debrid and TorBox — the reason most people want this — hand out
 * WebDAV credentials that are as good as the account itself.
 */
class WebDavClient(
    private val credentials: WebDavCredentials,
    private val client: OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(15))
            .readTimeout(Duration.ofSeconds(30))
            .build(),
) {
    /**
     * Whether the share answers and the credentials are accepted.
     *
     * Returns a reason rather than a boolean: "wrong password" and "server unreachable" send
     * somebody looking in completely different places, and a single "could not connect" has them
     * checking their typing when their network is down.
     */
    fun probe(): WebDavProbe {
        val base = credentials.baseUrl.toHttpUrlOrNull() ?: return WebDavProbe.InvalidAddress
        return runCatching {
            client.newCall(propfindRequest(base, depth = 0)).execute().use { response ->
                when {
                    response.code == 401 || response.code == 403 -> WebDavProbe.Rejected
                    // 207 is the WebDAV success code. A 200 means the host answered but is not
                    // speaking WebDAV — usually an ordinary web page at that address.
                    response.code == 207 -> WebDavProbe.Reachable
                    response.isSuccessful -> WebDavProbe.NotWebDav
                    else -> WebDavProbe.Unreachable
                }
            }
        }.getOrDefault(WebDavProbe.Unreachable)
    }

    /**
     * What is inside [path], or an empty list when it cannot be read.
     *
     * `Depth: 1` lists the folder's own children and nothing deeper. Asking for `infinity` is what
     * a naive client does, and on a share holding a film library it asks the server to walk tens of
     * thousands of files to answer one request — many servers refuse it outright.
     */
    fun list(path: String = "/"): List<WebDavEntry> {
        val url = resolve(path) ?: return emptyList()
        return runCatching {
            client.newCall(propfindRequest(url, depth = 1)).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList()
                val body = response.body?.string().orEmpty()
                WebDavListing.parse(body, requestedPath = url.encodedPath)
            }
        }.getOrDefault(emptyList())
    }

    /**
     * The address a player can fetch [href] from, credentials included.
     *
     * The one place a credential-bearing URL is produced, and it is produced on demand rather than
     * stored: this is the late resolution the rest of the app applies to playback addresses, for
     * the same reason — a URL that is kept is a URL that ends up in a queue, a log or a history row.
     */
    fun downloadUrl(href: String): String? = resolve(href)?.let { url -> credentials.authorise(url) }

    private fun resolve(path: String): HttpUrl? {
        val base = credentials.baseUrl.toHttpUrlOrNull() ?: return null
        // An href from a listing is server-absolute ("/media/a.mkv"); a path typed by hand may be
        // relative. `resolve` handles both, and refuses anything that would leave the host.
        return base.resolve(path)?.takeIf { candidate -> candidate.host == base.host }
    }

    private fun propfindRequest(url: HttpUrl, depth: Int): Request =
        Request.Builder()
            .url(url)
            .method("PROPFIND", PROPFIND_BODY.toRequestBody(XML_MEDIA_TYPE))
            .header("Depth", depth.toString())
            .header("Authorization", Credentials.basic(credentials.username, credentials.password))
            .build()

    private companion object {
        val XML_MEDIA_TYPE = "application/xml; charset=utf-8".toMediaType()

        /**
         * Asks for the four properties this app reads, rather than for everything.
         *
         * An `allprop` request makes the server compute and send every property it holds for every
         * file — checksums, versions, share state — which on a large folder is a much larger
         * response for four fields that are always present.
         */
        val PROPFIND_BODY =
            """
            <?xml version="1.0" encoding="utf-8"?>
            <d:propfind xmlns:d="DAV:">
              <d:prop>
                <d:displayname/>
                <d:resourcetype/>
                <d:getcontentlength/>
                <d:getcontenttype/>
              </d:prop>
            </d:propfind>
            """.trimIndent()
    }
}

/** Why a share could not be opened, in terms that point somewhere useful. */
enum class WebDavProbe {
    /** The share answered and accepted the credentials. */
    Reachable,

    /** The address is not a URL at all. */
    InvalidAddress,

    /** The server answered and refused the username or password. */
    Rejected,

    /** Something is at that address, but it does not speak WebDAV. */
    NotWebDav,

    /** Nothing answered: wrong host, no network, or a server that is down. */
    Unreachable,
}

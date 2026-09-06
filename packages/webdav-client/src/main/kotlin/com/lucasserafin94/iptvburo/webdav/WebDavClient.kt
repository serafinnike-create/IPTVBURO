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
            // readTimeout resets on every byte received, so it never fires against a server
            // (hostile or merely congested) that paces its response just under that interval —
            // a slow-loris trick that would hang the calling thread indefinitely. callTimeout
            // bounds the whole call's wall-clock duration regardless of pacing.
            .callTimeout(Duration.ofMinutes(2))
            .build(),
    private val maximumListingBytes: Int = DEFAULT_MAXIMUM_LISTING_BYTES,
) {
    init {
        require(maximumListingBytes > 0) { "maximumListingBytes must be positive" }
    }
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
     * What is inside [path].
     *
     * `Depth: 1` lists the folder's own children and nothing deeper. Asking for `infinity` is what
     * a naive client does, and on a share holding a film library it asks the server to walk tens of
     * thousands of files to answer one request — many servers refuse it outright.
     *
     * An empty list means the folder is empty, and only that. A network failure is an
     * [java.io.IOException], an HTTP refusal is a [WebDavHttpException], and a listing larger than
     * [maximumListingBytes] is a [WebDavResponseTooLargeException]: collapsing any of them into an
     * empty list would show a full share as an empty folder, which is a much worse lie than an
     * error.
     */
    fun list(path: String = "/"): List<WebDavEntry> {
        val url =
            resolve(path)
                ?: throw WebDavException("The path cannot be resolved against the share address.")
        return client.newCall(propfindRequest(url, depth = 1)).execute().use { response ->
            if (!response.isSuccessful) throw WebDavHttpException(response.code)
            val body = response.body ?: throw WebDavException("The server returned no listing.")
            WebDavListing.parse(body.boundedString(maximumListingBytes), requestedPath = url.encodedPath)
        }
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

    /**
     * Reads the whole body, refusing anything past [maximumBytes].
     *
     * `string()` alone would materialise whatever the server chooses to send; a declared length is
     * checked first and the stream is then capped anyway, because a chunked response declares
     * nothing.
     */
    private fun okhttp3.ResponseBody.boundedString(maximumBytes: Int): String {
        if (contentLength() > maximumBytes) throw WebDavResponseTooLargeException(maximumBytes)
        val bytes = byteStream().readAtMost(maximumBytes + 1)
        if (bytes.size > maximumBytes) throw WebDavResponseTooLargeException(maximumBytes)
        return String(bytes, Charsets.UTF_8)
    }

    private fun java.io.InputStream.readAtMost(maximumBytes: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream(minOf(maximumBytes, READ_BUFFER_BYTES))
        val buffer = ByteArray(READ_BUFFER_BYTES)
        var remaining = maximumBytes
        while (remaining > 0) {
            val read = read(buffer, 0, minOf(buffer.size, remaining))
            if (read < 0) break
            if (read == 0) {
                val singleByte = read()
                if (singleByte < 0) break
                output.write(singleByte)
                remaining -= 1
            } else {
                output.write(buffer, 0, read)
                remaining -= read
            }
        }
        return output.toByteArray()
    }

    private companion object {
        const val READ_BUFFER_BYTES = 32 * 1024

        /**
         * Ceiling for one folder listing, held whole in memory. A folder of ten thousand files is
         * a few megabytes of XML; thirty-two is far past anything legitimate and short of what
         * would pressure the heap.
         */
        const val DEFAULT_MAXIMUM_LISTING_BYTES = 32 * 1024 * 1024

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

/**
 * A listing could not be read.
 *
 * Messages carry a status code or a byte limit, never the share address — the address identifies
 * the subscriber's account and is redacted everywhere else in this package.
 */
open class WebDavException(
    message: String,
    cause: Throwable? = null,
) : java.io.IOException(message, cause)

/** The server answered with a non-success status. [statusCode] is all that is safe to carry. */
class WebDavHttpException(
    val statusCode: Int,
) : WebDavException("The WebDAV server returned HTTP $statusCode.")

/** The listing was larger than the configured ceiling. */
class WebDavResponseTooLargeException(
    val maximumBytes: Int,
) : WebDavException("The WebDAV listing exceeded the configured safety limit ($maximumBytes bytes).")

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

package com.lucasserafin94.iptvburo.desktop.data

import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * A playlist kept on the user's own server, opened as a stream.
 *
 * The case this exists for is a household NAS: the M3U lives on the box that holds the media, and
 * copying it to the laptop every time it changes is the step people forget. Pointing at it directly
 * means the list is whatever the server currently has.
 *
 * Only the playlist file is fetched. Nothing here browses the server's folders or builds a catalogue
 * out of file names — the address given is the address read, and the parser downstream is the same
 * one a local file goes through, so a remote list and a local one behave identically from here on.
 *
 * ## Credentials
 *
 * A NAS is nearly always behind a password, so one is accepted. It is held for the duration of the
 * call and never stored by this class, never written to a log, and never placed in [toString].
 *
 * For WebDAV the password travels in an Authorization header rather than in the URL, so it does not
 * end up in a proxy's access log. FTP has no such header — the protocol puts credentials in the
 * connection itself — which is why [FtpPlaylistReader] builds a URL containing them and why that
 * URL is never returned, logged, or included in an error message.
 */
sealed interface RemotePlaylistSource {
    /** Opens the playlist for reading. The caller owns the stream and must close it. */
    fun open(): InputStream

    /** What to call the imported source in the sidebar. Never the URL: it can carry a password. */
    val displayName: String
}

/**
 * A playlist on a WebDAV share, or on any HTTP server that serves the file.
 *
 * WebDAV needs no protocol support to *read* one file: a GET with Basic authentication is what a
 * WebDAV client does for a download, so an ordinary HTTP client is enough. It would take a real
 * WebDAV implementation to list a collection, which this deliberately does not do.
 */
class WebDavPlaylistReader(
    private val url: String,
    private val username: String? = null,
    private val password: String? = null,
    private val client: OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(15))
            // Generous: a large playlist over a home upstream link is slow rather than broken.
            .readTimeout(Duration.ofMinutes(2))
            .build(),
) : RemotePlaylistSource {
    override val displayName: String = hostOf(url)

    /**
     * The address as HTTP.
     *
     * People paste `webdav://` because that is what their NAS's own interface shows them, and no
     * HTTP client accepts it — the request would fail on the scheme with a message about the URL
     * being malformed, which reads as "your address is wrong" when it is the one their server gave
     * them. WebDAV *is* HTTP, so the scheme is simply mapped: the secure form to https, the plain
     * form to http.
     */
    private val httpUrl: String =
        when {
            url.startsWith("webdavs://", ignoreCase = true) ->
                "https://" + url.substring("webdavs://".length)
            url.startsWith("webdav://", ignoreCase = true) ->
                "http://" + url.substring("webdav://".length)
            else -> url
        }

    override fun open(): InputStream {
        val request =
            Request.Builder()
                .url(httpUrl)
                .apply {
                    // Sent pre-emptively rather than waiting for a 401. Servers vary in whether
                    // they challenge, and an unauthenticated GET that quietly returns a login page
                    // would be parsed as a playlist with no channels in it.
                    if (!username.isNullOrBlank()) {
                        header("Authorization", Credentials.basic(username, password.orEmpty()))
                    }
                }
                .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            // The status only. The URL and any header would carry the credential into whatever
            // shows this message.
            throw IOException("The server answered $code.")
        }
        return response.body?.byteStream() ?: run {
            response.close()
            throw IOException("The server sent no content.")
        }
    }

    /** Never the full URL: it may carry a password, and this ends up on screen. */
    override fun toString(): String = "WebDavPlaylistReader($displayName)"
}

/**
 * A playlist on an FTP server.
 *
 * The JDK has carried an FTP protocol handler since forever and it is enough for this: one file,
 * read once, in binary mode. A dedicated FTP library would buy directory listing and resumption,
 * neither of which this needs.
 *
 * Credentials go in the URL because FTP has nowhere else to put them, so that URL is built here,
 * used immediately, and never surfaced.
 */
class FtpPlaylistReader(
    private val host: String,
    private val path: String,
    private val username: String? = null,
    private val password: String? = null,
    private val port: Int? = null,
) : RemotePlaylistSource {
    override val displayName: String = host

    override fun open(): InputStream {
        // Percent-encoded: a password containing @ or / would otherwise be read as the end of the
        // credential or the start of the path, and the connection would fail with a message
        // implying the server was wrong rather than the password unusual.
        val credentials =
            if (username.isNullOrBlank()) {
                ""
            } else {
                "${encode(username)}:${encode(password.orEmpty())}@"
            }
        val portPart = port?.let { value -> ":$value" }.orEmpty()
        val cleanPath = if (path.startsWith("/")) path else "/$path"
        // `;type=i` asks for binary. Without it some servers translate line endings, which corrupts
        // nothing in an M3U but does in anything else, and being explicit costs a suffix.
        val url = URI("ftp://$credentials$host$portPart$cleanPath;type=i").toURL()

        return try {
            url.openStream()
        } catch (error: IOException) {
            // Rewritten deliberately: the JDK's FTP messages quote the URL, which at this point
            // contains the password.
            throw IOException("Could not read the playlist from $host.")
        }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8)
            // URLEncoder is a form encoder: it turns a space into '+', which is wrong in a URL
            // component and would send the wrong password.
            .replace("+", "%20")

    override fun toString(): String = "FtpPlaylistReader($host)"
}

/**
 * The host part of a URL, for naming the source in the sidebar.
 *
 * Falls back to the whole string only when it does not parse as a URL, in which case it is not a
 * URL and therefore not carrying a password in the userinfo position.
 */
private fun hostOf(url: String): String =
    runCatching { URI(url).host }.getOrNull()?.takeIf(String::isNotBlank) ?: url

/** Which protocol an address is for, decided by its scheme. */
enum class RemotePlaylistProtocol {
    WEBDAV,
    FTP,
    ;

    companion object {
        /**
         * The protocol [url] names, or null when it names none this app can read.
         *
         * `http`/`https` are treated as WebDAV because reading one file is a plain GET either way —
         * a user pointing at a file their web server hosts should not be told the scheme is wrong.
         */
        fun of(url: String): RemotePlaylistProtocol? =
            when (url.trim().substringBefore("://").lowercase()) {
                "webdav", "webdavs", "http", "https" -> WEBDAV
                "ftp" -> FTP
                else -> null
            }
    }
}

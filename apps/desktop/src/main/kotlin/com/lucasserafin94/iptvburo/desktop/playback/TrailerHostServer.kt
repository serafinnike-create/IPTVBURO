package com.lucasserafin94.iptvburo.desktop.playback

import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Serves the one page that hosts a YouTube embed.
 *
 * YouTube refuses to configure its player when the embed is the top-level document with no page
 * behind it — "Video player configuration error, Error 153". It wants an ordinary web page with an
 * ordinary origin embedding it in an iframe, which is what every site doing this has.
 *
 * A `data:` URL does not work: its origin is `null`, which is the very thing being rejected. So the
 * page comes from a real HTTP origin — `http://127.0.0.1:<port>` — bound to the loopback interface
 * only. Nothing outside this machine can reach it, and it carries exactly one page: an iframe.
 *
 * The same approach the VLC control interface already uses here, for the same reason: a loopback
 * server is the cheapest way to get a real origin without shipping a web server.
 */
class TrailerHostServer private constructor(
    private val server: HttpServer,
) {
    private val stopped = AtomicBoolean(false)

    val origin: String
        get() = "http://127.0.0.1:${server.address.port}"

    fun pageUrlFor(
        youtubeId: String,
        autoplay: Boolean = true,
        muted: Boolean = false,
    ): String = "$origin/watch?v=$youtubeId&autoplay=${autoplay.asFlag()}&mute=${muted.asFlag()}"

    private fun Boolean.asFlag(): String = if (this) "1" else "0"

    fun stop() {
        if (!stopped.compareAndSet(false, true)) return
        runCatching { server.stop(0) }
    }

    companion object {
        /**
         * Starts the server, or null when a loopback port cannot be bound.
         *
         * Null rather than an exception: a trailer is an extra, and a machine that refuses the bind
         * — a locked-down firewall, most likely — must still play films. The caller falls back to
         * the system browser.
         */
        fun start(): TrailerHostServer? =
            runCatching {
                // Port 0 lets the OS pick a free one; binding explicitly to loopback means the
                // socket is never reachable from the network.
                val http = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)

                http.createContext("/watch") { exchange ->
                    runCatching {
                        val query = exchange.requestURI.rawQuery.orEmpty()
                        val requestedId =
                            query
                                .split('&')
                                .firstOrNull { part -> part.startsWith("v=") }
                                ?.removePrefix("v=")
                                .orEmpty()

                        // Validated, not trusted. This id is interpolated into HTML, and the server
                        // is reachable by anything running as this user — a video id is letters,
                        // digits, dash and underscore, and nothing else gets through.
                        fun flag(name: String): Boolean =
                            query.split('&').any { part -> part == "$name=1" }

                        val body =
                            if (requestedId.matches(VIDEO_ID)) {
                                page(
                                    youtubeId = requestedId,
                                    origin = "http://127.0.0.1:${http.address.port}",
                                    autoplay = flag("autoplay"),
                                    muted = flag("mute"),
                                )
                            } else {
                                "<!doctype html><html><body style=\"background:#000\"></body></html>"
                            }

                        val bytes = body.toByteArray(StandardCharsets.UTF_8)
                        exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
                        exchange.sendResponseHeaders(200, bytes.size.toLong())
                        exchange.responseBody.use { output -> output.write(bytes) }
                    }
                    runCatching { exchange.close() }
                }

                http.executor = null
                http.start()
                TrailerHostServer(http)
            }.getOrNull()

        private val VIDEO_ID = Regex("[A-Za-z0-9_-]{6,32}")

        /**
         * The host page: a black background and one iframe filling it.
         *
         * `origin` matches the address this page is served from, which is what the player checks.
         */
        private fun page(
            youtubeId: String,
            origin: String,
            autoplay: Boolean,
            muted: Boolean,
        ): String {
            val embed =
                buildString {
                    append("https://www.youtube-nocookie.com/embed/").append(youtubeId)
                    append("?autoplay=").append(if (autoplay) 1 else 0)
                    append("&mute=").append(if (muted) 1 else 0)
                    // A trailer opened deliberately gets controls; one playing behind a banner does
                    // not, and must never make noise unasked.
                    append("&controls=").append(if (muted) 0 else 1)
                    append("&rel=0&modestbranding=1&playsinline=1")
                    if (muted) append("&loop=1&playlist=").append(youtubeId)
                    append("&origin=").append(origin)
                }
            return """
                <!doctype html>
                <html><head><meta charset="utf-8"><title>Trailer</title>
                <style>html,body{margin:0;height:100%;background:#000;overflow:hidden}
                /* The banner is wider than 16:9, and the player letterboxes rather than filling it
                   — black bars either side of the video, which on the opening screen reads as a
                   broken image. Scaled to cover instead, the way the artwork underneath is: the
                   frame is made 16:9 by whichever side is short, then blown up to overflow.

                   The trailer is decoration, so losing a little at the edges is the right trade;
                   the artwork behind it has always been cropped the same way. */
                #fit{position:absolute;top:50%;left:50%;transform:translate(-50%,-50%);
                     width:100vw;height:56.25vw;min-height:100vh;min-width:177.78vh}
                iframe{border:0;display:block;width:100%;height:100%}</style></head>
                <body><div id="fit"><iframe src="$embed"
                allow="autoplay; encrypted-media; fullscreen"
                allowfullscreen></iframe></div></body></html>
            """.trimIndent()
        }
    }
}

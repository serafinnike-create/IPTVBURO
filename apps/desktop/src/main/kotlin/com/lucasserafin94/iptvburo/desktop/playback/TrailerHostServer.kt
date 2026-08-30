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
        blendIntoHero: Boolean = false,
        /**
         * Whether the trailer plays beside something else rather than being opened on its own.
         *
         * True for the Home banner and the Descobrir card: both start by themselves, next to
         * artwork somebody is actually looking at. Those get no controls, repeat, and start muted —
         * no engine autoplays audio, so asking for sound means not starting at all.
         *
         * Not `autoplay`, which the deliberate trailer lightbox also uses, and not [blendIntoHero],
         * which is only about the banner's masks. The question here is whether anybody asked for
         * this video.
         */
        unattended: Boolean = false,
    ): String =
        "$origin/watch?v=$youtubeId&autoplay=${autoplay.asFlag()}" +
            "&mute=${muted.asFlag()}&hero=${blendIntoHero.asFlag()}" +
            "&unattended=${unattended.asFlag()}"

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
                                    blendIntoHero = flag("hero"),
                                    unattended = flag("unattended"),
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
            blendIntoHero: Boolean,
            unattended: Boolean,
        ): String {
            val embed =
                buildString {
                    append("https://www.youtube-nocookie.com/embed/").append(youtubeId)
                    append("?autoplay=").append(if (autoplay) 1 else 0)
                    // Anything that starts on its own starts muted, whatever the viewer asked
                    // for — the banner and the Descobrir card alike.
                    //
                    // Every engine refuses to autoplay audio: asked for sound up front, the banner
                    // does not start at all and shows YouTube's play button over a still frame —
                    // seen exactly that way once the sound preference was remembered. So it starts
                    // silent and the script below unmutes it the moment it is actually playing,
                    // which is the closest any page can get to opening with sound.
                    append("&mute=").append(if (muted || unattended) 1 else 0)
                    // A trailer opened deliberately gets controls and plays once; the banner has
                    // neither, and repeats.
                    //
                    // Decided by where the video sits, not by whether it has sound. These keyed off
                    // `muted`, so turning the banner's sound on put YouTube's pause button over it
                    // and stopped it looping — reported with a screenshot of the controls sitting
                    // there. The two questions are unrelated.
                    // No controls on anything that plays itself: the banner and the Descobrir
                    // card are both decoration beside something else, and a pause button over them
                    // is clutter for a video nobody asked to open. A trailer somebody opened
                    // deliberately still gets them.
                    append("&controls=").append(if (unattended) 0 else 1)
                    append("&rel=0&modestbranding=1&playsinline=1")
                    // Lets the page below talk to the player at all, which is what makes unmuting
                    // after the start possible.
                    if (unattended) append("&enablejsapi=1")
                    if (unattended) append("&loop=1&playlist=").append(youtubeId)
                    append("&origin=").append(origin)
                }
            val bodyClass = if (blendIntoHero) " class=\"cinematic-hero\"" else ""

            /*
             * Turns the sound on once the banner is already playing.
             *
             * The banner always loads muted because that is the only way it loads at all, so a
             * viewer who asked for sound would otherwise never get it. Waiting for the player to
             * report that it is playing before unmuting keeps the autoplay: the engine has already
             * granted it by then, and raising the volume afterwards is not a new request.
             *
             * Retried on a timer as well as on the ready event — the player answers when it feels
             * like it, and a single attempt that lands too early leaves the banner silent for good.
             * It stops as soon as it has worked.
             */
            val unmuteScript =
                if (unattended && !muted) {
                    """
                    <script>
                    (function(){
                      var frame=document.querySelector('iframe');
                      var done=false;
                      function ask(what,args){
                        if(!frame||!frame.contentWindow)return;
                        frame.contentWindow.postMessage(JSON.stringify(
                          {event:'command',func:what,args:args||[]}),'*');
                      }
                      function raise(){
                        if(done)return;
                        ask('unMute');ask('setVolume',[100]);
                      }
                      // Asks the player to report its state; without this it sends nothing and the
                      // listener below never hears that playback started.
                      function listen(){
                        if(!frame||!frame.contentWindow)return;
                        frame.contentWindow.postMessage(JSON.stringify(
                          {event:'listening',id:1}),'*');
                      }
                      frame&&frame.addEventListener('load',listen);
                      listen();
                      window.addEventListener('message',function(e){
                        var d;try{d=JSON.parse(e.data)}catch(_){return}
                        if(!d||!d.info)return;
                        // 1 is "playing": the engine has granted the autoplay, so sound is safe.
                        //
                        // Read from either shape the player answers in. Listening only for the
                        // state-change event missed a player that was already playing before this
                        // script attached — and the sound switch then appeared to do nothing at
                        // all, which is exactly how it was reported. The infoDelivery replies carry
                        // the current state too, so an already-running video is caught as well.
                        var s = d.info.playerState;
                        if(s===undefined && d.info.currentTime!==undefined) s = 1;
                        if(s===1){raise();done=true}
                      });
                      // The timer only ever asks the player to report itself. It must NOT call
                      // raise(): an unMute that arrives before the video is playing is read as a
                      // request to autoplay with audio, which the engine refuses outright — and
                      // what the viewer gets is YouTube's play button parked over a still frame.
                      // Reported exactly that way, with the sound switch already on.
                      //
                      // So the sound is raised in one place only: the PLAYING message below.
                      var tries=0;
                      var timer=setInterval(function(){
                        listen();
                        // Sixty tries, not twenty: ten seconds was not enough for a trailer that
                        // buffers, and when the timer gave up the sound was never raised at all.
                        if(done||++tries>60)clearInterval(timer);
                      },500);
                    })();
                    </script>
                    """.trimIndent()
                } else {
                    ""
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
                iframe{border:0;display:block;width:100%;height:100%}

                /* Chromium is an AWT heavyweight surface, so a Compose scrim cannot be painted
                   over it. Put the cinematic masks in this page itself: the left edge becomes the
                   same BURO canvas as the copy column and the bottom dissolves into the first
                   shelf. Without these two masks the player reads as a rectangle laid on top of
                   the hero. This class is used only by the Home banner; the explicit trailer
                   lightbox keeps an unobstructed 16:9 player and its controls. */
                body.cinematic-hero::before,body.cinematic-hero::after{
                     content:"";position:fixed;z-index:2;pointer-events:none}
                body.cinematic-hero::before{
                     /* Wide enough to actually dissolve.
                        At 28% the fade covered a narrow strip of a player that is itself only just
                        over half the banner, so what showed was a hard vertical edge where the
                        video began — reported as the trailer not sitting properly in the banner.
                        Half the player's width, held opaque at the start, reads as one picture. */
                     inset:0 auto 0 0;width:50%;
                     background:linear-gradient(90deg,
                         rgba(8,9,10,1) 0%,rgba(8,9,10,1) 14%,
                         rgba(8,9,10,.86) 34%,rgba(8,9,10,.46) 66%,
                         rgba(8,9,10,0) 100%)}
                body.cinematic-hero::after{
                     inset:auto 0 0 0;height:38%;
                     background:linear-gradient(0deg,
                         rgba(8,9,10,.96) 0%,rgba(8,9,10,.64) 34%,
                         rgba(8,9,10,0) 100%)}
                /* The banner's player is blown up past the frame on purpose.

                   `controls=0` is a request, not a guarantee: YouTube still draws its own title
                   bar along the top and its transport buttons across the middle-bottom, and both
                   showed on the banner over a video nobody had asked to control — reported as the
                   trailer not sitting right. They are drawn relative to the player, so scaling the
                   player beyond the visible frame carries them outside it, and what is left in
                   view is picture. The explicit trailer lightbox is untouched: there the controls
                   are the point. */
                body.cinematic-hero #fit{width:126vw;height:70.9vw;
                     min-height:126vh;min-width:224vh}
                /* The moving picture arrives as part of the banner, not as a player dropped on it.

                   This animation must live here rather than in Compose: JCEF is an AWT heavyweight
                   surface, so Compose alpha and transforms do not affect its pixels. A restrained
                   fade with a tiny pull-back gives the artwork-to-video hand-off some depth while
                   keeping the title and actions still and readable. */
                body.cinematic-hero #fit{
                     animation:hero-reveal 850ms cubic-bezier(.2,.72,.24,1) both;
                     transform-origin:center center}
                @keyframes hero-reveal{
                     from{opacity:0;transform:translate(-50%,-50%) scale(1.035)}
                     to{opacity:1;transform:translate(-50%,-50%) scale(1)}}
                @media (prefers-reduced-motion:reduce){body.cinematic-hero #fit{animation:none}}
                </style></head>
                <body$bodyClass><div id="fit"><iframe src="$embed"
                allow="autoplay; encrypted-media; fullscreen"
                allowfullscreen></iframe></div>$unmuteScript</body></html>
            """.trimIndent()
        }
    }
}

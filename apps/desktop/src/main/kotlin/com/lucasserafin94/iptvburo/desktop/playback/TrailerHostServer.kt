package com.lucasserafin94.iptvburo.desktop.playback

import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
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
    private val artworkByToken: ConcurrentHashMap<String, String>,
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
        /**
         * Whether the video repeats instead of ending.
         *
         * Separate from [unattended]: the Descobrir card still wants to loop a short trailer while
         * somebody reads the synopsis beside it, but the Home banner needs the opposite — YouTube's
         * own playerState 0 only ever fires once, and looping is what a fixed-timer rotation used
         * to fall back on before this existed.
         */
        loop: Boolean = unattended,
        /** Kept server-side: private or signed artwork URLs never enter the visible browser URL. */
        artworkUrl: String? = null,
    ): String =
        "$origin/watch?v=$youtubeId&autoplay=${autoplay.asFlag()}" +
            "&mute=${muted.asFlag()}&hero=${blendIntoHero.asFlag()}" +
            "&unattended=${unattended.asFlag()}&loop=${loop.asFlag()}" +
            artworkUrl
                ?.takeIf { blendIntoHero && it.isSafeArtworkUrl() }
                ?.let { safeUrl ->
                    val token = UUID.randomUUID().toString()
                    // Bounded, because only a page that is actually fetched removes its own token.
                    //
                    // A trailer that fails, or one the rotation replaces before its page loads,
                    // leaves its entry behind — and the banner rotates all day. Each entry holds an
                    // artwork address, so an app left open accumulates them for as long as it runs.
                    // The oldest are dropped rather than the newest refused: a token that has not
                    // been redeemed in the last few hundred rotations is not going to be.
                    if (artworkByToken.size >= MAX_ARTWORK_TOKENS) {
                        artworkByToken.keys.take(artworkByToken.size / 2).forEach(artworkByToken::remove)
                    }
                    artworkByToken[token] = safeUrl
                    "&art=$token"
                }.orEmpty()

        private fun Boolean.asFlag(): String = if (this) "1" else "0"

    private fun String.isSafeArtworkUrl(): Boolean =
        runCatching {
            val uri = java.net.URI(this)
            uri.scheme.equals("https", ignoreCase = true) || uri.scheme.equals("http", ignoreCase = true)
        }.getOrDefault(false)

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
                val artworkByToken = ConcurrentHashMap<String, String>()

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

                        val artworkToken =
                            query
                                .split('&')
                                .firstOrNull { part -> part.startsWith("art=") }
                                ?.removePrefix("art=")
                        // One-use lookup: the real URL is never placed in the page address and the
                        // server does not retain credentials after producing the document.
                        val artworkUrl = artworkToken?.let(artworkByToken::remove)

                        val body =
                            if (requestedId.matches(VIDEO_ID)) {
                                page(
                                    youtubeId = requestedId,
                                    origin = "http://127.0.0.1:${http.address.port}",
                                    autoplay = flag("autoplay"),
                                    muted = flag("mute"),
                                    blendIntoHero = flag("hero"),
                                    unattended = flag("unattended"),
                                    loop = flag("loop"),
                                    artworkUrl = artworkUrl,
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
                TrailerHostServer(http, artworkByToken)
            }.getOrNull()

        private val VIDEO_ID = Regex("[A-Za-z0-9_-]{6,32}")

        /**
         * How many unredeemed artwork tokens to keep.
         *
         * Generous: the banner holds twenty titles and the Descobrir deck fifteen, so this is many
         * sessions' worth of rotation. It exists to stop unbounded growth, not to be reached.
         */
        private const val MAX_ARTWORK_TOKENS = 256

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
            /** Whether the video repeats instead of ending. See pageUrlFor. */
            loop: Boolean,
            artworkUrl: String?,
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
                    if (unattended) append("&disablekb=1&fs=0&iv_load_policy=3")
                    // Lets the page below talk to the player at all, which is what makes unmuting
                    // after the start possible.
                    if (unattended) append("&enablejsapi=1")
                    if (loop) append("&loop=1&playlist=").append(youtubeId)
                    append("&origin=").append(origin)
                }
            val safeArtworkCss =
                artworkUrl
                    ?.takeIf { blendIntoHero }
                    ?.replace("\\", "\\\\")
                    ?.replace("\"", "\\\"")
                    ?.replace("<", "%3C")
                    ?.replace(">", "%3E")
                    ?.replace("\r", "")
                    ?.replace("\n", "")
            val bodyClass =
                when {
                    blendIntoHero && safeArtworkCss != null ->
                        " class=\"cinematic-hero unattended with-art\""
                    blendIntoHero -> " class=\"cinematic-hero unattended\""
                    unattended -> " class=\"ambient-card unattended\""
                    else -> ""
                }
            val pointerGlass = if (unattended) "<div id=\"pointer-glass\"></div>" else ""

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
            /*
             * Hides the player when YouTube refuses to play the video.
             *
             * The availability check before this asks YouTube's oEmbed whether the video is public,
             * and a video can pass that and still be refused at playback — a region lock, an
             * embedding restriction, a rights holder blocking this player. What shows then is
             * YouTube's own card, "An error occurred. Please try again later", sitting beside the
             * poster on the Descobrir card. Reported with a screenshot of exactly that: an error
             * message is the one thing the opening screen and the deck must never show.
             *
             * So the page hides itself, and the artwork underneath — which is always drawn — is
             * what remains. The engine cannot tell us this: the page loads perfectly, and the error
             * is content inside it. Only the player knows, and this is how it says so.
             */
            val hideRejectedPlayer =
                if (unattended) "document.documentElement.style.display='none';" else ""
            val failureScript =
                """
                    <script>
                    (function(){
                      var frame=document.querySelector('iframe');
                      var resolved=false;
                      function signal(value){
                        if(value==='playing'&&resolved)return;
                        if(value==='playing')resolved=true;
                        if(window.cefQuery)window.cefQuery({request:value});
                      }
                      function listen(){
                        if(!frame||!frame.contentWindow)return;
                        frame.contentWindow.postMessage(JSON.stringify(
                          {event:'listening',id:2}),'*');
                      }
                      frame&&frame.addEventListener('load',listen);
                      listen();
                      var poll=setInterval(listen,500);
                      setTimeout(function(){clearInterval(poll)},30000);
                      window.addEventListener('message',function(e){
                        var d;try{d=JSON.parse(e.data)}catch(_){return}
                        /* 2, 5, 100, 101 and 150 are YouTube's "cannot play this here" codes.
                           Whichever arrives, the answer is the same: show the artwork instead. */
                        if(d&&d.event==='onError'){
                          $hideRejectedPlayer
                          signal('failed');
                          return;
                        }
                        if(d&&d.info){
                          var state=d.info.playerState;
                          if(state===1||d.info.currentTime>0)signal('playing');
                          // 0 is YouTube's own "ended". Real end-of-video, not a guess from a
                          // fixed timer: a trailer held for a flat sixty seconds was cut off
                          // mid-scene as often as it was allowed to finish naturally, and one
                          // that ran shorter left the banner sitting on a frozen last frame for
                          // however long was left of that guess.
                          if(state===0)signal('ended');
                        }
                      });
                      setTimeout(function(){if(!resolved)signal('failed')},10000);
                    })();
                    </script>
                """.trimIndent()
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

                /* Autoplay previews are not miniature YouTube pages. An invisible glass layer
                   keeps pointer hover out of the cross-origin iframe, so moving the mouse through
                   Descobrir does not summon its title, transport controls and bottom toolbar. */
                body.unattended #pointer-glass{
                     position:fixed;inset:0;z-index:4;background:transparent}

                /* Descobrir owns a clean 16:9 card, not the hero's oversized crop. Overscan the
                   iframe by two pixels rather than insetting it: an inset exposed Chromium's own
                   top and right edges as a grey rule around the moving picture. The page itself
                   draws the radius because Compose cannot clip a heavyweight Chromium surface. */
                body.ambient-card #fit{
                     top:-2px;right:-2px;bottom:-2px;left:-2px;
                     width:auto;height:auto;min-width:0;min-height:0;
                     transform:none;border-radius:18px;overflow:hidden;
                     animation:card-reveal 420ms ease-out both}
                body.ambient-card iframe{outline:0}
                @keyframes card-reveal{from{opacity:0}to{opacity:1}}

                /* Chromium is an AWT heavyweight surface, so a Compose scrim cannot be painted
                   over it. Put the cinematic masks in this page itself: the left edge becomes the
                   same BURO canvas as the copy column and the bottom dissolves into the first
                   shelf. Without these two masks the player reads as a rectangle laid on top of
                   the hero. This class is used only by the Home banner; the explicit trailer
                   lightbox keeps an unobstructed 16:9 player and its controls. */
                body.cinematic-hero::before,body.cinematic-hero::after{
                     content:"";position:fixed;z-index:2;pointer-events:none}
                body.cinematic-hero:not(.with-art)::before{
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
                /* Repeat the exact banner artwork inside Chromium and dissolve it into motion.
                   A transparent CSS edge cannot reveal Compose under a heavyweight AWT window; it
                   reveals Chromium's black canvas. Mirroring the still here removes that black
                   vertical seam, so the poster genuinely becomes the trailer in one surface. */
                body.cinematic-hero.with-art::before{
                     inset:0;
                     background-image:linear-gradient(rgba(8,9,10,.08),rgba(8,9,10,.08)),
                         url(\"${safeArtworkCss.orEmpty()}\");
                     background-size:100% 100%,172.414% auto;
                     background-position:center,right center;
                     background-repeat:no-repeat;
                     -webkit-mask-image:linear-gradient(90deg,
                         #000 0%,#000 18%,rgba(0,0,0,.88) 34%,
                         rgba(0,0,0,.42) 58%,transparent 78%);
                     mask-image:linear-gradient(90deg,
                         #000 0%,#000 18%,rgba(0,0,0,.88) 34%,
                         rgba(0,0,0,.42) 58%,transparent 78%)}
                body.cinematic-hero::after{
                     inset:auto 0 0 0;height:46%;
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
                allowfullscreen></iframe></div>$pointerGlass$failureScript$unmuteScript</body></html>
            """.trimIndent()
        }
    }
}

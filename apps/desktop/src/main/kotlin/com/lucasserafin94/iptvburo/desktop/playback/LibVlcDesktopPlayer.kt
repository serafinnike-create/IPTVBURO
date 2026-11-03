package com.lucasserafin94.iptvburo.desktop.playback

import com.lucasserafin94.iptvburo.desktop.DebugLog
import com.lucasserafin94.iptvburo.desktop.ui.DesktopStrings
import com.lucasserafin94.iptvburo.desktop.ui.ScreenStrings
import com.lucasserafin94.iptvburo.desktop.user.DesktopLanguage
import com.sun.jna.Memory
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer

/**
 * The macOS player: libVLC inside the app, each frame painted by the app itself.
 *
 * The Windows player hands VLC a window handle and lets it draw there from its own process. macOS
 * has no equivalent — a process cannot draw into another's view — so libVLC runs in this process and
 * gives each decoded frame to [VideoSurface] through its video callbacks. The frame is copied into a
 * Java image and painted like any other Swing content, which also means Compose menus can sit over
 * the picture here, where on Windows they cannot.
 *
 * Decoding uses VideoToolbox where the codec allows it; the copy back to memory is the price of
 * drawing the frame ourselves, and frames wider than [MAX_FRAME_WIDTH] are scaled by VLC first so
 * a 4K stream does not copy 33 MB per frame for a window that cannot show it.
 */
internal class LibVlcDesktopPlayer(
    private val subtitleStyle: SubtitleStyle = SubtitleStyle(),
    @Suppress("unused") private val hardwareDecoding: VlcHardwareDecoding = VlcHardwareDecoding.AUTOMATIC,
    private val startupDelayMillis: Long = 0L,
    private val networkCachingMillis: Int = DEFAULT_NETWORK_CACHING_MILLIS,
    private val logTag: String = "player",
    private val text: ScreenStrings = DesktopStrings.of(DesktopLanguage.PORTUGUESE_BRAZIL).shareStrings.screens,
) : DesktopVideoPlayer {
    private val executor: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { task -> Thread(task, "libvlc-$logTag").apply { isDaemon = true } }
    private val disposed = AtomicBoolean(false)
    private val started = AtomicBoolean(false)

    @Volatile private var snapshot = DesktopPlaybackSnapshot(engineName = "VLC")

    // Touched only on [executor].
    private var instance: Pointer? = null
    private var player: Pointer? = null
    private var current: DesktopPlaybackRequest? = null
    private var reconnectAttempts = 0
    private var lastPositionMillis = 0L
    private var lastLengthMillis = 0L
    private var tracksReadAt = 0L

    private val surface = VideoSurface()

    /** Frames painted so far; read by the macOS smoke test to prove the picture really arrives. */
    internal val framesPresented: Long get() = surface.frames

    // Held for the life of the player: libVLC keeps raw pointers to these, and a collected callback
    // is a crash on the next frame.
    private val formatCallback =
        object : LibVlc.VideoFormatCallback {
            override fun invoke(
                opaque: PointerByReference?,
                chroma: Pointer,
                width: IntByReference,
                height: IntByReference,
                pitches: Pointer,
                lines: Pointer,
            ): Int {
                var frameWidth = width.value.coerceAtLeast(2)
                var frameHeight = height.value.coerceAtLeast(2)
                if (frameWidth > MAX_FRAME_WIDTH) {
                    frameHeight = (frameHeight.toLong() * MAX_FRAME_WIDTH / frameWidth).toInt().coerceAtLeast(2)
                    frameWidth = MAX_FRAME_WIDTH
                }
                chroma.write(0, CHROMA_RV32, 0, CHROMA_RV32.size)
                width.value = frameWidth
                height.value = frameHeight
                pitches.setInt(0, frameWidth * 4)
                lines.setInt(0, frameHeight)
                surface.allocate(frameWidth, frameHeight)
                return 1
            }
        }
    private val cleanupCallback = object : LibVlc.VideoCleanupCallback { override fun invoke(opaque: Pointer?) = Unit }
    private val lockCallback =
        object : LibVlc.VideoLockCallback {
            override fun invoke(opaque: Pointer?, planes: Pointer): Pointer? {
                planes.setPointer(0, surface.buffer())
                return null
            }
        }
    private val unlockCallback =
        object : LibVlc.VideoUnlockCallback { override fun invoke(opaque: Pointer?, picture: Pointer?, planes: Pointer?) = Unit }
    private val displayCallback =
        object : LibVlc.VideoDisplayCallback { override fun invoke(opaque: Pointer?, picture: Pointer?) = surface.present() }

    override fun createComponent(
        request: DesktopPlaybackRequest,
        onPointerActivity: () -> Unit,
        onClick: () -> Unit,
        onKey: (Int) -> Boolean,
        onDoubleClick: (() -> Unit)?,
        onWheel: (Int) -> Unit,
    ): JPanel {
        surface.bindInput(onPointerActivity, onClick, onKey, onDoubleClick, onWheel)
        if (started.compareAndSet(false, true)) {
            executor.schedule({ start(request) }, startupDelayMillis.coerceIn(0L, 3_000L), TimeUnit.MILLISECONDS)
            executor.scheduleWithFixedDelay(::poll, POLL_MILLIS, POLL_MILLIS, TimeUnit.MILLISECONDS)
        }
        return surface
    }

    override fun createCanvas(
        request: DesktopPlaybackRequest,
        onPointerActivity: () -> Unit,
        onClick: () -> Unit,
        onKey: (Int) -> Boolean,
    ): Component = createComponent(request, onPointerActivity, onClick, onKey, null) {}

    override fun setPointerVisible(component: JPanel, visible: Boolean) {
        component.cursor =
            if (visible) {
                Cursor.getDefaultCursor()
            } else {
                Toolkit.getDefaultToolkit().createCustomCursor(
                    BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB),
                    Point(0, 0),
                    "hidden",
                )
            }
    }

    override fun snapshot(): DesktopPlaybackSnapshot = snapshot

    override fun togglePlayback() =
        onPlayer { vlc, mp ->
            val playing = vlc.libvlc_media_player_get_state(mp) == LibVlc.STATE_PLAYING
            vlc.libvlc_media_player_set_pause(mp, if (playing) 1 else 0)
            if (!playing && vlc.libvlc_media_player_get_state(mp) == LibVlc.STATE_ENDED) {
                vlc.libvlc_media_player_play(mp)
            }
        }

    override fun seekToFraction(fraction: Double) {
        if (current?.isLive == true) return
        onPlayer { vlc, mp -> vlc.libvlc_media_player_set_position(mp, fraction.coerceIn(0.0, 1.0).toFloat()) }
    }

    override fun seekBy(millis: Double) {
        if (current?.isLive == true) return
        onPlayer { vlc, mp ->
            val target = (vlc.libvlc_media_player_get_time(mp) + millis.toLong()).coerceAtLeast(0L)
            val length = vlc.libvlc_media_player_get_length(mp)
            vlc.libvlc_media_player_set_time(mp, if (length > 0) target.coerceAtMost(length - 1_000L) else target)
        }
    }

    override fun setAspectRatio(ratio: PlaybackAspectRatio) {
        surface.aspectRatio = ratio
        snapshot = snapshot.copy(aspectRatio = ratio)
        surface.repaint()
    }

    override fun setBrightness(value: Double) {
        val brightness = value.coerceIn(0.0, 2.0)
        snapshot = snapshot.copy(brightness = brightness)
        onPlayer { vlc, mp ->
            vlc.libvlc_video_set_adjust_int(mp, LibVlc.ADJUST_ENABLE, 1)
            vlc.libvlc_video_set_adjust_float(mp, LibVlc.ADJUST_BRIGHTNESS, brightness.toFloat())
        }
    }

    override fun setVolume(value: Double, remember: Boolean) {
        val volume = value.coerceIn(0.0, 1.0)
        snapshot = snapshot.copy(volume = volume)
        onPlayer { vlc, mp -> vlc.libvlc_audio_set_volume(mp, (volume * 100).toInt()) }
    }

    override fun selectAudioTrack(trackId: Int) {
        onPlayer { vlc, mp ->
            vlc.libvlc_audio_set_track(mp, trackId)
            tracksReadAt = 0L
        }
    }

    override fun selectSubtitleTrack(trackId: Int) {
        onPlayer { vlc, mp ->
            vlc.libvlc_video_set_spu(mp, trackId)
            tracksReadAt = 0L
        }
    }

    override fun setPlaybackRate(value: Double) {
        val rate = value.coerceIn(0.25, 4.0)
        snapshot = snapshot.copy(playbackRate = rate)
        onPlayer { vlc, mp -> vlc.libvlc_media_player_set_rate(mp, rate.toFloat()) }
    }

    override fun retry(request: DesktopPlaybackRequest) {
        if (disposed.get()) return
        executor.execute {
            reconnectAttempts = 0
            start(request)
        }
    }

    override fun dispose() {
        if (!disposed.compareAndSet(false, true)) return
        // Stopping libVLC blocks until its threads are down, so it is never done on the UI thread;
        // the executor is told to finish that one job and then go.
        executor.execute {
            releasePlayer()
            instance?.let { vlc()?.libvlc_release(it) }
            instance = null
        }
        executor.shutdown()
    }

    // -----------------------------------------------------------------------------------------

    private fun vlc(): LibVlc? = LibVlcRuntime.library()

    private fun onPlayer(action: (LibVlc, Pointer) -> Unit) {
        if (disposed.get()) return
        executor.execute {
            val vlc = vlc() ?: return@execute
            val mp = player ?: return@execute
            runCatching { action(vlc, mp) }
        }
    }

    /** Opens [request] on a fresh media player, replacing any previous one. */
    private fun start(
        request: DesktopPlaybackRequest,
        resumeAtMillis: Long = request.startPositionMillis,
    ) {
        if (disposed.get()) return
        current = request
        val vlc = vlc()
        if (vlc == null) {
            snapshot = snapshot.copy(loading = false, errorMessage = text.playerStartFailed)
            return
        }
        val libvlc = instance ?: vlc.libvlc_new(instanceArguments().size, instanceArguments())?.also { instance = it }
        if (libvlc == null) {
            snapshot = snapshot.copy(loading = false, errorMessage = text.playerStartFailed)
            return
        }
        releasePlayer()
        val media =
            if (request.uri.scheme.equals("file", ignoreCase = true)) {
                vlc.libvlc_media_new_path(libvlc, java.io.File(request.uri).absolutePath)
            } else {
                vlc.libvlc_media_new_location(libvlc, request.uri.toASCIIString())
            }
        if (media == null) {
            snapshot = snapshot.copy(loading = false, errorMessage = text.playerStartFailed)
            return
        }
        mediaOptions(request, resumeAtMillis).forEach { option -> vlc.libvlc_media_add_option(media, option) }
        val mp = vlc.libvlc_media_player_new_from_media(media)
        vlc.libvlc_media_release(media)
        if (mp == null) {
            snapshot = snapshot.copy(loading = false, errorMessage = text.playerStartFailed)
            return
        }
        player = mp
        vlc.libvlc_video_set_format_callbacks(mp, formatCallback, cleanupCallback)
        vlc.libvlc_video_set_callbacks(mp, lockCallback, unlockCallback, displayCallback, null)
        vlc.libvlc_audio_set_volume(mp, (snapshot.volume * 100).toInt())
        if (snapshot.brightness != 1.0) {
            vlc.libvlc_video_set_adjust_int(mp, LibVlc.ADJUST_ENABLE, 1)
            vlc.libvlc_video_set_adjust_float(mp, LibVlc.ADJUST_BRIGHTNESS, snapshot.brightness.toFloat())
        }
        tracksReadAt = 0L
        snapshot = snapshot.copy(loading = true, ended = false, errorMessage = null)
        DebugLog.d(logTag) { "libvlc play (live=${request.isLive})" }
        vlc.libvlc_media_player_play(mp)
    }

    private fun releasePlayer() {
        val vlc = vlc() ?: return
        player?.let { mp ->
            runCatching { vlc.libvlc_media_player_stop(mp) }
            runCatching { vlc.libvlc_media_player_release(mp) }
        }
        player = null
    }

    private fun instanceArguments(): Array<String> =
        arrayOf(
            "--no-video-title-show",
            "--no-stats",
            "--no-snapshot-preview",
            "--avcodec-hw=any",
            "--freetype-rel-fontsize=${subtitleStyle.vlcRelativeSize}",
            "--freetype-color=${subtitleStyle.textColour.vlcValue}",
            "--freetype-opacity=255",
            "--freetype-background-opacity=${if (subtitleStyle.background) 160 else 0}",
            "--quiet",
        )

    private fun mediaOptions(
        request: DesktopPlaybackRequest,
        resumeAtMillis: Long,
    ): List<String> =
        buildList {
            add(":network-caching=$networkCachingMillis")
            add(":live-caching=$networkCachingMillis")
            add(":file-caching=1000")
            add(":http-reconnect")
            if (!request.isLive && resumeAtMillis > 0L) add(":start-time=${resumeAtMillis / 1000.0}")
        }

    /** Reads the player's state into [snapshot], and reconnects a stream that dropped. */
    private fun poll() {
        if (disposed.get()) return
        val vlc = vlc() ?: return
        val mp = player ?: return
        val request = current ?: return
        runCatching {
            val state = vlc.libvlc_media_player_get_state(mp)
            val time = vlc.libvlc_media_player_get_time(mp)
            val length = vlc.libvlc_media_player_get_length(mp)
            if (time > 0) lastPositionMillis = time
            if (length > 0) lastLengthMillis = length
            val playing = state == LibVlc.STATE_PLAYING
            if (playing) reconnectAttempts = 0

            val nearEnd = lastLengthMillis > 0 && lastPositionMillis >= lastLengthMillis - END_TOLERANCE_MILLIS
            val dropped = state == LibVlc.STATE_ERROR || (state == LibVlc.STATE_ENDED && (request.isLive || !nearEnd))
            if (dropped && reconnectAttempts < MAX_RECONNECTS) {
                reconnectAttempts += 1
                snapshot = snapshot.copy(reconnecting = true, playing = false)
                val delayMillis = RECONNECT_BASE_MILLIS shl (reconnectAttempts - 1)
                val resumeAt = if (request.isLive) 0L else lastPositionMillis
                executor.schedule({ if (!disposed.get() && player == mp) start(request, resumeAt) }, delayMillis, TimeUnit.MILLISECONDS)
                return
            }

            val now = System.currentTimeMillis()
            var audio = snapshot.audioTracks
            var subtitles = snapshot.subtitleTracks
            var activeAudio = snapshot.activeAudioTrackId
            var activeSubtitle = snapshot.activeSubtitleTrackId
            if (playing && now - tracksReadAt > TRACKS_REFRESH_MILLIS) {
                tracksReadAt = now
                audio = readTracks(vlc, vlc.libvlc_audio_get_track_description(mp))
                subtitles = readTracks(vlc, vlc.libvlc_video_get_spu_description(mp))
                activeAudio = vlc.libvlc_audio_get_track(mp)
                activeSubtitle = vlc.libvlc_video_get_spu(mp)
            }

            snapshot =
                snapshot.copy(
                    loading = state == LibVlc.STATE_OPENING || state == LibVlc.STATE_BUFFERING || (!playing && !surface.hasFrame),
                    ready = surface.hasFrame || playing,
                    playing = playing,
                    positionMillis = lastPositionMillis.toDouble(),
                    durationMillis = if (request.isLive) 0.0 else lastLengthMillis.toDouble(),
                    ended = state == LibVlc.STATE_ENDED && !dropped,
                    reconnecting = false,
                    errorMessage = if (dropped) text.playerStopped else null,
                    audioTracks = audio,
                    subtitleTracks = subtitles,
                    activeAudioTrackId = activeAudio,
                    activeSubtitleTrackId = activeSubtitle,
                )
        }
    }

    /** Walks a libvlc_track_description_t list: an int id, a name, and the next node. */
    private fun readTracks(
        vlc: LibVlc,
        head: Pointer?,
    ): List<MediaTrack> {
        head ?: return emptyList()
        return try {
            buildList {
                var node: Pointer? = head
                var guard = 0
                while (node != null && guard++ < MAX_TRACKS) {
                    val id = node.getInt(0)
                    val name = node.getPointer(com.sun.jna.Native.POINTER_SIZE.toLong())?.getString(0, "UTF-8").orEmpty()
                    add(MediaTrack(id = id, label = name.ifBlank { "#$id" }))
                    node = node.getPointer(2L * com.sun.jna.Native.POINTER_SIZE)
                }
            }
        } finally {
            vlc.libvlc_track_description_list_release(head)
        }
    }

    /**
     * Where the frames land and are painted.
     *
     * Two pixel arrays: libVLC writes into native memory, [present] copies the finished frame into
     * the image the panel paints, under a lock, so a paint never shows half of one frame and half of
     * the next.
     */
    private inner class VideoSurface : JPanel() {
        @Volatile var aspectRatio: PlaybackAspectRatio = PlaybackAspectRatio.DEFAULT

        @Volatile var hasFrame: Boolean = false
            private set

        @Volatile var frames: Long = 0L
            private set

        private val lock = Any()
        private var native: Memory? = null
        private var image: BufferedImage? = null
        private var pixels: IntArray? = null

        init {
            background = Color.BLACK
            isOpaque = true
            isFocusable = true
            isDoubleBuffered = true
        }

        fun allocate(width: Int, height: Int) {
            synchronized(lock) {
                native = Memory(width.toLong() * height * 4)
                val frame = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
                image = frame
                pixels = (frame.raster.dataBuffer as DataBufferInt).data
                hasFrame = false
            }
        }

        fun buffer(): Pointer? = synchronized(lock) { native }

        fun present() {
            synchronized(lock) {
                val source = native ?: return
                val target = pixels ?: return
                source.read(0, target, 0, target.size)
                hasFrame = true
                frames += 1
            }
            repaint()
        }

        override fun paintComponent(graphics: Graphics) {
            super.paintComponent(graphics)
            val g = graphics as Graphics2D
            g.color = Color.BLACK
            g.fillRect(0, 0, width, height)
            synchronized(lock) {
                val frame = image ?: return
                if (!hasFrame) return
                val sourceRatio =
                    when (aspectRatio) {
                        PlaybackAspectRatio.RATIO_16_9 -> 16.0 / 9.0
                        PlaybackAspectRatio.RATIO_16_10 -> 16.0 / 10.0
                        PlaybackAspectRatio.RATIO_4_3 -> 4.0 / 3.0
                        PlaybackAspectRatio.RATIO_3_2 -> 3.0 / 2.0
                        else -> frame.width.toDouble() / frame.height
                    }
                val panelRatio = width.toDouble() / height.coerceAtLeast(1)
                val cover = aspectRatio == PlaybackAspectRatio.FILL
                val (drawWidth, drawHeight) =
                    if ((sourceRatio > panelRatio) != cover) {
                        width to (width / sourceRatio).toInt()
                    } else {
                        (height * sourceRatio).toInt() to height
                    }
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                g.drawImage(frame, (width - drawWidth) / 2, (height - drawHeight) / 2, drawWidth, drawHeight, null)
            }
        }

        private var inputBound = false

        fun bindInput(
            onPointerActivity: () -> Unit,
            onClick: () -> Unit,
            onKey: (Int) -> Boolean,
            onDoubleClick: (() -> Unit)?,
            onWheel: (Int) -> Unit,
        ) {
            if (inputBound) return
            inputBound = true
            addMouseMotionListener(
                object : MouseMotionAdapter() {
                    override fun mouseMoved(event: MouseEvent) = onPointerActivity()

                    override fun mouseDragged(event: MouseEvent) = onPointerActivity()
                },
            )
            addKeyListener(
                object : KeyAdapter() {
                    override fun keyPressed(event: KeyEvent) {
                        if (onKey(event.keyCode)) event.consume()
                    }
                },
            )
            addMouseWheelListener { event ->
                onPointerActivity()
                onWheel(event.wheelRotation)
            }
            addMouseListener(
                object : MouseAdapter() {
                    private var pendingClick: Timer? = null

                    override fun mousePressed(event: MouseEvent) {
                        requestFocusInWindow()
                        onPointerActivity()
                        if (onDoubleClick == null) onClick()
                    }

                    override fun mouseClicked(event: MouseEvent) {
                        val doubleClick = onDoubleClick ?: return
                        if (!SwingUtilities.isLeftMouseButton(event)) return
                        if (event.clickCount >= 2) {
                            pendingClick?.stop()
                            pendingClick = null
                            doubleClick()
                        } else {
                            val interval =
                                (Toolkit.getDefaultToolkit().getDesktopProperty("awt.multiClickInterval") as? Int) ?: 400
                            pendingClick?.stop()
                            pendingClick = Timer(interval) { onClick() }.apply {
                                isRepeats = false
                                start()
                            }
                        }
                    }

                    override fun mouseEntered(event: MouseEvent) = onPointerActivity()
                },
            )
        }
    }

    private companion object {
        val CHROMA_RV32 = "RV32".toByteArray(Charsets.US_ASCII)

        /** Wide enough for a full-HD window; a 4K stream is scaled by VLC before it is copied. */
        const val MAX_FRAME_WIDTH = 1920
        const val POLL_MILLIS = 250L
        const val TRACKS_REFRESH_MILLIS = 3_000L
        const val MAX_TRACKS = 64
        const val MAX_RECONNECTS = 5
        const val RECONNECT_BASE_MILLIS = 1_000L

        /** How close to the end a stop counts as the film finishing rather than the stream dropping. */
        const val END_TOLERANCE_MILLIS = 15_000L
    }
}

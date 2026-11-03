package com.lucasserafin94.iptvburo.desktop.playback

import com.lucasserafin94.iptvburo.desktop.DebugLog
import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import java.io.File

/**
 * The part of libVLC's C API the macOS player uses, bound directly with JNA.
 *
 * Written out rather than taken from vlcj: vlcj is GPL, and linking it would put this app under the
 * GPL. libVLC itself is LGPL, which allows exactly this — a program calling the library it ships
 * beside it. Only the functions the player needs are declared; each is the VLC 3 signature.
 */
@Suppress("FunctionName", "LocalVariableName")
internal interface LibVlc : Library {
    fun libvlc_new(argc: Int, argv: Array<String>): Pointer?

    fun libvlc_release(instance: Pointer)

    fun libvlc_media_new_location(instance: Pointer, mrl: String): Pointer?

    fun libvlc_media_new_path(instance: Pointer, path: String): Pointer?

    fun libvlc_media_add_option(media: Pointer, option: String)

    fun libvlc_media_release(media: Pointer)

    fun libvlc_media_player_new_from_media(media: Pointer): Pointer?

    fun libvlc_media_player_release(player: Pointer)

    fun libvlc_media_player_play(player: Pointer): Int

    fun libvlc_media_player_set_pause(player: Pointer, pause: Int)

    fun libvlc_media_player_stop(player: Pointer)

    fun libvlc_media_player_get_state(player: Pointer): Int

    fun libvlc_media_player_get_time(player: Pointer): Long

    fun libvlc_media_player_set_time(player: Pointer, time: Long)

    fun libvlc_media_player_get_length(player: Pointer): Long

    fun libvlc_media_player_set_position(player: Pointer, position: Float)

    fun libvlc_media_player_is_seekable(player: Pointer): Int

    fun libvlc_media_player_set_rate(player: Pointer, rate: Float): Int

    fun libvlc_audio_set_volume(player: Pointer, volume: Int): Int

    fun libvlc_audio_get_track(player: Pointer): Int

    fun libvlc_audio_set_track(player: Pointer, track: Int): Int

    fun libvlc_audio_get_track_description(player: Pointer): Pointer?

    fun libvlc_video_get_spu(player: Pointer): Int

    fun libvlc_video_set_spu(player: Pointer, spu: Int): Int

    fun libvlc_video_get_spu_description(player: Pointer): Pointer?

    fun libvlc_track_description_list_release(list: Pointer)

    fun libvlc_video_set_adjust_int(player: Pointer, option: Int, value: Int)

    fun libvlc_video_set_adjust_float(player: Pointer, option: Int, value: Float)

    fun libvlc_video_set_format_callbacks(player: Pointer, setup: VideoFormatCallback, cleanup: VideoCleanupCallback)

    fun libvlc_video_set_callbacks(
        player: Pointer,
        lock: VideoLockCallback,
        unlock: VideoUnlockCallback,
        display: VideoDisplayCallback,
        opaque: Pointer?,
    )

    interface VideoFormatCallback : Callback {
        fun invoke(
            opaque: PointerByReference?,
            chroma: Pointer,
            width: IntByReference,
            height: IntByReference,
            pitches: Pointer,
            lines: Pointer,
        ): Int
    }

    interface VideoCleanupCallback : Callback {
        fun invoke(opaque: Pointer?)
    }

    interface VideoLockCallback : Callback {
        fun invoke(opaque: Pointer?, planes: Pointer): Pointer?
    }

    interface VideoUnlockCallback : Callback {
        fun invoke(opaque: Pointer?, picture: Pointer?, planes: Pointer?)
    }

    interface VideoDisplayCallback : Callback {
        fun invoke(opaque: Pointer?, picture: Pointer?)
    }

    companion object {
        // libvlc_state_t
        const val STATE_OPENING = 1
        const val STATE_BUFFERING = 2
        const val STATE_PLAYING = 3
        const val STATE_PAUSED = 4
        const val STATE_STOPPED = 5
        const val STATE_ENDED = 6
        const val STATE_ERROR = 7

        // libvlc_video_adjust_option_t
        const val ADJUST_ENABLE = 0
        const val ADJUST_BRIGHTNESS = 2
    }
}

/** The C library's setenv, to point libVLC at its plugins before it starts. */
@Suppress("FunctionName")
private interface LibC : Library {
    fun setenv(name: String, value: String, overwrite: Int): Int
}

/**
 * Finds and loads the libVLC that ships inside the app, once per process.
 *
 * The bundle keeps VLC.app's own layout — `vlc/lib/libvlc.dylib`, `vlc/lib/libvlccore.dylib` and
 * `vlc/plugins/` — because libVLC's plugin loader and the dylibs' own load paths assume it. An
 * installed VLC.app is the fallback for a development run on a Mac.
 */
internal object LibVlcRuntime {
    @Volatile
    private var loaded: LibVlc? = null

    @Volatile
    private var failed = false

    fun library(): LibVlc? {
        loaded?.let { return it }
        if (failed) return null
        synchronized(this) {
            loaded?.let { return it }
            if (failed) return null
            val result = runCatching(::load).onFailure { error ->
                DebugLog.d("libvlc") { "load failed: ${error.javaClass.simpleName}" }
            }.getOrNull()
            if (result == null) failed = true else loaded = result
            return result
        }
    }

    /** Where the bundled engine lives, or null when there is none to be found. */
    internal fun vlcRoot(): File? =
        listOfNotNull(
            System.getProperty("compose.application.resources.dir")?.let { File(it, "vlc") },
            File("/Applications/VLC.app/Contents/MacOS"),
        ).firstOrNull { root -> File(root, "lib/libvlc.dylib").isFile && File(root, "plugins").isDirectory }

    private fun load(): LibVlc {
        val root = requireNotNull(vlcRoot()) { "libVLC is not bundled" }
        val lib = File(root, "lib")
        // Before libvlc_new reads it: the plugins are found through this, and without them libVLC
        // starts and then has no demuxer, decoder or audio output to play anything with.
        Native.load("c", LibC::class.java).setenv("VLC_PLUGIN_PATH", File(root, "plugins").absolutePath, 1)
        // libvlc.dylib links libvlccore through @rpath. Loading the core first by absolute path means
        // the dynamic loader already holds it when libvlc asks, whatever the rpath says.
        NativeLibrary.addSearchPath("vlccore", lib.absolutePath)
        NativeLibrary.addSearchPath("vlc", lib.absolutePath)
        NativeLibrary.getInstance(File(lib, "libvlccore.dylib").absolutePath)
        return Native.load(File(lib, "libvlc.dylib").absolutePath, LibVlc::class.java)
    }
}

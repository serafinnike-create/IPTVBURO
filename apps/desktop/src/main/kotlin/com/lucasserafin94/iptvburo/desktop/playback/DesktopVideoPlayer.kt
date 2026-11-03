package com.lucasserafin94.iptvburo.desktop.playback

import com.lucasserafin94.iptvburo.desktop.ui.DesktopStrings
import com.lucasserafin94.iptvburo.desktop.ui.ScreenStrings
import com.lucasserafin94.iptvburo.desktop.user.DesktopLanguage
import com.lucasserafin94.iptvburo.domain.model.AudioOutputMode
import java.awt.Component
import javax.swing.JPanel

/**
 * What the player screens need from a video engine.
 *
 * Two engines sit behind it. Windows runs VLC as its own process, drawing into the app's window
 * through its handle — see [VlcDesktopPlayer]. macOS cannot do that: one process may not draw into
 * another's view there, so [LibVlcDesktopPlayer] runs libVLC inside the app and paints each frame
 * itself. Both hand back an ordinary Swing component, so the player, multiview and the guide's
 * preview are the same code on both systems.
 */
interface DesktopVideoPlayer {
    fun createComponent(
        request: DesktopPlaybackRequest,
        onPointerActivity: () -> Unit = {},
        onClick: () -> Unit = {},
        onKey: (Int) -> Boolean = { false },
        onDoubleClick: (() -> Unit)? = null,
        onWheel: (Int) -> Unit = {},
    ): JPanel

    /** A bare surface for multiview, where the grid owns the panel. */
    fun createCanvas(
        request: DesktopPlaybackRequest,
        onPointerActivity: () -> Unit = {},
        onClick: () -> Unit = {},
        onKey: (Int) -> Boolean = { false },
    ): Component

    fun setPointerVisible(
        component: JPanel,
        visible: Boolean,
    )

    fun snapshot(): DesktopPlaybackSnapshot

    fun togglePlayback()

    fun seekToFraction(fraction: Double)

    fun seekBy(millis: Double)

    fun setAspectRatio(ratio: PlaybackAspectRatio)

    fun setBrightness(value: Double)

    fun setVolume(
        value: Double,
        remember: Boolean = true,
    )

    fun selectAudioTrack(trackId: Int)

    fun selectSubtitleTrack(trackId: Int)

    fun setPlaybackRate(value: Double)

    fun retry(request: DesktopPlaybackRequest)

    fun dispose()

    companion object {
        /** The engine for this operating system, configured as the caller asks. */
        fun create(
            subtitleStyle: SubtitleStyle = SubtitleStyle(),
            audioOutput: AudioOutputMode = AudioOutputMode.SYSTEM,
            hardwareDecoding: VlcHardwareDecoding = VlcHardwareDecoding.DISABLED,
            startupDelayMillis: Long = 0L,
            networkCachingMillis: Int,
            logTag: String = "player",
            text: ScreenStrings = DesktopStrings.of(DesktopLanguage.PORTUGUESE_BRAZIL).shareStrings.screens,
        ): DesktopVideoPlayer =
            if (com.sun.jna.Platform.isMac()) {
                LibVlcDesktopPlayer(
                    subtitleStyle = subtitleStyle,
                    hardwareDecoding = hardwareDecoding,
                    startupDelayMillis = startupDelayMillis,
                    networkCachingMillis = networkCachingMillis,
                    logTag = logTag,
                    text = text,
                )
            } else {
                VlcDesktopPlayer(
                    subtitleStyle = subtitleStyle,
                    audioOutput = audioOutput,
                    hardwareDecoding = hardwareDecoding,
                    startupDelayMillis = startupDelayMillis,
                    networkCachingMillis = networkCachingMillis,
                    logTag = logTag,
                    text = text,
                )
            }
    }
}

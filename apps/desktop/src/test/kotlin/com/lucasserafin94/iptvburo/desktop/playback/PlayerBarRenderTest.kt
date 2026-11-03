package com.lucasserafin94.iptvburo.desktop.playback

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.use
import com.lucasserafin94.iptvburo.desktop.user.DesktopLanguage
import com.lucasserafin94.iptvburo.domain.model.AudioOutputMode
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import org.jetbrains.skia.EncodedImageFormat

/**
 * The player's bar laid out for real, at a desktop width, in a film and in a live channel.
 *
 * The bar sits beside a native video window and cannot be seen in a unit test any other way. This
 * renders it to PNG under build/ so a change can be looked at, and fails if laying it out throws —
 * which is how an overflowing row or a crash in a tooltip would first show up.
 */
class PlayerBarRenderTest {
    private val film =
        DesktopPlaybackSnapshot(
            loading = false,
            ready = true,
            playing = true,
            positionMillis = 1_234_000.0,
            durationMillis = 2_700_000.0,
            volume = 0.62,
            audioTracks = listOf(MediaTrack(1, "Português"), MediaTrack(2, "English")),
            activeAudioTrackId = 1,
            subtitleTracks = listOf(MediaTrack(-1, "Off"), MediaTrack(3, "Português")),
            activeSubtitleTrackId = 3,
        )

    private fun render(name: String, state: DesktopPlaybackSnapshot, live: Boolean, next: String?, panel: PlayerPanel? = null) {
        val text = PlayerText.of(DesktopLanguage.PORTUGUESE_BRAZIL)
        ImageComposeScene(width = 1366, height = if (panel != null) 380 else 180).use { scene ->
            scene.setContent {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    Column(Modifier.fillMaxWidth().background(Color(0xF20A0C0F)).padding(horizontal = 20.dp, vertical = 10.dp)) {
                        StatusLine(
                            state = state,
                            showError = false,
                            notice = null,
                            upNextOffered = next != null,
                            nextEpisodeTitle = next,
                            secondsLeft = 24,
                            text = text,
                            onRetry = {},
                            onPlayNext = {},
                            onCancelNext = {},
                        )
                        panel?.let {
                            PanelContent(
                                panel = it, state = state, isLive = live, audioOutput = AudioOutputMode.SYSTEM, text = text,
                                onSelectSubtitle = {}, onSelectAudio = {}, onSpeed = {}, onAspect = {}, onAudioOutput = {}, onBrightness = {},
                            )
                        }
                        if (!live) SeekBar(state = state, onSeek = {})
                        ControlsRow(
                            state = state, isLive = live, isFullScreen = false, isFavorite = !live, muted = false,
                            audioOutput = AudioOutputMode.SYSTEM, hasNext = next != null, text = text,
                            onTogglePlayback = {}, onSeekBy = {}, onToggleFavorite = {}, onToggleMute = {},
                            onVolume = {}, openPanel = panel, onTogglePanel = {},
                            onPlayNext = {}, onMiniPlayer = {}, onFullScreen = {}, onClose = {},
                        )
                    }
                }
            }
            val image = scene.render()
            val bytes = image.encodeToData(EncodedImageFormat.PNG)!!.bytes
            val out = File("build/player-bar/$name.png").apply { parentFile.mkdirs() }
            out.writeBytes(bytes)
            assertTrue(out.length() > 0)
        }
    }

    @Test
    fun `the bar lays out for a film with the next episode offered`() =
        render("film-up-next", film, live = false, next = "Episódio 5 · O retorno")

    @Test
    fun `the settings panel lays out above the controls`() =
        render("settings-panel", film, live = false, next = null, panel = PlayerPanel.SETTINGS)

    @Test
    fun `the bar lays out for a live channel`() =
        render("live", film.copy(durationMillis = 0.0, positionMillis = 0.0), live = true, next = null)
}

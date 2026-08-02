package com.lucasserafin94.iptvburo.desktop.playback

import java.awt.BorderLayout
import javax.swing.JPanel
import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.layout.StackPane
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.scene.media.MediaView
import javafx.util.Duration

class JavaFxDesktopPlayer {
    @Volatile
    private var snapshot = DesktopPlaybackSnapshot()
    private var mediaPlayer: MediaPlayer? = null

    fun createComponent(request: DesktopPlaybackRequest): JPanel {
        val panel = JPanel(BorderLayout())
        val fxPanel = JFXPanel()
        panel.add(fxPanel, BorderLayout.CENTER)
        Platform.runLater {
            runCatching {
                val media = Media(request.uri.toASCIIString())
                val player = MediaPlayer(media)
                val root = StackPane()
                val view = MediaView(player).apply {
                    isPreserveRatio = true
                    fitWidthProperty().bind(root.widthProperty())
                    fitHeightProperty().bind(root.heightProperty())
                }
                root.children.add(view)
                mediaPlayer = player
                fxPanel.scene = Scene(root, 960.0, 540.0).apply {
                    fill = javafx.scene.paint.Color.BLACK
                }
                player.volume = snapshot.volume
                player.setOnReady {
                    snapshot = snapshot.copy(
                        ready = true,
                        durationMillis = player.totalDuration.toMillis().finiteOrZero(),
                        errorMessage = null,
                    )
                    player.play()
                }
                player.setOnPlaying { snapshot = snapshot.copy(playing = true, ready = true) }
                player.setOnPaused { snapshot = snapshot.copy(playing = false) }
                player.setOnStopped { snapshot = snapshot.copy(playing = false) }
                player.currentTimeProperty().addListener { _, _, value ->
                    snapshot = snapshot.copy(positionMillis = value.toMillis().finiteOrZero())
                }
                player.totalDurationProperty().addListener { _, _, value ->
                    snapshot = snapshot.copy(durationMillis = value.toMillis().finiteOrZero())
                }
                player.setOnError {
                    snapshot = snapshot.copy(
                        playing = false,
                        errorMessage = safePlaybackError(player.error),
                    )
                }
                media.setOnError {
                    snapshot = snapshot.copy(
                        playing = false,
                        errorMessage = safePlaybackError(media.error),
                    )
                }
            }.onFailure {
                snapshot = snapshot.copy(errorMessage = "O formato não é compatível com o player Windows.")
            }
        }
        return panel
    }

    fun snapshot(): DesktopPlaybackSnapshot = snapshot

    fun togglePlayback() = onPlayer { player -> if (player.status == MediaPlayer.Status.PLAYING) player.pause() else player.play() }

    fun seekToFraction(fraction: Double) = onPlayer { player ->
        val duration = player.totalDuration.toMillis()
        if (duration.isFinite() && duration > 0.0) player.seek(Duration.millis(duration * fraction.coerceIn(0.0, 1.0)))
    }

    fun seekBy(millis: Double) = onPlayer { player ->
        val duration = player.totalDuration.toMillis().finiteOrZero()
        val target = (player.currentTime.toMillis() + millis).coerceIn(0.0, duration.coerceAtLeast(0.0))
        player.seek(Duration.millis(target))
    }

    fun setVolume(value: Double) {
        val safe = value.coerceIn(0.0, 1.0)
        snapshot = snapshot.copy(volume = safe)
        onPlayer { it.volume = safe }
    }

    fun dispose() {
        val player = mediaPlayer
        mediaPlayer = null
        if (player != null) Platform.runLater { player.stop(); player.dispose() }
    }

    private fun onPlayer(block: (MediaPlayer) -> Unit) {
        val player = mediaPlayer ?: return
        Platform.runLater { runCatching { block(player) } }
    }

    private fun Double.finiteOrZero(): Double = if (isFinite() && this >= 0.0) this else 0.0

    private fun safePlaybackError(error: Throwable?): String =
        when {
            error == null -> "A reprodução foi interrompida."
            else -> "Este vídeo não pôde ser decodificado pelo player Windows. Tente uma versão H.264/AAC."
        }
}

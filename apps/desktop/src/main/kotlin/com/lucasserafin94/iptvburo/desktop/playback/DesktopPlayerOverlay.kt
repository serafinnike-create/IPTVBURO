package com.lucasserafin94.iptvburo.desktop.playback

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lucasserafin94.iptvburo.desktop.ui.BuroColors
import kotlinx.coroutines.delay

@Composable
fun DesktopPlayerOverlay(request: DesktopPlaybackRequest, onClose: () -> Unit) {
    val controller = remember(request) { JavaFxDesktopPlayer() }
    var state by remember(request) { mutableStateOf(DesktopPlaybackSnapshot()) }

    DisposableEffect(controller) { onDispose(controller::dispose) }
    LaunchedEffect(controller) {
        while (true) {
            state = controller.snapshot()
            delay(250)
        }
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onClose) { Text("Voltar") }
            Spacer(Modifier.width(16.dp))
            Text(request.title, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("PLAYER WINDOWS", color = BuroColors.Primary)
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            SwingPanel(
                factory = { controller.createComponent(request) },
                modifier = Modifier.fillMaxSize(),
            )
            state.errorMessage?.let { message ->
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.82f)), contentAlignment = Alignment.Center) {
                    Text(message, color = Color.White, modifier = Modifier.padding(32.dp))
                }
            }
        }
        Column(Modifier.fillMaxWidth().background(Color(0xE6121418)).padding(horizontal = 24.dp, vertical = 14.dp)) {
            Slider(
                value = if (state.durationMillis > 0.0) (state.positionMillis / state.durationMillis).toFloat().coerceIn(0f, 1f) else 0f,
                onValueChange = { controller.seekToFraction(it.toDouble()) },
                enabled = state.ready && state.durationMillis > 0.0,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = { controller.seekBy(-10_000.0) }, enabled = state.ready) { Text("-10 s") }
                Button(onClick = controller::togglePlayback, enabled = state.ready) { Text(if (state.playing) "Pausar" else "Reproduzir") }
                Button(onClick = { controller.seekBy(30_000.0) }, enabled = state.ready) { Text("+30 s") }
                Spacer(Modifier.weight(1f))
                Text("Volume", color = Color.White)
                Slider(
                    value = state.volume.toFloat(),
                    onValueChange = { controller.setVolume(it.toDouble()) },
                    modifier = Modifier.width(180.dp),
                )
            }
        }
    }
}

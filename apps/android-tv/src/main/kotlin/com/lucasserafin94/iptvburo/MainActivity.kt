package com.lucasserafin94.iptvburo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.lucasserafin94.iptvburo.playback.PlaybackSessionFactory
import com.lucasserafin94.iptvburo.ui.AppContent
import com.lucasserafin94.iptvburo.ui.MainViewModel
import com.lucasserafin94.iptvburo.ui.screens.AppShellScreen
import com.lucasserafin94.iptvburo.ui.screens.LegalOnboardingScreen
import com.lucasserafin94.iptvburo.ui.screens.PlayerScreen
import com.lucasserafin94.iptvburo.ui.theme.IptvBuroTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    @Inject
    lateinit var playbackSessionFactory: PlaybackSessionFactory

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            IptvBuroTheme {
                IptvBuroRoot(
                    viewModel = viewModel,
                    playbackSessionFactory = playbackSessionFactory,
                )
            }
        }
    }
}

@Composable
private fun IptvBuroRoot(
    viewModel: MainViewModel,
    playbackSessionFactory: PlaybackSessionFactory,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let(viewModel::importPlaylist)
    }

    when {
        state.isInitializing -> Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF070B14)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.common_loading),
                color = Color.White,
                fontSize = 18.sp,
            )
        }

        !state.hasAcceptedLegalNotice -> LegalOnboardingScreen(
            onAccept = viewModel::acceptLegalNotice,
        )

        state.content is AppContent.Player -> {
            val playerContent = state.content as AppContent.Player
            PlayerScreen(
                channel = playerContent.channel,
                playbackSessionFactory = playbackSessionFactory,
                onBack = { viewModel.goBack() },
            )
        }

        else -> {
            BackHandler(enabled = state.content != AppContent.Home) {
                viewModel.goBack()
            }
            AppShellScreen(
                state = state,
                onSelectSection = viewModel::selectSection,
                onImportSource = {
                    filePicker.launch(
                        arrayOf(
                            "audio/x-mpegurl",
                            "application/x-mpegURL",
                            "application/vnd.apple.mpegurl",
                            "text/plain",
                            "application/octet-stream",
                        ),
                    )
                },
                onOpenSource = viewModel::openSource,
                onOpenCategory = viewModel::openCategory,
                onOpenChannel = viewModel::openChannel,
                onOpenHomeItem = viewModel::openStory,
                onRememberHomeFocus = viewModel::rememberLastFocusedHomeItem,
                onBack = { viewModel.goBack() },
            )
        }
    }
}

package com.lucasserafin94.iptvburo

import android.content.res.Configuration
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.compose.LocalActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.CircularProgressIndicator
import androidx.tv.material3.Text
import com.lucasserafin94.iptvburo.playback.PlaybackSessionFactory
import com.lucasserafin94.iptvburo.playback.AndroidPlaybackProgressCoordinator
import com.lucasserafin94.iptvburo.ui.AppContent
import com.lucasserafin94.iptvburo.ui.MainViewModel
import com.lucasserafin94.iptvburo.ui.screens.AppShellScreen
import com.lucasserafin94.iptvburo.ui.screens.LegalOnboardingScreen
import com.lucasserafin94.iptvburo.ui.screens.LanguageSelectionScreen
import com.lucasserafin94.iptvburo.ui.screens.PlayerScreen
import com.lucasserafin94.iptvburo.ui.screens.ProfilePickerScreen
import com.lucasserafin94.iptvburo.ui.localization.AppLocaleController
import com.lucasserafin94.iptvburo.ui.theme.IptvBuroTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    @Inject
    lateinit var playbackSessionFactory: PlaybackSessionFactory

    @Inject
    lateinit var playbackProgressCoordinator: AndroidPlaybackProgressCoordinator

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocaleController.wrapBaseContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            val isTelevision =
                resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK ==
                    Configuration.UI_MODE_TYPE_TELEVISION
            if (isTelevision) {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                show(WindowInsetsCompat.Type.systemBars())
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }

        setContent {
            IptvBuroTheme {
                IptvBuroRoot(
                    viewModel = viewModel,
                    playbackSessionFactory = playbackSessionFactory,
                    playbackProgressCoordinator = playbackProgressCoordinator,
                )
            }
        }
    }
}

@Composable
private fun IptvBuroRoot(
    viewModel: MainViewModel,
    playbackSessionFactory: PlaybackSessionFactory,
    playbackProgressCoordinator: AndroidPlaybackProgressCoordinator,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalActivity.current as MainActivity
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let(viewModel::importPlaylist)
    }

    when {
        !AppLocaleController.hasSelection(activity) -> LanguageSelectionScreen(
            languages = AppLocaleController.supportedLanguages,
            onSelect = { tag -> AppLocaleController.applySelection(activity, tag) },
        )

        state.isInitializing -> BuroBootScreen()

        !state.hasAcceptedLegalNotice -> LegalOnboardingScreen(
            onAccept = viewModel::acceptLegalNotice,
        )

        state.isProfilesLoading -> BuroBootScreen()

        state.activeProfile == null -> ProfilePickerScreen(
            profiles = state.profiles,
            onSelect = viewModel::selectProfile,
            onCreate = viewModel::createProfile,
        )

        state.content is AppContent.Player -> {
            val playerContent = state.content as AppContent.Player
            PlayerScreen(
                channel = playerContent.channel,
                nowPlaying = state.liveNow,
                nextPlaying = state.liveNext,
                isEpgLoading = state.isLiveEpgLoading,
                playbackSessionFactory = playbackSessionFactory,
                playbackProgressCoordinator = playbackProgressCoordinator,
                activeProfileId = state.activeProfile?.id,
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
                onImportXtreamSource = viewModel::importXtreamSource,
                onCancelXtreamImport = viewModel::cancelXtreamImport,
                onImportStalkerSource = viewModel::importStalkerSource,
                onCancelStalkerImport = viewModel::cancelStalkerImport,
                onOpenSource = viewModel::openSource,
                onOpenCategory = viewModel::openCategory,
                onOpenChannel = viewModel::openChannel,
                onPlayMovie = viewModel::playSelectedMovie,
                onToggleMovieFavorite = viewModel::toggleSelectedMovieFavorite,
                onOpenPerson = viewModel::openPerson,
                onOpenEpisode = viewModel::openEpisode,
                onDownloadMovie = viewModel::downloadSelectedMovie,
                onDownloadEpisode = viewModel::downloadEpisode,
                onCancelDownload = viewModel::cancelDownload,
                onDeleteDownload = viewModel::deleteDownload,
                onSelectProfile = viewModel::selectProfile,
                onCreateProfile = viewModel::createProfile,
                onRequestProfileSelection = viewModel::requestProfileSelection,
                onSelectLanguage = { tag -> AppLocaleController.applySelection(activity, tag) },
                onLoadMore = viewModel::loadMoreChannels,
                onRetryCatalog = viewModel::retryCatalog,
                onOpenHomeItem = viewModel::openStory,
                onRememberHomeFocus = viewModel::rememberLastFocusedHomeItem,
                onBack = { viewModel.goBack() },
            )
        }
    }
}

@Composable
private fun BuroBootScreen() {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF090A0D)),
    ) {
        Image(
            painter = painterResource(R.drawable.buro_nocturne_hero),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            alignment = Alignment.CenterEnd,
            contentScale = ContentScale.Crop,
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            0f to Color(0xFF090A0D),
                            0.55f to Color(0xE6090A0D),
                            1f to Color(0x33090A0D),
                        ),
                    ),
        )
        Column(
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .safeDrawingPadding()
                    .padding(horizontal = 48.dp)
                    .widthIn(max = 520.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = "IPTV  BURO",
                color = Color(0xFFF6F7FA),
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp,
                textAlign = TextAlign.Start,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.common_loading),
                color = Color(0xFFB6BAC5),
                fontSize = 16.sp,
            )
            Spacer(Modifier.height(22.dp))
            CircularProgressIndicator(
                color = Color(0xFF8B7CFF),
            )
        }
    }
}

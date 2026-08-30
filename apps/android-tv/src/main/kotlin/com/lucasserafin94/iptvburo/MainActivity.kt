package com.lucasserafin94.iptvburo

import android.content.res.Configuration
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.lucasserafin94.iptvburo.data.licensing.AndroidLicenseEndpoints
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.compose.LocalActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.compose.material3.CircularProgressIndicator
import androidx.tv.material3.Text
import com.lucasserafin94.iptvburo.playback.PlaybackSessionFactory
import com.lucasserafin94.iptvburo.playback.AndroidPlaybackProgressCoordinator
import com.lucasserafin94.iptvburo.domain.model.CatalogContentType
import com.lucasserafin94.iptvburo.data.billing.GooglePlayBillingManager
import com.lucasserafin94.iptvburo.data.billing.GooglePlayBillingOutcome
import com.lucasserafin94.iptvburo.data.licensing.AndroidLicenseService
import com.lucasserafin94.iptvburo.ui.AppContent
import com.lucasserafin94.iptvburo.ui.BootStageUi
import com.lucasserafin94.iptvburo.ui.formatDownloadRate
import com.lucasserafin94.iptvburo.ui.LicenseUiState
import com.lucasserafin94.iptvburo.ui.MainViewModel
import com.lucasserafin94.iptvburo.ui.openStreamingOffer
import com.lucasserafin94.iptvburo.ui.shareTitle
import com.lucasserafin94.iptvburo.ui.ParentalMessage
import com.lucasserafin94.iptvburo.ui.screens.AppShellScreen
import com.lucasserafin94.iptvburo.ui.screens.CatalogueGuardUi
import com.lucasserafin94.iptvburo.ui.screens.GuardCategoryUi
import com.lucasserafin94.iptvburo.ui.screens.BuroCinematicBackdrop
import com.lucasserafin94.iptvburo.ui.screens.LegalOnboardingScreen
import com.lucasserafin94.iptvburo.ui.screens.LanguageSelectionScreen
import com.lucasserafin94.iptvburo.ui.screens.LicenseGateScreen
import com.lucasserafin94.iptvburo.ui.screens.PlayerScreen
import com.lucasserafin94.iptvburo.ui.screens.ProfilePickerScreen
import com.lucasserafin94.iptvburo.ui.localization.AppLocaleController
import com.lucasserafin94.iptvburo.ui.theme.BuroCanvas
import com.lucasserafin94.iptvburo.ui.theme.BuroGold
import com.lucasserafin94.iptvburo.ui.theme.BuroTextPrimary
import com.lucasserafin94.iptvburo.ui.theme.BuroTextSecondary
import com.lucasserafin94.iptvburo.ui.theme.IptvBuroTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.size.Size
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private lateinit var googlePlayBilling: GooglePlayBillingManager

    @Inject
    lateinit var playbackSessionFactory: PlaybackSessionFactory

    @Inject
    lateinit var playbackProgressCoordinator: AndroidPlaybackProgressCoordinator

    @Inject
    lateinit var licenseService: AndroidLicenseService

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

        googlePlayBilling =
            GooglePlayBillingManager(this, licenseService) { outcome ->
                runOnUiThread {
                    when (outcome) {
                        GooglePlayBillingOutcome.Verified -> viewModel.refreshLicense()
                        GooglePlayBillingOutcome.Pending ->
                            Toast.makeText(this, R.string.play_billing_pending, Toast.LENGTH_LONG).show()
                        GooglePlayBillingOutcome.Rejected ->
                            Toast.makeText(this, R.string.play_billing_rejected, Toast.LENGTH_LONG).show()
                        GooglePlayBillingOutcome.Unavailable ->
                            Toast.makeText(this, R.string.play_billing_unavailable, Toast.LENGTH_LONG).show()
                        GooglePlayBillingOutcome.Cancelled -> Unit
                    }
                }
            }

        setContent {
            IptvBuroTheme {
                IptvBuroRoot(
                    viewModel = viewModel,
                    playbackSessionFactory = playbackSessionFactory,
                    playbackProgressCoordinator = playbackProgressCoordinator,
                    onPurchase = googlePlayBilling::launchPurchase,
                    onRestore = googlePlayBilling::restorePurchases,
                )
            }
        }

        handleSharedLink(intent)
    }

    /**
     * A shared link arriving while the app is already running.
     *
     * `launchMode` is the default, but Android still delivers a second link into the existing task
     * through here rather than through a fresh [onCreate]; without this, tapping a link with BURO
     * already open would merely bring the previous screen forward.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSharedLink(intent)
    }

    /**
     * Hands a `VIEW` intent's link to the view model, if it is one of ours.
     *
     * The data is treated as untrusted text: [com.lucasserafin94.iptvburo.domain.model.TitleShareLink]
     * refuses anything it does not recognise, and the link names a title rather than a location, so
     * a hostile link cannot make the app open a stream or reach a host of the sender's choosing.
     */
    private fun handleSharedLink(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val link = intent.dataString ?: return
        viewModel.openSharedLink(link)
    }

    override fun onDestroy() {
        if (::googlePlayBilling.isInitialized) googlePlayBilling.close()
        super.onDestroy()
    }
}

@Composable
private fun IptvBuroRoot(
    viewModel: MainViewModel,
    playbackSessionFactory: PlaybackSessionFactory,
    playbackProgressCoordinator: AndroidPlaybackProgressCoordinator,
    onPurchase: (String) -> Unit,
    onRestore: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalActivity.current as MainActivity
    val blockedLicense = state.license as? LicenseUiState.Blocked
    var isShowingPosterReveal by remember { mutableStateOf(false) }

    // Android's mandatory system splash is only allowed to draw a solid background and one icon.
    // Once Compose owns the window, keep our cinematic screen visible until the first screen's
    // artwork is actually in hand.
    //
    // This used to be a flat 1.6 second timer, which is what made the app look like it froze on
    // opening: the timer ran out while the covers were still being fetched, so Home arrived as a
    // grid of empty boxes that filled in one by one under the viewer's eyes. Waiting for the images
    // themselves means the loading screen is doing what it says it is doing.
    //
    // [BOOT_POSTER_REVEAL_TIMEOUT_MILLIS] still caps it. A slow or failing network must never trap
    // somebody on a loading screen — past the ceiling the app opens anyway and fills in as it goes,
    // which is the old behaviour and an acceptable worst case rather than the normal one.
    val platformContext = LocalPlatformContext.current
    LaunchedEffect(state.bootBackdropUrls) {
        if (
            state.bootBackdropUrls.isNotEmpty() &&
            state.activeProfile != null &&
            state.sources.isNotEmpty()
        ) {
            isShowingPosterReveal = true
            withTimeoutOrNull(BOOT_POSTER_REVEAL_TIMEOUT_MILLIS) {
                val loader = SingletonImageLoader.get(platformContext)
                // Fetched together rather than one after another: these are the handful of images
                // the first screen shows, and serialising them would make the wait the sum of every
                // request instead of the slowest one.
                coroutineScope {
                    state.bootBackdropUrls.take(BOOT_POSTER_PREFETCH_COUNT).map { url ->
                        async {
                            runCatching {
                                loader.execute(
                                    ImageRequest.Builder(platformContext)
                                        .data(url)
                                        // An explicit size, because this request has no view and
                                        // no layout to measure itself against. Without one the
                                        // request waits for a size that never arrives: it never
                                        // completes and never fails, so `awaitAll` below hangs
                                        // until the timeout and the wall's own covers queue behind
                                        // it. Roughly the poster size the wall draws at.
                                        .size(BOOT_POSTER_PIXEL_WIDTH, BOOT_POSTER_PIXEL_HEIGHT)
                                        .build(),
                                )
                            }
                        }
                    }.awaitAll()
                }
            }
            // A short beat after the artwork lands, so the reveal reads as a considered transition
            // rather than a flash. Kept small because the wait above is now the real cost.
            delay(BOOT_POSTER_SETTLE_MILLIS)
            isShowingPosterReveal = false
        }
    }

    // The first ON_RESUME can happen before the asynchronous licence check reaches Blocked. Keying
    // this effect to the resulting device id guarantees that a same-device reinstall is restored
    // on its first screen, while the lifecycle hook below catches later returns from Play.
    LaunchedEffect(blockedLicense?.deviceId) {
        blockedLicense?.let { onRestore(it.deviceId) }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        blockedLicense?.let { blocked ->
            viewModel.refreshLicense()
            onRestore(blocked.deviceId)
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let(viewModel::importPlaylist)
    }

    // A shared title this list does not carry. Said once, as a toast, rather than as a screen: the
    // user tapped a link expecting a film, and the useful thing is to tell them plainly and leave
    // them wherever they already were.
    LaunchedEffect(state.sharedTitleMissing) {
        if (state.sharedTitleMissing) {
            Toast.makeText(activity, R.string.share_title_not_in_library, Toast.LENGTH_LONG).show()
            viewModel.dismissSharedTitleNotice()
        }
    }

    when {
        !AppLocaleController.hasSelection(activity) -> LanguageSelectionScreen(
            languages = AppLocaleController.supportedLanguages,
            onSelect = { tag -> AppLocaleController.applySelection(activity, tag) },
        )

        state.isInitializing ->
            BuroBootScreen(
                R.string.boot_preparing,
                state.bootStage,
                state.bootBackdropUrls,
                state.downloadBytesPerSecond,
            )

        !state.hasAcceptedLegalNotice -> LegalOnboardingScreen(
            onAccept = viewModel::acceptLegalNotice,
        )

        state.license is LicenseUiState.NotChecked || state.license is LicenseUiState.Checking ->
            BuroBootScreen(
                R.string.license_checking,
                backdropPosters = state.bootBackdropUrls,
                downloadBytesPerSecond = state.downloadBytesPerSecond,
            )

        state.license is LicenseUiState.Blocked -> {
            val license = state.license as LicenseUiState.Blocked
            LicenseGateScreen(
                state = license,
                onPurchase = onPurchase,
                onRetry = viewModel::refreshLicense,
                onRedeem = viewModel::redeemLicense,
                onInspectKey = viewModel::inspectKey,
                backdropPosters = state.bootBackdropUrls,
            )
        }

        state.isProfilesLoading ->
            BuroBootScreen(
                R.string.boot_preparing,
                state.bootStage,
                state.bootBackdropUrls,
                state.downloadBytesPerSecond,
            )

        // Held until the catalogue has actually produced something to show.
        //
        // The screen used to disappear the moment the profile list arrived, which on a catalogue of
        // forty thousand items left the user looking at an empty home for several seconds — the app
        // appeared to have opened onto nothing. A returning user with a configured source is
        // precisely the case where the wait is longest and the reassurance most useful.
        state.activeProfile != null &&
            state.sources.isNotEmpty() &&
            (state.bootStage != BootStageUi.READY || isShowingPosterReveal) ->
            BuroBootScreen(
                R.string.boot_preparing,
                state.bootStage,
                state.bootBackdropUrls,
                state.downloadBytesPerSecond,
            )

        state.activeProfile == null -> ProfilePickerScreen(
            profiles = state.profiles,
            onSelect = viewModel::selectProfile,
            onCreate = viewModel::createProfile,
            // The sign-in gate offers the playlist too: a new profile created here should be able
            // to reuse one already configured, which is the whole point of per-profile lists.
            // Editing and deleting stay absent — choosing who is watching is not the moment to
            // rename or remove somebody.
            sources = state.sources,
            onSelectProfileSource = viewModel::selectProfileSource,
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
                isFavorite = playerContent.channel.id in state.favoriteIds,
                // Live television is excluded: a channel is not a title someone returns to from a
                // favourites list, and the domain files favourites by content identity.
                onToggleFavorite =
                    if (playerContent.channel.contentType == CatalogContentType.LIVE) {
                        null
                    } else {
                        { viewModel.toggleChannelFavorite(playerContent.channel) }
                    },
                subtitles = state.subtitles,
                liveSchedule = state.liveSchedule,
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
                onFocusGuideChannel = viewModel::focusGuideChannel,
                onHeroTrailerFor = viewModel::heroTrailerFor,
                bannerTrailerSound = state.bannerTrailerSound,
                onToggleBannerTrailerSound = viewModel::toggleBannerTrailerSound,
                onLoadHeroTrailer = viewModel::loadHeroTrailer,
                onSearch = viewModel::search,
                onPlayMovie = viewModel::playSelectedMovie,
                onToggleMovieFavorite = viewModel::toggleSelectedMovieFavorite,
                onToggleChannelFavorite = viewModel::toggleChannelFavorite,
                onToggleReminder = viewModel::toggleReminder,
                onDiscoverKeep = viewModel::keepDiscoveryCard,
                onDiscoverSkip = viewModel::skipDiscoveryCard,
                onDiscoverDealAgain = viewModel::dealDiscoveryDeck,
                onStartCastReceiver = viewModel::startCastReceiver,
                onStopCastReceiver = viewModel::stopCastReceiver,
                onRemoveReminder = viewModel::removeReminder,
                onOpenReminder = viewModel::openReminder,
                onSetReminderNotify = viewModel::setReminderNotify,
                onSetReminderTime = viewModel::setReminderTime,
                onShareTitle = { request ->
                    shareTitle(
                        activity = activity,
                        kind = request.kind,
                        title = request.title,
                        year = request.year,
                        artworkUrl = request.artworkUrl,
                        description = request.description,
                    )
                },
                onCastTitle = viewModel::openCast,
                onSubscriptionSeeMore = viewModel::expandService,
                onSubscriptionCloseExpanded = viewModel::closeExpandedService,
                onMarkNotificationsRead = viewModel::markNotificationsRead,
                onDismissNotification = viewModel::dismissNotification,
                onClearNotifications = viewModel::clearNotifications,
                onChooseCacheBudget = viewModel::chooseCacheBudget,
                onDeclineCacheOffer = viewModel::declineCacheOffer,
                onStartCacheFill = viewModel::startCacheFill,
                onStopCacheFill = viewModel::stopCacheFill,
                onRefreshCacheFill = viewModel::refreshCacheFill,
                onClearCache = viewModel::clearArtworkCache,
                onCastSearchAgain = viewModel::searchForScreens,
                onCastConnectToAddress = viewModel::connectToScreenAt,
                onCastChoose = viewModel::chooseCastTarget,
                onCastBack = viewModel::backToCastTargets,
                onCastSend = viewModel::sendToCastTarget,
                onCastClose = viewModel::closeCast,
                onOpenPerson = viewModel::openPerson,
                onRequestCastPhotos = viewModel::ensureCastPhotos,
                onCatalogueFilterChange = viewModel::setCatalogueFilter,
                onCatalogueLayoutChange = viewModel::setCatalogueLayout,
                onOpenEpisode = viewModel::openEpisode,
                onDownloadMovie = viewModel::downloadSelectedMovie,
                onDownloadEpisode = viewModel::downloadEpisode,
                onDownloadSeason = viewModel::downloadSeason,
                onDownloadSeries = viewModel::downloadWholeSeries,
                onCancelDownload = viewModel::cancelDownload,
                onDeleteDownload = viewModel::deleteDownload,
                onPlayDownload = viewModel::playDownload,
                onSelectProfile = viewModel::selectProfile,
                onCreateProfile = viewModel::createProfile,
                availableAvatars = viewModel.availableAvatars(),
                onUpdateProfile = viewModel::updateProfile,
                onDeleteProfile = viewModel::deleteProfile,
                onSelectProfileSource = viewModel::selectProfileSource,
                onRequestProfileSelection = viewModel::requestProfileSelection,
                onSaveTmdbKey = viewModel::saveTmdbKey,
                onOpenPurchase = { deviceId ->
                    val language = AppLocaleController.selectedLanguageTag(activity)
                    val uri = Uri.parse(AndroidLicenseEndpoints.purchaseUrl(deviceId, language))
                    runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                    Unit
                },
                onRedeemLicense = viewModel::redeemLicense,
                onSaveSharedTmdbKey = viewModel::saveSharedTmdbKey,
                onSaveCriticsKey = viewModel::saveCriticsKey,
                onSelectSubscriptionKind = viewModel::selectSubscriptionKind,
                onSelectSubscriptionRegion = viewModel::selectSubscriptionRegion,
                onOpenSubscriptionTitle = viewModel::openSubscriptionTitle,
                onCloseSubscriptionTitle = viewModel::closeSubscriptionTitle,
                onOpenSubscriptionOffer = { offer ->
                    // The user's own copy opens in the app; every other row is a signpost to
                    // somebody else's service and goes through the launcher's safety checks.
                    val localId = offer.localContentId
                    if (offer.isUserLibrary && localId != null) {
                        viewModel.openChannelById(localId)
                    } else {
                        openStreamingOffer(activity, offer)
                    }
                },
                onOpenPersonCredit = viewModel::openPersonCredit,
                onOpenProviderShortcut = viewModel::openProviderShortcut,
                onSelectLanguage = { tag -> AppLocaleController.applySelection(activity, tag) },
                guard =
                    CatalogueGuardUi(
                        hasPin = state.hasParentalPin,
                        lockAdultCategories = state.parentalLock.lockAdultCategories,
                        pinMessage =
                            state.parentalMessage?.let { message ->
                                stringResource(
                                    when (message) {
                                        ParentalMessage.BAD_FORMAT -> R.string.parental_pin_format
                                        ParentalMessage.WRONG_PIN -> R.string.parental_wrong_pin
                                    },
                                )
                            },
                        subtitles = state.subtitles,
                        categories =
                            state.allCategories.mapNotNull { category ->
                                category.id?.let { id ->
                                    GuardCategoryUi(
                                        id = id,
                                        name = category.name,
                                        // Blank when the count was never fetched, which is the
                                        // case for this list; the row then simply omits the line.
                                        sectionLabel =
                                            if (category.channelCount > 0) {
                                                stringResource(
                                                    R.string.categories_channel_count,
                                                    category.channelCount,
                                                )
                                            } else {
                                                ""
                                            },
                                    )
                                }
                            },
                        hiddenIds = state.hiddenCategoryIds,
                        lockedIds = state.parentalLock.lockedCategoryIds,
                        onSetPin = viewModel::setParentalPin,
                        onClearPin = viewModel::clearParentalPin,
                        onLockAdultChange = viewModel::setLockAdultCategories,
                        onSubtitlesChange = viewModel::saveSubtitlePresentation,
                        onHiddenChange = viewModel::setCategoryHidden,
                        onLockedChange = viewModel::setCategoryLocked,
                    ),
                onSubmitParentalPin = viewModel::submitParentalPin,
                onDismissParentalPrompt = viewModel::dismissParentalPrompt,
                onLoadMore = viewModel::loadMoreChannels,
                onRetryCatalog = viewModel::retryCatalog,
                onRefreshCatalog = viewModel::refreshCatalog,
                onRunDiagnostics = viewModel::runDiagnostics,
                onToggleMergeSources = viewModel::setMergeEverySource,
                onOpenHomeItem = viewModel::openStory,
                onRememberHomeFocus = viewModel::rememberLastFocusedHomeItem,
                onBack = { viewModel.goBack() },
            )
        }
    }
}

@Composable
private fun BuroBootScreen(
    messageResource: Int,
    stage: BootStageUi? = null,
    backdropPosters: List<String> = emptyList(),
    downloadBytesPerSecond: Long? = null,
) {
    Box(
        modifier = Modifier.fillMaxSize().background(BuroCanvas),
    ) {
        BuroCinematicBackdrop(posterUrls = backdropPosters)
        Column(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .safeDrawingPadding()
                    .padding(horizontal = 24.dp, vertical = 28.dp)
                    .widthIn(max = 520.dp)
                    .background(BuroCanvas.copy(alpha = 0.88f), androidx.compose.foundation.shape.RoundedCornerShape(28.dp))
                    .padding(horizontal = 28.dp, vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "IPTV  BURO",
                color = BuroTextPrimary,
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(messageResource),
                color = BuroTextSecondary,
                fontSize = 16.sp,
            )
            // What is actually happening, not just that something is. A catalogue of tens of
            // thousands of items takes long enough that an unlabelled spinner reads as a hang.
            stage?.let { current ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(current.labelResource()),
                    color = BuroGold,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
            }
            // How fast it is arriving, when anything is.
            //
            // A long wait with no figure cannot be told apart from a stuck one, which is the
            // question somebody staring at this screen actually has. Omitted rather than shown as
            // zero when there is nothing to report: a zero would answer that question wrongly.
            formatDownloadRate(downloadBytesPerSecond)?.let { rate ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.download_rate, rate),
                    color = BuroTextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(22.dp))
            CircularProgressIndicator(
                color = BuroGold,
            )
        }
    }
}

private fun BootStageUi.labelResource(): Int =
    when (this) {
        BootStageUi.PROFILES -> R.string.boot_stage_profiles
        BootStageUi.CATALOGUE -> R.string.boot_stage_catalogue
        BootStageUi.ARTWORK -> R.string.boot_stage_artwork
        BootStageUi.READY -> R.string.boot_stage_ready
    }

/**
 * The longest the loading screen will wait for artwork before opening anyway.
 *
 * A ceiling, not a target: on a warm cache the wait is imperceptible. It exists so a slow or
 * failing network degrades to the old behaviour — open now, fill in as you go — instead of holding
 * somebody on a loading screen indefinitely.
 */
private const val BOOT_POSTER_REVEAL_TIMEOUT_MILLIS = 6_000L

/** A beat after the artwork lands, so the reveal reads as a transition rather than a flash. */
private const val BOOT_POSTER_SETTLE_MILLIS = 220L

/** How many of the first screen's images are waited for. The hero and the first row, in practice. */
private const val BOOT_POSTER_PREFETCH_COUNT = 6

/** The size the loading screen's covers are decoded at; see the prefetch above. */
private const val BOOT_POSTER_PIXEL_WIDTH = 232

private const val BOOT_POSTER_PIXEL_HEIGHT = 348

package com.lucasserafin94.iptvburo.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import com.lucasserafin94.iptvburo.desktop.user.DesktopLanguage

/**
 * Every user-visible string on Windows.
 *
 * Modelled as a data class rather than a `Map<String, String>` on purpose: a missing translation is
 * a compile error instead of a runtime `NoSuchElementException`, and the tables are allocated once
 * at class-init instead of on every recomposition.
 */
/**
 * Strings for the settings screen.
 *
 * A group rather than more fields on [DesktopStrings]: that class's constructor had reached the
 * JVM's 254-argument ceiling and any further addition broke the app at class load. New settings
 * strings belong here.
 */
/**
 * Strings for the activation screen.
 *
 * Its own group for the same reason as [SettingsStrings]: the main constructor is at the JVM's
 * argument ceiling. Grouped separately from settings besides, because this screen is shown *instead
 * of* the app rather than inside it, and the two are edited for different reasons.
 */
data class LicenseStrings(
    val trialTitle: String,
    val trialBody: String,
    val expiredTitle: String,
    val expiredBody: String,
    val revokedTitle: String,
    val revokedBody: String,
    val unreachableTitle: String,
    val unreachableBody: String,
    val verifyTitle: String,
    val verifyBody: String,
    val deviceLabel: String,
    /** The key this installation redeemed, shown so its owner can keep a copy. */
    val activationKeyLabel: String,
    /** Says plainly that losing the key means buying another. */
    val activationKeyHint: String,
    val macLabel: String,
    val scanHint: String,
    val openInBrowser: String,
    val retry: String,
    val haveKey: String,
    val keyPlaceholder: String,
    val redeem: String,
    val redeemFailed: String,
    /** What the server says a typed key is, before it is spent. */
    val keyAvailable: String,
    val keyAvailableDays: (Int) -> String,
    val keyYours: String,
    val keyInUse: String,
    val keyExpired: String,
    val quit: String,
    /** Leaves this screen while there is still a working app behind it. */
    val back: String,
    /** Returns from the code field to the QR code and price. */
    val backToPurchase: String,
    /** The term beside the price: "2 anos". Formatted with %d years. */
    val termYears: String,
    /**
     * The three prices, chosen by the machine's country rather than its language.
     *
     * Identical in all four translations, and deliberately so: "R$ 99,90" is the same string to a
     * German as to a Brazilian. What changes is only the word for the term beside it, which is why
     * these carry it. Keeping them per-language rather than as constants means a translator can fix
     * "2 anos" without touching the number.
     */
    val priceEur: String,
    val priceUsd: String,
    val priceBrl: String,
    val whyNotLifetime: String,
    val copied: String,
    val clockWarning: String,
    /** Shown in the app while a trial is still running, so its end is never a surprise. */
    val trialDaysLeft: String,
    /**
     * The same countdown for a paid licence, shown only in its final month.
     *
     * Formatted with `%d`. A translation that drops the placeholder prints a sentence with no
     * number in it, and does so only in that one language — which is why a test checks for it.
     */
    val licenseDaysLeft: String,
    val licenseLastDay: String,
    val trialLastDay: String,
    val buyNow: String,
)

data class SettingsStrings(
    /** Editing a profile from the gate: rename, avatar, Kids, and which playlist it signs in to. */
    val profileEdit: String,
    val profileEditTitle: String,
    val profileNameLabel: String,
    val profileAvatarLabel: String,
    val profileKidsLabel: String,
    val profileKidsHint: String,
    val profileSourceLabel: String,
    val profileSourceNone: String,
    val profileSourceChange: String,
    val profileMusicLabel: String,
    val profileMusicNone: String,
    val profileMusicChoose: String,
    val profileMusicClear: String,
    val profileSave: String,
    val expandSidebar: String,
    val subtitlesLabel: String,
    val subtitlesHint: String,
    val subtitlesBackground: String,
    val historyTitle: String,
    val historyClearAll: String,
    val historyEmpty: String,
    val categoriesLabel: String,
    val categoriesHint: String,
    val categoryHide: String,
    val categoryLock: String,
    val clockLabel: String,
    val clockHint: String,
    val clock24h: String,
    val clock12h: String,
    val parentalTitle: String,
    val parentalHint: String,
    val parentalSetPin: String,
    val parentalChangePin: String,
    val parentalRemovePin: String,
    val parentalCurrentPin: String,
    val parentalNewPin: String,
    val parentalWrongPin: String,
    val parentalPinSaved: String,
    /**
     * Says the profile is still on the shipped PIN.
     *
     * Shown because 0000 is public knowledge. The lock is real and works from the first launch;
     * this is what stops it being mistaken for a secret.
     */
    val parentalDefaultPin: String,
    val parentalPinFormat: String,
    val parentalLockAdult: String,
    val parentalLocked: String,
    /** Opens the rest of the day's schedule for a live channel. */
    val epgShowSchedule: String,
    val epgHideSchedule: String,
    val parentalUnlock: String,
    /** Shown only on the first run, where the wait is longest and needs explaining. */
    val firstRunTitle: String,
    val firstRunBody: String,
    /** The TMDb key: what it is for, that it is free, and how to get one. */
    val firstRunTmdbTitle: String,
    val firstRunTmdbBody: String,
    /**
     * What the preparation screen is doing right now.
     *
     * These were Portuguese string literals in DesktopAppState — invisible while only a returning
     * Brazilian user saw them, and wrong the moment the preparation screen became something all
     * four languages meet on their first launch.
     */
    val startupAuthenticating: String,
    val startupOrganising: String,
    /** The per-profile TMDb key: what it is for, and that leaving it empty shares the other one. */
    val profileKeyLabel: String,
    val profileKeyHint: String,
    val profileKeyShared: String,
    val profileKeyOwn: String,
    /**
     * The button that opens the settings dialog.
     *
     * Named for what it opens rather than for one thing inside it: the dialog also holds subtitle
     * appearance, the clock format, category hiding and the history controls, and calling the
     * button "Controle dos pais" hid all of that behind a name that sounded like it did not apply.
     */
    val moreSettingsTitle: String,
    val moreSettingsHint: String,
    /**
     * Multiview, on the live tab only.
     *
     * Four films at once is not something anyone wants; four matches at once is what a second
     * screen normally gets used for, which is why this exists nowhere else.
     */
    val multiviewAdd: String,
    val multiviewRemove: String,
    val multiviewOpen: String,
    /** Shown when every queued channel failed to produce a playable stream. */
    /** The toolbar label before anything is queued: an instruction, not an action. */
    val multiviewHint: String,
    /** The audio picker and the screen-size controls on the multiview bar. */
    /** The music workshop: cleaning up names a playlist got from filenames. */
    val musicWorkshop: String,
    val musicWorkshopSummary: String,
    val musicWorkshopNames: String,
    val musicWorkshopDuplicates: String,
    val musicWorkshopApplyAll: String,
    val musicWorkshopApplyOne: String,
    val musicWorkshopUndoAll: String,
    val musicWorkshopNothingToFix: String,
    val musicWorkshopNoDuplicates: String,
    val musicWorkshopSameAddress: String,
    val musicWorkshopSameName: String,
    val multiviewAudioFrom: String,
    val multiviewFullScreen: String,
    val multiviewWindowed: String,
    val multiviewUnavailable: String,
    val multiviewUnavailableHint: String,
    /** Shown when the grid is opened before any channel has been added. */
    val multiviewEmpty: String,
    val multiviewEmptyHint: String,
    val multiviewClear: String,
    val multiviewFull: String,
    /**
     * The licence gate.
     *
     * This screen is where someone decides whether to pay or close the app for ever, so the wording
     * matters commercially rather than only cosmetically. Two rules run through it: never ask for
     * money when the problem is a connection, and answer "why is this not lifetime?" before it is
     * asked, because the competing products in this market all say lifetime.
     */
    val licenseTrialOverTitle: String,
    val licenseTrialOverBody: String,
    val licenseExpiredTitle: String,
    val licenseExpiredBody: String,
    val licenseRevokedTitle: String,
    val licenseRevokedBody: String,
    val licenseVerifyTitle: String,
    val licenseVerifyBody: String,
    val licenseOfflineTitle: String,
    val licenseOfflineBody: String,
    val licenseTwoYears: String,
    val licenseWhyNotLifetime: String,
    val licenseBuy: String,
    val licenseRetry: String,
    val licenseDeviceLabel: String,
    val licenseHaveKey: String,
    val licenseKeyPlaceholder: String,
    val licenseRedeem: String,
    val licenseDaysLeft: String,
)

data class DesktopStrings(
    // Shell
    val library: String,
    val home: String,
    val live: String,
    val movies: String,
    val series: String,
    /**
     * Sharing a title. Grouped rather than flat, and not only for tidiness: this class is a
     * constructor with one parameter per string, and the JVM caps a method signature at 255 slots.
     * Adding ten more fields at the top level pushed it past that limit and every test in the
     * module failed to load with `ClassFormatError: Too many arguments in method signature` —
     * before running a single assertion. Nested groups are how the file stays under the ceiling.
     */
    val shareStrings: ShareStrings,
    val downloadStrings: DownloadStrings,
    val savedForLater: SavedForLaterStrings,
    val search: String,
    val sources: String,
    val profile: String,
    val yourLibrary: String,
    val connectXtream: String,
    val importM3u: String,
    val checkUpdate: String,
    val refreshCatalog: String,
    val termsTitle: String,
    val termsNoContent: String,
    val termsYourSource: String,
    val termsResponsibility: String,
    /** What the app does not promise, which is as much a protection for the buyer as the seller. */
    val termsNoWarranty: String,
    /** One licence, one machine — stated before the sale rather than discovered after it. */
    val termsOneDevice: String,
    /** The refund position, and the statutory right that overrides it in Brazil. */
    val termsRefund: String,
    /** Link out to the full document, which is where the detail lives. */
    val termsReadFull: String,
    val termsAccept: String,
    val setupTitle: String,
    val setupSubtitle: String,
    val setupProfileName: String,
    val setupUseExisting: String,
    val setupYourList: String,
    val setupNewList: String,
    val setupListName: String,
    val serverLabel: String,
    val usernameLabel: String,
    val passwordLabel: String,
    val setupContinue: String,
    val connectingTitle: String,
    val connectingBody: String,
    val setupFailedTitle: String,
    val setupFailedBody: String,
    val setupRetry: String,
    val chooseRating: String,
    val anyRating: String,
    val continueEmptyTitle: String,
    val continueEmptyBody: String,
    val resumeFrom: String,
    val startOver: String,
    val forgetProgress: String,
    val metadataKeyLabel: String,
    /** The TMDb key walkthrough, grouped so this constructor stays under the JVM argument cap. */
    val tmdbGuide: TmdbGuideStrings,
    val metadataKeyHint: String,
    val metadataKeyPlaceholder: String,
    val layoutPoster: String,
    val layoutCompact: String,
    val layoutList: String,
    val removeProfile: String,
    val confirmRemoveProfile: String,
    val showPassword: String,
    val hidePassword: String,
    val avatarUsePhoto: String,
    val avatarRemovePhoto: String,
    val avatarChoosePhotoTitle: String,
    val checkingUpdate: String,
    val upToDate: String,
    val downloading: String,
    val installerVerified: String,
    val updateFailed: String,
    val privateSession: String,
    val nothingSensitiveSaved: String,
    // Home
    val dailySelection: String,
    val continueWatching: String,
    val moviesForToday: String,
    val seriesToExplore: String,
    val liveNow: String,
    val heroFallbackTitle: String,
    val heroSubtitle: String,
    val watchNow: String,
    val details: String,
    val options: String,
    val organizingToday: String,
    /**
     * Badge on the seasonal rail. The rail's own name ("Especial de Natal") is not here: it is data
     * carried by the seasonal collection, because the set of occasions grows with the calendar and
     * a new one must not force a new field on all four language blocks.
     */
    val seasonalBadge: String,
    val tryAgain: String,
    val watched: String,
    val onAir: String,
    // States
    val emptyHeadline: String,
    val emptyBody: String,
    val emptyBadge: String,
    val credentialsStayLocal: String,
    val authenticating: String,
    val organizingPlaylist: String,
    val noSensitiveData: String,
    val close: String,
    val cancel: String,
    val understood: String,
    val whoIsWatching: String,
    val newProfile: String,
    val addProfile: String,
    val kidsProfile: String,
    val adultProfile: String,
    val forgetSource: String,
    val searchChannel: String,
    val results: String,
    val items: String,
    val sourcesCount: String,
    val selectChannel: String,
    val vaultProtected: String,
    val credentialsEncrypted: String,
    // Catalogue workspace
    val endSession: String,
    val searchCatalog: String,
    val categories: String,
    val allCategories: String,
    val catalog: String,
    val noMatch: String,
    val previous: String,
    val next: String,
    val page: String,
    /** The "no filter" option in a type selector: everything, films and series together. */
    /** Shown when a filter or a search matches none of the rows on screen. */
    val downloadsNoMatch: String,
    val allItems: String,
    val allYears: String,
    val releasesIn: String,
    val sessionActive: String,
    val loadingCatalog: String,
    val sessionClosed: String,
    val backToCatalog: String,
    val selectItem: String,
    val download: String,
    val downloadInProgress: String,
    val downloaded: String,
    val downloadFailed: String,
    val removeDownload: String,
    val downloads: String,
    val resetSettings: String,
    val resetConfirm: String,
    val resetWarning: String,
    val settings: String,
    val languageLabel: String,
    val chooseYear: String,
    val downloadPaused: String,
    val downloadsEmptyTitle: String,
    val downloadsEmptyBody: String,
    val updateReadyBody: String,
    val updateRestartNow: String,
    val updateLater: String,
    // Music
    val music: String,
    val musicHome: String,
    val musicArtists: String,
    val musicRadio: String,
    val musicNewReleases: String,
    val musicMostPlayed: String,
    val musicTracks: String,
    val musicStations: String,
    val musicAddPlaylist: String,
    val musicEmptyTitle: String,
    val musicEmptyBody: String,
    val musicNoArtists: String,
    val musicNoRadio: String,
    val musicNoDownloads: String,
    val musicBackToArtists: String,
    val musicPlaylistLabel: String,
    val musicPlaylistOptional: String,
    val musicPlaylistHint: String,
    val musicPlaylistChoose: String,
    val musicPlaylistRemove: String,
    val musicPlaylistTitle: String,
    // Playlists and listening history (GDD 8 sections 17 and 18).
    val musicPlaylists: String,
    val musicPlaylistsEmpty: String,
    val musicPlaylistNew: String,
    val musicPlaylistNewName: String,
    val musicPlaylistRename: String,
    val musicPlaylistDelete: String,
    val musicPlaylistDuplicate: String,
    val musicPlaylistDuplicateSuffix: String,
    val musicPlaylistImport: String,
    val musicPlaylistExport: String,
    val musicPlaylistBack: String,
    val musicPlaylistEmptyTracks: String,
    val musicPlaylistRemoveTrack: String,
    val musicPlaylistMoveUp: String,
    val musicPlaylistMoveDown: String,
    val musicSmartPlaylists: String,
    val musicSmartFavourites: String,
    val musicSmartRecentlyPlayed: String,
    val musicSmartMostPlayed: String,
    val musicSmartNeverPlayed: String,
    val musicSmartRecentlyAdded: String,
    val musicExportWarningTitle: String,
    val musicExportWarningBody: String,
    val musicExportWarningConfirm: String,
    val musicExportWarningCancel: String,
    // Playback queue (GDD 8 section 16)
    val queueTitle: String,
    val queueNowPlaying: String,
    val queueUpNext: String,
    val queueEmptyBody: String,
    val queuePlayNow: String,
    val queuePlayNext: String,
    val queueAddToEnd: String,
    val queueRemove: String,
    val queueClear: String,
    val queueMoveUp: String,
    val queueMoveDown: String,
    val queueOpen: String,
    val queueClose: String,
    val queueCount: String,
    // Streaming discovery (GDD 9)
    val subscriptions: String,
    val subscriptionsDemoBadge: String,
    val subscriptionsDemoNotice: String,
    val subscriptionsWhereToWatch: String,
    val subscriptionsInYourLibrary: String,
    val subscriptionsIncludedInSubscription: String,
    val subscriptionsFreeWithAds: String,
    val subscriptionsRent: String,
    val subscriptionsBuy: String,
    val subscriptionsRequiresSubscription: String,
    val subscriptionsUnavailable: String,
    val subscriptionsOpenProvider: String,
    val subscriptionsMyServices: String,
    val subscriptionsRegion: String,
    /** Explains what each settings section actually controls — the labels alone read as synonyms. */
    val languageHint: String,
    val regionHint: String,
    val metadataKeyUses: String,
    /** Confirms a pasted key took effect — there is no Save button, and silence looks like failure. */
    val metadataKeySaved: String,
    val metadataKeyUsingBundled: String,
    /**
     * Settings-screen strings, grouped rather than listed flat.
     *
     * The JVM caps a method at 254 arguments, and this class's constructor reached it — the app
     * compiled and then died on class load with "Too many arguments in method signature". Grouping
     * spends one parameter instead of twenty-one, and there is room to grow again.
     */
    val settingsText: SettingsStrings,
    val licenseText: LicenseStrings,
    val subscriptionsSynopsis: String,
    val subscriptionsCast: String,
    val subscriptionsWatchTrailer: String,
    val subscriptionsAvailableOn: String,
    val subscriptionsFilterMovies: String,
    val subscriptionsFilterSeries: String,
    val subscriptionsFilterUpcoming: String,
    val subscriptionsFilterThisWeek: String,
    /**
     * The caveat on the upcoming shelf.
     *
     * TMDb dates a release but does not say which service will carry it, so a shelf under a
     * provider's name means "out soon", not "coming to this service". Saying so is the difference
     * between informing the user and misleading them.
     */
    val subscriptionsUpcomingNote: String,
    val subscriptionsEmptyBody: String,
    // Browse-by-service shelves (GDD 9)
    val subscriptionsBrowseByService: String,
    val subscriptionsNoShelves: String,
    val subscriptionsLoadFailed: String,
    /**
     * Shown instead of [subscriptionsLoadFailed] when TMDb rejected the key.
     *
     * Names the real cause and where to fix it. The generic message told people to check a
     * connection that was working, which is the whole reason this string exists.
     */
    val subscriptionsKeyRejected: String,
    val subscriptionsBackToServices: String,
    val subscriptionsSelectedTitle: String,
) {
    companion object {
        fun of(language: DesktopLanguage): DesktopStrings =
            when (language) {
                DesktopLanguage.PORTUGUESE_BRAZIL -> PtBr
                DesktopLanguage.ENGLISH -> En
                DesktopLanguage.GERMAN -> De
                DesktopLanguage.ITALIAN -> It
                DesktopLanguage.SPANISH -> Es
            }

        private val Es =
            DesktopStrings(
                library = "BIBLIOTECA",
                home = "Inicio",
                live = "En vivo",
                movies = "Películas",
                series = "Series",
                downloadStrings =
                    DownloadStrings(
                        downloadSeries = "Descargar serie",
                        downloadSeason = "Descargar temporada %d",
                        downloadSeriesConfirmTitle = "¿Descargar la serie completa?",
                        downloadSeasonConfirmTitle = "¿Descargar la temporada %d?",
                        downloadConfirmBody = "Se descargarán %d episodios. Esto puede usar mucho espacio y datos.",
                        downloadConfirmAction = "Descargar",
                    ),
                savedForLater =
                    SavedForLaterStrings(
                        favorites = "Favoritos",
                        reminderAdd = "Recordatorio",
                        reminderActive = "Recordatorio activo",
                        reminderNoNotice = "Guardado en este equipo. El aviso aparece aquí en la app.",
                        reminderAnnounce = "Avisarme de mis recordatorios",
                        reminderHourLabel = "Avisar a las",
                        reminderInAppOnly =
                            "El aviso aparece dentro de la app, la primera vez que la abras " +
                                "después de la hora elegida. Con la app cerrada no envía " +
                                "notificaciones de Windows.",
                        reminderNoticeBody = "Tienes %d título(s) marcado(s) para ver.",
                        reminderNoticeDismiss = "Entendido",
                        remindersTitle = "Recordatorios",
                        remindersEmpty =
                            "Todavía no has marcado nada. Usa el botón Recordatorio en una " +
                                "película, una serie o un estreno en Suscripciones.",
                        reminderOpen = "Abrir",
                        reminderRemove = "Quitar",
                        reminderNotInLibrary = "Aún no está en tu lista",
                        newEpisodeBody = "Nuevo episodio: T%1${'$'}d E%2${'$'}d",
                        newSeasonBody = "Nueva temporada: T%1${'$'}d",
                    ),
                shareStrings =
                    ShareStrings(
                        cast =
                            CastStrings(
                                            castAction = "Enviar a pantalla",
                                            castTitle = "Enviar a una pantalla",
                                            castSearching = "Buscando pantallas en esta red…",
                                            castNoneFound = "No se encontró ninguna pantalla. Abre el IPTV BURO en el otro equipo, activa «Recibir» y comprueba que ambos estén en la misma red.",
                                            castManualTitle = "O escribe la dirección",
                                            castManualHint = "Algunos routers bloquean la búsqueda entre dispositivos. La otra pantalla muestra su dirección en «Recibir».",
                                            castManualLabel = "Dirección en esta red",
                                            castManualConnect = "Conectar",
                                            castManualInvalid = "Eso no parece una dirección de esta red.",
                                            castSearchAgain = "Buscar de nuevo",
                                            castCodePrompt = "Enviar a %s",
                                            castCodeHint = "Escribe los cuatro dígitos que aparecen en esa pantalla.",
                                            castCodeInvalid = "El código tiene cuatro dígitos.",
                                            castSend = "Enviar",
                                            castSending = "Enviando a %s…",
                                            castSent = "Enviado a %s. Si no empieza, comprueba el código en esa pantalla.",
                                            castFailed = "No se pudo contactar con %s.",
                                            castChooseAnother = "Elegir otra pantalla",
                            ),
                    notifications =
                        NotificationStrings(
                            title = "Avisos",
                            empty = "Nada por aqui.",
                            clearAll = "Borrar todo",
                            dismiss = "Descartar",
                        ),
                    failures =
                        FailureStrings(
                            sessionExpired =
                                "La sesión de tu lista ha caducado. El catálogo sigue guardado, " +
                                    "pero hay que entrar de nuevo en la fuente para cargar novedades.",
                            outOfMemory =
                                "No hubo memoria suficiente para montar esta pantalla. " +
                                    "Es una limitación de la aplicación, no de tu lista.",
                            invalidServer = "La dirección del servidor no es válida.",
                            invalidServerScheme = "Revise el inicio de la dirección: debe ser http:// o https://.",
                            authenticationRejected = "El servidor rechazó el usuario o la contraseña.",
                            networkUnreachable = "No se pudo alcanzar el servidor.",
                            httpError = "El servidor respondió con un error HTTP.",
                            responseTooLarge = "El catálogo superó el límite seguro de esta versión.",
                            invalidResponse =
                                "El servidor no devolvió un catálogo Xtream compatible. Detalles en %1${'$'}s",
                            appFault =
                                "No se pudo montar esta pantalla (%1${'$'}s). Es un fallo de la " +
                                    "aplicación, no de tu lista. Detalles en %2${'$'}s",
                        ),
                    startup =
                        StartupStrings(
                            openingSession = "Abriendo tu sesión…",
                            joiningList = "Uniendo %1${'$'}s…",
                            loadingLiveCategories = "Cargando categorías de canales…",
                            loadingMovieCategories = "Cargando categorías de películas…",
                            loadingSeriesCategories = "Cargando categorías de series…",
                            downloadingMovies = "Descargando la lista de películas…",
                            downloadingSeries = "Descargando la lista de series…",
                            organising = "Organizando películas y series…",
                            ready = "Listo",
                        ),
                    screens =
                        ScreenStrings(
                            setupMissingProfileName = "Escriba el nombre del perfil para continuar",
                            setupMissingConnection = "Complete el servidor, el usuario y la contraseña, o elija una lista ya configurada",
                            diagnosticsAction = "Diagnóstico",
                            diagnosticsTitle = "Diagnóstico de conexión",
                            mergeSourcesTitle = "Unir todas las listas",
                            mergeSourcesHelp = "Muestra todas sus listas como un solo catálogo. Nada se repite: la lista más grande manda y las otras completan lo que falta.",
                            mergeSourcesRestart = "Las listas se reorganizan al momento.",
                            mergeSourcesFailed = "%1${'$'}s no respondió. Las demás listas siguen funcionando.",
                            mergeSourcesOffline = "No responde",
                            diagnosticsLatencyGood = "Latencia baja: los canales cambian sin espera",
                            diagnosticsLatencyFair = "Latencia alta: puede cortar y tardar al cambiar de canal",
                            diagnosticsLatencyUnstable = "Latencia muy alta: va a causar cortes y congelamientos",
                            diagnosticsLatencyUnknown = "No se pudo medir la latencia",
                            diagnosticsRunning = "Probando…",
                            diagnosticsRun = "Probar de nuevo",
                            diagnosticsClose = "Cerrar",
                            diagnosticsDownload = "Descarga",
                            diagnosticsUpload = "Subida",
                            diagnosticsPing = "Latencia",
                            diagnosticsLoss = "Pérdida de paquetes",
                            diagnosticsCatalogue = "Lista",
                            diagnosticsConnection = "Conexión",
                            diagnosticsMemory = "Memoria",
                            diagnosticsAddress = "Dirección IP",
                            diagnosticsGateway = "Puerta de enlace",
                            diagnosticsNetmask = "Máscara de red",
                            diagnosticsVerdictGood = "Todo bien",
                            diagnosticsVerdictWarning = "Atención",
                            diagnosticsVerdictProblem = "Problema encontrado",
                            diagnosticsQualityUnstable = "Conexión mala: puede causar cortes y congelamientos",
                            diagnosticsQualitySd = "Conexión justa: suficiente para calidad estándar",
                            diagnosticsQualityHd = "Conexión buena para películas en 1080p",
                            diagnosticsQualityUhd = "Conexión perfecta para 4K y TV en vivo",
                            diagnosticsQualityUnknown = "No se pudo medir la velocidad",
                            diagnosticsWireless = "Wi-Fi: el cable evita cortes que la señal inalámbrica causa",
                            diagnosticsWired = "Cable de red",
                            diagnosticsNoLink = "Sin conexión de red",
                            diagnosticsCatalogueEmpty = "La lista no cargó: revise la suscripción con quien se la vendió",
                            diagnosticsSignedOut = "Sin lista configurada",
                            diagnosticsLowMemory = "Poca memoria: cierre otros programas o reinicie la aplicación",
                            deviceCodeAction = "Código del dispositivo",
                            deviceCodeHelp = "Envía este código a quien te vendió la lista y podrá configurarla por ti.",
                            setupRenameList = "Renombrar",
                            setupRemoveList = "Eliminar",
                            setupRemoveListConfirm = "¿Eliminar la lista «%1${'$'}s»? La contraseña también se borra.",
                            importFileMissing = "El archivo seleccionado ya no existe.",
                            importAccessDenied = "El sistema no permitió leer ese archivo.",
                            importBlocked = "El acceso al archivo fue bloqueado por el sistema.",
                            importFailed = "No se pudo importar la lista. Comprueba que el archivo sea un M3U/M3U8 válido e inténtalo de nuevo.",
                            movieDetailsLoading = "Cargando la ficha de la película…",
                            epgLoading = "Cargando ahora y a continuación…",
                            guideNow = "Agora",
                            guideNext = "A seguir",
                            catchUpShow = "Rever (%1${'$'}s)",
                            catchUpHide = "Ocultar",
                            similarTitles = "Títulos parecidos",
                            epgUnavailable = "El guía no está disponible; el canal sigue accesible.",
                            epgEmpty = "La fuente no informó ninguna programación.",
                            loadEpisodes = "Cargar episodios",
                            episodesLoading = "Cargando episodios…",
                            externalOpenFailed = "No se pudo abrir",
                            externalNoDefaultApp = "Este sistema no ofrece una aplicación predeterminada para abrir el canal.",
                            externalRefused = "La aplicación externa rechazó la dirección. No se copió ningún dato.",
                            externalHeadersWarning = "El canal exige cabeceras HTTP; un navegador común puede no reproducirlo.",
                            externalAddressValid = "Dirección válida para una aplicación externa.",
                            headersUnsupported = "Este canal exige cabeceras HTTP que el reproductor de Windows aún no puede aplicar. La reproducción se desactivó para no mostrar un botón que fallará.",
                            noChannelMatches = "Ningún canal coincide con el filtro.",
                            connectXtreamTitle = "Conectar servidor Xtream",
                            searchingCatalogue = "Buscando en el catálogo…",
                            noFurtherTitles = "No se encontró ningún otro título en el catálogo.",
                            noPlayableEpisodes = "El servidor no devolvió episodios reproducibles.",
                            playerStopped = "El motor de vídeo se cerró inesperadamente.",
                            playerStartFailed = "El motor de vídeo de Windows no pudo iniciarse.",
                            playerStalled = "El servidor respondió, pero este vídeo no se inició. Inténtalo de nuevo o elige otro título.",
                            updateCheckFailed = "No se pudo comprobar si hay actualizaciones ahora.",
                            demoMovieNotice = "Título de ejemplo. Esta disponibilidad es ficticia.",
                            demoSeriesNotice = "Serie de ejemplo. Esta disponibilidad es ficticia.",
                        ),
                    remoteSource =
                        RemoteSourceStrings(
                            title = "Servidor propio (NAS)",
                            hint = "Lee una lista M3U guardada en tu servidor por WebDAV o FTP.",
                            addressLabel = "Dirección",
                            addressPlaceholder = "webdav://nas.local/media/lista.m3u",
                            userLabel = "Usuario (opcional)",
                            passwordLabel = "Contraseña (opcional)",
                            credentialsNotice = "Las credenciales se usan solo para esta lectura y no se guardan.",
                            connect = "Conectar",
                            cancel = "Cancelar",
                            unsupportedAddress = "Dirección no compatible. Usa webdav://, http:// o ftp://",
                        ),
                    ratings =
                        RatingStrings(
                            title = "Evaluaciones",
                            source = "Puntuación TMDb",
                            votes = "%s votos",
                            critics = "Críticos",
                            criticKeyLabel = "Clave OMDb (opcional)",
                            criticKeyHint = "Añade Tomatometer, Metascore y IMDb. Consíguela en omdbapi.com",
                            criticKeyPlaceholder = "Clave de la API",
                            criticKeySaved = "Clave guardada: se muestran las notas de la crítica.",
                            criticKeyAbsent = "Sin clave: solo se muestra la puntuación del público de TMDb.",
                            adultKeyTitle = "Carátulas de la guía adulta",
                            adultKeyBody = "TMDb no cubre este catálogo, así que las carátulas requieren una clave de ThePornDB. Sin ella, esas filas siguen mostrando el título. La clave es suya: no se envía a ningún otro sitio.",
                            adultKeyPlaceholder = "Clave de la API",
                            adultKeySaved = "Clave guardada: se buscarán las carátulas.",
                            adultKeyAbsent = "Sin clave: esas filas muestran solo el título.",
                            adultKeySite = "Obtener una clave en theporndb.net",
                            criticGuideButton = "¿No sabes cómo conseguirla?",
                            criticGuideTitle = "Cómo conseguir una clave OMDb",
                            criticGuideSubtitle = "Cuatro pasos. La clave gratuita llega por correo.",
                            criticGuideOpenSite = "Abrir omdbapi.com",
                            criticStep1Title = "Abre omdbapi.com",
                            criticStep1Body =
                                "Ve a la sección API Key del sitio. No hay cuenta que crear: solo " +
                                    "pide una dirección de correo.",
                            criticStep2Title = "Elige el plan gratuito",
                            criticStep2Body =
                                "Marca FREE, que permite 1.000 consultas al día, mucho más de lo que " +
                                    "usa esta aplicación. El plan de pago no hace falta.",
                            criticStep3Title = "Escribe tu correo y envía",
                            criticStep3Body =
                                "Usa una dirección que puedas abrir ahora mismo y describe el uso en " +
                                    "pocas palabras; \"uso personal\" es una respuesta correcta.",
                            criticStep4Title = "Activa la clave desde el correo",
                            criticStep4Body =
                                "La clave llega con un enlace de activación. Ábrelo, o la clave sigue " +
                                    "inactiva, y luego pega la clave aquí en los ajustes.",
                            criticSketchEmail = "Tu correo",
                            criticSketchFree = "FREE",
                            criticSketchSubmit = "Enviar",
                            criticSketchInbox = "Clave + activación",
                        ),
                    discovery =
                        DiscoveryStrings(
                            title = "Descubrir",
                            hint = "Desliza o usa los botones: guardar lo añade a favoritos.",
                            keep = "Guardar",
                            skip = "Saltar",
                            details = "Detalles",
                            exhausted = "Has visto todo por ahora.",
                            another = "Buscar más",
                            loading = "Creando tu selección…",
                            kept = "Guardado en favoritos",
                        ),
                    settingsTabs =
                        SettingsTabStrings(
                            general = "General",
                            content = "Contenido",
                            subtitles = "Subtítulos",
                            data = "Datos",
                            maintenance = "Mantenimiento",
                        ),
                    cache =
                        CacheStrings(
                            title = "Guardar capas neste computador",
                            explanation = "O app guarda as capas e imagens no seu computador para que a lista abra na hora, sem baixar tudo de novo a cada vez.",
                            firstTimeWarning = "Na primeira vez pode demorar: depende do tamanho da sua lista. O download acontece em segundo plano e você pode usar o app normalmente.",
                            sizeLabel = "Espaço reservado",
                            gigabytes = "%d GB",
                            disabled = "Não guardar",
                            estimate = "Sua lista precisa de aproximadamente %s.",
                            start = "Começar",
                            skip = "Agora nao",
                            filling = "Guardando capas",
                            progress = "%1${'$'}d de %2${'$'}d",
                            pause = "Pausar",
                            resume = "Continuar",
                            cancel = "Cancelar",
                            complete = "Tudo guardado.",
                            used = "Em uso: %s",
                            clear = "Limpar cache",
                            clearTitle = "Limpiar el caché de portadas?",
                            clearBody = "Las portadas guardadas se borrarán y habrá que descargarlas de nuevo. No se pierde nada de tu lista.",
                            restartNote = "Mudar o tamanho vale a partir da próxima vez que você abrir o app.",
                            percent = "%d%%",
                            refresh = "Actualizar",
                        ),
                    serviceCatalogue =
                        ServiceCatalogueStrings(
                            seeMore = "Ver mas",
                            allFrom = "Todo de %s",
                            backToShelves = "Volver a los servicios",
                            genreSelector = "Género",
                            serviceSelector = "Servicio",
                            allGenres = "Todos los géneros",
                            allServices = "Todos los servicios",
                            servicesUnavailable = "no indicado en tu lista",
                            servicesLoading = "buscando…",
                            duplicatesLabel = "Copias repetidas",
                            duplicatesHint = "Las listas suelen traer la misma película varias veces, una por calidad o doblaje.",
                            duplicatesToggle = "Mostrar una sola tarjeta por película",
                        ),
                    receiver =
                        CastReceiverStrings(
                            title = "Recibir del celular",
                            hint =
                                "Permite que el celular encuentre este equipo y le envíe un " +
                                    "título. Los dos tienen que estar en la misma red. Escribe " +
                                    "el código de abajo en el celular, una sola vez.",
                            receiveNow = "Recibir ahora",
                            autoStart = "Activar al abrir la app",
                            codeLabel = "Código",
                            codeExplanation =
                                "Este código es siempre el mismo en este equipo. Escríbelo una " +
                                    "vez en el celular y no volverá a pedirlo. Solo quien tenga " +
                                    "este número puede enviar aquí.",
                            regenerate = "Generar un código nuevo",
                        ),
                    share = "Compartir",
                    shareTitle = "Compartir título",
                    shareSubtitle = "Envía una recomendación, no tu lista.",
                    shareDestination = "Enviar por",
                    shareByEmail = "Correo",
                    shareCopyLink = "Copiar enlace",
                    shareCopied = "¡Copiado!",
                    shareNoCredentials =
                        "El enlace no contiene tu servidor, usuario ni contraseña. " +
                            "Quien lo reciba lo abre con su propia lista.",
                    shareNotFoundTitle = "No está en tu lista",
                    shareNotFoundBody =
                        "Tu proveedor no ofrece este título. Un enlace compartido es una " +
                            "recomendación: cada persona lo abre con su propia lista.",
                    ),
                search = "Búsqueda",
                sources = "FUENTES",
                profile = "Perfil",
                yourLibrary = "Tu biblioteca",
                connectXtream = "Conectar Xtream",
                importM3u = "Importar M3U",
                checkUpdate = "Buscar actualización",
                refreshCatalog = "Actualizar listas",
                termsTitle = "Antes de empezar",
                termsNoContent = "IPTV BURO no ofrece, aloja ni revende ningún canal, película o serie.",
                termsYourSource = "Usas tu propia lista: la aplicación solo reproduce lo que tu proveedor entrega.",
                termsResponsibility = "Eres responsable de tener derecho de acceso al contenido que añadas.",
                                termsNoWarranty = "El funcionamiento depende de tu lista y de tu conexión. No garantizamos que un proveedor, canal o formato concreto funcione.",
                termsOneDevice = "La licencia vale para un ordenador. Cambiar de equipo exige una nueva activación.",
                termsRefund = "Tienes 7 días de prueba antes de pagar. Tras la compra, el derecho de desistimiento sigue la ley de consumo de tu país.",
                termsReadFull = "Leer los términos completos",
                termsAccept = "Acepto y continuar",
                setupTitle = "Crea tu perfil",
                setupSubtitle = "Cada perfil guarda sus propios favoritos. Las descargas quedan disponibles para todos.",
                setupProfileName = "Nombre del perfil",
                setupUseExisting = "USAR UNA LISTA YA CONFIGURADA",
                setupYourList = "TU LISTA",
                setupNewList = "O AÑADIR UNA LISTA NUEVA",
                setupListName = "Nombre de la lista",
                serverLabel = "Servidor",
                usernameLabel = "Usuario",
                passwordLabel = "Contraseña",
                setupContinue = "Continuar",
                connectingTitle = "Cargando tu lista",
                connectingBody = "Conectando con el proveedor y preparando el catálogo. Puede tardar unos instantes.",
                setupFailedTitle = "No se pudo cargar la lista",
                setupFailedBody = "El proveedor no respondió. Revisa el servidor, el usuario y la contraseña.",
                setupRetry = "Intentar de nuevo",
                chooseRating = "Nota",
                anyRating = "Todas las notas",
                continueEmptyTitle = "Nada para continuar",
                continueEmptyBody = "Las películas y episodios que empieces aparecen aquí.",
                resumeFrom = "Continuar",
                startOver = "Desde el principio",
                forgetProgress = "Quitar",
                metadataKeyLabel = "Clave TMDb (opcional)",
                                tmdbGuide =
                                    TmdbGuideStrings(
                                        tmdbGuideTitle = "Cómo obtener tu clave TMDb",
                                        tmdbGuideSubtitle = "Seis pasos. Es gratuito y lleva unos cinco minutos.",
                                        tmdbGuideOpenSignup = "Crear cuenta en TMDb",
                                        tmdbGuideOpenApiPage = "Ya tengo cuenta",
                                        tmdbGuideButton = "¿No sabes cómo conseguirla?",
                                        tmdbStep1Title = "Crea una cuenta gratuita",
                                        tmdbStep1Body = "Abre themoviedb.org y pulsa Sign Up. Necesitas un correo, un nombre de usuario y una contraseña.",
                                        tmdbStep2Title = "Confirma el correo e inicia sesión",
                                        tmdbStep2Body = "TMDb envía un correo de confirmación. Sin confirmarlo, la página de la API no está disponible.",
                                        tmdbStep3Title = "Abre Settings y luego API",
                                        tmdbStep3Body = "Pulsa tu foto arriba a la derecha, elige Settings y luego API en el menú lateral.",
                                        tmdbStep4Title = "Pide una clave de desarrollador",
                                        tmdbStep4Body = "Elige Developer, no Commercial. Es la opción correcta para uso personal y es gratuita.",
                                        tmdbStep5Title = "Rellena el formulario",
                                        tmdbStep5Body = "Nombre de la aplicación: IPTV BURO. URL: sirve cualquier dirección tuya. Finalidad: uso personal, para ver carátulas y sinopsis.",
                                        tmdbStep6Title = "Copia la clave y pégala aquí",
                                        tmdbStep6Body = "Copia el valor de API Key (v3 auth), de 32 caracteres, y pégalo en el campo de esta ventana. Se guarda solo en tu ordenador.",
                                        tmdbSketchSignUp = "Sign Up",
                                        tmdbSketchApiMenu = "API",
                                        tmdbSketchRequestType = "Tipo de solicitud",
                                        tmdbSketchDeveloper = "Developer",
                                        tmdbSketchFormFields = "Nombre, URL, finalidad",
                                        tmdbSketchApiKeyLabel = "API Key (v3 auth)",
                                        tmdbSketchCopy = "Copiar",
                                        tmdbSketchSettings = "Ajustes",
                                        tmdbSketchPaste = "Pegar aquí",
                                    ),
                metadataKeyHint = "Pega tu clave de themoviedb.org/settings/api",
                metadataKeyPlaceholder = "Clave de la API",
                layoutPoster = "Carátulas",
                layoutCompact = "Compacto",
                layoutList = "Lista",
                removeProfile = "Quitar",
                confirmRemoveProfile = "¿Confirmar?",
                showPassword = "Mostrar",
                hidePassword = "Ocultar",
                avatarUsePhoto = "Usar una foto",
                avatarRemovePhoto = "Quitar foto",
                avatarChoosePhotoTitle = "Elige una foto para el perfil",
                checkingUpdate = "Buscando actualización…",
                upToDate = "Ya tienes la versión más reciente.",
                downloading = "Descargando",
                installerVerified = "Instalador verificado. Actualizando…",
                updateFailed = "No se pudo instalar la actualización.",
                privateSession = "Sesión privada",
                nothingSensitiveSaved = "No se guarda nada sensible",
                dailySelection = "SELECCIÓN DIARIA",
                continueWatching = "Seguir viendo",
                moviesForToday = "Películas elegidas para hoy",
                seriesToExplore = "Series para seguir explorando",
                liveNow = "En vivo ahora",
                heroFallbackTitle = "Tu biblioteca está lista",
                heroSubtitle =
                    "Una selección distinta cada día, organizada sin mezclar toda la biblioteca " +
                        "en la misma pantalla.",
                watchNow = "Ver",
                details = "Ver detalles",
                options = "opciones",
                organizingToday = "Organizando la selección de hoy…",
                seasonalBadge = "DE TEMPORADA",
                tryAgain = "Intentar de nuevo",
                watched = "visto",
                onAir = "EN VIVO",
                emptyHeadline = "Toda tu biblioteca.\nSin ruido.",
                emptyBody =
                    "Importa tu fuente autorizada y deja que IPTV BURO organice canales, películas y " +
                        "series en una experiencia única en todas las pantallas.",
                emptyBadge = "BURO NOCTURNE  •  BIBLIOTECA PRIVADA",
                credentialsStayLocal = "La fuente se reconecta con la caja fuerte protegida de este usuario.",
                authenticating = "Autenticando y preparando el catálogo…",
                organizingPlaylist = "Organizando tu lista…",
                noSensitiveData = "No se guardará ningún dato sensible.",
                close = "Cerrar",
                cancel = "Cancelar",
                understood = "Entendido",
                whoIsWatching = "¿Quién está viendo?",
                newProfile = "Nuevo perfil",
                addProfile = "Añadir",
                kidsProfile = "Perfil infantil",
                adultProfile = "Perfil adulto",
                forgetSource = "Olvidar fuente",
                searchChannel = "Buscar canal…",
                results = "resultados",
                items = "elementos",
                sourcesCount = "fuentes",
                selectChannel = "Selecciona un canal",
                vaultProtected = "Caja fuerte protegida",
                credentialsEncrypted = "Credenciales cifradas por Windows",
                endSession = "Cerrar sesión",
                searchCatalog = "Buscar en este catálogo…",
                categories = "Categorías",
                allCategories = "Todas",
                catalog = "Catálogo",
                noMatch = "Ningún elemento coincide con el filtro.",
                previous = "Anterior",
                next = "Siguiente",
                page = "Página",
                downloadsNoMatch = "No se encontró nada con ese filtro.",
                allItems = "Todo",
                allYears = "Todos los años",
                releasesIn = "Estrenos",
                sessionActive = "Sesión activa · conexión protegida en Windows",
                loadingCatalog = "Cargando catálogo…",
                sessionClosed = "Sesión cerrada",
                backToCatalog = "Volver al catálogo",
                selectItem = "Selecciona un elemento",
                download = "Descargar",
                downloadInProgress = "Descargando",
                downloaded = "Descargado",
                downloadFailed = "Error en la descarga",
                removeDownload = "Quitar descarga",
                downloads = "Descargas",
                resetSettings = "Restablecer ajustes",
                resetConfirm = "Borrar todo",
                resetWarning = "Esto borra perfiles, favoritos y progreso. Los archivos descargados se conservan.",
                settings = "Ajustes",
                languageLabel = "IDIOMA",
                chooseYear = "Elegir año",
                downloadPaused = "En pausa",
                downloadsEmptyTitle = "Ninguna copia sin conexión",
                downloadsEmptyBody = "Abre una película y elige Descargar. Las copias guardadas aparecen aquí y se ven sin internet.",
                updateReadyBody = "La actualizacion se descargo y verifico. La aplicacion se cerrara, Windows instalara la nueva version y la aplicacion se abrira sola. Puede tardar un minuto.",
                updateRestartNow = "Cerrar y actualizar",
                updateLater = "Más tarde",
                music = "Música",
                musicHome = "Inicio",
                musicArtists = "Artistas",
                musicRadio = "Radio",
                musicNewReleases = "Novedades",
                musicMostPlayed = "Más escuchadas",
                musicTracks = "pistas",
                musicStations = "emisoras",
                musicAddPlaylist = "Añadir lista de música",
                musicEmptyTitle = "Todavía no hay música",
                musicEmptyBody = "Añade una lista M3U de música a tu perfil para escucharla aquí.",
                musicNoArtists = "Esta lista no trae artistas identificados.",
                musicNoRadio = "Esta lista no trae emisoras de radio.",
                musicNoDownloads = "La música que descargues aparece aquí.",
                musicBackToArtists = "Volver a los artistas",
                musicPlaylistLabel = "Lista de música",
                musicPlaylistOptional = "OPCIONAL",
                musicPlaylistHint = "Un M3U solo de música. Sin él, nada cambia en la aplicación.",
                musicPlaylistChoose = "Elegir archivo",
                musicPlaylistRemove = "Quitar",
                musicPlaylistTitle = "Elige tu lista de música",
                musicPlaylists = "Listas",
                musicPlaylistsEmpty = "Todavía no has creado ninguna lista.",
                musicPlaylistNew = "Lista nueva",
                musicPlaylistNewName = "Mi lista",
                musicPlaylistRename = "Renombrar",
                musicPlaylistDelete = "Eliminar",
                musicPlaylistDuplicate = "Duplicar",
                musicPlaylistDuplicateSuffix = "copia",
                musicPlaylistImport = "Importar M3U",
                musicPlaylistExport = "Exportar M3U",
                musicPlaylistBack = "Volver a las listas",
                musicPlaylistEmptyTracks = "Esta lista todavía no tiene pistas.",
                musicPlaylistRemoveTrack = "Quitar de la lista",
                musicPlaylistMoveUp = "Subir",
                musicPlaylistMoveDown = "Bajar",
                musicSmartPlaylists = "Listas inteligentes",
                musicSmartFavourites = "Favoritas",
                musicSmartRecentlyPlayed = "Escuchadas hace poco",
                musicSmartMostPlayed = "Más escuchadas",
                musicSmartNeverPlayed = "Nunca escuchadas",
                musicSmartRecentlyAdded = "Añadidas hace poco",
                musicExportWarningTitle = "Este archivo puede contener direcciones sensibles",
                musicExportWarningBody =
                    "Algunas direcciones de esta lista parecen incluir credenciales o firmas de acceso. " +
                        "Quien reciba el archivo podrá usar tu suscripción. Expórtalo solo para ti.",
                musicExportWarningConfirm = "Exportar de todos modos",
                musicExportWarningCancel = "Cancelar",
                queueTitle = "Cola",
                queueNowPlaying = "Sonando ahora",
                queueUpNext = "A continuación",
                queueEmptyBody = "Nada na fila. Use “Tocar em seguida” ou “Adicionar ao final”.",
                queuePlayNow = "Reproducir ahora",
                queuePlayNext = "Reproducir a continuación",
                queueAddToEnd = "Añadir al final",
                queueRemove = "Quitar de la cola",
                queueClear = "Vaciar",
                queueMoveUp = "Subir",
                queueMoveDown = "Bajar",
                queueOpen = "Abrir la cola",
                queueClose = "Cerrar la cola",
                queueCount = "en la cola",
                subscriptions = "Suscripciones",
                subscriptionsDemoBadge = "DEMO",
                subscriptionsDemoNotice =
                    "Estos resultados son de ejemplo, creados solo para mostrar la pantalla. " +
                        "No hay ningún servicio de streaming real conectado.",
                subscriptionsWhereToWatch = "Dónde ver",
                subscriptionsInYourLibrary = "Ya está en tu lista",
                subscriptionsIncludedInSubscription = "Incluido en tu suscripción",
                subscriptionsFreeWithAds = "Gratis con anuncios",
                subscriptionsRent = "Alquilar",
                subscriptionsBuy = "Comprar",
                subscriptionsRequiresSubscription = "Requiere suscripción",
                subscriptionsUnavailable = "No disponible aquí",
                subscriptionsOpenProvider = "Abrir en el servicio oficial",
                subscriptionsMyServices = "Mis servicios",
                subscriptionsRegion = "Región",
                languageHint = "Idioma de los textos de la aplicación",
                regionHint = "País usado para saber qué servicios de streaming tienen cada película",
                metadataKeyUses = "Se usa para carátulas, reparto, tráileres y la pestaña Suscripciones",
                metadataKeySaved = "✓ Clave guardada. Ya está en uso.",
                metadataKeyUsingBundled = "Usando la clave predeterminada de la aplicación.",
                settingsText =
                    SettingsStrings(
                        profileEdit = "Editar",
                        profileEditTitle = "Editar perfil",
                        profileNameLabel = "Nombre",
                        profileAvatarLabel = "Imagen",
                        profileKidsLabel = "Modo infantil",
                        profileKidsHint = "Muestra solo contenido para niños",
                        profileSourceLabel = "Lista",
                        profileSourceNone = "Usar la que ya está conectada",
                        profileSourceChange = "Cambiar lista",
                        profileMusicLabel = "Música (M3U)",
                        profileMusicNone = "Ningún archivo elegido",
                        profileMusicChoose = "Elegir archivo",
                        profileMusicClear = "Quitar",
                        profileSave = "Guardar",
                        expandSidebar = "Expandir",
                        subtitlesLabel = "Subtítulos",
                        subtitlesHint = "Vale para la próxima película que abras",
                        subtitlesBackground = "Fondo oscuro",
                        historyTitle = "Historial",
                        historyClearAll = "Borrar todo",
                        historyEmpty = "Todavía no has visto nada.",
                        categoriesLabel = "Categorías",
                        categoriesHint = "Oculta lo que no usas, o protégelo con la contraseña",
                        categoryHide = "Ocultar",
                        categoryLock = "Proteger",
                        clockLabel = "Reloj",
                        clockHint = "Formato de la hora mostrada arriba",
                        clock24h = "24 horas",
                        clock12h = "12 horas",
                        parentalTitle = "Control parental",
                        parentalHint = "Protege categorías con una contraseña de 4 dígitos",
                        parentalSetPin = "Crear contraseña",
                        parentalChangePin = "Cambiar contraseña",
                        parentalRemovePin = "Quitar contraseña",
                        parentalCurrentPin = "Contraseña actual",
                        parentalNewPin = "Contraseña nueva",
                        parentalWrongPin = "Contraseña incorrecta.",
                        parentalDefaultPin = "Bloqueo activo con la contraseña estándar 0000. Elija la suya para que no sea adivinada.",
                        parentalPinSaved = "✓ Contraseña guardada.",
                        parentalPinFormat = "La contraseña debe tener 4 números.",
                        parentalLockAdult = "Bloquear categorías para adultos automáticamente",
                        parentalLocked = "Contenido protegido",
                        firstRunTitle = "Preparando IPTV BURO",
                        firstRunBody =
                            "Solo esta vez tarda más: tu lista se está leyendo y organizando. " +
                                "En las próximas veces ya estará listo.",
                        firstRunTmdbTitle = "Carátulas y sinopsis",
                        firstRunTmdbBody =
                            "Para ver carátulas, sinopsis y reparto, añade una clave de TMDb en " +
                                "Ajustes. Es gratuita: crea una cuenta en themoviedb.org, pide la clave " +
                                "de API y pégala en BURO.",
                        startupAuthenticating = "Autenticando…",
                        startupOrganising = "Organizando tu lista…",
                        profileKeyLabel = "Clave solo de este perfil",
                        profileKeyHint =
                            "Déjalo vacío para usar la misma clave que los demás perfiles. " +
                                "Rellénalo para que este perfil use su propia cuenta de TMDb.",
                        profileKeyShared = "Usando la clave compartida",
                        profileKeyOwn = "Este perfil usa su propia clave",
                        moreSettingsTitle = "Más opciones",
                        moreSettingsHint = "Subtítulos, reloj, categorías y control parental",
                        multiviewAdd = "Ver juntos",
                        multiviewRemove = "Quitar de la pantalla doble",
                        multiviewOpen = "Ver juntos",
                        multiviewHint = "Ver de 2 a 4 canales juntos",
                                                musicWorkshop = "Taller de música",
                        musicWorkshopSummary = "%d pistas · %d corregidas",
                        musicWorkshopNames = "Nombres",
                        musicWorkshopDuplicates = "Repetidas",
                        musicWorkshopApplyAll = "Corregir todas (%d)",
                        musicWorkshopApplyOne = "Corregir",
                        musicWorkshopUndoAll = "Deshacer las %d correcciones",
                        musicWorkshopNothingToFix = "Nada que corregir. Los nombres ya están limpios.",
                        musicWorkshopNoDuplicates = "Ninguna pista repetida.",
                        musicWorkshopSameAddress = "Misma dirección — sin duda la misma pista",
                        musicWorkshopSameName = "Mismo nombre — comprueba antes de quitar",
                        multiviewAudioFrom = "Audio de",
                        multiviewFullScreen = "Pantalla completa",
                        multiviewWindowed = "Ventana",
                        multiviewUnavailable = "No se pudo abrir la pantalla doble",
                        multiviewUnavailableHint = "Los canales elegidos no respondieron. Prueba con otros.",
                        multiviewEmpty = "Elige primero los canales",
                        multiviewEmptyHint = "Pasa el ratón sobre un canal en vivo y pulsa ▦ para juntar hasta 4.",
                        multiviewClear = "Vaciar",
                        multiviewFull = "Máximo de %d canales",
                        licenseTrialOverTitle = "Tus 7 días han terminado",
                        licenseTrialOverBody =
                            "Esperamos que te haya gustado. Para seguir viendo, activa este " +
                                "dispositivo.",
                        licenseExpiredTitle = "Tu licencia ha caducado",
                        licenseExpiredBody = "Renueva para seguir usando IPTV BURO en este dispositivo.",
                        licenseRevokedTitle = "Licencia cancelada",
                        licenseRevokedBody =
                            "Esta licencia fue cancelada. Si crees que es un error, escríbenos.",
                        licenseVerifyTitle = "Necesitamos verificar tu licencia",
                        licenseVerifyBody =
                            "Hace tiempo de la última verificación. Conéctate a internet " +
                                "una vez y todo vuelve a la normalidad.",
                        licenseOfflineTitle = "Sin conexión",
                        licenseOfflineBody =
                            "No pudimos verificar tu licencia. Revisa tu conexión e inténtalo de nuevo.",
                        licenseTwoYears = "por 2 años",
                        licenseWhyNotLifetime =
                            "No es de por vida porque la aplicación se sigue manteniendo y actualizando: " +
                                "los proveedores cambian, los formatos cambian, Windows cambia.",
                        licenseBuy = "Activar dispositivo",
                        licenseRetry = "Intentar de nuevo",
                        licenseDeviceLabel = "Tu dispositivo",
                        licenseHaveKey = "Tengo un código de activación",
                        licenseKeyPlaceholder = "Código",
                        licenseRedeem = "Usar código",
                        licenseDaysLeft = "Quedan %d días",
                                                epgShowSchedule = "Ver programación (%d)",
                        epgHideSchedule = "Ocultar programación",
                        parentalUnlock = "Escribe la contraseña para ver esta categoría",
                    ),
                subscriptionsSynopsis = "Sinopsis",
                subscriptionsCast = "Reparto",
                subscriptionsWatchTrailer = "▶  Ver tráiler",
                subscriptionsAvailableOn = "Disponible en",
                subscriptionsFilterMovies = "Películas",
                subscriptionsFilterSeries = "Series",
                subscriptionsFilterUpcoming = "Próximamente",
                subscriptionsFilterThisWeek = "Esta semana",
                subscriptionsUpcomingNote =
                    "Estrenos con fecha fijada. Todavía no se sabe en qué servicio se estrenará cada título.",
                subscriptionsEmptyBody = "No se encontró nada para este título.",
                subscriptionsBrowseByService = "Por servicio",
                subscriptionsNoShelves = "Todavía no hay servicios que mostrar.",
                subscriptionsLoadFailed = "No pudimos cargar los servicios. Comprueba la conexión e inténtalo de nuevo.",
                subscriptionsKeyRejected =
                    "TMDb rechazó la clave de API. Revísala en Opciones — una clave nueva puede tardar unos minutos en activarse.",
                subscriptionsBackToServices = "Volver a los servicios",
                subscriptionsSelectedTitle = "Título seleccionado",
                licenseText =
                    LicenseStrings(
                        trialTitle = "Tu periodo de prueba ha terminado",
                        trialBody =
                            "Los 7 días han terminado. Activa este dispositivo para seguir usando IPTV BURO.",
                        expiredTitle = "Licencia caducada",
                        expiredBody =
                            "La licencia de este dispositivo ha caducado. Renuévala para continuar.",
                        revokedTitle = "Licencia cancelada",
                        revokedBody =
                            "Esta licencia ya no está activa. Si crees que es un error, escríbenos "
                                + "indicando el código de abajo.",
                        unreachableTitle = "No se pudo verificar la licencia",
                        unreachableBody =
                            "No pudimos contactar con el servidor. Revisa tu conexión e inténtalo de nuevo.",
                        verifyTitle = "Hay que verificar",
                        verifyBody =
                            "La aplicación pasó demasiado tiempo sin verificar. Conéctate a internet una "
                                + "vez para continuar.",
                        deviceLabel = "Dispositivo",
                        activationKeyLabel = "Clave de activación",
                        activationKeyHint = "Guárdala. Está ligada a este equipo; perderla obliga a comprar otra.",
                        macLabel = "MAC",
                        scanHint = "Apunta la cámara del móvil al código",
                        openInBrowser = "Abrir en el navegador",
                        retry = "Intentar de nuevo",
                        haveKey = "¿Tienes un código de activación?",
                        keyPlaceholder = "XXXX-XXXX",
                        redeem = "Activar",
                        redeemFailed = "Código no válido o ya usado.",
                        keyAvailable = "Clave válida y libre.",
                        keyAvailableDays = { days -> "Clave de $days días, libre." },
                        keyYours = "Esta clave ya es de este equipo.",
                        keyInUse = "Esta clave ya se usó en otro equipo.",
                        keyExpired = "Esta clave ha caducado.",
                        quit = "Cerrar",
                        back = "Volver a la aplicación",
                        backToPurchase = "Prefiero pagar",
                        termYears = "%d años",
                        priceEur = "€ 9,90 · 2 años",
                        priceUsd = "US$ 9,90 · 2 años",
                        priceBrl = "R$ 99,90 · 2 años",
                        whyNotLifetime =
                            "Por qué no es de por vida: la aplicación se sigue manteniendo y actualizando. "
                                + "Los 2 años pagan ese trabajo.",
                        copied = "Copiado",
                        clockWarning =
                            "El reloj de este ordenador parece incorrecto. Las fechas de la licencia vienen del "
                                + "servidor de todos modos.",
                        trialDaysLeft = "Quedan %d días de prueba",
                        licenseDaysLeft = "Quedan %d días",
                        licenseLastDay = "Último día",
                        trialLastDay = "Último día de prueba",
                        buyNow = "Activar",
                    ),
            )

        private val PtBr =
            DesktopStrings(
                library = "BIBLIOTECA",
                home = "Início",
                live = "Ao vivo",
                movies = "Filmes",
                series = "Séries",
                downloadStrings =
                    DownloadStrings(
                        downloadSeries = "Baixar série",
                        downloadSeason = "Baixar temporada %d",
                        downloadSeriesConfirmTitle = "Baixar a série inteira?",
                        downloadSeasonConfirmTitle = "Baixar a temporada %d?",
                        downloadConfirmBody = "%d episódios serão baixados. Isso pode usar bastante espaço e dados.",
                        downloadConfirmAction = "Baixar",
                    ),
                savedForLater =
                    SavedForLaterStrings(
                        favorites = "Favoritos",
                        reminderAdd = "Lembrete",
                        reminderActive = "Lembrete ativo",
                        reminderNoNotice = "Salvo neste computador. O aviso aparece aqui no app.",
                        reminderAnnounce = "Avisar sobre os lembretes",
                        reminderHourLabel = "Avisar às",
                        reminderInAppOnly =
                            "O aviso aparece dentro do app, na primeira vez que você abrir depois " +
                                "do horário escolhido. O app não envia notificação do Windows " +
                                "enquanto está fechado.",
                        reminderNoticeBody = "Você tem %d título(s) marcado(s) para assistir.",
                        reminderNoticeDismiss = "Entendi",
                        remindersTitle = "Lembretes",
                        remindersEmpty =
                            "Você ainda não marcou nenhum título. Use o botão Lembrete na página " +
                                "de um filme, de uma série ou de um lançamento em Assinaturas.",
                        reminderOpen = "Abrir",
                        reminderRemove = "Remover",
                        reminderNotInLibrary = "Ainda não está na sua lista",
                        newEpisodeBody = "Novo episódio: T%1${'$'}d E%2${'$'}d",
                        newSeasonBody = "Nova temporada: T%1${'$'}d",
                    ),
                shareStrings =
                    ShareStrings(
                        cast =
                            CastStrings(
                                            castAction = "Enviar à tela",
                                            castTitle = "Enviar para uma tela",
                                            castSearching = "Procurando telas nesta rede…",
                                            castNoneFound = "Nenhuma tela encontrada. Abra o IPTV BURO no outro aparelho, ligue “Receber” e confirme que os dois estão na mesma rede.",
                                            castManualTitle = "Ou digite o endereço",
                                            castManualHint = "Alguns roteadores bloqueiam a busca entre aparelhos. A outra tela mostra o endereço dela em “Receber”.",
                                            castManualLabel = "Endereço nesta rede",
                                            castManualConnect = "Conectar",
                                            castManualInvalid = "Isso não parece um endereço desta rede.",
                                            castSearchAgain = "Procurar de novo",
                                            castCodePrompt = "Enviar para %s",
                                            castCodeHint = "Digite os quatro dígitos que aparecem naquela tela.",
                                            castCodeInvalid = "O código tem quatro dígitos.",
                                            castSend = "Enviar",
                                            castSending = "Enviando para %s…",
                                            castSent = "Enviado para %s. Se não começar, confira o código naquela tela.",
                                            castFailed = "Não foi possível alcançar %s.",
                                            castChooseAnother = "Escolher outra tela",
                            ),
                    notifications =
                        NotificationStrings(
                            title = "Avisos",
                            empty = "Nada por aqui.",
                            clearAll = "Limpar tudo",
                            dismiss = "Descartar",
                        ),
                    failures =
                        FailureStrings(
                            sessionExpired =
                                "A sessão da sua lista expirou. O catálogo continua salvo, mas é " +
                                    "preciso entrar de novo na fonte para carregar novidades.",
                            outOfMemory =
                                "Não houve memória suficiente para montar esta tela. " +
                                    "Isso é uma limitação do aplicativo, não da sua lista.",
                            invalidServer = "O endereço do servidor não é válido.",
                            invalidServerScheme = "Confira o começo do endereço: precisa ser http:// ou https://.",
                            authenticationRejected = "O servidor recusou o usuário ou a senha.",
                            networkUnreachable = "Não foi possível alcançar o servidor.",
                            httpError = "O servidor respondeu com um erro HTTP.",
                            responseTooLarge = "O catálogo excedeu o limite seguro desta prévia.",
                            invalidResponse = "O servidor não retornou um catálogo Xtream compatível. Detalhes em %1${'$'}s",
                            appFault =
                                "Não foi possível montar esta tela (%1${'$'}s). Isso é uma falha do " +
                                    "aplicativo, não da sua lista. Detalhes em %2${'$'}s",
                        ),
                    startup =
                        StartupStrings(
                            openingSession = "Abrindo a sua sessão…",
                            joiningList = "Juntando %1${'$'}s…",
                            loadingLiveCategories = "Carregando categorias de canais…",
                            loadingMovieCategories = "Carregando categorias de filmes…",
                            loadingSeriesCategories = "Carregando categorias de séries…",
                            downloadingMovies = "Baixando a lista de filmes…",
                            downloadingSeries = "Baixando a lista de séries…",
                            organising = "Organizando filmes e séries…",
                            ready = "Pronto",
                        ),
                    screens =
                        ScreenStrings(
                            setupMissingProfileName = "Escreva o nome do perfil para continuar",
                            setupMissingConnection = "Preencha servidor, usuário e senha, ou escolha uma lista já configurada",
                            diagnosticsAction = "Diagnóstico",
                            diagnosticsTitle = "Diagnóstico da ligação",
                            mergeSourcesTitle = "Juntar todas as listas",
                            mergeSourcesHelp = "Mostra todas as suas listas como um só catálogo. Nada se repete: a lista maior manda e as outras completam o que falta.",
                            mergeSourcesRestart = "As listas sao reorganizadas na hora.",
                            mergeSourcesFailed = "%1${'$'}s não respondeu. As outras listas continuam a funcionar.",
                            mergeSourcesOffline = "Não responde",
                            diagnosticsLatencyGood = "Latência baixa: os canais trocam sem espera",
                            diagnosticsLatencyFair = "Latência alta: pode travar e demorar ao trocar de canal",
                            diagnosticsLatencyUnstable = "Latência muito alta: vai causar travamentos e cortes",
                            diagnosticsLatencyUnknown = "Não foi possível medir a latência",
                            diagnosticsRunning = "A testar…",
                            diagnosticsRun = "Testar de novo",
                            diagnosticsClose = "Fechar",
                            diagnosticsDownload = "Descarga",
                            diagnosticsUpload = "Envio",
                            diagnosticsPing = "Latência",
                            diagnosticsLoss = "Perda de pacotes",
                            diagnosticsCatalogue = "Lista",
                            diagnosticsConnection = "Ligação",
                            diagnosticsMemory = "Memória",
                            diagnosticsAddress = "Endereço IP",
                            diagnosticsGateway = "Gateway",
                            diagnosticsNetmask = "Máscara de rede",
                            diagnosticsVerdictGood = "Está tudo bem",
                            diagnosticsVerdictWarning = "Atenção",
                            diagnosticsVerdictProblem = "Problema encontrado",
                            diagnosticsQualityUnstable = "Ligação fraca: pode causar travamentos e cortes",
                            diagnosticsQualitySd = "Ligação razoável: dá para qualidade normal",
                            diagnosticsQualityHd = "Ligação boa para filmes em 1080p",
                            diagnosticsQualityUhd = "Ligação perfeita para 4K e TV ao vivo",
                            diagnosticsQualityUnknown = "Não foi possível medir a velocidade",
                            diagnosticsWireless = "Wi-Fi: o cabo evita os cortes que o sem-fios costuma causar",
                            diagnosticsWired = "Cabo de rede",
                            diagnosticsNoLink = "Sem ligação de rede",
                            diagnosticsCatalogueEmpty = "A lista não carregou: confirme a assinatura com quem lhe vendeu",
                            diagnosticsSignedOut = "Sem lista configurada",
                            diagnosticsLowMemory = "Pouca memória: feche outros programas ou reinicie a aplicação",
                            deviceCodeAction = "Código do aparelho",
                            deviceCodeHelp = "Envie este código a quem lhe vendeu a lista para que ele a configure por você.",
                            setupRenameList = "Renomear",
                            setupRemoveList = "Remover",
                            setupRemoveListConfirm = "Remover a lista «%1${'$'}s»? A senha também é apagada.",
                            importFileMissing = "O arquivo selecionado não existe mais.",
                            importAccessDenied = "O sistema não permitiu ler esse arquivo.",
                            importBlocked = "O acesso ao arquivo foi bloqueado pelo sistema.",
                            importFailed = "Não foi possível importar a lista. Verifique se o arquivo é M3U/M3U8 válido e tente novamente.",
                            movieDetailsLoading = "Carregando ficha do filme…",
                            epgLoading = "Carregando agora e próximo…",
                            guideNow = "Agora",
                            guideNext = "A seguir",
                            catchUpShow = "Rever (%1${'$'}s)",
                            catchUpHide = "Ocultar",
                            similarTitles = "Títulos parecidos",
                            epgUnavailable = "Guia indisponível; o canal continua acessível.",
                            epgEmpty = "Sem programação informada pela fonte.",
                            loadEpisodes = "Carregar episódios",
                            episodesLoading = "Carregando episódios…",
                            externalOpenFailed = "Não foi possível abrir",
                            externalNoDefaultApp = "Este sistema não oferece um aplicativo padrão para abrir o canal.",
                            externalRefused = "O aplicativo externo recusou o endereço. Nenhum dado foi copiado.",
                            externalHeadersWarning = "O canal exige cabeçalhos HTTP; um navegador comum pode não reproduzi-lo.",
                            externalAddressValid = "Endereço válido para um aplicativo externo.",
                            headersUnsupported = "Este canal exige cabeçalhos HTTP que o player Windows atual ainda não consegue aplicar. A reprodução foi desativada para não apresentar um botão que falhará.",
                            noChannelMatches = "Nenhum canal corresponde ao filtro.",
                            connectXtreamTitle = "Conectar servidor Xtream",
                            searchingCatalogue = "Procurando no catálogo…",
                            noFurtherTitles = "Nenhum outro título encontrado no catálogo.",
                            noPlayableEpisodes = "O servidor não retornou episódios reproduzíveis.",
                            playerStopped = "O motor de vídeo foi encerrado inesperadamente.",
                            playerStartFailed = "O motor de vídeo do Windows não pôde ser iniciado.",
                            playerStalled = "O servidor respondeu, mas este vídeo não iniciou. Tente novamente ou escolha outro título.",
                            updateCheckFailed = "Não foi possível verificar atualizações agora.",
                            demoMovieNotice = "Título de exemplo. Esta disponibilidade é fictícia.",
                            demoSeriesNotice = "Série de exemplo. Esta disponibilidade é fictícia.",
                        ),
                    remoteSource =
                        RemoteSourceStrings(
                            title = "Servidor próprio (NAS)",
                            hint = "Lê uma lista M3U guardada no seu servidor por WebDAV ou FTP.",
                            addressLabel = "Endereço",
                            addressPlaceholder = "webdav://nas.local/media/lista.m3u",
                            userLabel = "Usuário (opcional)",
                            passwordLabel = "Senha (opcional)",
                            credentialsNotice = "As credenciais são usadas só para esta leitura e não ficam salvas.",
                            connect = "Conectar",
                            cancel = "Cancelar",
                            unsupportedAddress = "Endereço não suportado. Use webdav://, http:// ou ftp://",
                        ),
                    ratings =
                        RatingStrings(
                            title = "Avaliações",
                            source = "Nota TMDb",
                            votes = "%s votos",
                            critics = "Críticos",
                            criticKeyLabel = "Chave OMDb (opcional)",
                            criticKeyHint = "Adiciona Tomatometer, Metascore e IMDb. Pegue a sua em omdbapi.com",
                            criticKeyPlaceholder = "Chave da API",
                            criticKeySaved = "Chave salva: as notas da crítica aparecem.",
                            criticKeyAbsent = "Sem chave: aparece só a nota do público do TMDb.",
                            adultKeyTitle = "Capas do guia adulto",
                            adultKeyBody = "O TMDb não cobre esse catálogo, então as capas exigem uma chave do ThePornDB. Sem ela, essas linhas continuam mostrando o título. A chave é sua: não é enviada a mais nenhum lugar.",
                            adultKeyPlaceholder = "Chave da API",
                            adultKeySaved = "Chave salva: as capas serão buscadas.",
                            adultKeyAbsent = "Sem chave: essas linhas mostram apenas o título.",
                            adultKeySite = "Obter uma chave em theporndb.net",
                            criticGuideButton = "Não sabe como obter?",
                            criticGuideTitle = "Como obter uma chave OMDb",
                            criticGuideSubtitle = "Quatro passos. A chave gratuita chega por e-mail.",
                            criticGuideOpenSite = "Abrir omdbapi.com",
                            criticStep1Title = "Abra omdbapi.com",
                            criticStep1Body =
                                "Vá até a secção API Key do site. Não há conta para criar — o site " +
                                    "pede apenas um endereço de e-mail.",
                            criticStep2Title = "Escolha o plano gratuito",
                            criticStep2Body =
                                "Marque FREE, que permite 1.000 consultas por dia — muito mais do que " +
                                    "este aplicativo usa. O plano pago não é necessário.",
                            criticStep3Title = "Informe o seu e-mail e envie",
                            criticStep3Body =
                                "Use um endereço que você possa abrir agora e descreva o uso em poucas " +
                                    "palavras; \"uso pessoal\" é uma resposta correta.",
                            criticStep4Title = "Ative a chave pelo e-mail",
                            criticStep4Body =
                                "A chave chega com um link de ativação. Abra esse link, senão a chave " +
                                    "continua inativa, e depois cole a chave aqui nas configurações.",
                            criticSketchEmail = "Seu e-mail",
                            criticSketchFree = "FREE",
                            criticSketchSubmit = "Enviar",
                            criticSketchInbox = "Chave + ativação",
                        ),
                    discovery =
                        DiscoveryStrings(
                            title = "Descobrir",
                            hint = "Deslize ou use os botões: guardar vai para os favoritos.",
                            keep = "Guardar",
                            skip = "Pular",
                            details = "Detalhes",
                            exhausted = "Você viu tudo por enquanto.",
                            another = "Buscar mais",
                            loading = "Montando sua seleção...",
                            kept = "Guardado nos favoritos",
                        ),
                    settingsTabs =
                        SettingsTabStrings(
                            general = "Geral",
                            content = "Conteúdo",
                            subtitles = "Legendas",
                            data = "Dados",
                            maintenance = "Manutenção",
                        ),
                    cache =
                        CacheStrings(
                            title = "Guardar capas neste computador",
                            explanation = "O app guarda as capas e imagens no seu computador para que a lista abra na hora, sem baixar tudo de novo a cada vez.",
                            firstTimeWarning = "Na primeira vez pode demorar: depende do tamanho da sua lista. O download acontece em segundo plano e você pode usar o app normalmente.",
                            sizeLabel = "Espaço reservado",
                            gigabytes = "%d GB",
                            disabled = "Não guardar",
                            estimate = "Sua lista precisa de aproximadamente %s.",
                            start = "Começar",
                            skip = "Agora nao",
                            filling = "Guardando capas",
                            progress = "%1${'$'}d de %2${'$'}d",
                            pause = "Pausar",
                            resume = "Continuar",
                            cancel = "Cancelar",
                            complete = "Tudo guardado.",
                            used = "Em uso: %s",
                            clear = "Limpar cache",
                            clearTitle = "Limpar o cache de capas?",
                            clearBody = "As capas já guardadas serão apagadas e precisarão ser baixadas de novo. Nada da sua lista é perdido.",
                            restartNote = "Mudar o tamanho vale a partir da próxima vez que você abrir o app.",
                            percent = "%d%%",
                            refresh = "Atualizar",
                        ),
                    serviceCatalogue =
                        ServiceCatalogueStrings(
                            seeMore = "Ver mais",
                            allFrom = "Tudo de %s",
                            backToShelves = "Voltar aos servicos",
                            genreSelector = "Gênero",
                            serviceSelector = "Serviço",
                            allGenres = "Todos os gêneros",
                            allServices = "Todos os serviços",
                            servicesUnavailable = "não informado na sua lista",
                            servicesLoading = "procurando…",
                            duplicatesLabel = "Cópias repetidas",
                            duplicatesHint = "As listas costumam trazer o mesmo filme várias vezes, uma por qualidade ou dublagem.",
                            duplicatesToggle = "Mostrar só um card por filme",
                        ),
                    receiver =
                        CastReceiverStrings(
                            title = "Receber do celular",
                            hint =
                                "Deixa o celular encontrar este computador e enviar um título " +
                                    "para cá. Os dois precisam estar na mesma rede. Digite o " +
                                    "código abaixo no celular, uma vez.",
                            receiveNow = "Receber agora",
                            autoStart = "Ligar sozinho ao abrir o app",
                            codeLabel = "Código",
                            codeExplanation =
                                "Este código é sempre o mesmo neste computador. Digite uma vez " +
                                    "no celular e ele não pede de novo. Só quem tem este número " +
                                    "pode enviar para cá.",
                            regenerate = "Gerar um código novo",
                        ),
                    share = "Compartilhar",
                    shareTitle = "Compartilhar título",
                    shareSubtitle = "Envie uma recomendação, não a sua lista.",
                    shareDestination = "Enviar por",
                    shareByEmail = "E-mail",
                    shareCopyLink = "Copiar link",
                    shareCopied = "Copiado!",
                    shareNoCredentials =
                        "O link não contém seu servidor, usuário ou senha. " +
                            "Quem receber abre com a lista dele.",
                    shareNotFoundTitle = "Não está na sua lista",
                    shareNotFoundBody =
                        "Seu provedor não oferece este título. Um link compartilhado é uma " +
                            "recomendação: cada pessoa abre com a própria lista.",
                    ),
                search = "Pesquisa",
                sources = "FONTES",
                profile = "Perfil",
                yourLibrary = "Sua biblioteca",
                connectXtream = "Conectar Xtream",
                importM3u = "Importar M3U",
                checkUpdate = "Verificar atualização",
                refreshCatalog = "Atualizar listas",
                termsTitle = "Antes de começar",
                termsNoContent = "O IPTV BURO não oferece, hospeda nem revende nenhum canal, filme ou série.",
                termsYourSource = "Você usa a sua própria lista: o aplicativo apenas reproduz o que o seu provedor entrega.",
                termsResponsibility = "Você é responsável por ter direito de acesso ao conteúdo que adicionar.",
                                termsNoWarranty = "O funcionamento depende da lista e da internet do usuário. Não garantimos que qualquer provedor, canal ou formato funcione.",
                termsOneDevice = "A licença vale para um computador. Trocar de máquina exige uma nova ativação.",
                termsRefund = "Você tem 7 dias de teste antes de pagar. Após a compra, o direito de arrependimento segue o Código de Defesa do Consumidor.",
                termsReadFull = "Ler os termos completos",
                termsAccept = "Concordo e continuar",
                setupTitle = "Crie o seu perfil",
                setupSubtitle = "Cada perfil guarda os próprios favoritos. Os downloads ficam disponíveis para todos.",
                setupProfileName = "Nome do perfil",
                setupUseExisting = "USAR UMA LISTA JÁ CONFIGURADA",
                setupYourList = "SUA LISTA",
                setupNewList = "OU ADICIONAR UMA NOVA LISTA",
                setupListName = "Nome da lista",
                serverLabel = "Servidor",
                usernameLabel = "Usuário",
                passwordLabel = "Senha",
                setupContinue = "Continuar",
                connectingTitle = "Carregando a sua lista",
                connectingBody = "Conectando ao provedor e preparando o catálogo. Isso pode levar alguns instantes.",
                setupFailedTitle = "Não foi possível carregar a lista",
                setupFailedBody = "O provedor não respondeu. Confira o servidor, o usuário e a senha.",
                setupRetry = "Tentar novamente",
                chooseRating = "Nota",
                anyRating = "Todas as notas",
                continueEmptyTitle = "Nada para continuar",
                continueEmptyBody = "Os filmes e episódios que você começar aparecem aqui.",
                resumeFrom = "Continuar",
                startOver = "Do início",
                forgetProgress = "Remover",
                metadataKeyLabel = "Chave TMDb (opcional)",
                                tmdbGuide =
                                    TmdbGuideStrings(
                                        tmdbGuideTitle = "Como obter a sua chave TMDb",
                                        tmdbGuideSubtitle = "Seis passos. É gratuito e leva cerca de cinco minutos.",
                                        tmdbGuideOpenSignup = "Criar conta no TMDb",
                                        tmdbGuideOpenApiPage = "Já tenho conta",
                                        tmdbGuideButton = "Não sabe como obter?",
                                        tmdbStep1Title = "Crie uma conta gratuita",
                                        tmdbStep1Body = "Abra themoviedb.org e clique em Sign Up. Precisa de um email, um nome de utilizador e uma palavra-passe.",
                                        tmdbStep2Title = "Confirme o email e entre",
                                        tmdbStep2Body = "O TMDb envia um email de confirmação. Sem confirmar, a página da API não fica disponível.",
                                        tmdbStep3Title = "Abra Settings e depois API",
                                        tmdbStep3Body = "Clique na sua foto no canto superior direito, escolha Settings e depois API no menu lateral.",
                                        tmdbStep4Title = "Peça uma chave de programador",
                                        tmdbStep4Body = "Escolha Developer, não Commercial. É a opção correta para uso pessoal e é gratuita.",
                                        tmdbStep5Title = "Preencha o formulário",
                                        tmdbStep5Body = "Nome da aplicação: IPTV BURO. URL: pode indicar qualquer endereço seu. Finalidade: uso pessoal, para ver capas e sinopses.",
                                        tmdbStep6Title = "Copie a chave e cole aqui",
                                        tmdbStep6Body = "Copie o valor de API Key (v3 auth), com 32 caracteres, e cole no campo desta janela. É guardada apenas no seu computador.",
                                        tmdbSketchSignUp = "Sign Up",
                                        tmdbSketchApiMenu = "API",
                                        tmdbSketchRequestType = "Tipo de pedido",
                                        tmdbSketchDeveloper = "Developer",
                                        tmdbSketchFormFields = "Nome, URL, finalidade",
                                        tmdbSketchApiKeyLabel = "API Key (v3 auth)",
                                        tmdbSketchCopy = "Copiar",
                                        tmdbSketchSettings = "Definições",
                                        tmdbSketchPaste = "Colar aqui",
                                    ),
                metadataKeyHint = "Cole a sua chave de themoviedb.org/settings/api",
                metadataKeyPlaceholder = "Chave da API",
                layoutPoster = "Capas",
                layoutCompact = "Compacto",
                layoutList = "Lista",
                removeProfile = "Remover",
                confirmRemoveProfile = "Confirmar?",
                showPassword = "Mostrar",
                hidePassword = "Ocultar",
                avatarUsePhoto = "Usar uma foto",
                avatarRemovePhoto = "Remover foto",
                avatarChoosePhotoTitle = "Escolha uma foto para o perfil",
                checkingUpdate = "Verificando atualização…",
                upToDate = "Você já está na versão mais recente.",
                downloading = "Baixando",
                installerVerified = "Instalador verificado. Atualizando…",
                updateFailed = "A atualização não pôde ser instalada.",
                privateSession = "Sessão privada",
                nothingSensitiveSaved = "Nada sensível é salvo",
                dailySelection = "SELEÇÃO DIÁRIA",
                continueWatching = "Continuar assistindo",
                moviesForToday = "Filmes escolhidos para hoje",
                seriesToExplore = "Séries para continuar explorando",
                liveNow = "Ao vivo agora",
                heroFallbackTitle = "Sua biblioteca está pronta",
                heroSubtitle =
                    "Uma seleção diferente a cada dia, organizada sem misturar toda a biblioteca " +
                        "na mesma tela.",
                watchNow = "Assistir",
                details = "Ver detalhes",
                options = "opções",
                organizingToday = "Organizando a seleção de hoje…",
                seasonalBadge = "DA ÉPOCA",
                tryAgain = "Tentar novamente",
                watched = "assistido",
                onAir = "AO VIVO",
                emptyHeadline = "Toda a sua biblioteca.\nSem ruído.",
                emptyBody =
                    "Importe sua fonte autorizada e deixe o IPTV BURO organizar canais, filmes e " +
                        "séries numa experiência única em todas as telas.",
                emptyBadge = "BURO NOCTURNE  •  BIBLIOTECA PRIVADA",
                credentialsStayLocal = "A fonte é reconectada com o cofre protegido deste usuário.",
                authenticating = "Autenticando e preparando o catálogo…",
                organizingPlaylist = "Organizando sua playlist…",
                noSensitiveData = "Nenhum dado sensível será salvo.",
                close = "Fechar",
                cancel = "Cancelar",
                understood = "Entendi",
                whoIsWatching = "Quem está assistindo?",
                newProfile = "Novo perfil",
                addProfile = "Adicionar",
                kidsProfile = "Perfil infantil",
                adultProfile = "Perfil adulto",
                forgetSource = "Esquecer fonte",
                searchChannel = "Buscar canal…",
                results = "resultados",
                items = "itens",
                sourcesCount = "fontes",
                selectChannel = "Selecione um canal",
                vaultProtected = "Cofre protegido",
                credentialsEncrypted = "Credenciais cifradas pelo Windows",
                endSession = "Encerrar sessão",
                searchCatalog = "Buscar neste catálogo…",
                categories = "Categorias",
                allCategories = "Todas",
                catalog = "Catálogo",
                noMatch = "Nenhum item corresponde ao filtro.",
                previous = "Anterior",
                next = "Próxima",
                page = "Página",
                downloadsNoMatch = "Nada encontrado com esse filtro.",
                allItems = "Tudo",
                allYears = "Todos os anos",
                releasesIn = "Lançamentos",
                sessionActive = "Sessão ativa · conexão protegida no Windows",
                loadingCatalog = "Carregando catálogo…",
                sessionClosed = "Sessão encerrada",
                backToCatalog = "Voltar ao catálogo",
                selectItem = "Selecione um item",
                download = "Baixar",
                downloadInProgress = "Baixando",
                downloaded = "Baixado",
                downloadFailed = "Falha no download",
                removeDownload = "Remover download",
                downloads = "Downloads",
                resetSettings = "Redefinir configuracoes",
                resetConfirm = "Apagar tudo",
                resetWarning = "Isso apaga perfis, favoritos e progresso. Os arquivos baixados sao mantidos.",
                settings = "Configurações",
                languageLabel = "IDIOMA",
                chooseYear = "Escolher ano",
                downloadPaused = "Pausado",
                downloadsEmptyTitle = "Nenhuma copia offline",
                downloadsEmptyBody = "Abra um filme e escolha Baixar. As copias salvas aparecem aqui e tocam sem internet.",
                updateReadyBody = "A atualizacao foi baixada e verificada. O aplicativo vai fechar, o Windows instala a nova versao e o aplicativo abre sozinho. Pode levar cerca de um minuto.",
                updateRestartNow = "Fechar e atualizar",
                updateLater = "Depois",
                music = "Músicas",
                musicHome = "Início",
                musicArtists = "Artistas",
                musicRadio = "Rádio",
                musicNewReleases = "Novidades",
                musicMostPlayed = "Mais tocadas",
                musicTracks = "faixas",
                musicStations = "estações",
                musicAddPlaylist = "Adicionar lista de músicas",
                musicEmptyTitle = "Nenhuma música ainda",
                musicEmptyBody = "Adicione uma lista M3U de músicas ao seu perfil para ouvir por aqui.",
                musicNoArtists = "Esta lista não traz artistas identificados.",
                musicNoRadio = "Esta lista não traz estações de rádio.",
                musicNoDownloads = "As músicas que você baixar aparecem aqui.",
                musicBackToArtists = "Voltar aos artistas",
                musicPlaylistLabel = "Lista de músicas",
                musicPlaylistOptional = "OPCIONAL",
                musicPlaylistHint = "Um M3U só de músicas. Sem ele, nada muda no aplicativo.",
                musicPlaylistChoose = "Escolher arquivo",
                musicPlaylistRemove = "Remover",
                musicPlaylistTitle = "Escolha a sua lista de músicas",
                musicPlaylists = "Playlists",
                musicPlaylistsEmpty = "Você ainda não criou nenhuma playlist.",
                musicPlaylistNew = "Nova playlist",
                musicPlaylistNewName = "Minha playlist",
                musicPlaylistRename = "Renomear",
                musicPlaylistDelete = "Excluir",
                musicPlaylistDuplicate = "Duplicar",
                musicPlaylistDuplicateSuffix = "cópia",
                musicPlaylistImport = "Importar M3U",
                musicPlaylistExport = "Exportar M3U",
                musicPlaylistBack = "Voltar às playlists",
                musicPlaylistEmptyTracks = "Esta playlist ainda não tem faixas.",
                musicPlaylistRemoveTrack = "Remover da playlist",
                musicPlaylistMoveUp = "Mover para cima",
                musicPlaylistMoveDown = "Mover para baixo",
                musicSmartPlaylists = "Playlists inteligentes",
                musicSmartFavourites = "Favoritas",
                musicSmartRecentlyPlayed = "Tocadas recentemente",
                musicSmartMostPlayed = "Mais tocadas",
                musicSmartNeverPlayed = "Nunca tocadas",
                musicSmartRecentlyAdded = "Adicionadas recentemente",
                musicExportWarningTitle = "Este arquivo pode conter endereços sensíveis",
                musicExportWarningBody =
                    "Alguns endereços desta playlist parecem incluir credenciais ou assinaturas de acesso. " +
                        "Quem receber o arquivo poderá usar a sua assinatura. Exporte apenas para você mesmo.",
                musicExportWarningConfirm = "Exportar mesmo assim",
                musicExportWarningCancel = "Cancelar",
                queueTitle = "Fila",
                queueNowPlaying = "Tocando agora",
                queueUpNext = "A seguir",
                queueEmptyBody = "Nada na fila. Use “Tocar em seguida” ou “Adicionar ao final”.",
                queuePlayNow = "Tocar agora",
                queuePlayNext = "Tocar em seguida",
                queueAddToEnd = "Adicionar ao final",
                queueRemove = "Remover da fila",
                queueClear = "Limpar",
                queueMoveUp = "Mover para cima",
                queueMoveDown = "Mover para baixo",
                queueOpen = "Abrir a fila",
                queueClose = "Fechar a fila",
                queueCount = "na fila",
                subscriptions = "Assinaturas",
                subscriptionsDemoBadge = "DEMO",
                subscriptionsDemoNotice =
                    "Estes resultados são de exemplo, criados apenas para demonstrar a tela. " +
                        "Nenhum serviço de streaming real está conectado.",
                subscriptionsWhereToWatch = "Onde assistir",
                subscriptionsInYourLibrary = "Já está na sua lista",
                subscriptionsIncludedInSubscription = "Incluído na sua assinatura",
                subscriptionsFreeWithAds = "Grátis com anúncios",
                subscriptionsRent = "Alugar",
                subscriptionsBuy = "Comprar",
                subscriptionsRequiresSubscription = "Requer assinatura",
                subscriptionsUnavailable = "Indisponível por aqui",
                subscriptionsOpenProvider = "Abrir no serviço oficial",
                subscriptionsMyServices = "Meus serviços",
                subscriptionsRegion = "Região",
                languageHint = "Idioma dos textos do aplicativo",
                regionHint = "País usado para saber quais serviços de streaming têm cada filme",
                metadataKeyUses = "Usada para capas, elenco, trailers e a aba Assinaturas",
                metadataKeySaved = "✓ Chave salva. Já está em uso.",
                metadataKeyUsingBundled = "Usando a chave padrão do aplicativo.",
                settingsText =
                    SettingsStrings(
                        profileEdit = "Editar",
                        profileEditTitle = "Editar perfil",
                        profileNameLabel = "Nome",
                        profileAvatarLabel = "Imagem",
                        profileKidsLabel = "Modo infantil",
                        profileKidsHint = "Mostra apenas conteúdo para crianças",
                        profileSourceLabel = "Lista",
                        profileSourceNone = "Usar a que já está conectada",
                        profileSourceChange = "Trocar lista",
                        profileMusicLabel = "Música (M3U)",
                        profileMusicNone = "Nenhum arquivo escolhido",
                        profileMusicChoose = "Escolher arquivo",
                        profileMusicClear = "Remover",
                        profileSave = "Salvar",
                        expandSidebar = "Expandir",
                        subtitlesLabel = "Legendas",
                        subtitlesHint = "Vale para o próximo filme aberto",
                        subtitlesBackground = "Fundo escuro",
                        historyTitle = "Histórico",
                        historyClearAll = "Apagar tudo",
                        historyEmpty = "Nada assistido ainda.",
                        categoriesLabel = "Categorias",
                        categoriesHint = "Esconda o que não usa, ou proteja com a senha",
                        categoryHide = "Ocultar",
                        categoryLock = "Proteger",
                        clockLabel = "Relógio",
                        clockHint = "Formato da hora mostrada no topo",
                        clock24h = "24 horas",
                        clock12h = "12 horas",
                        parentalTitle = "Controle dos pais",
                        parentalHint = "Protege categorias com uma senha de 4 dígitos",
                        parentalSetPin = "Criar senha",
                        parentalChangePin = "Alterar senha",
                        parentalRemovePin = "Remover senha",
                        parentalCurrentPin = "Senha atual",
                        parentalNewPin = "Nova senha",
                        parentalWrongPin = "Senha incorreta.",
                        parentalDefaultPin = "Bloqueio ativo com a senha padrão 0000. Escolha a sua para que ninguém a adivinhe.",
                        parentalPinSaved = "✓ Senha salva.",
                        parentalPinFormat = "A senha precisa ter 4 números.",
                        parentalLockAdult = "Bloquear categorias adultas automaticamente",
                        parentalLocked = "Conteúdo protegido",
                        firstRunTitle = "A preparar o IPTV BURO",
                        firstRunBody =
                            "Só desta vez demora mais: a sua lista está a ser lida e organizada. " +
                                "Nas próximas aberturas isto já estará pronto.",
                        firstRunTmdbTitle = "Capas e sinopses",
                        firstRunTmdbBody =
                            "Para ver capas, sinopses e elenco, adicione uma chave do TMDb em " +
                                "Opções. É gratuita: crie uma conta em themoviedb.org, peça a chave " +
                                "de API e cole-a no BURO.",
                        startupAuthenticating = "Autenticando…",
                        startupOrganising = "Organizando a sua lista…",
                        profileKeyLabel = "Chave só deste perfil",
                        profileKeyHint =
                            "Deixe em branco para usar a mesma chave dos outros perfis. " +
                                "Preencha para este perfil usar a sua própria conta do TMDb.",
                        profileKeyShared = "Usando a chave compartilhada",
                        profileKeyOwn = "Este perfil usa a própria chave",
                        moreSettingsTitle = "Mais opções",
                        moreSettingsHint = "Legendas, relógio, categorias e controle dos pais",
                        multiviewAdd = "Ver junto",
                        multiviewRemove = "Tirar da tela dupla",
                        multiviewOpen = "Ver junto",
                        multiviewHint = "Ver 2 a 4 canais juntos",
                                                musicWorkshop = "Oficina de músicas",
                        musicWorkshopSummary = "%d faixas · %d corrigidas",
                        musicWorkshopNames = "Nomes",
                        musicWorkshopDuplicates = "Repetidas",
                        musicWorkshopApplyAll = "Corrigir todas (%d)",
                        musicWorkshopApplyOne = "Corrigir",
                        musicWorkshopUndoAll = "Desfazer todas as %d correções",
                        musicWorkshopNothingToFix = "Nada a corrigir. Os nomes já estão limpos.",
                        musicWorkshopNoDuplicates = "Nenhuma faixa repetida.",
                        musicWorkshopSameAddress = "Mesmo endereço — com certeza a mesma faixa",
                        musicWorkshopSameName = "Mesmo nome — confira antes de remover",
                        multiviewAudioFrom = "Áudio de",
                        multiviewFullScreen = "Tela cheia",
                        multiviewWindowed = "Janela",
                        multiviewUnavailable = "Não foi possível abrir a tela dupla",
                        multiviewUnavailableHint = "Os canais escolhidos não responderam. Tente outros.",
                        multiviewEmpty = "Escolha os canais primeiro",
                        multiviewEmptyHint = "Passe o mouse sobre um canal ao vivo e clique em ▦ para juntar até 4.",
                        multiviewClear = "Limpar",
                        multiviewFull = "Máximo de %d canais",
                        licenseTrialOverTitle = "Seus 7 dias terminaram",
                        licenseTrialOverBody =
                            "Esperamos que tenha gostado. Para continuar assistindo, ative este " +
                                "dispositivo.",
                        licenseExpiredTitle = "Sua licença expirou",
                        licenseExpiredBody = "Renove para continuar usando o IPTV BURO neste dispositivo.",
                        licenseRevokedTitle = "Licença cancelada",
                        licenseRevokedBody =
                            "Esta licença foi cancelada. Se acha que houve um engano, fale connosco.",
                        licenseVerifyTitle = "Precisamos verificar sua licença",
                        licenseVerifyBody =
                            "Já faz um tempo desde a última verificação. Conecte-se à internet " +
                                "uma vez e volta ao normal.",
                        licenseOfflineTitle = "Sem conexão",
                        licenseOfflineBody =
                            "Não conseguimos verificar sua licença. Confira sua internet e tente de novo.",
                        licenseTwoYears = "por 2 anos",
                        licenseWhyNotLifetime =
                            "Não é vitalício porque o app continua sendo mantido e atualizado: " +
                                "provedores mudam, formatos mudam, o Windows muda.",
                        licenseBuy = "Ativar dispositivo",
                        licenseRetry = "Tentar de novo",
                        licenseDeviceLabel = "Seu dispositivo",
                        licenseHaveKey = "Tenho um código de ativação",
                        licenseKeyPlaceholder = "Código",
                        licenseRedeem = "Usar código",
                        licenseDaysLeft = "Faltam %d dias",
                                                epgShowSchedule = "Ver programação (%d)",
                        epgHideSchedule = "Ocultar programação",
                        parentalUnlock = "Digite a senha para ver esta categoria",
                    ),
                subscriptionsSynopsis = "Sinopse",
                subscriptionsCast = "Elenco",
                subscriptionsWatchTrailer = "▶  Ver trailer",
                subscriptionsAvailableOn = "Disponível em",
                subscriptionsFilterMovies = "Filmes",
                subscriptionsFilterSeries = "Séries",
                subscriptionsFilterUpcoming = "Em breve",
                subscriptionsFilterThisWeek = "Esta semana",
                subscriptionsUpcomingNote =
                    "Lançamentos com data marcada. Ainda não se sabe em qual serviço cada título vai estrear.",
                subscriptionsEmptyBody = "Nada encontrado para este título.",
                subscriptionsBrowseByService = "Por serviço",
                subscriptionsNoShelves = "Nenhum serviço para mostrar ainda.",
                subscriptionsLoadFailed = "Não foi possível carregar os serviços. Confira a conexão e tente novamente.",
                subscriptionsKeyRejected =
                    "O TMDb recusou a chave de API. Confira a chave em Opções — uma chave nova pode levar alguns minutos para valer.",
                subscriptionsBackToServices = "Voltar aos serviços",
                subscriptionsSelectedTitle = "Título selecionado",
                licenseText =
                    LicenseStrings(
                        trialTitle = "Seu período de teste terminou",
                        trialBody =
                            "Os 7 dias acabaram. Ative este dispositivo para continuar usando o IPTV BURO.",
                        expiredTitle = "Licença expirada",
                        expiredBody =
                            "A licença deste dispositivo venceu. Renove para continuar.",
                        revokedTitle = "Licença cancelada",
                        revokedBody =
                            "Esta licença não está mais ativa. Se achar que é um engano, escreva para "
                                + "nós informando o código abaixo.",
                        unreachableTitle = "Não foi possível verificar a licença",
                        unreachableBody =
                            "Não conseguimos falar com o servidor. Confira sua conexão e tente de novo.",
                        verifyTitle = "Precisa verificar",
                        verifyBody =
                            "O aplicativo ficou tempo demais sem verificar. Conecte-se à internet uma "
                                + "vez para continuar.",
                        deviceLabel = "Dispositivo",
                        activationKeyLabel = "Chave de ativação",
                        activationKeyHint = "Guarde esta chave. Ela vale só neste computador; perdê-la obriga a comprar outra.",
                        macLabel = "MAC",
                        scanHint = "Aponte a câmera do celular para o código",
                        openInBrowser = "Abrir no navegador",
                        retry = "Tentar de novo",
                        haveKey = "Tem um código de ativação?",
                        keyPlaceholder = "XXXX-XXXX",
                        redeem = "Ativar",
                        redeemFailed = "Código inválido ou já usado.",
                        keyAvailable = "Chave válida e livre.",
                        keyAvailableDays = { days -> "Chave de $days dias, livre." },
                        keyYours = "Esta chave já é deste aparelho.",
                        keyInUse = "Esta chave já foi usada em outro aparelho.",
                        keyExpired = "Esta chave expirou.",
                        quit = "Fechar",
                        back = "Voltar ao aplicativo",
                        backToPurchase = "Prefiro pagar",
                        termYears = "%d anos",
                        priceEur = "€ 9,90 · 2 anos",
                        priceUsd = "US$ 9.90 · 2 anos",
                        priceBrl = "R$ 99,90 · 2 anos",
                        whyNotLifetime =
                            "Por que não é vitalício: o aplicativo continua sendo mantido e atualizado. "
                                + "Os 2 anos pagam esse trabalho.",
                        copied = "Copiado",
                        clockWarning =
                            "O relógio deste computador parece errado. As datas da licença vêm do "
                                + "servidor de qualquer forma.",
                        trialDaysLeft = "Faltam %d dias de teste",
                        licenseDaysLeft = "Faltam %d dias",
                        licenseLastDay = "Último dia",
                        trialLastDay = "Último dia de teste",
                        buyNow = "Ativar",
                    ),
            )

        private val En =
            DesktopStrings(
                library = "LIBRARY",
                home = "Home",
                live = "Live TV",
                movies = "Movies",
                series = "Series",
                downloadStrings =
                    DownloadStrings(
                        downloadSeries = "Download series",
                        downloadSeason = "Download season %d",
                        downloadSeriesConfirmTitle = "Download the whole series?",
                        downloadSeasonConfirmTitle = "Download season %d?",
                        downloadConfirmBody = "%d episodes will be downloaded. This can use a lot of storage and data.",
                        downloadConfirmAction = "Download",
                    ),
                savedForLater =
                    SavedForLaterStrings(
                        favorites = "Favorites",
                        reminderAdd = "Remind me",
                        reminderActive = "Reminder set",
                        reminderNoNotice = "Saved on this computer. The notice appears here in the app.",
                        reminderAnnounce = "Tell me about my reminders",
                        reminderHourLabel = "Tell me at",
                        reminderInAppOnly =
                            "The notice appears inside the app, the first time you open it after " +
                                "the hour you chose. The app does not send Windows notifications " +
                                "while it is closed.",
                        reminderNoticeBody = "You have %d title(s) marked to watch.",
                        reminderNoticeDismiss = "Got it",
                        remindersTitle = "Reminders",
                        remindersEmpty =
                            "You have not marked anything yet. Use the Remind me button on a " +
                                "film, a series, or an upcoming release under Subscriptions.",
                        reminderOpen = "Open",
                        reminderRemove = "Remove",
                        reminderNotInLibrary = "Not in your list yet",
                        newEpisodeBody = "New episode: S%1${'$'}d E%2${'$'}d",
                        newSeasonBody = "New season: S%1${'$'}d",
                    ),
                shareStrings =
                    ShareStrings(
                        cast =
                            CastStrings(
                                            castAction = "Send to screen",
                                            castTitle = "Send to a screen",
                                            castSearching = "Looking for screens on this network…",
                                            castNoneFound = "No screen found. Open IPTV BURO on the other device, turn on “Receive”, and make sure both are on the same network.",
                                            castManualTitle = "Or enter the address",
                                            castManualHint = "Some routers block the search between devices. The other screen shows its address under “Receive”.",
                                            castManualLabel = "Address on this network",
                                            castManualConnect = "Connect",
                                            castManualInvalid = "That does not look like an address on this network.",
                                            castSearchAgain = "Search again",
                                            castCodePrompt = "Send to %s",
                                            castCodeHint = "Type the four digits shown on that screen.",
                                            castCodeInvalid = "The code is four digits.",
                                            castSend = "Send",
                                            castSending = "Sending to %s…",
                                            castSent = "Sent to %s. If nothing starts, check the code on that screen.",
                                            castFailed = "Could not reach %s.",
                                            castChooseAnother = "Choose another screen",
                            ),
                    notifications =
                        NotificationStrings(
                            title = "Notifications",
                            empty = "Nothing here.",
                            clearAll = "Clear all",
                            dismiss = "Dismiss",
                        ),
                    failures =
                        FailureStrings(
                            sessionExpired =
                                "Your list's session has expired. The catalogue is still saved, but you " +
                                    "need to sign in to the source again to load anything new.",
                            outOfMemory =
                                "There was not enough memory to build this screen. " +
                                    "That is a limit of the application, not of your list.",
                            invalidServer = "The server address is not valid.",
                            invalidServerScheme = "Check the start of the address: it must be http:// or https://.",
                            authenticationRejected = "The server refused the username or password.",
                            networkUnreachable = "The server could not be reached.",
                            httpError = "The server answered with an HTTP error.",
                            responseTooLarge = "The catalogue exceeded this build's safe limit.",
                            invalidResponse = "The server did not return a compatible Xtream catalogue. Details in %1${'$'}s",
                            appFault =
                                "This screen could not be built (%1${'$'}s). That is a fault in the " +
                                    "application, not in your list. Details in %2${'$'}s",
                        ),
                    startup =
                        StartupStrings(
                            openingSession = "Opening your session…",
                            joiningList = "Merging %1${'$'}s…",
                            loadingLiveCategories = "Loading channel categories…",
                            loadingMovieCategories = "Loading film categories…",
                            loadingSeriesCategories = "Loading series categories…",
                            downloadingMovies = "Downloading the film list…",
                            downloadingSeries = "Downloading the series list…",
                            organising = "Organising films and series…",
                            ready = "Ready",
                        ),
                    screens =
                        ScreenStrings(
                            setupMissingProfileName = "Enter a profile name to continue",
                            setupMissingConnection = "Fill in the server, username and password, or pick a playlist you already have",
                            diagnosticsAction = "Diagnostics",
                            diagnosticsTitle = "Connection diagnostics",
                            mergeSourcesTitle = "Merge every playlist",
                            mergeSourcesHelp = "Shows all your playlists as one catalogue. Nothing repeats: the biggest list leads and the others fill the gaps.",
                            mergeSourcesRestart = "The lists are reorganised right away.",
                            mergeSourcesFailed = "%1${'$'}s did not answer. Your other playlists are still working.",
                            mergeSourcesOffline = "Not answering",
                            diagnosticsLatencyGood = "Low latency: channels change without waiting",
                            diagnosticsLatencyFair = "High latency: this can stall and slow channel changes",
                            diagnosticsLatencyUnstable = "Very high latency: this will cause freezing and stalls",
                            diagnosticsLatencyUnknown = "The latency could not be measured",
                            diagnosticsRunning = "Testing…",
                            diagnosticsRun = "Test again",
                            diagnosticsClose = "Close",
                            diagnosticsDownload = "Download",
                            diagnosticsUpload = "Upload",
                            diagnosticsPing = "Latency",
                            diagnosticsLoss = "Packet loss",
                            diagnosticsCatalogue = "Playlist",
                            diagnosticsConnection = "Connection",
                            diagnosticsMemory = "Memory",
                            diagnosticsAddress = "IP address",
                            diagnosticsGateway = "Gateway",
                            diagnosticsNetmask = "Subnet mask",
                            diagnosticsVerdictGood = "Everything looks fine",
                            diagnosticsVerdictWarning = "Worth a look",
                            diagnosticsVerdictProblem = "Problem found",
                            diagnosticsQualityUnstable = "Poor connection: this will cause freezing and stalls",
                            diagnosticsQualitySd = "Modest connection: enough for standard quality",
                            diagnosticsQualityHd = "Good connection for 1080p films",
                            diagnosticsQualityUhd = "Perfect connection for 4K and live TV",
                            diagnosticsQualityUnknown = "The speed could not be measured",
                            diagnosticsWireless = "Wi-Fi: a cable avoids the stalls wireless tends to cause",
                            diagnosticsWired = "Wired",
                            diagnosticsNoLink = "No network connection",
                            diagnosticsCatalogueEmpty = "The playlist did not load: check your subscription with your seller",
                            diagnosticsSignedOut = "No playlist configured",
                            diagnosticsLowMemory = "Low memory: close other programs or restart the app",
                            deviceCodeAction = "Device code",
                            deviceCodeHelp = "Send this code to whoever sold you your playlist and they can set it up for you.",
                            setupRenameList = "Rename",
                            setupRemoveList = "Remove",
                            setupRemoveListConfirm = "Remove the list «%1${'$'}s»? Its password is erased too.",
                            importFileMissing = "The selected file no longer exists.",
                            importAccessDenied = "The system would not allow that file to be read.",
                            importBlocked = "Access to the file was blocked by the system.",
                            importFailed = "Could not import the playlist. Check that the file is valid M3U/M3U8 and try again.",
                            movieDetailsLoading = "Loading the film details…",
                            epgLoading = "Loading now and next…",
                            guideNow = "Now",
                            guideNext = "Next",
                            catchUpShow = "Watch again (%1${'$'}s)",
                            catchUpHide = "Hide",
                            similarTitles = "Similar titles",
                            epgUnavailable = "No guide available; the channel still works.",
                            epgEmpty = "The source listed no programmes.",
                            loadEpisodes = "Load episodes",
                            episodesLoading = "Loading episodes…",
                            externalOpenFailed = "Could not open",
                            externalNoDefaultApp = "This system offers no default application for opening the channel.",
                            externalRefused = "The external application refused the address. No data was copied.",
                            externalHeadersWarning = "The channel requires HTTP headers; an ordinary browser may not play it.",
                            externalAddressValid = "Address valid for an external application.",
                            headersUnsupported = "This channel requires HTTP headers the current Windows player cannot yet apply. Playback is disabled rather than offering a button that will fail.",
                            noChannelMatches = "No channel matches the filter.",
                            connectXtreamTitle = "Connect an Xtream server",
                            searchingCatalogue = "Searching the catalogue…",
                            noFurtherTitles = "No further titles found in the catalogue.",
                            noPlayableEpisodes = "The server returned no playable episodes.",
                            playerStopped = "The video engine stopped unexpectedly.",
                            playerStartFailed = "The Windows video engine could not start.",
                            playerStalled = "The server answered, but this video did not start. Try again or choose another title.",
                            updateCheckFailed = "Could not check for updates right now.",
                            demoMovieNotice = "Example title. This availability is fictional.",
                            demoSeriesNotice = "Example series. This availability is fictional.",
                        ),
                    remoteSource =
                        RemoteSourceStrings(
                            title = "Your own server (NAS)",
                            hint = "Reads an M3U playlist kept on your server over WebDAV or FTP.",
                            addressLabel = "Address",
                            addressPlaceholder = "webdav://nas.local/media/list.m3u",
                            userLabel = "Username (optional)",
                            passwordLabel = "Password (optional)",
                            credentialsNotice = "Credentials are used for this read only and are not stored.",
                            connect = "Connect",
                            cancel = "Cancel",
                            unsupportedAddress = "Unsupported address. Use webdav://, http:// or ftp://",
                        ),
                    ratings =
                        RatingStrings(
                            title = "Ratings",
                            source = "TMDb score",
                            votes = "%s votes",
                            critics = "Critics",
                            criticKeyLabel = "OMDb key (optional)",
                            criticKeyHint = "Adds Tomatometer, Metascore and IMDb. Get one at omdbapi.com",
                            criticKeyPlaceholder = "API key",
                            criticKeySaved = "Key saved: the critics' scores are shown.",
                            criticKeyAbsent = "Without a key only TMDb's audience score is shown.",
                            adultKeyTitle = "Adult guide artwork",
                            adultKeyBody = "TMDb does not cover this catalogue, so its artwork needs a ThePornDB key. Without one those rows keep showing the title. The key is yours: it is sent nowhere else.",
                            adultKeyPlaceholder = "API key",
                            adultKeySaved = "Key saved: artwork will be fetched.",
                            adultKeyAbsent = "No key: those rows show the title only.",
                            adultKeySite = "Get a key at theporndb.net",
                            criticGuideButton = "Not sure how to get one?",
                            criticGuideTitle = "Getting an OMDb key",
                            criticGuideSubtitle = "Four steps. The free key arrives by email.",
                            criticGuideOpenSite = "Open omdbapi.com",
                            criticStep1Title = "Open omdbapi.com",
                            criticStep1Body =
                                "Go to the site's API Key section. There is no account to create — " +
                                    "an email address is all it asks for.",
                            criticStep2Title = "Choose the free plan",
                            criticStep2Body =
                                "Pick FREE, which allows 1,000 requests a day — far more than this " +
                                    "app uses. The paid tier is not needed.",
                            criticStep3Title = "Enter your email and submit",
                            criticStep3Body =
                                "Give an address you can open right away, and describe the use briefly " +
                                    "— \"personal use\" is an accurate answer.",
                            criticStep4Title = "Activate the key from the email",
                            criticStep4Body =
                                "The key arrives with an activation link. Open it, or the key stays " +
                                    "inactive, then paste the key here in settings.",
                            criticSketchEmail = "Your email",
                            criticSketchFree = "FREE",
                            criticSketchSubmit = "Submit",
                            criticSketchInbox = "Key + activation",
                        ),
                    discovery =
                        DiscoveryStrings(
                            title = "Discover",
                            hint = "Swipe or use the buttons: keeping adds it to favourites.",
                            keep = "Keep",
                            skip = "Skip",
                            details = "Details",
                            exhausted = "You have seen everything for now.",
                            another = "Find more",
                            loading = "Building your selection...",
                            kept = "Saved to favourites",
                        ),
                    settingsTabs =
                        SettingsTabStrings(
                            general = "General",
                            content = "Content",
                            subtitles = "Subtitles",
                            data = "Data",
                            maintenance = "Maintenance",
                        ),
                    cache =
                        CacheStrings(
                            title = "Keep artwork on this computer",
                            explanation = "The app keeps posters and images on your computer so the library opens at once, instead of fetching everything again each time.",
                            firstTimeWarning = "The first fill can take a while: it depends on how large your list is. It runs in the background and you can use the app normally.",
                            sizeLabel = "Space reserved",
                            gigabytes = "%d GB",
                            disabled = "Do not keep",
                            estimate = "Your library needs about %s.",
                            start = "Start",
                            skip = "Not now",
                            filling = "Keeping artwork",
                            progress = "%1${'$'}d of %2${'$'}d",
                            pause = "Pause",
                            resume = "Resume",
                            cancel = "Cancel",
                            complete = "Everything is stored.",
                            used = "In use: %s",
                            clear = "Clear cache",
                            clearTitle = "Clear the cover cache?",
                            clearBody = "The stored covers will be deleted and downloaded again as they are needed. Nothing in your library is lost.",
                            restartNote = "A change of size takes effect the next time you open the app.",
                            percent = "%d%%",
                            refresh = "Refresh",
                        ),
                    serviceCatalogue =
                        ServiceCatalogueStrings(
                            seeMore = "See more",
                            allFrom = "All from %s",
                            backToShelves = "Back to services",
                            genreSelector = "Genre",
                            serviceSelector = "Service",
                            allGenres = "All genres",
                            allServices = "All services",
                            servicesUnavailable = "not recorded in your list",
                            servicesLoading = "looking…",
                            duplicatesLabel = "Repeated copies",
                            duplicatesHint = "Lists usually carry the same film several times over, one per quality or dubbing.",
                            duplicatesToggle = "Show one card per film",
                        ),
                    receiver =
                        CastReceiverStrings(
                            title = "Receive from your phone",
                            hint =
                                "Lets your phone find this computer and send it a title. Both " +
                                    "have to be on the same network. Type the code below into " +
                                    "your phone, once.",
                            receiveNow = "Receive now",
                            autoStart = "Turn on when the app opens",
                            codeLabel = "Code",
                            codeExplanation =
                                "This code stays the same on this computer. Type it into your " +
                                    "phone once and it will not ask again. Only someone with " +
                                    "this number can send here.",
                            regenerate = "Generate a new code",
                        ),
                    share = "Share",
                    shareTitle = "Share title",
                    shareSubtitle = "Send a recommendation, not your list.",
                    shareDestination = "Send via",
                    shareByEmail = "Email",
                    shareCopyLink = "Copy link",
                    shareCopied = "Copied!",
                    shareNoCredentials =
                        "The link carries no server, username or password. " +
                            "Whoever receives it opens it with their own list.",
                    shareNotFoundTitle = "Not in your list",
                    shareNotFoundBody =
                        "Your provider does not carry this title. A shared link is a " +
                            "recommendation: everyone opens it with their own list.",
                    ),
                search = "Search",
                sources = "SOURCES",
                profile = "Profile",
                yourLibrary = "Your library",
                connectXtream = "Connect Xtream",
                importM3u = "Import M3U",
                checkUpdate = "Check for update",
                refreshCatalog = "Refresh lists",
                termsTitle = "Before you start",
                termsNoContent = "IPTV BURO does not provide, host or resell any channel, film or series.",
                termsYourSource = "You bring your own playlist: the app only plays what your provider delivers.",
                termsResponsibility = "You are responsible for having the right to access the content you add.",
                                termsNoWarranty = "Playback depends on your own list and connection. We do not guarantee that any provider, channel or format will work.",
                termsOneDevice = "A licence covers one computer. Moving to another machine requires a new activation.",
                termsRefund = "You get a 7-day trial before paying. After purchase, refunds follow the consumer law of your country.",
                termsReadFull = "Read the full terms",
                termsAccept = "I agree, continue",
                setupTitle = "Create your profile",
                setupSubtitle = "Each profile keeps its own favourites. Downloads stay available to everyone.",
                setupProfileName = "Profile name",
                setupUseExisting = "USE A PLAYLIST YOU ALREADY SET UP",
                setupYourList = "YOUR PLAYLIST",
                setupNewList = "OR ADD A NEW PLAYLIST",
                setupListName = "Playlist name",
                serverLabel = "Server",
                usernameLabel = "Username",
                passwordLabel = "Password",
                setupContinue = "Continue",
                connectingTitle = "Loading your playlist",
                connectingBody = "Connecting to the provider and preparing the catalogue. This can take a moment.",
                setupFailedTitle = "The playlist could not be loaded",
                setupFailedBody = "The provider did not respond. Check the server, username and password.",
                setupRetry = "Try again",
                chooseRating = "Rating",
                anyRating = "Any rating",
                continueEmptyTitle = "Nothing to continue",
                continueEmptyBody = "Films and episodes you start appear here.",
                resumeFrom = "Resume",
                startOver = "Start over",
                forgetProgress = "Remove",
                metadataKeyLabel = "TMDb key (optional)",
                                tmdbGuide =
                                    TmdbGuideStrings(
                                        tmdbGuideTitle = "How to get your TMDb key",
                                        tmdbGuideSubtitle = "Six steps. It is free and takes about five minutes.",
                                        tmdbGuideOpenSignup = "Create a TMDb account",
                                        tmdbGuideOpenApiPage = "I already have an account",
                                        tmdbGuideButton = "Not sure how to get one?",
                                        tmdbStep1Title = "Create a free account",
                                        tmdbStep1Body = "Open themoviedb.org and click Sign Up. You need an email address, a username and a password.",
                                        tmdbStep2Title = "Confirm your email and sign in",
                                        tmdbStep2Body = "TMDb sends a confirmation email. Until you confirm it, the API page is not available.",
                                        tmdbStep3Title = "Open Settings, then API",
                                        tmdbStep3Body = "Click your picture at the top right, choose Settings, then API in the side menu.",
                                        tmdbStep4Title = "Request a developer key",
                                        tmdbStep4Body = "Choose Developer, not Commercial. It is the right option for personal use and it is free.",
                                        tmdbStep5Title = "Fill in the form",
                                        tmdbStep5Body = "Application name: IPTV BURO. URL: any address of your own will do. Purpose: personal use, to show artwork and synopses.",
                                        tmdbStep6Title = "Copy the key and paste it here",
                                        tmdbStep6Body = "Copy the API Key (v3 auth) value, 32 characters, and paste it into the field in this window. It is stored only on your computer.",
                                        tmdbSketchSignUp = "Sign Up",
                                        tmdbSketchApiMenu = "API",
                                        tmdbSketchRequestType = "Request type",
                                        tmdbSketchDeveloper = "Developer",
                                        tmdbSketchFormFields = "Name, URL, purpose",
                                        tmdbSketchApiKeyLabel = "API Key (v3 auth)",
                                        tmdbSketchCopy = "Copy",
                                        tmdbSketchSettings = "Settings",
                                        tmdbSketchPaste = "Paste here",
                                    ),
                metadataKeyHint = "Paste your key from themoviedb.org/settings/api",
                metadataKeyPlaceholder = "API key",
                layoutPoster = "Posters",
                layoutCompact = "Compact",
                layoutList = "List",
                removeProfile = "Remove",
                confirmRemoveProfile = "Confirm?",
                showPassword = "Show",
                hidePassword = "Hide",
                avatarUsePhoto = "Use a photo",
                avatarRemovePhoto = "Remove photo",
                avatarChoosePhotoTitle = "Choose a profile photo",
                checkingUpdate = "Checking for update…",
                upToDate = "You are already on the latest version.",
                downloading = "Downloading",
                installerVerified = "Installer verified. Updating…",
                updateFailed = "The update could not be installed.",
                privateSession = "Private session",
                nothingSensitiveSaved = "Nothing sensitive is stored",
                dailySelection = "DAILY SELECTION",
                continueWatching = "Continue watching",
                moviesForToday = "Movies picked for today",
                seriesToExplore = "Series worth exploring",
                liveNow = "Live now",
                heroFallbackTitle = "Your library is ready",
                heroSubtitle =
                    "A different selection every day, arranged without dumping the whole library " +
                        "onto one screen.",
                watchNow = "Watch",
                details = "Details",
                options = "options",
                organizingToday = "Arranging today's selection…",
                seasonalBadge = "IN SEASON",
                tryAgain = "Try again",
                watched = "watched",
                onAir = "LIVE",
                emptyHeadline = "Your whole library.\nNo noise.",
                emptyBody =
                    "Import your authorised source and let IPTV BURO arrange channels, movies and " +
                        "series into one experience across every screen.",
                emptyBadge = "BURO NOCTURNE  •  PRIVATE LIBRARY",
                credentialsStayLocal = "The source reconnects from this user's protected vault.",
                authenticating = "Authenticating and preparing the catalogue…",
                organizingPlaylist = "Organising your playlist…",
                noSensitiveData = "No sensitive data will be stored.",
                close = "Close",
                cancel = "Cancel",
                understood = "Got it",
                whoIsWatching = "Who's watching?",
                newProfile = "New profile",
                addProfile = "Add",
                kidsProfile = "Kids profile",
                adultProfile = "Adult profile",
                forgetSource = "Forget source",
                searchChannel = "Search channel…",
                results = "results",
                items = "items",
                sourcesCount = "sources",
                selectChannel = "Select a channel",
                vaultProtected = "Vault protected",
                credentialsEncrypted = "Credentials encrypted by Windows",
                endSession = "End session",
                searchCatalog = "Search this catalogue…",
                categories = "Categories",
                allCategories = "All",
                catalog = "Catalogue",
                noMatch = "Nothing matches this filter.",
                previous = "Previous",
                next = "Next",
                page = "Page",
                downloadsNoMatch = "Nothing matches that filter.",
                allItems = "All",
                allYears = "All years",
                releasesIn = "Released in",
                sessionActive = "Session active · connection protected on Windows",
                loadingCatalog = "Loading catalogue…",
                sessionClosed = "Session ended",
                backToCatalog = "Back to catalogue",
                selectItem = "Select an item",
                download = "Download",
                downloadInProgress = "Downloading",
                downloaded = "Downloaded",
                downloadFailed = "Download failed",
                removeDownload = "Remove download",
                downloads = "Downloads",
                resetSettings = "Reset settings",
                resetConfirm = "Erase everything",
                resetWarning = "This erases profiles, favourites and progress. Downloaded files are kept.",
                settings = "Settings",
                languageLabel = "LANGUAGE",
                chooseYear = "Choose year",
                downloadPaused = "Paused",
                downloadsEmptyTitle = "No offline copies",
                downloadsEmptyBody = "Open a film and choose Download. Stored copies appear here and play without a connection.",
                updateReadyBody = "The update was downloaded and verified. The app will close, Windows installs the new version, and the app reopens on its own. This can take about a minute.",
                updateRestartNow = "Close and update",
                updateLater = "Later",
                music = "Music",
                musicHome = "Home",
                musicArtists = "Artists",
                musicRadio = "Radio",
                musicNewReleases = "New releases",
                musicMostPlayed = "Most played",
                musicTracks = "tracks",
                musicStations = "stations",
                musicAddPlaylist = "Add a music playlist",
                musicEmptyTitle = "No music yet",
                musicEmptyBody = "Add a music M3U playlist to your profile to listen here.",
                musicNoArtists = "This playlist names no artists.",
                musicNoRadio = "This playlist carries no radio stations.",
                musicNoDownloads = "Music you download appears here.",
                musicBackToArtists = "Back to artists",
                musicPlaylistLabel = "Music playlist",
                musicPlaylistOptional = "OPTIONAL",
                musicPlaylistHint = "A music-only M3U. Without it, nothing in the app changes.",
                musicPlaylistChoose = "Choose file",
                musicPlaylistRemove = "Remove",
                musicPlaylistTitle = "Choose your music playlist",
                musicPlaylists = "Playlists",
                musicPlaylistsEmpty = "You have not created any playlists yet.",
                musicPlaylistNew = "New playlist",
                musicPlaylistNewName = "My playlist",
                musicPlaylistRename = "Rename",
                musicPlaylistDelete = "Delete",
                musicPlaylistDuplicate = "Duplicate",
                musicPlaylistDuplicateSuffix = "copy",
                musicPlaylistImport = "Import M3U",
                musicPlaylistExport = "Export M3U",
                musicPlaylistBack = "Back to playlists",
                musicPlaylistEmptyTracks = "This playlist has no tracks yet.",
                musicPlaylistRemoveTrack = "Remove from playlist",
                musicPlaylistMoveUp = "Move up",
                musicPlaylistMoveDown = "Move down",
                musicSmartPlaylists = "Smart playlists",
                musicSmartFavourites = "Favourites",
                musicSmartRecentlyPlayed = "Recently played",
                musicSmartMostPlayed = "Most played",
                musicSmartNeverPlayed = "Never played",
                musicSmartRecentlyAdded = "Recently added",
                musicExportWarningTitle = "This file may contain sensitive addresses",
                musicExportWarningBody =
                    "Some addresses in this playlist appear to include credentials or access signatures. " +
                        "Anyone who receives the file could use your subscription. Export it only for yourself.",
                musicExportWarningConfirm = "Export anyway",
                musicExportWarningCancel = "Cancel",
                queueTitle = "Queue",
                queueNowPlaying = "Now playing",
                queueUpNext = "Up next",
                queueEmptyBody = "Nothing queued. Use “Play next” or “Add to end”.",
                queuePlayNow = "Play now",
                queuePlayNext = "Play next",
                queueAddToEnd = "Add to end",
                queueRemove = "Remove from queue",
                queueClear = "Clear",
                queueMoveUp = "Move up",
                queueMoveDown = "Move down",
                queueOpen = "Open the queue",
                queueClose = "Close the queue",
                queueCount = "queued",
                subscriptions = "Subscriptions",
                subscriptionsDemoBadge = "DEMO",
                subscriptionsDemoNotice =
                    "These results are examples, made up to demonstrate the screen. " +
                        "No real streaming service is connected.",
                subscriptionsWhereToWatch = "Where to watch",
                subscriptionsInYourLibrary = "Already in your list",
                subscriptionsIncludedInSubscription = "Included in your subscription",
                subscriptionsFreeWithAds = "Free with ads",
                subscriptionsRent = "Rent",
                subscriptionsBuy = "Buy",
                subscriptionsRequiresSubscription = "Requires a subscription",
                subscriptionsUnavailable = "Not available here",
                subscriptionsOpenProvider = "Open in the official service",
                subscriptionsMyServices = "My services",
                subscriptionsRegion = "Region",
                languageHint = "Language of the app's own text",
                regionHint = "Country used to work out which streaming services carry each film",
                metadataKeyUses = "Used for posters, cast, trailers and the Subscriptions tab",
                metadataKeySaved = "✓ Key saved. It is in use now.",
                metadataKeyUsingBundled = "Using the app's default key.",
                settingsText =
                    SettingsStrings(
                        profileEdit = "Edit",
                        profileEditTitle = "Edit profile",
                        profileNameLabel = "Name",
                        profileAvatarLabel = "Picture",
                        profileKidsLabel = "Kids mode",
                        profileKidsHint = "Shows only children's content",
                        profileSourceLabel = "Playlist",
                        profileSourceNone = "Use whichever is connected",
                        profileSourceChange = "Change playlist",
                        profileMusicLabel = "Music (M3U)",
                        profileMusicNone = "No file chosen",
                        profileMusicChoose = "Choose file",
                        profileMusicClear = "Remove",
                        profileSave = "Save",
                        expandSidebar = "Expand",
                        subtitlesLabel = "Subtitles",
                        subtitlesHint = "Applies to the next title you open",
                        subtitlesBackground = "Dark background",
                        historyTitle = "History",
                        historyClearAll = "Clear all",
                        historyEmpty = "Nothing watched yet.",
                        categoriesLabel = "Categories",
                        categoriesHint = "Hide what you do not use, or protect it with the PIN",
                        categoryHide = "Hide",
                        categoryLock = "Protect",
                        clockLabel = "Clock",
                        clockHint = "How the time reads in the header",
                        clock24h = "24-hour",
                        clock12h = "12-hour",
                        parentalTitle = "Parental controls",
                        parentalHint = "Protects categories with a four-digit PIN",
                        parentalSetPin = "Create a PIN",
                        parentalChangePin = "Change PIN",
                        parentalRemovePin = "Remove PIN",
                        parentalCurrentPin = "Current PIN",
                        parentalNewPin = "New PIN",
                        parentalWrongPin = "Wrong PIN.",
                        parentalDefaultPin = "Locked with the standard PIN 0000. Choose your own so it cannot be guessed.",
                        parentalPinSaved = "✓ PIN saved.",
                        parentalPinFormat = "The PIN must be four digits.",
                        parentalLockAdult = "Lock adult categories automatically",
                        parentalLocked = "Protected content",
                        firstRunTitle = "Setting up IPTV BURO",
                        firstRunBody =
                            "This one time takes longer: your list is being read and organised. " +
                                "It will already be ready the next time you open the app.",
                        firstRunTmdbTitle = "Artwork and synopses",
                        firstRunTmdbBody =
                            "For posters, synopses and cast, add a TMDb key in Settings. It is " +
                                "free: create an account at themoviedb.org, request the API key and " +
                                "paste it into BURO.",
                        startupAuthenticating = "Signing in…",
                        startupOrganising = "Organising your list…",
                        profileKeyLabel = "Key for this profile only",
                        profileKeyHint =
                            "Leave blank to use the same key as the other profiles. Fill it in for " +
                                "this profile to use its own TMDb account.",
                        profileKeyShared = "Using the shared key",
                        profileKeyOwn = "This profile uses its own key",
                        moreSettingsTitle = "More settings",
                        moreSettingsHint = "Subtitles, clock, categories and parental controls",
                        multiviewAdd = "Watch together",
                        multiviewRemove = "Remove from multiview",
                        multiviewOpen = "Watch together",
                        multiviewHint = "Watch 2 to 4 channels together",
                                                musicWorkshop = "Music workshop",
                        musicWorkshopSummary = "%d tracks · %d corrected",
                        musicWorkshopNames = "Names",
                        musicWorkshopDuplicates = "Repeated",
                        musicWorkshopApplyAll = "Fix all (%d)",
                        musicWorkshopApplyOne = "Fix",
                        musicWorkshopUndoAll = "Undo all %d corrections",
                        musicWorkshopNothingToFix = "Nothing to fix. The names are already clean.",
                        musicWorkshopNoDuplicates = "No repeated tracks.",
                        musicWorkshopSameAddress = "Same address — certainly the same track",
                        musicWorkshopSameName = "Same name — check before removing",
                        multiviewAudioFrom = "Audio from",
                        multiviewFullScreen = "Full screen",
                        multiviewWindowed = "Windowed",
                        multiviewUnavailable = "Could not open split screen",
                        multiviewUnavailableHint = "The chosen channels did not respond. Try others.",
                        multiviewEmpty = "Choose the channels first",
                        multiviewEmptyHint = "Hover a live channel and click ▦ to add up to four.",
                        multiviewClear = "Clear",
                        multiviewFull = "Maximum of %d channels",
                        licenseTrialOverTitle = "Your 7 days are up",
                        licenseTrialOverBody =
                            "We hope you enjoyed it. To keep watching, activate this device.",
                        licenseExpiredTitle = "Your licence has expired",
                        licenseExpiredBody = "Renew to keep using IPTV BURO on this device.",
                        licenseRevokedTitle = "Licence cancelled",
                        licenseRevokedBody =
                            "This licence has been cancelled. If you think that is a mistake, get in touch.",
                        licenseVerifyTitle = "We need to check your licence",
                        licenseVerifyBody =
                            "It has been a while since the last check. Connect to the internet once " +
                                "and you are back to normal.",
                        licenseOfflineTitle = "No connection",
                        licenseOfflineBody =
                            "We could not check your licence. Check your internet and try again.",
                        licenseTwoYears = "for 2 years",
                        licenseWhyNotLifetime =
                            "Not lifetime, because the app keeps being maintained and updated: " +
                                "providers change, formats change, Windows changes.",
                        licenseBuy = "Activate device",
                        licenseRetry = "Try again",
                        licenseDeviceLabel = "Your device",
                        licenseHaveKey = "I have an activation code",
                        licenseKeyPlaceholder = "Code",
                        licenseRedeem = "Use code",
                        licenseDaysLeft = "%d days left",
                                                epgShowSchedule = "See schedule (%d)",
                        epgHideSchedule = "Hide schedule",
                        parentalUnlock = "Enter the PIN to open this category",
                    ),
                subscriptionsSynopsis = "Synopsis",
                subscriptionsCast = "Cast",
                subscriptionsWatchTrailer = "▶  Watch trailer",
                subscriptionsAvailableOn = "Available on",
                subscriptionsFilterMovies = "Films",
                subscriptionsFilterSeries = "Series",
                subscriptionsFilterUpcoming = "Coming soon",
                subscriptionsFilterThisWeek = "This week",
                subscriptionsUpcomingNote =
                    "Titles with a release date set. Which service will carry each one is not yet known.",
                subscriptionsEmptyBody = "Nothing found for this title.",
                subscriptionsBrowseByService = "By service",
                subscriptionsNoShelves = "No services to show yet.",
                subscriptionsLoadFailed = "We could not load the services. Check your connection and try again.",
                subscriptionsKeyRejected =
                    "TMDb refused the API key. Check it in Options — a new key can take a few minutes to become active.",
                subscriptionsBackToServices = "Back to services",
                subscriptionsSelectedTitle = "Selected title",
                licenseText =
                    LicenseStrings(
                        trialTitle = "Your trial has ended",
                        trialBody =
                            "The 7 days are over. Activate this device to keep using IPTV BURO.",
                        expiredTitle = "Licence expired",
                        expiredBody =
                            "This device's licence has run out. Renew it to carry on.",
                        revokedTitle = "Licence revoked",
                        revokedBody =
                            "This licence is no longer active. If you think that is a mistake, write to "
                                + "us quoting the identifier below.",
                        unreachableTitle = "Could not check your licence",
                        unreachableBody =
                            "We could not reach the server. Check your connection and try again.",
                        verifyTitle = "Verification needed",
                        verifyBody =
                            "The app has run offline for long enough. Go online once to carry on.",
                        deviceLabel = "Device",
                        activationKeyLabel = "Activation key",
                        activationKeyHint = "Keep this key. It is tied to this computer; losing it means buying another.",
                        macLabel = "MAC",
                        scanHint = "Scan this with your phone",
                        openInBrowser = "Open in browser",
                        retry = "Try again",
                        haveKey = "Have an activation code?",
                        keyPlaceholder = "XXXX-XXXX",
                        redeem = "Activate",
                        redeemFailed = "That code is not valid, or has already been used.",
                        keyAvailable = "Key is valid and unused.",
                        keyAvailableDays = { days -> "Key grants $days days, unused." },
                        keyYours = "This key already belongs to this device.",
                        keyInUse = "This key is already in use on another device.",
                        keyExpired = "This key has expired.",
                        quit = "Quit",
                        back = "Back to the app",
                        backToPurchase = "Pay instead",
                        termYears = "%d years",
                        priceEur = "€9.90 · 2 years",
                        priceUsd = "$9.90 · 2 years",
                        priceBrl = "R$99.90 · 2 years",
                        whyNotLifetime =
                            "Why not lifetime: the app keeps being maintained and updated. The 2 years "
                                + "pay for that work.",
                        copied = "Copied",
                        clockWarning =
                            "This computer's clock looks wrong. Your licence dates come from the server "
                                + "either way.",
                        trialDaysLeft = "%d days left in your trial",
                        licenseDaysLeft = "%d days left",
                        licenseLastDay = "Last day",
                        trialLastDay = "Last day of your trial",
                        buyNow = "Activate",
                    ),
            )

        private val De =
            DesktopStrings(
                library = "BIBLIOTHEK",
                home = "Start",
                live = "Live-TV",
                movies = "Filme",
                series = "Serien",
                downloadStrings =
                    DownloadStrings(
                        downloadSeries = "Serie herunterladen",
                        downloadSeason = "Staffel %d herunterladen",
                        downloadSeriesConfirmTitle = "Die ganze Serie herunterladen?",
                        downloadSeasonConfirmTitle = "Staffel %d herunterladen?",
                        downloadConfirmBody = "%d Folgen werden heruntergeladen. Das kann viel Speicher und Daten verbrauchen.",
                        downloadConfirmAction = "Herunterladen",
                    ),
                savedForLater =
                    SavedForLaterStrings(
                        favorites = "Favoriten",
                        reminderAdd = "Erinnerung",
                        reminderActive = "Erinnerung aktiv",
                        reminderNoNotice = "Auf diesem Computer gespeichert. Der Hinweis erscheint hier in der App.",
                        reminderAnnounce = "An Erinnerungen erinnern",
                        reminderHourLabel = "Hinweis um",
                        reminderInAppOnly =
                            "Der Hinweis erscheint in der App, beim ersten Öffnen nach der " +
                                "gewählten Uhrzeit. Solange die App geschlossen ist, sendet sie " +
                                "keine Windows-Benachrichtigung.",
                        reminderNoticeBody = "Sie haben %d Titel vorgemerkt.",
                        reminderNoticeDismiss = "Verstanden",
                        remindersTitle = "Erinnerungen",
                        remindersEmpty =
                            "Sie haben noch nichts markiert. Nutzen Sie die Schaltfläche " +
                                "Erinnerung bei einem Film, einer Serie oder einem kommenden " +
                                "Titel unter Abos.",
                        reminderOpen = "Öffnen",
                        reminderRemove = "Entfernen",
                        reminderNotInLibrary = "Noch nicht in Ihrer Liste",
                        newEpisodeBody = "Neue Folge: S%1${'$'}d E%2${'$'}d",
                        newSeasonBody = "Neue Staffel: S%1${'$'}d",
                    ),
                shareStrings =
                    ShareStrings(
                        cast =
                            CastStrings(
                                            castAction = "An Bildschirm",
                                            castTitle = "An einen Bildschirm senden",
                                            castSearching = "Bildschirme in diesem Netzwerk werden gesucht …",
                                            castNoneFound = "Kein Bildschirm gefunden. Öffnen Sie IPTV BURO auf dem anderen Gerät, schalten Sie „Empfangen“ ein und prüfen Sie, ob beide im selben Netzwerk sind.",
                                            castManualTitle = "Oder Adresse eingeben",
                                            castManualHint = "Manche Router blockieren die Suche zwischen Geräten. Der andere Bildschirm zeigt seine Adresse unter „Empfangen“.",
                                            castManualLabel = "Adresse in diesem Netzwerk",
                                            castManualConnect = "Verbinden",
                                            castManualInvalid = "Das sieht nicht nach einer Adresse in diesem Netzwerk aus.",
                                            castSearchAgain = "Erneut suchen",
                                            castCodePrompt = "An %s senden",
                                            castCodeHint = "Geben Sie die vier Ziffern ein, die auf diesem Bildschirm stehen.",
                                            castCodeInvalid = "Der Code besteht aus vier Ziffern.",
                                            castSend = "Senden",
                                            castSending = "Wird an %s gesendet …",
                                            castSent = "An %s gesendet. Startet nichts, prüfen Sie den Code auf diesem Bildschirm.",
                                            castFailed = "%s war nicht erreichbar.",
                                            castChooseAnother = "Anderen Bildschirm wählen",
                            ),
                    notifications =
                        NotificationStrings(
                            title = "Hinweise",
                            empty = "Nichts hier.",
                            clearAll = "Alles loeschen",
                            dismiss = "Verwerfen",
                        ),
                    failures =
                        FailureStrings(
                            sessionExpired =
                                "Die Sitzung Ihrer Liste ist abgelaufen. Der Katalog bleibt gespeichert, " +
                                    "aber Sie müssen sich erneut bei der Quelle anmelden, um Neues zu laden.",
                            outOfMemory =
                                "Es war nicht genug Speicher vorhanden, um diesen Bildschirm aufzubauen. " +
                                    "Das ist eine Grenze der Anwendung, nicht Ihrer Liste.",
                            invalidServer = "Die Serveradresse ist ungültig.",
                            invalidServerScheme = "Prüfen Sie den Anfang der Adresse: er muss http:// oder https:// sein.",
                            authenticationRejected = "Der Server hat Benutzername oder Passwort abgelehnt.",
                            networkUnreachable = "Der Server war nicht erreichbar.",
                            httpError = "Der Server antwortete mit einem HTTP-Fehler.",
                            responseTooLarge = "Der Katalog überschritt die sichere Grenze dieser Version.",
                            invalidResponse = "Der Server lieferte keinen kompatiblen Xtream-Katalog. Details in %1${'$'}s",
                            appFault =
                                "Dieser Bildschirm konnte nicht aufgebaut werden (%1${'$'}s). Das ist ein Fehler " +
                                    "der Anwendung, nicht Ihrer Liste. Details in %2${'$'}s",
                        ),
                    startup =
                        StartupStrings(
                            openingSession = "Ihre Sitzung wird geöffnet…",
                            joiningList = "%1${'$'}s wird zusammengeführt…",
                            loadingLiveCategories = "Senderkategorien werden geladen…",
                            loadingMovieCategories = "Filmkategorien werden geladen…",
                            loadingSeriesCategories = "Serienkategorien werden geladen…",
                            downloadingMovies = "Filmliste wird heruntergeladen…",
                            downloadingSeries = "Serienliste wird heruntergeladen…",
                            organising = "Filme und Serien werden sortiert…",
                            ready = "Fertig",
                        ),
                    screens =
                        ScreenStrings(
                            setupMissingProfileName = "Geben Sie einen Profilnamen ein, um fortzufahren",
                            setupMissingConnection = "Server, Benutzer und Passwort ausfüllen oder eine vorhandene Liste wählen",
                            diagnosticsAction = "Diagnose",
                            diagnosticsTitle = "Verbindungsdiagnose",
                            mergeSourcesTitle = "Alle Listen zusammenführen",
                            mergeSourcesHelp = "Zeigt alle Listen als einen Katalog. Nichts doppelt sich: die größte Liste führt, die anderen füllen die Lücken.",
                            mergeSourcesRestart = "Die Listen werden sofort neu geordnet.",
                            mergeSourcesFailed = "%1${'$'}s hat nicht geantwortet. Die anderen Listen funktionieren weiter.",
                            mergeSourcesOffline = "Antwortet nicht",
                            diagnosticsLatencyGood = "Niedrige Latenz: Kanäle wechseln ohne Wartezeit",
                            diagnosticsLatencyFair = "Hohe Latenz: kann stocken und Kanalwechsel verzögern",
                            diagnosticsLatencyUnstable = "Sehr hohe Latenz: verursacht Ruckeln und Aussetzer",
                            diagnosticsLatencyUnknown = "Die Latenz konnte nicht gemessen werden",
                            diagnosticsRunning = "Test läuft…",
                            diagnosticsRun = "Erneut testen",
                            diagnosticsClose = "Schließen",
                            diagnosticsDownload = "Download",
                            diagnosticsUpload = "Upload",
                            diagnosticsPing = "Latenz",
                            diagnosticsLoss = "Paketverlust",
                            diagnosticsCatalogue = "Liste",
                            diagnosticsConnection = "Verbindung",
                            diagnosticsMemory = "Speicher",
                            diagnosticsAddress = "IP-Adresse",
                            diagnosticsGateway = "Gateway",
                            diagnosticsNetmask = "Subnetzmaske",
                            diagnosticsVerdictGood = "Alles in Ordnung",
                            diagnosticsVerdictWarning = "Beachten",
                            diagnosticsVerdictProblem = "Problem gefunden",
                            diagnosticsQualityUnstable = "Schlechte Verbindung: verursacht Ruckeln und Aussetzer",
                            diagnosticsQualitySd = "Mäßige Verbindung: reicht für Standardqualität",
                            diagnosticsQualityHd = "Gute Verbindung für Filme in 1080p",
                            diagnosticsQualityUhd = "Perfekte Verbindung für 4K und Live-TV",
                            diagnosticsQualityUnknown = "Die Geschwindigkeit konnte nicht gemessen werden",
                            diagnosticsWireless = "WLAN: ein Kabel vermeidet die Aussetzer, die Funk verursacht",
                            diagnosticsWired = "Kabel",
                            diagnosticsNoLink = "Keine Netzwerkverbindung",
                            diagnosticsCatalogueEmpty = "Die Liste wurde nicht geladen: Abo beim Verkäufer prüfen",
                            diagnosticsSignedOut = "Keine Liste eingerichtet",
                            diagnosticsLowMemory = "Wenig Speicher: andere Programme schließen oder App neu starten",
                            deviceCodeAction = "Gerätecode",
                            deviceCodeHelp = "Senden Sie diesen Code an den Verkäufer Ihrer Liste, damit er sie einrichten kann.",
                            setupRenameList = "Umbenennen",
                            setupRemoveList = "Entfernen",
                            setupRemoveListConfirm = "Liste «%1${'$'}s» entfernen? Das Passwort wird ebenfalls gelöscht.",
                            importFileMissing = "Die ausgewählte Datei existiert nicht mehr.",
                            importAccessDenied = "Das System hat das Lesen dieser Datei nicht erlaubt.",
                            importBlocked = "Der Zugriff auf die Datei wurde vom System blockiert.",
                            importFailed = "Die Liste konnte nicht importiert werden. Prüfen Sie, ob die Datei gültiges M3U/M3U8 ist, und versuchen Sie es erneut.",
                            movieDetailsLoading = "Filmdetails werden geladen…",
                            epgLoading = "Jetzt und danach werden geladen…",
                            guideNow = "Jetzt",
                            guideNext = "Danach",
                            catchUpShow = "Erneut ansehen (%1${'$'}s)",
                            catchUpHide = "Ausblenden",
                            similarTitles = "Ähnliche Titel",
                            epgUnavailable = "Kein Programmführer verfügbar; der Sender läuft weiterhin.",
                            epgEmpty = "Die Quelle hat kein Programm gemeldet.",
                            loadEpisodes = "Folgen laden",
                            episodesLoading = "Folgen werden geladen…",
                            externalOpenFailed = "Konnte nicht geöffnet werden",
                            externalNoDefaultApp = "Dieses System bietet keine Standardanwendung zum Öffnen des Senders.",
                            externalRefused = "Die externe Anwendung hat die Adresse abgelehnt. Es wurden keine Daten kopiert.",
                            externalHeadersWarning = "Der Sender benötigt HTTP-Header; ein gewöhnlicher Browser kann ihn womöglich nicht abspielen.",
                            externalAddressValid = "Für eine externe Anwendung gültige Adresse.",
                            headersUnsupported = "Dieser Sender benötigt HTTP-Header, die der aktuelle Windows-Player noch nicht setzen kann. Die Wiedergabe ist deaktiviert, statt eine Schaltfläche anzubieten, die fehlschlägt.",
                            noChannelMatches = "Kein Sender entspricht dem Filter.",
                            connectXtreamTitle = "Xtream-Server verbinden",
                            searchingCatalogue = "Der Katalog wird durchsucht…",
                            noFurtherTitles = "Keine weiteren Titel im Katalog gefunden.",
                            noPlayableEpisodes = "Der Server lieferte keine abspielbaren Folgen.",
                            playerStopped = "Die Video-Engine wurde unerwartet beendet.",
                            playerStartFailed = "Die Windows-Video-Engine konnte nicht gestartet werden.",
                            playerStalled = "Der Server hat geantwortet, aber dieses Video startete nicht. Versuchen Sie es erneut oder wählen Sie einen anderen Titel.",
                            updateCheckFailed = "Es konnte gerade nicht nach Updates gesucht werden.",
                            demoMovieNotice = "Beispieltitel. Diese Verfügbarkeit ist fiktiv.",
                            demoSeriesNotice = "Beispielserie. Diese Verfügbarkeit ist fiktiv.",
                        ),
                    remoteSource =
                        RemoteSourceStrings(
                            title = "Eigener Server (NAS)",
                            hint = "Liest eine M3U-Liste von Ihrem Server über WebDAV oder FTP.",
                            addressLabel = "Adresse",
                            addressPlaceholder = "webdav://nas.local/media/liste.m3u",
                            userLabel = "Benutzername (optional)",
                            passwordLabel = "Passwort (optional)",
                            credentialsNotice = "Zugangsdaten werden nur für diesen Abruf genutzt und nicht gespeichert.",
                            connect = "Verbinden",
                            cancel = "Abbrechen",
                            unsupportedAddress = "Adresse nicht unterstützt. Nutzen Sie webdav://, http:// oder ftp://",
                        ),
                    ratings =
                        RatingStrings(
                            title = "Bewertungen",
                            source = "TMDb-Wertung",
                            votes = "%s Stimmen",
                            critics = "Kritiker",
                            criticKeyLabel = "OMDb-Schlüssel (optional)",
                            criticKeyHint = "Ergänzt Tomatometer, Metascore und IMDb. Erhältlich auf omdbapi.com",
                            criticKeyPlaceholder = "API-Schlüssel",
                            criticKeySaved = "Schlüssel gespeichert: Kritikerwertungen werden angezeigt.",
                            criticKeyAbsent = "Ohne Schlüssel wird nur die TMDb-Publikumswertung angezeigt.",
                            adultKeyTitle = "Cover für den Erwachsenenbereich",
                            adultKeyBody = "TMDb deckt diesen Katalog nicht ab, daher braucht das Cover einen ThePornDB-Schlüssel. Ohne ihn zeigen diese Zeilen weiterhin den Titel. Der Schlüssel gehört Ihnen und wird nirgendwo sonst gesendet.",
                            adultKeyPlaceholder = "API-Schlüssel",
                            adultKeySaved = "Schlüssel gespeichert: Cover werden geladen.",
                            adultKeyAbsent = "Kein Schlüssel: diese Zeilen zeigen nur den Titel.",
                            adultKeySite = "Schlüssel auf theporndb.net holen",
                            criticGuideButton = "Sie wissen nicht, wie?",
                            criticGuideTitle = "Einen OMDb-Schlüssel erhalten",
                            criticGuideSubtitle = "Vier Schritte. Der kostenlose Schlüssel kommt per E-Mail.",
                            criticGuideOpenSite = "omdbapi.com öffnen",
                            criticStep1Title = "Öffnen Sie omdbapi.com",
                            criticStep1Body =
                                "Gehen Sie zum Bereich API Key. Ein Konto ist nicht erforderlich — " +
                                    "eine E-Mail-Adresse genügt.",
                            criticStep2Title = "Wählen Sie den kostenlosen Tarif",
                            criticStep2Body =
                                "Wählen Sie FREE mit 1.000 Anfragen pro Tag — weit mehr, als diese " +
                                    "App benötigt. Der kostenpflichtige Tarif ist nicht nötig.",
                            criticStep3Title = "E-Mail eingeben und absenden",
                            criticStep3Body =
                                "Geben Sie eine Adresse an, auf die Sie sofort zugreifen können, und " +
                                    "beschreiben Sie den Zweck kurz, etwa \"privater Gebrauch\".",
                            criticStep4Title = "Schlüssel per E-Mail aktivieren",
                            criticStep4Body =
                                "Der Schlüssel kommt mit einem Aktivierungslink. Öffnen Sie ihn, sonst " +
                                    "bleibt der Schlüssel inaktiv, und fügen Sie ihn dann hier ein.",
                            criticSketchEmail = "Ihre E-Mail",
                            criticSketchFree = "FREE",
                            criticSketchSubmit = "Absenden",
                            criticSketchInbox = "Schlüssel + Aktivierung",
                        ),
                    discovery =
                        DiscoveryStrings(
                            title = "Entdecken",
                            hint = "Wischen oder Tasten nutzen: Behalten legt den Titel zu den Favoriten.",
                            keep = "Behalten",
                            skip = "Überspringen",
                            details = "Details",
                            exhausted = "Sie haben vorerst alles gesehen.",
                            another = "Mehr finden",
                            loading = "Auswahl wird erstellt…",
                            kept = "Zu Favoriten hinzugefügt",
                        ),
                    settingsTabs =
                        SettingsTabStrings(
                            general = "Allgemein",
                            content = "Inhalt",
                            subtitles = "Untertitel",
                            data = "Daten",
                            maintenance = "Wartung",
                        ),
                    cache =
                        CacheStrings(
                            title = "Bilder auf diesem Computer behalten",
                            explanation = "Die App bewahrt Poster und Bilder auf Ihrem Computer auf, damit die Liste sofort erscheint, statt alles jedes Mal neu zu laden.",
                            firstTimeWarning = "Das erste Mal kann dauern: je nach Groesse Ihrer Liste. Es laeuft im Hintergrund und Sie koennen die App normal nutzen.",
                            sizeLabel = "Reservierter Platz",
                            gigabytes = "%d GB",
                            disabled = "Nicht behalten",
                            estimate = "Ihre Liste braucht etwa %s.",
                            start = "Starten",
                            skip = "Jetzt nicht",
                            filling = "Bilder werden gespeichert",
                            progress = "%1${'$'}d von %2${'$'}d",
                            pause = "Pause",
                            resume = "Fortsetzen",
                            cancel = "Abbrechen",
                            complete = "Alles gespeichert.",
                            used = "Belegt: %s",
                            clear = "Cache leeren",
                            clearTitle = "Cover-Cache leeren?",
                            clearBody = "Die gespeicherten Cover werden gelöscht und bei Bedarf erneut geladen. Aus Ihrer Liste geht nichts verloren.",
                            restartNote = "Eine Groessenaenderung gilt ab dem naechsten Start der App.",
                            percent = "%d%%",
                            refresh = "Aktualisieren",
                        ),
                    serviceCatalogue =
                        ServiceCatalogueStrings(
                            seeMore = "Mehr anzeigen",
                            allFrom = "Alles von %s",
                            backToShelves = "Zuruck zu den Diensten",
                            genreSelector = "Genre",
                            serviceSelector = "Dienst",
                            allGenres = "Alle Genres",
                            allServices = "Alle Dienste",
                            servicesUnavailable = "in Ihrer Liste nicht angegeben",
                            servicesLoading = "suche…",
                            duplicatesLabel = "Mehrfache Kopien",
                            duplicatesHint = "Listen enthalten denselben Film oft mehrfach, je nach Qualität oder Synchronisation.",
                            duplicatesToggle = "Nur eine Karte pro Film zeigen",
                        ),
                    receiver =
                        CastReceiverStrings(
                            title = "Vom Telefon empfangen",
                            hint =
                                "Lässt das Telefon diesen Computer finden und ihm einen Titel " +
                                    "schicken. Beide müssen im selben Netzwerk sein. Geben Sie " +
                                    "den Code unten einmal am Telefon ein.",
                            receiveNow = "Jetzt empfangen",
                            autoStart = "Beim Öffnen der App einschalten",
                            codeLabel = "Code",
                            codeExplanation =
                                "Dieser Code bleibt auf diesem Computer immer derselbe. Einmal " +
                                    "am Telefon eingeben, dann fragt es nicht mehr. Nur wer " +
                                    "diese Nummer hat, kann hierher senden.",
                            regenerate = "Neuen Code erzeugen",
                        ),
                    share = "Teilen",
                    shareTitle = "Titel teilen",
                    shareSubtitle = "Sende eine Empfehlung, nicht deine Liste.",
                    shareDestination = "Senden über",
                    shareByEmail = "E-Mail",
                    shareCopyLink = "Link kopieren",
                    shareCopied = "Kopiert!",
                    shareNoCredentials =
                        "Der Link enthält weder Server noch Benutzername oder Passwort. " +
                            "Wer ihn erhält, öffnet ihn mit der eigenen Liste.",
                    shareNotFoundTitle = "Nicht in deiner Liste",
                    shareNotFoundBody =
                        "Dein Anbieter führt diesen Titel nicht. Ein geteilter Link ist eine " +
                            "Empfehlung: Jede Person öffnet ihn mit der eigenen Liste.",
                    ),
                search = "Suche",
                sources = "QUELLEN",
                profile = "Profil",
                yourLibrary = "Deine Bibliothek",
                connectXtream = "Xtream verbinden",
                importM3u = "M3U importieren",
                checkUpdate = "Nach Update suchen",
                refreshCatalog = "Listen aktualisieren",
                termsTitle = "Bevor es losgeht",
                termsNoContent = "IPTV BURO bietet, hostet und verkauft keine Sender, Filme oder Serien.",
                termsYourSource = "Sie bringen Ihre eigene Liste mit: die App spielt nur ab, was Ihr Anbieter liefert.",
                termsResponsibility = "Sie sind dafür verantwortlich, zum Zugriff auf die hinzugefügten Inhalte berechtigt zu sein.",
                                termsNoWarranty = "Die Wiedergabe hängt von Ihrer eigenen Liste und Verbindung ab. Wir garantieren nicht, dass ein bestimmter Anbieter, Sender oder ein Format funktioniert.",
                termsOneDevice = "Eine Lizenz gilt für einen Computer. Ein Wechsel des Geräts erfordert eine neue Aktivierung.",
                termsRefund = "Sie erhalten 7 Tage Testzeit vor dem Kauf. Nach dem Kauf gilt das Widerrufsrecht Ihres Landes.",
                termsReadFull = "Vollständige Bedingungen lesen",
                termsAccept = "Einverstanden, weiter",
                setupTitle = "Profil erstellen",
                setupSubtitle = "Jedes Profil hat eigene Favoriten. Downloads bleiben für alle verfügbar.",
                setupProfileName = "Profilname",
                setupUseExisting = "BEREITS EINGERICHTETE LISTE VERWENDEN",
                setupYourList = "IHRE LISTE",
                setupNewList = "ODER EINE NEUE LISTE HINZUFÜGEN",
                setupListName = "Name der Liste",
                serverLabel = "Server",
                usernameLabel = "Benutzername",
                passwordLabel = "Passwort",
                setupContinue = "Weiter",
                connectingTitle = "Liste wird geladen",
                connectingBody = "Verbindung zum Anbieter und Aufbau des Katalogs. Das kann einen Moment dauern.",
                setupFailedTitle = "Die Liste konnte nicht geladen werden",
                setupFailedBody = "Der Anbieter hat nicht geantwortet. Prüfen Sie Server, Benutzername und Passwort.",
                setupRetry = "Erneut versuchen",
                chooseRating = "Bewertung",
                anyRating = "Alle Bewertungen",
                continueEmptyTitle = "Nichts zum Fortsetzen",
                continueEmptyBody = "Begonnene Filme und Folgen erscheinen hier.",
                resumeFrom = "Fortsetzen",
                startOver = "Von vorn",
                forgetProgress = "Entfernen",
                metadataKeyLabel = "TMDb-Schlüssel (optional)",
                                tmdbGuide =
                                    TmdbGuideStrings(
                                        tmdbGuideTitle = "So erhalten Sie Ihren TMDb-Schlüssel",
                                        tmdbGuideSubtitle = "Sechs Schritte. Kostenlos und in etwa fünf Minuten erledigt.",
                                        tmdbGuideOpenSignup = "TMDb-Konto erstellen",
                                        tmdbGuideOpenApiPage = "Ich habe bereits ein Konto",
                                        tmdbGuideButton = "Sie wissen nicht, wie?",
                                        tmdbStep1Title = "Kostenloses Konto erstellen",
                                        tmdbStep1Body = "Öffnen Sie themoviedb.org und klicken Sie auf Sign Up. Sie brauchen eine E-Mail-Adresse, einen Benutzernamen und ein Passwort.",
                                        tmdbStep2Title = "E-Mail bestätigen und anmelden",
                                        tmdbStep2Body = "TMDb sendet eine Bestätigungs-E-Mail. Ohne Bestätigung ist die API-Seite nicht verfügbar.",
                                        tmdbStep3Title = "Settings öffnen, dann API",
                                        tmdbStep3Body = "Klicken Sie oben rechts auf Ihr Bild, wählen Sie Settings und dann API im Seitenmenü.",
                                        tmdbStep4Title = "Entwicklerschlüssel anfordern",
                                        tmdbStep4Body = "Wählen Sie Developer, nicht Commercial. Das ist die richtige Option für den privaten Gebrauch und kostenlos.",
                                        tmdbStep5Title = "Formular ausfüllen",
                                        tmdbStep5Body = "Anwendungsname: IPTV BURO. URL: eine beliebige eigene Adresse genügt. Zweck: privater Gebrauch, um Bilder und Inhaltsangaben anzuzeigen.",
                                        tmdbStep6Title = "Schlüssel kopieren und hier einfügen",
                                        tmdbStep6Body = "Kopieren Sie den Wert API Key (v3 auth) mit 32 Zeichen und fügen Sie ihn in das Feld in diesem Fenster ein. Er wird nur auf Ihrem Computer gespeichert.",
                                        tmdbSketchSignUp = "Sign Up",
                                        tmdbSketchApiMenu = "API",
                                        tmdbSketchRequestType = "Anfragetyp",
                                        tmdbSketchDeveloper = "Developer",
                                        tmdbSketchFormFields = "Name, URL, Zweck",
                                        tmdbSketchApiKeyLabel = "API Key (v3 auth)",
                                        tmdbSketchCopy = "Kopieren",
                                        tmdbSketchSettings = "Einstellungen",
                                        tmdbSketchPaste = "Hier einfügen",
                                    ),
                metadataKeyHint = "Fügen Sie Ihren Schlüssel von themoviedb.org/settings/api ein",
                metadataKeyPlaceholder = "API-Schlüssel",
                layoutPoster = "Poster",
                layoutCompact = "Kompakt",
                layoutList = "Liste",
                removeProfile = "Entfernen",
                confirmRemoveProfile = "Bestätigen?",
                showPassword = "Anzeigen",
                hidePassword = "Verbergen",
                avatarUsePhoto = "Foto verwenden",
                avatarRemovePhoto = "Foto entfernen",
                avatarChoosePhotoTitle = "Profilfoto auswählen",
                checkingUpdate = "Suche nach Update…",
                upToDate = "Du hast bereits die neueste Version.",
                downloading = "Lädt herunter",
                installerVerified = "Installer geprüft. Wird aktualisiert…",
                updateFailed = "Das Update konnte nicht installiert werden.",
                privateSession = "Private Sitzung",
                nothingSensitiveSaved = "Nichts Sensibles wird gespeichert",
                dailySelection = "TAGESAUSWAHL",
                continueWatching = "Weiterschauen",
                moviesForToday = "Filme für heute",
                seriesToExplore = "Serien zum Entdecken",
                liveNow = "Jetzt live",
                heroFallbackTitle = "Deine Bibliothek ist bereit",
                heroSubtitle =
                    "Jeden Tag eine andere Auswahl – geordnet, ohne die ganze Bibliothek auf " +
                        "einen Bildschirm zu kippen.",
                watchNow = "Ansehen",
                details = "Details",
                options = "Optionen",
                organizingToday = "Die Auswahl von heute wird zusammengestellt…",
                seasonalBadge = "ZUR SAISON",
                tryAgain = "Erneut versuchen",
                watched = "gesehen",
                onAir = "LIVE",
                emptyHeadline = "Deine ganze Bibliothek.\nOhne Lärm.",
                emptyBody =
                    "Importiere deine autorisierte Quelle und lass IPTV BURO Kanäle, Filme und " +
                        "Serien zu einem Erlebnis auf allen Bildschirmen ordnen.",
                emptyBadge = "BURO NOCTURNE  •  PRIVATE BIBLIOTHEK",
                credentialsStayLocal =
                    "Die Quelle wird aus dem geschützten Tresor dieses Benutzers wiederhergestellt.",
                authenticating = "Authentifizierung und Katalogaufbau…",
                organizingPlaylist = "Deine Playlist wird geordnet…",
                noSensitiveData = "Es werden keine sensiblen Daten gespeichert.",
                close = "Schließen",
                cancel = "Abbrechen",
                understood = "Verstanden",
                whoIsWatching = "Wer schaut?",
                newProfile = "Neues Profil",
                addProfile = "Hinzufügen",
                kidsProfile = "Kinderprofil",
                adultProfile = "Erwachsenenprofil",
                forgetSource = "Quelle vergessen",
                searchChannel = "Kanal suchen…",
                results = "Ergebnisse",
                items = "Einträge",
                sourcesCount = "Quellen",
                selectChannel = "Wähle einen Kanal",
                vaultProtected = "Tresor geschützt",
                credentialsEncrypted = "Zugangsdaten von Windows verschlüsselt",
                endSession = "Sitzung beenden",
                searchCatalog = "In diesem Katalog suchen…",
                categories = "Kategorien",
                allCategories = "Alle",
                catalog = "Katalog",
                noMatch = "Nichts entspricht diesem Filter.",
                previous = "Zurück",
                next = "Weiter",
                page = "Seite",
                downloadsNoMatch = "Nichts entspricht diesem Filter.",
                allItems = "Alle",
                allYears = "Alle Jahre",
                releasesIn = "Erschienen",
                sessionActive = "Sitzung aktiv · Verbindung unter Windows geschützt",
                loadingCatalog = "Katalog wird geladen…",
                sessionClosed = "Sitzung beendet",
                backToCatalog = "Zurück zum Katalog",
                selectItem = "Wähle einen Eintrag",
                download = "Herunterladen",
                downloadInProgress = "Wird geladen",
                downloaded = "Geladen",
                downloadFailed = "Download fehlgeschlagen",
                removeDownload = "Download entfernen",
                downloads = "Downloads",
                resetSettings = "Einstellungen zuruecksetzen",
                resetConfirm = "Alles loeschen",
                resetWarning = "Loescht Profile, Favoriten und Fortschritt. Heruntergeladene Dateien bleiben.",
                settings = "Einstellungen",
                languageLabel = "SPRACHE",
                chooseYear = "Jahr wählen",
                downloadPaused = "Pausiert",
                downloadsEmptyTitle = "Keine Offline-Kopien",
                downloadsEmptyBody = "Oeffne einen Film und waehle Herunterladen. Gespeicherte Kopien erscheinen hier und laufen ohne Verbindung.",
                updateReadyBody = "Das Update wurde geladen und geprueft. Die App schliesst sich, Windows installiert die neue Version, und die App oeffnet sich von selbst. Das kann etwa eine Minute dauern.",
                updateRestartNow = "Schliessen und aktualisieren",
                updateLater = "Spaeter",
                music = "Musik",
                musicHome = "Start",
                musicArtists = "Künstler",
                musicRadio = "Radio",
                musicNewReleases = "Neuheiten",
                musicMostPlayed = "Meistgehört",
                musicTracks = "Titel",
                musicStations = "Sender",
                musicAddPlaylist = "Musikliste hinzufügen",
                musicEmptyTitle = "Noch keine Musik",
                musicEmptyBody = "Füge deinem Profil eine Musik-M3U hinzu, um hier zu hören.",
                musicNoArtists = "Diese Liste nennt keine Künstler.",
                musicNoRadio = "Diese Liste enthält keine Radiosender.",
                musicNoDownloads = "Heruntergeladene Musik erscheint hier.",
                musicBackToArtists = "Zurück zu den Künstlern",
                musicPlaylistLabel = "Musikliste",
                musicPlaylistOptional = "OPTIONAL",
                musicPlaylistHint = "Eine reine Musik-M3U. Ohne sie ändert sich nichts in der App.",
                musicPlaylistChoose = "Datei wählen",
                musicPlaylistRemove = "Entfernen",
                musicPlaylistTitle = "Wähle deine Musikliste",
                musicPlaylists = "Playlists",
                musicPlaylistsEmpty = "Du hast noch keine Playlist erstellt.",
                musicPlaylistNew = "Neue Playlist",
                musicPlaylistNewName = "Meine Playlist",
                musicPlaylistRename = "Umbenennen",
                musicPlaylistDelete = "Löschen",
                musicPlaylistDuplicate = "Duplizieren",
                musicPlaylistDuplicateSuffix = "Kopie",
                musicPlaylistImport = "M3U importieren",
                musicPlaylistExport = "M3U exportieren",
                musicPlaylistBack = "Zurück zu den Playlists",
                musicPlaylistEmptyTracks = "Diese Playlist enthält noch keine Titel.",
                musicPlaylistRemoveTrack = "Aus der Playlist entfernen",
                musicPlaylistMoveUp = "Nach oben",
                musicPlaylistMoveDown = "Nach unten",
                musicSmartPlaylists = "Intelligente Playlists",
                musicSmartFavourites = "Favoriten",
                musicSmartRecentlyPlayed = "Zuletzt gehört",
                musicSmartMostPlayed = "Meistgehört",
                musicSmartNeverPlayed = "Nie gehört",
                musicSmartRecentlyAdded = "Zuletzt hinzugefügt",
                musicExportWarningTitle = "Diese Datei kann sensible Adressen enthalten",
                musicExportWarningBody =
                    "Einige Adressen in dieser Playlist scheinen Zugangsdaten oder Signaturen zu enthalten. " +
                        "Wer die Datei erhält, könnte dein Abo nutzen. Exportiere sie nur für dich selbst.",
                musicExportWarningConfirm = "Trotzdem exportieren",
                musicExportWarningCancel = "Abbrechen",
                queueTitle = "Warteschlange",
                queueNowPlaying = "Läuft gerade",
                queueUpNext = "Als Nächstes",
                queueEmptyBody = "Nichts in der Warteschlange. Nutze „Als Nächstes“ oder „Ans Ende“.",
                queuePlayNow = "Jetzt abspielen",
                queuePlayNext = "Als Nächstes abspielen",
                queueAddToEnd = "Ans Ende stellen",
                queueRemove = "Aus der Warteschlange entfernen",
                queueClear = "Leeren",
                queueMoveUp = "Nach oben",
                queueMoveDown = "Nach unten",
                queueOpen = "Warteschlange öffnen",
                queueClose = "Warteschlange schließen",
                queueCount = "in der Warteschlange",
                subscriptions = "Abonnements",
                subscriptionsDemoBadge = "DEMO",
                subscriptionsDemoNotice =
                    "Diese Ergebnisse sind Beispiele und dienen nur der Darstellung des Bildschirms. " +
                        "Es ist kein echter Streamingdienst verbunden.",
                subscriptionsWhereToWatch = "Wo ansehen",
                subscriptionsInYourLibrary = "Bereits in Ihrer Liste",
                subscriptionsIncludedInSubscription = "In Ihrem Abo enthalten",
                subscriptionsFreeWithAds = "Kostenlos mit Werbung",
                subscriptionsRent = "Leihen",
                subscriptionsBuy = "Kaufen",
                subscriptionsRequiresSubscription = "Abo erforderlich",
                subscriptionsUnavailable = "Hier nicht verfügbar",
                subscriptionsOpenProvider = "Im offiziellen Dienst öffnen",
                languageHint = "Sprache der App-Texte",
                regionHint = "Land, nach dem ermittelt wird, welche Streamingdienste einen Film führen",
                metadataKeyUses = "Für Poster, Besetzung, Trailer und den Bereich Abonnements",
                metadataKeySaved = "✓ Schlüssel gespeichert und aktiv.",
                metadataKeyUsingBundled = "Der Standardschlüssel der App wird verwendet.",
                settingsText =
                    SettingsStrings(
                        profileEdit = "Bearbeiten",
                        profileEditTitle = "Profil bearbeiten",
                        profileNameLabel = "Name",
                        profileAvatarLabel = "Bild",
                        profileKidsLabel = "Kindermodus",
                        profileKidsHint = "Zeigt nur Inhalte für Kinder",
                        profileSourceLabel = "Playlist",
                        profileSourceNone = "Die bereits verbundene verwenden",
                        profileSourceChange = "Playlist wechseln",
                        profileMusicLabel = "Musik (M3U)",
                        profileMusicNone = "Keine Datei gewählt",
                        profileMusicChoose = "Datei wählen",
                        profileMusicClear = "Entfernen",
                        profileSave = "Speichern",
                        expandSidebar = "Erweitern",
                        subtitlesLabel = "Untertitel",
                        subtitlesHint = "Gilt für den nächsten Titel",
                        subtitlesBackground = "Dunkler Hintergrund",
                        historyTitle = "Verlauf",
                        historyClearAll = "Alles löschen",
                        historyEmpty = "Noch nichts gesehen.",
                        categoriesLabel = "Kategorien",
                        categoriesHint = "Verbergen Sie Ungenutztes oder schützen Sie es mit der PIN",
                        categoryHide = "Ausblenden",
                        categoryLock = "Schützen",
                        clockLabel = "Uhr",
                        clockHint = "Format der Uhrzeit in der Kopfzeile",
                        clock24h = "24 Stunden",
                        clock12h = "12 Stunden",
                        parentalTitle = "Kindersicherung",
                        parentalHint = "Schützt Kategorien mit einer vierstelligen PIN",
                        parentalSetPin = "PIN einrichten",
                        parentalChangePin = "PIN ändern",
                        parentalRemovePin = "PIN entfernen",
                        parentalCurrentPin = "Aktuelle PIN",
                        parentalNewPin = "Neue PIN",
                        parentalWrongPin = "Falsche PIN.",
                        parentalDefaultPin = "Sperre aktiv mit der Standard-PIN 0000. Wählen Sie eine eigene, damit sie nicht erraten wird.",
                        parentalPinSaved = "✓ PIN gespeichert.",
                        parentalPinFormat = "Die PIN muss vier Ziffern haben.",
                        parentalLockAdult = "Erwachsenenkategorien automatisch sperren",
                        parentalLocked = "Geschützter Inhalt",
                        firstRunTitle = "IPTV BURO wird eingerichtet",
                        firstRunBody =
                            "Nur dieses eine Mal dauert es länger: Ihre Liste wird gelesen und " +
                                "geordnet. Beim nächsten Start ist alles bereit.",
                        firstRunTmdbTitle = "Cover und Inhaltsangaben",
                        firstRunTmdbBody =
                            "Für Cover, Inhaltsangaben und Besetzung fügen Sie in den Optionen " +
                                "einen TMDb-Schlüssel hinzu. Er ist kostenlos: Konto auf " +
                                "themoviedb.org anlegen, API-Schlüssel anfordern und in BURO einfügen.",
                        startupAuthenticating = "Anmeldung läuft…",
                        startupOrganising = "Ihre Liste wird geordnet…",
                        profileKeyLabel = "Schlüssel nur für dieses Profil",
                        profileKeyHint =
                            "Leer lassen, um denselben Schlüssel wie die anderen Profile zu " +
                                "verwenden. Ausfüllen, damit dieses Profil sein eigenes " +
                                "TMDb-Konto nutzt.",
                        profileKeyShared = "Gemeinsamer Schlüssel wird verwendet",
                        profileKeyOwn = "Dieses Profil nutzt einen eigenen Schlüssel",
                        moreSettingsTitle = "Weitere Optionen",
                        moreSettingsHint = "Untertitel, Uhr, Kategorien und Kindersicherung",
                        multiviewAdd = "Zusammen ansehen",
                        multiviewRemove = "Aus der Mehrfachansicht entfernen",
                        multiviewOpen = "Zusammen ansehen",
                        multiviewHint = "2 bis 4 Sender zusammen",
                                                musicWorkshop = "Musik-Werkstatt",
                        musicWorkshopSummary = "%d Titel · %d korrigiert",
                        musicWorkshopNames = "Namen",
                        musicWorkshopDuplicates = "Doppelte",
                        musicWorkshopApplyAll = "Alle korrigieren (%d)",
                        musicWorkshopApplyOne = "Korrigieren",
                        musicWorkshopUndoAll = "Alle %d Korrekturen rückgängig",
                        musicWorkshopNothingToFix = "Nichts zu korrigieren. Die Namen sind sauber.",
                        musicWorkshopNoDuplicates = "Keine doppelten Titel.",
                        musicWorkshopSameAddress = "Gleiche Adresse — sicher derselbe Titel",
                        musicWorkshopSameName = "Gleicher Name — vor dem Entfernen prüfen",
                        multiviewAudioFrom = "Ton von",
                        multiviewFullScreen = "Vollbild",
                        multiviewWindowed = "Fenster",
                        multiviewUnavailable = "Geteilter Bildschirm nicht möglich",
                        multiviewUnavailableHint = "Die gewählten Sender antworteten nicht.",
                        multiviewEmpty = "Zuerst Sender wählen",
                        multiviewEmptyHint = "Auf einen Sender zeigen und ▦ klicken, bis zu vier.",
                        multiviewClear = "Leeren",
                        multiviewFull = "Höchstens %d Kanäle",
                        licenseTrialOverTitle = "Ihre 7 Tage sind vorbei",
                        licenseTrialOverBody =
                            "Wir hoffen, es hat Ihnen gefallen. Aktivieren Sie dieses Gerät, um " +
                                "weiterzuschauen.",
                        licenseExpiredTitle = "Ihre Lizenz ist abgelaufen",
                        licenseExpiredBody =
                            "Verlängern Sie, um IPTV BURO auf diesem Gerät weiter zu nutzen.",
                        licenseRevokedTitle = "Lizenz storniert",
                        licenseRevokedBody =
                            "Diese Lizenz wurde storniert. Wenn das ein Irrtum ist, melden Sie sich.",
                        licenseVerifyTitle = "Wir müssen Ihre Lizenz prüfen",
                        licenseVerifyBody =
                            "Die letzte Prüfung ist eine Weile her. Einmal mit dem Internet " +
                                "verbinden genügt.",
                        licenseOfflineTitle = "Keine Verbindung",
                        licenseOfflineBody =
                            "Die Lizenz konnte nicht geprüft werden. Prüfen Sie Ihre Verbindung.",
                        licenseTwoYears = "für 2 Jahre",
                        licenseWhyNotLifetime =
                            "Nicht lebenslang, weil die App weiter gepflegt wird: Anbieter ändern " +
                                "sich, Formate ändern sich, Windows ändert sich.",
                        licenseBuy = "Gerät aktivieren",
                        licenseRetry = "Erneut versuchen",
                        licenseDeviceLabel = "Ihr Gerät",
                        licenseHaveKey = "Ich habe einen Aktivierungscode",
                        licenseKeyPlaceholder = "Code",
                        licenseRedeem = "Code einlösen",
                        licenseDaysLeft = "Noch %d Tage",
                                                epgShowSchedule = "Programm ansehen (%d)",
                        epgHideSchedule = "Programm ausblenden",
                        parentalUnlock = "PIN eingeben, um diese Kategorie zu öffnen",
                    ),
                subscriptionsSynopsis = "Handlung",
                subscriptionsCast = "Besetzung",
                subscriptionsWatchTrailer = "▶  Trailer ansehen",
                subscriptionsAvailableOn = "Verfügbar bei",
                subscriptionsFilterMovies = "Filme",
                subscriptionsFilterSeries = "Serien",
                subscriptionsFilterUpcoming = "Demnächst",
                subscriptionsFilterThisWeek = "Diese Woche",
                subscriptionsUpcomingNote =
                    "Titel mit festem Erscheinungsdatum. Welcher Dienst sie zeigen wird, ist noch nicht bekannt.",
                subscriptionsMyServices = "Meine Dienste",
                subscriptionsRegion = "Region",
                subscriptionsEmptyBody = "Für diesen Titel wurde nichts gefunden.",
                subscriptionsBrowseByService = "Nach Dienst",
                subscriptionsNoShelves = "Noch keine Dienste vorhanden.",
                subscriptionsLoadFailed = "Die Dienste konnten nicht geladen werden. Verbindung prüfen und erneut versuchen.",
                subscriptionsKeyRejected =
                    "TMDb hat den API-Schlüssel abgelehnt. Prüfen Sie ihn in den Optionen — ein neuer Schlüssel " +
                        "kann einige Minuten bis zur Aktivierung brauchen.",
                subscriptionsBackToServices = "Zurück zu den Diensten",
                subscriptionsSelectedTitle = "Ausgewählter Titel",
                licenseText =
                    LicenseStrings(
                        trialTitle = "Testzeitraum beendet",
                        trialBody =
                            "Die 7 Tage sind vorbei. Aktivieren Sie dieses Gerät, um IPTV BURO weiter "
                                + "zu nutzen.",
                        expiredTitle = "Lizenz abgelaufen",
                        expiredBody =
                            "Die Lizenz dieses Geräts ist abgelaufen. Verlängern Sie sie, um fortzufahren.",
                        revokedTitle = "Lizenz widerrufen",
                        revokedBody =
                            "Diese Lizenz ist nicht mehr aktiv. Falls das ein Fehler ist, schreiben Sie "
                                + "uns mit der Kennung unten.",
                        unreachableTitle = "Lizenz nicht überprüfbar",
                        unreachableBody =
                            "Der Server ist nicht erreichbar. Prüfen Sie Ihre Verbindung und versuchen "
                                + "Sie es erneut.",
                        verifyTitle = "Überprüfung erforderlich",
                        verifyBody =
                            "Die App lief lange genug offline. Gehen Sie einmal online, um fortzufahren.",
                        deviceLabel = "Gerät",
                        activationKeyLabel = "Aktivierungsschlüssel",
                        activationKeyHint = "Bewahre ihn auf. Er gilt nur für diesen Rechner; ohne ihn musst du neu kaufen.",
                        macLabel = "MAC",
                        scanHint = "Code mit dem Handy scannen",
                        openInBrowser = "Im Browser öffnen",
                        retry = "Erneut versuchen",
                        haveKey = "Haben Sie einen Aktivierungscode?",
                        keyPlaceholder = "XXXX-XXXX",
                        redeem = "Aktivieren",
                        redeemFailed = "Code ungültig oder bereits verwendet.",
                        keyAvailable = "Schlüssel gültig und frei.",
                        keyAvailableDays = { days -> "Schlüssel für $days Tage, frei." },
                        keyYours = "Dieser Schlüssel gehört bereits zu diesem Gerät.",
                        keyInUse = "Dieser Schlüssel wird bereits auf einem anderen Gerät genutzt.",
                        keyExpired = "Dieser Schlüssel ist abgelaufen.",
                        quit = "Schließen",
                        back = "Zurück zur App",
                        backToPurchase = "Lieber bezahlen",
                        termYears = "%d Jahre",
                        priceEur = "9,90 € · 2 Jahre",
                        priceUsd = "9,90 $ · 2 Jahre",
                        priceBrl = "99,90 R$ · 2 Jahre",
                        whyNotLifetime =
                            "Warum nicht lebenslang: Die App wird weiter gepflegt und aktualisiert. Die "
                                + "2 Jahre bezahlen diese Arbeit.",
                        copied = "Kopiert",
                        clockWarning =
                            "Die Uhr dieses Rechners scheint falsch zu gehen. Die Lizenzdaten kommen "
                                + "ohnehin vom Server.",
                        trialDaysLeft = "Noch %d Tage im Test",
                        licenseDaysLeft = "Noch %d Tage",
                        licenseLastDay = "Letzter Tag",
                        trialLastDay = "Letzter Testtag",
                        buyNow = "Aktivieren",
                    ),
            )

        private val It =
            DesktopStrings(
                library = "LIBRERIA",
                home = "Home",
                live = "TV in diretta",
                movies = "Film",
                series = "Serie",
                downloadStrings =
                    DownloadStrings(
                        downloadSeries = "Scarica serie",
                        downloadSeason = "Scarica stagione %d",
                        downloadSeriesConfirmTitle = "Scaricare l’intera serie?",
                        downloadSeasonConfirmTitle = "Scaricare la stagione %d?",
                        downloadConfirmBody = "Verranno scaricati %d episodi. Può occupare molto spazio e traffico.",
                        downloadConfirmAction = "Scarica",
                    ),
                savedForLater =
                    SavedForLaterStrings(
                        favorites = "Preferiti",
                        reminderAdd = "Promemoria",
                        reminderActive = "Promemoria attivo",
                        reminderNoNotice = "Salvato su questo computer. L’avviso appare qui nell’app.",
                        reminderAnnounce = "Avvisami dei promemoria",
                        reminderHourLabel = "Avvisami alle",
                        reminderInAppOnly =
                            "L’avviso appare nell’app, la prima volta che la apri dopo l’ora " +
                                "scelta. Ad app chiusa non invia notifiche di Windows.",
                        reminderNoticeBody = "Hai %d titolo/i da guardare.",
                        reminderNoticeDismiss = "Ho capito",
                        remindersTitle = "Promemoria",
                        remindersEmpty =
                            "Non hai ancora segnato nulla. Usa il pulsante Promemoria su un film, " +
                                "una serie o un titolo in arrivo in Abbonamenti.",
                        reminderOpen = "Apri",
                        reminderRemove = "Rimuovi",
                        reminderNotInLibrary = "Non è ancora nel tuo elenco",
                        newEpisodeBody = "Nuovo episodio: S%1${'$'}d E%2${'$'}d",
                        newSeasonBody = "Nuova stagione: S%1${'$'}d",
                    ),
                shareStrings =
                    ShareStrings(
                        cast =
                            CastStrings(
                                            castAction = "Invia a schermo",
                                            castTitle = "Invia a uno schermo",
                                            castSearching = "Ricerca di schermi in questa rete…",
                                            castNoneFound = "Nessuno schermo trovato. Apri IPTV BURO sull’altro dispositivo, attiva “Ricevi” e verifica che siano sulla stessa rete.",
                                            castManualTitle = "Oppure digita l’indirizzo",
                                            castManualHint = "Alcuni router bloccano la ricerca tra dispositivi. L’altro schermo mostra il suo indirizzo sotto “Ricevi”.",
                                            castManualLabel = "Indirizzo su questa rete",
                                            castManualConnect = "Collega",
                                            castManualInvalid = "Non sembra un indirizzo di questa rete.",
                                            castSearchAgain = "Cerca di nuovo",
                                            castCodePrompt = "Invia a %s",
                                            castCodeHint = "Digita le quattro cifre mostrate su quello schermo.",
                                            castCodeInvalid = "Il codice è di quattro cifre.",
                                            castSend = "Invia",
                                            castSending = "Invio a %s…",
                                            castSent = "Inviato a %s. Se non parte, controlla il codice su quello schermo.",
                                            castFailed = "Impossibile raggiungere %s.",
                                            castChooseAnother = "Scegli un altro schermo",
                            ),
                    notifications =
                        NotificationStrings(
                            title = "Avvisi",
                            empty = "Niente qui.",
                            clearAll = "Cancella tutto",
                            dismiss = "Ignora",
                        ),
                    failures =
                        FailureStrings(
                            sessionExpired =
                                "La sessione della tua lista è scaduta. Il catalogo resta salvato, ma devi " +
                                    "accedere di nuovo alla fonte per caricare le novità.",
                            outOfMemory =
                                "Non c'era memoria sufficiente per costruire questa schermata. " +
                                    "È un limite dell'applicazione, non della tua lista.",
                            invalidServer = "L'indirizzo del server non è valido.",
                            invalidServerScheme = "Controlli l'inizio dell'indirizzo: deve essere http:// o https://.",
                            authenticationRejected = "Il server ha rifiutato l'utente o la password.",
                            networkUnreachable = "Non è stato possibile raggiungere il server.",
                            httpError = "Il server ha risposto con un errore HTTP.",
                            responseTooLarge = "Il catalogo ha superato il limite sicuro di questa versione.",
                            invalidResponse = "Il server non ha restituito un catalogo Xtream compatibile. Dettagli in %1${'$'}s",
                            appFault =
                                "Non è stato possibile costruire questa schermata (%1${'$'}s). È un errore " +
                                    "dell'applicazione, non della tua lista. Dettagli in %2${'$'}s",
                        ),
                    startup =
                        StartupStrings(
                            openingSession = "Apertura della sessione…",
                            joiningList = "Unione di %1${'$'}s…",
                            loadingLiveCategories = "Caricamento categorie dei canali…",
                            loadingMovieCategories = "Caricamento categorie dei film…",
                            loadingSeriesCategories = "Caricamento categorie delle serie…",
                            downloadingMovies = "Scaricamento della lista dei film…",
                            downloadingSeries = "Scaricamento della lista delle serie…",
                            organising = "Organizzazione di film e serie…",
                            ready = "Pronto",
                        ),
                    screens =
                        ScreenStrings(
                            setupMissingProfileName = "Inserisci un nome profilo per continuare",
                            setupMissingConnection = "Compila server, utente e password, oppure scegli una lista già configurata",
                            diagnosticsAction = "Diagnostica",
                            diagnosticsTitle = "Diagnostica della connessione",
                            mergeSourcesTitle = "Unisci tutte le liste",
                            mergeSourcesHelp = "Mostra tutte le liste come un solo catalogo. Niente si ripete: la lista più grande guida e le altre completano.",
                            mergeSourcesRestart = "Le liste vengono riorganizzate subito.",
                            mergeSourcesFailed = "%1${'$'}s non ha risposto. Le altre liste continuano a funzionare.",
                            mergeSourcesOffline = "Non risponde",
                            diagnosticsLatencyGood = "Latenza bassa: i canali cambiano senza attesa",
                            diagnosticsLatencyFair = "Latenza alta: può bloccarsi e rallentare il cambio canale",
                            diagnosticsLatencyUnstable = "Latenza molto alta: causerà blocchi e interruzioni",
                            diagnosticsLatencyUnknown = "Non è stato possibile misurare la latenza",
                            diagnosticsRunning = "Test in corso…",
                            diagnosticsRun = "Riprova",
                            diagnosticsClose = "Chiudi",
                            diagnosticsDownload = "Download",
                            diagnosticsUpload = "Upload",
                            diagnosticsPing = "Latenza",
                            diagnosticsLoss = "Perdita di pacchetti",
                            diagnosticsCatalogue = "Lista",
                            diagnosticsConnection = "Connessione",
                            diagnosticsMemory = "Memoria",
                            diagnosticsAddress = "Indirizzo IP",
                            diagnosticsGateway = "Gateway",
                            diagnosticsNetmask = "Maschera di rete",
                            diagnosticsVerdictGood = "Va tutto bene",
                            diagnosticsVerdictWarning = "Da controllare",
                            diagnosticsVerdictProblem = "Problema rilevato",
                            diagnosticsQualityUnstable = "Connessione scarsa: causerà blocchi e interruzioni",
                            diagnosticsQualitySd = "Connessione modesta: sufficiente per la qualità standard",
                            diagnosticsQualityHd = "Connessione buona per i film in 1080p",
                            diagnosticsQualityUhd = "Connessione perfetta per 4K e TV in diretta",
                            diagnosticsQualityUnknown = "Non è stato possibile misurare la velocità",
                            diagnosticsWireless = "Wi-Fi: il cavo evita le interruzioni tipiche del wireless",
                            diagnosticsWired = "Cavo di rete",
                            diagnosticsNoLink = "Nessuna connessione di rete",
                            diagnosticsCatalogueEmpty = "La lista non si è caricata: verifica l’abbonamento con il venditore",
                            diagnosticsSignedOut = "Nessuna lista configurata",
                            diagnosticsLowMemory = "Poca memoria: chiudi altri programmi o riavvia l’app",
                            deviceCodeAction = "Codice del dispositivo",
                            deviceCodeHelp = "Invia questo codice a chi ti ha venduto la lista perché la configuri per te.",
                            setupRenameList = "Rinomina",
                            setupRemoveList = "Rimuovi",
                            setupRemoveListConfirm = "Rimuovere la lista «%1${'$'}s»? Anche la password viene cancellata.",
                            importFileMissing = "Il file selezionato non esiste più.",
                            importAccessDenied = "Il sistema non ha permesso di leggere quel file.",
                            importBlocked = "L'accesso al file è stato bloccato dal sistema.",
                            importFailed = "Impossibile importare la lista. Verifica che il file sia un M3U/M3U8 valido e riprova.",
                            movieDetailsLoading = "Caricamento della scheda del film…",
                            epgLoading = "Caricamento di ora e a seguire…",
                            guideNow = "Ora",
                            guideNext = "A seguire",
                            catchUpShow = "Rivedi (%1${'$'}s)",
                            catchUpHide = "Nascondi",
                            similarTitles = "Titoli simili",
                            epgUnavailable = "Guida non disponibile; il canale resta accessibile.",
                            epgEmpty = "La fonte non ha indicato alcun palinsesto.",
                            loadEpisodes = "Carica episodi",
                            episodesLoading = "Caricamento episodi…",
                            externalOpenFailed = "Impossibile aprire",
                            externalNoDefaultApp = "Questo sistema non offre un'applicazione predefinita per aprire il canale.",
                            externalRefused = "L'applicazione esterna ha rifiutato l'indirizzo. Nessun dato è stato copiato.",
                            externalHeadersWarning = "Il canale richiede intestazioni HTTP; un browser comune potrebbe non riprodurlo.",
                            externalAddressValid = "Indirizzo valido per un'applicazione esterna.",
                            headersUnsupported = "Questo canale richiede intestazioni HTTP che il lettore Windows attuale non sa ancora applicare. La riproduzione è disattivata invece di offrire un pulsante che fallirà.",
                            noChannelMatches = "Nessun canale corrisponde al filtro.",
                            connectXtreamTitle = "Connetti server Xtream",
                            searchingCatalogue = "Ricerca nel catalogo…",
                            noFurtherTitles = "Nessun altro titolo trovato nel catalogo.",
                            noPlayableEpisodes = "Il server non ha restituito episodi riproducibili.",
                            playerStopped = "Il motore video si è chiuso inaspettatamente.",
                            playerStartFailed = "Il motore video di Windows non è riuscito ad avviarsi.",
                            playerStalled = "Il server ha risposto, ma questo video non è partito. Riprova o scegli un altro titolo.",
                            updateCheckFailed = "Impossibile controllare gli aggiornamenti in questo momento.",
                            demoMovieNotice = "Titolo di esempio. Questa disponibilità è fittizia.",
                            demoSeriesNotice = "Serie di esempio. Questa disponibilità è fittizia.",
                        ),
                    remoteSource =
                        RemoteSourceStrings(
                            title = "Server personale (NAS)",
                            hint = "Legge una lista M3U salvata sul tuo server via WebDAV o FTP.",
                            addressLabel = "Indirizzo",
                            addressPlaceholder = "webdav://nas.local/media/lista.m3u",
                            userLabel = "Utente (facoltativo)",
                            passwordLabel = "Password (facoltativa)",
                            credentialsNotice = "Le credenziali servono solo per questa lettura e non vengono salvate.",
                            connect = "Connetti",
                            cancel = "Annulla",
                            unsupportedAddress = "Indirizzo non supportato. Usa webdav://, http:// o ftp://",
                        ),
                    ratings =
                        RatingStrings(
                            title = "Valutazioni",
                            source = "Punteggio TMDb",
                            votes = "%s voti",
                            critics = "Critica",
                            criticKeyLabel = "Chiave OMDb (facoltativa)",
                            criticKeyHint = "Aggiunge Tomatometer, Metascore e IMDb. Ottienila su omdbapi.com",
                            criticKeyPlaceholder = "Chiave API",
                            criticKeySaved = "Chiave salvata: i voti della critica sono visibili.",
                            criticKeyAbsent = "Senza chiave viene mostrato solo il punteggio del pubblico di TMDb.",
                            adultKeyTitle = "Copertine della guida per adulti",
                            adultKeyBody = "TMDb non copre questo catalogo, quindi le copertine richiedono una chiave ThePornDB. Senza, quelle righe continuano a mostrare il titolo. La chiave è tua: non viene inviata altrove.",
                            adultKeyPlaceholder = "Chiave API",
                            adultKeySaved = "Chiave salvata: le copertine verranno cercate.",
                            adultKeyAbsent = "Nessuna chiave: quelle righe mostrano solo il titolo.",
                            adultKeySite = "Ottieni una chiave su theporndb.net",
                            criticGuideButton = "Non sai come ottenerla?",
                            criticGuideTitle = "Ottenere una chiave OMDb",
                            criticGuideSubtitle = "Quattro passaggi. La chiave gratuita arriva per email.",
                            criticGuideOpenSite = "Apri omdbapi.com",
                            criticStep1Title = "Apri omdbapi.com",
                            criticStep1Body =
                                "Vai alla sezione API Key del sito. Non serve creare un account: " +
                                    "basta un indirizzo email.",
                            criticStep2Title = "Scegli il piano gratuito",
                            criticStep2Body =
                                "Seleziona FREE, che consente 1.000 richieste al giorno — molto più " +
                                    "di quanto serva a questa app. Il piano a pagamento non è necessario.",
                            criticStep3Title = "Inserisci la tua email e invia",
                            criticStep3Body =
                                "Indica un indirizzo a cui puoi accedere subito e descrivi brevemente " +
                                    "l'uso previsto, ad esempio \"uso personale\".",
                            criticStep4Title = "Attiva la chiave dalla email",
                            criticStep4Body =
                                "Riceverai la chiave con un link di attivazione. Aprilo, altrimenti la " +
                                    "chiave resta inattiva, poi incollala qui nelle impostazioni.",
                            criticSketchEmail = "La tua email",
                            criticSketchFree = "FREE",
                            criticSketchSubmit = "Invia",
                            criticSketchInbox = "Chiave + attivazione",
                        ),
                    discovery =
                        DiscoveryStrings(
                            title = "Scopri",
                            hint = "Scorri o usa i pulsanti: tenere lo aggiunge ai preferiti.",
                            keep = "Tieni",
                            skip = "Salta",
                            details = "Dettagli",
                            exhausted = "Hai visto tutto per ora.",
                            another = "Trova altri",
                            loading = "Creazione della selezione...",
                            kept = "Aggiunto ai preferiti",
                        ),
                    settingsTabs =
                        SettingsTabStrings(
                            general = "Generale",
                            content = "Contenuti",
                            subtitles = "Sottotitoli",
                            data = "Dati",
                            maintenance = "Manutenzione",
                        ),
                    cache =
                        CacheStrings(
                            title = "Conserva le copertine su questo computer",
                            explanation = "L'app conserva copertine e immagini sul tuo computer perche' la lista si apra subito, invece di scaricare tutto ogni volta.",
                            firstTimeWarning = "La prima volta puo' richiedere tempo: dipende da quanto e' grande la tua lista. Avviene in secondo piano e puoi usare l'app normalmente.",
                            sizeLabel = "Spazio riservato",
                            gigabytes = "%d GB",
                            disabled = "Non conservare",
                            estimate = "La tua lista richiede circa %s.",
                            start = "Inizia",
                            skip = "Non ora",
                            filling = "Salvataggio copertine",
                            progress = "%1${'$'}d di %2${'$'}d",
                            pause = "Pausa",
                            resume = "Riprendi",
                            cancel = "Annulla",
                            complete = "Tutto salvato.",
                            used = "In uso: %s",
                            clear = "Svuota cache",
                            clearTitle = "Svuotare la cache delle copertine?",
                            clearBody = "Le copertine salvate verranno eliminate e scaricate di nuovo quando serviranno. Non si perde nulla della tua lista.",
                            restartNote = "Il cambio di dimensione vale dalla prossima apertura dell'app.",
                            percent = "%d%%",
                            refresh = "Aggiorna",
                        ),
                    serviceCatalogue =
                        ServiceCatalogueStrings(
                            seeMore = "Vedi altro",
                            allFrom = "Tutto di %s",
                            backToShelves = "Torna ai servizi",
                            genreSelector = "Genere",
                            serviceSelector = "Servizio",
                            allGenres = "Tutti i generi",
                            allServices = "Tutti i servizi",
                            servicesUnavailable = "non indicato nella tua lista",
                            servicesLoading = "ricerca…",
                            duplicatesLabel = "Copie ripetute",
                            duplicatesHint = "Le liste portano spesso lo stesso film più volte, una per qualità o doppiaggio.",
                            duplicatesToggle = "Mostra una sola scheda per film",
                        ),
                    receiver =
                        CastReceiverStrings(
                            title = "Ricevi dal telefono",
                            hint =
                                "Permette al telefono di trovare questo computer e inviargli un " +
                                    "titolo. Devono essere sulla stessa rete. Digita il codice " +
                                    "qui sotto sul telefono, una volta sola.",
                            receiveNow = "Ricevi ora",
                            autoStart = "Attiva all’avvio dell’app",
                            codeLabel = "Codice",
                            codeExplanation =
                                "Questo codice resta sempre lo stesso su questo computer. " +
                                    "Digitalo una volta sul telefono e non te lo chiederà più. " +
                                    "Solo chi ha questo numero può inviare qui.",
                            regenerate = "Genera un nuovo codice",
                        ),
                    share = "Condividi",
                    shareTitle = "Condividi titolo",
                    shareSubtitle = "Invia un consiglio, non la tua lista.",
                    shareDestination = "Invia tramite",
                    shareByEmail = "E-mail",
                    shareCopyLink = "Copia link",
                    shareCopied = "Copiato!",
                    shareNoCredentials =
                        "Il link non contiene server, nome utente o password. " +
                            "Chi lo riceve lo apre con la propria lista.",
                    shareNotFoundTitle = "Non è nella tua lista",
                    shareNotFoundBody =
                        "Il tuo provider non offre questo titolo. Un link condiviso è un " +
                            "consiglio: ognuno lo apre con la propria lista.",
                    ),
                search = "Cerca",
                sources = "FONTI",
                profile = "Profilo",
                yourLibrary = "La tua libreria",
                connectXtream = "Connetti Xtream",
                importM3u = "Importa M3U",
                checkUpdate = "Cerca aggiornamenti",
                refreshCatalog = "Aggiorna elenchi",
                termsTitle = "Prima di iniziare",
                termsNoContent = "IPTV BURO non fornisce, ospita né rivende alcun canale, film o serie.",
                termsYourSource = "Usi la tua lista: l'app riproduce solo ciò che il tuo provider fornisce.",
                termsResponsibility = "Sei responsabile di avere il diritto di accedere ai contenuti che aggiungi.",
                                termsNoWarranty = "La riproduzione dipende dalla tua lista e dalla tua connessione. Non garantiamo che un determinato provider, canale o formato funzioni.",
                termsOneDevice = "Una licenza vale per un computer. Cambiare macchina richiede una nuova attivazione.",
                termsRefund = "Hai 7 giorni di prova prima di pagare. Dopo l acquisto, il diritto di recesso segue la legge del tuo paese.",
                termsReadFull = "Leggi i termini completi",
                termsAccept = "Accetto e continua",
                setupTitle = "Crea il tuo profilo",
                setupSubtitle = "Ogni profilo conserva i propri preferiti. I download restano disponibili per tutti.",
                setupProfileName = "Nome del profilo",
                setupUseExisting = "USA UNA LISTA GIÀ CONFIGURATA",
                setupYourList = "LA TUA LISTA",
                setupNewList = "OPPURE AGGIUNGI UNA NUOVA LISTA",
                setupListName = "Nome della lista",
                serverLabel = "Server",
                usernameLabel = "Nome utente",
                passwordLabel = "Password",
                setupContinue = "Continua",
                connectingTitle = "Caricamento della lista",
                connectingBody = "Connessione al provider e preparazione del catalogo. Può richiedere qualche istante.",
                setupFailedTitle = "Impossibile caricare la lista",
                setupFailedBody = "Il provider non ha risposto. Controlla server, nome utente e password.",
                setupRetry = "Riprova",
                chooseRating = "Voto",
                anyRating = "Tutti i voti",
                continueEmptyTitle = "Niente da continuare",
                continueEmptyBody = "I film e gli episodi che inizi compaiono qui.",
                resumeFrom = "Riprendi",
                startOver = "Dall'inizio",
                forgetProgress = "Rimuovi",
                metadataKeyLabel = "Chiave TMDb (facoltativa)",
                                tmdbGuide =
                                    TmdbGuideStrings(
                                        tmdbGuideTitle = "Come ottenere la tua chiave TMDb",
                                        tmdbGuideSubtitle = "Sei passaggi. È gratuito e richiede circa cinque minuti.",
                                        tmdbGuideOpenSignup = "Crea un account TMDb",
                                        tmdbGuideOpenApiPage = "Ho già un account",
                                        tmdbGuideButton = "Non sai come fare?",
                                        tmdbStep1Title = "Crea un account gratuito",
                                        tmdbStep1Body = "Apri themoviedb.org e clicca su Sign Up. Servono un indirizzo email, un nome utente e una password.",
                                        tmdbStep2Title = "Conferma la mail e accedi",
                                        tmdbStep2Body = "TMDb invia una mail di conferma. Senza confermarla, la pagina API non è disponibile.",
                                        tmdbStep3Title = "Apri Settings e poi API",
                                        tmdbStep3Body = "Clicca sulla tua immagine in alto a destra, scegli Settings e poi API nel menu laterale.",
                                        tmdbStep4Title = "Richiedi una chiave da sviluppatore",
                                        tmdbStep4Body = "Scegli Developer, non Commercial. È la scelta giusta per uso personale ed è gratuita.",
                                        tmdbStep5Title = "Compila il modulo",
                                        tmdbStep5Body = "Nome applicazione: IPTV BURO. URL: va bene un qualsiasi tuo indirizzo. Scopo: uso personale, per mostrare copertine e trame.",
                                        tmdbStep6Title = "Copia la chiave e incollala qui",
                                        tmdbStep6Body = "Copia il valore API Key (v3 auth), 32 caratteri, e incollalo nel campo di questa finestra. Viene salvata solo sul tuo computer.",
                                        tmdbSketchSignUp = "Sign Up",
                                        tmdbSketchApiMenu = "API",
                                        tmdbSketchRequestType = "Tipo di richiesta",
                                        tmdbSketchDeveloper = "Developer",
                                        tmdbSketchFormFields = "Nome, URL, scopo",
                                        tmdbSketchApiKeyLabel = "API Key (v3 auth)",
                                        tmdbSketchCopy = "Copia",
                                        tmdbSketchSettings = "Impostazioni",
                                        tmdbSketchPaste = "Incolla qui",
                                    ),
                metadataKeyHint = "Incolla la tua chiave da themoviedb.org/settings/api",
                metadataKeyPlaceholder = "Chiave API",
                layoutPoster = "Locandine",
                layoutCompact = "Compatto",
                layoutList = "Elenco",
                removeProfile = "Rimuovi",
                confirmRemoveProfile = "Confermi?",
                showPassword = "Mostra",
                hidePassword = "Nascondi",
                avatarUsePhoto = "Usa una foto",
                avatarRemovePhoto = "Rimuovi foto",
                avatarChoosePhotoTitle = "Scegli una foto per il profilo",
                checkingUpdate = "Ricerca aggiornamenti…",
                upToDate = "Hai già la versione più recente.",
                downloading = "Download in corso",
                installerVerified = "Installer verificato. Aggiornamento…",
                updateFailed = "Non è stato possibile installare l'aggiornamento.",
                privateSession = "Sessione privata",
                nothingSensitiveSaved = "Nulla di sensibile viene salvato",
                dailySelection = "SELEZIONE DEL GIORNO",
                continueWatching = "Continua a guardare",
                moviesForToday = "Film scelti per oggi",
                seriesToExplore = "Serie da esplorare",
                liveNow = "In diretta ora",
                heroFallbackTitle = "La tua libreria è pronta",
                heroSubtitle =
                    "Una selezione diversa ogni giorno, organizzata senza riversare l'intera " +
                        "libreria in un'unica schermata.",
                watchNow = "Guarda",
                details = "Dettagli",
                options = "opzioni",
                organizingToday = "Sto preparando la selezione di oggi…",
                seasonalBadge = "DI STAGIONE",
                tryAgain = "Riprova",
                watched = "guardato",
                onAir = "IN DIRETTA",
                emptyHeadline = "Tutta la tua libreria.\nSenza rumore.",
                emptyBody =
                    "Importa la tua fonte autorizzata e lascia che IPTV BURO organizzi canali, " +
                        "film e serie in un'unica esperienza su ogni schermo.",
                emptyBadge = "BURO NOCTURNE  •  LIBRERIA PRIVATA",
                credentialsStayLocal =
                    "La fonte viene riconnessa dalla cassaforte protetta di questo utente.",
                authenticating = "Autenticazione e preparazione del catalogo…",
                organizingPlaylist = "Sto organizzando la tua playlist…",
                noSensitiveData = "Nessun dato sensibile verrà salvato.",
                close = "Chiudi",
                cancel = "Annulla",
                understood = "Ho capito",
                whoIsWatching = "Chi sta guardando?",
                newProfile = "Nuovo profilo",
                addProfile = "Aggiungi",
                kidsProfile = "Profilo bambini",
                adultProfile = "Profilo adulti",
                forgetSource = "Dimentica fonte",
                searchChannel = "Cerca canale…",
                results = "risultati",
                items = "elementi",
                sourcesCount = "fonti",
                selectChannel = "Seleziona un canale",
                vaultProtected = "Cassaforte protetta",
                credentialsEncrypted = "Credenziali cifrate da Windows",
                endSession = "Termina sessione",
                searchCatalog = "Cerca in questo catalogo…",
                categories = "Categorie",
                allCategories = "Tutte",
                catalog = "Catalogo",
                noMatch = "Nessun elemento corrisponde al filtro.",
                previous = "Precedente",
                next = "Successiva",
                page = "Pagina",
                downloadsNoMatch = "Nessun risultato per questo filtro.",
                allItems = "Tutti",
                allYears = "Tutti gli anni",
                releasesIn = "Usciti nel",
                sessionActive = "Sessione attiva · connessione protetta su Windows",
                loadingCatalog = "Caricamento del catalogo…",
                sessionClosed = "Sessione terminata",
                backToCatalog = "Torna al catalogo",
                selectItem = "Seleziona un elemento",
                download = "Scarica",
                downloadInProgress = "Scaricamento",
                downloaded = "Scaricato",
                downloadFailed = "Download non riuscito",
                removeDownload = "Rimuovi download",
                downloads = "Download",
                resetSettings = "Reimposta impostazioni",
                resetConfirm = "Cancella tutto",
                resetWarning = "Cancella profili, preferiti e avanzamento. I file scaricati restano.",
                settings = "Impostazioni",
                languageLabel = "LINGUA",
                chooseYear = "Scegli anno",
                downloadPaused = "In pausa",
                downloadsEmptyTitle = "Nessuna copia offline",
                downloadsEmptyBody = "Apri un film e scegli Scarica. Le copie salvate compaiono qui e si riproducono senza connessione.",
                updateReadyBody = "L aggiornamento e stato scaricato e verificato. L app si chiude, Windows installa la nuova versione e l app si riapre da sola. Puo richiedere circa un minuto.",
                updateRestartNow = "Chiudi e aggiorna",
                updateLater = "Piu tardi",
                music = "Musica",
                musicHome = "Home",
                musicArtists = "Artisti",
                musicRadio = "Radio",
                musicNewReleases = "Novità",
                musicMostPlayed = "Più ascoltate",
                musicTracks = "brani",
                musicStations = "stazioni",
                musicAddPlaylist = "Aggiungi un elenco musicale",
                musicEmptyTitle = "Ancora nessuna musica",
                musicEmptyBody = "Aggiungi una playlist M3U di musica al tuo profilo per ascoltare qui.",
                musicNoArtists = "Questa lista non indica artisti.",
                musicNoRadio = "Questa lista non contiene stazioni radio.",
                musicNoDownloads = "La musica che scarichi compare qui.",
                musicBackToArtists = "Torna agli artisti",
                musicPlaylistLabel = "Playlist musicale",
                musicPlaylistOptional = "FACOLTATIVO",
                musicPlaylistHint = "Un M3U di sola musica. Senza, nulla cambia nell'app.",
                musicPlaylistChoose = "Scegli file",
                musicPlaylistRemove = "Rimuovi",
                musicPlaylistTitle = "Scegli la tua playlist musicale",
                musicPlaylists = "Playlist",
                musicPlaylistsEmpty = "Non hai ancora creato nessuna playlist.",
                musicPlaylistNew = "Nuova playlist",
                musicPlaylistNewName = "La mia playlist",
                musicPlaylistRename = "Rinomina",
                musicPlaylistDelete = "Elimina",
                musicPlaylistDuplicate = "Duplica",
                musicPlaylistDuplicateSuffix = "copia",
                musicPlaylistImport = "Importa M3U",
                musicPlaylistExport = "Esporta M3U",
                musicPlaylistBack = "Torna alle playlist",
                musicPlaylistEmptyTracks = "Questa playlist non contiene ancora brani.",
                musicPlaylistRemoveTrack = "Rimuovi dalla playlist",
                musicPlaylistMoveUp = "Sposta su",
                musicPlaylistMoveDown = "Sposta giù",
                musicSmartPlaylists = "Playlist intelligenti",
                musicSmartFavourites = "Preferiti",
                musicSmartRecentlyPlayed = "Ascoltate di recente",
                musicSmartMostPlayed = "Più ascoltate",
                musicSmartNeverPlayed = "Mai ascoltate",
                musicSmartRecentlyAdded = "Aggiunte di recente",
                musicExportWarningTitle = "Questo file può contenere indirizzi sensibili",
                musicExportWarningBody =
                    "Alcuni indirizzi di questa playlist sembrano includere credenziali o firme di accesso. " +
                        "Chi riceve il file potrebbe usare il tuo abbonamento. Esportalo solo per te stesso.",
                musicExportWarningConfirm = "Esporta comunque",
                musicExportWarningCancel = "Annulla",
                queueTitle = "Coda",
                queueNowPlaying = "In riproduzione",
                queueUpNext = "A seguire",
                queueEmptyBody = "Niente in coda. Usa “Riproduci dopo” o “Aggiungi alla fine”.",
                queuePlayNow = "Riproduci ora",
                queuePlayNext = "Riproduci dopo",
                queueAddToEnd = "Aggiungi alla fine",
                queueRemove = "Rimuovi dalla coda",
                queueClear = "Svuota",
                queueMoveUp = "Sposta su",
                queueMoveDown = "Sposta giù",
                queueOpen = "Apri la coda",
                queueClose = "Chiudi la coda",
                queueCount = "in coda",
                subscriptions = "Abbonamenti",
                subscriptionsDemoBadge = "DEMO",
                subscriptionsDemoNotice =
                    "Questi risultati sono di esempio, creati solo per mostrare la schermata. " +
                        "Nessun servizio di streaming reale è collegato.",
                subscriptionsWhereToWatch = "Dove guardare",
                subscriptionsInYourLibrary = "Già nella tua lista",
                subscriptionsIncludedInSubscription = "Incluso nel tuo abbonamento",
                subscriptionsFreeWithAds = "Gratis con pubblicità",
                subscriptionsRent = "Noleggia",
                subscriptionsBuy = "Acquista",
                subscriptionsRequiresSubscription = "Richiede un abbonamento",
                subscriptionsUnavailable = "Non disponibile qui",
                subscriptionsOpenProvider = "Apri nel servizio ufficiale",
                subscriptionsMyServices = "I miei servizi",
                subscriptionsRegion = "Regione",
                languageHint = "Lingua dei testi dell'app",
                regionHint = "Paese usato per sapere quali servizi di streaming offrono ogni film",
                metadataKeyUses = "Usata per copertine, cast, trailer e la scheda Abbonamenti",
                metadataKeySaved = "✓ Chiave salvata e già attiva.",
                metadataKeyUsingBundled = "Si sta usando la chiave predefinita dell'app.",
                settingsText =
                    SettingsStrings(
                        profileEdit = "Modifica",
                        profileEditTitle = "Modifica profilo",
                        profileNameLabel = "Nome",
                        profileAvatarLabel = "Immagine",
                        profileKidsLabel = "Modalità bambini",
                        profileKidsHint = "Mostra solo contenuti per bambini",
                        profileSourceLabel = "Lista",
                        profileSourceNone = "Usa quella già connessa",
                        profileSourceChange = "Cambia lista",
                        profileMusicLabel = "Musica (M3U)",
                        profileMusicNone = "Nessun file scelto",
                        profileMusicChoose = "Scegli file",
                        profileMusicClear = "Rimuovi",
                        profileSave = "Salva",
                        expandSidebar = "Espandi",
                        subtitlesLabel = "Sottotitoli",
                        subtitlesHint = "Vale per il prossimo titolo aperto",
                        subtitlesBackground = "Sfondo scuro",
                        historyTitle = "Cronologia",
                        historyClearAll = "Cancella tutto",
                        historyEmpty = "Non hai ancora guardato nulla.",
                        categoriesLabel = "Categorie",
                        categoriesHint = "Nascondi ciò che non usi, o proteggilo con il PIN",
                        categoryHide = "Nascondi",
                        categoryLock = "Proteggi",
                        clockLabel = "Orologio",
                        clockHint = "Formato dell'ora mostrata in alto",
                        clock24h = "24 ore",
                        clock12h = "12 ore",
                        parentalTitle = "Controllo genitori",
                        parentalHint = "Protegge le categorie con un PIN di quattro cifre",
                        parentalSetPin = "Crea un PIN",
                        parentalChangePin = "Cambia PIN",
                        parentalRemovePin = "Rimuovi PIN",
                        parentalCurrentPin = "PIN attuale",
                        parentalNewPin = "Nuovo PIN",
                        parentalWrongPin = "PIN errato.",
                        parentalDefaultPin = "Blocco attivo con il PIN standard 0000. Scegline uno tuo perché non venga indovinato.",
                        parentalPinSaved = "✓ PIN salvato.",
                        parentalPinFormat = "Il PIN deve avere quattro cifre.",
                        parentalLockAdult = "Blocca automaticamente le categorie per adulti",
                        parentalLocked = "Contenuto protetto",
                        firstRunTitle = "Preparazione di IPTV BURO",
                        firstRunBody =
                            "Solo questa volta richiede più tempo: la tua lista viene letta e " +
                                "organizzata. Alla prossima apertura sarà già pronta.",
                        firstRunTmdbTitle = "Copertine e trame",
                        firstRunTmdbBody =
                            "Per copertine, trame e cast aggiungi una chiave TMDb nelle Opzioni. " +
                                "È gratuita: crea un account su themoviedb.org, richiedi la chiave " +
                                "API e incollala in BURO.",
                        startupAuthenticating = "Accesso in corso…",
                        startupOrganising = "Organizzazione della tua lista…",
                        profileKeyLabel = "Chiave solo per questo profilo",
                        profileKeyHint =
                            "Lascia vuoto per usare la stessa chiave degli altri profili. " +
                                "Compila per far usare a questo profilo il proprio account TMDb.",
                        profileKeyShared = "Si sta usando la chiave condivisa",
                        profileKeyOwn = "Questo profilo usa una chiave propria",
                        moreSettingsTitle = "Altre opzioni",
                        moreSettingsHint = "Sottotitoli, orologio, categorie e controllo genitori",
                        multiviewAdd = "Guarda insieme",
                        multiviewRemove = "Togli dalla vista multipla",
                        multiviewOpen = "Guarda insieme",
                        multiviewHint = "Guarda 2-4 canali insieme",
                                                musicWorkshop = "Officina musica",
                        musicWorkshopSummary = "%d brani · %d corretti",
                        musicWorkshopNames = "Nomi",
                        musicWorkshopDuplicates = "Ripetuti",
                        musicWorkshopApplyAll = "Correggi tutti (%d)",
                        musicWorkshopApplyOne = "Correggi",
                        musicWorkshopUndoAll = "Annulla tutte le %d correzioni",
                        musicWorkshopNothingToFix = "Niente da correggere. I nomi sono già puliti.",
                        musicWorkshopNoDuplicates = "Nessun brano ripetuto.",
                        musicWorkshopSameAddress = "Stesso indirizzo — certamente lo stesso brano",
                        musicWorkshopSameName = "Stesso nome — controlla prima di rimuovere",
                        multiviewAudioFrom = "Audio da",
                        multiviewFullScreen = "Schermo intero",
                        multiviewWindowed = "Finestra",
                        multiviewUnavailable = "Impossibile aprire lo schermo diviso",
                        multiviewUnavailableHint = "I canali scelti non hanno risposto.",
                        multiviewEmpty = "Scegli prima i canali",
                        multiviewEmptyHint = "Passa sul canale e premi ▦, fino a quattro.",
                        multiviewClear = "Svuota",
                        multiviewFull = "Massimo %d canali",
                        licenseTrialOverTitle = "I tuoi 7 giorni sono finiti",
                        licenseTrialOverBody =
                            "Speriamo ti sia piaciuto. Per continuare a guardare, attiva questo dispositivo.",
                        licenseExpiredTitle = "La tua licenza è scaduta",
                        licenseExpiredBody = "Rinnova per continuare a usare IPTV BURO su questo dispositivo.",
                        licenseRevokedTitle = "Licenza annullata",
                        licenseRevokedBody =
                            "Questa licenza è stata annullata. Se pensi sia un errore, scrivici.",
                        licenseVerifyTitle = "Dobbiamo verificare la licenza",
                        licenseVerifyBody =
                            "È passato un po' dall'ultimo controllo. Basta collegarsi a internet una volta.",
                        licenseOfflineTitle = "Nessuna connessione",
                        licenseOfflineBody =
                            "Non è stato possibile verificare la licenza. Controlla la connessione.",
                        licenseTwoYears = "per 2 anni",
                        licenseWhyNotLifetime =
                            "Non è a vita perché l'app continua a essere aggiornata: i provider " +
                                "cambiano, i formati cambiano, Windows cambia.",
                        licenseBuy = "Attiva dispositivo",
                        licenseRetry = "Riprova",
                        licenseDeviceLabel = "Il tuo dispositivo",
                        licenseHaveKey = "Ho un codice di attivazione",
                        licenseKeyPlaceholder = "Codice",
                        licenseRedeem = "Usa codice",
                        licenseDaysLeft = "%d giorni rimasti",
                                                epgShowSchedule = "Vedi programmazione (%d)",
                        epgHideSchedule = "Nascondi programmazione",
                        parentalUnlock = "Inserisci il PIN per aprire questa categoria",
                    ),
                subscriptionsSynopsis = "Trama",
                subscriptionsCast = "Cast",
                subscriptionsWatchTrailer = "▶  Guarda il trailer",
                subscriptionsAvailableOn = "Disponibile su",
                subscriptionsFilterMovies = "Film",
                subscriptionsFilterSeries = "Serie",
                subscriptionsFilterUpcoming = "Prossimamente",
                subscriptionsFilterThisWeek = "Questa settimana",
                subscriptionsUpcomingNote =
                    "Titoli con data di uscita fissata. Non si sa ancora quale servizio li offrirà.",
                subscriptionsEmptyBody = "Nessun risultato per questo titolo.",
                subscriptionsBrowseByService = "Per servizio",
                subscriptionsNoShelves = "Nessun servizio da mostrare per ora.",
                subscriptionsLoadFailed = "Impossibile caricare i servizi. Controlla la connessione e riprova.",
                subscriptionsKeyRejected =
                    "TMDb ha rifiutato la chiave API. Controllala nelle Opzioni — una chiave nuova può richiedere qualche minuto.",
                subscriptionsBackToServices = "Torna ai servizi",
                subscriptionsSelectedTitle = "Titolo selezionato",
                licenseText =
                    LicenseStrings(
                        trialTitle = "La prova è terminata",
                        trialBody =
                            "I 7 giorni sono finiti. Attiva questo dispositivo per continuare a usare "
                                + "IPTV BURO.",
                        expiredTitle = "Licenza scaduta",
                        expiredBody = "La licenza di questo dispositivo è scaduta. Rinnovala per continuare.",
                        revokedTitle = "Licenza revocata",
                        revokedBody =
                            "Questa licenza non è più attiva. Se pensi si tratti di un errore, scrivici "
                                + "indicando il codice qui sotto.",
                        unreachableTitle = "Impossibile verificare la licenza",
                        unreachableBody =
                            "Non riusciamo a raggiungere il server. Controlla la connessione e riprova.",
                        verifyTitle = "Verifica necessaria",
                        verifyBody =
                            "L'app ha funzionato offline abbastanza a lungo. Collegati a internet una "
                                + "volta per continuare.",
                        deviceLabel = "Dispositivo",
                        activationKeyLabel = "Chiave di attivazione",
                        activationKeyHint = "Conservala. Vale solo su questo computer; perderla significa comprarne un'altra.",
                        macLabel = "MAC",
                        scanHint = "Inquadra il codice con il telefono",
                        openInBrowser = "Apri nel browser",
                        retry = "Riprova",
                        haveKey = "Hai un codice di attivazione?",
                        keyPlaceholder = "XXXX-XXXX",
                        redeem = "Attiva",
                        redeemFailed = "Codice non valido o già usato.",
                        keyAvailable = "Chiave valida e libera.",
                        keyAvailableDays = { days -> "Chiave da $days giorni, libera." },
                        keyYours = "Questa chiave appartiene già a questo dispositivo.",
                        keyInUse = "Questa chiave è già usata su un altro dispositivo.",
                        keyExpired = "Questa chiave è scaduta.",
                        quit = "Chiudi",
                        back = "Torna all'app",
                        backToPurchase = "Preferisco pagare",
                        termYears = "%d anni",
                        priceEur = "9,90 € · 2 anni",
                        priceUsd = "9,90 $ · 2 anni",
                        priceBrl = "99,90 R$ · 2 anni",
                        whyNotLifetime =
                            "Perché non a vita: l'app continua a essere mantenuta e aggiornata. I 2 anni "
                                + "pagano quel lavoro.",
                        copied = "Copiato",
                        clockWarning =
                            "L'orologio del computer sembra sbagliato. Le date della licenza vengono "
                                + "comunque dal server.",
                        trialDaysLeft = "%d giorni di prova rimasti",
                        licenseDaysLeft = "%d giorni rimasti",
                        licenseLastDay = "Ultimo giorno",
                        trialLastDay = "Ultimo giorno di prova",
                        buyNow = "Attiva",
                    ),
            )
    }
}

/**
 * Ambient string table.
 *
 * Screens read this instead of threading a `DesktopLanguage` parameter through every composable,
 * which is what left most of the UI hardcoded in Portuguese.
 */
val LocalDesktopStrings: ProvidableCompositionLocal<DesktopStrings> =
    staticCompositionLocalOf { DesktopStrings.of(DesktopLanguage.PORTUGUESE_BRAZIL) }

/** Shorthand for `LocalDesktopStrings.current`. */
val strings: DesktopStrings
    @Composable get() = LocalDesktopStrings.current

/**
 * The TMDb key walkthrough, held apart from [DesktopStrings].
 *
 * Not a stylistic grouping. The JVM refuses to load a class whose constructor takes more than 254
 * arguments, and adding these twenty-six inline pushed it to 257: the class then failed to load at
 * all, which took the entire test suite down with a ClassFormatError before a single assertion ran
 * — including the test that exists to catch exactly this overflow.
 *
 * Anything added here from now on costs the outer class nothing.
 */
/**
 * Wording for sharing a title, and for receiving one.
 *
 * What a share carries is a *recommendation* — a normalised title, a year and a public poster —
 * which the recipient's own app resolves against their own playlist. Nothing about the sender's
 * provider travels. See `TitleShareLink` in the domain, which enforces that; these strings say it
 * to the user.
 */
/**
 * Downloading a whole season or series.
 *
 * Nested rather than added to [DesktopStrings] directly. That class is a few fields short of the
 * JVM's 255-argument limit for a constructor, which has already been hit once here and produced a
 * `ClassFormatError` that failed every desktop test before a single assertion ran. New groups of
 * related strings go in their own class from now on.
 */
/**
 * Sending a title to another screen on the network.
 *
 * Nested for the same reason [DownloadStrings] is: [DesktopStrings] is close to the JVM's
 * 255-argument constructor limit, which has already been hit here once.
 */
data class CastStrings(
    /** The button beside Compartilhar. */
    val castAction: String,
    val castTitle: String,
    val castSearching: String,
    val castNoneFound: String,
    /** The typed-address fallback, offered only when the search found nothing. */
    val castManualTitle: String,
    val castManualHint: String,
    val castManualLabel: String,
    val castManualConnect: String,
    val castManualInvalid: String,
    val castSearchAgain: String,
    /** Takes the screen's name. */
    val castCodePrompt: String,
    val castCodeHint: String,
    val castCodeInvalid: String,
    val castSend: String,
    /** Takes the screen's name. */
    val castSending: String,
    /** Takes the screen's name. Says *sent*, never *playing* — see CastSendState.Sent. */
    val castSent: String,
    /** Takes the screen's name. */
    val castFailed: String,
    val castChooseAnother: String,
)

/**
 * Marking a title for later: liking it, and asking to be reminded about it.
 *
 * Grouped for the reason [DownloadStrings] and [CastStrings] are — [DesktopStrings] sits at the
 * ceiling StringsConstructorLimitTest enforces, and this file has crossed the real JVM limit once
 * and shipped an app that would not start. [favorites] moved down here from the top level to pay
 * for the reminder strings, which is the regrouping that test exists to force.
 */
/**
 * Letting a phone on the network send this machine a title.
 *
 * These were written straight into SettingsDialog in Portuguese, so anyone running the app in
 * English, German, Italian or Spanish met a wall of Portuguese in the middle of their settings.
 * Grouped rather than added to [DesktopStrings] for the usual reason — see the note there on the
 * JVM's constructor ceiling, which this file has crossed once already.
 */
data class CastReceiverStrings(
    /** The section's own heading. */
    val title: String,
    /** What the feature does, and the one condition it needs. */
    val hint: String,
    /** Listening right now, this session. */
    val receiveNow: String,
    /** Whether it starts listening again next time the app opens. */
    val autoStart: String,
    /** Takes the four-digit code. */
    val codeLabel: String,
    /** That the code stays the same, so it is typed once. */
    val codeExplanation: String,
    /** Throws the code away and mints another, for when it has been seen by the wrong person. */
    val regenerate: String,
)

/**
 * The Assinaturas area's expanded view: one service's whole catalogue.
 *
 * A group of its own, and a small one, because [DesktopStrings] is at the ceiling
 * StringsConstructorLimitTest defends — thirty subscription strings already sit at the top level
 * and the next area to grow should come down here rather than push that number up. Nested inside
 * [ShareStrings] for the same reason [CastStrings] is: somewhere is needed, and a new top-level
 * field is the one thing that cannot be afforded.
 */
/**
 * The bell beside the profile, and the panel behind it.
 *
 * Grouped for the reason every recent group is: [DesktopStrings] sits at the ceiling
 * StringsConstructorLimitTest defends, and a new top-level field is the one thing that cannot be
 * afforded.
 */
data class NotificationStrings(
    /** The bell's own name, for a screen reader and the tooltip. */
    val title: String,
    /** Shown in place of the list when the bell is empty. */
    val empty: String,
    /** Forgets everything in the bell. */
    val clearAll: String,
    /** Forgets one notice. */
    val dismiss: String,
)

/**
 * The artwork cache: what it buys, what it costs, and how far along it is.
 *
 * Grouped for the reason every recent group is — [DesktopStrings] sits at the ceiling
 * StringsConstructorLimitTest defends, and a new top-level field is the one thing that cannot be
 * afforded.
 */
/**
 * Descobrir: one card at a time, kept or passed over.
 *
 * Grouped for the usual reason — [DesktopStrings] sits at the ceiling StringsConstructorLimitTest
 * defends, and a new top-level field is the one thing that cannot be afforded.
 */
/**
 * The ratings block on a title's page.
 *
 * Named for the source rather than dressed as somebody else's brand. Rotten Tomatoes' Tomatometer
 * and IMDb's score are licensed marks with no free API — showing a tomato beside a number that came
 * from TMDb would be inventing an endorsement, which is the one thing a ratings panel must not do.
 */
data class RatingStrings(
    /** The section heading. */
    val title: String,
    /** Who the score came from, said plainly. */
    val source: String,
    /** How many people voted. Takes a formatted count. */
    val votes: String,
    /**
     * The label under the critics' row, separating it from the audience score above.
     *
     * The score names themselves — Tomatometer, Metascore, IMDb — are not translated. They are the
     * companies' own names for their own measures, and a translated "Tomatômetro" would be a name
     * Rotten Tomatoes does not use.
     */
    val critics: String,
    /** The settings section where the OMDb key is pasted. */
    val criticKeyLabel: String,
    /** What setting the key buys, said before the field rather than after it. */
    val criticKeyHint: String,
    /** Empty-field placeholder. */
    val criticKeyPlaceholder: String,
    /** Confirms a pasted key took effect — there is no Save button here either. */
    val criticKeySaved: String,
    /** What the app does without one: the audience score alone, which is the default. */
    val criticKeyAbsent: String,
    /**
     * The way in for somebody who has never registered an OMDb key.
     *
     * The hint named `omdbapi.com` and stopped there, which assumes the reader knows that the site
     * wants an email address, that the free tier is the "FREE" radio button, and that the key
     * arrives by email rather than on screen. The TMDb key beside it has had a step-by-step guide
     * since a customer got stuck on exactly that kind of form; this is the same door.
     */
    val criticGuideButton: String,
    /** Heading for the adult-artwork key. */
    val adultKeyTitle: String,
    /** Explains why this catalogue needs its own key and what happens without one. */
    val adultKeyBody: String,
    val adultKeyPlaceholder: String,
    val adultKeySaved: String,
    val adultKeyAbsent: String,
    /** Opens theporndb.net, where the key is issued. */
    val adultKeySite: String,
    val criticGuideTitle: String,
    val criticGuideSubtitle: String,
    /** Opens the page where the key is requested. */
    val criticGuideOpenSite: String,
    val criticStep1Title: String,
    val criticStep1Body: String,
    val criticStep2Title: String,
    val criticStep2Body: String,
    val criticStep3Title: String,
    val criticStep3Body: String,
    val criticStep4Title: String,
    val criticStep4Body: String,
    /** Labels drawn inside the page sketches, short enough to fit them. */
    val criticSketchEmail: String,
    val criticSketchFree: String,
    val criticSketchSubmit: String,
    val criticSketchInbox: String,
)

/**
 * Adding a playlist that lives on the user's own server.
 *
 * One address field and two optional credential fields. The protocol is read from the address
 * rather than chosen from a menu: people paste what their NAS's own interface showed them, and
 * being asked to classify it first is a question they should not have to answer.
 */
data class RemoteSourceStrings(
    /** The button that opens this, and the dialog's heading. */
    val title: String,
    /** What this is for, said once above the field. */
    val hint: String,
    /** The address field's label. */
    val addressLabel: String,
    /** Shown in the empty address field: an example, so the expected shape is obvious. */
    val addressPlaceholder: String,
    /** The username field, which is optional. */
    val userLabel: String,
    /** The password field, also optional. */
    val passwordLabel: String,
    /** Says the credentials are not written to disk, before the user types one. */
    val credentialsNotice: String,
    /** Starts the import. */
    val connect: String,
    /** Closes without importing. */
    val cancel: String,
    /** Shown when the address names a protocol this app cannot read. */
    val unsupportedAddress: String,
)

/**
 * The settings screen's tabs.
 *
 * Eleven sections in one scrolling column meant every setting was found by reading past the ten
 * that were not wanted. Grouped, each tab holds two or three related things, and the group names
 * answer "where would this live" without opening them.
 */
data class SettingsTabStrings(
    /** Language, region, clock: what the app is, before what it shows. */
    val general: String,
    /** What appears in the catalogue: categories and the parental lock. */
    val content: String,
    /** Subtitles, which are numerous enough to be their own group. */
    val subtitles: String,
    /** Keys and cached artwork: what the app fetches and what it keeps. */
    val data: String,
    /** Updates, refresh, ending the session, and the version. */
    val maintenance: String,
)

data class DiscoveryStrings(
    /** The sidebar destination and the screen's heading. */
    val title: String,
    /** What the screen is for, said once above the first card. */
    val hint: String,
    /** Keeps the title: it goes to favourites. */
    val keep: String,
    /** Passes over it. */
    val skip: String,
    /**
     * Opens the full page instead of deciding.
     *
     * The card holds a poster, a year, a genre and — only once TMDb answers — a synopsis, so on
     * most cards there was nothing to judge but the artwork.
     */
    val details: String,
    /** Shown when the deck runs out. */
    val exhausted: String,
    /** Builds another deck. */
    val another: String,
    /** While the first deck is being gathered. */
    val loading: String,
    /** Confirms what keeping did, since the card leaves immediately. */
    val kept: String,
)

data class CacheStrings(
    /** The setting's name, in the first-run panel and in settings. */
    val title: String,
    /** What keeping artwork on disk actually buys. */
    val explanation: String,
    /** The honest warning about the first fill. */
    val firstTimeWarning: String,
    /** Labels the size chooser. */
    val sizeLabel: String,
    /** Takes the number of gigabytes. */
    val gigabytes: String,
    /** The zero option, worded as a choice rather than as an absence. */
    val disabled: String,
    /** Roughly how much this library would need. Takes a formatted size such as "4 GB". */
    val estimate: String,
    /** Accepts the choice on the first-run panel. */
    val start: String,
    /** Declines it, without implying the app is worse for it. */
    val skip: String,
    /** Heading over the progress bar. */
    val filling: String,
    /** Takes done and total. */
    val progress: String,
    val pause: String,
    val resume: String,
    val cancel: String,
    /** Everything the budget allows is stored. */
    val complete: String,
    /** How much is currently held. Takes a formatted size. */
    val used: String,
    /** Empties the cache. */
    val clear: String,
    /** Title of the confirmation, because clearing throws away a fill that took minutes. */
    val clearTitle: String,
    /** What is lost by clearing, in plain terms. */
    val clearBody: String,
    /** That a size change waits for the next launch, because Coil is built once per process. */
    val restartNote: String,
    /** Percentage on the always-visible bar. Takes a whole number. */
    val percent: String,
    /** Fetches artwork the library has gained since the last fill. */
    val refresh: String,
)

data class ServiceCatalogueStrings(
    /** Ends a service's rail and opens that service's full catalogue. */
    val seeMore: String,
    /** The heading of that catalogue. Takes the service's name. */
    val allFrom: String,
    /** Returns from the full catalogue to the shelves. */
    val backToShelves: String,
    /**
     * The two category selectors on Films and Series.
     *
     * They replaced a single horizontal rail that mixed both kinds of category — "Acao" beside
     * "Netflix" — in one strip of thirty-odd chips that scrolled sideways. Naming the two questions
     * separately is most of what makes the catalogue navigable.
     */
    val genreSelector: String,
    val serviceSelector: String,
    /** The "no filter" option in each: every genre, or every service. */
    val allGenres: String,
    val allServices: String,
    /**
     * Shown in place of the Serviço selector when the playlist files nothing by service.
     *
     * The selector used to be hidden in that case, which is why it was missing from Filmes on a list
     * that organises films by genre — and a control that appears on one tab and not another reads as
     * a fault. Naming the reason is more use than silence.
     */
    val servicesUnavailable: String,
    /** Shown while the service index is being built from TMDb, so the control is not read as empty. */
    val servicesLoading: String,
    /**
     * The setting that collapses a provider's repeated copies of one film.
     *
     * Added after the Filmes grid was reported as showing duplicate films: a list carries the same
     * title three or four times over, one per quality or dubbing, and the catalogue listed all of
     * them.
     */
    val duplicatesLabel: String,
    val duplicatesHint: String,
    val duplicatesToggle: String,
)

data class SavedForLaterStrings(
    /** The sidebar destination, and the detail-page button when the title is not yet liked. */
    val favorites: String,
    /** The button on the detail page, before it is marked. */
    val reminderAdd: String,
    /** The same button once the title is marked, which is also how it is unmarked. */
    val reminderActive: String,
    /**
     * Said plainly under the button once a title is marked.
     *
     * Windows stores reminders but has nothing that announces one — the daily notification is the
     * phone's. Without this line the button would imply an alert that is never going to arrive,
     * which is the kind of promise that makes someone distrust the rest of the app.
     */
    val reminderNoNotice: String,
    /** The sidebar destination, and the heading of the screen it opens. */
    val remindersTitle: String,
    /** Shown when nothing has been marked, in place of an empty list. */
    val remindersEmpty: String,
    /** On an entry the library has a row for. */
    val reminderOpen: String,
    /** Forgets one entry. */
    val reminderRemove: String,
    /**
     * Said on an entry the library cannot open.
     *
     * The ordinary case for an upcoming film, and not a fault: without this the row would simply
     * fail to respond and read as broken.
     */
    val reminderNotInLibrary: String,
    /** Body of a new-episode notice. Takes the season and the episode number. */
    val newEpisodeBody: String,
    /** Body of a new-season notice. Takes the season number. */
    val newSeasonBody: String,
    /** The switch that turns the in-app notice on and off. */
    val reminderAnnounce: String,
    /** Labels the hour picker. */
    val reminderHourLabel: String,
    /** Explains that the notice appears in the app, since nothing arrives while it is closed. */
    val reminderInAppOnly: String,
    /** The notice itself. Takes the number of marked titles. */
    val reminderNoticeBody: String,
    /** Dismisses the notice for the rest of the day. */
    val reminderNoticeDismiss: String,
)

data class DownloadStrings(
    /** The button beside Compartilhar. */
    val downloadSeries: String,
    /** Takes the season number. */
    val downloadSeason: String,
    val downloadSeriesConfirmTitle: String,
    val downloadSeasonConfirmTitle: String,
    /** Takes the episode count — the number the confirmation actually promises to fetch. */
    val downloadConfirmBody: String,
    val downloadConfirmAction: String,
)

/**
 * What the user is told when something fails.
 *
 * These lived as Portuguese literals inside `FailureMessages`, so an app running in English, Spanish,
 * German or Italian answered a failed connection in Portuguese — and a failure message is exactly the
 * moment a user most needs to read what it says.
 *
 * The rules the messages themselves follow are set out on `FailureMessages`: the provider is blamed
 * only for a genuine Xtream failure, and the exception's own text is never shown, because OkHttp puts
 * the full request URL — with the subscriber's username and password — into its IOException.
 */
/**
 * What the splash says it is doing.
 *
 * These were Portuguese literals in `DesktopAppState`, so the first screen a non-Portuguese user ever
 * sees — before any setting can be reached — was in a language they may not read.
 */
/**
 * The last of the wording that used to live inside the screens.
 *
 * Grouped by where it appears rather than by type, because that is how it is read: somebody
 * translating checks one screen at a time. Nested on [ShareStrings] for the ceiling reason set out
 * there — `DesktopStrings` is at its enforced constructor limit.
 */
data class ScreenStrings(
    /**
     * Browsing every subscription as one catalogue.
     *
     * Somebody who buys a second list to fill the gaps in the first ends up switching
     * between them to find which has the film they want — work the app should be doing.
     */
    val mergeSourcesTitle: String,
    val mergeSourcesHelp: String,
    /**
     * That the lists are rebuilt straight away.
     *
     * The switch rebuilds the catalogue on the spot, behind the loading screen. It used to
     * store a preference and change nothing until the next launch, which reads as a dead
     * button — reported twice about this very feature.
     */
    val mergeSourcesRestart: String,
    /** Names the list that is down, so the rest are visibly still working. */
    val mergeSourcesFailed: String,
    /**
     * The same thing in two words, for the row that already carries the name.
     *
     * The sidebar prints the list's name on the line above, so the full sentence would repeat it
     * and wrap a narrow column into three lines saying one thing.
     */
    val mergeSourcesOffline: String,
    /**
     * Why Continuar is disabled.
     *
     * A disabled button that says nothing is the defect: somebody filled in the whole
     * list at the bottom of a scrolling form and had nothing happen, with the one empty
     * field off the top of the screen.
     */
    val setupMissingProfileName: String,
    val setupMissingConnection: String,
    /**
     * The connection test, beside the button that refreshes the lists.
     *
     * There because somebody whose picture freezes has no way to tell whether the fault is their
     * Wi-Fi, their provider or the app — and without an answer, they blame the app.
     */
    val diagnosticsAction: String,
    val diagnosticsTitle: String,
    val diagnosticsRunning: String,
    val diagnosticsRun: String,
    val diagnosticsClose: String,
    val diagnosticsDownload: String,
    val diagnosticsUpload: String,
    val diagnosticsPing: String,
    val diagnosticsLoss: String,
    val diagnosticsCatalogue: String,
    val diagnosticsConnection: String,
    val diagnosticsMemory: String,
    val diagnosticsAddress: String,
    val diagnosticsGateway: String,
    val diagnosticsNetmask: String,
    /** The verdicts, which are the whole point: a reading nobody can act on is just a number. */
    val diagnosticsVerdictGood: String,
    val diagnosticsVerdictWarning: String,
    val diagnosticsVerdictProblem: String,
    val diagnosticsQualityUnstable: String,
    val diagnosticsQualitySd: String,
    val diagnosticsQualityHd: String,
    val diagnosticsQualityUhd: String,
    val diagnosticsQualityUnknown: String,
    /**
     * What a latency reading means for watching.
     *
     * A viewer shown "173 ms" in red learns that something is wrong and nothing about
     * what. Latency is the reading most likely to explain a picture that freezes on a
     * connection whose speed looks fine, so it is the one that most needs a sentence.
     */
    val diagnosticsLatencyGood: String,
    val diagnosticsLatencyFair: String,
    val diagnosticsLatencyUnstable: String,
    val diagnosticsLatencyUnknown: String,
    val diagnosticsWireless: String,
    val diagnosticsWired: String,
    val diagnosticsNoLink: String,
    val diagnosticsCatalogueEmpty: String,
    val diagnosticsSignedOut: String,
    val diagnosticsLowMemory: String,
    /**
     * Opens this machine's code, from the profile screen.
     *
     * Reachable before any playlist is configured, on purpose: the code is how a seller finds
     * this install to set the list up remotely, and the person who needs that most is exactly
     * the one who has not managed to configure anything yet.
     */
    val deviceCodeAction: String,
    /** Explains what the code is for, on the screen that shows it. */
    val deviceCodeHelp: String,
    /** Renames a saved playlist, whose label is all that tells one from another. */
    val setupRenameList: String,
    /** Forgets a saved playlist and the password stored with it. */
    val setupRemoveList: String,
    /** Asks before forgetting one. Takes the list's name. */
    val setupRemoveListConfirm: String,
    // Importing a playlist file.
    val importFileMissing: String,
    val importAccessDenied: String,
    val importBlocked: String,
    /** Anything else that goes wrong reading a playlist, naming the formats that are accepted. */
    val importFailed: String,
    /** Shown while a film's details are being fetched. */
    val movieDetailsLoading: String,
    /** Shown while a channel's now-and-next guide is being fetched. */
    val epgLoading: String,
    /** Labels what is on air now, on a playlist channel whose list brought a guide. */
    val guideNow: String,
    /** Labels what follows it. */
    val guideNext: String,
    /** Opens the list of programmes the channel still has a recording of. Takes a count. */
    val catchUpShow: String,
    /** Closes it again. */
    val catchUpHide: String,
    /** Heading of the shelf of other titles to open from the one on screen. */
    val similarTitles: String,
    /** The source offers no guide for this channel; watching it still works. */
    val epgUnavailable: String,
    /** The guide answered, but with nothing scheduled in it. */
    val epgEmpty: String,
    /** The button that fetches a series' episode list. */
    val loadEpisodes: String,
    /** Shown while that episode list is being fetched. */
    val episodesLoading: String,
    // Handing a channel to an external player.
    val externalOpenFailed: String,
    val externalNoDefaultApp: String,
    val externalRefused: String,
    val externalHeadersWarning: String,
    val externalAddressValid: String,
    /** Why a channel cannot be played here at all, rather than offering a button that fails. */
    val headersUnsupported: String,
    /** The channel list, filtered to nothing. */
    val noChannelMatches: String,
    // The Xtream sign-in dialog.
    val connectXtreamTitle: String,
    // Searching the catalogue from a person's page.
    val searchingCatalogue: String,
    val noFurtherTitles: String,
    val noPlayableEpisodes: String,
    // The video engine.
    val playerStopped: String,
    val playerStartFailed: String,
    val playerStalled: String,
    // Checking for a new version.
    val updateCheckFailed: String,
    /** The demo catalogue's own disclaimer, which must never read as a real listing. */
    val demoMovieNotice: String,
    val demoSeriesNotice: String,
)

data class StartupStrings(
    val openingSession: String,
    /**
     * A second subscription being read during the splash.
     *
     * It carries the list's name because the wait belongs to that list: a bar that sits still for a
     * minute with no explanation reads as a hang, which is how it was reported — "quando apliquei o
     * app nao foi para tela de carregamento".
     */
    val joiningList: String,
    val loadingLiveCategories: String,
    val loadingMovieCategories: String,
    val loadingSeriesCategories: String,
    val downloadingMovies: String,
    val downloadingSeries: String,
    val organising: String,
    val ready: String,
)

data class FailureStrings(
    /** The catalogue survived on disk and the session did not. */
    val sessionExpired: String,
    /** Not enough memory to build the screen. An app limitation, not the user's list. */
    val outOfMemory: String,
    val invalidServer: String,
    /**
     * The address whose scheme is nearly right.
     *
     * "The address is not valid" on `http:7/buro.ac` reads as a wrong address, and somebody who
     * typed the host correctly is certain the app is at fault — reported exactly that way. Naming
     * the part that is wrong turns it into a typo they can see.
     */
    val invalidServerScheme: String,
    val authenticationRejected: String,
    val networkUnreachable: String,
    val httpError: String,
    val responseTooLarge: String,
    /** Takes the log location: this is the one cause the user cannot act on unaided. */
    val invalidResponse: String,
    /** Takes the exception's type name and the log location. Never its message. */
    val appFault: String,
)

data class ShareStrings(
    /**
     * Sending to a screen on the network.
     *
     * Nested here rather than on [DesktopStrings], which is at its enforced ceiling: casting is
     * sharing with a different destination, so this is where it belongs anyway.
     */
    val cast: CastStrings,
    /**
     * The other end of casting: this machine receiving instead of sending.
     *
     * Beside [cast] because it is the same feature seen from the other side, and nested here for
     * the same ceiling reason.
     */
    val receiver: CastReceiverStrings,
    /** One service's whole catalogue, reached from the end of its shelf. */
    val serviceCatalogue: ServiceCatalogueStrings,
    /** The artwork cache setting and its progress. */
    val cache: CacheStrings,
    /** The settings screen's own navigation. */
    val settingsTabs: SettingsTabStrings,
    /** The Descobrir screen. */
    val discovery: DiscoveryStrings,
    /** The ratings block on a title page. */
    val ratings: RatingStrings,
    /** Importing a playlist the user keeps on their own server. */
    val remoteSource: RemoteSourceStrings,
    /** The bell beside the profile. */
    val notifications: NotificationStrings,
    /** What the user is told when something fails. */
    val failures: FailureStrings,
    /** What the splash says it is doing while the catalogue loads. */
    val startup: StartupStrings,
    /** Wording that used to sit inside individual screens. */
    val screens: ScreenStrings,
    /** The button on the title page, beside Favourites. */
    val share: String,
    val shareTitle: String,
    val shareSubtitle: String,
    val shareDestination: String,
    val shareByEmail: String,
    val shareCopyLink: String,
    val shareCopied: String,
    /** Stated in the product, so the sender knows their subscription is not in the message. */
    val shareNoCredentials: String,
    /**
     * A received link whose title this user's own provider does not carry. Not an error: the share
     * names a title, and whether it exists is a fact about the recipient's list.
     */
    val shareNotFoundTitle: String,
    val shareNotFoundBody: String,
)

data class TmdbGuideStrings(
    val tmdbGuideTitle: String,
    val tmdbGuideSubtitle: String,
    val tmdbGuideOpenSignup: String,
    val tmdbGuideOpenApiPage: String,
    /** The entry point in settings, beside the direct link to the API page. */
    val tmdbGuideButton: String,
    val tmdbStep1Title: String,
    val tmdbStep1Body: String,
    val tmdbStep2Title: String,
    val tmdbStep2Body: String,
    val tmdbStep3Title: String,
    val tmdbStep3Body: String,
    val tmdbStep4Title: String,
    val tmdbStep4Body: String,
    val tmdbStep5Title: String,
    val tmdbStep5Body: String,
    val tmdbStep6Title: String,
    val tmdbStep6Body: String,
    /** Labels drawn inside the page sketches, short enough to fit them. */
    val tmdbSketchSignUp: String,
    val tmdbSketchApiMenu: String,
    val tmdbSketchRequestType: String,
    val tmdbSketchDeveloper: String,
    val tmdbSketchFormFields: String,
    val tmdbSketchApiKeyLabel: String,
    val tmdbSketchCopy: String,
    val tmdbSketchSettings: String,
    val tmdbSketchPaste: String,
)

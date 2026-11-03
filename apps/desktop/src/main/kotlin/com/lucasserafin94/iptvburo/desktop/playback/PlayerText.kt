package com.lucasserafin94.iptvburo.desktop.playback

import com.lucasserafin94.iptvburo.desktop.user.DesktopLanguage
import com.lucasserafin94.iptvburo.domain.model.AudioOutputMode

/**
 * Every word the player shows, in the app's language.
 *
 * The overlay was written with Portuguese literals throughout — its own comment admitted it — so a
 * viewer running the app in English or German met Portuguese at exactly the moment they were
 * watching. Kept apart from DesktopStrings because that class is near the JVM's constructor-argument
 * ceiling, and the player's words belong together anyway.
 */
data class PlayerText(
    val back: String,
    val play: String,
    val pause: String,
    val rewind: String,
    val forward: String,
    val volume: String,
    val mute: String,
    val unmute: String,
    val muted: String,
    val fullScreen: String,
    val exitFullScreen: String,
    val miniPlayer: String,
    val backToApp: String,
    val favorite: String,
    val unfavorite: String,
    val speed: String,
    val aspect: String,
    val audio: String,
    val subtitles: String,
    /** The speaker layout, kept apart from [audio], which is the audio track. */
    val speakers: String,
    val brightness: String,
    /** Brightness only reaches the next title; VLC builds the filter with the video chain. */
    val brightnessNextTitle: String,
    val settings: String,
    val live: String,
    val loading: String,
    val reconnecting: String,
    val retry: String,
    val nextEpisode: String,
    val previousChannel: String,
    val nextChannel: String,
    /** "Next episode in %d s". */
    val nextEpisodeIn: String,
    val playNow: String,
    val cancel: String,
    /** A one-line reminder of the keyboard, shown as the shortcuts tooltip. */
    val shortcutsHint: String,
    /** The space bar's name, for the play button's tooltip. */
    val spaceKey: String,
    val aspectDefault: String,
    val aspectFill: String,
    val audioOutputSystem: String,
    val audioOutputHeadphones: String,
) {
    fun aspectLabel(ratio: PlaybackAspectRatio): String =
        when (ratio) {
            PlaybackAspectRatio.DEFAULT -> aspectDefault
            PlaybackAspectRatio.FILL -> aspectFill
            else -> ratio.label
        }

    fun audioOutputLabel(mode: AudioOutputMode): String =
        when (mode) {
            AudioOutputMode.SYSTEM -> audioOutputSystem
            AudioOutputMode.STEREO -> "2.0"
            AudioOutputMode.SURROUND_51 -> "5.1"
            AudioOutputMode.SURROUND_71 -> "7.1"
            AudioOutputMode.HEADPHONES -> audioOutputHeadphones
        }

    companion object {
        fun of(language: DesktopLanguage): PlayerText =
            when (language) {
                DesktopLanguage.PORTUGUESE_BRAZIL -> PORTUGUESE
                DesktopLanguage.ENGLISH -> ENGLISH
                DesktopLanguage.SPANISH -> SPANISH
                DesktopLanguage.GERMAN -> GERMAN
                DesktopLanguage.ITALIAN -> ITALIAN
            }

        private val PORTUGUESE =
            PlayerText(
                back = "Voltar",
                play = "Reproduzir",
                pause = "Pausar",
                rewind = "Voltar 10 s",
                forward = "Avançar 30 s",
                volume = "Volume",
                mute = "Silenciar",
                unmute = "Ativar som",
                muted = "Sem som",
                fullScreen = "Tela cheia",
                exitFullScreen = "Sair da tela cheia",
                miniPlayer = "Janela pequena",
                backToApp = "Voltar ao app",
                favorite = "Favoritar",
                unfavorite = "Remover dos favoritos",
                speed = "Velocidade",
                aspect = "Formato da imagem",
                audio = "Áudio",
                subtitles = "Legendas",
                speakers = "Alto-falantes",
                brightness = "Brilho",
                brightnessNextTitle = "vale a partir do próximo título",
                settings = "Ajustes",
                live = "AO VIVO",
                loading = "Preparando vídeo…",
                reconnecting = "A conexão caiu. Reconectando…",
                retry = "Tentar novamente",
                nextEpisode = "Próximo episódio",
                previousChannel = "Canal anterior",
                nextChannel = "Próximo canal",
                nextEpisodeIn = "Próximo episódio em %d s",
                playNow = "Assistir agora",
                cancel = "Cancelar",
                shortcutsHint =
                    "Espaço pausa · ←/→ 10 s · ↑/↓ volume · M sem som · F tela cheia · N próximo episódio",
                spaceKey = "Espaço",
                aspectDefault = "Padrão",
                aspectFill = "Preencher",
                audioOutputSystem = "Padrão",
                audioOutputHeadphones = "Fone",
            )

        private val ENGLISH =
            PlayerText(
                back = "Back",
                play = "Play",
                pause = "Pause",
                rewind = "Back 10 s",
                forward = "Forward 30 s",
                volume = "Volume",
                mute = "Mute",
                unmute = "Unmute",
                muted = "Muted",
                fullScreen = "Full screen",
                exitFullScreen = "Exit full screen",
                miniPlayer = "Mini player",
                backToApp = "Back to the app",
                favorite = "Add to favourites",
                unfavorite = "Remove from favourites",
                speed = "Speed",
                aspect = "Picture shape",
                audio = "Audio",
                subtitles = "Subtitles",
                speakers = "Speakers",
                brightness = "Brightness",
                brightnessNextTitle = "applies from the next title",
                settings = "Settings",
                live = "LIVE",
                loading = "Preparing video…",
                reconnecting = "Connection lost. Reconnecting…",
                retry = "Try again",
                nextEpisode = "Next episode",
                previousChannel = "Previous channel",
                nextChannel = "Next channel",
                nextEpisodeIn = "Next episode in %d s",
                playNow = "Play now",
                cancel = "Cancel",
                shortcutsHint =
                    "Space pauses · ←/→ 10 s · ↑/↓ volume · M mute · F full screen · N next episode",
                spaceKey = "Space",
                aspectDefault = "Original",
                aspectFill = "Fill",
                audioOutputSystem = "Default",
                audioOutputHeadphones = "Headphones",
            )

        private val SPANISH =
            PlayerText(
                back = "Volver",
                play = "Reproducir",
                pause = "Pausar",
                rewind = "Atrás 10 s",
                forward = "Adelante 30 s",
                volume = "Volumen",
                mute = "Silenciar",
                unmute = "Activar sonido",
                muted = "Sin sonido",
                fullScreen = "Pantalla completa",
                exitFullScreen = "Salir de pantalla completa",
                miniPlayer = "Ventana pequeña",
                backToApp = "Volver a la app",
                favorite = "Añadir a favoritos",
                unfavorite = "Quitar de favoritos",
                speed = "Velocidad",
                aspect = "Formato de imagen",
                audio = "Audio",
                subtitles = "Subtítulos",
                speakers = "Altavoces",
                brightness = "Brillo",
                brightnessNextTitle = "se aplica desde el próximo título",
                settings = "Ajustes",
                live = "EN VIVO",
                loading = "Preparando el vídeo…",
                reconnecting = "Se perdió la conexión. Reconectando…",
                retry = "Reintentar",
                nextEpisode = "Siguiente episodio",
                previousChannel = "Canal anterior",
                nextChannel = "Canal siguiente",
                nextEpisodeIn = "Siguiente episodio en %d s",
                playNow = "Ver ahora",
                cancel = "Cancelar",
                shortcutsHint =
                    "Espacio pausa · ←/→ 10 s · ↑/↓ volumen · M silencio · F pantalla completa · N siguiente",
                spaceKey = "Espacio",
                aspectDefault = "Original",
                aspectFill = "Rellenar",
                audioOutputSystem = "Predeterminado",
                audioOutputHeadphones = "Auriculares",
            )

        private val GERMAN =
            PlayerText(
                back = "Zurück",
                play = "Abspielen",
                pause = "Pause",
                rewind = "10 s zurück",
                forward = "30 s vor",
                volume = "Lautstärke",
                mute = "Stumm",
                unmute = "Ton an",
                muted = "Stumm",
                fullScreen = "Vollbild",
                exitFullScreen = "Vollbild beenden",
                miniPlayer = "Kleines Fenster",
                backToApp = "Zurück zur App",
                favorite = "Zu Favoriten",
                unfavorite = "Aus Favoriten entfernen",
                speed = "Geschwindigkeit",
                aspect = "Bildformat",
                audio = "Audio",
                subtitles = "Untertitel",
                speakers = "Lautsprecher",
                brightness = "Helligkeit",
                brightnessNextTitle = "gilt ab dem nächsten Titel",
                settings = "Einstellungen",
                live = "LIVE",
                loading = "Video wird vorbereitet …",
                reconnecting = "Verbindung unterbrochen. Neuer Versuch …",
                retry = "Erneut versuchen",
                nextEpisode = "Nächste Folge",
                previousChannel = "Vorheriger Sender",
                nextChannel = "Nächster Sender",
                nextEpisodeIn = "Nächste Folge in %d s",
                playNow = "Jetzt ansehen",
                cancel = "Abbrechen",
                shortcutsHint =
                    "Leertaste Pause · ←/→ 10 s · ↑/↓ Lautstärke · M stumm · F Vollbild · N nächste Folge",
                spaceKey = "Leertaste",
                aspectDefault = "Original",
                aspectFill = "Füllen",
                audioOutputSystem = "Standard",
                audioOutputHeadphones = "Kopfhörer",
            )

        private val ITALIAN =
            PlayerText(
                back = "Indietro",
                play = "Riproduci",
                pause = "Pausa",
                rewind = "Indietro 10 s",
                forward = "Avanti 30 s",
                volume = "Volume",
                mute = "Silenzia",
                unmute = "Riattiva audio",
                muted = "Senza audio",
                fullScreen = "Schermo intero",
                exitFullScreen = "Esci da schermo intero",
                miniPlayer = "Finestra piccola",
                backToApp = "Torna all'app",
                favorite = "Aggiungi ai preferiti",
                unfavorite = "Rimuovi dai preferiti",
                speed = "Velocità",
                aspect = "Formato immagine",
                audio = "Audio",
                subtitles = "Sottotitoli",
                speakers = "Altoparlanti",
                brightness = "Luminosità",
                brightnessNextTitle = "vale dal prossimo titolo",
                settings = "Impostazioni",
                live = "IN DIRETTA",
                loading = "Preparazione del video…",
                reconnecting = "Connessione persa. Riconnessione…",
                retry = "Riprova",
                nextEpisode = "Prossimo episodio",
                previousChannel = "Canale precedente",
                nextChannel = "Canale successivo",
                nextEpisodeIn = "Prossimo episodio tra %d s",
                playNow = "Guarda ora",
                cancel = "Annulla",
                shortcutsHint =
                    "Spazio pausa · ←/→ 10 s · ↑/↓ volume · M muto · F schermo intero · N prossimo episodio",
                spaceKey = "Spazio",
                aspectDefault = "Originale",
                aspectFill = "Riempi",
                audioOutputSystem = "Predefinito",
                audioOutputHeadphones = "Cuffie",
            )
    }
}

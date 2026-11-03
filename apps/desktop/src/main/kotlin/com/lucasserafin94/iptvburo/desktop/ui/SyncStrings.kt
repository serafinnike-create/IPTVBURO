package com.lucasserafin94.iptvburo.desktop.ui

import com.lucasserafin94.iptvburo.desktop.sync.SyncProblem
import com.lucasserafin94.iptvburo.desktop.user.DesktopLanguage

/**
 * Words for "Sincronizar aparelhos" in Settings.
 *
 * A table of its own, like [WebServiceStrings], so the feature does not grow each of
 * [DesktopStrings]' five constructors. `%d` is a number of devices or favourites, `%s` a code.
 */
data class SyncStrings(
    val title: String,
    val hint: String,
    val createCode: String,
    val codeLabel: String,
    val codeHint: String,
    val joinField: String,
    val join: String,
    val membersFormat: String,
    val syncNow: String,
    val leave: String,
    val syncing: String,
    val lastSyncFormat: String,
    val receivedFormat: String,
    val licenceRequired: String,
    val notInGroup: String,
    val unknownCode: String,
    val groupFull: String,
    val network: String,
    val rejected: String,
) {
    fun problem(problem: SyncProblem): String =
        when (problem) {
            SyncProblem.LICENCE_REQUIRED -> licenceRequired
            SyncProblem.NOT_IN_GROUP -> notInGroup
            SyncProblem.UNKNOWN_CODE -> unknownCode
            SyncProblem.GROUP_FULL -> groupFull
            SyncProblem.NETWORK -> network
            SyncProblem.REJECTED -> rejected
        }

    companion object {
        fun of(language: DesktopLanguage): SyncStrings =
            when (language) {
                DesktopLanguage.PORTUGUESE_BRAZIL -> PtBr
                DesktopLanguage.ENGLISH -> En
                DesktopLanguage.GERMAN -> De
                DesktopLanguage.ITALIAN -> It
                DesktopLanguage.SPANISH -> Es
            }

        private val PtBr =
            SyncStrings(
                title = "Sincronizar aparelhos",
                hint = "Os mesmos favoritos no PC, no celular, na TV e no Mac. Sem conta nem senha: um aparelho mostra um código e o outro digita. Os perfis são combinados pelo nome.",
                createCode = "Mostrar código",
                codeLabel = "Código: %s",
                codeHint = "Digite este código no outro aparelho, em Sincronizar aparelhos. Vale por 10 minutos.",
                joinField = "Código do outro aparelho",
                join = "Entrar",
                membersFormat = "%d aparelhos sincronizados",
                syncNow = "Sincronizar agora",
                leave = "Parar de sincronizar",
                syncing = "Sincronizando…",
                lastSyncFormat = "Última sincronização: %s",
                receivedFormat = "%d favoritos recebidos dos outros aparelhos",
                licenceRequired = "A sincronização precisa de uma licença ativa neste aparelho.",
                notInGroup = "Este aparelho não está sincronizado com nenhum outro.",
                unknownCode = "Código errado ou expirado. Peça um código novo no outro aparelho.",
                groupFull = "Já há 10 aparelhos sincronizados, que é o máximo.",
                network = "Sem conexão com o servidor. Tente de novo daqui a pouco.",
                rejected = "O servidor recusou o pedido. Tente de novo.",
            )

        private val En =
            SyncStrings(
                title = "Sync devices",
                hint = "The same favourites on PC, phone, TV and Mac. No account or password: one device shows a code and the other types it. Profiles are matched by name.",
                createCode = "Show code",
                codeLabel = "Code: %s",
                codeHint = "Type this code on the other device, under Sync devices. It works for 10 minutes.",
                joinField = "Code from the other device",
                join = "Join",
                membersFormat = "%d devices in sync",
                syncNow = "Sync now",
                leave = "Stop syncing",
                syncing = "Syncing…",
                lastSyncFormat = "Last sync: %s",
                receivedFormat = "%d favourites received from the other devices",
                licenceRequired = "Sync needs an active licence on this device.",
                notInGroup = "This device is not in sync with any other.",
                unknownCode = "Wrong or expired code. Ask the other device for a new one.",
                groupFull = "10 devices are already in sync, which is the most allowed.",
                network = "No connection to the server. Try again in a moment.",
                rejected = "The server refused the request. Try again.",
            )

        private val De =
            SyncStrings(
                title = "Geräte synchronisieren",
                hint = "Dieselben Favoriten auf PC, Handy, TV und Mac. Ohne Konto und Passwort: Ein Gerät zeigt einen Code, das andere gibt ihn ein. Profile werden über den Namen zugeordnet.",
                createCode = "Code anzeigen",
                codeLabel = "Code: %s",
                codeHint = "Gib diesen Code auf dem anderen Gerät unter Geräte synchronisieren ein. Er gilt 10 Minuten.",
                joinField = "Code vom anderen Gerät",
                join = "Beitreten",
                membersFormat = "%d Geräte synchronisiert",
                syncNow = "Jetzt synchronisieren",
                leave = "Synchronisierung beenden",
                syncing = "Wird synchronisiert…",
                lastSyncFormat = "Zuletzt synchronisiert: %s",
                receivedFormat = "%d Favoriten von den anderen Geräten erhalten",
                licenceRequired = "Die Synchronisierung braucht eine aktive Lizenz auf diesem Gerät.",
                notInGroup = "Dieses Gerät ist mit keinem anderen synchronisiert.",
                unknownCode = "Falscher oder abgelaufener Code. Lass dir auf dem anderen Gerät einen neuen geben.",
                groupFull = "Es sind bereits 10 Geräte synchronisiert, das ist das Maximum.",
                network = "Keine Verbindung zum Server. Versuche es gleich noch einmal.",
                rejected = "Der Server hat die Anfrage abgelehnt. Versuche es erneut.",
            )

        private val It =
            SyncStrings(
                title = "Sincronizza dispositivi",
                hint = "Gli stessi preferiti su PC, telefono, TV e Mac. Nessun account né password: un dispositivo mostra un codice e l'altro lo digita. I profili si abbinano per nome.",
                createCode = "Mostra codice",
                codeLabel = "Codice: %s",
                codeHint = "Digita questo codice sull'altro dispositivo, in Sincronizza dispositivi. Vale 10 minuti.",
                joinField = "Codice dell'altro dispositivo",
                join = "Unisciti",
                membersFormat = "%d dispositivi sincronizzati",
                syncNow = "Sincronizza ora",
                leave = "Interrompi sincronizzazione",
                syncing = "Sincronizzazione…",
                lastSyncFormat = "Ultima sincronizzazione: %s",
                receivedFormat = "%d preferiti ricevuti dagli altri dispositivi",
                licenceRequired = "La sincronizzazione richiede una licenza attiva su questo dispositivo.",
                notInGroup = "Questo dispositivo non è sincronizzato con nessun altro.",
                unknownCode = "Codice errato o scaduto. Chiedi un nuovo codice all'altro dispositivo.",
                groupFull = "Ci sono già 10 dispositivi sincronizzati, il massimo consentito.",
                network = "Nessuna connessione al server. Riprova tra poco.",
                rejected = "Il server ha rifiutato la richiesta. Riprova.",
            )

        private val Es =
            SyncStrings(
                title = "Sincronizar dispositivos",
                hint = "Los mismos favoritos en el PC, el móvil, la TV y el Mac. Sin cuenta ni contraseña: un dispositivo muestra un código y el otro lo escribe. Los perfiles se emparejan por nombre.",
                createCode = "Mostrar código",
                codeLabel = "Código: %s",
                codeHint = "Escribe este código en el otro dispositivo, en Sincronizar dispositivos. Vale 10 minutos.",
                joinField = "Código del otro dispositivo",
                join = "Unirse",
                membersFormat = "%d dispositivos sincronizados",
                syncNow = "Sincronizar ahora",
                leave = "Dejar de sincronizar",
                syncing = "Sincronizando…",
                lastSyncFormat = "Última sincronización: %s",
                receivedFormat = "%d favoritos recibidos de los otros dispositivos",
                licenceRequired = "La sincronización necesita una licencia activa en este dispositivo.",
                notInGroup = "Este dispositivo no está sincronizado con ningún otro.",
                unknownCode = "Código incorrecto o caducado. Pide uno nuevo en el otro dispositivo.",
                groupFull = "Ya hay 10 dispositivos sincronizados, que es el máximo.",
                network = "Sin conexión con el servidor. Inténtalo de nuevo en un momento.",
                rejected = "El servidor rechazó la solicitud. Inténtalo de nuevo.",
            )
    }
}

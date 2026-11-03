package com.lucasserafin94.iptvburo.desktop.ui

import com.lucasserafin94.iptvburo.desktop.user.DesktopLanguage

/**
 * Words for the web services (GoDorama): the settings switch, the 18+ question, the home card, the
 * page's own window and the "only GoDorama" choice at setup.
 *
 * Kept apart from [DesktopStrings] so one small feature does not add a field to each of its five
 * very large constructors. Every `%s` is the service's name.
 */
data class WebServiceStrings(
    val sectionTitle: String,
    val sectionHint: String,
    val tagline: String,
    val open: String,
    val enable: String,
    val disable: String,
    val ageTitle: String,
    val ageBody: String,
    val ageConfirm: String,
    val ageCancel: String,
    val signOut: String,
    val close: String,
    val historyNote: String,
    val homeTitle: String,
    val setupOnly: String,
    val setupOnlyHint: String,
    val openFailed: String,
    val offerTitle: String,
    val offerBody: String,
    val offerLater: String,
) {
    fun format(
        template: String,
        name: String,
    ): String = template.replace("%s", name)

    companion object {
        fun of(language: DesktopLanguage): WebServiceStrings =
            when (language) {
                DesktopLanguage.PORTUGUESE_BRAZIL -> PtBr
                DesktopLanguage.ENGLISH -> En
                DesktopLanguage.GERMAN -> De
                DesktopLanguage.ITALIAN -> It
                DesktopLanguage.SPANISH -> Es
            }

        private val PtBr =
            WebServiceStrings(
                sectionTitle = "Serviços",
                sectionHint = "Serviços que você assina à parte e assiste na página deles, dentro do BURO. Você entra com a sua conta; o BURO não guarda a sua senha.",
                tagline = "Microdramas em vídeo vertical",
                open = "Abrir %s",
                enable = "Ativar %s",
                disable = "Desativar",
                ageTitle = "Somente para maiores de 18",
                ageBody = "Pelos termos do próprio serviço, %s é para maiores de 18 anos. Você entra com a sua conta na página do serviço; o BURO nunca guarda a sua senha.",
                ageConfirm = "Tenho 18 anos ou mais",
                ageCancel = "Cancelar",
                signOut = "Sair da conta",
                close = "Fechar",
                historyNote = "O que você assiste fica salvo na sua conta %s.",
                homeTitle = "Seus serviços",
                setupOnly = "Usar só %s",
                setupOnlyHint = "Sem lista IPTV: o BURO abre só %s. Dá para adicionar uma lista depois.",
                openFailed = "Não foi possível abrir %s.",
                offerTitle = "Novidade: %s",
                offerBody = "Agora o BURO também abre o %s, com a sua conta. Microdramas em vídeo vertical, dentro do app.",
                offerLater = "Agora não",
            )

        private val En =
            WebServiceStrings(
                sectionTitle = "Services",
                sectionHint = "Services you subscribe to separately and watch on their own page, inside BURO. You sign in with your own account; BURO never stores your password.",
                tagline = "Short vertical drama series",
                open = "Open %s",
                enable = "Turn on %s",
                disable = "Turn off",
                ageTitle = "For adults only",
                ageBody = "By its own terms, %s is for people aged 18 or over. You sign in with your own account on the service's page; BURO never stores your password.",
                ageConfirm = "I am 18 or older",
                ageCancel = "Cancel",
                signOut = "Sign out",
                close = "Close",
                historyNote = "What you watch is saved in your %s account.",
                homeTitle = "Your services",
                setupOnly = "Use only %s",
                setupOnlyHint = "No IPTV list: BURO opens only %s. You can add a list later.",
                openFailed = "Could not open %s.",
                offerTitle = "New: %s",
                offerBody = "BURO now also opens %s with your own account. Short vertical drama series, inside the app.",
                offerLater = "Not now",
            )

        private val De =
            WebServiceStrings(
                sectionTitle = "Dienste",
                sectionHint = "Dienste, die du separat abonnierst und auf ihrer eigenen Seite in BURO ansiehst. Du meldest dich mit deinem eigenen Konto an; BURO speichert dein Passwort nie.",
                tagline = "Kurze Serien im Hochformat",
                open = "%s öffnen",
                enable = "%s aktivieren",
                disable = "Deaktivieren",
                ageTitle = "Nur für Erwachsene",
                ageBody = "Laut seinen eigenen Bedingungen ist %s erst ab 18 Jahren. Du meldest dich auf der Seite des Dienstes mit deinem eigenen Konto an; BURO speichert dein Passwort nie.",
                ageConfirm = "Ich bin 18 oder älter",
                ageCancel = "Abbrechen",
                signOut = "Abmelden",
                close = "Schließen",
                historyNote = "Was du schaust, wird in deinem %s-Konto gespeichert.",
                homeTitle = "Deine Dienste",
                setupOnly = "Nur %s verwenden",
                setupOnlyHint = "Ohne IPTV-Liste: BURO öffnet nur %s. Eine Liste kannst du später hinzufügen.",
                openFailed = "%s konnte nicht geöffnet werden.",
                offerTitle = "Neu: %s",
                offerBody = "BURO öffnet jetzt auch %s mit deinem eigenen Konto. Kurze Hochformat-Serien, direkt in der App.",
                offerLater = "Nicht jetzt",
            )

        private val It =
            WebServiceStrings(
                sectionTitle = "Servizi",
                sectionHint = "Servizi a cui ti abboni a parte e che guardi sulla loro pagina, dentro BURO. Accedi con il tuo account; BURO non salva mai la tua password.",
                tagline = "Serie brevi in verticale",
                open = "Apri %s",
                enable = "Attiva %s",
                disable = "Disattiva",
                ageTitle = "Solo per maggiorenni",
                ageBody = "Secondo i suoi stessi termini, %s è riservato ai maggiori di 18 anni. Accedi con il tuo account sulla pagina del servizio; BURO non salva mai la tua password.",
                ageConfirm = "Ho 18 anni o più",
                ageCancel = "Annulla",
                signOut = "Esci dall'account",
                close = "Chiudi",
                historyNote = "Ciò che guardi resta salvato nel tuo account %s.",
                homeTitle = "I tuoi servizi",
                setupOnly = "Usa solo %s",
                setupOnlyHint = "Senza lista IPTV: BURO apre solo %s. Puoi aggiungere una lista più tardi.",
                openFailed = "Impossibile aprire %s.",
                offerTitle = "Novità: %s",
                offerBody = "Ora BURO apre anche %s con il tuo account. Serie brevi in verticale, dentro l’app.",
                offerLater = "Non ora",
            )

        private val Es =
            WebServiceStrings(
                sectionTitle = "Servicios",
                sectionHint = "Servicios que contratas aparte y ves en su propia página, dentro de BURO. Entras con tu cuenta; BURO nunca guarda tu contraseña.",
                tagline = "Microdramas en vídeo vertical",
                open = "Abrir %s",
                enable = "Activar %s",
                disable = "Desactivar",
                ageTitle = "Solo para mayores de 18",
                ageBody = "Según sus propios términos, %s es para mayores de 18 años. Entras con tu cuenta en la página del servicio; BURO nunca guarda tu contraseña.",
                ageConfirm = "Tengo 18 años o más",
                ageCancel = "Cancelar",
                signOut = "Cerrar sesión",
                close = "Cerrar",
                historyNote = "Lo que ves queda guardado en tu cuenta de %s.",
                homeTitle = "Tus servicios",
                setupOnly = "Usar solo %s",
                setupOnlyHint = "Sin lista IPTV: BURO abre solo %s. Puedes añadir una lista después.",
                openFailed = "No se pudo abrir %s.",
                offerTitle = "Novedad: %s",
                offerBody = "Ahora BURO también abre %s con tu cuenta. Microdramas en vídeo vertical, dentro de la app.",
                offerLater = "Ahora no",
            )
    }
}

package com.lucasserafin94.iptvburo.desktop.ui

import com.lucasserafin94.iptvburo.desktop.user.DesktopLanguage

/**
 * The optional service line on the first setup screen: "Também tenho GoDorama".
 *
 * A table of its own, like [WebServiceStrings], for one small addition to setup. `%s` is the
 * service's name.
 */
data class SetupServiceStrings(
    val optionalTitle: String,
    val alsoHave: String,
    val alsoHaveHint: String,
) {
    fun format(
        template: String,
        name: String,
    ): String = template.replace("%s", name)

    companion object {
        fun of(language: DesktopLanguage): SetupServiceStrings =
            when (language) {
                DesktopLanguage.PORTUGUESE_BRAZIL -> PtBr
                DesktopLanguage.ENGLISH -> En
                DesktopLanguage.GERMAN -> De
                DesktopLanguage.ITALIAN -> It
                DesktopLanguage.SPANISH -> Es
            }

        private val PtBr =
            SetupServiceStrings(
                optionalTitle = "Serviços (opcional)",
                alsoHave = "Também tenho %s",
                alsoHaveHint = "Ao terminar, o %s abre para você entrar com a sua conta. Não é obrigatório: a lista IPTV funciona sem ele.",
            )

        private val En =
            SetupServiceStrings(
                optionalTitle = "Services (optional)",
                alsoHave = "I also have %s",
                alsoHaveHint = "When you finish, %s opens so you can sign in with your account. Not required: the IPTV list works without it.",
            )

        private val De =
            SetupServiceStrings(
                optionalTitle = "Dienste (optional)",
                alsoHave = "Ich habe auch %s",
                alsoHaveHint = "Danach öffnet sich %s, damit du dich mit deinem Konto anmeldest. Nicht nötig: Die IPTV-Liste funktioniert auch ohne.",
            )

        private val It =
            SetupServiceStrings(
                optionalTitle = "Servizi (facoltativo)",
                alsoHave = "Ho anche %s",
                alsoHaveHint = "Alla fine si apre %s per accedere con il tuo account. Non è obbligatorio: la lista IPTV funziona anche senza.",
            )

        private val Es =
            SetupServiceStrings(
                optionalTitle = "Servicios (opcional)",
                alsoHave = "También tengo %s",
                alsoHaveHint = "Al terminar, se abre %s para que entres con tu cuenta. No es obligatorio: la lista IPTV funciona sin él.",
            )
    }
}

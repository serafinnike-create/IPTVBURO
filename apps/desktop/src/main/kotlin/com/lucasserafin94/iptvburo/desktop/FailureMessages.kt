package com.lucasserafin94.iptvburo.desktop

import com.lucasserafin94.iptvburo.xtream.XtreamClientException
import com.lucasserafin94.iptvburo.xtream.XtreamFailureReason

/**
 * Turns a failure into something safe to show, attributing it to the provider only when it is
 * theirs.
 *
 * Extracted from `DesktopAppState` to be testable. That is not tidying for its own sake: this
 * mapping has now misled twice on real installations. Once when every non-Xtream throwable fell
 * into "the server did not return a compatible Xtream catalogue", and again when a missing session
 * produced the same sentence over a screen showing 32,466 of the user's own titles. Both times the
 * message sent the diagnosis to the customer's provider, which was working the whole time.
 *
 * Two rules hold everywhere here:
 *
 * - the provider is blamed only for an [XtreamClientException], never for anything else;
 * - the exception's own `message` is never shown. OkHttp puts the full request URL into its
 *   IOException text, and an Xtream URL carries the subscriber's username and password.
 */
internal object FailureMessages {
    /** Marker the repository raises when the catalogue is present but the credentials are not. */
    const val NO_SESSION_MARKER = "No Xtream session is active"

    fun forFailure(
        error: Throwable,
        logLocation: String,
    ): String =
        when (error) {
            is XtreamClientException -> forXtream(error, logLocation)

            // The catalogue survived on disk and the session did not.
            //
            // Reported by a user who installed a new build over an old one: the titles were all
            // there, and every action that needed the provider failed as though their list were
            // malformed. Nothing had been asked of the provider at all.
            is IllegalArgumentException, is IllegalStateException ->
                if (error.message?.contains(NO_SESSION_MARKER) == true) {
                    "A sessão da sua lista expirou. O catálogo continua salvo, mas é preciso " +
                        "entrar de novo na fonte para carregar novidades."
                } else {
                    appFault(error, logLocation)
                }

            is OutOfMemoryError ->
                "Não houve memória suficiente para montar esta tela. " +
                    "Isso é uma limitação do aplicativo, não da sua lista."

            else -> appFault(error, logLocation)
        }

    private fun forXtream(
        error: XtreamClientException,
        logLocation: String,
    ): String =
        when (error.reason) {
            XtreamFailureReason.INVALID_SERVER -> "O endereço do servidor não é válido."
            XtreamFailureReason.AUTHENTICATION -> "O servidor recusou o usuário ou a senha."
            XtreamFailureReason.NETWORK -> "Não foi possível alcançar o servidor."
            XtreamFailureReason.HTTP -> "O servidor respondeu com um erro HTTP."
            XtreamFailureReason.RESPONSE_TOO_LARGE -> "O catálogo excedeu o limite seguro desta prévia."
            // Names the log: this is the one reason the user can do nothing about unaided, and
            // telling a genuinely odd provider from a fault in this app needs the file.
            XtreamFailureReason.INVALID_RESPONSE ->
                "O servidor não retornou um catálogo Xtream compatível. Detalhes em $logLocation"
        }

    /** Names the type so a screenshot is worth something, and never the message. */
    private fun appFault(
        error: Throwable,
        logLocation: String,
    ): String =
        "Não foi possível montar esta tela (${error::class.simpleName}). " +
            "Isso é uma falha do aplicativo, não da sua lista. Detalhes em $logLocation"
}

package com.lucasserafin94.iptvburo.ui.cast

import com.lucasserafin94.iptvburo.domain.model.CastMessage
import com.lucasserafin94.iptvburo.domain.model.ContentIdentity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What the cast sheet is doing, and what it should show. */
sealed interface CastUiState {
    /** Not open. */
    data object Idle : CastUiState

    data object Searching : CastUiState

    /**
     * Screens found. Empty is a real answer and needs its own wording — many home routers separate
     * wifi from ethernet and drop the broadcast, so "none found" is not the same as "none exist".
     */
    data class Found(val targets: List<CastTarget>) : CastUiState

    /** A screen was chosen and is waiting for the code shown on it. */
    data class NeedsCode(val target: CastTarget, val badCode: Boolean = false) : CastUiState

    data class Sending(val target: CastTarget) : CastUiState

    /**
     * Delivered.
     *
     * Says "sent", never "playing". The receiver answers a wrong code with silence, so the phone
     * genuinely cannot tell a mistyped code from a screen that stopped listening — and claiming
     * playback started would be a guess presented as fact.
     */
    data class Sent(val target: CastTarget) : CastUiState

    /** The bytes did not reach the screen: it went away, or the network refused the connection. */
    data class Failed(val target: CastTarget) : CastUiState
}

/**
 * Drives sending a title to a screen on the same network.
 *
 * Deliberately free of Android UI types so it can be tested without a device. The blocking socket
 * work is pushed to [io]; the caller supplies the scope, so where the waiting happens stays visible
 * at the call site rather than hidden in here.
 *
 * The pairing code is held only for as long as the sheet is open. It is a proof of being in the
 * same room rather than a password, and the receiver regenerates it every session, so storing it
 * would mean remembering something that stops being true.
 */
class CastController(
    private val sender: CastTransport,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    var state: CastUiState = CastUiState.Idle
        private set

    /**
     * The screens the last search found.
     *
     * Kept beside the state so going back to the list does not need another search. Discovery takes
     * over a second, and repeating it because somebody tapped the wrong row would make correcting a
     * mistake slower than making it.
     */
    private var lastFound: List<CastTarget> = emptyList()

    /** Looks for screens. Called when the sheet opens and when the user asks again. */
    suspend fun search() {
        state = CastUiState.Searching
        val targets = withContext(io) { sender.discover() }
        lastFound = targets
        state = CastUiState.Found(targets)
    }

    fun choose(target: CastTarget) {
        state = CastUiState.NeedsCode(target)
    }

    /** Back to the list, so a wrong choice does not need the sheet closed and reopened. */
    fun back() {
        state = CastUiState.Found(lastFound)
    }

    fun close() {
        state = CastUiState.Idle
        lastFound = emptyList()
    }

    /**
     * Sends [identity] to the chosen screen.
     *
     * A malformed code is refused here rather than sent: four digits is the whole contract, and a
     * message the receiver will silently drop is worse than one never sent, because the sender is
     * told nothing either way.
     */
    suspend fun send(
        code: String,
        identity: ContentIdentity,
        title: String,
        positionMillis: Long,
    ) {
        val target = (state as? CastUiState.NeedsCode)?.target ?: return
        if (!CastMessage.isWellFormedPairingCode(code)) {
            state = CastUiState.NeedsCode(target, badCode = true)
            return
        }

        state = CastUiState.Sending(target)
        val message =
            CastMessage(
                identity = identity,
                title = title,
                positionMillis = positionMillis.coerceAtLeast(0L),
                pairingCode = code,
            )
        val delivered = withContext(io) { sender.send(target, message) }
        state = if (delivered) CastUiState.Sent(target) else CastUiState.Failed(target)
    }
}

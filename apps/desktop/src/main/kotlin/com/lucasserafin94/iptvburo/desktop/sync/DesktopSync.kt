package com.lucasserafin94.iptvburo.desktop.sync

import com.lucasserafin94.iptvburo.desktop.license.DeviceFingerprint
import com.lucasserafin94.iptvburo.desktop.license.DeviceIdentityProvider
import com.lucasserafin94.iptvburo.desktop.license.DeviceProofAction
import com.lucasserafin94.iptvburo.desktop.license.LicenseEndpoints
import com.lucasserafin94.iptvburo.sync.FavoriteSync
import com.lucasserafin94.iptvburo.sync.FavoriteSyncSession
import com.lucasserafin94.iptvburo.sync.SyncClient
import com.lucasserafin94.iptvburo.sync.SyncFailure
import com.lucasserafin94.iptvburo.sync.SyncInvite
import com.lucasserafin94.iptvburo.sync.SyncProver
import com.lucasserafin94.iptvburo.sync.SyncResult
import com.lucasserafin94.iptvburo.sync.SyncStateStore
import com.lucasserafin94.iptvburo.sync.SyncStatus
import java.util.concurrent.TimeUnit
import java.util.prefs.Preferences
import okhttp3.OkHttpClient

/**
 * Keeps this computer's favourites in step with the household's other devices.
 *
 * There is no account. The device's own key — the one the licence already pins — signs every call,
 * and devices find each other by a short code one shows and another types. What crosses the network
 * is the title's identity, name and year, for a favourite of a named profile; never a playlist, an
 * address or a credential.
 *
 * The rules are the shared [FavoriteSyncSession]'s; this supplies the computer's key and keeps the
 * ledger in preferences beside the favourites themselves. Titles are not secrets, and the ledger is
 * what makes a removal here reach the others instead of the favourite returning on the next
 * exchange.
 */
class DesktopSync(
    client: SyncClient = defaultClient(),
    preferences: Preferences = com.lucasserafin94.iptvburo.desktop.DesktopStorage.preferences(NODE),
    clock: () -> Long = System::currentTimeMillis,
) {
    private val session = FavoriteSyncSession(client, PreferencesSyncStore(preferences), clock)

    val isMember: Boolean get() = session.isMember

    fun status(): SyncResult<SyncStatus> = session.status()

    fun invite(): SyncResult<SyncInvite> = session.invite()

    fun join(code: String): SyncResult<Int> = session.join(code)

    fun leave(): SyncResult<Unit> = session.leave()

    fun exchange(
        current: Collection<FavoriteSync.Local>,
        localProfiles: Collection<String>,
    ): SyncResult<FavoriteSyncSession.Outcome> = session.exchange(current, localProfiles)

    internal fun loadLedger(): FavoriteSync.Ledger = session.loadLedger()

    companion object {
        private const val NODE = "com/lucasserafin94/iptvburo/sync-v1"

        fun defaultClient(identity: DeviceIdentityProvider = DeviceFingerprint): SyncClient =
            SyncClient(
                http =
                    OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(20, TimeUnit.SECONDS)
                        .build(),
                baseUrl = "https://${LicenseEndpoints.DOMAIN}",
                prover = DeviceSyncProver(identity),
            )
    }
}

private class PreferencesSyncStore(private val preferences: Preferences) : SyncStateStore {
    override var isMember: Boolean
        get() = preferences.getBoolean(KEY_MEMBER, false)
        set(value) {
            runCatching { preferences.putBoolean(KEY_MEMBER, value) }
        }

    override fun readLedger(): String? = preferences.get(KEY_LEDGER, null)

    override fun writeLedger(encoded: String) {
        preferences.put(KEY_LEDGER, encoded)
    }

    private companion object {
        const val KEY_LEDGER = "favorites-ledger"
        const val KEY_MEMBER = "member"
    }
}

/** Signs sync calls with the installation's key, for the `sync` action only. */
internal class DeviceSyncProver(
    private val identity: DeviceIdentityProvider,
) : SyncProver {
    override val deviceId: String
        get() = identity.getOrCreate().deviceId

    override fun proof(nonce: String): String = identity.getOrCreate().proof(DeviceProofAction.SYNC, nonce)
}

/** Why a sync call failed, for the screen to say in the viewer's language (see SyncStrings). */
enum class SyncProblem {
    LICENCE_REQUIRED,
    NOT_IN_GROUP,
    UNKNOWN_CODE,
    GROUP_FULL,
    NETWORK,
    REJECTED,
    ;

    companion object {
        fun of(failure: SyncFailure): SyncProblem =
            when (failure) {
                SyncFailure.LICENCE_REQUIRED -> LICENCE_REQUIRED
                SyncFailure.NOT_IN_GROUP -> NOT_IN_GROUP
                SyncFailure.UNKNOWN_CODE -> UNKNOWN_CODE
                SyncFailure.GROUP_FULL -> GROUP_FULL
                SyncFailure.NETWORK -> NETWORK
                SyncFailure.REJECTED -> REJECTED
            }
    }
}

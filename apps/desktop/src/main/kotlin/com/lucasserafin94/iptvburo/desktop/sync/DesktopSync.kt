package com.lucasserafin94.iptvburo.desktop.sync

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lucasserafin94.iptvburo.desktop.license.DeviceFingerprint
import com.lucasserafin94.iptvburo.desktop.license.DeviceIdentityProvider
import com.lucasserafin94.iptvburo.desktop.license.DeviceProofAction
import com.lucasserafin94.iptvburo.desktop.license.LicenseEndpoints
import com.lucasserafin94.iptvburo.sync.FavoriteSync
import com.lucasserafin94.iptvburo.sync.SyncClient
import com.lucasserafin94.iptvburo.sync.SyncFailure
import com.lucasserafin94.iptvburo.sync.SyncInvite
import com.lucasserafin94.iptvburo.sync.SyncProver
import com.lucasserafin94.iptvburo.sync.SyncResult
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
 * The ledger — what this device last agreed with the household — lives in preferences beside the
 * favourites themselves. Titles are not secrets, and the ledger is what makes a removal here reach
 * the others instead of the favourite returning on the next exchange.
 */
class DesktopSync(
    private val client: SyncClient = defaultClient(),
    private val preferences: Preferences = com.lucasserafin94.iptvburo.desktop.DesktopStorage.preferences(NODE),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Favourites to apply here after an exchange, per profile name. */
    data class Outcome(
        val add: List<FavoriteSync.Local>,
        val remove: List<FavoriteSync.Local>,
    )

    /**
     * Whether this device has joined a household, as far as it knows.
     *
     * Kept locally so nothing touches the network on a device that never asked for sync: the
     * background exchange runs only while this is true.
     */
    val isMember: Boolean
        get() = preferences.getBoolean(KEY_MEMBER, false)

    private fun setMember(member: Boolean) {
        runCatching { preferences.putBoolean(KEY_MEMBER, member) }
    }

    fun status(): SyncResult<SyncStatus> =
        client.status().also { result ->
            if (result is SyncResult.Success) setMember(result.value.inGroup)
        }

    /** A code for another device to type. Asking for one puts this device in a household. */
    fun invite(): SyncResult<SyncInvite> =
        client.invite().also { result ->
            if (result is SyncResult.Success) setMember(true)
        }

    /**
     * Joins the household [code] belongs to. The ledger starts over, so this device's favourites
     * are offered to the new household and the household's come down in full.
     */
    fun join(code: String): SyncResult<Int> =
        client.join(normaliseCode(code)).also { result ->
            if (result is SyncResult.Success) {
                saveLedger(FavoriteSync.Ledger())
                setMember(true)
            }
        }

    /** Leaves the household. Favourites stay on this device; only the link is cut. */
    fun leave(): SyncResult<Unit> =
        client.leave().also { result ->
            if (result is SyncResult.Success) {
                saveLedger(FavoriteSync.Ledger())
                setMember(false)
            }
        }

    /**
     * Pushes what changed here and pulls what changed elsewhere, page after page.
     *
     * The ledger is saved after every page, so a crash half-way leaves the device asking again only
     * for what it had not yet taken in.
     */
    fun exchange(
        current: Collection<FavoriteSync.Local>,
        localProfiles: Collection<String>,
    ): SyncResult<Outcome> {
        var ledger = loadLedger()
        val outgoing = FavoriteSync.localChanges(current, ledger, clock())
        val add = mutableListOf<FavoriteSync.Local>()
        val remove = mutableListOf<FavoriteSync.Local>()
        var pending = outgoing.records
        ledger = outgoing.ledger
        var pages = 0
        while (true) {
            val batch = pending.take(MAX_PUSH)
            pending = pending.drop(MAX_PUSH)
            val page =
                when (val result = client.exchange(ledger.revision, batch)) {
                    is SyncResult.Success -> result.value
                    is SyncResult.Failure -> {
                        if (result.reason == SyncFailure.NOT_IN_GROUP) setMember(false)
                        return result
                    }
                }
            val incoming = FavoriteSync.applyRemote(page.records, ledger, page.revision, localProfiles)
            add += incoming.add
            remove += incoming.remove
            ledger = incoming.ledger
            saveLedger(ledger)
            pages++
            if ((!page.hasMore && pending.isEmpty()) || pages >= MAX_PAGES) break
        }
        return SyncResult.Success(Outcome(add, remove))
    }

    internal fun loadLedger(): FavoriteSync.Ledger =
        runCatching {
            val raw = preferences.get(KEY_LEDGER, null) ?: return FavoriteSync.Ledger()
            val stored = gson.fromJson<StoredLedger>(raw, object : TypeToken<StoredLedger>() {}.type)
            FavoriteSync.Ledger(
                revision = stored.revision,
                entries =
                    stored.entries.associate { entry ->
                        entry.key to
                            FavoriteSync.LedgerEntry(
                                FavoriteSync.Local(entry.profile, entry.identity, entry.title, entry.year, entry.kind),
                                present = entry.present,
                                changedAtMillis = entry.changedAt,
                            )
                    },
            )
        }.getOrDefault(FavoriteSync.Ledger())

    private fun saveLedger(ledger: FavoriteSync.Ledger) {
        val stored =
            StoredLedger(
                revision = ledger.revision,
                entries =
                    ledger.entries.map { (key, entry) ->
                        StoredEntry(
                            key = key,
                            profile = entry.favorite.profile,
                            identity = entry.favorite.identityKey,
                            title = entry.favorite.title,
                            year = entry.favorite.year,
                            kind = entry.favorite.kind,
                            present = entry.present,
                            changedAt = entry.changedAtMillis,
                        )
                    },
            )
        runCatching { preferences.put(KEY_LEDGER, gson.toJson(stored)) }
    }

    private data class StoredLedger(val revision: Long = 0L, val entries: List<StoredEntry> = emptyList())

    private data class StoredEntry(
        val key: String,
        val profile: String,
        val identity: String,
        val title: String,
        val year: Int?,
        val kind: String,
        val present: Boolean,
        val changedAt: Long,
    )

    companion object {
        private const val NODE = "com/lucasserafin94/iptvburo/sync-v1"
        private const val KEY_LEDGER = "favorites-ledger"
        private const val KEY_MEMBER = "member"

        /** The server's limit per push. */
        private const val MAX_PUSH = 200

        /** A household's whole library many times over; stops a misbehaving server looping us. */
        private const val MAX_PAGES = 100

        private val gson = Gson()

        /** Codes are shown as ABCD-EFGH; people type them with spaces, lower case, or no dash. */
        fun normaliseCode(code: String): String = code.uppercase().filter { it.isLetterOrDigit() }

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

/** Signs sync calls with the installation's key, for the `sync` action only. */
internal class DeviceSyncProver(
    private val identity: DeviceIdentityProvider,
) : SyncProver {
    override val deviceId: String
        get() = identity.getOrCreate().deviceId

    override fun proof(nonce: String): String = identity.getOrCreate().proof(DeviceProofAction.SYNC, nonce)
}

/** The screen's words for a failure; the strings themselves live in [com.lucasserafin94.iptvburo.desktop.ui.SyncStrings]. */
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

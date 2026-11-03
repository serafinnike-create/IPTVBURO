package com.lucasserafin94.iptvburo.sync

import com.google.gson.Gson

/**
 * Where a device keeps what it needs between syncs: whether it joined a household, and the ledger
 * of what it last agreed with it. Each platform backs this with its own preferences.
 */
interface SyncStateStore {
    var isMember: Boolean

    /** The ledger as [FavoriteSyncSession] wrote it, or null when there is none yet. */
    fun readLedger(): String?

    fun writeLedger(encoded: String)
}

/**
 * One device's side of favourite sync: pairing, and the push-and-pull exchange.
 *
 * Shared by every platform so they cannot drift apart on the rules — when a device counts as a
 * member, when the ledger starts over, how pages are walked. A platform supplies the client (which
 * signs with its own key) and the store, reads its favourites into [FavoriteSync.Local], and applies
 * the [Outcome].
 */
class FavoriteSyncSession(
    private val client: SyncClient,
    private val store: SyncStateStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Favourites to apply on this device after an exchange. */
    data class Outcome(
        val add: List<FavoriteSync.Local>,
        val remove: List<FavoriteSync.Local>,
    )

    /**
     * Whether this device has joined a household, as far as it knows.
     *
     * Kept locally so nothing touches the network on a device that never asked for sync: the
     * platforms run their background exchange only while this is true.
     */
    val isMember: Boolean
        get() = store.isMember

    fun status(): SyncResult<SyncStatus> =
        client.status().also { result ->
            if (result is SyncResult.Success) store.isMember = result.value.inGroup
        }

    /** A code for another device to type. Asking for one puts this device in a household. */
    fun invite(): SyncResult<SyncInvite> =
        client.invite().also { result ->
            if (result is SyncResult.Success) store.isMember = true
        }

    /**
     * Joins the household [code] belongs to. The ledger starts over, so this device's favourites
     * are offered to the new household and the household's come down in full.
     */
    fun join(code: String): SyncResult<Int> =
        client.join(normaliseCode(code)).also { result ->
            if (result is SyncResult.Success) {
                saveLedger(FavoriteSync.Ledger())
                store.isMember = true
            }
        }

    /** Leaves the household. Favourites stay on the device; only the link is cut. */
    fun leave(): SyncResult<Unit> =
        client.leave().also { result ->
            if (result is SyncResult.Success) {
                saveLedger(FavoriteSync.Ledger())
                store.isMember = false
            }
        }

    /**
     * Pushes what changed here and pulls what changed elsewhere, page after page.
     *
     * The ledger is saved after every page, so a crash half-way leaves the device asking again only
     * for what it had not yet taken in. Being told the device is no longer in a household ends its
     * membership, which stops the background exchange.
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
                        if (result.reason == SyncFailure.NOT_IN_GROUP) store.isMember = false
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

    fun loadLedger(): FavoriteSync.Ledger = LedgerCodec.decode(store.readLedger())

    private fun saveLedger(ledger: FavoriteSync.Ledger) {
        runCatching { store.writeLedger(LedgerCodec.encode(ledger)) }
    }

    companion object {
        /** The server's limit per push. */
        private const val MAX_PUSH = 200

        /** A household's whole library many times over; stops a misbehaving server looping us. */
        private const val MAX_PAGES = 100

        /** Codes are shown as ABCD-EFGH; people type them with spaces, lower case, or no dash. */
        fun normaliseCode(code: String): String = code.uppercase().filter { it.isLetterOrDigit() }
    }
}

/** The ledger to and from the JSON a store keeps. Anything unreadable decodes to an empty ledger. */
internal object LedgerCodec {
    private val gson = Gson()

    private data class StoredLedger(val revision: Long = 0L, val entries: List<StoredEntry>? = null)

    private data class StoredEntry(
        val key: String?,
        val profile: String?,
        val identity: String?,
        val title: String?,
        val year: Int?,
        val kind: String?,
        val present: Boolean,
        val changedAt: Long,
    )

    fun encode(ledger: FavoriteSync.Ledger): String =
        gson.toJson(
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
            ),
        )

    fun decode(raw: String?): FavoriteSync.Ledger {
        if (raw.isNullOrBlank()) return FavoriteSync.Ledger()
        return runCatching {
            val stored = gson.fromJson(raw, StoredLedger::class.java)
            FavoriteSync.Ledger(
                revision = stored.revision,
                entries =
                    stored.entries.orEmpty().mapNotNull { entry ->
                        val key = entry.key ?: return@mapNotNull null
                        val favorite =
                            FavoriteSync.Local(
                                profile = entry.profile ?: return@mapNotNull null,
                                identityKey = entry.identity ?: return@mapNotNull null,
                                title = entry.title.orEmpty(),
                                year = entry.year,
                                kind = entry.kind.orEmpty(),
                            )
                        key to FavoriteSync.LedgerEntry(favorite, entry.present, entry.changedAt)
                    }.toMap(),
            )
        }.getOrDefault(FavoriteSync.Ledger())
    }
}

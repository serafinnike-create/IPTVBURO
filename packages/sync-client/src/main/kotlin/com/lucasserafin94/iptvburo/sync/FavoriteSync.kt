package com.lucasserafin94.iptvburo.sync

/**
 * Favourites kept in step across a household's devices, worked out without touching any store.
 *
 * Each platform keeps its favourites its own way; what they share is the title's identity key, the
 * same one the apps already use to keep favourites across a change of playlist. This object turns
 * "what this device has now" and "what it had at the last sync" into records to push, and records
 * pulled from the others into favourites to add and remove. The platform only reads and writes its
 * own store; everything that could disagree between platforms is decided here, once.
 *
 * A removal is pushed as a tombstone carrying the same content as the favourite did, so a device
 * that never saw the favourite still knows which title the removal is about — and a favourite
 * removed on the phone does not come back from the television on the next exchange.
 */
object FavoriteSync {
    /** One favourite as a device holds it. [profile] is the profile's name as shown. */
    data class Local(
        val profile: String,
        val identityKey: String,
        val title: String,
        val year: Int?,
        val kind: String,
    ) {
        internal val recordKey: String
            get() = SyncEntry.recordKeyFor("fav", profile, identityKey)
    }

    /** What this device last agreed with the household, per record key. */
    data class LedgerEntry(
        val favorite: Local,
        val present: Boolean,
        val changedAtMillis: Long,
    )

    data class Ledger(
        /** The highest server revision this device has applied. */
        val revision: Long = 0L,
        val entries: Map<String, LedgerEntry> = emptyMap(),
    )

    data class Outgoing(val records: List<SyncRecord>, val ledger: Ledger)

    data class Incoming(
        val add: List<Local>,
        val remove: List<Local>,
        val ledger: Ledger,
    )

    /**
     * What changed here since the last sync, as records to push.
     *
     * A favourite present now and absent (or removed) in the ledger is an addition; one the ledger
     * holds as present and that is gone now is a removal. Both are stamped [nowMillis], which is
     * what lets the newer edit win on the server.
     */
    fun localChanges(
        current: Collection<Local>,
        ledger: Ledger,
        nowMillis: Long,
    ): Outgoing {
        val now = current.associateBy(Local::recordKey)
        val records = mutableListOf<SyncRecord>()
        val entries = ledger.entries.toMutableMap()
        now.forEach { (key, favorite) ->
            val known = entries[key]
            if (known == null || !known.present) {
                records += record(favorite, deleted = false, nowMillis)
                entries[key] = LedgerEntry(favorite, present = true, changedAtMillis = nowMillis)
            }
        }
        ledger.entries.forEach { (key, known) ->
            if (known.present && key !in now) {
                records += record(known.favorite, deleted = true, nowMillis)
                entries[key] = known.copy(present = false, changedAtMillis = nowMillis)
            }
        }
        return Outgoing(records, ledger.copy(entries = entries))
    }

    /**
     * Records from the other devices, as favourites to add and remove here.
     *
     * Only for profiles this device has ([localProfiles], compared without case), and only when the
     * record is newer than what the ledger already holds for it — an echo of this device's own push
     * changes nothing. Records that are not favourites, or that do not decode, are ignored.
     */
    fun applyRemote(
        records: List<SyncRecord>,
        ledger: Ledger,
        revision: Long,
        localProfiles: Collection<String>,
    ): Incoming {
        val profiles = localProfiles.associateBy(SyncEntry::normaliseProfile)
        val entries = ledger.entries.toMutableMap()
        val add = mutableListOf<Local>()
        val remove = mutableListOf<Local>()
        records.forEach { record ->
            val entry = SyncEntryCodec.decode(record.data) as? SyncEntry.Favorite ?: return@forEach
            val profile = profiles[SyncEntry.normaliseProfile(entry.profile)] ?: return@forEach
            val favorite = Local(profile, entry.identityKey, entry.title, entry.year, entry.kind)
            if (favorite.recordKey != record.key) return@forEach
            val known = entries[record.key]
            if (known != null && known.changedAtMillis >= record.updatedAtMillis) return@forEach
            val present = !record.deleted
            if (present && known?.present != true) add += favorite
            if (!present && known?.present == true) remove += favorite
            entries[record.key] = LedgerEntry(favorite, present, record.updatedAtMillis)
        }
        return Incoming(add, remove, Ledger(revision = maxOf(ledger.revision, revision), entries = entries))
    }

    private fun record(
        favorite: Local,
        deleted: Boolean,
        nowMillis: Long,
    ): SyncRecord {
        val entry =
            SyncEntry.Favorite(
                profile = favorite.profile,
                identityKey = favorite.identityKey,
                title = favorite.title,
                year = favorite.year,
                kind = favorite.kind,
                changedAtMillis = nowMillis,
            )
        return SyncRecord(
            key = favorite.recordKey,
            updatedAtMillis = nowMillis,
            deleted = deleted,
            data = SyncEntryCodec.encode(entry),
        )
    }
}

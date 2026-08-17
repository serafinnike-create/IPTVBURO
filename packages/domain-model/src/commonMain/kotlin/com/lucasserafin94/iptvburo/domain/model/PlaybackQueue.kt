package com.lucasserafin94.iptvburo.domain.model

/**
 * The kind of thing an entry in the queue is.
 *
 * GDD 8 §16 states three rules that only make sense if the queue knows what it is holding:
 * radio replaces the queue for the live session, a podcast may carry its own episode queue, and an
 * audiobook does not mix chapters with music by default. All three are decisions about *kinds*, so
 * the kind travels with the entry rather than being re-derived by every caller.
 *
 * [MUSIC] and [RADIO] are the two that exist today; the rest are declared now so that adding a
 * podcast source later is a data change rather than a reshaping of the queue.
 */
enum class QueueMediaKind {
    MUSIC,
    RADIO,
    PODCAST,
    AUDIOBOOK,
    ;

    /**
     * Whether an entry of this kind may sit in the same queue as an entry of [other].
     *
     * GDD 8 §16: "audiobook não mistura capítulos com músicas por padrão". Podcasts get the same
     * treatment because §16 gives them "fila própria de episódios" — a queue of their own, not a
     * share of the music one. Radio never coexists with anything: it is handled earlier, by
     * [PlaybackQueue.playNow] replacing the whole queue.
     */
    fun mixesWith(other: QueueMediaKind): Boolean =
        when {
            this == other -> true
            this == AUDIOBOOK || other == AUDIOBOOK -> false
            this == PODCAST || other == PODCAST -> false
            // MUSIC and RADIO are the only remaining pair, and radio never reaches this check
            // because it replaces the queue instead of joining it.
            else -> true
        }
}

/**
 * One item in the queue.
 *
 * **This type deliberately carries no stream URI, no token, no signed URL and no request header.**
 * GDD 8 §16: "a fila nunca guarda token ou URL resolvida; persistir identidades e resolver no
 * playback." The queue may be written to disk to restore the last session, and this repository is
 * public — a queue entry that carried a provider URL would put subscription credentials into a
 * user's application data and, sooner or later, into a bug report. [mediaId] is the library's own
 * identifier; whatever plays the entry looks the real media up at the moment of playback.
 *
 * [title] and [subtitle] are duplicated here only so the panel can render an entry whose track has
 * disappeared from the library (playlist replaced mid-session) without showing a blank row.
 */
data class QueueEntry(
    /** Library-local identity, e.g. `MusicTrack.id`. Never a URL. */
    val mediaId: String,
    val kind: QueueMediaKind,
    val title: String,
    val subtitle: String? = null,
    /**
     * Distinguishes two queue positions holding the same media.
     *
     * Queueing the same song twice is legitimate, and every operation below addresses entries by
     * position rather than by id, so this exists purely to give Compose a stable list key. Without
     * it a reorder of a queue containing duplicates animates the wrong rows.
     */
    val handle: Long = 0L,
) {
    init {
        require(mediaId.isNotBlank()) { "A queue entry needs a media identity." }
    }
}

/**
 * The playback queue for one profile's session.
 *
 * GDD 8 §16 scopes the queue "por perfil e por sessão", so this type holds no profile id itself:
 * the owner keys it by profile, and a profile switch drops the whole value. It is immutable — every
 * operation returns a new queue — which is what makes the ordering rules testable without a player,
 * a coroutine or a clock.
 *
 * [index] points at the entry currently playing. It is `-1` exactly when [entries] is empty; every
 * operation maintains that. Nothing here does I/O, resolves a URI or touches Compose.
 */
data class PlaybackQueue(
    val entries: List<QueueEntry> = emptyList(),
    val index: Int = -1,
) {
    init {
        if (entries.isEmpty()) {
            require(index == -1) { "An empty queue has no current entry." }
        } else {
            require(index in entries.indices) { "Queue index $index is outside 0..${entries.lastIndex}." }
        }
    }

    val isEmpty: Boolean
        get() = entries.isEmpty()

    val size: Int
        get() = entries.size

    /** The entry playing now, or null when the queue is empty. */
    val current: QueueEntry?
        get() = entries.getOrNull(index)

    /** What "play next" would start, without advancing. Null at the end of the queue. */
    val upNext: QueueEntry?
        get() = entries.getOrNull(index + 1)

    /**
     * The entries after the current one, in order — what the panel shows under "a seguir".
     *
     * Empty rather than throwing on an empty queue, because the panel renders before anything is
     * queued.
     */
    val upcoming: List<QueueEntry>
        get() = if (isEmpty) emptyList() else entries.drop(index + 1)

    /**
     * Starts [entry] immediately, replacing whatever was queued.
     *
     * Two GDD 8 §16 rules meet here:
     *
     * - "rádio substitui a fila por sessão ao vivo" — a station is a live session, not a list item,
     *   so starting one leaves a queue of exactly that station. Anything queued behind it would
     *   never play, since a station does not end.
     * - "audiobook não mistura capítulos com músicas" — starting an audiobook while music is queued
     *   is a change of context, not an insertion, so the music queue does not survive it either.
     *
     * For music starting a track from a shelf is likewise a fresh start; [playNow] with a
     * [contextEntries] list is how a caller starts a track *within* an album or artist.
     */
    fun playNow(entry: QueueEntry): PlaybackQueue = PlaybackQueue(listOf(entry), 0)

    /**
     * Starts [entries] as the whole queue, positioned at [startIndex].
     *
     * This is "play this artist starting from track four": the surrounding tracks become the queue
     * so that playback continues into them, which is the whole point of a queue existing.
     *
     * Returns an empty queue for an empty list rather than throwing, so a caller can pass a
     * filtered list without guarding first.
     */
    fun playNow(entries: List<QueueEntry>, startIndex: Int = 0): PlaybackQueue {
        if (entries.isEmpty()) return EMPTY
        // An impossible start index means the caller's list and its selection have drifted apart —
        // a filtered shelf re-rendering mid-click. Starting from the head is the honest recovery;
        // clamping to the last entry would silently play the wrong track and look deliberate.
        val start = if (startIndex in entries.indices) startIndex else 0
        // A radio station in a list is still a live session: keep only the station itself, and
        // discard whatever else the caller batched with it (GDD 8 §16).
        val chosen = entries[start]
        if (chosen.kind == QueueMediaKind.RADIO) return PlaybackQueue(listOf(chosen), 0)
        val compatible = entries.filter { it.kind.mixesWith(chosen.kind) }
        val adjusted = compatible.indexOfFirst { it === chosen }.let { if (it >= 0) it else 0 }
        return PlaybackQueue(compatible, adjusted)
    }

    /**
     * Inserts [entry] immediately after the current one.
     *
     * The naive implementation appends, which is a different feature: with eight tracks queued,
     * "tocar em seguida" must play the new track next, not ninth. Nothing about the current entry
     * or the playback position changes — this only reshapes what comes after it.
     *
     * On an empty queue there is nothing to follow, so the entry becomes the queue and starts.
     */
    fun playNext(entry: QueueEntry): PlaybackQueue {
        if (isEmpty) return playNow(entry)
        // Radio never joins a queue; asking to hear a station "next" is asking to hear it now, and
        // it would otherwise sit there as an entry that can never end (GDD 8 §16).
        if (entry.kind == QueueMediaKind.RADIO) return playNow(entry)
        val currentEntry = entries[index]
        if (!entry.kind.mixesWith(currentEntry.kind)) return playNow(entry)
        val next = entries.toMutableList().apply { add(index + 1, entry) }
        return PlaybackQueue(next, index)
    }

    /**
     * Appends [entry] to the end of the queue.
     *
     * The same kind guard as [playNext] applies: a chapter appended behind a playlist of songs
     * would eventually play in the middle of a listening session, which §16 forbids.
     */
    fun addToEnd(entry: QueueEntry): PlaybackQueue {
        if (isEmpty) return playNow(entry)
        if (entry.kind == QueueMediaKind.RADIO) return playNow(entry)
        if (!entry.kind.mixesWith(entries[index].kind)) return playNow(entry)
        return PlaybackQueue(entries + entry, index)
    }

    /** Appends several entries, preserving their order. */
    fun addAllToEnd(added: List<QueueEntry>): PlaybackQueue =
        added.fold(this) { queue, entry -> queue.addToEnd(entry) }

    /**
     * Moves the entry at [from] to [to], keeping the same entry playing.
     *
     * The index has to be recomputed rather than left alone: dragging a queued track above the
     * playing one shifts the playing one down by a position, and an untouched index would silently
     * make the panel highlight — and the "next" button advance from — the wrong row.
     *
     * Out-of-range arguments return the queue unchanged; a drag can end anywhere, and a list
     * reordering itself is not worth a crash.
     */
    fun reorder(from: Int, to: Int): PlaybackQueue {
        if (from !in entries.indices || to !in entries.indices || from == to) return this
        val moving = entries[from]
        val next = entries.toMutableList()
        next.removeAt(from)
        next.add(to, moving)
        val playing = entries[index]
        // Identity comparison, not equality: a queue may hold the same media twice and `indexOf`
        // would then find the first copy rather than the one that was playing.
        val newIndex = next.indexOfFirst { it === playing }
        return PlaybackQueue(next, newIndex)
    }

    /**
     * Removes the entry at [position].
     *
     * Three cases, and only the first is obvious:
     *
     * - removing something *before* the current entry shifts the current one down; the index must
     *   follow it or playback jumps to a different track on the next advance;
     * - removing something *after* it changes nothing;
     * - removing the entry that is *playing* leaves the index pointing at whatever slid into that
     *   slot — the next track — which is the intended behaviour. Callers detect this with
     *   [currentChangedBy] and restart playback. Removing the last entry empties the queue and the
     *   index returns to -1 rather than dangling at 0 over an empty list.
     */
    fun removeAt(position: Int): PlaybackQueue {
        if (position !in entries.indices) return this
        val next = entries.toMutableList().apply { removeAt(position) }
        if (next.isEmpty()) return EMPTY
        val newIndex =
            when {
                position < index -> index - 1
                // The removed entry was the current one. Staying put lands on the entry that took
                // its place; at the very end there is no such entry, so fall back to the new last.
                position == index -> index.coerceAtMost(next.lastIndex)
                else -> index
            }
        return PlaybackQueue(next, newIndex)
    }

    /** Removes the first entry matching [mediaId], if any. Convenience for row-level controls. */
    fun removeFirst(mediaId: String): PlaybackQueue {
        val position = entries.indexOfFirst { it.mediaId == mediaId }
        return if (position < 0) this else removeAt(position)
    }

    /** Empties the queue. GDD 8 §16 action "limpar". */
    fun clear(): PlaybackQueue = EMPTY

    /**
     * Advances to the next entry, or null when the queue has run out.
     *
     * Null rather than wrapping to the start: repeat is a playback mode that GDD 8 handles
     * elsewhere, and silently looping would make a finished queue indistinguishable from one still
     * playing.
     */
    fun advance(): PlaybackQueue? = if (index + 1 in entries.indices) copy(index = index + 1) else null

    /** Steps back one entry, or null at the start of the queue. */
    fun back(): PlaybackQueue? = if (index - 1 in entries.indices) copy(index = index - 1) else null

    /** Jumps to [position], for a double-click on a queued row. Out of range leaves the queue alone. */
    fun jumpTo(position: Int): PlaybackQueue =
        if (position in entries.indices) copy(index = position) else this

    /**
     * Whether the entry playing changed between this queue and [other].
     *
     * The caller needs this to decide whether a queue edit has to restart the player. Comparing the
     * queues themselves would be wrong: reordering the tail changes the queue while the same track
     * keeps playing, and interrupting it there would be a visible defect.
     */
    fun currentChangedBy(other: PlaybackQueue): Boolean = current != other.current

    /**
     * The queue's identities, in order, for saving it as a playlist (GDD 8 §16 "salvar como
     * playlist") or for restoring it next session.
     *
     * Identities only — this is what gets written down, and by §16 it must never contain a resolved
     * URL.
     */
    fun mediaIds(): List<String> = entries.map(QueueEntry::mediaId)

    companion object {
        val EMPTY: PlaybackQueue = PlaybackQueue()

        /**
         * Rebuilds a queue from a stored list of identities.
         *
         * [resolve] maps an identity back to a live entry; identities that no longer resolve — the
         * user replaced their playlist between sessions — are dropped rather than kept as unplayable
         * rows. If the entry that was playing is among the dropped ones, the queue restarts at the
         * position where it used to be, which is the nearest thing to where the user left off.
         */
        fun restore(
            mediaIds: List<String>,
            index: Int,
            resolve: (String) -> QueueEntry?,
        ): PlaybackQueue {
            val resolved = mutableListOf<QueueEntry>()
            // How many of the surviving entries sat at or before the stored index. That count is
            // where the current entry now lives, or where it would have lived had it survived.
            var survivorsUpToIndex = 0
            mediaIds.forEachIndexed { position, mediaId ->
                val entry = resolve(mediaId) ?: return@forEachIndexed
                if (position <= index) survivorsUpToIndex = resolved.size + 1
                resolved += entry
            }
            if (resolved.isEmpty()) return EMPTY
            // survivorsUpToIndex counts entries; the index of the last of them is one less. Zero
            // survivors before the old position means everything ahead of it was dropped, so the
            // queue simply restarts at its new head.
            val restoredIndex = (survivorsUpToIndex - 1).coerceIn(resolved.indices)
            return PlaybackQueue(resolved, restoredIndex)
        }
    }
}

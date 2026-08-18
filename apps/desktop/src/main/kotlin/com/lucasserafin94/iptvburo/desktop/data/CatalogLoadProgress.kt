package com.lucasserafin94.iptvburo.desktop.data

/**
 * How far a catalogue download has got, reported while it runs.
 *
 * The splash used to move in three fixed steps — 0.75, 0.88, 0.96 — and the jump from the first to
 * the second spanned the whole catalogue download, which on a real list is tens of seconds. The bar
 * sat at 80% for all of it, and a bar that does not move says "hung" rather than "working". This is
 * what lets it move.
 *
 * ## Why a count and not a percentage
 *
 * A provider's catalogue does not announce its size: the response is a JSON array read as a stream,
 * so the total is unknown until the last item arrives. Reporting a fraction would mean inventing a
 * denominator. The count is the honest figure — "12.480 títulos" is true where "30%" would be a
 * guess — and the screen decides how to present it.
 *
 * ## Why there is no byte count here
 *
 * A transfer rate measured at the socket would be the better number, and it is not available: the
 * request is performed inside `packages/xtream-client`, which is shared with Android, and OkHttp's
 * EventListener only reports a body's size once the body has finished — which is exactly too late to
 * animate anything. Rather than invent bytes from parsed items and print a rate that is really a
 * parse speed, the screen reports the rate in **items per second**, which is what is actually
 * being observed.
 */
data class CatalogLoadProgress(
    /** Items parsed so far. Monotonic within one load. */
    val items: Int,
    /** When this reading was taken, for deriving a rate. */
    val atMillis: Long,
)

/**
 * Receives progress while a catalogue loads.
 *
 * Called from the IO thread doing the reading, so implementations must be cheap and must not touch
 * Compose state directly.
 *
 * Throttled by the caller rather than by the receiver: emitting once per item over 41,698 items
 * would cost more than the work it describes.
 */
typealias CatalogLoadListener = (CatalogLoadProgress) -> Unit

/**
 * How often progress is worth reporting.
 *
 * Every 250 items is frequent enough that the bar moves several times a second on a fast link, and
 * rare enough that the reporting itself is not measurable against the parse.
 */
internal const val CATALOG_PROGRESS_ITEM_INTERVAL = 250

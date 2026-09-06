package com.lucasserafin94.iptvburo.desktop

/**
 * Routine diagnostics, silent unless asked for.
 *
 * The module's support channel is [DiagnosticLog]: everything printed reaches the log file a user
 * can send back. That is right for failures — a line that says *why* something broke is the whole
 * point of the file — but wrong for routine chatter: "loaded 8 shelves", "already cached,
 * skipping", a player state line every few seconds. Noise buries the failure lines and grows the
 * file a customer has to email.
 *
 * So the routine lines go through here instead. Off by default; on with
 * `-Diptvburo.debug=true` (or by flipping [enabled] from a diagnostics screen). Because the lines
 * still go through `println` when enabled, [DiagnosticLog] mirrors them to the file exactly as
 * before — the flag only decides whether they are said at all.
 *
 * The same writing rule binds these lines as every other: no URL, credential, token or provider
 * address. The message is a lambda so a disabled flag costs no string building.
 */
internal object DebugLog {
    @Volatile
    var enabled: Boolean = System.getProperty("iptvburo.debug") == "true"

    fun d(tag: String, message: () -> String) {
        if (enabled) println("[$tag] ${message()}")
    }
}

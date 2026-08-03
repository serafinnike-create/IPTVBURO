package com.lucasserafin94.iptvburo.stalker

/**
 * Normalises the many shapes users paste a MAC address in.
 *
 * Portals only accept the canonical colon-separated uppercase form. Providers hand MACs out over
 * WhatsApp and email in every other shape (`00-1A-79-...`, `001a79...`, with stray spaces), so
 * rejecting those would look like "wrong credentials" when the credentials are in fact right.
 */
object StalkerMacAddress {
    private val SEPARATORS = Regex("""[\s:\-.]""")
    private val HEX_12 = Regex("""^[0-9A-F]{12}$""")

    /**
     * Returns the canonical `AA:BB:CC:DD:EE:FF` form, or null when [raw] is not a MAC.
     */
    fun normalise(raw: String): String? {
        val stripped = raw.uppercase().replace(SEPARATORS, "")
        if (!HEX_12.matches(stripped)) return null
        return stripped.chunked(2).joinToString(":")
    }

    fun isValid(raw: String): Boolean = normalise(raw) != null

    /**
     * Masks a MAC for display and logs, keeping only the final octet.
     *
     * A MAC is the entire credential on a Stalker portal, so it must never be printed in full.
     */
    fun mask(raw: String): String {
        val normalised = normalise(raw) ?: return "<invalid>"
        return "**:**:**:**:**:" + normalised.takeLast(2)
    }
}

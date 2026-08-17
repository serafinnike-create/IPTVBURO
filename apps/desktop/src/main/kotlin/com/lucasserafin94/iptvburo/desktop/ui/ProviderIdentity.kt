package com.lucasserafin94.iptvburo.desktop.ui

import androidx.compose.ui.graphics.Color

/**
 * The streaming service a category belongs to, as far as the playlist can tell.
 *
 * Derived from the category name, because that is the only signal an Xtream playlist gives: a
 * provider files its titles under "Filmes | Netflix" and nothing in the protocol says so more
 * directly. That makes this a good guess rather than a fact, which is why an unrecognised name yields
 * null and the row simply appears under the genres instead.
 *
 * ## Why a monogram and a colour rather than the real logo
 *
 * The wordmarks belong to the services. Drawing "N" in Netflix red reads instantly as Netflix without
 * shipping their asset, and it keeps working when a provider invents a category this app has never
 * heard of. It is also the only version that can be drawn at any size without a file per service.
 *
 * Deliberately the same list, monograms and colours as the Android app's `ProviderIdentity`. Two
 * copies that drift would badge the same category differently on the two platforms, which is worse
 * than either choice on its own.
 */
data class ProviderIdentity(
    val monogram: String,
    val label: String,
    /** The service's own colour, used for the chip so it reads at a glance. */
    val colour: Color,
) {
    /**
     * Ink for [monogram] against [colour].
     *
     * Apple's badge is near-white, so white lettering on it is invisible; everything else here is a
     * saturated colour that white sits on cleanly.
     */
    val ink: Color
        get() = if (label == "Apple TV+") Color(0xFF111111) else Color.White
}

/**
 * Reads a service out of a category name, or null when none is recognisable.
 *
 * Case-insensitive and matched on substrings because providers are inconsistent: "SÉRIES - NETFLIX",
 * "Filmes | netflix 4K" and "VOD Netflix" all name the same service.
 */
fun providerIdentityFor(categoryName: String?): ProviderIdentity? {
    val normalized = categoryName?.lowercase()?.takeIf(String::isNotBlank) ?: return null
    return when {
        "netflix" in normalized -> ProviderIdentity("N", "Netflix", NetflixRed)
        "amazon" in normalized || "prime video" in normalized ->
            ProviderIdentity("AP", "Prime Video", PrimeBlue)
        "disney" in normalized -> ProviderIdentity("D+", "Disney+", DisneyBlue)
        "globoplay" in normalized -> ProviderIdentity("G", "Globoplay", GloboRed)
        "discovery" in normalized -> ProviderIdentity("D", "Discovery+", DiscoveryBlue)
        "apple tv" in normalized || "apple+" in normalized ->
            ProviderIdentity("", "Apple TV+", AppleGrey)
        "hbo" in normalized -> ProviderIdentity("HBO", "HBO", HboPurple)
        "paramount" in normalized -> ProviderIdentity("P+", "Paramount+", ParamountBlue)
        "star+" in normalized || "star plus" in normalized ->
            ProviderIdentity("S+", "Star+", StarBlue)
        "crunchyroll" in normalized -> ProviderIdentity("CR", "Crunchyroll", CrunchyrollOrange)
        MAX_PROVIDER.containsMatchIn(normalized) -> ProviderIdentity("M", "Max", MaxBlue)
        else -> null
    }
}

/**
 * "Max" as a whole word, compiled once.
 *
 * Bounded rather than a plain `in` test so a category called "Cinemax" or "Max Series" is not badged
 * as the streaming service. Compiling it once matters: this runs for every category on every
 * recomposition of a list whose whole job is to scroll smoothly.
 */
private val MAX_PROVIDER = Regex("(^|[ |\\-])max([ |\\-]|$)")

private val NetflixRed = Color(0xFFE50914)
private val PrimeBlue = Color(0xFF00A8E1)
private val DisneyBlue = Color(0xFF113CCF)
private val GloboRed = Color(0xFFE30613)
private val DiscoveryBlue = Color(0xFF0077C8)
private val AppleGrey = Color(0xFFE8E8ED)
private val HboPurple = Color(0xFF991EEB)
private val ParamountBlue = Color(0xFF0064FF)
private val StarBlue = Color(0xFF1D1D6E)
private val CrunchyrollOrange = Color(0xFFF47521)
private val MaxBlue = Color(0xFF0046FF)

package com.lucasserafin94.iptvburo.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
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
    /**
     * The service's actual mark, when TMDb has supplied one.
     *
     * TMDb publishes the providers' logos through `watch/providers` and licenses them for exactly
     * this use, with attribution — which is what makes showing a genuine Netflix or Prime mark
     * legitimate here, where drawing a look-alike would not be.
     *
     * The selector shipped with monograms only, and the reply was immediate: "AP" is not the Prime
     * Video logo, and a row of two-letter chips is not the "identificação fácil" the feature was
     * for. Null until the directory loads, or when a service TMDb does not carry appears in a
     * playlist, and then [monogram] on [colour] stands in.
     */
    val logoUrl: String? = null,
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
 * The identity for a label this app has already resolved, such as "Netflix" or "Prime Video".
 *
 * The service index keys on those labels rather than on the raw category names it was built from, so
 * turning one back into a mark needs the reverse lookup. Implemented through [providerIdentityFor] so
 * there is one list of services rather than two that could disagree.
 */
fun providerIdentityForLabel(label: String): ProviderIdentity? = providerIdentityFor(label)

/**
 * "Max" as a whole word, compiled once.
 *
 * Bounded rather than a plain `in` test so a category called "Cinemax" or "Max Series" is not badged
 * as the streaming service. Compiling it once matters: this runs for every category on every
 * recomposition of a list whose whole job is to scroll smoothly.
 */
private val MAX_PROVIDER = Regex("(^|[ |\\-])max([ |\\-]|$)")

/**
 * The services' official logos by [ProviderIdentity.label], readable by any composable.
 *
 * A CompositionLocal rather than a parameter because the alternative is threading one map through
 * every selector, row and card on the way to a badge — several layers of plumbing for a value that
 * is the same everywhere and changes once, when the directory loads.
 *
 * Empty by default, which is the honest starting state: every badge falls back to its monogram until
 * the real marks arrive.
 */
val LocalProviderLogos = staticCompositionLocalOf<Map<String, String>> { emptyMap() }

/**
 * The identity for [categoryName], carrying the official logo when one is known.
 *
 * The one call a composable should make: it applies the naming rules and the logo lookup together,
 * so a screen cannot accidentally draw a monogram while the real mark sits in the catalogue.
 */
@Composable
fun rememberProviderIdentity(categoryName: String?): ProviderIdentity? {
    val logos = LocalProviderLogos.current
    return remember(categoryName, logos) {
        providerIdentityFor(categoryName)?.let { identity ->
            identity.copy(logoUrl = logos[identity.label])
        }
    }
}

/** Attaches the official logos to identities resolved outside composition, such as in a split. */
fun ProviderIdentity.withLogoFrom(logos: Map<String, String>): ProviderIdentity =
    if (logoUrl != null) this else copy(logoUrl = logos[label])

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

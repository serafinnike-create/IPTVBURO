package com.lucasserafin94.iptvburo.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import androidx.compose.ui.graphics.Color

/**
 * The streaming service a title came from, as far as the catalogue can tell.
 *
 * Derived from the category name, because that is the only signal an Xtream playlist gives: a
 * provider files its titles under "Filmes | Netflix" and nothing in the protocol says so more
 * directly. That makes this a good guess rather than a fact, which is why an unrecognised name
 * yields null and the badge simply does not appear.
 *
 * ## Why a monogram and a colour rather than the real logo
 *
 * The wordmarks belong to the services. Drawing "N" in Netflix red reads instantly as Netflix
 * without shipping their asset, and it keeps working when a provider invents a category this app
 * has never heard of. It is also the only version that can be drawn at any size without a file
 * per service per density.
 */
data class ProviderIdentity(
    val monogram: String,
    val label: String,
    /** The service's own colour, used for the badge so it reads at a glance. */
    val colour: Color,
    /**
     * The service's actual mark, when TMDb has supplied one for this title.
     *
     * TMDb publishes the providers' logos through `watch/providers` and licenses them for exactly
     * this use, with attribution — which is what makes showing a real Netflix or Prime mark
     * legitimate here, where drawing a look-alike would not be. Null everywhere the lookup has not
     * run or found nothing, and then [monogram] on [colour] stands in.
     */
    val logoUrl: String? = null,
)

/**
 * Reads a service out of a category name, or null when none is recognisable.
 *
 * Lives here rather than beside the catalogue grid that first needed it: the details screen names
 * the same services, and two copies of this list would drift into disagreeing about what a title
 * belongs to.
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
        // Ordered before the plain "apple tv" test, which would otherwise swallow both: TMDb lists
        // the rental shop and the subscription service separately, and they are different products.
        "apple tv store" in normalized -> ProviderIdentity("", "Apple TV Store", AppleGrey)
        "apple tv" in normalized -> ProviderIdentity("", "Apple TV+", AppleGrey)
        "google play" in normalized -> ProviderIdentity("GP", "Google Play Movies", GooglePlayBlue)
        "looke" in normalized -> ProviderIdentity("L", "Looke", LookeRed)
        "plex" in normalized -> ProviderIdentity("P", "Plex", PlexAmber)
        "telecine" in normalized -> ProviderIdentity("T", "Telecine", TelecineRed)
        "hbo" in normalized -> ProviderIdentity("HBO", "HBO", HboPurple)
        "paramount" in normalized -> ProviderIdentity("P+", "Paramount+", ParamountBlue)
        "star+" in normalized || "star plus" in normalized ->
            ProviderIdentity("S+", "Star+", StarBlue)
        MAX_PROVIDER.containsMatchIn(normalized) -> ProviderIdentity("M", "Max", MaxBlue)
        else -> null
    }
}

/**
 * "Max" as a whole word, compiled once.
 *
 * Bounded rather than a plain `in` test so a category called "Cinemax" or "Max Series" is not
 * badged as the streaming service. Compiling it once matters: this runs for every category on
 * every recomposition of a list whose whole job is to scroll smoothly.
 */
private val MAX_PROVIDER = Regex("(^|[ |])max([ |]|\$)")

private val NetflixRed = Color(0xFFE50914)
private val PrimeBlue = Color(0xFF00A8E1)
private val DisneyBlue = Color(0xFF113CCF)
private val GloboRed = Color(0xFFE30613)
private val DiscoveryBlue = Color(0xFF0077C8)
private val AppleGrey = Color(0xFFE8E8ED)
private val HboPurple = Color(0xFF991EEB)
private val ParamountBlue = Color(0xFF0064FF)
private val StarBlue = Color(0xFF1D1D6E)
private val MaxBlue = Color(0xFF0046FF)
private val GooglePlayBlue = Color(0xFF1A73E8)
private val LookeRed = Color(0xFFD5001C)
private val PlexAmber = Color(0xFFE5A00D)
private val TelecineRed = Color(0xFFE11B22)

/**
 * Matches a TMDb provider name to one of the identities above, keeping its official logo.
 *
 * The names TMDb uses and the names a playlist uses are not the same string — "Amazon Prime Video"
 * against "Prime Video", "HBO Max" against "Max" — so the match runs through [providerIdentityFor],
 * which already knows every spelling this app has seen.
 */
fun ProviderIdentity.withLogoFrom(
    providerNames: List<Pair<String, String?>>,
): ProviderIdentity {
    val match =
        providerNames.firstOrNull { (name, _) ->
            providerIdentityFor(name)?.label == label
        } ?: return this
    return copy(logoUrl = match.second)
}

/**
 * The service's mark, drawn at [size].
 *
 * The real logo when the catalogue has one — TMDb distributes the providers' own marks and licenses
 * them for this, which is what makes a genuine Netflix or Prime badge legitimate — and the monogram
 * on the brand colour until it arrives or when it never does.
 *
 * One composable for every place a badge appears, so a card, a category tile and a details page
 * cannot drift into showing the same service three different ways.
 */
@Composable
fun ProviderMark(
    provider: ProviderIdentity,
    size: Dp,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = size / 4,
) {
    val logo = provider.logoUrl
    if (logo != null) {
        AsyncImage(
            model = logo,
            contentDescription = provider.label,
            modifier = modifier.size(size).clip(RoundedCornerShape(cornerRadius)),
            contentScale = ContentScale.Fit,
        )
        return
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(provider.colour),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = provider.monogram,
            // Against a saturated brand colour white reads; a pale badge needs dark ink instead
            // of vanishing into itself. Decided from the colour rather than from the service's
            // name, so a pale service added later cannot be missed off a hard-coded list.
            color = if (provider.colour.isPale()) Color(0xFF111111) else Color.White,
            fontSize = (size.value * 0.34f).sp,
            fontWeight = FontWeight.Black,
        )
    }
}

/**
 * The services' official logos, readable by any card without being passed down to it.
 *
 * A CompositionLocal rather than a parameter because the alternative is threading one map through
 * every card, row and rail on the way to the badge — five layers of plumbing for a value that is
 * the same everywhere and changes once, when the directory loads.
 *
 * Empty by default, which is the honest starting state: every badge falls back to its monogram
 * until the real marks arrive.
 */
val LocalProviderLogos = staticCompositionLocalOf<Map<String, String>> { emptyMap() }

/**
 * The identity for [categoryName], carrying the official logo when one is known.
 *
 * The one call a card should make: it applies the naming rules and the logo lookup together, so a
 * screen cannot accidentally draw a monogram while the real mark sits in the catalogue.
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

/**
 * Whether ink on this colour has to be dark to be read.
 *
 * Relative luminance rather than a list of service names: Apple's near-white mark was the only pale
 * one when this was written, and the next pale service to be added would otherwise have printed
 * white on white until somebody noticed.
 */
private fun Color.isPale(): Boolean = (0.299f * red + 0.587f * green + 0.114f * blue) > 0.6f

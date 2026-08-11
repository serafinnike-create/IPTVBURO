package com.lucasserafin94.iptvburo.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import com.lucasserafin94.iptvburo.domain.model.ExternalContentLauncher
import com.lucasserafin94.iptvburo.domain.model.ExternalLaunchTarget
import com.lucasserafin94.iptvburo.domain.model.LaunchDecision

/**
 * Hands a subscription offer to the service that owns it.
 *
 * The decision of *whether* an address may be opened is [ExternalContentLauncher]'s, in the shared
 * domain: it refuses raw streams, credential-bearing URLs and odd schemes. This function only
 * performs the resulting intent, so Android and Windows cannot end up applying different rules to
 * the same offer.
 *
 * Nothing is played here and nothing is opened inside BURO. A refused or missing target does
 * nothing at all — silently, because the row is a signpost and a failed signpost is not an error the
 * user needs to dismiss.
 */
internal fun openStreamingOffer(
    activity: Activity,
    offer: SubscriptionOfferUi,
) {
    // The user's own library has no external destination by design: it is content they already
    // have, and it is opened in-app rather than sent to anyone else.
    if (offer.isUserLibrary) return
    if (offer.webUrl.isNullOrBlank() && offer.appDeepLink.isNullOrBlank()) return

    val target = ExternalLaunchTarget(webUrl = offer.webUrl, appDeepLink = offer.appDeepLink)
    val uri =
        when (val decision = ExternalContentLauncher.decide(target)) {
            is LaunchDecision.OpenApp -> decision.uri
            is LaunchDecision.OpenWeb -> decision.url
            is LaunchDecision.Unavailable -> return
        }

    // NEW_TASK so the service's app or the browser owns its own back stack: without it, Back from
    // the provider would land inside BURO's task rather than where the user came from.
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { activity.startActivity(intent) }
        .onFailure { error ->
            // A device with no browser and no provider app installed is the ordinary case here,
            // not a fault worth reporting. The URI is deliberately not logged: it is a destination
            // built from the user's own viewing interest.
            if (error !is ActivityNotFoundException) throw error
        }
}

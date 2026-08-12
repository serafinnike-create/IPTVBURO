package com.lucasserafin94.iptvburo.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import com.lucasserafin94.iptvburo.R
import com.lucasserafin94.iptvburo.domain.model.ContentIdentity
import com.lucasserafin94.iptvburo.domain.model.ContentKind
import com.lucasserafin94.iptvburo.domain.model.TitleShareLink

/**
 * Hands a title to the system share sheet, so it can be sent through WhatsApp or anywhere else.
 *
 * What travels is decided by [TitleShareLink], in the shared domain: the title, its year, a public
 * poster and a capped synopsis — never the provider's address or a stream URL. This function only
 * performs the intent, exactly as [openStreamingOffer] does for outbound offers, so the two
 * platforms cannot end up applying different rules to the same content.
 *
 * The poster is sent as a URL inside the message text rather than as an attached image. Attaching it
 * would mean downloading the file and exposing it through a `FileProvider`, and messaging apps
 * already unfurl a link into a preview card with the artwork — the same result without the app
 * granting any other application read access to its cache.
 */
internal fun shareTitle(
    activity: Activity,
    kind: ContentKind,
    title: String,
    year: Int?,
    artworkUrl: String?,
    description: String?,
) {
    val link =
        TitleShareLink.of(
            identity = ContentIdentity.of(kind, title, year),
            title = title,
            year = year,
            artworkUrl = artworkUrl,
            description = description,
        ) ?: return

    // Subject is used by mail clients and ignored by messaging apps, which read the text alone —
    // so the text has to stand on its own, and repeats the title rather than relying on it.
    val heading = link.year?.let { "${link.title} ($it)" } ?: link.title
    val message =
        buildString {
            append(heading)
            link.description?.let { synopsis ->
                append("\n\n").append(synopsis)
            }
            append("\n\n").append(link.webUrl())
            // Named so the recipient understands what the link is before tapping it. Without this
            // the message is a bare URL to a domain nobody recognises, which reads as spam.
            append("\n").append(activity.getString(R.string.share_sent_with))
        }

    val send =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, heading)
            putExtra(Intent.EXTRA_TEXT, message)
        }

    val chooser =
        Intent.createChooser(send, activity.getString(R.string.share_chooser_title))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    runCatching { activity.startActivity(chooser) }
        .onFailure { error ->
            // A television with no share target installed is the ordinary case here, not a fault
            // worth reporting. Nothing is logged: the message names what the user is watching.
            if (error !is ActivityNotFoundException) throw error
        }
}

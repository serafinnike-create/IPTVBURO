package com.lucasserafin94.iptvburo.desktop.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lucasserafin94.iptvburo.desktop.ui.BuroColors
import com.lucasserafin94.iptvburo.desktop.ui.BuroRadius
import com.lucasserafin94.iptvburo.desktop.ui.BuroRemoteArtwork
import com.lucasserafin94.iptvburo.desktop.ui.BuroSpacing
import com.lucasserafin94.iptvburo.desktop.ui.strings
import com.lucasserafin94.iptvburo.domain.model.TitleShareLink

/**
 * Sends a title to somebody, without sending anything about the sender's provider.
 *
 * The share is a *recommendation*, not a stream: [TitleShareLink] carries a normalised title, a
 * year and a public poster, and the recipient's own app resolves that against their own playlist.
 * The dialog shows exactly what will be sent — poster, title, synopsis, link — so that promise is
 * visible rather than asserted. The link on screen is the literal string that gets shared.
 *
 * Every destination is reached by handing a URL to the system browser, which is the only mechanism
 * available here: there is no Windows share sheet reachable from the JVM, and WhatsApp's own
 * `wa.me` endpoint is designed for exactly this. The link is copied to the clipboard at the same
 * time, so a service this dialog does not list is still one paste away.
 */
@Composable
fun ShareTitleDialog(
    link: TitleShareLink,
    onDismiss: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val text = strings
    val backdropInteraction = remember { MutableInteractionSource() }
    val webUrl = remember(link) { link.webUrl() }

    // The message body, built once: the same text goes to every destination and to the clipboard,
    // so what the user previews is what each service receives.
    val message =
        remember(link, webUrl) {
            buildString {
                append(link.title)
                link.year?.let { append(" ($it)") }
                link.description?.let {
                    append("\n\n")
                    append(it)
                }
                append("\n\n")
                append(webUrl)
            }
        }

    var copied by remember { mutableStateOf(false) }
    // The confirmation is a transient acknowledgement, not a state the dialog stays in.
    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(2_000)
            copied = false
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(BuroColors.Canvas.copy(alpha = 0.86f))
                // Hoisted for the same reason as the other dialogs: DismissAreaRippleTest reads the
                // argument list, and an inline interaction source hides the `indication = null`.
                .clickable(
                    indication = null,
                    interactionSource = backdropInteraction,
                    onClick = onDismiss,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth(0.9f)
                    .clip(BuroRadius.Medium)
                    .background(BuroColors.Surface)
                    .border(1.dp, BuroColors.BorderSoft, BuroRadius.Medium)
                    .clickable(enabled = false) {}
                    .padding(BuroSpacing.Lg),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = text.shareStrings.shareTitle,
                        color = BuroColors.Text,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = text.shareStrings.shareSubtitle,
                        color = BuroColors.TextSubtle,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text("✕", color = BuroColors.TextMuted, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(BuroSpacing.Md))

            // A preview of the card the recipient will see, so nothing about the share is a
            // surprise once it has already been sent.
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(BuroRadius.Small)
                        .background(BuroColors.SurfaceRaised)
                        .padding(BuroSpacing.Md),
                verticalAlignment = Alignment.Top,
            ) {
                if (link.artworkUrl != null) {
                    BuroRemoteArtwork(
                        artworkUrl = link.artworkUrl,
                        contentDescription = link.title,
                        modifier =
                            Modifier
                                .width(72.dp)
                                .aspectRatio(2f / 3f)
                                .clip(BuroRadius.Small)
                                .background(BuroColors.Surface),
                        contentScale = ContentScale.Crop,
                    ) {}
                    Spacer(Modifier.width(BuroSpacing.Md))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = link.year?.let { "${link.title} ($it)" } ?: link.title,
                        color = BuroColors.Text,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    link.description?.let { description ->
                        Spacer(Modifier.height(BuroSpacing.Xs))
                        Text(
                            text = description,
                            color = BuroColors.TextMuted,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(BuroSpacing.Xs))
                    Text(
                        text = webUrl,
                        color = BuroColors.Accent,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.height(BuroSpacing.Md))
            Text(
                text = text.shareStrings.shareDestination,
                color = BuroColors.TextSubtle,
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.height(BuroSpacing.Sm))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Sm),
                verticalArrangement = Arrangement.spacedBy(BuroSpacing.Xs),
            ) {
                ShareDestination("WhatsApp") {
                    // Copied as well as opened. wa.me prefills the message, but only after the user
                    // has picked a chat; if they dismiss that picker the text is still on the
                    // clipboard rather than lost.
                    copyToClipboard(message)
                    copied = true
                    onOpenUrl("https://wa.me/?text=${encodeForUrl(message)}")
                }
                ShareDestination("Telegram") {
                    copyToClipboard(message)
                    copied = true
                    onOpenUrl(
                        "https://t.me/share/url?url=${encodeForUrl(webUrl)}" +
                            "&text=${encodeForUrl(message.removeSuffix(webUrl).trim())}",
                    )
                }
                ShareDestination("X") {
                    copyToClipboard(message)
                    copied = true
                    onOpenUrl("https://twitter.com/intent/tweet?text=${encodeForUrl(message)}")
                }
                ShareDestination(text.shareStrings.shareByEmail) {
                    copyToClipboard(message)
                    copied = true
                    // A subject and body, so the mail client opens on a message that is ready to
                    // send rather than on an empty draft holding a URL.
                    onOpenUrl(
                        "mailto:?subject=${encodeForUrl(link.title)}&body=${encodeForUrl(message)}",
                    )
                }
                ShareDestination(if (copied) text.shareStrings.shareCopied else text.shareStrings.shareCopyLink) {
                    copyToClipboard(message)
                    copied = true
                }
            }

            Spacer(Modifier.height(BuroSpacing.Md))
            // States the boundary in the product itself, not only in the code: the person sharing
            // should know their subscription is not travelling with the message.
            Text(
                text = text.shareStrings.shareNoCredentials,
                color = BuroColors.TextSubtle,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ShareDestination(
    label: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.height(44.dp),
        shape = BuroRadius.Small,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = BuroColors.Text),
    ) {
        Text(label, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Percent-encodes text for use in a query value.
 *
 * `URLEncoder` is deliberately not used: it emits `+` for a space, which WhatsApp and the mail
 * clients render literally, so a shared synopsis arrived full of plus signs.
 */
private fun encodeForUrl(value: String): String =
    buildString {
        value.toByteArray(Charsets.UTF_8).forEach { byte ->
            val char = byte.toInt().toChar()
            if (char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' || char in "-_.~") {
                append(char)
            } else {
                append('%')
                append(HEX[(byte.toInt() shr 4) and 0xF])
                append(HEX[byte.toInt() and 0xF])
            }
        }
    }

private const val HEX = "0123456789ABCDEF"

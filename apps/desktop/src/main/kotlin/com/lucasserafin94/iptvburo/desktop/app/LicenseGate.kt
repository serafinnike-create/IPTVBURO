package com.lucasserafin94.iptvburo.desktop.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucasserafin94.iptvburo.desktop.license.KeyInfo
import com.lucasserafin94.iptvburo.desktop.license.KeyState
import com.lucasserafin94.iptvburo.desktop.license.LicenseClient
import com.lucasserafin94.iptvburo.desktop.license.LicenseEndpoints
import com.lucasserafin94.iptvburo.desktop.license.LicenseStatus
import com.lucasserafin94.iptvburo.desktop.license.PriceQuote
import com.lucasserafin94.iptvburo.desktop.license.QrCode
import com.lucasserafin94.iptvburo.desktop.ui.BuroColors
import com.lucasserafin94.iptvburo.desktop.ui.BuroMark
import com.lucasserafin94.iptvburo.desktop.ui.BuroSpacing
import com.lucasserafin94.iptvburo.desktop.ui.LicenseStrings
import com.lucasserafin94.iptvburo.desktop.ui.strings
import com.lucasserafin94.iptvburo.domain.model.LicenseBlockReason
import java.awt.Desktop
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The screen shown instead of the app when it may not run.
 *
 * ## What it has to accomplish
 *
 * Somebody sees this because their trial ended, or because their connection is down. Those two
 * people need opposite things, and confusing them is expensive in both directions: asking for money
 * when the problem is the wifi loses a sale and earns a complaint, while showing a "retry" button to
 * someone whose trial expired leaves them clicking it for ever.
 *
 * So the screen has two shapes. A blocked-and-must-pay shape with a price, a QR code and a way to
 * activate; and a something-went-wrong shape with an explanation and a retry, which never mentions
 * money.
 *
 * ## The identifier is the most important thing here
 *
 * Everything else can be recovered from. The device code is what a customer reads out when they
 * write for support, and it is what a manual grant is keyed on — so it is large, monospaced,
 * selectable, and next to a copy button.
 */
@Composable
fun LicenseGate(
    status: LicenseStatus,
    client: LicenseClient,
    onRechecked: (LicenseStatus) -> Unit,
    /** Called with a key that was accepted, so the app can show it back to its owner later. */
    onKeyRedeemed: (String) -> Unit = {},
    /** The key already redeemed by this installation, shown back so it can be kept. */
    activationKey: String? = null,
    onQuit: () -> Unit,
    languageTag: String,
    /** Posters remembered from the last catalogue load; decorative and never interactive. */
    backdropPosters: List<String> = emptyList(),
    /**
     * Loads the public price once. Injectable so layout tests never depend on production network
     * timing; the default keeps the installed application's behaviour unchanged.
     */
    priceLoader: suspend () -> PriceQuote? = {
        withContext(Dispatchers.IO) { client.price() }
    },
    /**
     * The poster wall never affects foreground measurement. Tests may disable it to avoid an
     * infinite decorative animation keeping the Compose test clock busy.
     */
    renderBackdrop: Boolean = true,
    /**
     * How to go back, or null when there is nowhere to go back to.
     *
     * The same screen serves two situations. Opened from the countdown while a trial is running, it
     * is a page somebody chose to look at and must be able to leave — trapping them there is a bug,
     * and a bad one, because the app behind it works perfectly.
     *
     * Shown *because* the licence has lapsed, there is nothing behind it: closing would reveal an
     * app the customer is not entitled to use. Null in that case, and the only ways out are paying,
     * redeeming a key, or quitting.
     */
    onDismiss: (() -> Unit)? = null,
) {
    val text = strings.licenseText
    val reason = status.blockReason ?: LicenseBlockReason.NOT_ACTIVATED

    // Whether this is a payment problem or a connection problem. It decides the entire layout, so it
    // is worked out once here rather than being asked repeatedly further down.
    val needsPayment = reason in
        setOf(
            LicenseBlockReason.TRIAL_ENDED,
            LicenseBlockReason.EXPIRED,
            LicenseBlockReason.REVOKED,
            LicenseBlockReason.NOT_ACTIVATED,
        )

    var busy by remember { mutableStateOf(false) }
    var keyInput by remember { mutableStateOf("") }
    // What the server says the typed key is, or null while unknown.
    //
    // Null covers "not asked yet", "still asking" and "could not ask" alike, and all three show
    // nothing: a description that failed to load must never look like a verdict on the key.
    var keyInfo by remember { mutableStateOf<KeyInfo?>(null) }
    var keyFailed by remember { mutableStateOf(false) }
    var enteringKey by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    var keyCopied by remember { mutableStateOf(false) }

    // A failed redemption stays on the code view so the error remains beside the field that caused
    // it. The state lives here because the route into that view is fixed outside the scrolling
    // purchase content.
    LaunchedEffect(keyFailed) {
        if (keyFailed) enteringKey = true
    }

    // The price, from the server rather than from this machine's locale.
    //
    // Fetched once when the screen opens. Until it answers the price is simply absent, which is the
    // right failure: a number that changes when the customer clicks through to pay costs more than
    // a moment with no number at all.
    var quote by remember { mutableStateOf<PriceQuote?>(null) }
    LaunchedEffect(Unit) {
        quote = priceLoader()
    }

    // Describes the key shortly after typing stops.
    //
    // Keyed on the input, so each edit cancels the previous lookup — asking on every keystroke
    // would send a request per character and describe codes the user was still halfway through.
    // The wait is what makes it one request per pause rather than per letter.
    LaunchedEffect(keyInput) {
        keyInfo = null
        val candidate = keyInput.trim()
        // Short entries are still being typed. Asking about them would mostly return "unknown",
        // which reads as a verdict on a code that is not finished.
        if (candidate.length < 6) return@LaunchedEffect
        kotlinx.coroutines.delay(600)
        keyInfo = withContext(Dispatchers.IO) { client.keyInfo(candidate) }
    }

    // The confirmation fades by itself. A permanent "Copied" beside the code would still be there
    // the next time the customer looked, saying nothing true.
    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(1_800)
            copied = false
        }
    }
    LaunchedEffect(keyCopied) {
        if (keyCopied) {
            kotlinx.coroutines.delay(1_800)
            keyCopied = false
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(BuroColors.Canvas),
        contentAlignment = Alignment.Center,
    ) {
        // The lock screen is still part of the entertainment product, not a billing form pasted on
        // a black window. The same slow wall used during startup keeps visual continuity while its
        // own scrim and this panel preserve the contrast of the price, device code and QR plate.
        if (renderBackdrop) {
            SplashPosterWall(posters = backdropPosters)
        }

        // Two layers, and the split is what makes this work at all.
        //
        // The outer box is bounded and painted; the inner column scrolls inside it. Putting
        // `verticalScroll` and a height on one modifier chain does not do this — the scroll measures
        // its content at unbounded height and the panel grows past the window regardless, which is
        // why the activation field stayed unreachable after the QR code was already shrunk.
        Column(
            modifier = Modifier
                .padding(BuroSpacing.Md)
                .widthIn(max = 600.dp)
                .fillMaxHeight(0.94f)
                .clip(RoundedCornerShape(24.dp))
                .background(BuroColors.Canvas.copy(alpha = 0.86f))
                .border(1.dp, BuroColors.BorderSoft, RoundedCornerShape(24.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
        Column(
            modifier = Modifier
                // Takes the space the outer column has left, and no more: `weight` is what bounds
                // the scrollable area against a parent whose height is already decided.
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = BuroSpacing.Xl, vertical = BuroSpacing.Md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Tightened throughout. Every element here was sized for a comfortable screen, and
            // together they made a column taller than a laptop window — which put the activation
            // route, the one thing somebody may have arrived needing, out of reach entirely.
            BuroMark(size = 40.dp)
            Spacer(Modifier.height(BuroSpacing.Sm))

            Text(
                text = titleFor(reason, text),
                color = BuroColors.Text,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(BuroSpacing.Xxs))
            Text(
                text = bodyFor(reason, text),
                color = BuroColors.TextMuted,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(BuroSpacing.Md))

            DeviceIdentity(
                deviceId = status.deviceId,
                copied = copied,
                text = text,
                onCopy = {
                    copyToClipboard(status.deviceId)
                    copied = true
                },
            )

            // The key that paid for this installation, once there is one.
            //
            // Shown beside the device code because the two answer different questions: the device
            // code is what support asks for, the key is what the customer owns. Nothing in the app
            // displayed the key at all, so the only copy was wherever they pasted it after buying —
            // and losing it means buying another, since it binds to this device.
            activationKey?.takeIf(String::isNotBlank)?.let { key ->
                Spacer(Modifier.height(BuroSpacing.Sm))
                ActivationKeyRow(
                    key = key,
                    copied = keyCopied,
                    text = text,
                    onCopy = {
                        copyToClipboard(key)
                        keyCopied = true
                    },
                )
            }

            if (status.clockSuspect) {
                Spacer(Modifier.height(BuroSpacing.Sm))
                Text(
                    text = text.clockWarning,
                    color = BuroColors.Warning,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(BuroSpacing.Md))

            if (needsPayment) {
                PaymentSection(
                    deviceId = status.deviceId,
                    languageTag = languageTag,
                    text = text,
                    quote = quote,
                    enteringKey = enteringKey,
                    busy = busy,
                    keyInput = keyInput,
                    keyFailed = keyFailed,
                    keyInfo = keyInfo,
                    onKeyChange = {
                        keyInput = it.uppercase()
                        keyFailed = false
                    },
                    onRedeem = {
                        busy = true
                        keyFailed = false
                    },
                    onEnteringKeyChange = { enteringKey = it },
                )
            } else {
                Button(
                    onClick = { busy = true },
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BuroColors.Primary,
                        contentColor = BuroColors.OnPrimary,
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = BuroColors.OnPrimary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(text.retry, fontWeight = FontWeight.Bold)
                    }
                }
            }

        }

        // Outside the scroll, so it is always on screen.
        //
        // The activation-code route is the primary escape from the purchase flow for somebody who
        // already paid or received a key. Its position is independent of QR size, translated font
        // metrics and whether the live price has finished loading.
        if (needsPayment && !enteringKey) {
            ActivationCodeButton(
                text = text,
                onClick = { enteringKey = true },
                modifier = Modifier.padding(horizontal = BuroSpacing.Xl),
            )
            Spacer(Modifier.height(BuroSpacing.Xs))
        }

        // Going back when there is a working app to go back to, quitting otherwise. Trapping
        // somebody here while their trial still has days left is alarming: the app behind works, and
        // the only apparent way out is to close the program.
        // Why two years and not for ever, answered before anybody asks — it is the objection that
        // arrives at the moment of paying. Down here rather than above the buttons: reassurance for
        // whoever reads it, never something between a customer and the action.
        if (needsPayment) {
            Text(
                text = text.whyNotLifetime,
                color = BuroColors.TextSubtle,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = BuroSpacing.Xl),
            )
        }

        TextButton(
            onClick = onDismiss ?: onQuit,
            modifier = Modifier.padding(bottom = BuroSpacing.Sm),
        ) {
            Text(
                text = if (onDismiss != null) text.back else text.quit,
                color = BuroColors.TextSubtle,
                fontSize = 13.sp,
            )
        }
        }
    }

    // Both the retry button and the redeem button set `busy`; this performs whichever one is
    // pending. Off the main thread, because it makes a network call and freezing the window while
    // somebody waits for a server is the thing that makes an app feel broken.
    LaunchedEffect(busy) {
        if (!busy) return@LaunchedEffect

        val result = withContext(Dispatchers.IO) {
            if (keyInput.isNotBlank()) client.redeem(keyInput) else client.check()
        }

        busy = false
        if (result == null) {
            keyFailed = true
        } else {
            keyFailed = !result.allowsUse && keyInput.isNotBlank()
            // Remembered only when the key actually worked.
            //
            // Losing the key costs money — it is bound to this device and the alternative to
            // finding it again is buying another — so the app keeps it and shows it back in
            // Options. Storing a rejected key would show the customer a code that does nothing.
            if (result.allowsUse && keyInput.isNotBlank()) onKeyRedeemed(keyInput.trim())
            onRechecked(result)
        }
    }
}

/**
 * The device code, and a way to copy it.
 *
 * The one value that matters on this screen. It is what a customer reads out to support and what a
 * manual grant is keyed on, so it is large, monospaced and next to a copy button — a fourteen
 * character code transcribed by hand is a support ticket waiting to happen.
 *
 * The machine's MAC address is deliberately absent. The installation identity is a key held by this
 * install, not a network adapter, and showing a MAC here would invite a customer to quote a value
 * that identifies nothing the server knows about.
 */
@Composable
private fun DeviceIdentity(
    deviceId: String,
    copied: Boolean,
    text: LicenseStrings,
    onCopy: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BuroColors.Surface)
            .border(1.dp, BuroColors.BorderSoft, RoundedCornerShape(14.dp))
            .padding(BuroSpacing.Sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text.deviceLabel, color = BuroColors.TextSubtle, fontSize = 11.sp)
        Spacer(Modifier.height(BuroSpacing.Xxs))
        Text(
            text = deviceId,
            color = BuroColors.Primary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.height(BuroSpacing.Xxs))
        TextButton(onClick = onCopy) {
            Text(
                text = if (copied) text.copied else "⧉",
                color = if (copied) BuroColors.Success else BuroColors.TextMuted,
                fontSize = 12.sp,
            )
        }
    }
}

/**
 * The activation key this installation redeemed.
 *
 * Quieter than the device code above it, on purpose: this is a value to keep, not one to read out
 * to support. It is smaller and not gold, so the screen still has one obvious focus.
 *
 * Shown in full rather than masked. Hiding most of it would protect nothing — it is bound to this
 * device and useless anywhere else — while defeating the reason it is here, which is that the
 * customer can write it down before they need it.
 */
@Composable
private fun ActivationKeyRow(
    key: String,
    copied: Boolean,
    text: LicenseStrings,
    onCopy: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BuroColors.Surface)
            .border(1.dp, BuroColors.BorderSoft, RoundedCornerShape(14.dp))
            .padding(BuroSpacing.Sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text.activationKeyLabel, color = BuroColors.TextSubtle, fontSize = 11.sp)
        Spacer(Modifier.height(BuroSpacing.Xxs))
        Text(
            text = key,
            color = BuroColors.Text,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.height(BuroSpacing.Xxs))
        Text(
            text = text.activationKeyHint,
            color = BuroColors.TextMuted,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onCopy) {
            Text(
                text = if (copied) text.copied else "⧉",
                color = if (copied) BuroColors.Success else BuroColors.TextMuted,
                fontSize = 12.sp,
            )
        }
    }
}

/**
 * Buying, or entering a code — one at a time.
 *
 * These were stacked, and the result was a column taller than a laptop window: headline, device
 * panel, price, QR plate, button, divider, field. The field went off the bottom, and no amount of
 * shrinking the parts fixed it, because there was simply more content than height.
 *
 * They are alternatives, not steps. Somebody either scans a code to pay or types a code they were
 * given, never both, so only one is on screen and a link swaps between them. That removes the height
 * problem rather than managing it, and it also makes the choice visible: a person holding a code can
 * see the app accepts one without scrolling to find out.
 */
@Composable
private fun PaymentSection(
    deviceId: String,
    languageTag: String,
    text: LicenseStrings,
    /** From the server. Null while it is being fetched, or if it could not be. */
    quote: PriceQuote?,
    enteringKey: Boolean,
    busy: Boolean,
    keyInput: String,
    keyFailed: Boolean,
    /** What the server says the typed key is. Null shows nothing. */
    keyInfo: KeyInfo? = null,
    onKeyChange: (String) -> Unit,
    onRedeem: () -> Unit,
    onEnteringKeyChange: (Boolean) -> Unit,
) {
    val purchaseUrl = remember(deviceId, languageTag) {
        LicenseEndpoints.purchaseUrl(deviceId, languageTag)
    }

    if (enteringKey) {
        KeyEntry(
            text = text,
            keyInput = keyInput,
            keyFailed = keyFailed,
            keyInfo = keyInfo,
            busy = busy,
            onKeyChange = onKeyChange,
            onRedeem = onRedeem,
            onBack = { onEnteringKeyChange(false) },
        )
        return
    }

    // Only when the server has answered.
    //
    // The app used to derive this from the operating system's locale and could disagree with the
    // page the button leads to — R$99,90 here and €9,90 there. Showing nothing for a moment is
    // better than showing a number that changes when the customer clicks it, which is the point at
    // which they stop believing the rest of the screen.
    if (quote != null) {
        Text(
            text = "${quote.label} · ${termLabel(quote.termDays, text)}",
            color = BuroColors.Text,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(BuroSpacing.Md))
    }

    // Smaller than it was. At 196dp the QR plate pushed everything below it off a laptop screen,
    // including the activation field — the one thing on here somebody may have arrived needing. A
    // code of this size still scans from arm's length, which is the distance it is used at.
    QrPanel(content = purchaseUrl)
    Spacer(Modifier.height(BuroSpacing.Xs))
    Text(text.scanHint, color = BuroColors.TextSubtle, fontSize = 12.sp)

    Spacer(Modifier.height(BuroSpacing.Md))

    Button(
        onClick = { openInBrowser(purchaseUrl) },
        colors = ButtonDefaults.buttonColors(
            containerColor = BuroColors.Primary,
            contentColor = BuroColors.OnPrimary,
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) {
        Text(text.openInBrowser, fontWeight = FontWeight.Bold)
    }

}

/**
 * Fixed route to code activation.
 *
 * Price text and font metrics vary by platform and language, while this action must not. It sits
 * outside the scroll so a customer holding a code can always reach it without discovering a hidden
 * scrollbar below the QR plate.
 */
@Composable
private fun ActivationCodeButton(
    text: LicenseStrings,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.fillMaxWidth().height(44.dp),
    ) {
        Text(
            text = text.haveKey,
            color = BuroColors.Primary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * Entering a code that was handed out.
 *
 * Its own view rather than a section under the QR plate. Stacked, it was the last thing in a column
 * taller than the window and could not be reached at all; alone, the field is the first thing the
 * eye lands on — which is right, because somebody who came here to type a code has already decided.
 */
@Composable
private fun KeyEntry(
    text: LicenseStrings,
    keyInput: String,
    keyFailed: Boolean,
    keyInfo: KeyInfo? = null,
    busy: Boolean,
    onKeyChange: (String) -> Unit,
    onRedeem: () -> Unit,
    onBack: () -> Unit,
) {
    Text(
        text = text.haveKey,
        color = BuroColors.Text,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(BuroSpacing.Md))

    OutlinedTextField(
        value = keyInput,
        onValueChange = onKeyChange,
        placeholder = { Text(text.keyPlaceholder, color = BuroColors.TextSubtle) },
        singleLine = true,
        isError = keyFailed,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (keyInput.isNotBlank()) onRedeem() }),
    )

    if (keyFailed) {
        Spacer(Modifier.height(BuroSpacing.Xxs))
        Text(text.redeemFailed, color = BuroColors.Error, fontSize = 12.sp)
    }

    // What the key is, once the server has said.
    //
    // Shown only when there is an answer and the redemption has not already failed: a description
    // beside a red error would be two verdicts about the same code, and the failure is the one that
    // just happened.
    if (!keyFailed) {
        keyInfo?.let { info ->
            Spacer(Modifier.height(BuroSpacing.Xxs))
            val days = info.grantDays
            Text(
                text =
                    when (info.state) {
                        // The days matter here: "30 dias" is the difference between a key worth
                        // using now and one worth keeping for later.
                        KeyState.AVAILABLE ->
                            if (days != null) text.keyAvailableDays(days) else text.keyAvailable
                        KeyState.YOURS -> text.keyYours
                        KeyState.IN_USE -> text.keyInUse
                        KeyState.EXPIRED -> text.keyExpired
                    },
                color =
                    when (info.state) {
                        KeyState.AVAILABLE, KeyState.YOURS -> BuroColors.Success
                        // Not an error colour: a key belonging to another machine is a fact about
                        // the key, not a mistake the person just made.
                        KeyState.IN_USE, KeyState.EXPIRED -> BuroColors.TextSubtle
                    },
                fontSize = 12.sp,
            )
        }
    }

    Spacer(Modifier.height(BuroSpacing.Md))

    Button(
        onClick = onRedeem,
        enabled = keyInput.isNotBlank() && !busy,
        colors = ButtonDefaults.buttonColors(
            containerColor = BuroColors.Primary,
            contentColor = BuroColors.OnPrimary,
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = BuroColors.OnPrimary,
                strokeWidth = 2.dp,
            )
        } else {
            Text(text.redeem, fontWeight = FontWeight.Bold)
        }
    }

    Spacer(Modifier.height(BuroSpacing.Xs))
    TextButton(onClick = onBack) {
        Text(text.backToPurchase, color = BuroColors.TextMuted, fontSize = 13.sp)
    }
}

/**
 * The QR code, drawn as squares.
 *
 * On a white plate with a wide margin, both of which a reader needs: the quiet zone is what lets a
 * camera find the code's edges, and dark-on-light is what every scanner expects. A code drawn in the
 * app's own dark palette looks better and scans worse, which is the wrong trade for the one element
 * whose entire purpose is being photographed.
 */
@Composable
private fun QrPanel(content: String) {
    val matrix = remember(content) { runCatching { QrCode.encode(content) }.getOrNull() }
    if (matrix == null) return

    // 152dp rather than 196: a version-3 code at this size is about 4dp per module, which a phone
    // reads comfortably from arm's length — the distance somebody actually holds it at. The larger
    // plate scanned no better and pushed the activation field off the bottom of a laptop screen.
    val plate = 152.dp

    Box(
        modifier = Modifier
            .size(plate)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(plate)) {
            val quiet = 4
            val modules = matrix.size + quiet * 2
            val module = size.minDimension / modules

            matrix.forEachIndexed { row, columns ->
                columns.forEachIndexed { column, dark ->
                    if (!dark) return@forEachIndexed
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(
                            x = (column + quiet) * module,
                            y = (row + quiet) * module,
                        ),
                        // A hair over one module, so neighbouring squares meet rather than leaving
                        // hairlines that a camera reads as light.
                        size = Size(module + 0.6f, module + 0.6f),
                    )
                }
            }
        }
    }
}

/**
 * The term beside the price, from the server's own number of days.
 *
 * Derived rather than written into the price string, so changing what is sold changes one constant
 * on the server instead of eight translations that would otherwise quietly disagree with it.
 */
private fun termLabel(days: Int, text: LicenseStrings): String =
    if (days >= 365) text.termYears.format(days / 365) else "$days"

private fun titleFor(reason: LicenseBlockReason, text: LicenseStrings): String =
    when (reason) {
        LicenseBlockReason.TRIAL_ENDED, LicenseBlockReason.NOT_ACTIVATED -> text.trialTitle
        LicenseBlockReason.EXPIRED -> text.expiredTitle
        LicenseBlockReason.REVOKED -> text.revokedTitle
        LicenseBlockReason.NEEDS_VERIFICATION -> text.verifyTitle
        LicenseBlockReason.UNREACHABLE -> text.unreachableTitle
    }

private fun bodyFor(reason: LicenseBlockReason, text: LicenseStrings): String =
    when (reason) {
        LicenseBlockReason.TRIAL_ENDED, LicenseBlockReason.NOT_ACTIVATED -> text.trialBody
        LicenseBlockReason.EXPIRED -> text.expiredBody
        LicenseBlockReason.REVOKED -> text.revokedBody
        LicenseBlockReason.NEEDS_VERIFICATION -> text.verifyBody
        LicenseBlockReason.UNREACHABLE -> text.unreachableBody
    }

/**
 * Opens the purchase page in the customer's browser.
 *
 * Failure is silent by design: the QR code is right there, and an error dialog about a browser would
 * be one more obstacle between somebody and paying.
 */
private fun openInBrowser(url: String) {
    runCatching {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(url))
        }
    }
}

/**
 * Puts [value] on the system clipboard, ignoring a clipboard the OS refuses to hand over.
 *
 * Internal rather than private: the share dialog copies the same way, and two implementations of
 * "put this text on the clipboard" would be two places to fix when one of them swallows the wrong
 * exception.
 */
internal fun copyToClipboard(value: String) {
    runCatching {
        java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(
            java.awt.datatransfer.StringSelection(value),
            null,
        )
    }
}

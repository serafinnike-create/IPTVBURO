package com.lucasserafin94.iptvburo.desktop.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lucasserafin94.iptvburo.desktop.DesktopAppState
import com.lucasserafin94.iptvburo.desktop.platform.ExternalOpenResult
import com.lucasserafin94.iptvburo.desktop.playback.WebServiceBrowser
import com.lucasserafin94.iptvburo.desktop.ui.BuroColors
import com.lucasserafin94.iptvburo.desktop.ui.BuroRadius
import com.lucasserafin94.iptvburo.desktop.ui.BuroSpacing
import com.lucasserafin94.iptvburo.desktop.ui.WebServiceStrings
import com.lucasserafin94.iptvburo.domain.model.WebServiceBrand
import com.lucasserafin94.iptvburo.domain.model.WebServices

/**
 * The service's own page, over the whole window, in the app's embedded browser.
 *
 * Drawn only while [DesktopAppState.openedWebService] is set, which the state sets only for a
 * service this profile may see and only where the engine exists. Closing saves the sign-in for this
 * profile (see WebServiceBrowser.dispose).
 */
@Composable
fun WebServiceOverlay(appState: DesktopAppState) {
    val brand = appState.openedWebService ?: return
    val profileId = appState.activeProfileId ?: return
    val text = WebServiceStrings.of(appState.language)
    val browser = remember(brand.id, profileId) { WebServiceBrowser(brand, profileId) }
    DisposableEffect(browser) { onDispose { browser.dispose() } }
    val panel = remember(browser) { browser.createComponent() }

    com.lucasserafin94.iptvburo.desktop.ui.EscapeToDismiss(appState::closeWebService)

    Column(
        modifier = Modifier.fillMaxSize().background(BuroColors.Canvas),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = BuroSpacing.Lg, vertical = BuroSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Sm),
        ) {
            OutlinedButton(onClick = appState::closeWebService, shape = BuroRadius.Small) {
                Text("← ${text.close}", color = BuroColors.Text)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = brand.displayName,
                    color = BuroColors.Text,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = text.format(text.historyNote, brand.displayName),
                    color = BuroColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = browser::signOut) {
                Text(text.signOut, color = BuroColors.TextMuted)
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth().background(Color.Black)) {
            if (panel == null) {
                Text(
                    text = text.format(text.openFailed, brand.displayName),
                    color = BuroColors.Text,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                com.lucasserafin94.iptvburo.desktop.ui.NativeSwingPanel(
                    factory = { panel },
                    modifier = Modifier.fillMaxSize(),
                    background = Color.Black,
                )
            }
        }
    }
}

/**
 * The services on Home, one card each. Nothing for a profile that has none — Kids included.
 *
 * [onExternalResult] receives what happened when the page had to go to the system browser, so the
 * screen can say so when even that failed.
 */
@Composable
fun WebServiceHomeCards(
    appState: DesktopAppState,
    onExternalResult: (ExternalOpenResult) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val services = appState.visibleWebServices
    if (services.isEmpty()) return
    val text = WebServiceStrings.of(appState.language)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(BuroSpacing.Xs)) {
        Text(
            text = text.homeTitle,
            color = BuroColors.Text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Sm)) {
            services.forEach { brand ->
                Column(
                    modifier =
                        Modifier
                            .width(300.dp)
                            .background(BuroColors.SurfaceRaised, BuroRadius.Medium)
                            .border(1.dp, BuroColors.BorderSoft, BuroRadius.Medium)
                            .clickable { appState.openWebService(brand.id)?.let(onExternalResult) }
                            .padding(BuroSpacing.Md),
                ) {
                    Text(
                        text = brand.displayName,
                        color = BuroColors.Text,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(text = text.tagline, color = BuroColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(BuroSpacing.Xs))
                    Text(
                        text = text.format(text.open, brand.displayName),
                        color = BuroColors.Primary,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

/**
 * The switches in Settings → Content, one per service. Not drawn for a Kids profile.
 *
 * Turning an age-restricted service on asks the 18+ question first; turning it off does not ask.
 */
@Composable
fun WebServiceSettingsSection(appState: DesktopAppState) {
    if (!appState.canUseWebServices) return
    val text = WebServiceStrings.of(appState.language)
    var confirming by remember { mutableStateOf<WebServiceBrand?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(BuroSpacing.Xs)) {
        Text(
            text = text.sectionTitle,
            color = BuroColors.Text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(text = text.sectionHint, color = BuroColors.TextMuted, style = MaterialTheme.typography.bodySmall)
        WebServices.all.forEach { brand ->
            val enabled = brand.id in appState.webServiceIds
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(brand.displayName, color = BuroColors.Text, fontWeight = FontWeight.Bold)
                    Text(text.tagline, color = BuroColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                }
                androidx.compose.material3.Switch(
                    checked = enabled,
                    onCheckedChange = { on ->
                        if (on && brand.asksAgeConfirmation) {
                            confirming = brand
                        } else {
                            appState.setWebServiceEnabled(brand.id, on)
                        }
                    },
                )
            }
        }
    }

    confirming?.let { brand ->
        WebServiceAgeDialog(
            text = text,
            brand = brand,
            onConfirm = {
                confirming = null
                appState.setWebServiceEnabled(brand.id, true)
            },
            onDismiss = { confirming = null },
        )
    }
}

/**
 * The GoDorama page of the sidebar: what the service is, and one button that does the next thing.
 *
 * Not turned on yet: "Ativar GoDorama", which asks the 18+ question and then opens the service
 * straight away — the person pressed it to watch, not to flip a setting. Turned on: "Abrir
 * GoDorama", with "Desativar" beside it.
 */
@Composable
fun WebServicesPage(
    appState: DesktopAppState,
    onExternalResult: (ExternalOpenResult) -> Unit = {},
) {
    val text = WebServiceStrings.of(appState.language)
    var confirming by remember { mutableStateOf<WebServiceBrand?>(null) }
    fun open(brand: WebServiceBrand) {
        appState.openWebService(brand.id)?.let(onExternalResult)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(BuroSpacing.Xl),
        verticalArrangement = Arrangement.spacedBy(BuroSpacing.Lg),
    ) {
        WebServices.all.forEach { brand ->
            val enabled = brand.id in appState.webServiceIds
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(BuroColors.SurfaceRaised, BuroRadius.Large)
                        .border(1.dp, BuroColors.BorderSoft, BuroRadius.Large)
                        .padding(BuroSpacing.Xl),
                verticalArrangement = Arrangement.spacedBy(BuroSpacing.Sm),
            ) {
                Text(
                    text = brand.displayName,
                    color = BuroColors.Text,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(text = text.tagline, color = BuroColors.TextMuted, style = MaterialTheme.typography.titleMedium)
                Text(text = text.sectionHint, color = BuroColors.TextMuted, style = MaterialTheme.typography.bodyMedium)
                if (enabled) {
                    Text(
                        text = text.format(text.historyNote, brand.displayName),
                        color = BuroColors.TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(BuroSpacing.Xs))
                Row(horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Sm)) {
                    Button(
                        onClick = {
                            when {
                                enabled -> open(brand)
                                brand.asksAgeConfirmation -> confirming = brand
                                else -> {
                                    appState.setWebServiceEnabled(brand.id, true)
                                    open(brand)
                                }
                            }
                        },
                        modifier = Modifier.height(48.dp),
                        shape = BuroRadius.Small,
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = BuroColors.Primary,
                                contentColor = BuroColors.OnPrimary,
                            ),
                    ) {
                        Text(
                            text = text.format(if (enabled) text.open else text.enable, brand.displayName),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    if (enabled) {
                        OutlinedButton(
                            onClick = { appState.setWebServiceEnabled(brand.id, false) },
                            modifier = Modifier.height(48.dp),
                            shape = BuroRadius.Small,
                        ) {
                            Text(text.disable, color = BuroColors.TextMuted)
                        }
                    }
                }
            }
        }
    }

    confirming?.let { brand ->
        WebServiceAgeDialog(
            text = text,
            brand = brand,
            onConfirm = {
                confirming = null
                appState.setWebServiceEnabled(brand.id, true)
                open(brand)
            },
            onDismiss = { confirming = null },
        )
    }
}

/** "Novidade: GoDorama", once per adult profile. Ativar goes to the page, which asks 18+. */
@Composable
fun WebServiceOfferDialog(appState: DesktopAppState) {
    val text = WebServiceStrings.of(appState.language)
    val brand = WebServices.GODORAMA
    AlertDialog(
        onDismissRequest = { appState.dismissWebServiceOffer(openPage = false) },
        containerColor = BuroColors.SurfaceRaised,
        title = { Text(text.format(text.offerTitle, brand.displayName), color = BuroColors.Text, fontWeight = FontWeight.Bold) },
        text = { Text(text.format(text.offerBody, brand.displayName), color = BuroColors.TextMuted) },
        confirmButton = {
            Button(
                onClick = { appState.dismissWebServiceOffer(openPage = true) },
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = BuroColors.Primary,
                        contentColor = BuroColors.OnPrimary,
                    ),
            ) { Text(text.format(text.enable, brand.displayName)) }
        },
        dismissButton = {
            TextButton(onClick = { appState.dismissWebServiceOffer(openPage = false) }) {
                Text(text.offerLater, color = BuroColors.TextMuted)
            }
        },
    )
}

/** The 18+ question. Says in the same breath that BURO keeps no password. */
@Composable
fun WebServiceAgeDialog(
    text: WebServiceStrings,
    brand: WebServiceBrand,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BuroColors.SurfaceRaised,
        title = { Text(text.ageTitle, color = BuroColors.Text, fontWeight = FontWeight.Bold) },
        text = { Text(text.format(text.ageBody, brand.displayName), color = BuroColors.TextMuted) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = BuroColors.Primary,
                        contentColor = BuroColors.OnPrimary,
                    ),
            ) { Text(text.ageConfirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(text.ageCancel, color = BuroColors.TextMuted) } },
    )
}

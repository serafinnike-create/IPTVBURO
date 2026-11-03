package com.lucasserafin94.iptvburo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.OndemandVideo
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.lucasserafin94.iptvburo.R
import com.lucasserafin94.iptvburo.domain.model.WebServiceBrand
import com.lucasserafin94.iptvburo.ui.components.FocusSurface
import com.lucasserafin94.iptvburo.ui.designsystem.BuroButton
import com.lucasserafin94.iptvburo.ui.designsystem.BuroButtonStyle
import com.lucasserafin94.iptvburo.ui.theme.BuroAccent
import com.lucasserafin94.iptvburo.ui.theme.BuroSurfaceRaised
import com.lucasserafin94.iptvburo.ui.theme.BuroTextPrimary
import com.lucasserafin94.iptvburo.ui.theme.BuroTextSecondary

/**
 * The web services on the Sources screen: turn one on, open it, turn it off.
 *
 * Beside the list buttons rather than among them, because a service is not a list — nothing is
 * imported, and it can be the only thing somebody uses. Not drawn at all for a Kids profile, which
 * is never offered an age-restricted service.
 */
@Composable
internal fun WebServiceSourcesSection(
    services: List<WebServiceBrand>,
    enabledIds: Set<String>,
    onSetEnabled: (String, Boolean) -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (services.isEmpty()) return
    var confirming by remember { mutableStateOf<WebServiceBrand?>(null) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.web_service_section_title),
            color = BuroTextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
        )
        services.forEach { brand ->
            val enabled = brand.id in enabledIds
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BuroSurfaceRaised, RoundedCornerShape(16.dp))
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.OndemandVideo,
                    contentDescription = null,
                    tint = BuroAccent,
                    modifier = Modifier.size(30.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = brand.displayName,
                        color = BuroTextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.web_service_tagline),
                        color = BuroTextSecondary,
                        fontSize = 13.sp,
                    )
                }
                if (enabled) {
                    BuroButton(onClick = { onOpen(brand.id) }) {
                        Text(text = stringResource(R.string.web_service_open, brand.displayName))
                    }
                    BuroButton(
                        onClick = { onSetEnabled(brand.id, false) },
                        style = BuroButtonStyle.Secondary,
                    ) {
                        Text(text = stringResource(R.string.web_service_disable))
                    }
                } else {
                    BuroButton(onClick = {
                        if (brand.minimumAge > 0) confirming = brand else onSetEnabled(brand.id, true)
                    }) {
                        Text(text = stringResource(R.string.web_service_enable, brand.displayName))
                    }
                }
            }
        }
    }

    confirming?.let { brand ->
        WebServiceAgeDialog(
            brand = brand,
            onConfirm = {
                confirming = null
                onSetEnabled(brand.id, true)
            },
            onDismiss = { confirming = null },
        )
    }
}

/**
 * The 18+ question asked before an age-restricted service is turned on.
 *
 * Says, in the same breath, that BURO keeps no password: the sign-in happens on the service's page.
 */
@Composable
private fun WebServiceAgeDialog(
    brand: WebServiceBrand,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .background(BuroSurfaceRaised, RoundedCornerShape(22.dp))
                .padding(22.dp),
        ) {
            Text(
                text = stringResource(R.string.web_service_age_title),
                color = BuroTextPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.web_service_age_body, brand.displayName),
                color = BuroTextSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Cancel first and focused first: on a remote, the safe answer is the one under
                // the cursor.
                BuroButton(onClick = onDismiss, style = BuroButtonStyle.Secondary) {
                    Text(text = stringResource(R.string.web_service_age_cancel))
                }
                BuroButton(onClick = onConfirm) {
                    Text(text = stringResource(R.string.web_service_age_confirm))
                }
            }
        }
    }
}

/** The services on Home, one card each, right under the banner. */
@Composable
internal fun HomeWebServicesRow(
    services: List<WebServiceBrand>,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (services.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.home_web_services),
            color = BuroTextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            services.forEach { brand ->
                FocusSurface(
                    onClick = { onOpen(brand.id) },
                    modifier = Modifier
                        .width(300.dp)
                        .height(96.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.OndemandVideo,
                            contentDescription = null,
                            tint = BuroAccent,
                            modifier = Modifier.size(34.dp),
                        )
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(
                                text = brand.displayName,
                                color = BuroTextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = stringResource(R.string.web_service_open, brand.displayName),
                                color = BuroTextSecondary,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

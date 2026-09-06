package com.lucasserafin94.iptvburo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.lucasserafin94.iptvburo.ui.components.FocusSurface
import com.lucasserafin94.iptvburo.ui.localization.AppLanguage
import com.lucasserafin94.iptvburo.ui.theme.BuroAccent
import com.lucasserafin94.iptvburo.ui.theme.BuroCanvas
import com.lucasserafin94.iptvburo.ui.theme.BuroSurface
import com.lucasserafin94.iptvburo.ui.theme.BuroTextPrimary
import com.lucasserafin94.iptvburo.ui.theme.BuroTextSecondary

@Composable
fun LanguageSelectionScreen(
    languages: List<AppLanguage>,
    onSelect: (String) -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(BuroCanvas, BuroSurface, BuroCanvas)))
                .safeDrawingPadding()
                .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("IPTV  BURO", color = BuroTextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
            Spacer(Modifier.height(16.dp))
            // This screen runs before any locale is applied, so string resources would resolve to
            // the device language — exactly what the user is here to change. Every line therefore
            // speaks each supported language natively, first one large, the rest as echoes.
            Text(LANGUAGE_PROMPTS.first(), color = BuroTextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            LANGUAGE_PROMPTS.drop(1).forEach { prompt ->
                Text(prompt, color = BuroTextSecondary, fontSize = 16.sp)
            }
            Spacer(Modifier.height(30.dp))
            // One row per language rather than five side by side. Sharing a phone's width between
            // five buttons left "Português (Brasil)" broken across three lines and "Español" split
            // mid-word — a language you cannot read is not a language you can choose.
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                for (language in languages) {
                    FocusSurface(
                        onClick = { onSelect(language.tag) },
                        modifier = Modifier.fillMaxWidth().height(58.dp),
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = language.displayName,
                                color = BuroTextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            // Same rule as the prompts: pre-locale, so the reassurance is repeated in every
            // supported language rather than only the one the device happens to be in.
            LANGUAGE_FOOTER_NOTES.forEach { note ->
                Text(note, color = BuroAccent, fontSize = 13.sp)
            }
        }
    }
}

/** "Choose your language", once per supported language, in the picker\'s own order. */
private val LANGUAGE_PROMPTS =
    listOf(
        "Escolha seu idioma",
        "Choose your language",
        "Elige tu idioma",
        "Wähle deine Sprache",
        "Scegli la tua lingua",
    )

/** "You can change this later in Settings.", once per supported language. */
private val LANGUAGE_FOOTER_NOTES =
    listOf(
        "Você poderá alterar isso em Configurações.",
        "You can change this later in Settings.",
        "Puedes cambiarlo más tarde en Ajustes.",
        "Du kannst das später in den Einstellungen ändern.",
        "Puoi cambiarlo in seguito nelle Impostazioni.",
    )

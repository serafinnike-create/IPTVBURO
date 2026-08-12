package com.lucasserafin94.iptvburo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.lucasserafin94.iptvburo.ui.components.FocusSurface
import com.lucasserafin94.iptvburo.ui.theme.BuroAccent
import com.lucasserafin94.iptvburo.ui.theme.BuroCanvas
import com.lucasserafin94.iptvburo.ui.theme.BuroSurface
import com.lucasserafin94.iptvburo.ui.theme.BuroTextPrimary

@Composable
internal fun CastPersonChip(
    name: String,
    onClick: () -> Unit,
    /**
     * The performer's photo, when one has been found.
     *
     * Null renders initials, which is the honest answer for a person the metadata source does not
     * know — and the only answer at all when no TMDB key is configured, since the provider sends
     * the cast as a bare comma-separated string with no images.
     */
    photoUrl: String? = null,
) {
    FocusSurface(
        onClick = onClick,
        modifier = Modifier.width(116.dp).height(116.dp),
        backgroundColor = BuroSurface,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        ) {
            Box(
                modifier = Modifier.width(48.dp).height(48.dp).clip(CircleShape)
                    .background(BuroAccent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                if (photoUrl != null) {
                    AsyncImage(
                        model = photoUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        text = name.personInitials(),
                        color = BuroAccent,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
            Text(
                text = name,
                color = BuroTextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

internal fun String.toCastNames(): List<String> =
    split(Regex("[,;]|\\s/\\s"))
        .map(String::trim)
        .filter { it.length in 2..100 }
        .distinctBy(String::lowercase)
        .take(24)

private fun String.personInitials(): String =
    split(Regex("\\s+"))
        .filter(String::isNotBlank)
        .take(2)
        .mapNotNull(String::firstOrNull)
        .joinToString("")
        .uppercase()
        .ifBlank { "?" }

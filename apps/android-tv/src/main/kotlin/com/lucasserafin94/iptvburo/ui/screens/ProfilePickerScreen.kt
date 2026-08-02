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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucasserafin94.iptvburo.ui.ProfileUi
import com.lucasserafin94.iptvburo.ui.components.FocusSurface
import com.lucasserafin94.iptvburo.ui.theme.BuroAccent
import com.lucasserafin94.iptvburo.ui.theme.BuroCanvas
import com.lucasserafin94.iptvburo.ui.theme.BuroSurface
import com.lucasserafin94.iptvburo.ui.theme.BuroTextPrimary
import com.lucasserafin94.iptvburo.ui.theme.BuroTextSecondary

@Composable
fun ProfilePickerScreen(
    profiles: List<ProfileUi>,
    onSelect: (String) -> Unit,
    onCreate: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var adding by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var isKids by remember { mutableStateOf(false) }
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(BuroCanvas, BuroSurface, BuroCanvas)))
                .safeDrawingPadding()
                .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().widthIn(max = 920.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("IPTV  BURO", color = BuroTextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
            Spacer(Modifier.height(18.dp))
            Text("Quem está assistindo?", color = BuroTextPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text("Cada pessoa mantém seus favoritos, idioma e progresso.", color = BuroTextSecondary, fontSize = 15.sp)
            Spacer(Modifier.height(30.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally)) {
                for (profile in profiles) {
                    ProfileCard(profile = profile, onClick = { onSelect(profile.id) })
                }
                if (profiles.size < 5) {
                    FocusSurface(onClick = { adding = true }, modifier = Modifier.size(128.dp)) {
                        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = BuroAccent, modifier = Modifier.size(42.dp))
                            Text("Adicionar", color = BuroTextPrimary, fontSize = 13.sp)
                        }
                    }
                }
            }
            if (adding) {
                Spacer(Modifier.height(28.dp))
                Column(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it.take(24) },
                        label = { Text("Nome do perfil") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FocusSurface(onClick = { isKids = !isKids }, modifier = Modifier.height(52.dp).weight(1f)) {
                            Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ChildCare, null, tint = if (isKids) BuroAccent else BuroTextSecondary)
                                Text(if (isKids) " Infantil" else " Adulto", color = BuroTextPrimary)
                            }
                        }
                        FocusSurface(
                            onClick = {
                                if (newName.isNotBlank()) {
                                    onCreate(newName, isKids)
                                    newName = ""
                                    adding = false
                                }
                            },
                            modifier = Modifier.height(52.dp).weight(1f),
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Criar perfil", color = BuroTextPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileCard(profile: ProfileUi, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, modifier = Modifier.size(128.dp)) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(
                modifier = Modifier.size(70.dp).clip(CircleShape).background(avatarBrush(profile.avatarKey)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (profile.isKids) Icons.Default.ChildCare else Icons.Default.Person,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(38.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(profile.name, color = BuroTextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1)
            if (profile.isKids) Text("Kids", color = BuroAccent, fontSize = 11.sp)
        }
    }
}

private fun avatarBrush(key: String): Brush =
    when (key) {
        "ember" -> Brush.linearGradient(listOf(Color(0xFFB84A3A), Color(0xFFF0A35A)))
        "forest" -> Brush.linearGradient(listOf(Color(0xFF184C3C), Color(0xFF68B78A)))
        "ocean" -> Brush.linearGradient(listOf(Color(0xFF173B63), Color(0xFF4A8CB8)))
        "moon" -> Brush.linearGradient(listOf(Color(0xFF3C365B), Color(0xFF8D82B7)))
        else -> Brush.linearGradient(listOf(Color(0xFF6B5A39), Color(0xFFD4B36A)))
    }

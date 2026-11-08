package com.smarted.tv.ui.mode

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.smarted.tv.core.model.DeviceMode
import com.smarted.tv.ui.components.GlassBackdrop
import com.smarted.tv.ui.components.GlassClickable
import com.smarted.tv.ui.theme.LocalDimens
import com.smarted.tv.ui.theme.Smoke

@Composable
fun ModeSelectScreen(suggested: DeviceMode, onSelect: (DeviceMode) -> Unit) {
    val d = LocalDimens.current
    val phoneFocus = remember { FocusRequester() }
    val tvFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { (if (suggested == DeviceMode.TV) tvFocus else phoneFocus).requestFocus() }
    }

    GlassBackdrop {
        BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp)) {
            val stacked = maxWidth < 600.dp
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text("SmartED-TV", fontSize = d.textHeadline, fontWeight = FontWeight.Bold, color = Smoke.TextPrimary)
                Text("Em qual dispositivo você está usando o app?", fontSize = d.textTitle, color = Smoke.TextSecondary, textAlign = TextAlign.Center)

                val phoneCard = @Composable {
                    ModeCard(
                        "Celular / Tablet", "Toque, gestos e rolagem", Icons.Rounded.PhoneAndroid,
                        Modifier.focusRequester(phoneFocus),
                    ) { onSelect(DeviceMode.PHONE) }
                }
                val tvCard = @Composable {
                    ModeCard(
                        "TV / TV Box / Projetor", "Controle remoto (D-pad)", Icons.Rounded.Tv,
                        Modifier.focusRequester(tvFocus),
                    ) { onSelect(DeviceMode.TV) }
                }
                if (stacked) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { phoneCard(); tvCard() }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) { phoneCard(); tvCard() }
                }
                Text("Você pode mudar isso depois em Configurações.", fontSize = d.textCaption, color = Smoke.TextTertiary)
            }
        }
    }
}

@Composable
private fun ModeCard(title: String, subtitle: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val d = LocalDimens.current
    GlassClickable(
        onClick = onClick,
        modifier = modifier.width(260.dp).heightIn(min = 150.dp),
        contentPadding = PaddingValues(20.dp),
    ) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, Modifier.size(48.dp), tint = Smoke.TextPrimary)
            Text(title, fontSize = d.textTitle, fontWeight = FontWeight.Bold, color = Smoke.TextPrimary, textAlign = TextAlign.Center)
            Text(subtitle, fontSize = d.textCaption, color = Smoke.TextSecondary, textAlign = TextAlign.Center)
        }
    }
}

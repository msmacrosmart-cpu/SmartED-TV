package com.smarted.tv.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.smarted.tv.core.model.Scope
import com.smarted.tv.ui.components.TvAction
import com.smarted.tv.ui.theme.LocalCompact
import com.smarted.tv.ui.theme.TvColors
import com.smarted.tv.ui.theme.smokedGlass
import com.smarted.tv.ui.theme.whiteFocus
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * Tela inicial inspirada no desenho do Ultra TV: relógio no alto e navegação lateral.
 * Aqui os três destinos ficam em cartões de vidro fumê, centralizados verticalmente à esquerda;
 * Configurações fica perto da borda inferior, como pedido para o SmartED.
 */
@Composable
fun HomeScreen(onNavigate: (Scope) -> Unit, onSettings: () -> Unit) {
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { now = ZonedDateTime.now(); delay(1000) } }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestFocus() }
    val compact = LocalCompact.current
    val locale = Locale.forLanguageTag("pt-BR")
    Box(Modifier.fillMaxSize().testTag("home")) {
        Column(
            Modifier.align(Alignment.TopEnd).testTag("home-clock"),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                now.format(DateTimeFormatter.ofPattern("HH:mm", locale)),
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
            )
            Text(
                now.format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", locale)),
                style = MaterialTheme.typography.bodyMedium,
                color = TvColors.Muted,
            )
        }
        Column(
            Modifier.align(Alignment.CenterStart).width(if (compact) 220.dp else 270.dp).testTag("home-navigation"),
            verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 16.dp),
        ) {
            NavigationCard("Canais", "Ao vivo, no seu ritmo", Icons.Default.LiveTv, { onNavigate(Scope.CHANNELS) }, first)
            NavigationCard("Filmes", "Histórias para descobrir", Icons.Default.Movie, { onNavigate(Scope.MOVIES) })
            NavigationCard("Séries", "Só mais um episódio", Icons.Default.VideoLibrary, { onNavigate(Scope.SERIES) })
        }
        Column(
            Modifier.align(Alignment.Center).padding(start = if (compact) 240.dp else 300.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("SmartED TV", style = MaterialTheme.typography.headlineLarge, color = Color.White, textAlign = TextAlign.Center)
            Text("Seu próximo momento começa aqui.", style = MaterialTheme.typography.bodyLarge, color = TvColors.Muted, textAlign = TextAlign.Center)
            Text("Canais, filmes e séries dos seus addons Stremio.", style = MaterialTheme.typography.bodyMedium, color = TvColors.Muted, textAlign = TextAlign.Center)
        }
        TvAction(
            "Configurações",
            onSettings,
            icon = Icons.Default.Settings,
            modifier = Modifier.align(Alignment.BottomStart).width(if (compact) 220.dp else 270.dp).testTag("settings"),
        )
        Text(
            "Setas para navegar · OK para selecionar · Voltar para retornar",
            color = TvColors.Muted,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

@Composable
private fun NavigationCard(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit, focus: FocusRequester? = null) {
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().testTag("nav-$title")
            .then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .whiteFocus(focused, radius = 19.dp)
            .padding(3.dp)
            .clip(shape)
            .smokedGlass()
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = if (LocalCompact.current) 16.dp else 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, null, Modifier.size(if (LocalCompact.current) 30.dp else 36.dp), tint = Color.White)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = Color.White, style = MaterialTheme.typography.titleLarge)
            Text(subtitle, color = TvColors.Muted, style = MaterialTheme.typography.labelMedium)
        }
    }
}

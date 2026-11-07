package com.smarted.tv.ui.screens

import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.smarted.tv.core.model.Scope
import com.smarted.tv.ui.components.TvAction
import com.smarted.tv.ui.theme.LocalCompact
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(onNavigate: (Scope) -> Unit, onSettings: () -> Unit) {
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { now = ZonedDateTime.now(); delay(1000) } }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestFocus() }
    val compact = LocalCompact.current
    Column(Modifier.fillMaxSize().testTag("home"), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.LiveTv, null, tint = Color.White, modifier = Modifier.size(30.dp))
                Text("SmartED", style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text("TV", style = MaterialTheme.typography.labelMedium, color = TvColorsMuted)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(now.format(DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("pt-BR"))), style = MaterialTheme.typography.headlineMedium, color = Color.White)
                Text(now.format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Locale.forLanguageTag("pt-BR"))), style = MaterialTheme.typography.bodyMedium, color = TvColorsMuted)
            }
        }
        Spacer(Modifier.weight(1f))
        Text("Seu próximo momento começa aqui.", style = MaterialTheme.typography.headlineLarge, color = Color.White, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text("Um só lugar. Conteúdo dos seus addons.", style = MaterialTheme.typography.bodyLarge, color = TvColorsMuted)
        Spacer(Modifier.height(if (compact) 26.dp else 40.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            NavigationCard("Canais", "Ao vivo, no seu ritmo", Icons.Default.LiveTv, { onNavigate(Scope.CHANNELS) }, Modifier.width(if (compact) 190.dp else 230.dp), first)
            NavigationCard("Filmes", "Histórias para descobrir", Icons.Default.Movie, { onNavigate(Scope.MOVIES) }, Modifier.width(if (compact) 190.dp else 230.dp))
            NavigationCard("Séries", "Só mais um episódio", Icons.Default.VideoLibrary, { onNavigate(Scope.SERIES) }, Modifier.width(if (compact) 190.dp else 230.dp))
        }
        Spacer(Modifier.weight(1f))
        TvAction("Configurações", onSettings, icon = Icons.Default.Settings, modifier = Modifier.testTag("settings"))
        Spacer(Modifier.height(4.dp))
        Text("Navegue com as setas · OK para selecionar · Voltar para retornar", color = TvColorsMuted, style = MaterialTheme.typography.labelMedium)
    }
}
private val TvColorsMuted = Color(0xFFB5B6BA)

@Composable
private fun NavigationCard(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier, focus: FocusRequester? = null) {
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Column(modifier.testTag("nav-$title").then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
        .onFocusChanged { focused = it.isFocused }.whiteFocus(focused).padding(4.dp).smokedGlass()
        .clickable(interactionSource = interaction, indication = null, onClick = onClick).padding(26.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, Modifier.size(36.dp), tint = Color.White)
        Text(title, color = Color.White, style = MaterialTheme.typography.headlineMedium)
        Text(subtitle, color = TvColorsMuted, style = MaterialTheme.typography.bodyMedium)
    }
}

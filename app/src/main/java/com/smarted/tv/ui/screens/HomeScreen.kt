package com.smarted.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smarted.tv.core.model.Tab
import com.smarted.tv.ui.components.ContinueCard
import com.smarted.tv.ui.components.HomeGlassButton
import com.smarted.tv.ui.components.HomeRow
import com.smarted.tv.ui.state.AppViewModel
import com.smarted.tv.ui.theme.LocalCompact
import com.smarted.tv.ui.theme.TvColors

/**
 * Home — Glass TV (padrão da base IPVIO-Stream, sem chamadas de metadados no boot):
 * Data/Hora no topo (renderizada pela casca), lista de botões fumê CENTRALIZADA para
 * navegação imediata por D-pad e, abaixo, apenas o que já existe no banco local
 * (favoritos ao vivo / continuar assistindo). Nada de rede para montar esta tela.
 */
@Composable
fun HomeScreen(
    app: AppViewModel,
    onOpenChannels: () -> Unit,
    onOpenVod: (Tab) -> Unit,
    onOpenSettings: () -> Unit,
    onPlay: (PlayRequest) -> Unit,
) {
    val ui by app.ui.collectAsState()
    val compact = LocalCompact.current
    val hasSources = ui.sources.any { it.enabled }
    val noContent = ui.counts.getOrDefault(Tab.CHANNELS, 0) == 0 &&
        ui.counts.getOrDefault(Tab.MOVIES, 0) == 0 && ui.counts.getOrDefault(Tab.SERIES, 0) == 0

    // Foco inicial do D-pad: primeiro botão da Home (blindado contra recomposição ainda não acoplada).
    val firstFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp)) {
        if (!hasSources || noContent) WelcomeCard(app, onOpenSettings, hasSources)

        // Lista de botões centralizada — vidro fumê, foco branco puro.
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Column(
                Modifier.width(if (compact) 420.dp else 560.dp),
                verticalArrangement = Arrangement.spacedBy(if (compact) 9.dp else 13.dp),
            ) {
                HomeGlassButton(
                    "Canais ao vivo", countLabel(ui.counts[Tab.CHANNELS], "canais prontos para o play", "Conecte uma fonte para assistir"),
                    Icons.Default.LiveTv, "home-open-channels", onOpenChannels, firstFocus,
                )
                HomeGlassButton(
                    "Filmes", countLabel(ui.counts[Tab.MOVIES], "títulos no catálogo local", "Busca instantânea na sua lista"),
                    Icons.Default.Movie, "home-open-movies", { onOpenVod(Tab.MOVIES) },
                )
                HomeGlassButton(
                    "Séries", countLabel(ui.counts[Tab.SERIES], "títulos no catálogo local", "Temporadas e episódios do painel"),
                    Icons.Default.Tv, "home-open-series", { onOpenVod(Tab.SERIES) },
                )
                HomeGlassButton(
                    "Configurações", if (hasSources) "Fontes, EPG, Elementum e interface" else "Adicionar M3U · Xtream · repositório OnePlay",
                    Icons.Default.Settings, "home-open-settings", onOpenSettings,
                )
            }
        }

        // Faixa inferior: só conteúdo local já carregado (sem sincronizar nada).
        if (ui.favoriteChannels.isNotEmpty() || ui.continueWatching.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth()
                    .height(if (compact) 190.dp else 232.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (ui.continueWatching.isNotEmpty()) {
                    HomeRow("Continuar assistindo", null, Modifier.testTag("row-continue")) {
                        items(ui.continueWatching, key = { "cw-$it" }) { record ->
                            ContinueCard(record.title, record.positionMs, record.durationMs, onClick = {
                                onPlay(
                                    PlayRequest(
                                        target = com.smarted.tv.core.model.PlaybackTarget(record.url, record.title, isLive = false),
                                        title = record.title, favKey = record.favKey, kind = record.kind,
                                    )
                                )
                            })
                        }
                    }
                }
                if (ui.favoriteChannels.isNotEmpty()) {
                    HomeRow("Canais favoritos ao vivo", ui.counts[Tab.CHANNELS], Modifier.testTag("row-fav-channels")) {
                        items(ui.favoriteChannels, key = { "fc-$it" }) { channel ->
                            FavoriteChannelCard(channel.name, channel.logoUrl) {
                                onPlay(
                                    PlayRequest(
                                        target = com.smarted.tv.core.model.PlaybackTarget(channel.streamUrl, channel.name, channel.headers, true),
                                        title = channel.name, favKey = channel.favKey, kind = "channel",
                                    )
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

private fun countLabel(count: Int?, noun: String, fallback: String): String {
    if (count == null || count <= 0) return fallback
    val formatted = "%,d".format(count).replace(',', '.')
    return "$formatted $noun"
}

@Composable
private fun WelcomeCard(app: AppViewModel, onOpenSettings: () -> Unit, hasSources: Boolean) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        Modifier.fillMaxWidth().testTag("home-welcome")
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0x12FFFFFF))
            .clickable(interactionSource = interaction, indication = null, onClick = onOpenSettings)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(if (hasSources) "Sincronize suas listas" else "Bem-vindo ao SmartED TV", style = MaterialTheme.typography.titleLarge, color = Color.White)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                if (hasSources) "As fontes estão cadastradas mas o catálogo local está vazio. Atualize cada lista em Configurações → Fontes."
                else "Conecte uma playlist M3U, um painel Xtream Codes ou um repositório estilo OnePlay. Tudo fica no banco local — a Home nunca espera rede.",
                style = MaterialTheme.typography.bodyMedium, color = TvColors.Muted,
                modifier = Modifier.weight(1f, fill = false), maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        val ui by app.ui.collectAsState()
        if (ui.busy.isNotBlank()) Text(ui.busy, color = TvColors.Muted, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun FavoriteChannelCard(name: String, logoUrl: String?, onPlay: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        Modifier.width(if (LocalCompact.current) 180.dp else 210.dp)
            .testTag("fav-channel-${name.filter(Char::isLetterOrDigit).take(18)}")
            .clip(RoundedCornerShape(16.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onPlay),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color(0xFF17181B))) {
            com.smarted.tv.ui.components.RemoteImage(logoUrl, name, Modifier.matchParentSize(), androidx.compose.ui.layout.ContentScale.Fit)
            Box(Modifier.align(Alignment.BottomEnd).padding(6.dp).background(Color(0xCC111214), RoundedCornerShape(5.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                Text("AO VIVO", color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
        }
        Text(name, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp))
    }
}

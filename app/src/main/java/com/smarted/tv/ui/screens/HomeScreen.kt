package com.smarted.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smarted.tv.core.model.Tab
import com.smarted.tv.core.model.VodEntry
import com.smarted.tv.core.sources.StreamRules
import com.smarted.tv.ui.components.ContinueCard
import com.smarted.tv.ui.components.HomeRow
import com.smarted.tv.ui.components.RemoteImage
import com.smarted.tv.ui.components.TvAction
import com.smarted.tv.ui.components.VodPosterCard
import com.smarted.tv.ui.state.AppViewModel
import com.smarted.tv.ui.theme.LocalCompact
import com.smarted.tv.ui.theme.TvColors
import com.smarted.tv.ui.theme.smokedGlass
import com.smarted.tv.ui.theme.whiteFocus

/**
 * Início: boas-vindas + atalhos quando não há fontes; com dados, linhas de
 * favoritos ao vivo, continuar assistindo e últimas entradas do repositório local.
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
    val hasSources = ui.sources.any { it.enabled }
    val noContent = ui.counts.getOrDefault(Tab.CHANNELS, 0) == 0 &&
        ui.counts.getOrDefault(Tab.MOVIES, 0) == 0 && ui.counts.getOrDefault(Tab.SERIES, 0) == 0

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        if (!hasSources || noContent) {
            WelcomeCard(app, onOpenSettings, hasSources)
        }
        if (ui.favoriteChannels.isNotEmpty()) {
            HomeRow("Canais favoritos ao vivo", ui.counts[Tab.CHANNELS], Modifier.testTag("row-fav-channels")) {
                items(ui.favoriteChannels, key { "fc-${it.id}" }) { channel ->
                    FavoriteChannelCard(channel.name, channel.logoUrl, onOpenChannels) {
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
        if (ui.continueWatching.isNotEmpty()) {
            HomeRow("Continuar assistindo", null, Modifier.testTag("row-continue")) {
                items(ui.continueWatching, key { "cw-${it.favKey}" }) { record ->
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
        if (ui.recentlyAddedMovies.isNotEmpty()) {
            VodRow("Últimos adicionados · Filmes", ui.recentlyAddedMovies, "row-movies", onPlay = { onVodPlay(it, onPlay) }, onOpenTab = { onOpenVod(Tab.MOVIES) })
        }
        if (ui.recentlyAddedSeries.isNotEmpty()) {
            VodRow("Últimos adicionados · Séries", ui.recentlyAddedSeries, "row-series", onPlay = { onVodPlay(it, onPlay) }, onOpenTab = { onOpenVod(Tab.SERIES) })
        }
        if (ui.topMovies.isNotEmpty() && ui.recentlyAddedMovies.isEmpty() && ui.recentlyAddedSeries.isEmpty()) {
            VodRow("Mais votados · Filmes", ui.topMovies, "row-top", onPlay = { onVodPlay(it, onPlay) }, onOpenTab = { onOpenVod(Tab.MOVIES) })
        }
        Spacer(Modifier.height(if (LocalCompact.current) 20.dp else 42.dp))
    }
}

private fun onVodPlay(entry: VodEntry, onPlay: (PlayRequest) -> Unit) {
    if (entry.kind == Tab.SERIES) return // séries abrem pelo catálogo (seleção de episódio)
    val target = entry.streamUrl.takeIf(StreamRules::isProbablyPlayable) ?: return
    onPlay(PlayRequest(com.smarted.tv.core.model.PlaybackTarget(target, entry.name, isLive = false), entry.name, entry.favKey, "vod"))
}

@Composable
private fun VodRow(title: String, entries: List<VodEntry>, tag: String, onPlay: (VodEntry) -> Unit, onOpenTab: () -> Unit) {
    HomeRow(title, entries.size, Modifier.testTag(tag)) {
        item {
            TvAction("Abrir catálogo →", onOpenTab, compact = true)
        }
        items(entries, key { "v-${it.id}" }) { entry ->
            Box(Modifier.width(if (LocalCompact.current) 168.dp else 196.dp)) {
                VodPosterCard(entry, onClick = { if (entry.kind == Tab.SERIES) onOpenTab() else onPlay(entry) })
            }
        }
    }
}

@Composable
private fun WelcomeCard(app: AppViewModel, onOpenSettings: () -> Unit, hasSources: Boolean) {
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    Column(
        Modifier.fillMaxWidth().testTag("home-welcome")
            .smokedGlass(22.dp).whiteFocus(focused, radius = 24.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onOpenSettings)
            .padding(26.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(if (hasSources) "Sincronize suas listas" else "Bem-vindo ao SmartED TV", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Text(
            if (hasSources) "Suas fontes estão cadastradas mas sem catálogo local. Atualize cada lista em Configurações → Fontes para importar canais, VOD e EPG."
            else "Conecte uma playlist M3U/M3U8, um painel Xtream Codes ou um repositório de fontes (estilo OnePlay) em Configurações → Fontes. Tudo fica no banco local do app, com auto-update e EPG sincronizado pelo nome real dos canais.",
            style = MaterialTheme.typography.bodyMedium, color = TvColors.Muted,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TvAction("Abrir Configurações", onOpenSettings, primary = true, modifier = Modifier.testTag("home-open-settings"))
            val ui by app.ui.collectAsState()
            if (ui.busy.isNotBlank()) Text(ui.busy, color = TvColors.Muted, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun FavoriteChannelCard(name: String, logoUrl: String?, onOpenChannels: () -> Unit, onPlay: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    Column(
        Modifier.width(if (LocalCompact.current) 180.dp else 210.dp).testTag("fav-channel-${name.filter(Char::isLetterOrDigit).take(18)}")
            .onFocusChanged { focused = it.isFocused }
            .whiteFocus(focused, radius = 20.dp).padding(3.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onPlay),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color(0xFF17181B))) {
            RemoteImage(logoUrl, name, Modifier.matchParentSize(), androidx.compose.ui.layout.ContentScale.Fit)
            Box(Modifier.align(Alignment.BottomEnd).padding(6.dp).background(Color(0xCC111214), RoundedCornerShape(5.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                Text("AO VIVO", color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
        }
        Text(name, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp))
    }
}

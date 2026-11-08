package com.smarted.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.bringIntoViewIfChildrenAreFocused
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smarted.tv.core.model.Tab
import com.smarted.tv.ui.components.ContentRow
import com.smarted.tv.ui.components.ContinueCard
import com.smarted.tv.ui.components.HomeMenuTile
import com.smarted.tv.ui.components.PosterCell
import com.smarted.tv.ui.components.RemoteImage
import com.smarted.tv.ui.state.AppViewModel
import com.smarted.tv.ui.theme.TvColors
import com.smarted.tv.ui.theme.dpx
import com.smarted.tv.ui.theme.spx

/**
 * Home — composição no padrão IPVIO-Stream: data/hora no topo (renderizadas pela casca),
 * menu centralizado em vidro fumê e fileiras de conteúdo local abaixo. Zero rede no boot.
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

    // Foco inicial do D-pad blindado: primeiro tile do menu (runCatching protege recomposição).
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    Column(
        Modifier.fillMaxSize().padding(horizontal = dpx(28)),
        verticalArrangement = Arrangement.spacedBy(dpx(14)),
    ) {
        if (!hasSources || noContent) WelcomeBanner(app, onOpenSettings, hasSources)

        // menu centralizado em vidro fumê
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Column(Modifier.width(dpx(760)), verticalArrangement = Arrangement.spacedBy(dpx(14))) {
                HomeMenuTile(
                    "Canais ao vivo", countLabel(ui.counts[Tab.CHANNELS], "canais prontos para o play", "Conecte uma fonte para assistir"),
                    Icons.Default.LiveTv, "home-open-channels", onOpenChannels, firstFocus,
                )
                HomeMenuTile(
                    "Filmes", countLabel(ui.counts[Tab.MOVIES], "títulos no catálogo local", "Busca instantânea na sua lista"),
                    Icons.Default.Movie, "home-open-movies", { onOpenVod(Tab.MOVIES) },
                )
                HomeMenuTile(
                    "Séries", countLabel(ui.counts[Tab.SERIES], "títulos no catálogo local", "Temporadas e episódios do painel"),
                    Icons.Default.Tv, "home-open-series", { onOpenVod(Tab.SERIES) },
                )
                HomeMenuTile(
                    "Configurações", if (hasSources) "Fontes, EPG, Elementum e reprodução" else "Adicionar M3U · Xtream · repositório OnePlay",
                    Icons.Default.Settings, "home-open-settings", onOpenSettings,
                )
            }
        }

        // fileiras locais (nada de rede aqui)
        val hasRows = ui.continueWatching.isNotEmpty() || ui.favoriteChannels.isNotEmpty() ||
            ui.recentlyAddedMovies.isNotEmpty() || ui.recentlyAddedSeries.isNotEmpty()
        if (hasRows) {
            Column(
                Modifier.fillMaxWidth().height(dpx(330)).verticalScroll(rememberScrollState()).bringIntoViewIfChildrenAreFocused(),
                verticalArrangement = Arrangement.spacedBy(dpx(16)),
            ) {
                if (ui.continueWatching.isNotEmpty()) {
                    ContentRow("Continuar assistindo", null, "row-continue") {
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
                    ContentRow("Canais favoritos ao vivo", ui.counts[Tab.CHANNELS], "row-fav-channels") {
                        items(ui.favoriteChannels, key = { "fc-$it" }) { channel ->
                            LiveMiniCard(channel.name, channel.logoUrl) {
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
                if (ui.recentlyAddedMovies.isNotEmpty()) {
                    ContentRow("Últimos adicionados · Filmes", ui.recentlyAddedMovies.size, "row-movies") {
                        item { PosterCell(ui.recentlyAddedMovies.first(), { onOpenVod(Tab.MOVIES) }, Modifier.width(dpx(300))) }
                        items(ui.recentlyAddedMovies.drop(1), key = { "v-$it" }) { entry ->
                            PosterCell(entry, { onOpenVod(Tab.MOVIES) }, Modifier.width(dpx(300)))
                        }
                        item {
                            OpenAllTile("Abrir catálogo de filmes →") { onOpenVod(Tab.MOVIES) }
                        }
                    }
                }
                if (ui.recentlyAddedSeries.isNotEmpty()) {
                    ContentRow("Últimos adicionados · Séries", ui.recentlyAddedSeries.size, "row-series") {
                        item { PosterCell(ui.recentlyAddedSeries.first(), { onOpenVod(Tab.SERIES) }, Modifier.width(dpx(300))) }
                        items(ui.recentlyAddedSeries.drop(1), key = { "s-$it" }) { entry ->
                            PosterCell(entry, { onOpenVod(Tab.SERIES) }, Modifier.width(dpx(300)))
                        }
                    }
                }
                Spacer(Modifier.height(dpx(10)))
            }
        }
    }
}

private fun countLabel(count: Int?, noun: String, fallback: String): String {
    if (count == null || count <= 0) return fallback
    return "%,d".format(count).replace(',', '.') + " " + noun
}

@Composable
private fun OpenAllTile(label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier.width(dpx(300)).height(dpx(150)).clip(RoundedCornerShape(dpx(16))).background(Color(0x12FFFFFF))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(dpx(18)), contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dpx(8))) {
            Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(dpx(20)))
            Text(label, color = Color.White, fontSize = spx(16))
        }
    }
}

@Composable
private fun LiveMiniCard(name: String, logoUrl: String?, onPlay: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        Modifier.width(dpx(330)).testTag("fav-channel-${name.filter(Char::isLetterOrDigit).take(18)}")
            .clip(RoundedCornerShape(dpx(16)))
            .clickable(interactionSource = interaction, indication = null, onClick = onPlay),
        verticalArrangement = Arrangement.spacedBy(dpx(8)),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color(0xFF101113))) {
            RemoteImage(logoUrl, name, Modifier.matchParentSize(), ContentScale.Fit)
            Box(Modifier.align(Alignment.BottomEnd).padding(dpx(8)).background(Color(0xCC111214), RoundedCornerShape(dpx(8))).padding(horizontal = dpx(10), vertical = dpx(3))) {
                Text("AO VIVO", color = Color.White, fontSize = spx(13))
            }
        }
        Text(name, color = Color.White, fontSize = spx(18), fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun WelcomeBanner(app: AppViewModel, onOpenSettings: () -> Unit, hasSources: Boolean) {
    val ui by app.ui.collectAsState()
    val interaction = remember { MutableInteractionSource() }
    Column(
        Modifier.fillMaxWidth().testTag("home-welcome")
            .clip(RoundedCornerShape(dpx(22)))
            .background(Brush.linearGradient(listOf(Color(0x14FFFFFF), Color(0x08FFFFFF))))
            .border(1.dp, TvColors.Outline, RoundedCornerShape(dpx(22)))
            .clickable(interactionSource = interaction, indication = null, onClick = onOpenSettings)
            .padding(horizontal = dpx(30), vertical = dpx(18)),
        verticalArrangement = Arrangement.spacedBy(dpx(6)),
    ) {
        Text(
            if (hasSources) "Sincronize suas listas" else "Bem-vindo ao SmartED TV",
            style = MaterialTheme.typography.titleLarge, color = Color.White,
        )
        Text(
            if (hasSources) "As fontes estão cadastradas mas o catálogo local está vazio. Toque para abrir Configurações → Fontes e atualizar cada lista."
            else "Conecte uma playlist M3U, um painel Xtream Codes ou um repositório estilo OnePlay em Configurações → Fontes. Tudo fica no banco local — a Home nunca espera rede.",
            style = MaterialTheme.typography.bodyMedium, color = TvColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        if (ui.busy.isNotBlank()) Text(ui.busy, color = TvColors.Muted, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

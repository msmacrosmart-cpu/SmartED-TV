package com.smarted.tv.ui.screens

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.clickable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smarted.tv.core.catalog.programWindow
import com.smarted.tv.core.catalog.sortItems
import com.smarted.tv.core.model.*
import com.smarted.tv.core.playback.ResolvedPlayback
import com.smarted.tv.player.VideoSurface
import com.smarted.tv.player.playerStatus
import com.smarted.tv.player.rememberVideoPlayer
import com.smarted.tv.ui.components.*
import com.smarted.tv.ui.state.CatalogViewModel
import com.smarted.tv.ui.theme.LocalCompact
import com.smarted.tv.ui.theme.TvColors
import com.smarted.tv.ui.theme.whiteFocus
import com.smarted.tv.ui.theme.smokedGlass
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

@Composable
fun ChannelsScreen(vm: CatalogViewModel, revision: Int, onHome: () -> Unit, onSettings: () -> Unit, onPlay: (MediaItem, Video?) -> Unit, previewEnabled: Boolean = true) {
    val ui by vm.ui.collectAsState()
    val data by vm.data.collectAsState()
    LaunchedEffect(revision) { vm.start(); if (revision > 0) vm.retry() }
    val categoryFocus = remember { FocusRequester() }
    val channelFocus = remember { FocusRequester() }
    val previewFocus = remember { FocusRequester() }
    val items = remember(ui.items, ui.query.sort, ui.query.direction) { sortItems(ui.items, ui.query.sort, ui.query.direction) }
    var selectedKey by remember { mutableStateOf<String?>(null) }
    val selected = items.firstOrNull { it.key == selectedKey } ?: items.firstOrNull()
    val listState = rememberLazyListState()
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) { while (true) { now = Instant.now(); delay(30_000) } }
    LaunchedEffect(selected?.key, now) {
        if (selected != null) {
            val window = programWindow(selected.videos, now)
            if (window.current == null || window.upcoming.isEmpty()) vm.refreshGuide(selected)
        }
    }
    LaunchedEffect(listState, items.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.index } }.collect { visible ->
            vm.ensureGuide(visible.mapNotNull { items.getOrNull(it) })
            if ((visible.lastOrNull() ?: 0) >= items.size - 6) vm.loadMore()
        }
    }
    Column(Modifier.fillMaxSize().testTag("channels"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            BackAction(onHome)
            Text("Canais", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            SearchField(ui.query.search, vm::search, "Pesquisar somente em Canais", Modifier.weight(1f), "channel-search")
        }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(0.8f).fillMaxHeight().focusGroup().focusRestorer().testTag("channels-categories"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("Categorias")
                val categories = listOf("Todos", "Favoritos") + vm.categories()
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(categories, key = { it }) { category ->
                        Choice(category, ui.query.category == category, { vm.category(category) }, Modifier.fillMaxWidth()
                            .focusProperties { right = channelFocus }.then(if (category == categories.first()) Modifier.focusRequester(categoryFocus) else Modifier))
                    }
                }
            }
            Column(Modifier.weight(1.15f).fillMaxHeight().focusGroup().focusRestorer().testTag("channels-list"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("${items.size} canais")
                if (ui.loading) Notice("Carregando canais…")
                if (items.isEmpty() && !ui.loading) EmptyState("Seus canais, aqui", ui.error.ifBlank { "Adicione um addon com catálogos do tipo tv ou channel e escolha o escopo Canais." }, Modifier.fillMaxWidth()) {
                    TvAction("Configurações", onSettings, focusRequester = channelFocus)
                }
                LazyColumn(state = listState, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(3.dp)) {
                    items(items, key = { it.key }) { item ->
                        val window = programWindow(item.videos, now)
                        ChannelRow(item, window.current?.title.orEmpty(), item.key == selected?.key, { selectedKey = item.key },
                            Modifier.fillMaxWidth().then(if (item.key == items.first().key) Modifier.focusRequester(channelFocus) else Modifier)
                                .focusProperties { left = categoryFocus; right = previewFocus })
                    }
                    if (ui.hasMore) item { TvAction(if (ui.loadingMore) "Carregando…" else "Mais canais", vm::loadMore, enabled = !ui.loadingMore, modifier = Modifier.fillMaxWidth()) }
                }
            }
            Column(Modifier.weight(1.5f).fillMaxHeight().focusGroup().focusRestorer().testTag("channels-preview"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (selected != null) {
                    Text(selected.name, color = Color.White, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Preview(selected, vm, previewEnabled, onPlay, previewFocus, channelFocus)
                    SectionLabel("A seguir · programação do addon")
                    val guide = programWindow(selected.videos, now)
                    val formatter = remember { DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()) }
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val programs = listOfNotNull(guide.current) + guide.upcoming
                        if (programs.isEmpty()) item { Notice("Este addon não fornece EPG para o canal. A transmissão continua disponível.") }
                        items(programs, key = { it.id }) { program ->
                            val start = runCatching { Instant.parse(program.startTime) }.getOrNull()
                            Panel(Modifier.fillMaxWidth(), padding = 10.dp) {
                                Text("${start?.let(formatter::format) ?: "--:--"}  ${program.title.ifBlank { "Programa" }}${if (program.id == guide.current?.id) "  · AGORA" else ""}",
                                    color = Color.White, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (program.description.isNotBlank()) Text(program.description, color = TvColors.Muted, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                } else EmptyState("Pré-visualização", "Selecione um canal para assistir e consultar a programação.")
            }
        }
        ui.warnings.forEach { Notice(it) }
        if (ui.error.isNotBlank()) Notice(ui.error)
    }
}

@Composable
private fun ChannelRow(item: MediaItem, nowTitle: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Row(modifier.testTag("channel-${item.key}").onFocusChanged { focused = it.isFocused }.whiteFocus(focused, selected, 12.dp)
        .smokedGlass().clickable(interactionSource = interaction, indication = null, onClick = onClick).padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        RemoteImage(item.imageUrl, "Logotipo de ${item.name}", Modifier.size(52.dp, 40.dp), ContentScale.Fit)
        Column(Modifier.weight(1f)) {
            Text(item.name, color = Color.White, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(nowTitle.ifBlank { "Programação não informada" }, color = TvColors.Muted, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Preview(item: MediaItem, vm: CatalogViewModel, enabled: Boolean, onPlay: (MediaItem, Video?) -> Unit, focus: FocusRequester, left: FocusRequester) {
    var result by remember(item.key) { mutableStateOf<ResolvedPlayback?>(null) }
    var error by remember(item.key) { mutableStateOf("") }
    var reload by remember { mutableIntStateOf(0) }
    val data by vm.data.collectAsState()
    LaunchedEffect(item.key, reload, data.addons, enabled) {
        if (!enabled) return@LaunchedEffect
        result = null; error = ""
        delay(450)
        try { result = vm.graph.resolver.resolve(item, null, data.addons) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "Pré-visualização indisponível." }
    }
    val stream = result?.streams?.firstOrNull { it.direct }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9).smokedGlass(12.dp), contentAlignment = Alignment.Center) {
            if (stream != null && enabled) {
                val player = rememberVideoPlayer(stream, true)
                val status by playerStatus(player)
                VideoSurface(player, Modifier.matchParentSize())
                if (status.error.isNotBlank()) Text(status.error, color = Color.White, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(14.dp))
                else if (!status.ready) Notice("Conectando à transmissão…")
            } else Text(if (error.isNotBlank()) "Pré-visualização indisponível" else "Conectando ao addon…", color = TvColors.Muted, style = MaterialTheme.typography.bodyMedium)
        }
        if (error.isNotBlank()) Notice(error)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TvAction("Tela cheia", { onPlay(item, null) }, Modifier.focusProperties { this.left = left }, icon = Icons.Default.Fullscreen, primary = true, compact = true, focusRequester = focus)
            val favorite = data.favorites.any { it.key == item.key }
            TvAction(if (favorite) "Favoritado" else "Favoritar", { vm.toggleFavorite(item) }, icon = if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, compact = true)
            if (error.isNotBlank()) TvAction("Tentar", { reload++ }, compact = true)
        }
    }
}

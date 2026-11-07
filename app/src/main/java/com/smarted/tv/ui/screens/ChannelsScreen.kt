package com.smarted.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smarted.tv.core.model.BrowseQuery
import com.smarted.tv.core.model.ChannelRow
import com.smarted.tv.core.model.Programme
import com.smarted.tv.player.VideoSurface
import com.smarted.tv.player.rememberVideoPlayer
import com.smarted.tv.ui.components.CategoryChip
import com.smarted.tv.ui.components.EmptyState
import com.smarted.tv.ui.components.HeartToggle
import com.smarted.tv.ui.components.RemoteImage
import com.smarted.tv.ui.components.SearchField
import com.smarted.tv.ui.components.TvAction
import com.smarted.tv.ui.state.AppViewModel
import com.smarted.tv.ui.rememberChannelsModel
import com.smarted.tv.ui.theme.LocalCompact
import com.smarted.tv.ui.theme.TvColors
import com.smarted.tv.ui.theme.smokedGlass
import com.smarted.tv.ui.theme.whiteFocus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Canais ao vivo — 3 colunas (HTV/xPlayer):
 * 1) categorias + favoritos (busca de categorias) · 2) lista com logo + EPG ao vivo
 * (busca filtra SOMENTE canais) · 3) player de pré-visualização + grade dos próximos programas.
 */
@Composable
fun ChannelsScreen(
    app: AppViewModel,
    onPlay: (PlayRequest) -> Unit,
    onOpenSettings: () -> Unit,
    previewActive: Boolean,
) {
    val vm = rememberChannelsModel(app.graph)
    val ui by vm.ui.collectAsState()
    val prefs by app.preferences.collectAsState()
    val listState = rememberLazyListState()
    val compact = LocalCompact.current
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 to listState.layoutInfo.totalItemsCount }
            .collect { (last, total) -> if (total > 0 && last >= total - 14) vm.loadMore() }
    }

    Row(Modifier.fillMaxSize().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp)) {
        // Coluna 1 — categorias
        Column(
            Modifier.fillMaxHeight().weight(0.24f).smokedGlass(18.dp).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SearchField(
                value = ui.categorySearch,
                onChange = vm::setCategorySearch,
                hint = "Buscar categoria…",
                modifier = Modifier.fillMaxWidth(),
                tag = "channel-category-search",
            )
            LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    CategoryChip(BrowseQuery.FAVORITES, ui.totalRows.takeIf { ui.query.isFavorites }, ui.query.isFavorites, { vm.setQuery { q -> q.copy(category = if (q.isFavorites) BrowseQuery.ALL else BrowseQuery.FAVORITES) } }, icon = Icons.Default.Star)
                }
                item {
                    CategoryChip(BrowseQuery.ALL, if (ui.query.isAll) ui.totalRows else null, ui.query.isAll, { vm.setQuery { q -> q.copy(category = BrowseQuery.ALL) } })
                }
                items(ui.groups.filter { it.first.isNotBlank() && com.smarted.tv.core.text.TextNormalizer.searchMatches(ui.categorySearch, it.first) }, key = { "g-$it" }) { (name, count) ->
                    CategoryChip(name, count, ui.query.category == name, { vm.setQuery { q -> q.copy(category = name) } })
                }
                if (ui.groups.isEmpty()) {
                    item {
                        EmptyState("Sem categorias", "Adicione e atualize uma fonte em Configurações → Fontes.", Modifier.fillMaxWidth(), action = { TvAction("Abrir Configurações", onOpenSettings, compact = true) })
                    }
                }
            }
        }

        // Coluna 2 — lista de canais (busca exclusiva daqui)
        Column(
            Modifier.fillMaxHeight().weight(0.44f).smokedGlass(18.dp).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SearchField(
                value = ui.query.search,
                onChange = { text -> vm.setQuery { it.copy(search = text) } },
                hint = "Buscar canais…",
                modifier = Modifier.fillMaxWidth(),
                tag = "channel-content-search",
            )
            if (ui.rows.isEmpty() && !ui.loading) {
                EmptyState(
                    if (ui.totalRows == 0 && ui.groups.isEmpty()) "Nenhum canal no repositório local" else "Nada para este filtro",
                    "Atualize a fonte (Configurações → Fontes) ou limpe a busca.",
                    Modifier.fillMaxWidth(),
                )
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f).testTag("channel-list"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 12.dp),
            ) {
                itemsIndexed(ui.rows, key = { _, row -> "row-${row.channel.id}" }) { _, row ->
                    ChannelRowItem(
                        row = row,
                        selected = ui.selected?.id == row.channel.id,
                        onSelect = { vm.select(row.channel) },
                        onPlay = { onPlay(PlayRequest(vm.playbackTarget(row.channel), row.channel.name, row.channel.favKey, "channel")) },
                        onFavorite = { vm.toggleFavorite(row.channel) },
                    )
                }
                if (ui.loading && ui.rows.isNotEmpty()) {
                    item { Text("Carregando mais canais…", color = TvColors.Muted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(10.dp)) }
                }
            }
        }

        // Coluna 3 — pré-visualização + grade EPG
        Column(
            Modifier.fillMaxHeight().weight(0.32f).smokedGlass(18.dp).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val channel = ui.selected
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)).background(Color.Black).testTag("channel-preview")) {
                if (channel != null && previewActive && prefs.previewPlayerEnabled && ui.previewTarget != null) {
                    PreviewSurface(target = ui.previewTarget!!)
                } else {
                    RemoteImage(channel?.logoUrl, channel?.name, Modifier.matchParentSize(), ContentScale.Fit)
                    Text(
                        if (channel == null) "Selecione um canal para pré-visualizar" else "Pré-visualização desligada (Interface)",
                        color = Color.White, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
                    )
                }
                if (channel != null) {
                    Row(
                        Modifier.align(Alignment.TopEnd).padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (!previewActive || !prefs.previewPlayerEnabled) {
                            Text("AO VIVO", modifier = Modifier.background(Color(0xB3E23B3B), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp), color = Color.White, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            if (channel != null) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(channel.name, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    ui.selectedNow?.let { now ->
                        Text("Agora · ${fmtTime(now.startMs)}–${fmtTime(now.stopMs)}  ${now.title}", style = MaterialTheme.typography.bodyMedium, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        TvAction("Assistir", { onPlay(PlayRequest(vm.playbackTarget(channel), channel.name, channel.favKey, "channel")) }, primary = true, compact = true, modifier = Modifier.testTag("channel-watch"))
                        TvAction("Mais de ${ui.upcoming.size + 1} programas", {}, compact = true, enabled = false)
                    }
                }
            }
            Text("GRADE DO CANAL", style = MaterialTheme.typography.labelMedium, color = TvColors.Muted)
            LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
                items(ui.upcoming, key = { "up-$it" }) { programme ->
                    ProgrammeGridCell(programme, highlight = ui.selectedNow?.startMs == programme.startMs)
                }
                if (ui.upcoming.isEmpty() && channel != null) {
                    item {
                        EmptyState("Sem grade para este canal", "O EPG da fonte não casou com este canal. Confira Configurações → EPG (vinculação automática).", Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewSurface(target: com.smarted.tv.core.model.PlaybackTarget) {
    val player = rememberVideoPlayer(target, muted = true)
    VideoSurface(player, Modifier.fillMaxSize(), showController = false)
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x22FFFFFF), Color(0x33000000)))))
}

@Composable
private fun ChannelRowItem(
    row: ChannelRow,
    selected: Boolean,
    onSelect: () -> Unit,
    onPlay: () -> Unit,
    onFavorite: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier.fillMaxWidth().testTag("channel-${row.channel.id}")
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onSelect()
            }
            .whiteFocus(focused, selected, 14.dp)
            .padding(2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) Color(0x2BFFFFFF) else if (selected) Color(0x18FFFFFF) else Color(0x0DFFFFFF))
            .clickable(interactionSource = interaction, indication = null, onClick = onPlay)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        row.channel.number?.let {
            Box(Modifier.width(34.dp), contentAlignment = Alignment.CenterStart) {
                Text(it.toString(), color = TvColors.Muted, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
        Box(Modifier.width(if (LocalCompact.current) 84.dp else 104.dp).height(if (LocalCompact.current) 40.dp else 50.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF0E0F12))) {
            RemoteImage(row.channel.logoUrl, row.channel.name, Modifier.matchParentSize(), ContentScale.Fit)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(row.channel.name, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val now = row.now
            if (now != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Agora", color = TvColors.Muted, style = MaterialTheme.typography.labelMedium)
                    Text(now.title, color = Color.White, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("${fmtTime(now.startMs)}", color = TvColors.Muted, style = MaterialTheme.typography.labelMedium)
                }
                Box(Modifier.fillMaxWidth().height(3.dp).background(Color(0x33FFFFFF), RoundedCornerShape(2.dp))) {
                    Box(Modifier.fillMaxWidth(row.progressPercent / 100f).height(3.dp).background(Color.White, RoundedCornerShape(2.dp)))
                }
                row.next?.let { next ->
                    Text("A seguir · ${fmtTime(next.startMs)} ${next.title}", color = TvColors.Muted, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else {
                Text(if (row.channel.groupName.isNotBlank()) row.channel.groupName else "Sem programação sincronizada", color = TvColors.Muted, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        HeartToggle(row.isFavorite, onFavorite, label = "channel-${row.channel.id}")
    }
}

@Composable
private fun ProgrammeGridCell(programme: Programme, highlight: Boolean) {
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier.fillMaxWidth().testTag("epg-${programme.startMs}").onFocusChanged { focused = it.isFocused }
            .whiteFocus(focused, highlight, 10.dp).padding(1.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (highlight) Color(0x1AFFFFFF) else Color(0x0AFFFFFF))
            .clickable(interactionSource = interaction, indication = null, onClick = {})
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (highlight) Icons.Default.PlayArrow else Icons.Default.Star, null, Modifier.size(13.dp), tint = if (highlight) Color.White else TvColors.Muted)
        Text("${fmtTime(programme.startMs)}–${fmtTime(programme.stopMs)}", color = Color.White, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        Text(programme.title, color = if (highlight) Color.White else TvColors.Muted, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

private val timeFormat = SimpleDateFormat("HH:mm", Locale("pt", "BR"))
private fun fmtTime(ms: Long): String = timeFormat.format(Date(ms))

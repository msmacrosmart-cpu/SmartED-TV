package com.smarted.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.smarted.tv.ui.components.keepFocusInView
import com.smarted.tv.ui.components.RemoteImage
import com.smarted.tv.ui.components.SearchField
import com.smarted.tv.ui.components.TvAction
import com.smarted.tv.ui.components.SectionHead
import com.smarted.tv.ui.rememberChannelsModel
import com.smarted.tv.ui.state.AppViewModel
import com.smarted.tv.ui.theme.LocalCompact
import com.smarted.tv.ui.theme.TvColors
import com.smarted.tv.ui.theme.dpx
import com.smarted.tv.ui.theme.spx
import com.smarted.tv.ui.theme.whiteFocus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Canais ao vivo — layout EXATO em 3 colunas (padrão Canais do app de referência):
 * 1) Categorias + Favoritos · 2) Canais com logo + EPG sincronizado · 3) Player de
 * pré-visualização + grade dos próximos programas. Escala de design dpx(); foco branco puro.
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
    LaunchedEffect(listState) {
        snapshotFlow { (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) to listState.layoutInfo.totalItemsCount }
            .collect { pair -> val (last, total) = pair; if (total > 0 && last >= total - 14) vm.loadMore() }
    }

    Row(
        Modifier.fillMaxSize().padding(horizontal = dpx(24), vertical = dpx(6)),
        horizontalArrangement = Arrangement.spacedBy(dpx(14)),
    ) {
        // ——— Coluna 1: categorias + favoritos ———
        GlassColumn(Modifier.weight(0.30f)) {
            SectionHead("Categorias + Favoritos")
            SearchField(
                value = ui.categorySearch,
                onChange = vm::setCategorySearch,
                hint = "Buscar categoria…",
                modifier = Modifier.fillMaxWidth(),
                tag = "channel-category-search",
            )
            LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(dpx(8))) {
                item {
                    CategoryChip(BrowseQuery.FAVORITES, ui.totalRows.takeIf { ui.query.isFavorites }, ui.query.isFavorites, { vm.setQuery { q -> q.copy(category = if (q.isFavorites) BrowseQuery.ALL else BrowseQuery.FAVORITES) } }, icon = Icons.Default.Star)
                }
                item {
                    CategoryChip(BrowseQuery.ALL, if (ui.query.isAll) ui.totalRows else null, ui.query.isAll, { vm.setQuery { q -> q.copy(category = BrowseQuery.ALL) } })
                }
                itemsIndexed(ui.groups.filter { it.first.isNotBlank() && com.smarted.tv.core.text.TextNormalizer.searchMatches(ui.categorySearch, it.first) }, key = { _, g -> "g-${g.first}" }) { _, group ->
                    CategoryChip(group.first, group.second, ui.query.category == group.first, { vm.setQuery { q -> q.copy(category = group.first) } })
                }
                if (ui.groups.isEmpty()) {
                    item {
                        EmptyState("Sem categorias", "Adicione e atualize uma fonte em Configurações → Fontes.", Modifier.fillMaxWidth(), action = { TvAction("Abrir Configurações", onOpenSettings, compact = true) })
                    }
                }
            }
        }

        // ——— Coluna 2: canais com logo + EPG (busca com escopo: só canais) ———
        GlassColumn(Modifier.weight(0.36f)) {
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
                verticalArrangement = Arrangement.spacedBy(dpx(10)),
                contentPadding = PaddingValues(bottom = dpx(12)),
            ) {
                itemsIndexed(ui.rows, key = { _, row -> "row-${row.channel.id}" }) { _, row ->
                    ChannelCell(
                        row = row,
                        selected = ui.selected?.id == row.channel.id,
                        onSelect = { vm.select(row.channel) },
                        onPlay = { onPlay(PlayRequest(vm.playbackTarget(row.channel), row.channel.name, row.channel.favKey, "channel")) },
                        onFavorite = { vm.toggleFavorite(row.channel) },
                    )
                }
                if (ui.loading && ui.rows.isNotEmpty()) {
                    item { Text("Carregando mais canais…", color = TvColors.Muted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(dpx(10))) }
                }
            }
        }

        // ——— Coluna 3: player de pré-visualização + grade EPG ———
        GlassColumn(Modifier.weight(0.36f)) {
            val channel = ui.selected
            Box(
                Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(dpx(16)))
                    .background(Color.Black).testTag("channel-preview"),
            ) {
                if (channel != null && previewActive && prefs.previewPlayerEnabled && ui.previewTarget != null) {
                    PreviewSurface(target = ui.previewTarget!!)
                } else {
                    RemoteImage(channel?.logoUrl, channel?.name, Modifier.matchParentSize(), ContentScale.Fit)
                    Text(
                        if (channel == null) "Selecione um canal para pré-visualizar" else "Pré-visualização desligada (Interface)",
                        color = Color.White, fontSize = spx(17),
                        modifier = Modifier.align(Alignment.BottomCenter).padding(dpx(12)),
                    )
                }
                if (channel != null) {
                    Row(Modifier.align(Alignment.TopEnd).padding(dpx(10)), horizontalArrangement = Arrangement.spacedBy(dpx(10))) {
                        if (!previewActive || !prefs.previewPlayerEnabled) {
                            Text("AO VIVO", modifier = Modifier.background(Color(0xB3E23B3B), RoundedCornerShape(dpx(8))).padding(horizontal = dpx(12), vertical = dpx(4)), color = Color.White, fontSize = spx(14))
                        }
                    }
                }
            }
            if (channel != null) {
                Column(verticalArrangement = Arrangement.spacedBy(dpx(6))) {
                    Text(channel.name, fontSize = spx(24), fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    ui.selectedNow?.let { now ->
                        Text("Agora · ${fmtTime(now.startMs)}–${fmtTime(now.stopMs)}  ${now.title}", color = Color.White, fontSize = spx(17), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(dpx(12)), verticalAlignment = Alignment.CenterVertically) {
                        TvAction("Assistir", { onPlay(PlayRequest(vm.playbackTarget(channel), channel.name, channel.favKey, "channel")) }, primary = true, compact = true, modifier = Modifier.testTag("channel-watch"))
                    }
                }
            }
            SectionHead("Grade do canal · próximos programas")
            LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(dpx(8)), contentPadding = PaddingValues(bottom = dpx(10))) {
                itemsIndexed(ui.upcoming, key = { _, p -> "p-${p.startMs}" }) { _, programme ->
                    ProgrammeCell(programme, highlight = programme.startMs <= System.currentTimeMillis())
                }
                if (ui.upcoming.isEmpty()) {
                    item { Text("Sem programação para este canal na janela atual — sincronize o EPG em Configurações.", color = TvColors.Muted, fontSize = spx(15), modifier = Modifier.padding(dpx(8))) }
                }
            }
        }
    }
}

@Composable
private fun GlassColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxHeight().clip(RoundedCornerShape(dpx(26)))
            .background(Brush.verticalGradient(listOf(Color(0x14FFFFFF), Color(0x08FFFFFF))))
            .border(1.dp, TvColors.Outline, RoundedCornerShape(dpx(26)))
            .padding(dpx(18)),
        verticalArrangement = Arrangement.spacedBy(dpx(12)),
        content = content,
    )
}

@Composable
private fun PreviewSurface(target: com.smarted.tv.core.model.PlaybackTarget) {
    val player = rememberVideoPlayer(target, muted = true)
    VideoSurface(player, Modifier.fillMaxSize(), showController = false)
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x22FFFFFF), Color(0x33000000)))))
}

/** Linha de canal estilo Smarters: número · logo 16:9 · nome · EPG atual com barra de progresso. */
@Composable
private fun ChannelCell(
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
            .keepFocusInView()
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onSelect()
            }
            .whiteFocus(focused, selected, dpx(22))
            .padding(dpx(3))
            .clip(RoundedCornerShape(dpx(16)))
            .background(if (selected) Color(0x26FFFFFF) else Color(0x0DFFFFFF))
            .clickable(interactionSource = interaction, indication = null, onClick = onPlay)
            .padding(horizontal = dpx(14), vertical = dpx(10)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dpx(14)),
    ) {
        row.channel.number?.let {
            Box(Modifier.width(dpx(64)), contentAlignment = Alignment.CenterStart) {
                Text(it.toString(), color = TvColors.Muted, fontSize = spx(16))
            }
        }
        Box(
            Modifier.width(dpx(150)).height(dpx(86)).clip(RoundedCornerShape(dpx(10)))
                .background(Color(0xFF0C0D0F)).border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(dpx(10))),
        ) {
            RemoteImage(row.channel.logoUrl, row.channel.name, Modifier.matchParentSize(), ContentScale.Fit)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(dpx(4))) {
            Text(row.channel.name, color = Color.White, fontSize = spx(21), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val now = row.now
            if (now != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dpx(10))) {
                    Text("Agora", color = TvColors.Muted, fontSize = spx(14))
                    Text(now.title, color = Color.White, fontSize = spx(17), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(fmtTime(now.startMs), color = TvColors.Muted, fontSize = spx(14))
                }
                Box(Modifier.fillMaxWidth().height(dpx(5)).background(Color(0x33FFFFFF), RoundedCornerShape(dpx(4)))) {
                    Box(Modifier.fillMaxWidth(row.progressPercent / 100f).height(dpx(5)).background(Color.White, RoundedCornerShape(dpx(4))))
                }
                row.next?.let { next ->
                    Text("A seguir · ${fmtTime(next.startMs)} ${next.title}", color = TvColors.Muted, fontSize = spx(15), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else {
                Text(if (row.channel.groupName.isNotBlank()) row.channel.groupName else "Sem programação sincronizada", color = TvColors.Muted, fontSize = spx(16), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        HeartToggle(row.isFavorite, onFavorite, label = "channel-${row.channel.id}")
    }
}

@Composable
private fun ProgrammeCell(programme: Programme, highlight: Boolean) {
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier.fillMaxWidth().testTag("epg-${programme.startMs}").onFocusChanged { focused = it.isFocused }
            .whiteFocus(focused, highlight, dpx(16))
            .padding(dpx(2))
            .clip(RoundedCornerShape(dpx(14)))
            .background(if (highlight) Color(0x1AFFFFFF) else Color(0x0AFFFFFF))
            .clickable(interactionSource = interaction, indication = null, onClick = {})
            .padding(horizontal = dpx(14), vertical = dpx(10)),
        horizontalArrangement = Arrangement.spacedBy(dpx(12)), verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (highlight) Icons.Default.PlayArrow else Icons.Default.Star, null, Modifier.size(dpx(16)), tint = if (highlight) Color.White else TvColors.Muted)
        Text("${fmtTime(programme.startMs)}–${fmtTime(programme.stopMs)}", color = Color.White, fontSize = spx(16), maxLines = 1)
        Text(programme.title, color = if (highlight) Color.White else TvColors.Muted, fontSize = spx(17), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

private val timeFormat = SimpleDateFormat("HH:mm", Locale("pt", "BR"))
private fun fmtTime(ms: Long): String = timeFormat.format(Date(ms))

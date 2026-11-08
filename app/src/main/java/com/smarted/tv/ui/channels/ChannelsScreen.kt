package com.smarted.tv.ui.channels

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.smarted.tv.core.data.AppGraph
import com.smarted.tv.core.model.*
import com.smarted.tv.core.util.asClock
import com.smarted.tv.core.util.rememberNowMs
import com.smarted.tv.ui.common.vmFactory
import com.smarted.tv.ui.components.*
import com.smarted.tv.ui.player.LivePreviewPlayer
import com.smarted.tv.ui.theme.LocalDeviceMode
import com.smarted.tv.ui.theme.LocalDimens
import com.smarted.tv.ui.theme.Smoke

@Composable
fun ChannelsScreen(onPlay: (PlaybackRequest) -> Unit) {
    val vm: ChannelsViewModel = viewModel(factory = vmFactory { ChannelsViewModel(AppGraph.content) })
    val ui by vm.ui.collectAsState()
    val focusedId by vm.focusedId.collectAsState()
    val upcoming by vm.upcoming.collectAsState()
    LaunchedEffect(ui.channels, focusedId) {
        if (focusedId == null || ui.channels.none { it.id == focusedId }) {
            ui.channels.firstOrNull()?.let { vm.focus(it.id) }
        }
    }
    val now by rememberNowMs()
    val mode = LocalDeviceMode.current

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // TV = 3 colunas. Tablet em paisagem também usa 3 colunas; celular = layout compacto.
        val wide = mode == DeviceMode.TV || maxWidth >= 840.dp
        if (wide) ChannelsWide(vm, ui, focusedId, now, upcoming, onPlay) else ChannelsCompact(vm, ui, now, onPlay)
    }
}

private fun categoryEntries(cats: List<Category>) = cats.map {
    CategoryEntry(
        it.id, it.name,
        when (it.id) {
            CAT_FAVORITES -> Icons.Rounded.Favorite
            CAT_ALL -> Icons.Rounded.Apps
            else -> null
        },
    )
}

/** Coluna 1: categorias + favoritos | Coluna 2: canais com logo + EPG | Coluna 3: preview + grade EPG. */
@Composable
@Composable
private fun ChannelsWide(
    vm: ChannelsViewModel, ui: ChannelsViewModel.Ui, focusedId: String?, now: Long,
    upcoming: List<EpgProgram>, onPlay: (PlaybackRequest) -> Unit,
) {
    val d = LocalDimens.current
    val focused = ui.channels.firstOrNull { it.id == focusedId } ?: ui.channels.firstOrNull()

    Row(
        Modifier.fillMaxSize().padding(horizontal = d.screenH, vertical = d.screenV),
        horizontalArrangement = Arrangement.spacedBy(d.gutter),
    ) {
        CategoryList(
            categoryEntries(ui.categories), ui.selectedId, vm::selectCategory,
            Modifier.weight(0.22f).fillMaxHeight(),
        )
        Column(Modifier.weight(0.40f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(d.gutter / 2)) {
            GlassTextField(ui.query, vm::setQuery, "Buscar canais")
            ChannelList(
                ui, now, Modifier.weight(1f).fillMaxWidth(),
                onFocus = vm::focus,
                onPlay = { onPlay(it.toRequest()) },
                onToggleFav = { vm.toggleFavorite(it.id) },
            )
        }
        ChannelPreview(
            focused, focused != null && focused.id in ui.favoriteIds, now, upcoming,
            Modifier.weight(0.38f).fillMaxHeight(),
            onFullscreen = { c -> onPlay(c.toRequest()) },
            onToggleFav = { c -> vm.toggleFavorite(c.id) },
        )
    }
}

@Composable
private fun ChannelsCompact(
    vm: ChannelsViewModel, ui: ChannelsViewModel.Ui, now: Long, onPlay: (PlaybackRequest) -> Unit,
) {
    val d = LocalDimens.current
    Column(
        Modifier.fillMaxSize().padding(horizontal = d.screenH, vertical = d.screenV),
        verticalArrangement = Arrangement.spacedBy(d.gutter / 2),
    ) {
        GlassTextField(ui.query, vm::setQuery, "Buscar canais")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
            items(ui.categories, key = { it.id }) {
                GlassChip(it.name, ui.selectedId == it.id, { vm.selectCategory(it.id) },
                    icon = if (it.id == CAT_FAVORITES) Icons.Rounded.Favorite else null)
            }
        }
        ChannelList(
            ui, now, Modifier.weight(1f).fillMaxWidth(),
            onFocus = { vm.focus(it.id) }, onPlay = { onPlay(it.toRequest()) },
            onToggleFav = { vm.toggleFavorite(it.id) },
        )
    }
}

@Composable
private fun ChannelList(
    ui: ChannelsViewModel.Ui, now: Long, modifier: Modifier,
    onFocus: (String) -> Unit, onPlay: (Channel) -> Unit, onToggleFav: (Channel) -> Unit,
) {
    val d = LocalDimens.current
    Box(modifier.glass()) {
        if (ui.loaded && ui.channels.isEmpty()) {
            Text(
                if (ui.query.isNotBlank()) "Nenhum canal encontrado" else "Nenhum canal nesta categoria",
                Modifier.align(Alignment.Center), fontSize = d.textBody, color = Smoke.TextTertiary,
            )
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(ui.channels, key = { it.id }) { ch ->
                ChannelRow(
                    ch, ch.id in ui.favoriteIds, now,
                    onFocused = { onFocus(ch.id) }, onClick = { onPlay(ch) }, onLongClick = { onToggleFav(ch) },
                )
            }
        }
    }
}

@Composable
private fun ChannelRow(
    ch: Channel, isFav: Boolean, now: Long,
    onFocused: () -> Unit, onClick: () -> Unit, onLongClick: () -> Unit,
) {
    val d = LocalDimens.current
    val current = ch.current(now)
    GlassClickable(
        onClick = onClick, onFocused = onFocused, onLongClick = onLongClick,
        modifier = Modifier.fillMaxWidth().height(d.rowHeight),
        contentPadding = PaddingValues(horizontal = 10.dp),
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                String.format("%03d", ch.number), fontSize = d.textCaption,
                color = Smoke.TextTertiary, modifier = Modifier.width(34.dp),
            )
            ChannelLogo(ch.logoUrl, ch.name, Modifier.size(44.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(ch.name, fontSize = d.textBody, fontWeight = FontWeight.SemiBold,
                    color = Smoke.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(current?.title ?: "Sem programação", fontSize = d.textCaption,
                    color = Smoke.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (current != null) {
                    LinearProgressIndicator(
                        progress = { current.progress(now) },
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = Smoke.FocusWhite, trackColor = Color.White.copy(alpha = 0.18f),
                    )
                }
            }
            if (isFav) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Rounded.Favorite, "Favorito", Modifier.size(18.dp), tint = Smoke.TextPrimary)
            }
        }
    }
}

@Composable
private fun ChannelLogo(url: String?, name: String, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.08f))) {
        Text(
            name.take(2).uppercase(), Modifier.align(Alignment.Center),
            fontSize = LocalDimens.current.textCaption, fontWeight = FontWeight.Bold, color = Smoke.TextSecondary,
        )
        if (url != null) {
            AsyncImage(url, name, Modifier.fillMaxSize().padding(4.dp), contentScale = ContentScale.Fit)
        }
    }
}

@Composable
private fun ChannelPreview(
    channel: Channel?, isFav: Boolean, now: Long, upcoming: List<EpgProgram>, modifier: Modifier,
    onFullscreen: (Channel) -> Unit, onToggleFav: (Channel) -> Unit,
) {
    val d = LocalDimens.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(d.gutter / 2)) {
        LivePreviewPlayer(
            channel?.streamUrl, channel?.headers ?: emptyMap(),
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).glass(),
        )
        if (channel != null) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(channel.name, fontSize = d.textTitle, fontWeight = FontWeight.Bold,
                    color = Smoke.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(channel.current(now)?.title ?: "Sem programação", fontSize = d.textBody,
                    color = Smoke.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassButton("Tela cheia", Icons.Rounded.Fullscreen, { onFullscreen(channel) })
                GlassButton(
                    if (isFav) "Remover" else "Favoritar",
                    if (isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    { onToggleFav(channel) },
                )
            }
        }
        // Grade EPG dos próximos programas, logo abaixo do player.
        GlassPanel(Modifier.weight(1f).fillMaxWidth(), padding = 10.dp) {
            val grid = (upcoming.ifEmpty { channel?.upcoming(now).orEmpty() }).take(10)
            if (upcoming.isEmpty()) {
                Text("Sem guia de programação", Modifier.align(Alignment.Center),
                    fontSize = d.textCaption, color = Smoke.TextTertiary)
            }
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(grid, key = { it.startMs + it.title.hashCode() }) { p ->
                    val live = p.isLive(now)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${p.startMs.asClock()} – ${p.endMs.asClock()}", fontSize = d.textCaption,
                                color = Smoke.TextSecondary)
                            if (live) {
                                Spacer(Modifier.width(8.dp))
                                Text("AGORA", fontSize = d.textCaption, fontWeight = FontWeight.Bold, color = Smoke.TextPrimary,
                                    modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.18f))
                                        .padding(horizontal = 6.dp))
                            }
                        }
                        Text(p.title, fontSize = d.textBody, fontWeight = if (live) FontWeight.Bold else FontWeight.Normal,
                            color = Smoke.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (live) {
                            LinearProgressIndicator(
                                progress = { p.progress(now) },
                                modifier = Modifier.fillMaxWidth().height(2.dp),
                                color = Smoke.FocusWhite, trackColor = Color.White.copy(alpha = 0.18f),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Reprodução de canal: cabeçalhos da fonte + flag live (sem salvar progresso). */
private fun Channel.toRequest() =
    PlaybackRequest(name, streamUrl, headers, favKey, "channel", isLive = true)

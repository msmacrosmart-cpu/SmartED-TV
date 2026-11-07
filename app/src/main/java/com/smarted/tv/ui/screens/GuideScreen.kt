package com.smarted.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smarted.tv.core.model.EpgProgram
import com.smarted.tv.core.model.MediaItem
import com.smarted.tv.core.util.TextUtils
import com.smarted.tv.ui.components.Artwork
import com.smarted.tv.ui.components.EdPill
import com.smarted.tv.ui.components.EmptyState
import com.smarted.tv.ui.components.ScreenTitle
import com.smarted.tv.ui.components.ThinProgress
import com.smarted.tv.ui.components.edBorder
import com.smarted.tv.ui.components.edInteraction
import com.smarted.tv.ui.state.GuideViewModel
import com.smarted.tv.ui.theme.Ed

/**
 * Guia de programação (EPG) — canais à esquerda, linha do tempo à direita.
 * Mostra claramente o status do XMLTV (inclusive quando cai no fallback).
 */
@Composable
fun GuideScreen(
    viewModel: GuideViewModel,
    onPlayChannel: (MediaItem) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val palette = Ed.palette
    val type = Ed.type

    LaunchedEffect(Unit) { viewModel.load() }

    Column(
        modifier = Modifier.fillMaxSize().background(palette.bg).padding(horizontal = 32.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScreenTitle(
                title = "Guia de programação",
                subtitle = state.status.takeIf { it.isNotBlank() },
                modifier = Modifier.weight(1f),
            )
            EdPill(label = "Recarregar EPG", onClick = { viewModel.forceReloadEpg() }, icon = com.smarted.tv.ui.components.EdIcons.refresh)
        }

        if (state.channels.isEmpty() && !state.loading) {
            EmptyState(
                title = "Sem dados de EPG",
                message = "Nenhum canal com tvg-id encontrado. Verifique se o XMLTV padrão " +
                    "(epg.best/brazil.xml) está acessível em Ajustes → EPG.",
                icon = com.smarted.tv.ui.components.EdIcons.guide,
            )
        } else {
            Row(Modifier.fillMaxSize()) {
                // coluna de canais
                LazyColumn(
                    modifier = Modifier.width(240.dp).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(state.channels, key = { it.id }) { channel ->
                        ChannelPickerRow(
                            channel = channel,
                            selected = state.selectedChannel?.id == channel.id,
                            onClick = { viewModel.selectChannel(channel) },
                        )
                    }
                }

                Column(
                    Modifier.weight(1f).fillMaxHeight().padding(start = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    state.selectedChannel?.let { channel ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                Modifier
                                    .width(96.dp)
                                    .height(54.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(palette.surface2),
                            ) {
                                Artwork(channel.poster, channel.title, Modifier.fillMaxSize())
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    channel.title,
                                    style = type.sectionTitle,
                                    color = palette.fg,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                channel.tvgId?.let {
                                    Text("tvg-id: $it", style = type.caption, color = palette.fg3)
                                }
                            }
                            EdPill(label = "Assistir", onClick = { onPlayChannel(channel) }, icon = com.smarted.tv.ui.components.EdIcons.play)
                        }
                    }

                    if (state.programs.isEmpty()) {
                        EmptyState(
                            title = "Sem programação para este canal",
                            message = "O guia não trouxe programas para o tvg-id deste canal. " +
                                "Isso é comum em canais sem correspondência no XMLTV.",
                        )
                    } else {
                        LazyColumn(
                            state = rememberLazyListState(),
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 32.dp),
                        ) {
                            items(state.programs, key = { "${it.channelId}-${it.startMs}" }) { program ->
                                ProgramRow(program = program, now = state.nowMs)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelPickerRow(channel: MediaItem, selected: Boolean, onClick: () -> Unit) {
    val palette = Ed.palette
    val type = Ed.type
    val interaction = edInteraction(onClick = onClick)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    interaction.isFocused -> palette.surface3
                    selected -> palette.surface2
                    else -> Color.Transparent
                },
            )
            .then(
                if (selected || interaction.isFocused) {
                    Modifier.edBorder(
                        color = if (selected) palette.accent else palette.accent.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp),
                        width = 2.dp,
                    )
                } else {
                    Modifier
                },
            )
            .then(interaction.modifier)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .width(48.dp)
                .height(30.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(palette.surface2),
        ) {
            Artwork(channel.poster, channel.title, Modifier.fillMaxSize(), androidx.compose.ui.layout.ContentScale.Fit)
        }
        Text(
            text = channel.title,
            style = type.caption.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
            color = if (selected) palette.accent else palette.fg2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ProgramRow(program: EpgProgram, now: Long) {
    val palette = Ed.palette
    val type = Ed.type
    val isNow = now in program.startMs until program.endMs
    val isPast = program.endMs < now

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isNow) palette.accentSoft else palette.surface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.width(96.dp)) {
            Text(
                text = TextUtils.formatTime(program.startMs),
                style = type.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = if (isNow) palette.accent else palette.fg2,
            )
            Text(
                text = TextUtils.formatTime(program.endMs),
                style = type.caption,
                color = palette.fg3,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = program.title,
                    style = type.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = if (isPast) palette.fg3 else palette.fg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isNow) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(palette.accent)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text("AGORA", style = type.label, color = palette.onAccent)
                    }
                }
            }
            program.description?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = TextUtils.ellipsize(it, 140),
                    style = type.caption,
                    color = palette.fg3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isNow) {
                ThinProgress(program.progressAt(now), Modifier.fillMaxWidth(0.4f))
            }
        }
        program.category?.let {
            Text(text = it, style = type.caption, color = palette.fg3, maxLines = 1)
        }
    }
}

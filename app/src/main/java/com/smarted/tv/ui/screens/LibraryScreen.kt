package com.smarted.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smarted.tv.core.model.MediaItem
import com.smarted.tv.core.model.WatchProgress
import com.smarted.tv.ui.components.EdIcons
import com.smarted.tv.ui.components.EdPill
import com.smarted.tv.ui.components.EmptyState
import com.smarted.tv.ui.components.PosterCard
import com.smarted.tv.ui.components.ScreenTitle
import com.smarted.tv.ui.components.SectionHeader
import com.smarted.tv.ui.components.WideCard
import com.smarted.tv.ui.state.HomeViewModel
import com.smarted.tv.ui.theme.Ed

/**
 * Minha lista: "Continuar assistindo" (com progresso) + favoritos.
 * Usa o [HomeViewModel] porque o estado da biblioteca já é observado por ele.
 */
@Composable
fun LibraryScreen(
    viewModel: HomeViewModel,
    mode: LibraryMode,
    onPlay: (MediaItem) -> Unit,
    onOpen: (MediaItem) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val palette = Ed.palette
    val type = Ed.type

    Column(
        modifier = Modifier.fillMaxSize().background(palette.bg).padding(horizontal = 32.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenTitle(
            title = if (mode == LibraryMode.CONTINUE) "Continuar assistindo" else "Minha lista",
            subtitle = if (mode == LibraryMode.CONTINUE) {
                "${state.continueWatching.size} em andamento"
            } else {
                "${state.favorites.size} favoritos"
            },
        )

        if (mode == LibraryMode.CONTINUE) {
            if (state.continueWatching.isEmpty()) {
                EmptyState(
                    title = "Nada em andamento",
                    message = "Comece a assistir a um filme ou série e ele aparece aqui, pronto para retomar.",
                    icon = EdIcons.play,
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(268.dp),
                    contentPadding = PaddingValues(bottom = 40.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    items(state.continueWatching, key = { it.ref }) { progress ->
                        ContinueTile(
                            progress = progress,
                            onPlay = { onPlay(progress.asItem()) },
                            onOpen = { onOpen(progress.asItem()) },
                        )
                    }
                }
            }
        } else {
            if (state.favorites.isEmpty()) {
                EmptyState(
                    title = "Sua lista está vazia",
                    message = "Abra um título e toque no coração para adicioná-lo à sua lista.",
                    icon = EdIcons.favoriteBorder,
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(152.dp),
                    contentPadding = PaddingValues(bottom = 40.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    items(state.favorites, key = { it.ref.key }) { item ->
                        PosterCard(
                            item = item,
                            onClick = { onOpen(item) },
                            modifier = Modifier.fillMaxWidth(),
                            width = null,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContinueTile(
    progress: WatchProgress,
    onPlay: () -> Unit,
    onOpen: () -> Unit,
) {
    val palette = Ed.palette
    val type = Ed.type
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        WideCard(
            item = progress.asItem(),
            onClick = onPlay,
            progress = progress.fraction,
            overline = progress.episodeLabel(),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "${(progress.fraction * 100).toInt()}% · ${remainingLabel(progress)}",
                style = type.caption,
                color = palette.fg3,
                modifier = Modifier.weight(1f),
            )
            EdPill(label = "Retomar", onClick = onPlay, icon = EdIcons.play)
            EdPill(label = "Ficha", onClick = onOpen)
        }
    }
}

private fun remainingLabel(progress: WatchProgress): String {
    val leftMs = (progress.durationMs - progress.positionMs).coerceAtLeast(0L)
    val minutes = (leftMs / 60_000L)
    return when {
        minutes <= 0 -> "quase no fim"
        minutes < 60 -> "faltam ${minutes}min"
        else -> "faltam ${minutes / 60}h ${minutes % 60}min"
    }
}

private fun WatchProgress.episodeLabel(): String? =
    if (season != null && episode != null) "T${season}E$episode" else null

private fun WatchProgress.asItem(): MediaItem = MediaItem(
    id = itemId.ifBlank { ref },
    addonId = addonId,
    kind = kind,
    title = title,
    subtitle = episodeLabel(),
    poster = poster,
    durationSec = durationMs / 1000L,
    number = episode,
)

enum class LibraryMode { CONTINUE, FAVORITES }

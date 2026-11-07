package com.smarted.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smarted.tv.core.model.MediaItem
import com.smarted.tv.ui.components.EdChip
import com.smarted.tv.ui.components.EdIcons
import com.smarted.tv.ui.components.EmptyState
import com.smarted.tv.ui.components.PosterCard
import com.smarted.tv.ui.components.SearchField
import com.smarted.tv.ui.components.ScreenTitle
import com.smarted.tv.ui.state.SearchViewModel
import com.smarted.tv.ui.theme.Ed

/**
 * Busca unificada: filtra canais, filmes e séries do catálogo indexado,
 * com histórico persistido e atalhos de sugestão.
 */
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onOpen: (MediaItem) -> Unit,
    onPlay: (MediaItem) -> Unit,
    searchFocusRequester: FocusRequester,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val palette = Ed.palette
    val type = Ed.type

    LaunchedEffect(Unit) { runCatching { searchFocusRequester.requestFocus() } }

    Column(
        modifier = Modifier.fillMaxSize().background(palette.bg).padding(horizontal = 32.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenTitle(
            title = "Buscar",
            subtitle = if (state.results.isNotEmpty()) "${state.results.size} resultados" else null,
        )
        SearchField(
            query = state.query,
            onQueryChange = {
                viewModel.setQuery(it)
                if (it.length > 2) viewModel.submit(it)
            },
            placeholder = "Buscar canais, filmes e séries",
            modifier = Modifier.fillMaxWidth(0.66f),
            focusRequester = searchFocusRequester,
        )

        if (state.history.isNotEmpty() && state.query.isBlank()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.history.take(10)) { term ->
                    EdChip(label = term, selected = false, onClick = { viewModel.setQuery(term) })
                }
            }
        }

        when {
            state.loading -> EmptyState(
                title = "Buscando…",
                message = "Consultando o catálogo indexado do Brazuca Play.",
                icon = EdIcons.search,
            )
            state.query.isBlank() -> EmptyState(
                title = "Digite para buscar",
                message = "A busca cobre todos os canais, filmes e séries das fontes ativas.",
                icon = EdIcons.search,
            )
            state.results.isEmpty() -> EmptyState(
                title = "Nenhum resultado",
                message = "Tente outro termo, sem acentos ou com menos palavras.",
                icon = EdIcons.search,
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(152.dp),
                contentPadding = PaddingValues(bottom = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                androidx.compose.foundation.lazy.grid.items(state.results, key = { it.id }) { item ->
                    PosterCard(
                        item = item,
                        onClick = {
                            if (item.streamUrl != null) onPlay(item) else onOpen(item)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        width = null,
                    )
                }
            }
        }
    }
}

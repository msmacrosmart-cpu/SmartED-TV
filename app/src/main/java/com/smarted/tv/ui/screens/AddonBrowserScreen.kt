package com.smarted.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smarted.tv.core.addons.AddonBrowser
import com.smarted.tv.core.addons.BrowserEntry
import com.smarted.tv.core.addons.RepositoryFolder
import com.smarted.tv.core.model.MediaKind
import com.smarted.tv.ui.components.AccentDot
import com.smarted.tv.ui.components.Artwork
import com.smarted.tv.ui.components.EdBadge
import com.smarted.tv.ui.components.EdIcons
import com.smarted.tv.ui.components.EdPrimaryButton
import com.smarted.tv.ui.components.EdSecondaryButton
import com.smarted.tv.ui.components.EmptyState
import com.smarted.tv.ui.components.ScreenTitle
import com.smarted.tv.ui.components.edFocusableSurface
import com.smarted.tv.ui.components.edInteraction
import com.smarted.tv.ui.state.AddonsViewModel
import com.smarted.tv.ui.theme.Ed

/**
 * Navegador de repositório no molde do Kodi.
 *
 * Primeiro nível: as pastas principais do repositório (**TV ao Vivo**, **Filmes**,
 * **Séries**). Segundo nível: os itens da pasta, com pôster, título e metadados.
 * Em cada nível o usuário conecta/desconecta a pasta da aba correspondente do
 * app — é o vínculo "diretório do repositório → aba".
 *
 * Visual: dark mode com superfícies de vidro e foco/seleção/bordas em branco
 * puro (#FFFFFF), o mesmo padrão do resto do app.
 */
@Composable
fun AddonBrowserScreen(
    viewModel: AddonsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val palette = Ed.palette
    val type = Ed.type
    val repo = state.browserRepository
    val openKind = state.browserKind

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.bg)
            .padding(horizontal = 32.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenTitle(
            title = if (openKind == null) "Repositório ${repo?.name ?: ""}".trim() else AddonBrowser.titleFor(openKind),
            subtitle = when {
                openKind != null -> AddonBrowser.descriptionFor(openKind) + " · ${state.browserEntries.size} itens"
                repo == null -> "Nenhum repositório ativo"
                else -> repo.url
            },
        )

        state.browserMessage?.let { message ->
            Text(message, style = type.caption, color = palette.fg2)
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                repo == null -> EmptyState(
                    title = "Sem repositório",
                    message = "Adicione um repositório em Addons → Repositório. O padrão é o Vikings.",
                    icon = EdIcons.cloud,
                )

                state.browserLoading -> Text("Lendo o repositório…", style = type.body, color = palette.fg2)

                openKind == null -> FolderList(
                    folders = state.browserFolders,
                    bound = state.browserBound,
                    onOpen = { viewModel.openFolder(it) },
                )

                else -> EntryList(
                    entries = state.browserEntries,
                    kind = openKind,
                    bound = openKind in state.browserBound,
                )
            }
        }

        // Barra de ações do nível atual.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (openKind != null) {
                val bound = openKind in state.browserBound
                EdPrimaryButton(
                    label = if (bound) "Conectado à aba" else "Conectar à aba ${AddonBrowser.titleFor(openKind)}",
                    icon = if (bound) EdIcons.check else EdIcons.add,
                    onClick = {
                        if (bound) {
                            repo?.let { viewModel.unbindFolder(it.id, openKind) }
                        } else {
                            viewModel.bindCurrentFolder()
                        }
                    },
                )
                EdSecondaryButton(label = "Pastas", onClick = { viewModel.closeFolder() }, icon = EdIcons.folder)
            } else {
                EdPrimaryButton(
                    label = "Sincronizar",
                    icon = EdIcons.refresh,
                    onClick = {
                        viewModel.sync()
                        viewModel.refreshBrowser()
                    },
                )
            }
            EdSecondaryButton(label = "Voltar", onClick = onBack, icon = EdIcons.back)
        }
    }
}

@Composable
private fun FolderList(
    folders: List<RepositoryFolder>,
    bound: List<MediaKind>,
    onOpen: (MediaKind) -> Unit,
) {
    val palette = Ed.palette
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(folders, key = { it.kind.name }) { folder ->
            FolderCard(folder = folder, bound = folder.kind in bound, onOpen = { onOpen(folder.kind) })
        }
    }
}

@Composable
private fun FolderCard(folder: RepositoryFolder, bound: Boolean, onOpen: () -> Unit) {
    val palette = Ed.palette
    val type = Ed.type
    val shape = RoundedCornerShape(18.dp)
    val interaction = edInteraction(onClick = onOpen)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp)
            .then(interaction.modifier)
            .edFocusableSurface(interaction.isFocused, shape, palette)
            .background(if (interaction.isFocused) palette.surface2 else palette.surface, shape)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Box(
            Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(palette.surface3),
            contentAlignment = Alignment.Center,
        ) {
            Icon(iconFor(folder.kind), contentDescription = null, tint = palette.accent, modifier = Modifier.size(28.dp))
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = folder.title,
                    style = type.sectionTitle,
                    color = palette.fg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (bound) EdBadge("NA ABA", palette.accent, filled = true)
            }
            Text(folder.description, style = type.caption, color = palette.fg3, maxLines = 1)
            Text(
                text = "${folder.count} item(ns) · ${folder.playableCount} com stream direto",
                style = type.label,
                color = palette.fg3,
            )
        }

        Icon(EdIcons.chevron, contentDescription = null, tint = palette.fg3, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun EntryList(entries: List<BrowserEntry>, kind: MediaKind, bound: Boolean) {
    val palette = Ed.palette
    val type = Ed.type

    if (entries.isEmpty()) {
        EmptyState(
            title = "Pasta vazia",
            message = "Este repositório não declarou itens para ${AddonBrowser.titleFor(kind)}. " +
                "Sincronize o repositório ou confira o índice de addons.",
            icon = iconFor(kind),
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(entries, key = { it.id }) { entry ->
            EntryCard(entry = entry, bound = bound)
        }
    }
}

@Composable
private fun EntryCard(entry: BrowserEntry, bound: Boolean) {
    val palette = Ed.palette
    val type = Ed.type
    val shape = RoundedCornerShape(14.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(shape)
            .background(palette.surface, shape)
            .then(if (bound) Modifier.border(1.dp, palette.accent.copy(alpha = 0.45f), shape) else Modifier)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(palette.surface2),
        ) {
            Artwork(entry.poster, entry.title, Modifier.fillMaxSize())
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = entry.title,
                    style = type.rowTitle.copy(fontWeight = FontWeight.SemiBold),
                    color = palette.fg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (entry.metaLine.isNotBlank()) {
                    Text(entry.metaLine, style = type.caption, color = palette.fg3)
                }
            }
            if (entry.summary.isNotBlank()) {
                Text(
                    text = entry.summary,
                    style = type.caption,
                    color = palette.fg3,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AccentDot(color = if (entry.playable) palette.ok else palette.fg3, size = 6.dp)
                Text(
                    text = if (entry.playable) "stream direto" else "catálogo (addon em Python não roda no Android)",
                    style = type.label,
                    color = palette.fg3,
                    maxLines = 1,
                )
            }
        }

        Box(Modifier.width(96.dp), contentAlignment = Alignment.CenterEnd) {
            EdBadge(kindLabel(entry.kind), palette.accent)
        }
    }
}

private fun iconFor(kind: MediaKind): ImageVector = when (kind) {
    MediaKind.LIVE -> EdIcons.live
    MediaKind.MOVIE -> EdIcons.movies
    MediaKind.SERIES -> EdIcons.series
    else -> EdIcons.folder
}

private fun kindLabel(kind: MediaKind): String = when (kind) {
    MediaKind.LIVE -> "CANAL"
    MediaKind.MOVIE -> "FILME"
    MediaKind.SERIES -> "SÉRIE"
    else -> "ITEM"
}

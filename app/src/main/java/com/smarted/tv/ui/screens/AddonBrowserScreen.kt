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
import com.smarted.tv.core.addons.AddonTree
import com.smarted.tv.core.addons.BrowserEntry
import com.smarted.tv.core.addons.Elementum
import com.smarted.tv.core.addons.RepositoryFolder
import com.smarted.tv.core.model.MediaKind
import com.smarted.tv.ui.components.AccentDot
import com.smarted.tv.ui.components.Artwork
import com.smarted.tv.ui.components.EdBadge
import com.smarted.tv.ui.components.EdIcons
import com.smarted.tv.ui.components.EdPrimaryButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.smarted.tv.ui.components.EdTextField
import com.smarted.tv.ui.components.EdSecondaryButton
import com.smarted.tv.ui.components.TargetToggle
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
    onPlay: (BrowserEntry) -> Unit,
    onPlayTree: (com.smarted.tv.core.addons.AddonTree.SavedItem) -> Unit,
    onOpenMyList: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val palette = Ed.palette
    val type = Ed.type
    val repo = state.browserRepository
    val openKind = state.browserKind
    val external = state.browserExternal
    // Termo de busca do Elementum (o daemon não tem catálogo: busca por texto).
    var query by remember { mutableStateOf(state.elementumQuery) }

    // Escolha da aba ao salvar: o usuário decide se o item vai para
    // Canais, Filmes ou Séries — é a mesma separação de acervo do app.
    state.saveDialogNode?.let { node ->
        SaveToShelfDialog(
            node = node,
            suggested = AddonTree.Shelf.suggest(node),
            onDismiss = { viewModel.dismissSaveDialog() },
            onPick = { shelf -> viewModel.saveToShelf(node, shelf) },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.bg)
            .padding(horizontal = 32.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenTitle(
            title = when {
                external -> state.treeTitle.ifBlank { "Elementum" }
                openKind != null -> AddonBrowser.titleFor(openKind)
                else -> "Repositório ${repo?.name ?: ""}".trim()
            },
            subtitle = when {
                external -> buildString {
                    append("Torrentes · daemon local ${Elementum.HOST}:${Elementum.DEFAULT_PORT}")
                    if (state.treeRoute.isNotBlank()) append(" · ${state.treeRoute}")
                }
                openKind != null -> AddonBrowser.descriptionFor(openKind) + " · ${state.browserEntries.size} itens"
                repo == null -> "Nenhum repositório ativo"
                else -> repo.url
            },
        )

        // Busca no Elementum: a árvore cobre pastas; o texto cobre o que não
        // estiver em destaque.
        if (external) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                EdTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Buscar filme ou série no Elementum",
                    modifier = Modifier.weight(1f),
                )
                EdPrimaryButton(
                    label = if (state.elementumBusy) "Buscando…" else "Buscar",
                    onClick = { viewModel.searchElementum(query) },
                    icon = EdIcons.search,
                )
                EdSecondaryButton(
                    label = "Raiz",
                    onClick = {
                        query = ""
                        viewModel.openElementum()
                    },
                    icon = EdIcons.refresh,
                )
            }
        }

        state.browserMessage?.let { message ->
            Text(message, style = type.caption, color = palette.fg2)
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                // ---------------- Elementum: árvore como no Kodi ----------------
                external -> TreeLevel(
                    state = state,
                    onOpen = { viewModel.openTreeFolder(it) },
                    onSave = { node -> viewModel.askSaveToShelf(node) },
                    onPlayNode = { node ->
                        onPlayTree(
                            AddonTree.toSaved(
                                node = node,
                                playUrl = AddonTree.playUrl(node),
                                folder = state.treeTitle,
                                folderRoute = state.treeRoute,
                            ),
                        )
                    },
                )

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
                    onPlay = onPlay,
                )
            }
        }

        // Barra de ações do nível atual.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (external) {
                EdSecondaryButton(
                    label = "Voltar pasta",
                    onClick = { viewModel.treeUp() },
                    icon = EdIcons.back,
                )
                EdSecondaryButton(
                    label = "Verificar daemon",
                    onClick = { viewModel.checkElementum() },
                    icon = EdIcons.refresh,
                )
                EdPrimaryButton(
                    label = "Minha lista (${state.savedCount})",
                    onClick = onOpenMyList,
                    icon = EdIcons.check,
                )
            } else if (openKind != null) {
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

/**
 * Um nível da árvore do Elementum.
 *
 * Pastas aparecem com seta e abrem no próximo nível; itens (torrentes) têm os
 * dois gestos que importam: **assistir** e **salvar na minha lista** — é assim
 * que o usuário monta a playlist dele entrando em Lançamentos, Ação, Aventura…
 */
@Composable
private fun TreeLevel(
    state: com.smarted.tv.ui.state.AddonsUiState,
    onOpen: (AddonTree.Node) -> Unit,
    onSave: (AddonTree.Node) -> Unit,
    onPlayNode: (AddonTree.Node) -> Unit,
) {
    val palette = Ed.palette
    val type = Ed.type

    if (state.browserLoading) {
        Text("Lendo o Elementum…", style = type.body, color = palette.fg2)
        return
    }

    if (state.treeNodes.isEmpty()) {
        EmptyState(
            title = if (state.elementum?.online == true) "Pasta vazia" else "Elementum desligado",
            message = state.browserMessage
                ?: "Abra o daemon do Elementum no aparelho e toque em Verificar daemon.",
            icon = EdIcons.folder,
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(state.treeNodes, key = { it.path.ifBlank { it.label } }) { node ->
            if (node.isFolder) {
                TreeFolderCard(node = node, onOpen = { onOpen(node) })
            } else {
                TreeItemCard(
                    node = node,
                    onPlay = { onPlayNode(node) },
                    onSave = { onSave(node) },
                )
            }
        }
    }
}

@Composable
private fun TreeFolderCard(node: AddonTree.Node, onOpen: () -> Unit) {
    val palette = Ed.palette
    val type = Ed.type
    val shape = RoundedCornerShape(14.dp)
    val style = AddonTree.styleOf(node)
    val icon = when (style) {
        AddonTree.FolderStyle.MOVIE -> EdIcons.movies
        AddonTree.FolderStyle.SERIES -> EdIcons.series
        AddonTree.FolderStyle.SEARCH -> EdIcons.search
        AddonTree.FolderStyle.GENRE -> EdIcons.folder
        else -> EdIcons.folder
    }

    val interaction = edInteraction(onClick = onOpen)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(74.dp)
            .then(interaction.modifier)
            .background(palette.surface, shape)
            .clip(shape)
            .edFocusableSurface(interaction.isFocused, shape, palette)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(palette.surface2),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = palette.accent, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = AddonTree.titleOf(node),
                style = type.rowTitle.copy(fontWeight = FontWeight.SemiBold),
                color = palette.fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (node.tagline.isNotBlank()) {
                Text(
                    text = node.tagline,
                    style = type.caption,
                    color = palette.fg3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        EdBadge("PASTA", palette.fg3)
        Icon(EdIcons.chevron, contentDescription = null, tint = palette.fg3, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun TreeItemCard(node: AddonTree.Node, onPlay: () -> Unit, onSave: () -> Unit) {
    val palette = Ed.palette
    val type = Ed.type
    val shape = RoundedCornerShape(14.dp)
    val meta = Elementum.parseTagline(node.tagline)
    val metaLine = listOf(
        meta.quality,
        if (meta.sizeBytes > 0) Elementum.humanSize(meta.sizeBytes) else "",
        if (meta.seeds > 0) "${meta.seeds} seeds" else "",
        node.label2,
    ).filter { it.isNotBlank() }.joinToString(" · ")

    val interaction = edInteraction(onClick = onPlay)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp)
            .then(interaction.modifier)
            .background(palette.surface, shape)
            .clip(shape)
            .edFocusableSurface(interaction.isFocused, shape, palette)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(58.dp).clip(RoundedCornerShape(12.dp)).background(palette.surface2),
            contentAlignment = Alignment.Center,
        ) {
            Artwork(node.icon.takeIf { it.startsWith("http") }, node.title, Modifier.fillMaxSize())
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = node.title,
                style = type.rowTitle.copy(fontWeight = FontWeight.SemiBold),
                color = palette.fg,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (metaLine.isNotBlank()) {
                Text(metaLine, style = type.caption, color = palette.fg3, maxLines = 1)
            } else if (node.plot.isNotBlank()) {
                Text(
                    text = node.plot,
                    style = type.caption,
                    color = palette.fg3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        EdSecondaryButton(label = "Assistir", onClick = onPlay, icon = EdIcons.play)
        Box(Modifier.size(8.dp))
        // Salvar é o gesto que monta a lista do usuário.
        EdSecondaryButton(label = "Salvar", onClick = onSave, icon = EdIcons.add)
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
private fun EntryList(
    entries: List<BrowserEntry>,
    kind: MediaKind,
    bound: Boolean,
    onPlay: (BrowserEntry) -> Unit,
) {
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
            EntryCard(entry = entry, bound = bound, onPlay = onPlay)
        }
    }
}

@Composable
private fun EntryCard(entry: BrowserEntry, bound: Boolean, onPlay: (BrowserEntry) -> Unit) {
    val palette = Ed.palette
    val type = Ed.type
    val shape = RoundedCornerShape(14.dp)
    // Item com stream direto abre no player; o resto é só catálogo.
    val interaction = edInteraction(onClick = { if (entry.playable) onPlay(entry) })

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .then(interaction.modifier)
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

/**
 * Escolha da aba ao salvar.
 *
 * O app mantém a separação de acervo: o usuário diz onde o item entra —
 * **Canais**, **Filmes** ou **Séries** — e a escolha manda (o item aparece na
 * aba escolhida, mesmo que o título sugira outra coisa). A sugestão vem do tipo
 * detectado no nó, mas é só o botão pré-selecionado.
 */
@Composable
private fun SaveToShelfDialog(
    node: AddonTree.Node,
    suggested: AddonTree.Shelf,
    onDismiss: () -> Unit,
    onPick: (AddonTree.Shelf) -> Unit,
) {
    val palette = Ed.palette
    val type = Ed.type
    var selected by remember(node) { mutableStateOf(suggested) }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.62f)
                .clip(RoundedCornerShape(18.dp))
                .background(palette.surface)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Salvar na minha lista", style = type.sectionTitle, color = palette.fg)
            Text(
                text = node.title,
                style = type.body,
                color = palette.fg2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Escolha onde este item vai aparecer:",
                style = type.caption,
                color = palette.fg3,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AddonTree.Shelf.ALL.forEach { shelf ->
                    TargetToggle(
                        kind = shelf.kind,
                        checked = selected == shelf,
                        onToggle = { checked -> if (checked) selected = shelf },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Text(
                text = when (selected) {
                    AddonTree.Shelf.CHANNELS -> "Vai para a playlist de canais ao vivo"
                    AddonTree.Shelf.MOVIES -> "Vai para a sua lista de filmes"
                    AddonTree.Shelf.SERIES -> "Vai para a sua lista de séries"
                },
                style = type.caption,
                color = palette.fg3,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EdPrimaryButton(
                    label = "Salvar em ${selected.label}",
                    icon = EdIcons.check,
                    onClick = { onPick(selected) },
                )
                EdSecondaryButton(label = "Cancelar", onClick = onDismiss)
            }
        }
    }
}

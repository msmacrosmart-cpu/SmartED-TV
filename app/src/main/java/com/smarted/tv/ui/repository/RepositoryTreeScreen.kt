package com.smarted.tv.ui.repository

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smarted.tv.core.data.AppGraph
import com.smarted.tv.core.model.PlaybackRequest
import com.smarted.tv.core.model.RepositoryNode
import com.smarted.tv.core.model.Tab
import com.smarted.tv.ui.common.vmFactory
import com.smarted.tv.ui.components.GlassButton
import com.smarted.tv.ui.components.GlassClickable
import com.smarted.tv.ui.components.GlassPanel
import com.smarted.tv.ui.components.glass
import com.smarted.tv.ui.theme.LocalDimens
import com.smarted.tv.ui.theme.Smoke

/**
 * Navegador de repositório no estilo Kodi (árvore de pastas), 100% navegável via D-pad.
 *
 * Caminho: Repositório › Addons › subpastas reais do repositório (ex. Filmes · Aventura)
 * › título → diálogo de destino: Reproduzir ou Salvar em Canais/Filmes/Séries.
 */
@Composable
fun RepositoryTreeScreen(onPlay: (PlaybackRequest) -> Unit) {
    val vm: RepositoryTreeViewModel = viewModel(
        factory = vmFactory { RepositoryTreeViewModel(AppGraph.content) },
    )
    val ui by vm.ui.collectAsState()
    val message by vm.message.collectAsState()
    val pending by vm.pending.collectAsState()
    val d = LocalDimens.current
    val ctx = LocalContext.current
    val path = ui.path
    val current = path.last()

    LaunchedEffect(message) {
        message?.let { msg ->
            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
            vm.consumeMessage()
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = d.screenH, vertical = d.screenV)) {
        // Breadcrumb (caminho Kodi): Repositório › Addons › TV › Canais Globo
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            path.mapIndexed { i, node ->
                val last = i == path.lastIndex
                Text(
                    text = node.name,
                    fontSize = d.textCaption,
                    fontWeight = if (last) FontWeight.Bold else FontWeight.Normal,
                    color = if (last) Smoke.TextPrimary else Smoke.TextSecondary,
                )
                if (!last) {
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Smoke.TextTertiary,
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = current.name,
            fontSize = d.textTitle,
            fontWeight = FontWeight.Bold,
            color = Smoke.TextPrimary,
        )
        Spacer(Modifier.height(10.dp))

        Box(Modifier.weight(1f).fillMaxSize().glass()) {
            if (!ui.loaded) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator(Modifier.size(24.dp))
                }
            } else if (ui.children.isEmpty()) {
                Text(
                    "Nenhum item neste nível.",
                    Modifier.align(Alignment.Center),
                    fontSize = d.textBody,
                    color = Smoke.TextTertiary,
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(items = ui.children, key = { it.id }) { node ->
                        when (node.kind) {
                            RepositoryNode.Kind.ITEM -> ItemRow(node) { vm.select(node) }
                            RepositoryNode.Kind.ADDON -> NavRow(node.name, Icons.Rounded.Extension) {
                                vm.navigateInto(node)
                            }
                            RepositoryNode.Kind.CATEGORY -> CategoryRow(node) {
                                vm.navigateInto(node)
                            }
                            RepositoryNode.Kind.FOLDER -> FolderRow(node) {
                                vm.navigateInto(node)
                            }
                            RepositoryNode.Kind.ROOT -> Unit
                        }
                    }
                }
            }
        }
    }

    // Diálogo de destino do conteúdo escolhido: reproduzir ou salvar na seção da categoria.
    pending?.let { item ->
        ItemActionDialog(
            item = item,
            section = vm.sectionOf(item),
            saved = vm.isSaved(item),
            onPlay = { vm.confirmPlay(onPlay) },
            onSave = { vm.saveSelected() },
            onDismiss = { vm.dismissSelection() },
        )
    }

    // Dentro da árvore, Voltar sobe um nível; na raiz, o MainShell devolve ao Início.
    BackHandler(enabled = path.size > 1) { vm.goBack() }
}

@Composable
private fun NavRow(title: String, icon: ImageVector, onClick: () -> Unit) =
    TreeRow(title, icon, onClick)

/**
 * Destino do conteúdo escolhido na árvore: reproduzir agora ou salvar na seção
 * da categoria (Canais/Filmes/Séries) — o usuário escolhe onde o conteúdo vai parar.
 */
@Composable
private fun ItemActionDialog(
    item: RepositoryNode,
    section: String,
    saved: Boolean,
    onPlay: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val d = LocalDimens.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        GlassPanel(Modifier.fillMaxWidth().widthIn(max = 640.dp), padding = 16.dp) {
            Column(
                Modifier.fillMaxWidth().padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    item.name,
                    fontSize = d.textTitle,
                    fontWeight = FontWeight.Bold,
                    color = Smoke.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "Conteúdo ${item.mediaKindLabel()} — escolha reproduzir ou salvar em $section.",
                    fontSize = d.textCaption,
                    color = Smoke.TextSecondary,
                )
                GlassButton("Reproduzir", Icons.Rounded.PlayArrow, onPlay)
                GlassButton(
                    if (saved) "Remover de $section" else "Salvar em $section",
                    if (saved) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    onSave,
                )
                GlassButton("Cancelar", Icons.Rounded.Close, onDismiss)
            }
        }
    }
}

/** Rótulo amigável da categoria do item (para o diálogo de destino). */
private fun RepositoryNode.mediaKindLabel() = when (mediaKind) {
    "channel" -> "de canais"
    "series" -> "de séries"
    else -> "de filmes"
}

@Composable
private fun CategoryRow(node: RepositoryNode, onClick: () -> Unit) =
    TreeRow(
        node.name,
        when (node.tab) {
            Tab.CHANNELS -> Icons.Rounded.LiveTv
            Tab.MOVIES -> Icons.Rounded.Movie
            Tab.SERIES -> Icons.Rounded.Theaters
            null -> Icons.Rounded.Extension
        },
        onClick,
    )

@Composable
private fun FolderRow(node: RepositoryNode, onClick: () -> Unit) =
    TreeRow(node.name, Icons.Rounded.Folder, onClick)

@Composable
private fun ItemRow(node: RepositoryNode, onPlay: () -> Unit) =
    TreeRow(node.name, Icons.Rounded.PlayArrow, onPlay)

@Composable
private fun TreeRow(title: String, icon: ImageVector, onClick: () -> Unit) {
    val d = LocalDimens.current
    GlassClickable(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = d.rowHeight),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) { focused ->
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon, contentDescription = null,
                Modifier.size(22.dp), tint = Smoke.TextPrimary,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                title, fontSize = d.textBody, fontWeight = FontWeight.SemiBold,
                color = Smoke.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

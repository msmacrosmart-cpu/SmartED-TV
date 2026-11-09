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
 * Explorador de repositórios no estilo Kodi, 100% navegável via D-pad.
 *
 * Caminho (4 níveis): Repositório › Add-ons declarados no JSON (tipos universais
 * Kodi: video, programs, radio, ...) › menu EXATO do add-on (o fluxo que o JSON
 * retornar) › subpastas/listas/conteúdo › título → diálogo: Reproduzir ou Salvar
 * em Canais/Filmes/Séries.
 */
@Composable
fun RepositoryTreeScreen(
    onPlay: (PlaybackRequest) -> Unit,
    onNavigate: (com.smarted.tv.ui.nav.Dest) -> Unit = {},
) {
    val vm: RepositoryTreeViewModel = viewModel(
        factory = vmFactory { RepositoryTreeViewModel(AppGraph.content) },
    )
    val ui by vm.ui.collectAsState()
    val message by vm.message.collectAsState()
    val pending by vm.pending.collectAsState()
    val xtreamExec by vm.xtreamExec.collectAsState()
    val detailsExec by vm.detailsExec.collectAsState()
    val connecting by vm.connecting.collectAsState()
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
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = current.name,
                fontSize = d.textTitle,
                fontWeight = FontWeight.Bold,
                color = Smoke.TextPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            // Prompt 07: reprodutor de app Android — Kodi original em tela cheia.
            GlassButton("Kodi · tela cheia", Icons.Rounded.Fullscreen, {
                if (!com.smarted.tv.ui.kodi.KodiLauncher.launchFullscreen(ctx)) {
                    Toast.makeText(
                        ctx,
                        "Kodi não instalado — instale o app oficial do Kodi (org.xbmc.kodi).",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            })
        }
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
                            RepositoryNode.Kind.REPOSITORY -> RepoRow(node) {
                                vm.navigateInto(node)
                            }
                            RepositoryNode.Kind.ADDON -> AddonRow(node) {
                                // Execução do add-on (menu/capacidade) — nunca
                                // explorador de arquivos de código.
                                vm.openAddon(node)
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

    // Execução Xtream nativa: credenciais (ou atalho p/ fonte já conectada) →
    // as categorias caem nas abas nativas Canais/Filmes/Séries.
    xtreamExec?.let { node ->
        AddonXtreamDialog(
            node = node,
            connecting = connecting,
            onUseExisting = { vm.useExistingXtream { onNavigate(com.smarted.tv.ui.nav.Dest.CHANNELS) } },
            onConnect = { url, user, pass ->
                vm.connectXtream(url, user, pass) {
                    onNavigate(com.smarted.tv.ui.nav.Dest.CHANNELS)
                }
            },
            onDismiss = { vm.dismissXtream() },
        )
    }

    // Sem menu e sem capacidade mapeável: metadados honestos (entrypoint etc.).
    detailsExec?.let { node ->
        AddonDetailsDialog(node = node, onDismiss = { vm.dismissDetails() })
    }

    // Dentro da árvore, Voltar sobe um nível; na raiz, o MainShell devolve ao Início.
    BackHandler(enabled = path.size > 1) { vm.goBack() }
}

/** Execução do add-on Xtream: painel de credenciais (cliente NATIVO do app). */
@Composable
private fun AddonXtreamDialog(
    node: RepositoryNode,
    connecting: Boolean,
    onUseExisting: () -> Unit,
    onConnect: (url: String, user: String, pass: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val d = LocalDimens.current
    var url by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    val existing by produceState(initialValue = false) {
        value = AppGraph.content.existingXtreamName() != null
    }
    val valid = url.isNotBlank() && user.isNotBlank() && pass.isNotBlank() && !connecting
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        GlassPanel(Modifier.fillMaxWidth().widthIn(max = 640.dp), padding = 16.dp) {
            Column(
                Modifier.fillMaxWidth().padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Executar ${node.name}",
                    fontSize = d.textTitle, fontWeight = FontWeight.Bold,
                    color = Smoke.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "Add-on Xtream Codes — o SmartED-TV usa o cliente NATIVO " +
                        "(mesmas categorias do add-on nas abas Canais/Filmes/Séries). " +
                        "O script Python do Kodi não é executado.",
                    fontSize = d.textCaption, color = Smoke.TextSecondary,
                )
                if (existing) {
                    GlassButton("Abrir categorias já importadas", Icons.Rounded.PlayArrow, onUseExisting)
                }
                com.smarted.tv.ui.components.GlassTextField(
                    url, { url = it }, "Servidor (http://host:porta)",
                    leadingIcon = null,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Next,
                )
                com.smarted.tv.ui.components.GlassTextField(
                    user, { user = it }, "Usuário",
                    leadingIcon = null,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Next,
                )
                com.smarted.tv.ui.components.GlassTextField(
                    pass, { pass = it }, "Senha", password = true,
                    leadingIcon = null,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassButton(
                        if (connecting) "Conectando…" else "Conectar e abrir",
                        Icons.Rounded.Check,
                        onClick = { if (valid) onConnect(url.trim(), user.trim(), pass.trim()) },
                    )
                    GlassButton("Cancelar", Icons.Rounded.Close, onDismiss)
                }
            }
        }
    }
}

/** Detalhes honestos do add-on sem menu/capacidade (sem listagem de código). */
@Composable
private fun AddonDetailsDialog(node: RepositoryNode, onDismiss: () -> Unit) {
    val d = LocalDimens.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        GlassPanel(Modifier.fillMaxWidth().widthIn(max = 640.dp), padding = 16.dp) {
            Column(
                Modifier.fillMaxWidth().padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    node.name, fontSize = d.textTitle, fontWeight = FontWeight.Bold,
                    color = Smoke.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                node.addonMeta?.let {
                    Text(it, fontSize = d.textBody, color = Smoke.TextSecondary)
                }
                node.addonEntrypoint?.let {
                    Text(
                        "Entrypoint: $it",
                        fontSize = d.textCaption, color = Smoke.TextTertiary,
                    )
                }
                node.addonDescription?.let {
                    Text(it, fontSize = d.textBody, color = Smoke.TextSecondary)
                }
                Text(
                    "Este add-on não declara menu nem capacidade compatível com o " +
                        "cliente nativo. O runtime Python do Kodi não é executado " +
                        "(docs/KODI_COMPAT.md) — os arquivos ficam no Gestor de " +
                        "Arquivos do repositório.",
                    fontSize = d.textCaption, color = Smoke.TextTertiary,
                )
                GlassButton("Fechar", Icons.Rounded.Close, onDismiss)
            }
        }
    }
}

@Composable
private fun NavRow(title: String, icon: ImageVector, onClick: () -> Unit) =
    TreeRow(title, icon, onClick)

/**
 * Linha de add-on estilo Kodi (referência): nome + autor/versão e descrição
 * quando o índice declara — sem dados fictícios, o que não existe não aparece.
 */
@Composable
private fun AddonRow(node: RepositoryNode, onClick: () -> Unit) {
    val d = LocalDimens.current
    GlassClickable(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = d.rowHeight),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = addonIcon(node), contentDescription = null,
                Modifier.size(22.dp), tint = Smoke.TextPrimary,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    node.name, fontSize = d.textBody, fontWeight = FontWeight.SemiBold,
                    color = Smoke.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (!node.addonMeta.isNullOrBlank()) {
                    Text(
                        node.addonMeta, fontSize = d.textCaption,
                        color = Smoke.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!node.addonDescription.isNullOrBlank()) {
                    Text(
                        node.addonDescription, fontSize = d.textCaption,
                        color = Smoke.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Ícone do grupo por tipo oficial de add-on (referência Kodi — nunca genérico). */
private fun groupIcon(type: String): ImageVector = when (type) {
    "video" -> Icons.Rounded.Movie
    "programs", "program", "tools", "script" -> Icons.Rounded.Build
    "music", "audio", "radio" -> Icons.Rounded.LibraryMusic
    "skins", "skin", "interface" -> Icons.Rounded.Palette
    "services", "service" -> Icons.Rounded.Settings
    "metadata" -> Icons.Rounded.Info
    "subtitles" -> Icons.Rounded.Subtitles
    "repository", "repositories" -> Icons.Rounded.Cloud
    "pvr" -> Icons.Rounded.LiveTv
    "pictures" -> Icons.Rounded.Image
    else -> Icons.Rounded.Extension
}

/** Nível 1: repositório cadastrado (OnePlay, etc.). */
@Composable
private fun RepoRow(node: RepositoryNode, onClick: () -> Unit) =
    TreeRow(node.name, Icons.Rounded.Storage, onClick)

/** Nível 2: addon — ícone pelo tipo UNIVERSAL declarado no JSON (desconhecido → puzzle). */
private fun addonIcon(node: RepositoryNode): ImageVector = when (node.addonType?.lowercase()) {
    "live", "tv", "channels" -> Icons.Rounded.LiveTv
    "movies", "films" -> Icons.Rounded.Movie
    "series" -> Icons.Rounded.Theaters
    else -> Icons.Rounded.Extension
}

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
        when {
            // Grupo de tipo oficial (adongroup|video|...): ícone do tipo, como no Kodi.
            node.id.startsWith("adongroup|") ->
                groupIcon(node.id.substringAfter("adongroup|").substringBefore("|"))
            else -> when (node.tab) {
                Tab.CHANNELS -> Icons.Rounded.LiveTv
                Tab.MOVIES -> Icons.Rounded.Movie
                Tab.SERIES -> Icons.Rounded.Theaters
                null -> Icons.Rounded.Extension
            }
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

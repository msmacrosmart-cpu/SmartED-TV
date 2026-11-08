package com.smarted.tv.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smarted.tv.core.data.AppGraph
import com.smarted.tv.core.model.DeviceMode
import com.smarted.tv.core.model.PlaylistSource
import com.smarted.tv.core.model.SourceType
import com.smarted.tv.data.OnePlayBootstrap.PHANTOM_NAME
import com.smarted.tv.ui.common.vmFactory
import com.smarted.tv.ui.components.*
import com.smarted.tv.ui.theme.LocalDimens
import com.smarted.tv.ui.theme.Smoke
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Composable
fun SettingsScreen(mode: DeviceMode, onChangeMode: () -> Unit) {
    val vm: SettingsViewModel = viewModel(factory = vmFactory { SettingsViewModel(AppGraph.system) })
    val sources by vm.sources.collectAsState()
    val busy by vm.busy.collectAsState()
    val d = LocalDimens.current
    val ctx = LocalContext.current
    var dialogType by remember { mutableStateOf<SourceType?>(null) }
    LaunchedEffect(Unit) {
        vm.message.collect { msg ->
            if (msg != null) { Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show(); vm.consumeMessage() }
        }
    }

    fun addUri(uri: Uri, type: SourceType) {
        runCatching {
            ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        vm.add(PlaylistSource(UUID.randomUUID().toString(), uri.lastPathSegment ?: "Local", type, uri.toString()))
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) addUri(uri, SourceType.M3U_FILE)
    }
    val pickTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) addUri(uri, SourceType.LOCAL_REPO)
    }
    // Muitas TVs/TV Boxes não têm seletor de arquivos (DocumentsUI): trate a ausência.
    fun safe(block: () -> Unit) = try { block() } catch (e: ActivityNotFoundException) {
        Toast.makeText(ctx, "Este aparelho não tem seletor de arquivos. Use uma URL.", Toast.LENGTH_LONG).show()
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = d.screenH, vertical = d.screenV),
        verticalArrangement = Arrangement.spacedBy(d.gutter),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Configurações", fontSize = d.textHeadline, fontWeight = FontWeight.Bold, color = Smoke.TextPrimary)
                if (busy.isBusy) {
                    Text("⟳ " + busy.message.ifBlank { "Sincronizando…" }, fontSize = d.textBody, color = Smoke.TextSecondary)
                } else if (sources.isNotEmpty()) {
                    GlassButton("Atualizar todas", Icons.Rounded.Refresh, { vm.refreshAll() })
                }
            }
        }

        // A fonte-fantasma (repositório OnePlay) é sincronizada automaticamente e nunca é exposta ao usuário.
        val visible = sources.filter { it.name != PHANTOM_NAME }
        item {
            Section("Listas · Xtream Codes / M3U") {
                val remote = visible.filter { it.type == SourceType.XTREAM || it.type == SourceType.M3U_URL || it.type == SourceType.REPOSITORY }
                if (remote.isEmpty()) Hint("Nenhuma lista adicionada.")
                remote.forEach { SourceRow(it, { vm.refresh(it.id) }, { vm.toggleEnabled(it.id) }) { vm.remove(it.id) } }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassButton("Adicionar Xtream Codes", Icons.Rounded.Dns, { dialogType = SourceType.XTREAM })
                    GlassButton("Adicionar M3U (URL)", Icons.Rounded.Link, { dialogType = SourceType.M3U_URL })
                    GlassButton("Adicionar repositório (URL)", Icons.Rounded.Storage, { dialogType = SourceType.REPOSITORY })
                }
            }
        }
        item {
            Section("Fontes e arquivos locais") {
                val local = visible.filter { it.type == SourceType.M3U_FILE || it.type == SourceType.LOCAL_REPO }
                if (local.isEmpty()) Hint("Nenhum arquivo ou pasta local adicionado.")
                local.forEach { SourceRow(it, { vm.refresh(it.id) }, { vm.toggleEnabled(it.id) }) { vm.remove(it.id) } }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassButton("Importar arquivo M3U", Icons.Rounded.Description, { safe { pickFile.launch(arrayOf("*/*")) } })
                    GlassButton("Adicionar pasta (repositório)", Icons.Rounded.Folder, { safe { pickTree.launch(null) } })
                }
            }
        }
        item { ElementumSection(vm) }
        item {
            Section("Dispositivo") {
                Text(
                    "Modo atual: " + if (mode == DeviceMode.TV) "TV / TV Box / Projetor" else "Celular / Tablet",
                    fontSize = d.textBody, color = Smoke.TextSecondary,
                )
                GlassButton("Alterar modo", Icons.Rounded.SwapHoriz, onChangeMode)
            }
        }
        item {
            Section("Sobre") {
                Hint("SmartED-TV é apenas um player. Não fornece canais, filmes ou séries: use somente fontes que você tem direito de acessar.")
            }
        }
    }

    dialogType?.let { type ->
        AddSourceDialog(type, onDismiss = { dialogType = null }) { vm.add(it); dialogType = null }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    val d = LocalDimens.current
    GlassPanel(Modifier.fillMaxWidth().widthIn(max = 820.dp), padding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontSize = d.textTitle, fontWeight = FontWeight.Bold, color = Smoke.TextPrimary)
            content()
        }
    }
}

@Composable
private fun Hint(text: String) =
    Text(text, fontSize = LocalDimens.current.textBody, color = Smoke.TextTertiary)

@Composable
private fun SourceRow(
    s: PlaylistSource, onRefresh: () -> Unit, onToggle: () -> Unit, onRemove: () -> Unit,
) {
    val d = LocalDimens.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(
            when (s.type) {
                SourceType.XTREAM -> Icons.Rounded.Dns
                SourceType.M3U_URL -> Icons.Rounded.Link
                SourceType.M3U_FILE -> Icons.Rounded.Description
                SourceType.LOCAL_REPO -> Icons.Rounded.Folder
                SourceType.REPOSITORY -> Icons.Rounded.Storage
            }, null, Modifier.size(22.dp), tint = if (s.enabled) Smoke.TextPrimary else Smoke.TextTertiary,
        )
        Column(Modifier.weight(1f)) {
            Text(s.name, fontSize = d.textBody, fontWeight = FontWeight.SemiBold, color = Smoke.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // Nunca exibe a senha.
            Text(
                if (s.type == SourceType.XTREAM) "${s.url} · ${s.username}" else s.url,
                fontSize = d.textCaption, color = Smoke.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            val statusLine = when {
                s.error.isNotBlank() -> "⚠ " + s.error
                s.status.isNotBlank() -> s.status
                else -> "Em dia"
            }
            val auto = if (s.autoUpdateMinutes > 0) (s.autoUpdateMinutes / 60).toString() + " h" else "manual"
            val last = if (s.lastRefreshMs > 0) " · " + SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(s.lastRefreshMs)) else ""
            Text(
                statusLine + " · auto " + auto + last,
                fontSize = d.textCaption,
                color = if (s.error.isNotBlank()) Color(0xFFFF9A8A) else Smoke.TextTertiary,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (!s.enabled) Text("pausada", fontSize = d.textCaption, color = Smoke.TextTertiary)
        GlassButton("Atualizar", Icons.Rounded.Refresh, onRefresh)
        GlassButton(if (s.enabled) "Pausar" else "Reativar", Icons.Rounded.PlayArrow, onToggle)
        GlassButton("Remover", Icons.Rounded.Delete, onRemove)
    }
}

@Composable
private fun ElementumSection(vm: SettingsViewModel) {
    val d = LocalDimens.current
    val el by vm.elementum.collectAsState()
    val prefs by vm.preferences.collectAsState()
    var host by remember(prefs.elementumHost) { mutableStateOf(prefs.elementumHost) }
    var port by remember(prefs.elementumPort) { mutableStateOf(prefs.elementumPort.toString()) }
    Section("Elementum · torrents (Kodi)") {
        Hint("O SmartED-TV usa o daemon Elementum (o mesmo do add-on do Kodi) para servir torrents. Nada é baixado para o aparelho.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassTextField(host, { host = it }, "Host (127.0.0.1)", leadingIcon = null,
                imeAction = androidx.compose.ui.text.input.ImeAction.Next, modifier = Modifier.weight(1.4f))
            GlassTextField(port, { port = it.filter { c -> c.isDigit() }.take(5) }, "Porta", leadingIcon = null,
                imeAction = androidx.compose.ui.text.input.ImeAction.Done, modifier = Modifier.weight(0.6f))
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassButton(if (prefs.elementumEnabled) "Desativar integração" else "Ativar integração",
                Icons.Rounded.Power, { vm.elementumApply(host, port, !prefs.elementumEnabled) })
            GlassButton("Salvar", Icons.Rounded.Check, { vm.elementumApply(host, port, prefs.elementumEnabled) })
            GlassButton("Testar conexão", Icons.Rounded.WifiTethering, { vm.elementumTest() })
            GlassButton("Atualizar catálogo", Icons.Rounded.Refresh, { vm.elementumUpdate() })
        }
        val tested = if (el.lastCheckMs > 0) SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(el.lastCheckMs)) else "—"
        Text(
            when {
                !prefs.elementumEnabled -> "○ Integração desativada."
                el.online -> "● Online · v" + el.version + " · " + el.activeTorrents + " torrents ativos"
                else -> "● Offline: " + el.message.ifBlank { "sem resposta" } + " (últ. teste " + tested + ")"
            },
            fontSize = d.textCaption,
            color = if (el.online) Color(0xFF9BF0A8) else Smoke.TextSecondary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Retomar reprodução onde parei", fontSize = d.textBody, color = Smoke.TextPrimary,
                modifier = Modifier.weight(1f))
            GlassButton(if (prefs.resumePlayback) "Ligado" else "Desligado", Icons.Rounded.History,
                { vm.setResume(!prefs.resumePlayback) })
        }
    }
}

@Composable
private fun AddSourceDialog(type: SourceType, onDismiss: () -> Unit, onSave: (PlaylistSource) -> Unit) {
    val d = LocalDimens.current
    val xtream = type == SourceType.XTREAM
    val repo = type == SourceType.REPOSITORY
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var epg by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    val valid = url.isNotBlank() && (!xtream || (user.isNotBlank() && pass.isNotBlank()))

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        GlassPanel(
            Modifier.widthIn(max = 560.dp).fillMaxWidth(0.92f),
            padding = 20.dp, fill = Color(0xFF0E0F12).copy(alpha = 0.96f),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    when {
                        xtream -> "Nova lista Xtream Codes"
                        repo -> "Novo repositório (índice JSON)"
                        else -> "Nova lista M3U"
                    }, fontSize = d.textTitle,
                    fontWeight = FontWeight.Bold, color = Smoke.TextPrimary)
                GlassTextField(name, { name = it }, "Nome (opcional)", leadingIcon = null, imeAction = androidx.compose.ui.text.input.ImeAction.Next)
                GlassTextField(url, { url = it },
                    when {
                        xtream -> "Servidor (http://host:porta)"
                        repo -> "URL do índice .json (raw)"
                        else -> "URL da lista M3U"
                    },
                    leadingIcon = null, imeAction = androidx.compose.ui.text.input.ImeAction.Next)
                if (!xtream && !repo) {
                    GlassTextField(epg, { epg = it }, "URL EPG (XMLTV) — opcional",
                        leadingIcon = null, imeAction = androidx.compose.ui.text.input.ImeAction.Next)
                }
                if (xtream) {
                    GlassTextField(user, { user = it }, "Usuário", leadingIcon = null, imeAction = androidx.compose.ui.text.input.ImeAction.Next)
                    GlassTextField(pass, { pass = it }, "Senha", leadingIcon = null, password = true,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassButton("Salvar", Icons.Rounded.Check, {
                        if (valid) onSave(PlaylistSource(
                            UUID.randomUUID().toString(),
                            name.ifBlank { if (xtream) "Xtream" else if (repo) "Repositório" else "M3U" },
                            type, url.trim(), user.trim().ifEmpty { null }, pass.ifEmpty { null },
                            epgUrl = epg.trim().ifEmpty { null },
                        ))
                    })
                    GlassButton("Cancelar", Icons.Rounded.Close, onDismiss)
                }
            }
        }
    }
}

package com.smarted.tv.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smarted.tv.core.model.Scope
import com.smarted.tv.core.protocol.AddonUrls
import com.smarted.tv.ui.components.*
import com.smarted.tv.ui.state.AppViewModel
import com.smarted.tv.ui.theme.*

@Composable
fun SettingsScreen(vm: AppViewModel, onHome: () -> Unit) {
    val data by vm.data.collectAsState()
    val state by vm.settings.collectAsState()
    var url by remember { mutableStateOf("") }
    var scope by remember { mutableStateOf(Scope.CHANNELS) }
    var adding by remember { mutableStateOf(false) }
    var removeUrl by remember { mutableStateOf<String?>(null) }
    var editUrl by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(adding) { if (adding) focus.requestFocus() }
    Row(Modifier.fillMaxSize().testTag("settings-screen"), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        Column(Modifier.width(if (LocalCompact.current) 200.dp else 250.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            BackAction(onHome)
            Text("Configurações", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Text("Seu conteúdo.\nSuas fontes.", style = MaterialTheme.typography.headlineLarge, color = Color.White)
            Text("Adicione manifests HTTP/JSON no padrão Stremio. Escolha onde cada addon aparece; nada é misturado automaticamente.", style = MaterialTheme.typography.bodyMedium, color = TvColors.Muted)
            Panel(Modifier.fillMaxWidth()) {
                Text("${data.addons.size} / 3", color = Color.White, style = MaterialTheme.typography.headlineLarge)
                Text("addons instalados", color = TvColors.Muted, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.weight(1f))
            Text("SmartED TV 2.0\nKotlin · Compose · Media3", color = TvColors.Muted, style = MaterialTheme.typography.labelMedium)
            Text("Sem listas fixas, sem execução de plugins. Use apenas fontes que você tem autorização para acessar.", color = TvColors.Muted, style = MaterialTheme.typography.labelMedium)
        }
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SectionLabel("Meus addons")
                TvAction("Adicionar addon", { adding = !adding; vm.clearMessages() }, icon = Icons.Default.Add, primary = true, enabled = data.addons.size < 3 && !state.busy, compact = true)
            }
            if (data.addons.size >= 3) Notice("Limite de 3 manifests atingido. Remova um addon para abrir espaço.")
            if (adding && data.addons.size < 3) Panel(Modifier.fillMaxWidth().testTag("add-addon-form")) {
                Text("Adicionar manifesto", color = Color.White, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(10.dp))
                SearchField(url, { url = it }, "https://seu-addon.com/manifest.json", Modifier.fillMaxWidth(), "manifest-url", focus)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TvAction("Colar URL", { clipboard.getText()?.text?.let { url = it } }, compact = true)
                    Text("HTTP ou HTTPS · até 3 addons", color = TvColors.Muted, style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.height(14.dp))
                SectionLabel("Este addon injeta conteúdo em:")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Scope.entries.forEach { Choice(it.label(), it == scope, { scope = it }, Modifier.testTag("install-scope-${it.name}")) }
                }
                Spacer(Modifier.height(10.dp))
                Text("A escolha é obrigatória e limita todas as buscas e resultados deste addon ao escopo selecionado.", color = TvColors.Muted, style = MaterialTheme.typography.bodySmall)
                state.preview?.let { Notice("${it.name} · versão ${it.version} · ${it.catalogsFor(scope).size} catálogo(s) compatíveis") }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TvAction(if (state.busy) "Validando…" else "Validar e adicionar", { vm.install(url, scope) }, enabled = !state.busy && url.isNotBlank(), primary = true, modifier = Modifier.testTag("install-addon"))
                    TvAction("Validar JSON", { vm.preview(url, scope) }, enabled = !state.busy && url.isNotBlank())
                    TvAction("Cancelar", { adding = false; vm.clearMessages() }, enabled = !state.busy)
                }
            }
            if (data.addons.isEmpty() && !adding) EmptyState("Comece com um addon", "Cole um manifesto para trazer seus canais, filmes ou séries. Não há catálogo fixo nem fonte embutida.", Modifier.fillMaxWidth()) {
                TvAction("Adicionar meu primeiro addon", { adding = true })
            }
            data.addons.forEach { addon ->
                Panel(Modifier.fillMaxWidth().testTag("addon-${addon.url}")) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        RemoteImage(addon.manifest.logo, null, Modifier.size(54.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(addon.manifest.name, color = Color.White, style = MaterialTheme.typography.titleLarge)
                            Text("${addon.scope.label()} · v${addon.manifest.version} · ${addon.manifest.catalogsFor(addon.scope).size} catálogos", color = TvColors.Muted, style = MaterialTheme.typography.bodyMedium)
                            Text(AddonUrls.display(addon.url), color = TvColors.Muted, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (addon.manifest.description.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(addon.manifest.description, color = TvColors.Muted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TvAction("Alterar escopo", { editUrl = if (editUrl == addon.url) null else addon.url }, compact = true)
                        TvAction("Atualizar", { vm.refresh(addon.url) }, icon = Icons.Default.Refresh, compact = true, enabled = !state.busy)
                        TvAction("Remover", { removeUrl = addon.url }, icon = Icons.Default.Delete, compact = true, enabled = !state.busy)
                    }
                    if (editUrl == addon.url) { Spacer(Modifier.height(10.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Scope.entries.forEach { Choice(it.label(), addon.scope == it, { vm.scope(addon.url, it); editUrl = null }, Modifier.testTag("change-${it.name}")) }
                    } }
                    if (removeUrl == addon.url) { Spacer(Modifier.height(10.dp)); Notice("Remover este addon e seus favoritos?"); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TvAction("Confirmar remoção", { vm.remove(addon.url); removeUrl = null }, primary = true, compact = true)
                        TvAction("Cancelar", { removeUrl = null }, compact = true)
                    } }
                }
            }
            if (state.busy) Notice("Conectando ao manifesto com limite de tempo…")
            if (state.error.isNotBlank()) Panel(Modifier.fillMaxWidth().testTag("settings-error")) { Text(state.error, color = Color.White, style = MaterialTheme.typography.bodyMedium) }
            if (state.message.isNotBlank()) Notice(state.message)
            Spacer(Modifier.height(12.dp))
            SectionLabel("Compatibilidade")
            Text("Catálogos, metadados, episódios, streams HTTP diretos, HLS/DASH e EPG nativo do addon. Torrents, plugins externos e DRM não são executados. Links externos podem ser abertos no navegador.", color = TvColors.Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}
fun Scope.label(): String = when (this) { Scope.CHANNELS -> "Canais"; Scope.MOVIES -> "Filmes"; Scope.SERIES -> "Séries" }

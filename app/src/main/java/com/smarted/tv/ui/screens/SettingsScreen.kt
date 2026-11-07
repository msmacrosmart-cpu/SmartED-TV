package com.smarted.tv.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smarted.tv.BuildConfig
import com.smarted.tv.core.model.Scope
import com.smarted.tv.core.model.UserPreferences
import com.smarted.tv.core.protocol.AddonUrls
import com.smarted.tv.ui.components.*
import com.smarted.tv.ui.state.AppViewModel
import com.smarted.tv.ui.theme.*

private enum class SettingsSection(val label: String, val icon: ImageVector) {
    ADDONS("Addons", Icons.Default.Extension),
    GENERAL("Geral", Icons.Default.Settings),
    INTERFACE("Interface", Icons.Default.Tune),
    PLAYER("Player", Icons.Default.PlayArrow),
    STREAMING("Streaming", Icons.Default.Language),
    SHORTCUTS("Atalhos", Icons.Default.Info),
    ABOUT("Sobre", Icons.Default.Info),
}

/**
 * Organização visual das configurações do Stremio: menu lateral + seções Geral, Interface,
 * Player, Streaming e Atalhos. A gestão de addons fica junto das configurações, como pedido
 * para o SmartED. Controles de conta/servidor de torrent do Stremio não são simulados:
 * este app nativo usa apenas manifestos e streams HTTP diretos.
 */
@Composable
fun SettingsScreen(vm: AppViewModel, onHome: () -> Unit) {
    val data by vm.data.collectAsState()
    val state by vm.settings.collectAsState()
    var section by remember { mutableStateOf(SettingsSection.ADDONS) }
    Row(Modifier.fillMaxSize().testTag("settings-screen"), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.width(if (LocalCompact.current) 200.dp else 240.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BackAction(onHome)
            Text("Configurações", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            SettingsSection.entries.forEach { entry ->
                Choice(entry.label, section == entry, { section = entry; vm.clearMessages() },
                    Modifier.fillMaxWidth().testTag("settings-section-${entry.name}"))
            }
            Spacer(Modifier.weight(1f))
            Text("SmartED TV ${BuildConfig.VERSION_NAME}", color = TvColors.Muted, style = MaterialTheme.typography.labelMedium)
            Text("Modelo Stremio · adaptação nativa Android TV", color = TvColors.Muted, style = MaterialTheme.typography.labelMedium)
        }
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(section.label, style = MaterialTheme.typography.headlineMedium, color = Color.White)
            when (section) {
                SettingsSection.ADDONS -> AddonsSettings(vm, state, data.addons)
                SettingsSection.GENERAL -> GeneralSettings(vm)
                SettingsSection.INTERFACE -> InterfaceSettings()
                SettingsSection.PLAYER -> PlayerSettings(vm, data.preferences)
                SettingsSection.STREAMING -> StreamingSettings()
                SettingsSection.SHORTCUTS -> ShortcutsSettings()
                SettingsSection.ABOUT -> AboutSettings()
            }
            if (state.busy) Notice("Conectando ao manifesto com limite de tempo…")
            if (state.error.isNotBlank()) Panel(Modifier.fillMaxWidth().testTag("settings-error")) {
                Text(state.error, color = Color.White, style = MaterialTheme.typography.bodyMedium)
            }
            if (state.message.isNotBlank()) Notice(state.message)
        }
    }
}

@Composable
private fun AddonsSettings(vm: AppViewModel, state: com.smarted.tv.ui.state.SettingsUi, addons: List<com.smarted.tv.core.model.InstalledAddon>) {
    var url by remember { mutableStateOf("") }
    var scope by remember { mutableStateOf(Scope.CHANNELS) }
    var adding by remember { mutableStateOf(false) }
    var removeUrl by remember { mutableStateOf<String?>(null) }
    var editUrl by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(adding) { if (adding) focus.requestFocus() }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
            SectionLabel("Meus addons")
            Text("${addons.size} / 3", color = Color.White, style = MaterialTheme.typography.headlineMedium)
        }
        TvAction("Adicionar addon", { adding = !adding; vm.clearMessages() }, icon = Icons.Default.Add, primary = true,
            enabled = addons.size < 3 && !state.busy, compact = true)
    }
    Notice("Cole a URL final de manifest.json e escolha se este addon aparece em Canais, Filmes ou Séries. O limite é de 3 manifestos instalados.")
    if (addons.size >= 3) Notice("Limite de 3 manifestos atingido. Remova um addon para abrir espaço.")
    if (adding && addons.size < 3) Panel(Modifier.fillMaxWidth().testTag("add-addon-form")) {
        Text("Adicionar manifesto", color = Color.White, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(10.dp))
        SearchField(url, { url = it }, "https://seu-addon.com/manifest.json", Modifier.fillMaxWidth(), "manifest-url", focus)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TvAction("Colar URL", { clipboard.getText()?.text?.let { url = it } }, compact = true)
            Text("HTTP/HTTPS · JSON · até 3 addons", color = TvColors.Muted, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(14.dp))
        SectionLabel("Este addon injeta conteúdo em:")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Scope.entries.forEach { Choice(it.label(), it == scope, { scope = it }, Modifier.testTag("install-scope-${it.name}")) }
        }
        Spacer(Modifier.height(10.dp))
        Text("A escolha limita catálogos, buscas, detalhes e streams deste addon ao escopo selecionado.", color = TvColors.Muted, style = MaterialTheme.typography.bodySmall)
        state.preview?.let { Notice("${it.name} · versão ${it.version} · ${it.catalogsFor(scope).size} catálogo(s) compatíveis") }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TvAction(if (state.busy) "Validando…" else "Validar e adicionar", { vm.install(url, scope) },
                enabled = !state.busy && url.isNotBlank(), primary = true, modifier = Modifier.testTag("install-addon"))
            TvAction("Validar JSON", { vm.preview(url, scope) }, enabled = !state.busy && url.isNotBlank())
            TvAction("Cancelar", { adding = false; vm.clearMessages() }, enabled = !state.busy)
        }
    }
    if (addons.isEmpty() && !adding) EmptyState("Comece com um addon",
        "Cole um manifesto para trazer seus canais, filmes ou séries. Não há catálogo fixo nem fonte embutida.", Modifier.fillMaxWidth()) {
        TvAction("Adicionar meu primeiro addon", { adding = true })
    }
    addons.forEach { addon ->
        Panel(Modifier.fillMaxWidth().testTag("addon-${addon.url}")) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                RemoteImage(addon.manifest.logo, null, Modifier.size(54.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(addon.manifest.name, color = Color.White, style = MaterialTheme.typography.titleLarge)
                    Text("${addon.scope.label()} · v${addon.manifest.version} · ${addon.manifest.catalogsFor(addon.scope).size} catálogos",
                        color = TvColors.Muted, style = MaterialTheme.typography.bodyMedium)
                    Text(AddonUrls.display(addon.url), color = TvColors.Muted, style = MaterialTheme.typography.labelMedium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (addon.manifest.description.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(addon.manifest.description, color = TvColors.Muted, style = MaterialTheme.typography.bodySmall,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TvAction("Alterar escopo", { editUrl = if (editUrl == addon.url) null else addon.url }, compact = true)
                TvAction("Atualizar", { vm.refresh(addon.url) }, icon = Icons.Default.Refresh, compact = true, enabled = !state.busy)
                TvAction("Remover", { removeUrl = addon.url }, icon = Icons.Default.Delete, compact = true, enabled = !state.busy)
            }
            if (editUrl == addon.url) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Scope.entries.forEach { Choice(it.label(), addon.scope == it, { vm.scope(addon.url, it); editUrl = null }, Modifier.testTag("change-${it.name}")) }
                }
            }
            if (removeUrl == addon.url) {
                Spacer(Modifier.height(10.dp))
                Notice("Remover este addon e seus favoritos?")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TvAction("Confirmar remoção", { vm.remove(addon.url); removeUrl = null }, primary = true, compact = true)
                    TvAction("Cancelar", { removeUrl = null }, compact = true)
                }
            }
        }
    }
}

@Composable
private fun GeneralSettings(vm: AppViewModel) {
    SettingsRow("Idioma do aplicativo", "Português (Brasil)")
    SettingsRow("Armazenamento", "Local no aparelho; addons, favoritos e progresso são persistidos.")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TvAction("Apagar histórico de reprodução", vm::clearProgress, compact = true)
        TvAction("Restaurar preferências padrão", vm::resetPreferences, compact = true)
    }
    Notice("Não há conta Stremio embutida. O app consome o protocolo de addons por HTTP/JSON.")
}

@Composable
private fun InterfaceSettings() {
    SettingsRow("Tema", "Dark Glassmorphism fumê")
    SettingsRow("Foco do controle remoto", "Branco puro")
    SettingsRow("Layout", "Canais em 3 colunas; Filmes e Séries em 2 colunas com grade vertical infinita.")
    Notice("A referência visual é traduzida para BR-PT e adaptada ao D-pad do Android TV.")
}

@Composable
private fun PlayerSettings(vm: AppViewModel, prefs: UserPreferences) {
    SectionLabel("Áudio e legendas")
    OptionRow("Idioma de áudio preferido", listOf("pt" to "Português", "en" to "Inglês", "es" to "Espanhol", "" to "Original"), prefs.audioLanguage) { value ->
        vm.preferences { it.copy(audioLanguage = value) }
    }
    OptionRow("Idioma de legendas preferido", listOf("pt" to "Português", "en" to "Inglês", "es" to "Espanhol", "" to "Automático"), prefs.subtitleLanguage) { value ->
        vm.preferences { it.copy(subtitleLanguage = value) }
    }
    OptionRow("Legendas", listOf(true to "Ativadas", false to "Desativadas"), prefs.subtitlesEnabled) { value ->
        vm.preferences { it.copy(subtitlesEnabled = value) }
    }
    OptionRow("Tamanho das legendas", listOf(75 to "75%", 100 to "100%", 125 to "125%", 150 to "150%"), prefs.subtitleSizePercent) { value ->
        vm.preferences { it.copy(subtitleSizePercent = value) }
    }
    SectionLabel("Reprodução")
    OptionRow("Retomar de onde parou", listOf(true to "Sim", false to "Não"), prefs.resumePlayback) { value ->
        vm.preferences { it.copy(resumePlayback = value) }
    }
    OptionRow("Próximo episódio automático", listOf(true to "Sim", false to "Não"), prefs.autoplayNextEpisode) { value ->
        vm.preferences { it.copy(autoplayNextEpisode = value) }
    }
    OptionRow("Voltar com seta esquerda", listOf(5 to "5 s", 10 to "10 s", 15 to "15 s", 30 to "30 s"), prefs.seekBackSeconds) { value ->
        vm.preferences { it.copy(seekBackSeconds = value) }
    }
    OptionRow("Avançar com seta direita", listOf(10 to "10 s", 30 to "30 s", 60 to "60 s"), prefs.seekForwardSeconds) { value ->
        vm.preferences { it.copy(seekForwardSeconds = value) }
    }
}

@Composable
private fun StreamingSettings() {
    SettingsRow("Protocolo", "Stremio addon protocol sobre HTTP/JSON")
    SettingsRow("Streams compatíveis", "HTTP diretos, HLS e DASH via Media3/ExoPlayer")
    SettingsRow("EPG", "Programação nativa do addon, por canal e data")
    Notice("Torrent/P2P, plugins externos e DRM não são executados. Links externos só abrem no navegador após confirmação.")
}

@Composable
private fun ShortcutsSettings() {
    SettingsRow("Navegação", "Setas do D-pad movem o foco; OK seleciona.")
    SettingsRow("Voltar", "Fecha detalhes, player ou retorna ao início.")
    SettingsRow("Busca", "OK no campo de busca abre o teclado; cada aba pesquisa somente o próprio escopo.")
}

@Composable
private fun AboutSettings() {
    SettingsRow("Aplicativo", "SmartED TV ${BuildConfig.VERSION_NAME}")
    SettingsRow("Implementação", "Kotlin · Jetpack Compose · Media3")
    SettingsRow("Referências visuais", "xplayer (cartões), HTV (menu de canais), Ultra TV (tela inicial), Stremio (configurações).")
    Notice("Use apenas fontes que você tem autorização para acessar.")
}

@Composable
private fun SettingsRow(label: String, value: String) {
    Panel(Modifier.fillMaxWidth(), padding = 14.dp) {
        Text(label, color = Color.White, style = MaterialTheme.typography.titleMedium)
        Text(value, color = TvColors.Muted, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun <T> OptionRow(label: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Panel(Modifier.fillMaxWidth(), padding = 14.dp) {
        Text(label, color = Color.White, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, text) -> Choice(text, value == selected, { onSelect(value) }) }
        }
    }
}

fun Scope.label(): String = when (this) { Scope.CHANNELS -> "Canais"; Scope.MOVIES -> "Filmes"; Scope.SERIES -> "Séries" }

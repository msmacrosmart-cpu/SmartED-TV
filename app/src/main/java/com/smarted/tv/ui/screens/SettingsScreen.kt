package com.smarted.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smarted.tv.core.model.ListSource
import com.smarted.tv.core.model.SourceKind
import com.smarted.tv.core.model.UserPreferences
import com.smarted.tv.ui.components.Choice
import com.smarted.tv.ui.components.EmptyState
import com.smarted.tv.ui.components.Notice
import com.smarted.tv.ui.components.Panel
import com.smarted.tv.ui.components.SearchField
import com.smarted.tv.ui.components.SectionLabel
import com.smarted.tv.ui.components.TvAction
import com.smarted.tv.ui.state.AppViewModel
import com.smarted.tv.ui.theme.LocalCompact
import com.smarted.tv.ui.theme.TvColors
import com.smarted.tv.ui.theme.smokedGlass
import com.smarted.tv.ui.theme.whiteFocus

private enum class SettingsPage(val label: String) {
    SOURCES("Fontes"), ELEMENTUM("Elementum"), PLAYBACK("Reprodução"), INTERFACE("Interface"), SYSTEM("Sistema"), ABOUT("Sobre");
}

/**
 * Configurações: menu lateral ao estilo Kodi + painel por seção.
 * Gerencia fontes M3U/Xtream/repositórios, EPG, Elementum, player e o estado do repositório local.
 */
@Composable
fun SettingsScreen(app: AppViewModel) {
    var page by remember { mutableStateOf(SettingsPage.SOURCES) }
    val ui by app.ui.collectAsState()
    val prefs by app.preferences.collectAsState()
    val compact = LocalCompact.current

    Row(Modifier.fillMaxSize().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp)) {
        Column(
            Modifier.fillMaxHeight().width(if (compact) 170.dp else 196.dp).smokedGlass(18.dp).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SectionLabel("Configurações")
            SettingsPage.entries.forEach { entry ->
                MenuRow(entry.label, page == entry) { page = entry }
            }
        }
        Column(Modifier.fillMaxHeight().weight(1f).smokedGlass(18.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (ui.busy.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.testTag("settings-busy")) {
                    Text("⟳", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(ui.busy, color = TvColors.Muted, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (ui.message.isNotBlank()) Notice("✓ ${ui.message}")
            if (ui.error.isNotBlank()) Notice("✕ ${ui.error}")
            when (page) {
                SettingsPage.SOURCES -> SourcesPanel(ui.sources, app, ui)
                SettingsPage.ELEMENTUM -> ElementumPanel(app, prefs)
                SettingsPage.PLAYBACK -> PlaybackPanel(prefs, app)
                SettingsPage.INTERFACE -> InterfacePanel(prefs, app)
                SettingsPage.SYSTEM -> SystemPanel(app, ui)
                SettingsPage.ABOUT -> AboutPanel(app)
            }
        }
    }
}

@Composable
private fun MenuRow(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier.fillMaxWidth().testTag("settings-menu-${label.lowercase()}")
            .onFocusChanged { focused = it.isFocused }
            .whiteFocus(focused, selected, 12.dp).padding(2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Color(0x2BFFFFFF) else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        Text(label, color = Color.White, style = if (selected) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium)
    }
}

// ---------------------------------------------------------------- Fontes

@Composable
private fun SourcesPanel(sources: List<ListSource>, app: AppViewModel, ui: com.smarted.tv.ui.state.AppUi) {
    var editing by remember { mutableStateOf<SettingsForm?>(null) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Fontes e repositórios", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Spacer(Modifier.weight(1f))
        TvAction("Adicionar lista M3U", { editing = SettingsForm.M3U }, primary = true, compact = true, modifier = Modifier.testTag("form-open-m3u"))
        TvAction("Painel Xtream", { editing = SettingsForm.XTREAM }, compact = true, modifier = Modifier.testTag("form-open-xtream"))
        TvAction("Repositório", { editing = SettingsForm.REPO }, compact = true, modifier = Modifier.testTag("form-open-repo"))
    }
    Notice("Auto-update no estilo repositórios OnePlay: cada fonte guarda listas, VOD e XMLTV no banco local e re-sincroniza no intervalo configurado.")
    val form = editing
    if (form != null) {
        SourceForm(form, onDismiss = { editing = null }, app = app)
    }
    if (sources.isEmpty() && form == null) {
        EmptyState("Nenhuma fonte cadastrada", "Adicione uma playlist M3U, um painel Xtream ou um repositório de fontes.", Modifier.fillMaxWidth())
    }
    LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(sources, key = { "src-$it" }) { source ->
            SourceRow(source, app, form == null)
        }
    }
}

private enum class SettingsForm { M3U, XTREAM, REPO }

@Composable
private fun SourceForm(form: SettingsForm, onDismiss: () -> Unit, app: AppViewModel) {
    var name by remember(form) { mutableStateOf("") }
    var url by remember(form) { mutableStateOf("") }
    var epg by remember(form) { mutableStateOf("") }
    var user by remember(form) { mutableStateOf("") }
    var pass by remember(form) { mutableStateOf("") }
    var interval by remember(form) { mutableStateOf("720") }

    fun submit() {
        val minutes = interval.toIntOrNull()?.coerceIn(0, 10_080) ?: 720
        when (form) {
            SettingsForm.M3U -> app.addM3u(name, url.trim(), epg.trim())
            SettingsForm.XTREAM -> app.addXtream(name, url.trim(), user.trim(), pass.trim(), epg.trim())
            SettingsForm.REPO -> app.addRepository(name, url.trim())
        }
        onDismiss()
    }

    Panel(Modifier.fillMaxWidth(), padding = 14.dp) {
        Text(
            when (form) {
                SettingsForm.M3U -> "Nova lista M3U/M3U8"
                SettingsForm.XTREAM -> "Painel Xtream Codes"
                SettingsForm.REPO -> "Repositório de fontes (JSON estilo OnePlay)"
            },
            style = MaterialTheme.typography.titleLarge, color = Color.White,
        )
        Text(
            when (form) {
                SettingsForm.M3U -> "URL da playlist (.m3u ou .m3u8, http/https) e opcionalmente a URL do EPG XMLTV."
                SettingsForm.XTREAM -> "Endereço base do painel (http://host:porta) + usuário e senha da operadora."
                SettingsForm.REPO -> "URL de um JSON {\"name\":…, \"sources\":[{name,kind,url,epg,update_minutes}]} — a importação cria e mantém as fontes filhas sincronizadas."
            },
            color = TvColors.Muted, style = MaterialTheme.typography.bodyMedium,
        )
        SearchField(name, { name = it }, "Nome (opcional)", Modifier.fillMaxWidth(), tag = "form-name")
        SearchField(url, { url = it }, if (form == SettingsForm.XTREAM) "http://host:porta" else "https://…/lista.m3u", Modifier.fillMaxWidth(), tag = "form-url")
        if (form == SettingsForm.XTREAM) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SearchField(user, { user = it }, "usuário", Modifier.weight(1f), tag = "form-user")
                SearchField(pass, { pass = it }, "senha", Modifier.weight(1f), tag = "form-pass")
            }
        }
        if (form != SettingsForm.REPO) {
            SearchField(epg, { epg = it }, "URL EPG XMLTV (opcional)", Modifier.fillMaxWidth(), tag = "form-epg")
        }
        SearchField(interval, { interval = it.filter(Char::isDigit).take(5) }, "auto-update (min; 0 = nunca)", Modifier.fillMaxWidth(), tag = "form-interval")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TvAction("Salvar e sincronizar", ::submit, primary = true, modifier = Modifier.testTag("form-save"), enabled = url.isNotBlank() && (form != SettingsForm.XTREAM || (user.isNotBlank() && pass.isNotBlank())))
            TvAction("Cancelar", onDismiss, modifier = Modifier.testTag("form-cancel"))
        }
    }
}

@Composable
private fun SourceRow(source: ListSource, app: AppViewModel, enabled: Boolean) {
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    var editEpg by remember(source.id) { mutableStateOf(false) }
    var epgDraft by remember(source.id) { mutableStateOf(source.epgUrl) }
    Column(
        Modifier.fillMaxWidth().testTag("source-${source.id}")
            .onFocusChanged { focused = it.isFocused }
            .whiteFocus(focused, radius = 14.dp).padding(2.dp)
            .clip(RoundedCornerShape(12.dp)).background(Color(0x12FFFFFF))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(source.name, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(
                when (source.kind) { SourceKind.M3U -> "M3U"; SourceKind.XTREAM -> "XTREAM"; SourceKind.REPOSITORY -> "REPOSITÓRIO" },
                color = TvColors.Muted, style = MaterialTheme.typography.labelMedium,
            )
            if (!source.enabled) Text("pausada", color = Color(0xFFFFB4AB), style = MaterialTheme.typography.labelMedium)
        }
        Text(source.url, color = TvColors.Muted, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (source.status.isNotBlank()) Text(source.status, color = Color(0xFF9FE8B0), style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("source-status-${source.id}"))
        if (source.error.isNotBlank()) Text(source.error, color = Color(0xFFFFC9C9), style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TvAction("Atualizar", { app.refreshSource(source.id) }, compact = true, enabled = enabled, modifier = Modifier.testTag("source-refresh-${source.id}"))
            if (source.kind != SourceKind.REPOSITORY) TvAction("Só EPG", { app.refreshEpgOnly(source.id) }, compact = true, enabled = enabled, modifier = Modifier.testTag("source-epg-${source.id}"))
            TvAction(if (source.enabled) "Pausar" else "Ativar", { app.toggleSource(source) }, compact = true, enabled = enabled)
            TvAction(if (source.autoUpdateMinutes > 0) "Auto ${source.autoUpdateMinutes}m" else "Auto off", {
                app.setSourceInterval(source, if (source.autoUpdateMinutes > 0) 0 else 720)
            }, compact = true, enabled = enabled, modifier = Modifier.testTag("source-interval-${source.id}"))
            if (source.kind == SourceKind.M3U) {
                TvAction(if (source.epgUrl.isBlank()) "Especificar EPG" else "Editar EPG", { editEpg = !editEpg }, compact = true, enabled = enabled)
            }
            TvAction("Remover", { app.removeSource(source) }, compact = true, enabled = enabled, modifier = Modifier.testTag("source-remove-${source.id}"))
        }
        if (editEpg) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                SearchField(epgDraft, { epgDraft = it }, "https://…/xmltv", Modifier.weight(1f), tag = "source-epg-edit-${source.id}")
                TvAction("Salvar", { app.setSourceEpgUrl(source, epgDraft); editEpg = false }, compact = true, primary = true)
            }
        }
    }
}

// ------------------------------------------------------------- Elementum

@Composable
private fun ElementumPanel(app: AppViewModel, prefs: UserPreferences) {
    val state by app.elementum.collectAsState()
    var host by remember(prefs.elementumHost) { mutableStateOf(prefs.elementumHost) }
    var port by remember(prefs.elementumPort) { mutableStateOf(prefs.elementumPort.toString()) }
    Text("Elementum (torrents/VOD)", style = MaterialTheme.typography.headlineMedium, color = Color.White)
    Notice("Integração com o daemon local do Elementum (Kodi add-on) via HTTP: status, torrents ativos, busca de catálogos e atualização automática dos provedores. O SmartED não executa torrent nem plugins — a reprodução direta acontece quando o daemon expõe URL HTTP; caso contrário o conteúdo abre no Kodi.")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Choice("Integração ativada", prefs.elementumEnabled, { app.preferences { it.copy(elementumEnabled = !it.elementumEnabled) } }, modifier = Modifier.testTag("elementum-toggle"))
        Spacer(Modifier.weight(1f))
        Text(
            if (prefs.elementumEnabled && state.online) "ONLINE · ${state.activeTorrents} torrent(s)" else if (prefs.elementumEnabled) "OFFLINE" else "desativado",
            color = if (state.online) Color(0xFF9FE8B0) else TvColors.Muted, style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.testTag("elementum-status"),
        )
    }
    Notice(state.message)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        SearchField(host, { host = it }, "host", Modifier.weight(1f), tag = "elementum-host")
        SearchField(port, { port = it.filter(Char::isDigit).take(5) }, "porta", Modifier.width(110.dp), tag = "elementum-port")
        TvAction("Aplicar", {
            app.preferences { it.copy(elementumHost = host.ifBlank { "127.0.0.1" }, elementumPort = port.toIntOrNull()?.coerceIn(1, 65535) ?: 65064) }
            app.elementumTest()
        }, compact = true, modifier = Modifier.testTag("elementum-apply"))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TvAction("Testar conexão", app::elementumTest, compact = true, modifier = Modifier.testTag("elementum-test"))
        TvAction(if (state.updating) "Atualizando…" else "Atualizar Elementum (reload provedores)", app::elementumUpdate, compact = true, enabled = !state.updating, modifier = Modifier.testTag("elementum-update"))
    }
}

// ------------------------------------------------------------- Reprodução

@Composable
private fun PlaybackPanel(prefs: UserPreferences, app: AppViewModel) {
    Text("Reprodução", style = MaterialTheme.typography.headlineMedium, color = Color.White)
    Notice("Áudio/legendas e saltos aplicados pelo player nativo (media3). Idiomas seguem o código BCP-47 preferido do stream.")
    LabeledChips("Idioma do áudio", listOf("pt", "es", "en", "")) { code -> app.preferences { it.copy(audioLanguage = code) } }
    LabeledChips("Idioma da legenda", listOf("pt", "en", "es", "")) { code -> app.preferences { it.copy(subtitleLanguage = code) } }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Choice("Legendas ativas", prefs.subtitlesEnabled, { app.preferences { it.copy(subtitlesEnabled = !it.subtitlesEnabled) } }, modifier = Modifier.testTag("pref-subs"))
        Choice("Retomar reprodução", prefs.resumePlayback, { app.preferences { it.copy(resumePlayback = !it.resumePlayback) } })
        Choice("Confirmar próximo episódio", prefs.autoplayNextEpisode, { app.preferences { it.copy(autoplayNextEpisode = !it.autoplayNextEpisode) } })
    }
    LabeledChips("Tamanho da legenda", listOf(75, 100, 125, 150)) { size -> app.preferences { it.copy(subtitleSizePercent = size) } }
    LabeledChips("Avançar no tempo", listOf(15, 30, 60)) { secs -> app.preferences { it.copy(seekForwardSeconds = secs) } }
    LabeledChips("Voltar no tempo", listOf(5, 10, 20)) { secs -> app.preferences { it.copy(seekBackSeconds = secs) } }
}

@Composable
private fun <T> LabeledChips(label: String, options: List<T>, onSelect: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionLabel(label)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                TvAction(option?.toString()?.ifBlank { "auto" } ?: "—", { onSelect(option) }, compact = true, modifier = Modifier.testTag("chip-$label-${option}"))
            }
        }
    }
}

// -------------------------------------------------------------- Interface

@Composable
private fun InterfacePanel(prefs: UserPreferences, app: AppViewModel) {
    Text("Interface", style = MaterialTheme.typography.headlineMedium, color = Color.White)
    Notice("Fumê escuro com foco branco é o único tema do app (identidade fixa para D-pad).")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Choice("Pré-visualização no Canais", prefs.previewPlayerEnabled, { app.preferences { it.copy(previewPlayerEnabled = !it.previewPlayerEnabled) } }, modifier = Modifier.testTag("pref-preview"))
    }
    LabeledChips("Janela do EPG (horas carregados)", listOf(6, 12, 24, 48)) { hours -> app.preferences { it.copy(epgWindowHours = hours) } }
    LabeledChips("Auto-update global das fontes (min)", listOf(0, 60, 720, 4320)) { minutes ->
        app.preferences { it.copy(autoUpdateMinutes = minutes) }
    }
}

// ---------------------------------------------------------------- Sistema

@Composable
private fun SystemPanel(app: AppViewModel, ui: com.smarted.tv.ui.state.AppUi) {
    Text("Sistema e repositório local", style = MaterialTheme.typography.headlineMedium, color = Color.White)
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
        Stat("canais", ui.counts[com.smarted.tv.core.model.Tab.CHANNELS] ?: 0)
        Stat("filmes", ui.counts[com.smarted.tv.core.model.Tab.MOVIES] ?: 0)
        Stat("séries", ui.counts[com.smarted.tv.core.model.Tab.SERIES] ?: 0)
        Stat("programas EPG", ui.programCount, Modifier.testTag("system-program-count"))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TvAction("Atualizar tudo agora", { app.refreshAll() }, compact = true, primary = true, modifier = Modifier.testTag("system-refresh-all"))
        TvAction("Reindexar EPG (nome a nome)", { app.refreshEpgForAll() }, compact = true, modifier = Modifier.testTag("system-reindex-epg"))
        TvAction("Limpar índices de catálogo", { app.clearCaches() }, compact = true, modifier = Modifier.testTag("system-wipe") )
        TvAction("Apagar histórico de reprodução", { app.clearProgress() }, compact = true)
        TvAction("Restaurar preferências", { app.resetPreferences() }, compact = true)
    }
    Notice("Os índices locais são reconstruídos das fontes a qualquer momento — nada de catálogo embutido no APK.")
}

@Composable
private fun Stat(label: String, value: Int, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value.toString(), color = Color.White, style = MaterialTheme.typography.headlineMedium)
        Text(label, color = TvColors.Muted, style = MaterialTheme.typography.labelMedium)
    }
}

// ------------------------------------------------------------------ Sobre

@Composable
private fun AboutPanel(app: AppViewModel) {
    val version = remember { runCatching { app.graph.javaClass.`package`?.implementationVersion }.getOrNull() ?: "3.0.0" }
    Text("Sobre", style = MaterialTheme.typography.headlineMedium, color = Color.White)
    Text("SmartED TV $version", color = Color.White, style = MaterialTheme.typography.titleMedium)
    Notice("Android TV nativo (Kotlin + Jetpack Compose). Arquitetura de listas e repositórios locais (Kodi/Elementum/OnePlay-style) com EPG sincronizado, M3U/Xtream e integração HTTP ao daemon do Elementum. Identidade fumê com foco branco puro para D-pad.")
    Notice("Sem torrent engine própria, sem execução de plugins remotos, sem catálogo embutido: as fontes são suas, o índice é local.")
}

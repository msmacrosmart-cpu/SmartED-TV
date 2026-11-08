package com.smarted.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.bringIntoViewIfChildrenAreFocused
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smarted.tv.core.model.ListSource
import com.smarted.tv.core.model.SourceKind
import com.smarted.tv.core.model.Tab
import com.smarted.tv.core.model.UserPreferences
import com.smarted.tv.ui.components.Choice
import com.smarted.tv.ui.components.EmptyState
import com.smarted.tv.ui.components.FilterPill
import com.smarted.tv.ui.components.Notice
import com.smarted.tv.ui.components.PasteField
import com.smarted.tv.ui.components.RemoteImage
import com.smarted.tv.ui.components.SearchField
import com.smarted.tv.ui.components.SectionHead
import com.smarted.tv.ui.components.StatTile
import com.smarted.tv.ui.components.TvAction
import com.smarted.tv.ui.state.AppViewModel
import com.smarted.tv.ui.state.AppUi
import com.smarted.tv.ui.theme.LocalCompact
import com.smarted.tv.ui.theme.TvColors
import com.smarted.tv.ui.theme.dpx
import com.smarted.tv.ui.theme.spx
import com.smarted.tv.ui.theme.whiteFocus

/**
 * Configurações no padrão do app de referência: menu lateral em vidro fumê + painel por seção.
 * Fontes (M3U / Xtream Codes / repositório estilo OnePlay) com formulários à prova de erro
 * (colar da área de transferência, auto-preenchimento do esquema http), sincronização visível
 * e ações por fonte. Backend intocado: SourceManager/IptvStore/CatalogRepository.
 */
private enum class SettingsPage(val label: String) {
    SOURCES("Fontes"), ELEMENTUM("Elementum"), PLAYBACK("Reprodução"), INTERFACE("Interface"), SYSTEM("Sistema"), ABOUT("Sobre"),
}

@Composable
fun SettingsScreen(app: AppViewModel) {
    var page by remember { mutableStateOf(SettingsPage.SOURCES) }
    val ui by app.ui.collectAsState()

    Row(
        Modifier.fillMaxSize().padding(horizontal = dpx(24), vertical = dpx(6)),
        horizontalArrangement = Arrangement.spacedBy(dpx(14)),
    ) {
        Column(
            Modifier.fillMaxHeight().width(dpx(300)).clip(RoundedCornerShape(dpx(26)))
                .background(Brush.verticalGradient(listOf(Color(0x14FFFFFF), Color(0x08FFFFFF))))
                .border(1.dp, TvColors.Outline, RoundedCornerShape(dpx(26)))
                .padding(dpx(16)),
            verticalArrangement = Arrangement.spacedBy(dpx(8)),
        ) {
            SectionHead("Configurações")
            SettingsPage.entries.forEach { entry ->
                SettingsMenuRow(entry.label, page == entry) { page = entry }
            }
        }
        Column(
            Modifier.fillMaxHeight().weight(1f).clip(RoundedCornerShape(dpx(26)))
                .background(Brush.verticalGradient(listOf(Color(0x14FFFFFF), Color(0x08FFFFFF))))
                .border(1.dp, TvColors.Outline, RoundedCornerShape(dpx(26)))
                .padding(dpx(24))
                // tudo cabe? ótimo — e o que não couber rola, seguindo o foco do D-pad.
                .imePadding()
                .verticalScroll(rememberScrollState())
                .bringIntoViewIfChildrenAreFocused(),
            verticalArrangement = Arrangement.spacedBy(dpx(12)),
        ) {
            if (ui.busy.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dpx(12)), modifier = Modifier.testTag("settings-busy")) {
                    Text("⟳", color = Color.White, fontWeight = FontWeight.Bold, fontSize = spx(22))
                    Text(ui.busy, color = Color(0xFFCFD2D8), fontSize = spx(17), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (ui.message.isNotBlank()) Text("✓ ${ui.message}", color = Color(0xFF9FE8B0), fontSize = spx(16), modifier = Modifier.testTag("settings-message"))
            if (ui.error.isNotBlank()) Text("✕ ${ui.error}", color = Color(0xFFFFC9C9), fontSize = spx(16), maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("settings-error"))
            when (page) {
                SettingsPage.SOURCES -> SourcesPanel(app, ui, Modifier.fillMaxWidth())
                SettingsPage.ELEMENTUM -> ElementumPanel(app, ui)
                SettingsPage.PLAYBACK -> { val prefs by app.preferences.collectAsState(); PlaybackPanel(prefs, app) }
                SettingsPage.INTERFACE -> { val prefs by app.preferences.collectAsState(); InterfacePanel(prefs, app) }
                SettingsPage.SYSTEM -> SystemPanel(app, ui)
                SettingsPage.ABOUT -> AboutPanel(app)
            }
        }
    }
}

@Composable
private fun SettingsMenuRow(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier.fillMaxWidth().testTag("settings-menu-${label.lowercase()}")
            .onFocusChanged { focused = it.isFocused }
            .whiteFocus(focused, selected, dpx(18))
            .padding(dpx(3))
            .clip(RoundedCornerShape(dpx(14)))
            .background(if (selected) Color(0x2BFFFFFF) else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = dpx(18), vertical = dpx(13)),
    ) {
        Text(label, color = Color.White, fontSize = spx(19), fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

// ---------------------------------------------------------------- Fontes

@Composable
private fun SourcesPanel(app: AppViewModel, ui: AppUi, modifier: Modifier = Modifier) {
    var editing by remember { mutableStateOf<SettingsForm?>(null) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(dpx(14))) {
        Text("Listas, painéis e repositórios", color = Color.White, fontSize = spx(30), fontWeight = FontWeight.SemiBold)
        Text(
            "Cada fonte guarda canais, VOD e XMLTV no banco local e re-sincroniza no intervalo configurado. Nada é embutido no APK.",
            color = TvColors.Muted, fontSize = spx(16),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(dpx(14))) {
            AddTile("Lista M3U", "URL + EPG opcional", Icons.Default.Add, "form-open-m3u") { editing = SettingsForm.M3U }
            AddTile("Painel Xtream", "host:porta + senha", Icons.Default.Add, "form-open-xtream") { editing = SettingsForm.XTREAM }
            AddTile("Repositório", "JSON estilo OnePlay", Icons.Default.Add, "form-open-repo") { editing = SettingsForm.REPO }
        }
        val form = editing
        if (form != null) {
            SourceForm(form, onDismiss = { editing = null }, app = app)
        }
        if (ui.sources.isEmpty() && form == null) {
            EmptyState(
                "Nenhuma fonte cadastrada",
                "Use um dos botões acima. Dica: na TV, cole a URL com o botão “Colar” (área de transferência do celular via Nearby/adjacente ou app de digitação).",
                Modifier.fillMaxWidth(),
            )
        }
        if (ui.sources.isNotEmpty()) {
            SectionHead("Fontes (${ui.sources.size})")
            Column(verticalArrangement = Arrangement.spacedBy(dpx(12))) {
                ui.sources.forEach { source -> SourceCard(source, app, interactive = form == null) }
            }
        }
    }
}

@Composable
private fun RowScope.AddTile(label: String, sub: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tag: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier.weight(1f).testTag(tag)
            .onFocusChanged { focused = it.isFocused }
            .whiteFocus(focused, radius = dpx(24))
            .padding(dpx(3))
            .clip(RoundedCornerShape(dpx(18)))
            .background(if (focused) Color(0x2EFFFFFF) else Color(0x12FFFFFF))
            .border(1.dp, TvColors.Outline, RoundedCornerShape(dpx(18)))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = dpx(18), vertical = dpx(14)),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dpx(12)),
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(dpx(26)))
        Column(Modifier.weight(1f)) {
            Text(label, color = Color.White, fontSize = spx(19), fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, color = TvColors.Muted, fontSize = spx(14), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private enum class SettingsForm { M3U, XTREAM, REPO }

private fun fixUrl(raw: String): String {
    val t = raw.trim()
    return when {
        t.isBlank() -> ""
        t.startsWith("http://") || t.startsWith("https://") || t.startsWith("file://") -> t
        else -> "http://$t"
    }
}

@Composable
private fun SourceForm(form: SettingsForm, onDismiss: () -> Unit, app: AppViewModel) {
    var name by remember(form) { mutableStateOf("") }
    var url by remember(form) { mutableStateOf("") }
    var epg by remember(form) { mutableStateOf("") }
    var user by remember(form) { mutableStateOf("") }
    var pass by remember(form) { mutableStateOf("") }
    var interval by remember(form) { mutableStateOf("720") }
    val urlFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { urlFocus.requestFocus() } }

    val urlReady = url.isNotBlank() && (form != SettingsForm.XTREAM || (user.isNotBlank() && pass.isNotBlank()))
    val problem = when {
        url.isBlank() -> "Preencha a URL (use “Colar” para trazer da área de transferência)."
        form == SettingsForm.XTREAM && (user.isBlank() || pass.isBlank()) -> "Painel Xtream exige usuário e senha."
        !url.startsWith("http://") && !url.startsWith("https://") -> "Sem esquema — vamos completar com http:// ao salvar."
        else -> ""
    }

    fun submit() {
        val minutes = interval.toIntOrNull()?.coerceIn(0, 10_080) ?: 720
        val fixedUrl = fixUrl(url)
        when (form) {
            SettingsForm.M3U -> app.addM3u(name.trim(), fixedUrl, fixUrl(epg))
            SettingsForm.XTREAM -> app.addXtream(name.trim(), fixedUrl, user.trim(), pass.trim(), fixUrl(epg))
            SettingsForm.REPO -> app.addRepository(name.trim(), fixedUrl)
        }
        onDismiss()
    }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(dpx(20)))
            .background(Brush.verticalGradient(listOf(Color(0x1AFFFFFF), Color(0x0DFFFFFF))))
            .border(1.dp, TvColors.Outline, RoundedCornerShape(dpx(20)))
            .padding(dpx(20)),
        verticalArrangement = Arrangement.spacedBy(dpx(10)),
    ) {
        Text(
            when (form) {
                SettingsForm.M3U -> "Nova lista M3U/M3U8"
                SettingsForm.XTREAM -> "Painel Xtream Codes"
                SettingsForm.REPO -> "Repositório de fontes (JSON estilo OnePlay)"
            },
            color = Color.White, fontSize = spx(24), fontWeight = FontWeight.SemiBold,
        )
        Text(
            when (form) {
                SettingsForm.M3U -> "URL da playlist (.m3u/.m3u8) e, opcionalmente, a URL do EPG XMLTV. A importação já dispara no salvar."
                SettingsForm.XTREAM -> "Endereço base do painel (http://host:porta) + usuário e senha. Canais, filmes, séries e XMLTV vêm do próprio painel."
                SettingsForm.REPO -> "URL de um JSON {\"name\":…, \"sources\":[{name,kind,url,epg,update_minutes}]} — as fontes filhas ficam registradas e sincronizadas."
            },
            color = TvColors.Muted, fontSize = spx(16),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(dpx(12))) {
            PasteField(name, { name = it }, "Nome (opcional)", Modifier.weight(1f), tag = "form-name")
            PasteField(interval, { interval = it.filter(Char::isDigit).take(5) }, "auto-update (min; 0 = nunca)", Modifier.width(dpx(360)), tag = "form-interval")
        }
        PasteField(url, { url = it }, if (form == SettingsForm.XTREAM) "http://host:porta" else "https://servidor/lista.m3u", Modifier.fillMaxWidth().focusRequester(urlFocus), tag = "form-url")
        if (form == SettingsForm.XTREAM) {
            Row(horizontalArrangement = Arrangement.spacedBy(dpx(12))) {
                PasteField(user, { user = it }, "usuário", Modifier.weight(1f), tag = "form-user")
                PasteField(pass, { pass = it }, "senha", Modifier.weight(1f), tag = "form-pass")
            }
        }
        if (form != SettingsForm.REPO) {
            PasteField(epg, { epg = it }, "URL EPG XMLTV (opcional)", Modifier.fillMaxWidth(), tag = "form-epg")
        }
        if (problem.isNotBlank() && !urlReady) Text("• $problem", color = Color(0xFFFFD9A8), fontSize = spx(15))
        Row(horizontalArrangement = Arrangement.spacedBy(dpx(14)), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = dpx(6))) {
            TvAction("Salvar e sincronizar", ::submit, primary = true, enabled = urlReady, modifier = Modifier.testTag("form-save"))
            TvAction("Cancelar", onDismiss, compact = true, modifier = Modifier.testTag("form-cancel"))
        }
    }
}

@Composable
private fun SourceCard(source: ListSource, app: AppViewModel, interactive: Boolean) {
    var focused by remember { mutableStateOf(false) }
    var editEpg by remember(source.id) { mutableStateOf(false) }
    var epgDraft by remember(source.id) { mutableStateOf(source.epgUrl) }
    Column(
        Modifier.fillMaxWidth().testTag("source-${source.id}")
            .onFocusChanged { focused = it.isFocused }
            .whiteFocus(focused, radius = dpx(22))
            .padding(dpx(3))
            .clip(RoundedCornerShape(dpx(18)))
            .background(if (focused) Color(0x1AFFFFFF) else Color(0x0DFFFFFF))
            .padding(dpx(18)),
        verticalArrangement = Arrangement.spacedBy(dpx(8)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dpx(12))) {
            Text(source.name, color = Color.White, fontSize = spx(23), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            FilterPill(
                when (source.kind) { SourceKind.M3U -> "M3U"; SourceKind.XTREAM -> "XTREAM"; SourceKind.REPOSITORY -> "REPOSITÓRIO" },
                false, "source-kind-${source.id}",
            ) {}
            if (!source.enabled) Text("pausada", color = Color(0xFFFFB4AB), fontSize = spx(16))
        }
        Text(source.url, color = TvColors.Muted, fontSize = spx(15), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (source.status.isNotBlank()) Text(source.status, color = Color(0xFF9FE8B0), fontSize = spx(15), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("source-status-${source.id}"))
        if (source.error.isNotBlank()) Text(source.error, color = Color(0xFFFFC9C9), fontSize = spx(15), maxLines = 2, overflow = TextOverflow.Ellipsis)
        // linha de ações com rolagem horizontal — nunca corta "Remover" em telas menores
        Row(
            horizontalArrangement = Arrangement.spacedBy(dpx(10)), verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.horizontalScroll(rememberScrollState()).bringIntoViewIfChildrenAreFocused(),
        ) {
            FilterPill("⟳ Atualizar agora", false, "source-refresh-${source.id}") { if (interactive) app.refreshSource(source.id) }
            if (source.kind != SourceKind.REPOSITORY) FilterPill("Só EPG", false, "source-epg-${source.id}") { if (interactive) app.refreshEpgOnly(source.id) }
            FilterPill(if (source.enabled) "Pausar" else "Ativar", !source.enabled, "source-toggle-${source.id}") { if (interactive) app.toggleSource(source) }
            FilterPill(if (source.autoUpdateMinutes > 0) "Auto ${source.autoUpdateMinutes}m" else "Auto off", source.autoUpdateMinutes > 0, "source-interval-${source.id}") {
                if (interactive) app.setSourceInterval(source, if (source.autoUpdateMinutes > 0) 0 else 720)
            }
            if (source.kind == SourceKind.M3U) {
                FilterPill(if (source.epgUrl.isBlank()) "Especificar EPG" else "Editar EPG", editEpg, "source-epg-edit-open-${source.id}") { editEpg = !editEpg }
            }
            FilterPill("Remover", false, "source-remove-${source.id}") { if (interactive) app.removeSource(source) }
        }
        if (editEpg) {
            Row(horizontalArrangement = Arrangement.spacedBy(dpx(12)), verticalAlignment = Alignment.CenterVertically) {
                PasteField(epgDraft, { epgDraft = it }, "https://…/xmltv", Modifier.weight(1f), tag = "source-epg-edit-${source.id}")
                TvAction("Salvar", { app.setSourceEpgUrl(source, fixUrl(epgDraft)); editEpg = false }, compact = true, primary = true)
            }
        }
    }
}

// ---------------------------------------------------------------- Elementum

@Composable
private fun ElementumPanel(app: AppViewModel, ui: AppUi) {
    val state by app.elementum.collectAsState()
    val prefs by app.preferences.collectAsState()
    var host by remember(prefs.elementumHost) { mutableStateOf(prefs.elementumHost) }
    var port by remember(prefs.elementumPort) { mutableStateOf(prefs.elementumPort.toString()) }
    Text("Elementum (torrents/VOD)", color = Color.White, fontSize = spx(30), fontWeight = FontWeight.SemiBold)
    Notice("Integração com o daemon local do Elementum (Kodi add-on) via HTTP: status, torrents ativos e reload de provedores. O SmartED não executa torrent nem plugins — a reprodução direta acontece quando o daemon expõe URL HTTP; caso contrário o conteúdo abre no Kodi.")
    Row(horizontalArrangement = Arrangement.spacedBy(dpx(12)), verticalAlignment = Alignment.CenterVertically) {
        Choice("Integração ativada", prefs.elementumEnabled, { app.preferences { it.copy(elementumEnabled = !it.elementumEnabled) } }, modifier = Modifier.testTag("elementum-toggle"))
        Spacer(Modifier.weight(1f))
        Text(
            if (prefs.elementumEnabled && state.online) "ONLINE · ${state.activeTorrents} torrent(s)" else if (prefs.elementumEnabled) "OFFLINE" else "desativado",
            color = if (state.online) Color(0xFF9FE8B0) else TvColors.Muted, fontSize = spx(19),
            modifier = Modifier.testTag("elementum-status"),
        )
    }
    Notice(state.message)
    Row(horizontalArrangement = Arrangement.spacedBy(dpx(12)), verticalAlignment = Alignment.CenterVertically) {
        PasteField(host, { host = it }, "host (ex.: 127.0.0.1)", Modifier.weight(1f), tag = "elementum-host")
        PasteField(port, { port = it.filter(Char::isDigit).take(5) }, "porta", Modifier.width(dpx(220)), tag = "elementum-port")
        TvAction("Aplicar", {
            app.preferences { it.copy(elementumHost = host.ifBlank { "127.0.0.1" }, elementumPort = port.toIntOrNull()?.coerceIn(1, 65535) ?: 65064) }
            app.elementumTest()
        }, compact = true, modifier = Modifier.testTag("elementum-apply"))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(dpx(12))) {
        TvAction("Testar conexão", app::elementumTest, compact = true, modifier = Modifier.testTag("elementum-test"))
        TvAction(if (state.updating) "Atualizando…" else "Atualizar Elementum (reload provedores)", app::elementumUpdate, compact = true, enabled = !state.updating, modifier = Modifier.testTag("elementum-update"))
    }
}

// ---------------------------------------------------------------- Reprodução

@Composable
private fun PlaybackPanel(prefs: UserPreferences, app: AppViewModel) {
    Text("Reprodução", color = Color.White, fontSize = spx(30), fontWeight = FontWeight.SemiBold)
    Notice("Áudio/legendas e saltos aplicados pelo player nativo (media3). Idiomas seguem o código BCP-47 preferido do stream.")
    LabeledChips("Idioma do áudio", listOf("pt", "es", "en", "")) { code -> app.preferences { it.copy(audioLanguage = code) } }
    LabeledChips("Idioma da legenda", listOf("pt", "en", "es", "")) { code -> app.preferences { it.copy(subtitleLanguage = code) } }
    Row(horizontalArrangement = Arrangement.spacedBy(dpx(12))) {
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
    Column(verticalArrangement = Arrangement.spacedBy(dpx(6))) {
        SectionHead(label)
        Row(horizontalArrangement = Arrangement.spacedBy(dpx(10))) {
            options.forEach { option ->
                FilterPill(option?.toString()?.ifBlank { "auto" } ?: "—", false, "chip-$label-$option") { onSelect(option) }
            }
        }
    }
}

// ---------------------------------------------------------------- Interface

@Composable
private fun InterfacePanel(prefs: UserPreferences, app: AppViewModel) {
    Text("Interface", color = Color.White, fontSize = spx(30), fontWeight = FontWeight.SemiBold)
    Notice("Black Glassmorphism fumê com foco branco puro é o único tema (identidade fixa para D-pad, escala de design 1920×1080).")
    Row(horizontalArrangement = Arrangement.spacedBy(dpx(12))) {
        Choice("Pré-visualização no Canais", prefs.previewPlayerEnabled, { app.preferences { it.copy(previewPlayerEnabled = !it.previewPlayerEnabled) } }, modifier = Modifier.testTag("pref-preview"))
    }
    LabeledChips("Janela do EPG (horas carregados)", listOf(6, 12, 24, 48)) { hours -> app.preferences { it.copy(epgWindowHours = hours) } }
    LabeledChips("Auto-update global das fontes (min)", listOf(0, 60, 720, 4320)) { minutes -> app.preferences { it.copy(autoUpdateMinutes = minutes) } }
}

// ---------------------------------------------------------------- Sistema

@Composable
private fun SystemPanel(app: AppViewModel, ui: AppUi) {
    Text("Sistema e repositório local", color = Color.White, fontSize = spx(30), fontWeight = FontWeight.SemiBold)
    Row(horizontalArrangement = Arrangement.spacedBy(dpx(24)), verticalAlignment = Alignment.CenterVertically) {
        StatTile("canais", ui.counts[Tab.CHANNELS] ?: 0, tag = "stat-channels")
        StatTile("filmes", ui.counts[Tab.MOVIES] ?: 0, tag = "stat-movies")
        StatTile("séries", ui.counts[Tab.SERIES] ?: 0, tag = "stat-series")
        StatTile("programas EPG", ui.programCount, tag = "system-program-count")
    }
    Row(horizontalArrangement = Arrangement.spacedBy(dpx(12))) {
        TvAction("Atualizar tudo agora", { app.refreshAll() }, compact = true, primary = true, modifier = Modifier.testTag("system-refresh-all"))
        TvAction("Reindexar EPG (nome a nome)", { app.refreshEpgForAll() }, compact = true, modifier = Modifier.testTag("system-reindex-epg"))
        TvAction("Limpar índices de catálogo", { app.clearCaches() }, compact = true, modifier = Modifier.testTag("system-wipe"))
        TvAction("Apagar histórico de reprodução", { app.clearProgress() }, compact = true)
        TvAction("Restaurar preferências", { app.resetPreferences() }, compact = true)
    }
    Notice("Os índices locais são reconstruídos das fontes a qualquer momento — nada de catálogo embutido no APK.")
}

// ------------------------------------------------------------------ Sobre

@Composable
private fun AboutPanel(app: AppViewModel) {
    val version = remember { runCatching { app.graph.javaClass.`package`?.implementationVersion }.getOrNull() ?: "3.0.0" }
    Text("Sobre", color = Color.White, fontSize = spx(30), fontWeight = FontWeight.SemiBold)
    Text("SmartED TV $version", color = Color.White, fontSize = spx(21), fontWeight = FontWeight.Medium)
    Notice("Android TV nativo (Kotlin + Jetpack Compose). Arquitetura de listas e repositórios locais (Kodi/Elementum/OnePlay-style) com EPG sincronizado, M3U/Xtream e integração HTTP ao daemon do Elementum. Camada visual inspirada na composição do IPVIO-Stream (GPLv3), reimplementada sob Black Glassmorphism fumê com foco branco puro.")
    Notice("Sem torrent engine própria, sem execução de plugins remotos, sem catálogo embutido: as fontes são suas, o índice é local.")
}

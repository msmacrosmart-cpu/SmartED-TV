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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smarted.tv.core.data.Settings
import com.smarted.tv.core.data.SettingsStore
import com.smarted.tv.core.model.MediaKind
import com.smarted.tv.ui.components.AccentDot
import com.smarted.tv.ui.components.DividerLine
import com.smarted.tv.ui.components.EdBadge
import com.smarted.tv.ui.components.EdChip
import com.smarted.tv.ui.components.EdIcons
import com.smarted.tv.ui.components.EdPrimaryButton
import com.smarted.tv.ui.components.EdSecondaryButton
import com.smarted.tv.ui.components.EdTextField
import com.smarted.tv.ui.components.EmptyState
import com.smarted.tv.ui.components.ScreenTitle
import com.smarted.tv.ui.components.SettingRow
import com.smarted.tv.ui.components.SettingsGroup
import com.smarted.tv.ui.components.edInteraction
import com.smarted.tv.ui.state.SettingsViewModel
import com.smarted.tv.ui.theme.Accents
import com.smarted.tv.ui.theme.Ed

/**
 * Configurações — inclui a aba dedicada **Trocar / Gerenciar Repositório**,
 * além de fontes públicas, EPG (XMLFALLBACK), TMDB, indexador e aparência.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onOpenAddons: () -> Unit,
    onOpenRepository: () -> Unit,
    onOpenSources: () -> Unit,
    onOpenEpg: () -> Unit,
    onOpenTmdbSite: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val palette = Ed.palette
    val type = Ed.type
    val settings = state.settings
    var showTmdbEditor by remember { mutableStateOf(false) }
    var tmdbKey by remember { mutableStateOf(settings.tmdbKey) }

    LaunchedEffect(Unit) { viewModel.refreshSources() }
    LaunchedEffect(state.message) {
        if (state.message != null) {
            kotlinx.coroutines.delay(4_000L)
            viewModel.consumeMessage()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(palette.bg).padding(horizontal = 32.dp, vertical = 18.dp),
    ) {
        ScreenTitle(
            title = "Ajustes",
            subtitle = "SmartED TV · Kotlin + Compose",
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(top = 10.dp),
            contentPadding = PaddingValues(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item("group-repo") {
                SettingsGroup("Repositório de addons") {
                    SettingRow(
                        icon = EdIcons.addon,
                        title = "Trocar / Gerenciar Repositório",
                        subtitle = "Ativo: ${state.repoName}",
                        value = state.repoUrl.removePrefix("https://").removeSuffix("/"),
                        onClick = onOpenRepository,
                    )
                    SettingRow(
                        icon = EdIcons.cloud,
                        title = "Addons instalados",
                        subtitle = "Gerenciar addons do repositório ativo",
                        value = "${state.addonCount}",
                        onClick = onOpenAddons,
                    )
                    SettingRow(
                        icon = EdIcons.refresh,
                        title = "Sincronizar repositório agora",
                        value = if (state.busy) "…" else null,
                        onClick = { viewModel.setRepo(state.repoUrl, state.repoName) },
                    )
                }
            }

            item("group-sources") {
                SettingsGroup("Conteúdo e indexação") {
                    SettingRow(
                        icon = EdIcons.live,
                        title = "Fontes públicas",
                        subtitle = "${state.catalogCounts.first} canais · ${state.catalogCounts.second} filmes · ${state.catalogCounts.third} séries",
                        value = "${state.sources.count { it.enabled }}/${state.sources.size}",
                        onClick = onOpenSources,
                    )
                    SettingRow(
                        icon = EdIcons.guide,
                        title = "EPG / XMLTV",
                        subtitle = "Fallback: ${SettingsStore.DEFAULT_EPG_URL.removePrefix("https://")}",
                        value = state.epgStatus.take(24),
                        onClick = onOpenEpg,
                    )
                    SettingRow(
                        icon = EdIcons.bolt,
                        title = "Indexador de fontes",
                        subtitle = state.indexerSummary.ifBlank { "Varredura e manutenção de links" },
                        onClick = { viewModel.runIndexerScan() },
                    )
                    SettingRow(
                        icon = EdIcons.tune,
                        title = "Manutenção de links ativos",
                        subtitle = "Testa amostras e descarta links mortos",
                        onClick = { viewModel.runMaintenance() },
                    )
                    SettingRow(
                        icon = EdIcons.refresh,
                        title = "Recarregar catálogo completo",
                        onClick = { viewModel.reloadCatalog() },
                    )
                }
            }

            item("group-metadata") {
                SettingsGroup("Metadados e reprodução") {
                    SettingRow(
                        icon = EdIcons.info,
                        title = "Chave da API TMDB",
                        subtitle = if (state.tmdbConfigured) {
                            "Configurada · notas, elenco e trailer ativos"
                        } else {
                            "Adicione uma chave para notas, elenco e trailer"
                        },
                        value = if (state.tmdbConfigured) "ativa" else "vazia",
                        onClick = { showTmdbEditor = !showTmdbEditor },
                    )
                    if (showTmdbEditor) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            EdTextField(
                                value = tmdbKey,
                                onValueChange = { tmdbKey = it },
                                placeholder = "Cole a chave v3 do TMDB",
                                label = "TMDB API KEY",
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                EdPrimaryButton(
                                    label = "Salvar chave",
                                    icon = EdIcons.check,
                                    onClick = { viewModel.setTmdbKey(tmdbKey) },
                                )
                                EdSecondaryButton(
                                    label = "Abrir TMDB",
                                    icon = EdIcons.language,
                                    onClick = onOpenTmdbSite,
                                )
                            }
                        }
                    }
                    SettingRow(
                        icon = EdIcons.play,
                        title = "Trailers do YouTube sem anúncios",
                        subtitle = "Player nativo integrado (youtube-nocookie + bloqueio de anúncios)",
                        toggle = settings.ytNoAds,
                        onToggle = { v -> viewModel.update { it.copy(ytNoAds = v) } },
                        onClick = { viewModel.update { it.copy(ytNoAds = !settings.ytNoAds) } },
                    )
                    SettingRow(
                        icon = EdIcons.play,
                        title = "Reproduzir próximo episódio",
                        toggle = settings.autoplayNext,
                        onToggle = { v -> viewModel.update { it.copy(autoplayNext = v) } },
                        onClick = { viewModel.update { it.copy(autoplayNext = !settings.autoplayNext) } },
                    )
                }
            }

            item("group-appearance") {
                SettingsGroup("Aparência") {
                    SettingRow(
                        icon = EdIcons.tune,
                        title = "Tema",
                        value = if (settings.themeDark) "Escuro" else "Claro",
                        onClick = { viewModel.update { s -> s.copy(themeDark = !s.themeDark) } },
                    )
                    SettingRow(
                        icon = EdIcons.hd,
                        title = "Cor de destaque",
                        value = Accents.byId(settings.accentId).label,
                        onClick = {
                            val all = Accents.all
                            val index = all.indexOfFirst { it.id == settings.accentId }
                            viewModel.update { it.copy(accentId = all[(index + 1) % all.size].id) }
                        },
                    )
                    SettingRow(
                        icon = EdIcons.settings,
                        title = "Tamanho da interface",
                        value = "${(settings.fontScale * 100).toInt()}%",
                        onClick = {
                            val next = when {
                                settings.fontScale < 0.95f -> 1f
                                settings.fontScale < 1.05f -> 1.12f
                                settings.fontScale < 1.15f -> 1.25f
                                else -> 0.9f
                            }
                            viewModel.update { it.copy(fontScale = next) }
                        },
                    )
                    SettingRow(
                        icon = EdIcons.language,
                        title = "Overscan da TV (bordas seguras)",
                        value = String.format("%.1f%%", settings.overscanPercent),
                        onClick = {
                            val next = when {
                                settings.overscanPercent < 2f -> 3f
                                settings.overscanPercent < 4f -> 0f
                                else -> 1.5f
                            }
                            viewModel.update { it.copy(overscanPercent = next) }
                        },
                    )
                }
            }

            item("group-maintenance") {
                SettingsGroup("Armazenamento e manutenção") {
                    SettingRow(
                        icon = EdIcons.folder,
                        title = "Tamanho do cache",
                        value = state.cacheLabel.ifBlank { "—" },
                        onClick = { viewModel.clearCache() },
                    )
                    SettingRow(
                        icon = EdIcons.delete,
                        title = "Limpar cache",
                        subtitle = "Catálogo, imagens e metadados em cache",
                        onClick = { viewModel.clearCache() },
                        danger = true,
                    )
                    SettingRow(
                        icon = EdIcons.cloudDownload,
                        title = "Atualização remota (nuvem)",
                        subtitle = "Sincroniza fontes a partir de um catálogo JSON remoto",
                        onClick = {
                            viewModel.syncCloudCatalog(
                                "https://raw.githubusercontent.com/msmacrosmart-cpu/SmartED-TV/main/app/src/main/assets/catalog/sources.json",
                            )
                        },
                    )
                }
            }

            item("about") {
                Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DividerLine()
                    Text(
                        text = "SmartED TV 1.0.0 · Brazuca Play 2.4.0 · Addons estilo Kodi",
                        style = type.caption,
                        color = palette.fg3,
                    )
                    Text(
                        text = "Conteúdo fornecido por fontes públicas e pelo repositório ativo. " +
                            "O app não hospeda mídia; apenas indexa e exibe as fontes configuradas.",
                        style = type.caption,
                        color = palette.fg3,
                    )
                }
            }
        }

        state.message?.let { msg ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.surface2)
                    .padding(12.dp),
            ) {
                Text(msg, style = type.bodySmall, color = palette.fg)
            }
        }
    }
}

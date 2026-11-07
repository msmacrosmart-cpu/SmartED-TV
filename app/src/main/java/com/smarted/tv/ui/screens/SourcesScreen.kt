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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smarted.tv.R
import com.smarted.tv.core.model.MediaKind
import com.smarted.tv.core.model.PublicSource
import com.smarted.tv.ui.components.AccentDot
import com.smarted.tv.ui.components.EdBadge
import com.smarted.tv.ui.components.EdChip
import com.smarted.tv.ui.components.EdIcons
import com.smarted.tv.ui.components.EdPrimaryButton
import com.smarted.tv.ui.components.EdSecondaryButton
import com.smarted.tv.ui.components.EdSwitch
import com.smarted.tv.ui.components.EdTextField
import com.smarted.tv.ui.components.EmptyState
import com.smarted.tv.ui.components.ScreenTitle
import com.smarted.tv.ui.components.TargetPicker
import com.smarted.tv.ui.components.labelFor
import com.smarted.tv.core.util.SourceTargeting
import com.smarted.tv.ui.state.SettingsViewModel
import com.smarted.tv.ui.theme.Ed

/** Fontes públicas do indexador (M3U/XTREAM/XMLTV). */
@Composable
fun SourcesScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val palette = Ed.palette
    val type = Ed.type

    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    // Abas marcadas no painel — o usuário decide onde o conteúdo entra.
    var targets by remember { mutableStateOf(listOf(MediaKind.LIVE)) }
    // Fonte em edição de abas (null = painel fechado).
    var editing by remember { mutableStateOf<PublicSource?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(palette.bg).padding(horizontal = 32.dp),
        contentPadding = PaddingValues(top = 18.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item("title") {
            ScreenTitle(
                title = "Fontes públicas",
                subtitle = "${state.sources.size} cadastradas · ${state.sources.count { it.enabled }} ativas",
            )
        }

        item("actions") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EdSecondaryButton(
                    label = "Varrer fontes",
                    icon = EdIcons.bolt,
                    onClick = { viewModel.runIndexerScan() },
                )
                EdSecondaryButton(
                    label = "Manutenção de links",
                    icon = EdIcons.tune,
                    onClick = { viewModel.runMaintenance() },
                )
                EdSecondaryButton(
                    label = "Recarregar catálogo",
                    icon = EdIcons.refresh,
                    onClick = { viewModel.reloadCatalog() },
                )
            }
        }

        if (state.sources.isEmpty()) {
            item("empty") {
                EmptyState(
                    title = "Nenhuma fonte",
                    message = "As fontes públicas padrão são restauradas ao reabrir o app.",
                    icon = EdIcons.live,
                )
            }
        }

        items(state.sources, key = { it.id }) { source ->
            SourceRow(
                source = source,
                onToggle = { viewModel.toggleSource(source.id, it) },
                onRemove = { viewModel.removeSource(source.id) },
                onEditTargets = { editing = source },
            )
        }

        item("form") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.surface)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.sources_add_title),
                    style = type.sectionTitle,
                    color = palette.fg,
                )
                Text(
                    text = stringResource(R.string.sources_add_hint),
                    style = type.caption,
                    color = palette.fg3,
                )
                EdTextField(value = name, onValueChange = { name = it }, placeholder = "Nome da fonte", label = "NOME")
                EdTextField(
                    value = url,
                    onValueChange = { url = it },
                    placeholder = "https://exemplo.com/lista.m3u",
                    label = "URL (M3U / XTREAM / XMLTV)",
                )
                // Painel de configuração: checkboxes por aba.
                TargetPicker(
                    selected = targets,
                    onToggle = { kind, checked ->
                        targets = if (checked) {
                            (targets + kind).distinct()
                        } else {
                            targets - kind
                        }
                    },
                )
                EdPrimaryButton(
                    label = "Adicionar fonte",
                    icon = EdIcons.add,
                    onClick = {
                        if (url.isNotBlank() && targets.isNotEmpty()) {
                            viewModel.addPublicSource(name, url, targets)
                            name = ""
                            url = ""
                        }
                    },
                )
                if (targets.isEmpty()) {
                    Text(
                        text = stringResource(R.string.sources_choose_targets),
                        style = type.caption,
                        color = palette.warn,
                    )
                }
            }
        }

        item("back") {
            EdSecondaryButton(label = "Voltar", onClick = onBack, icon = EdIcons.back)
        }
    }

    // Painel de edição das abas de uma fonte já cadastrada. Fica FORA do
    // LazyColumn: o escopo da lista (LazyListScope) não é @Composable, então
    // qualquer composable ali dentro quebra a compilação.
    editing?.let { source ->
        TargetsDialog(
            title = source.name,
            initial = source.targets,
            onDismiss = { editing = null },
            onConfirm = { selected ->
                viewModel.setSourceTargets(source.id, selected)
                editing = null
            },
        )
    }
}

@Composable
private fun SourceRow(
    source: PublicSource,
    onToggle: (Boolean) -> Unit,
    onRemove: () -> Unit,
    onEditTargets: () -> Unit,
) {
    val palette = Ed.palette
    val type = Ed.type
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .clip(shape)
            .background(palette.surface, shape)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AccentDot(
            color = when {
                !source.enabled -> palette.fg3
                source.healthy -> palette.ok
                else -> palette.bad
            },
            size = 8.dp,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = source.name,
                    style = type.rowTitle.copy(fontWeight = FontWeight.Medium),
                    color = palette.fg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Destino marcado pelo usuário: "Canais + Filmes", "Todas"…
                EdBadge(
                    text = SourceTargeting.describe(source.targets).lowercase(),
                    color = palette.fg3,
                )
                EdBadge(text = source.format.name, color = palette.accent.copy(alpha = 0.85f))
            }
            Text(
                text = source.url,
                style = type.caption,
                color = palette.fg3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            source.lastError?.let {
                Text("erro: $it", style = type.caption, color = palette.bad, maxLines = 1)
            }
        }
        if (source.lastCheckedMs > 0) {
            Text(
                text = if (source.healthy) "Disponível" else "Falhou",
                style = type.label,
                color = if (source.healthy) palette.ok else palette.bad,
            )
        }
        EdChip(label = stringResource(R.string.targets_change), selected = false, onClick = onEditTargets)
        EdChip(label = "Remover", selected = false, onClick = onRemove)
        EdSwitch(checked = source.enabled, onChange = onToggle)
    }
}

/**
 * Diálogo com o mesmo painel de checkboxes, para reconfigurar uma fonte que já
 * existe (sem precisar remover e adicionar de novo).
 */
@Composable
private fun TargetsDialog(
    title: String,
    initial: List<MediaKind>,
    onDismiss: () -> Unit,
    onConfirm: (List<MediaKind>) -> Unit,
) {
    val palette = Ed.palette
    val type = Ed.type
    var selected by remember { mutableStateOf(initial) }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(720.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(palette.surface)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = title, style = type.sectionTitle, color = palette.fg, maxLines = 1)
            TargetPicker(
                selected = selected,
                onToggle = { kind, checked ->
                    selected = if (checked) (selected + kind).distinct() else selected - kind
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EdPrimaryButton(
                    label = "Salvar",
                    icon = EdIcons.check,
                    onClick = { onConfirm(selected) },
                )
                EdSecondaryButton(label = "Cancelar", onClick = onDismiss)
            }
        }
    }
}

/** Configuração do EPG com fallback XMLTV explícito. */
@Composable
fun EpgScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val palette = Ed.palette
    val type = Ed.type

    var url by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(palette.bg).padding(horizontal = 32.dp),
        contentPadding = PaddingValues(top = 18.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item("title") {
            ScreenTitle(
                title = "EPG / XMLTV",
                subtitle = state.epgStatus.ifBlank { "Guia de programação" },
            )
        }

        item("info") {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.surface)
                    .padding(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Fallback automático", style = type.rowTitle, color = palette.fg)
                    Text(
                        text = "Se o addon do repositório não trouxer grade, o app tenta as fontes abaixo " +
                            "em ordem de prioridade — começando por https://epg.best/brazil.xml.",
                        style = type.bodySmall,
                        color = palette.fg2,
                    )
                    Text(
                        text = "Casamento por tvg-id, com fallback por nome normalizado do canal.",
                        style = type.caption,
                        color = palette.fg3,
                    )
                }
            }
        }

        item("actions") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EdSecondaryButton(label = "Recarregar EPG", icon = EdIcons.refresh, onClick = { viewModel.reloadEpg() })
                EdSecondaryButton(
                    label = "Restaurar padrão",
                    icon = EdIcons.cloud,
                    onClick = {
                        viewModel.addEpgSource(
                            com.smarted.tv.core.data.SettingsStore.DEFAULT_EPG_URL,
                            "EPG Brasil (padrão)",
                        )
                    },
                )
            }
        }

        items(state.epgSources, key = { it.url }) { source ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.surface)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AccentDot(
                    color = when (source.status) {
                        "OK" -> palette.ok
                        "ERROR" -> palette.bad
                        "EMPTY" -> palette.warn
                        else -> palette.fg3
                    },
                    size = 8.dp,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(source.label, style = type.rowTitle, color = palette.fg, maxLines = 1)
                    Text(source.url, style = type.caption, color = palette.fg3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        text = if (source.programCount > 0) {
                            "${source.channelCount} canais · ${source.programCount} programas · " + epgStatusLabel(source.status)
                        } else {
                            epgStatusLabel(source.status)
                        },
                        style = type.caption,
                        color = palette.fg3,
                    )
                }
                EdChip(label = "Remover", selected = false, onClick = { viewModel.removeEpgSource(source.url) })
            }
        }

        item("form") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.surface)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Adicionar fonte XMLTV", style = type.sectionTitle, color = palette.fg)
                EdTextField(value = label, onValueChange = { label = it }, placeholder = "Rótulo", label = "RÓTULO")
                EdTextField(
                    value = url,
                    onValueChange = { url = it },
                    placeholder = "https://epg.exemplo.com/guia.xml",
                    label = "URL DO XMLTV",
                )
                EdPrimaryButton(
                    label = "Adicionar",
                    icon = EdIcons.add,
                    onClick = {
                        if (url.isNotBlank()) {
                            viewModel.addEpgSource(url, label)
                            url = ""
                            label = ""
                        }
                    },
                )
            }
        }

        item("back") {
            EdSecondaryButton(label = "Voltar", onClick = onBack, icon = EdIcons.back)
        }
    }
}

/** Rótulo em português para o estado de uma fonte de EPG. */
private fun epgStatusLabel(status: String?): String = when (status) {
    "OK" -> "grade disponível"
    "EMPTY" -> "sem programas"
    "ERROR" -> "falhou ao carregar"
    "UNKNOWN", null -> "aguardando sincronização"
    else -> "aguardando sincronização"
}

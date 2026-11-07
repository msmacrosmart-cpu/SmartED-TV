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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smarted.tv.R
import com.smarted.tv.core.addons.AddonKind
import com.smarted.tv.core.addons.InstalledAddon
import com.smarted.tv.ui.components.AccentDot
import com.smarted.tv.ui.components.Artwork
import com.smarted.tv.ui.components.EdBadge
import com.smarted.tv.ui.components.EdChip
import com.smarted.tv.ui.components.EdIcons
import com.smarted.tv.ui.components.EdPrimaryButton
import com.smarted.tv.ui.components.EdSecondaryButton
import com.smarted.tv.ui.components.EdSwitch
import com.smarted.tv.ui.components.EmptyState
import com.smarted.tv.ui.components.ScreenTitle
import com.smarted.tv.ui.components.TargetPicker
import com.smarted.tv.ui.state.AddonsViewModel
import com.smarted.tv.ui.theme.Ed

/**
 * Addons instalados no repositório ativo (arquitetura estilo Kodi).
 * O addon nativo é sempre habilitado e não pode ser removido.
 */
@Composable
fun AddonsScreen(
    viewModel: AddonsViewModel,
    onOpenRepository: () -> Unit,
    onOpenBrowser: () -> Unit,
    onOpenElementum: () -> Unit,
    onOpenMyList: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // O daemon do Elementum é local: uma consulta rápida na abertura já
    // mostra se ele está ligado, sem o usuário pedir.
    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.checkElementum() }
    val palette = Ed.palette
    val type = Ed.type
    // Addon cujas abas estão sendo reconfiguradas (null = painel fechado).
    var editingTargets by remember { mutableStateOf<InstalledAddon?>(null) }

    editingTargets?.let { addon ->
        AddonTargetsDialog(
            addon = addon,
            onDismiss = { editingTargets = null },
            onConfirm = { kinds ->
                viewModel.setAddonTargets(addon.id, kinds)
                editingTargets = null
            },
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().background(palette.bg).padding(horizontal = 32.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            ScreenTitle(
                title = "Addons",
                subtitle = "${state.addons.size} instalados · ${state.repositoryName}",
                modifier = Modifier.weight(1f),
            )
            EdPrimaryButton(label = "Explorar", onClick = onOpenBrowser, icon = EdIcons.folder)
            Box(Modifier.size(10.dp))
            EdSecondaryButton(label = "Repositório", onClick = onOpenRepository, icon = EdIcons.cloud)
            Box(Modifier.size(10.dp))
            EdSecondaryButton(label = "Sincronizar", onClick = { viewModel.sync() }, icon = EdIcons.refresh)
            Box(Modifier.size(10.dp))
            EdSecondaryButton(
                label = "Minha lista" + if (state.savedCount > 0) " (${state.savedCount})" else "",
                onClick = onOpenMyList,
                icon = EdIcons.check,
            )
        }

        // Elementum: addon de torrentes do Vikings, servido pelo daemon local.
        ElementumCard(
            status = state.elementum,
            busy = state.elementumBusy,
            onOpen = onOpenElementum,
            onCheck = { viewModel.checkElementum() },
        )

        if (state.addons.isEmpty()) {
            EmptyState(
                title = "Nenhum addon instalado",
                message = "Sincronize o repositório ativo ou restaure o padrão (Vikings) em Ajustes → Repositório.",
                icon = EdIcons.addon,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.addons, key = { it.id }) { addon ->
                    AddonRow(
                        addon = addon,
                        onToggle = { viewModel.toggleAddon(addon.id, it) },
                        onRemove = { viewModel.removeAddon(addon.id) },
                        onEditTargets = { editingTargets = addon },
                    )
                }
            }
        }

        EdSecondaryButton(label = "Voltar", onClick = onBack, icon = EdIcons.back)
    }
}

@Composable
private fun AddonRow(
    addon: InstalledAddon,
    onToggle: (Boolean) -> Unit,
    onRemove: () -> Unit,
    onEditTargets: () -> Unit,
) {
    val palette = Ed.palette
    val type = Ed.type
    val builtin = addon.kind == AddonKind.BUILTIN
    val shape = RoundedCornerShape(14.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .clip(shape)
            .background(palette.surface, shape)
            .then(if (builtin) Modifier.border(1.dp, palette.accent.copy(alpha = 0.55f), shape) else Modifier)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(palette.surface2),
        ) {
            Artwork(addon.icon, addon.name, Modifier.fillMaxSize())
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = addon.name,
                    style = type.rowTitle.copy(fontWeight = FontWeight.SemiBold),
                    color = palette.fg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("v${addon.version}", style = type.caption, color = palette.fg3)
                if (builtin) EdBadge("NATIVO", palette.accent, filled = true)
                AccentDot(
                    color = if (addon.enabled) palette.ok else palette.fg3,
                    size = 6.dp,
                )
            }
            Text(
                text = addon.summary.ifBlank { addon.summaryLong },
                style = type.caption,
                color = palette.fg3,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = addon.types.joinToString(" · ") { kindLabel(it) },
                style = type.label,
                color = palette.fg3,
            )
        }
        EdChip(label = stringResource(R.string.targets_change), selected = false, onClick = onEditTargets)
        if (!builtin) {
            EdSecondaryButton(label = "Remover", onClick = onRemove, icon = EdIcons.delete)
        }
        EdSwitch(checked = addon.enabled, onChange = { if (!builtin || it) onToggle(it) })
    }
}

/**
 * Painel de abas do addon: mesmo componente do cadastro de fontes.
 * Aqui ele responde "em quais abas este addon pode contribuir?".
 */
@Composable
private fun AddonTargetsDialog(
    addon: InstalledAddon,
    onDismiss: () -> Unit,
    onConfirm: (List<com.smarted.tv.core.model.MediaKind>) -> Unit,
) {
    val palette = Ed.palette
    val type = Ed.type
    var selected by remember { mutableStateOf(addon.types.toList()) }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(760.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(palette.surface)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(addon.name, style = type.sectionTitle, color = palette.fg, maxLines = 1)
            Text(addon.summary, style = type.caption, color = palette.fg3, maxLines = 2)
            TargetPicker(
                selected = selected,
                onToggle = { kind, checked ->
                    selected = if (checked) (selected + kind).distinct() else selected - kind
                },
                hint = stringResource(R.string.addon_targets_hint),
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

private fun kindLabel(kind: com.smarted.tv.core.model.MediaKind): String = when (kind) {
    com.smarted.tv.core.model.MediaKind.LIVE -> "canais"
    com.smarted.tv.core.model.MediaKind.MOVIE -> "filmes"
    com.smarted.tv.core.model.MediaKind.SERIES -> "séries"
    com.smarted.tv.core.model.MediaKind.EPISODE -> "episódios"
    com.smarted.tv.core.model.MediaKind.UNKNOWN -> "—"
}

/**
 * Cartão do Elementum.
 *
 * O addon vem do repositório Vikings, mas quem entrega o vídeo é o **daemon
 * local** (`127.0.0.1:65220`): o app não executa plugin Python. O cartão mostra
 * o estado real desse serviço e o caminho para abrir a busca.
 */
@Composable
private fun ElementumCard(
    status: com.smarted.tv.core.addons.Elementum.Status?,
    busy: Boolean,
    onOpen: () -> Unit,
    onCheck: () -> Unit,
) {
    val palette = Ed.palette
    val type = Ed.type
    val shape = RoundedCornerShape(16.dp)
    val online = status?.online == true

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp)
            .clip(shape)
            .background(palette.surface, shape)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier.size(50.dp).clip(RoundedCornerShape(14.dp)).background(palette.surface2),
            contentAlignment = Alignment.Center,
        ) {
            Icon(EdIcons.addon, contentDescription = null, tint = palette.accent, modifier = Modifier.size(26.dp))
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Elementum",
                    style = type.rowTitle.copy(fontWeight = FontWeight.SemiBold),
                    color = palette.fg,
                )
                EdBadge("TORRENTES", palette.accent, filled = true)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AccentDot(
                    color = when {
                        busy -> palette.warn
                        online -> palette.ok
                        status == null -> palette.fg3
                        else -> palette.bad
                    },
                    size = 6.dp,
                )
                Text(
                    text = when {
                        busy -> "verificando o daemon…"
                        online -> "ligado · daemon 127.0.0.1:65220"
                        else -> "desligado · abra o daemon do Elementum"
                    },
                    style = type.label,
                    color = palette.fg3,
                )
            }
            Text(
                text = "Filmes e séries por torrente — nunca canais ao vivo",
                style = type.caption,
                color = palette.fg3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        EdSecondaryButton(label = "Verificar", onClick = onCheck, icon = EdIcons.refresh)
        Box(Modifier.size(8.dp))
        EdPrimaryButton(label = "Abrir", onClick = onOpen, icon = EdIcons.play)
    }
}

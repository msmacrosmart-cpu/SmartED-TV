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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smarted.tv.core.addons.AddonKind
import com.smarted.tv.core.addons.InstalledAddon
import com.smarted.tv.ui.components.AccentDot
import com.smarted.tv.ui.components.Artwork
import com.smarted.tv.ui.components.EdBadge
import com.smarted.tv.ui.components.EdIcons
import com.smarted.tv.ui.components.EdSecondaryButton
import com.smarted.tv.ui.components.EdSwitch
import com.smarted.tv.ui.components.EmptyState
import com.smarted.tv.ui.components.ScreenTitle
import com.smarted.tv.ui.state.AddonsViewModel
import com.smarted.tv.ui.theme.Ed

/**
 * Addons instalados no repositório ativo (arquitetura estilo Kodi).
 * O Brazuca Play é nativo, sempre habilitado e não pode ser removido.
 */
@Composable
fun AddonsScreen(
    viewModel: AddonsViewModel,
    onOpenRepository: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val palette = Ed.palette
    val type = Ed.type

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
            EdSecondaryButton(label = "Repositório", onClick = onOpenRepository, icon = EdIcons.cloud)
            Box(Modifier.size(10.dp))
            EdSecondaryButton(label = "Sincronizar", onClick = { viewModel.sync() }, icon = EdIcons.refresh)
        }

        if (state.addons.isEmpty()) {
            EmptyState(
                title = "Nenhum addon instalado",
                message = "Sincronize o repositório ativo ou restaure o Brazuca Play em Ajustes → Repositório.",
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
        if (!builtin) {
            EdSecondaryButton(label = "Remover", onClick = onRemove, icon = EdIcons.delete)
        }
        EdSwitch(checked = addon.enabled, onChange = { if (!builtin || it) onToggle(it) })
    }
}

private fun kindLabel(kind: com.smarted.tv.core.model.MediaKind): String = when (kind) {
    com.smarted.tv.core.model.MediaKind.LIVE -> "canais"
    com.smarted.tv.core.model.MediaKind.MOVIE -> "filmes"
    com.smarted.tv.core.model.MediaKind.SERIES -> "séries"
    com.smarted.tv.core.model.MediaKind.EPISODE -> "episódios"
    com.smarted.tv.core.model.MediaKind.UNKNOWN -> "—"
}

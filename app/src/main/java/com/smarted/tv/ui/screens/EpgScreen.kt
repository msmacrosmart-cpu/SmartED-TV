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

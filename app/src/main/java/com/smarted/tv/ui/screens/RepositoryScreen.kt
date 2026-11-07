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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smarted.tv.core.addons.Repository
import com.smarted.tv.core.data.SettingsStore
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
import com.smarted.tv.ui.components.edInteraction
import com.smarted.tv.ui.state.AddonsViewModel
import com.smarted.tv.ui.state.SettingsViewModel
import com.smarted.tv.ui.theme.Ed

/**
 * **Trocar / Gerenciar Repositório** (requisito 5).
 *
 * - Lista os repositórios conhecidos, marcando o ativo.
 * - Permite trocar com um clique (grava em SharedPreferences + Room e já
 *   sincroniza em background).
 * - Permite adicionar um repositório por URL e remover os não-ativos.
 * - Sempre abre com o Brazuca Play disponível como padrão.
 */
@Composable
fun RepositoryScreen(
    settingsViewModel: SettingsViewModel,
    addonsViewModel: AddonsViewModel,
    onBack: () -> Unit,
) {
    val addonsState by addonsViewModel.state.collectAsStateWithLifecycle()
    val settingsState by settingsViewModel.state.collectAsStateWithLifecycle()
    val palette = Ed.palette
    val type = Ed.type

    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(palette.bg).padding(horizontal = 32.dp),
        contentPadding = PaddingValues(top = 18.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item("title") {
            Row(verticalAlignment = Alignment.Bottom) {
                ScreenTitle(
                    title = "Trocar / Gerenciar Repositório",
                    subtitle = "Repositório define quais addons e fontes o app carrega ao abrir",
                    modifier = Modifier.weight(1f),
                )
                if (addonsState.syncing) {
                    EdBadge("SINCRONIZANDO", palette.warn, filled = true)
                }
            }
        }

        item("active") {
            ActiveRepositoryCard(
                name = settingsState.repoName,
                url = settingsState.repoUrl,
                status = addonsState.syncStatus,
                error = addonsState.error,
            )
        }

        item("default-hint") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "REPOSITÓRIOS DISPONÍVEIS",
                    style = type.label.copy(letterSpacing = 1.2.sp),
                    color = palette.fg3,
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                )
            }
        }

        items(addonsState.repositories, key = { it.id }) { repo ->
            RepositoryRow(
                repo = repo,
                active = repo.url == settingsState.repoUrl,
                onSwitch = {
                    message = "Trocando para ${repo.name}…"
                    addonsViewModel.switchRepository(repo.url, repo.name)
                    settingsViewModel.setRepo(repo.url, repo.name)
                    message = "Repositório ativo: ${repo.name}"
                },
                onRemove = {
                    addonsViewModel.removeRepository(repo.id)
                    message = "Repositório removido"
                },
            )
        }

        if (addonsState.repositories.isEmpty()) {
            item("empty") {
                EmptyState(
                    title = "Nenhum repositório cadastrado",
                    message = "Adicione uma URL abaixo. O Brazuca Play já vem embutido como padrão.",
                    icon = EdIcons.cloud,
                )
            }
        }

        item("add-form") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.surface)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Adicionar repositório", style = type.sectionTitle, color = palette.fg)
                Text(
                    text = "Cole a URL de um repositório estilo Kodi (ex.: https://vkodi.net/repo). " +
                        "O app procura addons.xml, addons.xml.md5 e o índice HTML do diretório.",
                    style = type.caption,
                    color = palette.fg3,
                )
                EdTextField(
                    value = url,
                    onValueChange = { url = it },
                    placeholder = "https://exemplo.com/repo",
                    label = "URL DO REPOSITÓRIO",
                )
                EdTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "Nome amigável (opcional)",
                    label = "NOME",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    EdPrimaryButton(
                        label = "Adicionar",
                        icon = EdIcons.add,
                        onClick = {
                            if (url.isBlank()) {
                                message = "Informe a URL do repositório"
                            } else {
                                addonsViewModel.addRepository(url, name) { error ->
                                    message = error ?: "Repositório adicionado"
                                }
                                url = ""
                                name = ""
                            }
                        },
                    )
                    EdSecondaryButton(
                        label = "Usar Brazuca Play (padrão)",
                        icon = EdIcons.cloud,
                        onClick = {
                            val defaultUrl = SettingsStore.DEFAULT_REPO_URL
                            addonsViewModel.switchRepository(defaultUrl, "Brazuca Play")
                            settingsViewModel.setRepo(defaultUrl, "Brazuca Play")
                            message = "Repositório padrão restaurado"
                        },
                    )
                    EdSecondaryButton(
                        label = "Testar / Sincronizar",
                        icon = EdIcons.refresh,
                        onClick = { addonsViewModel.sync() },
                    )
                }
            }
        }

        (message ?: addonsState.syncStatus.takeIf { it.isNotBlank() })?.let { msg ->
            item("message") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.surface2)
                        .padding(12.dp),
                ) {
                    Text(msg, style = type.bodySmall, color = palette.fg2)
                }
            }
        }

        item("back") {
            EdSecondaryButton(label = "Voltar", onClick = onBack, icon = EdIcons.back)
        }
    }
}

@Composable
private fun ActiveRepositoryCard(name: String, url: String, status: String, error: String?) {
    val palette = Ed.palette
    val type = Ed.type
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.accentSoft)
            .border(2.dp, palette.accent, RoundedCornerShape(16.dp))
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(palette.accent.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(EdIcons.cloud, contentDescription = null, tint = palette.accent, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(name, style = type.sectionTitle, color = palette.fg)
                AccentDot(palette.ok, 7.dp)
                Text("ATIVO", style = type.label, color = palette.ok)
            }
            Text(url, style = type.caption, color = palette.fg2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (status.isNotBlank()) {
                Text(status, style = type.caption, color = palette.fg3, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            error?.let {
                Text("Aviso: $it", style = type.caption, color = palette.warn, maxLines = 2)
            }
        }
    }
}

@Composable
private fun RepositoryRow(
    repo: Repository,
    active: Boolean,
    onSwitch: () -> Unit,
    onRemove: () -> Unit,
) {
    val palette = Ed.palette
    val type = Ed.type
    val interaction = edInteraction(onClick = onSwitch)
    val shape = RoundedCornerShape(14.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp)
            .clip(shape)
            .background(if (active) palette.accentSoft else palette.surface, shape)
            .then(
                when {
                    interaction.isFocused -> Modifier.border(3.dp, palette.accent, shape)
                    active -> Modifier.border(1.dp, palette.accent, shape)
                    else -> Modifier
                },
            )
            .then(interaction.modifier)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(palette.surface3),
            contentAlignment = Alignment.Center,
        ) {
            Icon(EdIcons.cloud, contentDescription = null, tint = palette.fg2, modifier = Modifier.size(19.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = repo.name,
                    style = type.rowTitle.copy(fontWeight = FontWeight.SemiBold),
                    color = palette.fg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (repo.isDefault) EdBadge("PADRÃO", palette.accent, filled = true)
                EdBadge(
                    text = when (repo.status.name) {
                        "ONLINE" -> "ONLINE"
                        "OFFLINE" -> "OFFLINE"
                        "SYNCING" -> "SINCronizando"
                        else -> repo.status.name
                    },
                    color = when (repo.status.name) {
                        "ONLINE" -> palette.ok
                        "OFFLINE" -> palette.bad
                        "SYNCING" -> palette.warn
                        else -> palette.fg3
                    },
                )
            }
            Text(
                text = repo.url,
                style = type.caption,
                color = palette.fg3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (repo.addonCount > 0) {
            Text("${repo.addonCount} addons", style = type.caption, color = palette.fg3)
        }
        if (!active) {
            EdChip(label = "Usar", selected = false, onClick = onSwitch)
            if (!repo.isDefault) {
                EdChip(label = "Remover", selected = false, onClick = onRemove)
            }
        } else {
            EdBadge("EM USO", palette.ok, filled = true)
        }
    }
}

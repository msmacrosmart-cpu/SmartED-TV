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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smarted.tv.R
import com.smarted.tv.core.addons.stremio.StremioAddonSource
import com.smarted.tv.core.addons.stremio.StremioCatalog
import com.smarted.tv.core.addons.stremio.StremioRegistry
import com.smarted.tv.ui.LocalAppGraph
import com.smarted.tv.ui.components.Artwork
import com.smarted.tv.ui.components.EdChip
import com.smarted.tv.ui.components.EdIcons
import com.smarted.tv.ui.components.EdPrimaryButton
import com.smarted.tv.ui.components.EdSecondaryButton
import com.smarted.tv.ui.components.EdTextField
import com.smarted.tv.ui.components.EmptyState
import com.smarted.tv.ui.components.ScreenTitle
import com.smarted.tv.ui.theme.Ed
import kotlinx.coroutines.launch

/**
 * Addons estilo Stremio — manifesto HTTP/JSON + vínculo manual por aba.
 *
 * A tela existe para deixar o contrato explícito na cara do usuário:
 *
 *  1. ele cola a URL do `manifest.json` (ou da base do addon);
 *  2. o app lê o manifesto e lista os **catálogos declarados**;
 *  3. cada catálogo pode ser ligado/desligado em Canais, Filmes ou Séries —
 *     o escopo vem do `type` do próprio catálogo, nunca de um chute do app.
 *
 * Nenhuma varredura de torrentes acontece aqui: só leitura de diretório.
 */
@Composable
fun StremioScreen(
    onBack: () -> Unit,
) {
    val graph = LocalAppGraph.current
    val palette = Ed.palette
    val type = Ed.type
    val scope = rememberCoroutineScope()
    val registry = remember(graph) { StremioRegistry(graph.appContext, graph.http) }
    val state by registry.state.collectAsStateWithLifecycle()
    var url by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.bg)
            .padding(horizontal = 32.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            ScreenTitle(
                title = stringResource(R.string.stremio_title),
                subtitle = stringResource(R.string.stremio_subtitle, state.addons.size),
                modifier = Modifier.weight(1f),
            )
            EdSecondaryButton(
                label = stringResource(R.string.action_back),
                onClick = onBack,
                icon = EdIcons.back,
            )
        }

        // ------------------------------------------------- instalar addon
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EdTextField(
                value = url,
                onValueChange = { url = it },
                placeholder = stringResource(R.string.stremio_url_hint),
                label = stringResource(R.string.stremio_url_label),
                modifier = Modifier.weight(1f),
            )
            EdPrimaryButton(
                label = stringResource(R.string.stremio_install),
                onClick = { scope.launch { registry.install(url) } },
                icon = EdIcons.cloudDownload,
                enabled = !state.loading && url.isNotBlank(),
            )
            if (state.addons.isNotEmpty()) {
                EdSecondaryButton(
                    label = stringResource(R.string.action_reload),
                    onClick = { registry.clearError() },
                    icon = EdIcons.refresh,
                )
            }
        }

        val status = state.error ?: state.status
        if (status.isNotBlank()) {
            Text(
                text = status,
                style = type.caption,
                color = if (state.error != null) palette.warn else palette.fg3,
                maxLines = 3,
            )
        }

        if (state.addons.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.stremio_empty_title),
                message = stringResource(R.string.stremio_empty_message),
                icon = EdIcons.cloud,
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(state.addons, key = { "stremio-${it.url}" }) { addon ->
                StremioAddonCard(
                    addon = addon,
                    isBound = { catalog, target -> state.isBound(addon.url, catalog.id, target) },
                    onToggle = { catalog, target -> registry.toggleBinding(addon, catalog, target) },
                    onRemove = { registry.remove(addon.url) },
                )
            }
        }
    }
}

@Composable
private fun StremioAddonCard(
    addon: StremioAddonSource,
    isBound: (StremioCatalog, com.smarted.tv.core.model.MediaKind) -> Boolean,
    onToggle: (StremioCatalog, com.smarted.tv.core.model.MediaKind) -> Unit,
    onRemove: () -> Unit,
) {
    val palette = Ed.palette
    val type = Ed.type
    val shape = RoundedCornerShape(18.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(palette.surface, shape)
            .border(1.dp, palette.glassBorder, shape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.surface2, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Artwork(
                    url = addon.manifest.logo,
                    title = addon.name,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = addon.name,
                        style = type.rowTitle.copy(fontWeight = FontWeight.SemiBold),
                        color = palette.fg,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (addon.version.isNotBlank()) {
                        Text(
                            text = "v${addon.version}",
                            style = type.caption,
                            color = palette.fg3,
                        )
                    }
                }
                Text(
                    text = addon.manifest.description ?: addon.url,
                    style = type.caption,
                    color = palette.fg3,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            EdSecondaryButton(
                label = stringResource(R.string.action_remove),
                onClick = onRemove,
                icon = EdIcons.delete,
            )
        }

        // ----- catálogos declarados + vínculo com a aba -----
        addon.catalogs.forEach { catalog ->
            val scope = catalog.scope
            if (scope != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier
                            .size(2.dp, 26.dp)
                            .background(palette.line, RoundedCornerShape(2.dp)),
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            text = catalog.displayName,
                            style = type.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = palette.fg,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(R.string.stremio_catalog_scope, catalog.type, scope.title),
                            style = type.caption,
                            color = palette.fg3,
                            maxLines = 1,
                        )
                    }
                    EdChip(
                        label = if (isBound(catalog, scope.kind)) {
                            stringResource(R.string.stremio_bound, scope.title)
                        } else {
                            stringResource(R.string.stremio_bind, scope.title)
                        },
                        selected = isBound(catalog, scope.kind),
                        onClick = { onToggle(catalog, scope.kind) },
                    )
                }
            }
        }

        // Escopos que o addon declara e que o app reconhece.
        val scopeList = addon.manifest.scopes
        if (scopeList.isNotEmpty()) {
            Text(
                text = stringResource(
                    R.string.stremio_scopes,
                    scopeList.joinToString(" · ") { it.title },
                ),
                style = type.caption,
                color = palette.fg3,
                maxLines = 1,
            )
        }
    }
}

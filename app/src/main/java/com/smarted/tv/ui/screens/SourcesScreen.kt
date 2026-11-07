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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.smarted.tv.core.addons.Repository
import com.smarted.tv.core.util.SourceTargeting
import com.smarted.tv.ui.components.AccentDot
import com.smarted.tv.ui.components.EdBadge
import com.smarted.tv.ui.components.EdIcons
import com.smarted.tv.ui.components.EdPrimaryButton
import com.smarted.tv.ui.components.EdSecondaryButton
import com.smarted.tv.ui.components.EmptyState
import com.smarted.tv.ui.components.ScreenTitle
import com.smarted.tv.ui.state.AddonsViewModel
import com.smarted.tv.ui.theme.Ed

/**
 * Fontes de conteúdo do app.
 *
 * A arquitetura é a de repositórios e addons (modelo Kodi): **não há mais
 * cadastro de listas M3U avulsas**. O que existe é o repositório (padrão:
 * Vikings), cujas pastas — TV ao Vivo, Filmes, Séries — o usuário conecta às
 * abas pelo navegador do repositório.
 *
 * Esta tela mostra o estado atual desse vínculo.
 */
@Composable
fun SourcesScreen(
    viewModel: AddonsViewModel,
    onOpenBrowser: () -> Unit,
    onOpenRepository: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val palette = Ed.palette
    val type = Ed.type

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.bg)
            .padding(horizontal = 32.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenTitle(
            title = "Fontes de conteúdo",
            subtitle = "${state.repositories.size} repositório(s) · modelo Repositórios + Addons",
        )

        // Explica o modelo atual em vez de oferecer um campo de M3U.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(palette.surface)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Sem listas M3U por enquanto",
                style = type.sectionTitle,
                color = palette.fg,
            )
            Text(
                text = "O conteúdo vem de repositórios de addons. Abra o navegador do " +
                    "repositório, entre na pasta (TV ao Vivo, Filmes ou Séries) e conecte-a " +
                    "à aba correspondente do app.",
                style = type.caption,
                color = palette.fg3,
            )
        }

        if (state.repositories.isEmpty()) {
            EmptyState(
                title = "Nenhum repositório",
                message = "Abra Repositório para adicionar uma URL ou restaurar o padrão (Vikings).",
                icon = EdIcons.cloud,
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.repositories, key = { it.id }) { repo ->
                    RepositoryRow(repo)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EdPrimaryButton(label = "Navegar no repositório", onClick = onOpenBrowser, icon = EdIcons.folder)
            EdSecondaryButton(label = "Repositório", onClick = onOpenRepository, icon = EdIcons.cloud)
            EdSecondaryButton(label = "Voltar", onClick = onBack, icon = EdIcons.back)
        }
    }
}

@Composable
private fun RepositoryRow(repo: Repository) {
    val palette = Ed.palette
    val type = Ed.type
    val shape = RoundedCornerShape(14.dp)
    val bound = SourceTargeting.describe(repo.kinds)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .clip(shape)
            .background(palette.surface, shape)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AccentDot(
            color = when {
                !repo.enabled -> palette.fg3
                repo.status.name == "ONLINE" -> palette.ok
                else -> palette.warn
            },
            size = 8.dp,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = repo.name,
                    style = type.rowTitle.copy(fontWeight = FontWeight.SemiBold),
                    color = palette.fg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (repo.isDefault) EdBadge("PADRÃO", palette.accent, filled = true)
            }
            Text(repo.url, style = type.caption, color = palette.fg3, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = "${repo.addonCount} addon(s) · ${bound.ifBlank { "nenhuma aba conectada" }}",
                style = type.label,
                color = palette.fg3,
            )
        }
        Box(Modifier.size(10.dp))
    }
}

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
import com.smarted.tv.core.addons.AddonTree
import com.smarted.tv.core.model.MediaKind
import com.smarted.tv.ui.components.Artwork
import com.smarted.tv.ui.components.EdBadge
import com.smarted.tv.ui.components.EdIcons
import com.smarted.tv.ui.components.EdPrimaryButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.smarted.tv.ui.components.TargetToggle
import com.smarted.tv.ui.components.EdSecondaryButton
import com.smarted.tv.ui.components.EmptyState
import com.smarted.tv.ui.components.ScreenTitle
import com.smarted.tv.ui.components.edFocusableSurface
import com.smarted.tv.ui.components.edInteraction
import com.smarted.tv.ui.state.AddonsViewModel
import com.smarted.tv.ui.theme.Ed

/**
 * **Minha lista** — a playlist que o usuário monta navegando os addons.
 *
 * Diferente das abas Filmes/Séries (que seguem a pasta conectada do
 * repositório), esta lista é escolha manual: cada item foi salvo de uma pasta
 * ("Lançamentos", "Ação", "Aventura"…) e fica aqui, agrupado pela origem, até o
 * usuário remover. É a "playlist própria" do app.
 */
@Composable
fun MyListScreen(
    viewModel: AddonsViewModel,
    onPlay: (AddonTree.SavedItem) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val palette = Ed.palette
    val type = Ed.type

    // Uma aba por destino escolhido: Canais, Filmes e Séries.
    var shelf by remember { mutableStateOf(AddonTree.Shelf.MOVIES) }
    val groups = AddonTree.groupByShelf(state.saved)
    val current = groups.firstOrNull { it.shelf == shelf } ?: groups.first()
    // Se o usuário tem itens só numa aba, abre nela.
    LaunchedEffect(groups.map { it.shelf to it.count }) {
        val hasCurrent = groups.any { it.shelf == shelf && it.count > 0 }
        if (!hasCurrent) {
            groups.firstOrNull { it.count > 0 }?.let { shelf = it.shelf } ?: run { shelf = current.shelf }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.bg)
            .padding(horizontal = 32.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenTitle(
            title = "Minha lista",
            subtitle = AddonTree.savedSummary(state.saved),
        )

        // Abas de destino: cada uma mostra quanto o usuário salvou nela.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AddonTree.Shelf.ALL.forEach { option ->
                val count = groups.firstOrNull { it.shelf == option }?.count ?: 0
                TargetToggle(
                    kind = option.kind,
                    checked = shelf == option,
                    onToggle = { checked -> if (checked) shelf = option },
                    modifier = Modifier.weight(1f),
                )
                if (count > 0) {
                    EdBadge("$count", if (shelf == option) palette.accent else palette.fg3)
                }
                Box(Modifier.width(2.dp))
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.saved.isEmpty() -> EmptyState(
                    title = "Sua lista está vazia",
                    message = "Abra o Elementum em Addons → Elementum, navegue pelas pastas " +
                        "(Lançamentos, Ação, Aventura…) e toque em Salvar no que quiser assistir. " +
                        "Você escolhe se o item vai para Canais, Filmes ou Séries.",
                    icon = EdIcons.folder,
                )

                current.isEmpty -> EmptyState(
                    title = "Nada em ${current.shelf.label}",
                    message = "Você ainda não salvou itens em ${current.shelf.label.lowercase()}. " +
                        "Abra o Elementum e use Salvar para escolher esta aba.",
                    icon = EdIcons.folder,
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    current.folders.forEach { (folder, items) ->
                        item(key = "folder:${current.shelf.name}:$folder") {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp, start = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = folder,
                                    style = type.label.copy(fontWeight = FontWeight.SemiBold),
                                    color = palette.fg2,
                                )
                                EdBadge("${items.size}", palette.fg3)
                            }
                        }
                        items(items, key = { it.id }) { saved ->
                            SavedRow(
                                item = saved,
                                onPlay = { onPlay(saved) },
                                onRemove = { viewModel.removeSaved(saved.id) },
                            )
                        }
                    }
                }
            }
        }

        EdSecondaryButton(label = "Voltar", onClick = onBack, icon = EdIcons.back)
    }
}

@Composable
private fun SavedRow(
    item: AddonTree.SavedItem,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
) {
    val palette = Ed.palette
    val type = Ed.type
    val shape = RoundedCornerShape(14.dp)
    val interaction = edInteraction(onClick = onPlay)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(86.dp)
            .then(interaction.modifier)
            .background(palette.surface, shape)
            .clip(shape)
            .edFocusableSurface(interaction.isFocused, shape, palette)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(54.dp).clip(RoundedCornerShape(12.dp)).background(palette.surface2),
            contentAlignment = Alignment.Center,
        ) {
            Artwork(item.poster, item.title, Modifier.fillMaxSize())
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = item.title,
                style = type.rowTitle.copy(fontWeight = FontWeight.SemiBold),
                color = palette.fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.meta.isNotBlank()) {
                Text(item.meta, style = type.caption, color = palette.fg3, maxLines = 1)
            }
        }

        EdBadge(text = item.shelf.label.uppercase(), color = palette.accent)
        Box(Modifier.width(10.dp))
        EdPrimaryButton(label = "Assistir", onClick = onPlay, icon = EdIcons.play)
        Box(Modifier.width(8.dp))
        EdSecondaryButton(label = "Remover", onClick = onRemove, icon = EdIcons.delete)
    }
}

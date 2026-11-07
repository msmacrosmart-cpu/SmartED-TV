package com.smarted.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smarted.tv.R
import com.smarted.tv.core.model.MediaKind
import com.smarted.tv.core.util.SourceTargeting
import com.smarted.tv.ui.theme.Ed
import com.smarted.tv.ui.theme.edFocusRing

/**
 * Painel de seleção de abas — o coração do cadastro de fonte/repositório.
 *
 * O usuário marca explicitamente onde o conteúdo se aplica:
 *
 * ```
 * ONDE ESTE CONTEÚDO APARECE
 * [x] Canais (Live TV)   [ ] Filmes (VOD)   [x] Séries
 * ```
 *
 * Cada opção é um botão focável de D-pad (não depende de toque), com anel de
 * foco **branco puro** e marca de seleção branca — sem nenhum tom rosa, como no
 * resto do design system.
 *
 * Se o usuário desmarcar tudo, a fonte é adicionada mas não alimenta nenhuma
 * aba; a tela de origem avisa isso em vez de deixar um cadastro inerte.
 */
@Composable
fun TargetPicker(
    selected: Collection<MediaKind>,
    onToggle: (MediaKind, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.targets_title),
    hint: String? = stringResource(R.string.targets_hint),
) {
    val palette = Ed.palette
    val type = Ed.type

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = type.label,
            color = palette.fg3,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SourceTargeting.ALL.forEach { kind ->
                TargetToggle(
                    kind = kind,
                    checked = kind in selected,
                    onToggle = { onToggle(kind, it) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (hint != null) {
            Text(text = hint, style = type.caption, color = palette.fg3)
        }
        if (selected.isEmpty()) {
            Text(
                text = stringResource(R.string.targets_empty_warning),
                style = type.caption,
                color = palette.warn,
            )
        }
    }
}

/** Uma aba com checkbox: quadrado branco quando marcado, contorno quando não. */
@Composable
fun TargetToggle(
    kind: MediaKind,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = Ed.palette
    val type = Ed.type
    val interaction = edInteraction(onClick = { onToggle(!checked) })
    val shape = RoundedCornerShape(12.dp)

    Row(
        modifier = modifier
            .edFocusRing(interaction.isFocused, shape)
            .clip(shape)
            .background(if (checked) palette.accentSoft else palette.surface2)
            .then(interaction.modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(if (checked) palette.accent else Color.Transparent)
                .border(
                    width = 2.dp,
                    color = if (checked) palette.accent else palette.line,
                    shape = RoundedCornerShape(5.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    imageVector = EdIcons.check,
                    contentDescription = null,
                    tint = palette.onAccent,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Text(
            text = labelFor(kind),
            style = type.bodySmall.copy(fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal),
            color = if (checked) palette.fg else palette.fg2,
            maxLines = 1,
        )
    }
}

/** Rótulo da aba, com o formato entre parênteses como no pedido de produto. */
@Composable
fun labelFor(kind: MediaKind): String = when (kind) {
    MediaKind.LIVE -> stringResource(R.string.target_live)
    MediaKind.MOVIE -> stringResource(R.string.target_movies)
    MediaKind.SERIES -> stringResource(R.string.target_series)
    else -> stringResource(R.string.target_live)
}

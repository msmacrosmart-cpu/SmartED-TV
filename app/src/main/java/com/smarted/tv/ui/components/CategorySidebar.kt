package com.smarted.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarted.tv.ui.theme.Ed

/**
 * Coluna de categorias (tela de Canais/Filmes) — idêntica à referência:
 * pílula selecionada com borda de acento, contagem à direita.
 */
@Composable
fun CategorySidebar(
    categories: List<Pair<String, Int>>,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "CATEGORIAS",
) {
    val palette = Ed.palette
    val type = Ed.type

    Column(modifier = modifier.width(268.dp)) {
        Text(
            text = title,
            style = type.label.copy(letterSpacing = 1.6.sp),
            color = palette.fg3,
            modifier = Modifier.padding(start = 18.dp, bottom = 10.dp),
        )
        androidx.compose.foundation.lazy.LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
        ) {
            item {
                CategoryRow(
                    label = "Todos",
                    count = categories.sumOf { it.second },
                    selected = selected == null,
                    onClick = { onSelect(null) },
                )
            }
            items(categories.size) { index ->
                val (name, count) = categories[index]
                CategoryRow(
                    label = name,
                    count = count,
                    selected = selected.equals(name, ignoreCase = true),
                    onClick = { onSelect(name) },
                )
            }
        }
    }
}

@Composable
private fun CategoryRow(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = Ed.palette
    val type = Ed.type
    val interaction = edInteraction(onClick = onClick)
    val focused = interaction.isFocused
    val shape = RoundedCornerShape(14.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(shape)
            .background(
                when {
                    focused -> palette.surface3
                    selected -> palette.surface2
                    else -> Color.Transparent
                },
                shape,
            )
            .then(
                if (selected || focused) {
                    Modifier.border(
                        2.dp,
                        if (selected) palette.accent else palette.accent.copy(alpha = 0.6f),
                        shape,
                    )
                } else {
                    Modifier
                },
            )
            .then(interaction.modifier)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = type.bodySmall.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
            color = when {
                selected -> palette.accent
                focused -> palette.fg
                else -> palette.fg2
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (count > 0) count.toString() else "",
            style = type.caption,
            color = if (selected) palette.accent.copy(alpha = 0.85f) else palette.fg3,
        )
    }
}

/** Pílula de filtro/ordenação (dropdown trigger da referência). */
@Composable
fun FilterPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    val palette = Ed.palette
    val type = Ed.type
    val interaction = edInteraction(onClick = onClick)
    val focused = interaction.isFocused
    val shape = RoundedCornerShape(percent = 50)

    Row(
        modifier = modifier
            .height(40.dp)
            .clip(shape)
            .background(
                when {
                    focused -> palette.surface3
                    active -> palette.accentSoft
                    else -> palette.surface
                },
                shape,
            )
            .then(
                when {
                    focused || active -> Modifier.border(2.dp, palette.accent, shape)
                    else -> Modifier.border(1.dp, palette.lineSoft, shape)
                },
            )
            .then(interaction.modifier)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (focused) palette.fg else palette.fg2, modifier = Modifier.size(15.dp))
        }
        Text(
            text = label,
            style = type.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = if (focused || active) palette.fg else palette.fg2,
            maxLines = 1,
        )
        Icon(
            imageVector = EdIcons.expand,
            contentDescription = null,
            tint = palette.fg3,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** Cabeçalho de seção com contagem à direita (ex.: "Favorites   8 items"). */
@Composable
fun SectionHeader(
    title: String,
    trailing: String? = null,
    modifier: Modifier = Modifier,
) {
    val palette = Ed.palette
    val type = Ed.type
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = type.sectionTitle, color = palette.fg, modifier = Modifier.weight(1f))
        trailing?.let {
            Text(it, style = type.bodySmall, color = palette.fg3)
        }
    }
}

/** Título de tela com subtítulo (ex.: "Movies   71.474 filmes"). */
@Composable
fun ScreenTitle(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    val palette = Ed.palette
    val type = Ed.type
    Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
        Text(title, style = type.display, color = palette.fg)
        subtitle?.let {
            Text(
                text = it,
                style = type.bodySmall,
                color = palette.fg3,
                modifier = Modifier.padding(start = 12.dp, bottom = 6.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val palette = Ed.palette
    val type = Ed.type
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(percent = 50)

    Row(
        modifier = modifier
            .height(48.dp)
            .clip(shape)
            .background(if (focused) palette.surface3 else palette.surface2, shape)
            .then(if (focused) Modifier.border(2.dp, palette.accent, shape) else Modifier)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(EdIcons.search, contentDescription = null, tint = palette.fg3, modifier = Modifier.size(18.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = type.bodySmall.copy(color = palette.fg),
            cursorBrush = SolidColor(palette.accent),
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { focused = it.isFocused }
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
            decorationBox = { inner ->
                Box {
                    if (query.isBlank()) {
                        Text(placeholder, style = type.bodySmall, color = palette.fg3)
                    }
                    inner()
                }
            },
        )
        if (query.isNotBlank()) {
            Icon(
                EdIcons.close,
                contentDescription = "Limpar",
                tint = palette.fg3,
                modifier = Modifier
                    .size(18.dp)
                    .clickable { onQueryChange("") },
            )
        }
    }
}

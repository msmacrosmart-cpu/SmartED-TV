package com.smarted.tv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarted.tv.core.model.MediaItem
import com.smarted.tv.core.util.TextUtils
import com.smarted.tv.ui.theme.Ed
import com.smarted.tv.ui.theme.EdEasing

/**
 * Hero de destaque (topo da Home) — réplica do banner da referência:
 * arte full-bleed com escurecimento, eyebrow de acento, título grande,
 * nota, sinopse e ações "Assistir" / "Detalhes".
 */
@Composable
fun HeroBanner(
    item: MediaItem,
    eyebrow: String,
    onPlay: () -> Unit,
    onDetails: () -> Unit,
    modifier: Modifier = Modifier,
    playFocusRequester: FocusRequester? = null,
    progress: Float? = null,
) {
    val palette = Ed.palette
    val type = Ed.type
    val shape = RoundedCornerShape(18.dp)
    val playInteraction = edInteraction(onClick = onPlay, focusRequester = playFocusRequester)
    val detailsInteraction = edInteraction(onClick = onDetails)
    val focused = playInteraction.isFocused || detailsInteraction.isFocused
    val borderAlpha by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(200, easing = EdEasing),
        label = "hero-ring",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp)
            .clip(shape)
            .background(palette.surface),
    ) {
        Artwork(
            url = item.backdrop ?: item.poster,
            title = item.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        // escurecimento à esquerda + base (mantém o texto legível sobre qualquer arte)
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to palette.bg.copy(alpha = 0.96f),
                        0.45f to palette.bg.copy(alpha = 0.72f),
                        1f to Color.Transparent,
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.45f to Color.Transparent,
                        1f to palette.bg.copy(alpha = 0.75f),
                    ),
                ),
        )
        if (borderAlpha > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .border(3.dp, palette.accent.copy(alpha = borderAlpha), shape),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(0.62f)
                .padding(horizontal = 34.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = eyebrow.uppercase(),
                style = type.eyebrow,
                color = palette.accent,
                maxLines = 1,
            )
            Text(
                text = item.title,
                style = type.display,
                color = palette.fg,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item.rating?.takeIf { it > 0 }?.let {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(6.dp).background(palette.accent, RoundedCornerShape(percent = 50)))
                        Text(String.format("%.1f", it), style = type.bodySmall, color = palette.fg)
                    }
                }
                item.year?.let { Text(it.toString(), style = type.bodySmall, color = palette.fg2) }
                item.quality?.let { QualityBadge(it) }
                if (item.genres.isNotEmpty()) {
                    Text(
                        text = item.genres.take(3).joinToString(" · "),
                        style = type.bodySmall,
                        color = palette.fg2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            val plot = TextUtils.stripHtml(item.details?.plot ?: item.plot)
            if (plot.isNotBlank()) {
                Text(
                    text = TextUtils.ellipsize(plot, 220),
                    style = type.body,
                    color = palette.fg2,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
                PrimaryActionButton(
                    label = if (progress != null && progress > 0.02f) "Continuar" else "Assistir",
                    icon = EdIcons.play,
                    interaction = playInteraction,
                )
                SecondaryActionButton(
                    label = "Detalhes",
                    icon = EdIcons.info,
                    interaction = detailsInteraction,
                )
            }
        }

        if (progress != null && progress > 0f) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(palette.fg3.copy(alpha = 0.25f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(4.dp)
                        .background(palette.accent),
                )
            }
        }
    }
}

@Composable
fun PrimaryActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    interaction: EdInteraction,
    modifier: Modifier = Modifier,
) {
    val palette = Ed.palette
    val type = Ed.type
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier = modifier
            .clip(shape)
            .background(if (interaction.isFocused) palette.accent else palette.fg.copy(alpha = 0.94f), shape)
            .then(if (interaction.isFocused) Modifier.border(3.dp, palette.fg, shape) else Modifier)
            .then(interaction.modifier)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (interaction.isFocused) palette.onAccent else palette.bg,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            style = type.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = if (interaction.isFocused) palette.onAccent else palette.bg,
        )
    }
}

@Composable
fun SecondaryActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    interaction: EdInteraction,
    modifier: Modifier = Modifier,
) {
    val palette = Ed.palette
    val type = Ed.type
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier = modifier
            .clip(shape)
            .background(
                if (interaction.isFocused) palette.surface3 else palette.surface.copy(alpha = 0.75f),
                shape,
            )
            .then(if (interaction.isFocused) Modifier.border(3.dp, palette.accent, shape) else Modifier)
            .then(interaction.modifier)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = palette.fg, modifier = Modifier.size(18.dp))
        Text(label, style = type.bodySmall.copy(fontWeight = FontWeight.Medium), color = palette.fg)
    }
}

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

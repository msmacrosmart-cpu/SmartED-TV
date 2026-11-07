package com.smarted.tv.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.composed
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.smarted.tv.ui.theme.Ed
import com.smarted.tv.ui.theme.EdEasing

/** Botão principal (fundo sólido). */
@Composable
fun EdPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
) {
    val palette = Ed.palette
    val type = Ed.type
    val interaction = edInteraction(onClick = onClick, enabled = enabled, focusRequester = focusRequester)
    val shape = RoundedCornerShape(percent = 50)
    val bg by animateColorAsState(
        when {
            !enabled -> palette.surface2
            interaction.isFocused -> palette.accent
            else -> palette.fg.copy(alpha = 0.94f)
        },
        tween(160, easing = EdEasing),
        label = "btn-bg",
    )
    Row(
        modifier = modifier
            .height(46.dp)
            .clip(shape)
            .background(bg, shape)
            .then(if (interaction.isFocused) Modifier.border(3.dp, palette.fg, shape) else Modifier)
            .then(interaction.modifier)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        icon?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                tint = if (interaction.isFocused) palette.onAccent else palette.bg,
                modifier = Modifier.size(17.dp),
            )
        }
        Text(
            text = label,
            style = type.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = if (interaction.isFocused) palette.onAccent else palette.bg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Botão secundário (contorno). */
@Composable
fun EdSecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
) {
    val palette = Ed.palette
    val type = Ed.type
    val interaction = edInteraction(onClick = onClick, enabled = enabled, focusRequester = focusRequester)
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier = modifier
            .height(46.dp)
            .clip(shape)
            .background(if (interaction.isFocused) palette.surface3 else palette.surface, shape)
            .then(
                when {
                    interaction.isFocused -> Modifier.border(3.dp, palette.accent, shape)
                    else -> Modifier.border(1.dp, palette.line, shape)
                },
            )
            .then(interaction.modifier)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        icon?.let {
            Icon(imageVector = it, contentDescription = null, tint = palette.fg2, modifier = Modifier.size(17.dp))
        }
        Text(
            text = label,
            style = type.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = if (enabled) palette.fg else palette.fg3,
            maxLines = 1,
        )
    }
}

/** Pílula compacta (ações de card, "ver tudo"). */
@Composable
fun EdPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    active: Boolean = false,
    focusRequester: FocusRequester? = null,
) {
    val palette = Ed.palette
    val type = Ed.type
    val interaction = edInteraction(onClick = onClick, focusRequester = focusRequester)
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier = modifier
            .height(38.dp)
            .clip(shape)
            .background(
                when {
                    interaction.isFocused -> palette.surface3
                    active -> palette.accentSoft
                    else -> palette.surface
                },
                shape,
            )
            .then(
                when {
                    interaction.isFocused || active -> Modifier.border(2.dp, palette.accent, shape)
                    else -> Modifier.border(1.dp, palette.lineSoft, shape)
                },
            )
            .then(interaction.modifier)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        icon?.let {
            Icon(it, contentDescription = null, tint = if (interaction.isFocused) palette.fg else palette.fg2, modifier = Modifier.size(15.dp))
        }
        Text(
            text = label,
            style = type.caption.copy(fontWeight = FontWeight.Medium),
            color = if (interaction.isFocused || active) palette.fg else palette.fg2,
            maxLines = 1,
        )
    }
}

/** Interruptor do design system (trilho branco puro quando ligado). */
@Composable
fun EdSwitch(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val palette = Ed.palette
    val interaction = edInteraction(onClick = { onChange(!checked) }, focusRequester = focusRequester)
    val shape = RoundedCornerShape(percent = 50)
    val track by animateColorAsState(
        if (checked) palette.accent else palette.surface3,
        tween(180, easing = EdEasing),
        label = "switch-track",
    )
    Box(
        modifier = modifier
            .width(46.dp)
            .height(26.dp)
            .clip(shape)
            .background(track, shape)
            .then(if (interaction.isFocused) Modifier.border(3.dp, palette.fg, shape) else Modifier)
            .then(interaction.modifier),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .padding(horizontal = 3.dp)
                .size(20.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(if (checked) palette.onAccent else palette.fg2),
        )
    }
}

/**
 * Campo de texto focado por D-pad. Em TV, o foco dá o anel e a digitação usa o
 * teclado virtual do sistema — o mesmo comportamento da referência.
 */
@Composable
fun EdTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    focusRequester: FocusRequester? = null,
    trailing: (@Composable () -> Unit)? = null,
    singleLine: Boolean = true,
    minHeight: Dp = 48.dp,
) {
    val palette = Ed.palette
    val type = Ed.type
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        label?.let {
            Text(it, style = type.label, color = palette.fg3)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(minHeight)
                .clip(shape)
                .background(if (focused) palette.surface3 else palette.surface2, shape)
                .then(if (focused) Modifier.border(2.dp, palette.accent, shape) else Modifier)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                textStyle = type.bodySmall.copy(color = palette.fg),
                cursorBrush = SolidColor(palette.accent),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { focused = it.isFocused }
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
                decorationBox = { inner ->
                    Box {
                        if (value.isBlank()) {
                            Text(placeholder, style = type.bodySmall, color = palette.fg3, maxLines = 1)
                        }
                        inner()
                    }
                },
            )
            trailing?.invoke()
        }
    }
}

/**
 * Linha de configuração: ícone em tile de acento, rótulo, valor à direita e
 * ação (toggle/chevron). Réplica exata de "Settings" da referência.
 */
@Composable
fun SettingRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
    subtitle: String? = null,
    toggle: Boolean? = null,
    onToggle: ((Boolean) -> Unit)? = null,
    accent: Color? = null,
    focusRequester: FocusRequester? = null,
    danger: Boolean = false,
) {
    val palette = Ed.palette
    val type = Ed.type
    val interaction = edInteraction(onClick = onClick, focusRequester = focusRequester)
    val shape = RoundedCornerShape(16.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(if (subtitle != null) 72.dp else 64.dp)
            .clip(shape)
            .background(
                when {
                    interaction.isFocused -> palette.surface3
                    else -> Color.Transparent
                },
                shape,
            )
            .then(if (interaction.isFocused) Modifier.border(2.dp, palette.accent, shape) else Modifier)
            .then(interaction.modifier)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val tile = accent ?: palette.accent
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(tile.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tile,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = type.rowTitle.copy(fontSize = type.rowTitle.fontSize * 0.92f),
                color = if (danger) palette.bad else palette.fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = type.caption,
                    color = palette.fg3,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (toggle != null && onToggle != null) {
            EdSwitch(checked = toggle, onChange = onToggle)
        } else {
            value?.let {
                Text(
                    text = it,
                    style = type.bodySmall,
                    color = palette.fg3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = EdIcons.chevron,
                contentDescription = null,
                tint = palette.fg3,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Chip selecionável (temporadas, gêneros, atalhos). */
@Composable
fun EdChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val palette = Ed.palette
    val type = Ed.type
    val interaction = edInteraction(onClick = onClick, focusRequester = focusRequester)
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = modifier
            .height(38.dp)
            .clip(shape)
            .background(
                when {
                    interaction.isFocused -> palette.surface3
                    selected -> palette.accent
                    else -> palette.surface
                },
                shape,
            )
            .then(
                when {
                    interaction.isFocused -> Modifier.border(2.dp, palette.accent, shape)
                    selected -> Modifier
                    else -> Modifier.border(1.dp, palette.lineSoft, shape)
                },
            )
            .then(interaction.modifier)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = type.caption.copy(fontWeight = FontWeight.Medium),
            color = when {
                selected -> palette.onAccent
                interaction.isFocused -> palette.fg
                else -> palette.fg2
            },
            maxLines = 1,
        )
    }
}

/** Indicador de carregamento simples (barra indeterminada de acento). */
@Composable
fun EdLoadingBar(modifier: Modifier = Modifier) {
    val palette = Ed.palette
    Box(
        modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(palette.surface2),
    ) {
        Box(
            Modifier
                .fillMaxWidth(0.35f)
                .height(2.dp)
                .background(palette.accent),
        )
    }
}

/** Badge de status com cor semântica. */
@Composable
fun EdBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
) {
    val type = Ed.type
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (filled) color.copy(alpha = 0.18f) else Color.Transparent)
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(text, style = type.label, color = color, maxLines = 1)
    }
}

/** Divisor com espaçamento usado entre grupos de configuração. */
@Composable
fun SettingsGroup(
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = Ed.palette
    val type = Ed.type
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        title?.let {
            Text(
                text = it.uppercase(),
                style = type.label.copy(letterSpacing = 1.4.sp),
                color = palette.fg3,
                modifier = Modifier.padding(start = 14.dp, top = 14.dp, bottom = 6.dp),
            )
        }
        content()
    }
}

/** Borda com cantos arredondados (helper compartilhado). */
fun Modifier.edBorder(
    color: Color,
    shape: androidx.compose.ui.graphics.Shape,
    width: Dp = 1.dp,
): Modifier = composed {
    border(width, color, shape)
}

/**
 * Espaçador elástico para usar dentro de [Row]/[Column].
 * É `RowScope` porque `Modifier.weight` só existe nesse escopo.
 */
@Composable
fun RowScope.SpacerWeight() {
    Spacer(Modifier.weight(1f))
}

@Composable
fun VGap(height: Dp) {
    Box(Modifier.height(height))
}

/** Botão de ícone circular. */
@Composable
fun EdIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    tint: Color = Ed.palette.fg2,
    focusRequester: FocusRequester? = null,
) {
    val palette = Ed.palette
    val interaction = edInteraction(onClick = onClick, focusRequester = focusRequester)
    val circle = RoundedCornerShape(percent = 50)
    Box(
        modifier = modifier
            .size(size)
            .clip(circle)
            .background(if (interaction.isFocused) palette.surface3 else Color.Transparent, circle)
            .then(if (interaction.isFocused) Modifier.border(2.dp, palette.accent, circle) else Modifier)
            .then(interaction.modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

/** Pequena pílula de progresso (barra fina). */
@Composable
fun ThinProgress(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = Ed.palette.accent,
    track: Color = Ed.palette.fg3.copy(alpha = 0.25f),
    height: Dp = 3.dp,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .background(track),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(height)
                .background(color),
        )
    }
}

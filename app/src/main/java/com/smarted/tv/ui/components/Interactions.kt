package com.smarted.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import com.smarted.tv.ui.theme.EdPalette
import com.smarted.tv.ui.theme.edLiftScale
import androidx.compose.ui.unit.Dp

/**
 * Estados de interação reutilizáveis.
 *
 * Centraliza o par (interactionSource, focusable) para que TODO elemento
 * clicável use a mesma fonte de foco — requisito para o anel de foco de TV
 * aparecer no lugar certo, mesmo quando o foco vem do D-pad.
 */
@Composable
fun edInteraction(
    onClick: () -> Unit,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    onLongClick: (() -> Unit)? = null,
): EdInteraction {
    val source = remember { MutableInteractionSource() }
    val focused: State<Boolean> = source.collectIsFocusedAsState()
    val pressed: State<Boolean> = source.collectIsPressedAsState()
    val modifier = Modifier
        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
        .clickable(
            enabled = enabled,
            interactionSource = source,
            indication = null,
            onClick = onClick,
        )
    return EdInteraction(source, focused, pressed, modifier)
}

data class EdInteraction(
    val source: MutableInteractionSource,
    val focused: State<Boolean>,
    val pressed: State<Boolean>,
    val modifier: Modifier,
) {
    val isFocused: Boolean get() = focused.value
    val isPressed: Boolean get() = pressed.value
}

/**
 * Superfície focável genérica: fundo (surface-3) + anel de acento de 3dp.
 * Equivale a `.tv-focus-inset` da referência.
 */
fun Modifier.edFocusableSurface(
    focused: Boolean,
    shape: Shape,
    palette: EdPalette,
    showRingOnly: Boolean = false,
): Modifier = composed {
    val base = this.clip(shape)
    if (!focused) {
        base
    } else if (showRingOnly) {
        base.border(3.dpBorder, palette.accent, shape)
    } else {
        base
            .background(palette.surface3, shape)
            .border(3.dpBorder, palette.accent, shape)
    }
}

private val Int.dpBorder get() = Dp(this.toFloat())

/** Leve crescimento do card ao focar (scale 1.04, 180ms). */
fun Modifier.edFocusLift(focused: Boolean): Modifier = composed {
    this.scale(edLiftScale(focused))
}

/** Anel de foco apenas (sem preenchimento), para cards com imagem cheia. */
fun Modifier.edRing(focused: Boolean, shape: Shape, palette: EdPalette): Modifier =
    if (focused) this.border(3.dpBorder, palette.accent, shape) else this

val EdPillShape: Shape = RoundedCornerShape(percent = 50)

/** Cor de texto suave para itens não focados em listas. */
fun rowTextColor(palette: EdPalette, focused: Boolean): Color =
    if (focused) palette.fg else palette.fg2

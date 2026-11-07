package com.smarted.tv.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Curva de easing usada em toda a referência: cubic-bezier(0.16, 1, 0.3, 1).
 */
val EdEasing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

/**
 * Anel de foco de TV: desenhado *dentro* do elemento (inset), 3dp, cor de acento.
 * Réplica de `.tv-focus-inset` / `.tv-focus-card` da referência.
 */
fun Modifier.edFocusRing(
    focused: Boolean,
    shape: Shape,
    color: Color = EdColors.Accent,
    width: Dp = 3.dp,
): Modifier = if (focused) {
    this.border(BorderStroke(width, color), shape)
} else {
    this
}

/** Preenchimento de foco (surface-3) + anel, usado em listas e linhas de configuração. */
fun Modifier.edFocusSurface(
    focused: Boolean,
    shape: Shape,
    palette: EdPalette,
): Modifier = if (focused) {
    this
        .background(palette.surface3, shape)
        .border(BorderStroke(3.dp, palette.accent), shape)
} else {
    this
}

/** Elevação ("lift") dos cards ao focar: scale(1.04) em 180ms. */
@Composable
fun edLiftScale(focused: Boolean): Float {
    val value by animateFloatAsState(
        targetValue = if (focused) 1.04f else 1f,
        animationSpec = tween(durationMillis = 180, easing = EdEasing),
        label = "ed-lift",
    )
    return value
}

fun Modifier.edLift(focused: Boolean): Modifier = composed {
    this.scale(edLiftScale(focused))
}

/** Lembra uma fonte de interação e devolve os estados de foco/pressionado. */
@Composable
fun rememberFocusState(): Triple<MutableInteractionSource, State<Boolean>, State<Boolean>> {
    val source = remember { MutableInteractionSource() }
    val focused = source.collectIsFocusedAsState()
    val pressed = source.collectIsPressedAsState()
    return Triple(source, focused, pressed)
}

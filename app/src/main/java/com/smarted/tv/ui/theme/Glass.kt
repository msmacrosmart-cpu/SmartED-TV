package com.smarted.tv.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Camadas de vidro do design system.
 *
 * O Compose não tem desfoque de fundo em tempo real barato em TV box, então o
 * efeito "glass" é obtido com empilhamento:
 *   1. preenchimento translúcido (8–12%) sobre o backdrop,
 *   2. gradiente diagonal sutil que simula a luz na superfície,
 *   3. contorno de 1dp quase invisível,
 *   4. borda superior mais clara (highlight).
 *
 * Custo: dois draw calls extras por cartão — irrelevante mesmo em 1 GB de RAM.
 */
@Composable
fun Modifier.edGlass(
    shape: Shape = RoundedCornerShape(18.dp),
    tint: Color = Color(0x14FFFFFF),
    border: Color = Color(0x1AFFFFFF),
): Modifier = this
    .clip(shape)
    .background(tint)
    .background(
        Brush.linearGradient(
            listOf(Color(0x0DFFFFFF), Color(0x00FFFFFF), Color(0x06FFFFFF)),
        ),
    )
    .border(1.dp, border, shape)

/** Vidro mais forte, para barras de navegação e HUDs sobre vídeo. */
@Composable
fun Modifier.edGlassBar(
    shape: Shape = RoundedCornerShape(999.dp),
    tint: Color = Color(0x1FFFFFFF),
): Modifier = edGlass(shape = shape, tint = tint, border = Color(0x26FFFFFF))

/** Cartão de vidro com conteúdo. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.edGlass(shape), content = content)
}

/** Véu escuro para dar contraste a texto sobre imagens. */
@Composable
fun ScrimOverlay(modifier: Modifier = Modifier, alpha: Float = 0.55f) {
    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0x00000000), Color.Black.copy(alpha = alpha)),
                ),
            ),
    )
}

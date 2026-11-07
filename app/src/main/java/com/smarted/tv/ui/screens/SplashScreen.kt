package com.smarted.tv.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarted.tv.ui.theme.Ed
import com.smarted.tv.ui.theme.EdEasing

/**
 * Splash de boot — reproduz a animação da referência: ponto, arco, anel,
 * barras e wordmark entram em sequência, sincronizados com o carregamento.
 */
@Composable
fun SplashScreen(message: String) {
    val palette = Ed.palette
    val type = Ed.type
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        progress.animateTo(1f, animationSpec = tween(900, easing = LinearEasing))
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(palette.bg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                // anel
                Box(
                    Modifier
                        .size(96.dp)
                        .graphicsLayer {
                            alpha = (progress.value * 2f).coerceIn(0f, 1f)
                            scaleX = 0.8f + progress.value * 0.2f
                            scaleY = 0.8f + progress.value * 0.2f
                        }
                        .background(Color.Transparent, RoundedCornerShape(percent = 50)),
                )
                Box(
                    Modifier
                        .size(84.dp)
                        .background(palette.accent.copy(alpha = 0.12f), RoundedCornerShape(percent = 50)),
                )
                Box(
                    Modifier
                        .size(64.dp)
                        .background(palette.surface2, RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "▶",
                        style = type.title.copy(fontSize = 26.sp),
                        color = palette.accent,
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "SmartED TV",
                    style = type.title.copy(fontSize = 26.sp, letterSpacing = 0.5.sp),
                    color = palette.fg,
                    modifier = Modifier.graphicsLayer { alpha = (progress.value * 1.8f).coerceIn(0f, 1f) },
                )
                Text(
                    text = "media center modular · estilo Kodi",
                    style = type.caption,
                    color = palette.fg3,
                    modifier = Modifier.graphicsLayer { alpha = (progress.value * 1.4f).coerceIn(0f, 1f) },
                )
            }

            // barras
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.height(22.dp),
            ) {
                val heights = listOf(8, 14, 20, 14, 8)
                heights.forEachIndexed { index, h ->
                    val local = ((progress.value - index * 0.08f) * 3f).coerceIn(0f, 1f)
                    Box(
                        Modifier
                            .width(4.dp)
                            .height((h * local).dp)
                            .background(palette.accent.copy(alpha = local), RoundedCornerShape(2.dp)),
                    )
                }
            }

            Text(
                text = message,
                style = type.bodySmall,
                color = palette.fg2,
                modifier = Modifier.graphicsLayer { alpha = (progress.value * 1.2f).coerceIn(0f, 1f) },
            )
        }
    }
}

/** Fade-out suave quando o splash cede lugar ao app. */
@Composable
fun SplashExit(content: @Composable () -> Unit) {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) { alpha.animateTo(1f, tween(280, easing = EdEasing)) }
    Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha.value }) { content() }
}

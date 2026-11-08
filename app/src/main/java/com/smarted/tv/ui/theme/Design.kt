package com.smarted.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Escala de design no padrão do app de referência: todas as medidas das telas são declaradas
 * em pixels de design sobre a base 1920×1080 e convertidas aqui — o layout mantém as mesmas
 * proporções em qualquer TV/DPI (720p, 1080p, 4K). Em 1080p, 1 px de design = 0,5 dp.
 */
@Composable
private fun designFactor(): Float = (LocalConfiguration.current.screenWidthDp / 1920f).coerceIn(0.30f, 0.70f)

@Composable
fun dpx(v: Int): Dp = (v * designFactor()).dp

@Composable
fun spx(v: Int): TextUnit = (v * designFactor() * 2f).sp

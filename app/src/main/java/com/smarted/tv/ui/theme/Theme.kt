package com.smarted.tv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarted.tv.core.model.DeviceMode

/**
 * Paleta Black Glassmorphism Fumê (painéis `smokedGlass`).
 * Foco e seleção = branco puro (#FFFFFF).
 */
object Smoke {
    val Background = Color(0xFF050506)
    val Glass = Color(0xFF16181D).copy(alpha = 0.62f)
    /** Token dos painéis smokedGlass — cor base do vidro fumê. */
    val smokedGlass = Glass
    val GlassRaised = Color(0xFF20232A).copy(alpha = 0.82f)
    val GlassSelected = Color(0xFF2A2D35).copy(alpha = 0.78f)
    val Hairline = Color.White.copy(alpha = 0.12f)
    val FocusWhite = Color(0xFFFFFFFF)
    val TextPrimary = Color.White
    val TextSecondary = Color.White.copy(alpha = 0.72f)
    val TextTertiary = Color.White.copy(alpha = 0.50f)
}

/**
 * Tokens de dimensão. Os valores de TV foram desenhados para a tela-base
 * 960×540 dp (= 1080p a 320 dpi, que é o que a maioria das Android TVs/TV Boxes
 * reporta; 4K reporta 640 dpi e cai no mesmo canvas em dp). Overscan-safe: 48×27 dp.
 */
@Immutable
data class Dimens(
    val screenH: Dp, val screenV: Dp, val gutter: Dp, val corner: Dp,
    val focusBorder: Dp, val selectedBorder: Dp, val hairline: Dp, val focusScale: Float,
    val railWidth: Dp, val railItem: Dp, val fieldHeight: Dp, val chipHeight: Dp, val rowHeight: Dp,
    val tileWidth: Dp, val heroHeight: Dp,
    val textHeadline: TextUnit, val textTitle: TextUnit, val textBody: TextUnit, val textCaption: TextUnit,
    /** Largura mínima da célula do grid de capas (proporção 2:3). */
    val posterMinWidth: Dp,
)

val TvDimens = Dimens(
    screenH = 48.dp, screenV = 27.dp, gutter = 16.dp, corner = 14.dp,
    focusBorder = 3.dp, selectedBorder = 2.dp, hairline = 1.dp, focusScale = 1.06f,
    railWidth = 96.dp, railItem = 72.dp, fieldHeight = 44.dp, chipHeight = 40.dp, rowHeight = 64.dp,
    tileWidth = 160.dp, heroHeight = 300.dp,
    textHeadline = 38.sp, textTitle = 20.sp, textBody = 15.sp, textCaption = 13.sp,
    posterMinWidth = 104.dp,
)

val PhoneDimens = Dimens(
    screenH = 16.dp, screenV = 12.dp, gutter = 12.dp, corner = 12.dp,
    focusBorder = 2.dp, selectedBorder = 1.5.dp, hairline = 1.dp, focusScale = 1f,
    railWidth = 0.dp, railItem = 56.dp, fieldHeight = 48.dp, chipHeight = 36.dp, rowHeight = 64.dp,
    tileWidth = 150.dp, heroHeight = 230.dp,
    textHeadline = 26.sp, textTitle = 17.sp, textBody = 14.sp, textCaption = 12.sp,
    posterMinWidth = 104.dp,
)

val LocalDimens = staticCompositionLocalOf { PhoneDimens }
val LocalDeviceMode = staticCompositionLocalOf { DeviceMode.PHONE }

/**
 * Único modificador de foco do app: borda branco puro (#FFFFFF) — D-pad sempre visível,
 * nunca cor quente de destaque (verificado pelo gate tools/check_architecture.py).
 */
@Composable
fun Modifier.whiteFocus(width: Dp = LocalDimens.current.focusBorder): Modifier =
    this.border(width, Smoke.FocusWhite, RoundedCornerShape(LocalDimens.current.corner))

@Composable
fun SmartEDTheme(mode: DeviceMode, content: @Composable () -> Unit) {
    val dimens = if (mode == DeviceMode.TV) TvDimens else PhoneDimens
    CompositionLocalProvider(LocalDimens provides dimens, LocalDeviceMode provides mode) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = Smoke.FocusWhite, onPrimary = Color.Black,
                background = Smoke.Background, onBackground = Color.White,
                surface = Smoke.Background, onSurface = Color.White,
            ),
            content = content,
        )
    }
}

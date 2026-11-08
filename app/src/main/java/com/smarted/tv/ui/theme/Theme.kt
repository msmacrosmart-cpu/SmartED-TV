package com.smarted.tv.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object TvColors {
    // Black Glassmorphism "fumê": fundo quase preto OLED, painéis translúcidos,
    // anéis de foco/seleção do D-Pad em branco puro (#FFFFFF) com espessura reforçada.
    val Background = Color(0xFF040506)
    val Foreground = Color(0xFFF5F5F5)
    val Muted = Color(0xFFB5B6BA)
    val Focus = Color(0xFFFFFFFF)
    val GlassTop = Color(0x14FFFFFF)
    val GlassBottom = Color(0x0AFFFFFF)
    val Outline = Color(0x2EFFFFFF)
    val Scrim = Color(0xE6040506)
}
val LocalCompact = staticCompositionLocalOf { false }
val FocusRingColorKey = SemanticsPropertyKey<Color>("FocusRingColor")

/** Every focus/selection ring goes through this function. No configurable accent exists. */
fun Modifier.whiteFocus(focused: Boolean, selected: Boolean = false, radius: Dp = 16.dp): Modifier {
    val shape = RoundedCornerShape(radius)
    return this
        .then(if (focused) Modifier.border(BorderStroke(3.dp, TvColors.Focus), shape)
            else if (selected) Modifier.border(BorderStroke(2.dp, Color(0xE6FFFFFF)), shape) else Modifier)
        .semantics { if (focused) this[FocusRingColorKey] = TvColors.Focus }
}

fun Modifier.smokedGlass(radius: Dp = 16.dp): Modifier {
    val shape = RoundedCornerShape(radius)
    return this.background(Brush.verticalGradient(listOf(TvColors.GlassTop, TvColors.GlassBottom)), shape)
        .border(1.dp, TvColors.Outline, shape)
}

@Composable
fun SmartEdTheme(compact: Boolean = false, content: @Composable () -> Unit) {
    val scheme = darkColorScheme(primary = Color.White, onPrimary = Color.Black,
        secondary = Color.White, onSecondary = Color.Black, background = TvColors.Background,
        onBackground = TvColors.Foreground, surface = Color(0xFF17181B), onSurface = TvColors.Foreground,
        surfaceVariant = Color(0xFF26272A), onSurfaceVariant = TvColors.Muted,
        outline = Color.White, error = Color(0xFFFFB4AB), primaryContainer = Color(0xFF303136),
        secondaryContainer = Color(0xFF303136), tertiary = Color.White)
    val base = Typography()
    val typography = Typography(
        headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = if (compact) 30.sp else 40.sp, letterSpacing = (-1).sp),
        headlineMedium = base.headlineMedium.copy(fontSize = if (compact) 23.sp else 28.sp),
        titleLarge = base.titleLarge.copy(fontSize = if (compact) 19.sp else 22.sp),
        titleMedium = base.titleMedium.copy(fontSize = if (compact) 15.sp else 17.sp),
        bodyLarge = base.bodyLarge.copy(fontSize = if (compact) 14.sp else 16.sp, lineHeight = if (compact) 20.sp else 24.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = if (compact) 12.sp else 14.sp, lineHeight = if (compact) 18.sp else 21.sp),
        labelLarge = base.labelLarge.copy(fontSize = if (compact) 13.sp else 15.sp),
        labelMedium = base.labelMedium.copy(fontSize = if (compact) 11.sp else 13.sp),
    )
    androidx.compose.runtime.CompositionLocalProvider(LocalCompact provides compact) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}

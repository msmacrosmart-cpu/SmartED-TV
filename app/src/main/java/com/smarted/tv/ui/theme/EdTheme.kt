package com.smarted.tv.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Tokens visuais portados 1:1 do design de referência (Extreme-InfiniTV).
 *
 * Fundação "slate" (oklch L=.165 C=.013 H=255) + acento fúcsia suave
 * (oklch L=.78 C=.15 H=330). Os valores hexadecimais abaixo foram convertidos
 * de OKLCH para sRGB para que o resultado em Compose seja idêntico ao original.
 */
object EdColors {
    // superfícies (tema escuro — padrão de um media center)
    val Bg = Color(0xFF0B0F14)
    val Surface = Color(0xFF151A1F)
    val Surface2 = Color(0xFF21262C)
    val Surface3 = Color(0xFF2E333A)
    val Line = Color(0xFF33383F)
    val LineSoft = Color(0xFF22272D)

    // primeiro plano
    val Fg = Color(0xFFF3F5F8)
    val Fg2 = Color(0xFFB2B8BE)
    val Fg3 = Color(0xFF7B8187)

    // acento único (fúcsia suave)
    val Accent = Color(0xFFEC92E5)
    val AccentSoft = Color(0x2EEC92E5)
    val OnAccent = Color(0xFF0B0F14)

    // estados
    val Ok = Color(0xFF65D49E)
    val Warn = Color(0xFFF7C15F)
    val Bad = Color(0xFFFF756F)

    // tons de imagem
    val Scrim = Color(0xE60B0F14)
    val ScrimSoft = Color(0x990B0F14)

    // tema claro (o app abre escuro; a opção existe para paridade com a referência)
    val LightBg = Color(0xFFF2DFF0)
    val LightSurface = Color(0xFFFAF5F9)
    val LightSurface2 = Color(0xFFE9DBE7)
    val LightSurface3 = Color(0xFFDFCDDC)
    val LightLine = Color(0xFFCDB6C9)
    val LightFg = Color(0xFF241722)
    val LightFg2 = Color(0xFF6B5A68)
    val LightFg3 = Color(0xFF7C6C79)
    val LightAccent = Color(0xFFC21FAB)
}

/** Paletas de acento suportadas (idênticas às da referência). */
@Immutable
data class AccentSwatch(
    val id: String,
    val label: String,
    val dark: Color,
    val light: Color,
    val onAccentDark: Color = EdColors.OnAccent,
    val onAccentLight: Color = Color.White,
)

object Accents {
    val all = listOf(
        AccentSwatch("fuchsia", "Fúcsia", Color(0xFFEC92E5), Color(0xFFC21FAB)),
        AccentSwatch("rose", "Rosé", Color(0xFFFF8D8F), Color(0xFFC21F41)),
        AccentSwatch("ember", "Âmbar", Color(0xFFFF9B50), Color(0xFFC25212)),
        AccentSwatch("emerald", "Esmeralda", Color(0xFF4AD496), Color(0xFF12804F)),
        AccentSwatch("cyan", "Ciano", Color(0xFF00D0EC), Color(0xFF00738A)),
        AccentSwatch("blue", "Azul", Color(0xFF65BDFF), Color(0xFF1667A8)),
        AccentSwatch("violet", "Violeta", Color(0xFFBFA2FF), Color(0xFF5B32B8)),
        AccentSwatch("gold", "Ouro", Color(0xFFFDC436), Color(0xFF8A6100), Color(0xFF0B0F14), Color(0xFF0B0F14)),
        AccentSwatch("lime", "Lima", Color(0xFF98DC38), Color(0xFF4B7A00), Color(0xFF0B0F14), Color(0xFFFFFFFF)),
        AccentSwatch("teal", "Verde-água", Color(0xFF37D8C9), Color(0xFF0A7A72), Color(0xFF0B0F14), Color.White),
        AccentSwatch("silver", "Prata", Color(0xFFBABEC4), Color(0xFF5A606A), Color(0xFF0B0F14), Color.White),
        AccentSwatch("white", "Branco", Color(0xFFF5F5F5), Color(0xFF3B3B3B), Color(0xFF0B0F14), Color.White),
    )

    fun byId(id: String?): AccentSwatch = all.firstOrNull { it.id == id } ?: all.first()
}

/** Escalas de espaçamento, raios e tipos do design de referência. */
@Immutable
data class EdDimens(
    val railWidth: Dp = 72.dp,
    val contentPadding: Dp = 32.dp,
    val gap: Dp = 16.dp,
    val gapSmall: Dp = 8.dp,
    val cardRadius: Dp = 14.dp,
    val panelRadius: Dp = 18.dp,
    val pillRadius: Dp = 999.dp,
    val focusRingWidth: Dp = 3.dp,
    val overscan: Dp = 24.dp,
    val portraitWidth: Dp = 178.dp,
    val portraitHeight: Dp = 264.dp,
    val landscapeWidth: Dp = 268.dp,
    val landscapeHeight: Dp = 151.dp,
    val posterGridMin: Dp = 156.dp,
)

val LocalEdDimens = staticCompositionLocalOf { EdDimens() }

/** Paleta resolvida em tempo de composição (tema + acento). */
@Immutable
data class EdPalette(
    val dark: Boolean,
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val surface3: Color,
    val line: Color,
    val lineSoft: Color,
    val fg: Color,
    val fg2: Color,
    val fg3: Color,
    val accent: Color,
    val accentSoft: Color,
    val onAccent: Color,
    val ok: Color,
    val warn: Color,
    val bad: Color,
)

fun paletteFor(dark: Boolean, accent: AccentSwatch): EdPalette = if (dark) {
    EdPalette(
        dark = true,
        bg = EdColors.Bg,
        surface = EdColors.Surface,
        surface2 = EdColors.Surface2,
        surface3 = EdColors.Surface3,
        line = EdColors.Line,
        lineSoft = EdColors.LineSoft,
        fg = EdColors.Fg,
        fg2 = EdColors.Fg2,
        fg3 = EdColors.Fg3,
        accent = accent.dark,
        accentSoft = accent.dark.copy(alpha = 0.18f),
        onAccent = accent.onAccentDark,
        ok = EdColors.Ok,
        warn = EdColors.Warn,
        bad = EdColors.Bad,
    )
} else {
    EdPalette(
        dark = false,
        bg = EdColors.LightBg,
        surface = EdColors.LightSurface,
        surface2 = EdColors.LightSurface2,
        surface3 = EdColors.LightSurface3,
        line = EdColors.LightLine,
        lineSoft = EdColors.LightSurface2,
        fg = EdColors.LightFg,
        fg2 = EdColors.LightFg2,
        fg3 = EdColors.LightFg3,
        accent = accent.light,
        accentSoft = accent.light.copy(alpha = 0.14f),
        onAccent = accent.onAccentLight,
        ok = Color(0xFF1D7A50),
        warn = Color(0xFF92600A),
        bad = Color(0xFFB32E28),
    )
}

val LocalEdPalette = staticCompositionLocalOf { paletteFor(true, Accents.all.first()) }
val LocalEdAccent = staticCompositionLocalOf { Accents.all.first() }

/** Escala tipográfica. O canvas de TV é escalado por [LocalFontScale]. */
@Immutable
data class EdTypography(
    val display: TextStyle,
    val title: TextStyle,
    val sectionTitle: TextStyle,
    val rowTitle: TextStyle,
    val body: TextStyle,
    val bodySmall: TextStyle,
    val caption: TextStyle,
    val eyebrow: TextStyle,
    val label: TextStyle,
    val mono: TextStyle,
)

fun typographyFor(scale: Float = 1f): EdTypography {
    fun s(v: TextUnit): TextUnit = (v.value * scale).sp
    return EdTypography(
        display = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = s(34.sp),
            lineHeight = s(40.sp),
            letterSpacing = (-0.6).sp,
        ),
        title = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = s(22.sp),
            lineHeight = s(28.sp),
            letterSpacing = (-0.2).sp,
        ),
        sectionTitle = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = s(19.sp),
            lineHeight = s(24.sp),
            letterSpacing = (-0.2).sp,
        ),
        rowTitle = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontSize = s(16.sp),
            lineHeight = s(22.sp),
        ),
        body = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = s(14.sp),
            lineHeight = s(20.sp),
        ),
        bodySmall = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = s(12.5.sp),
            lineHeight = s(18.sp),
        ),
        caption = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = s(11.5.sp),
            lineHeight = s(16.sp),
            letterSpacing = 0.1.sp,
        ),
        eyebrow = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = s(11.sp),
            lineHeight = s(14.sp),
            letterSpacing = 2.4.sp,
        ),
        label = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = s(10.sp),
            lineHeight = s(12.sp),
            letterSpacing = 0.6.sp,
        ),
        mono = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            fontSize = s(12.sp),
            lineHeight = s(16.sp),
            letterSpacing = 0.2.sp,
        ),
    )
}

val LocalEdTypography = staticCompositionLocalOf { typographyFor() }

/** Raios padrão do sistema. */
object EdShapes {
    val card = RoundedCornerShape(14.dp)
    val panel = RoundedCornerShape(18.dp)
    val pill = RoundedCornerShape(999.dp)
    val small = RoundedCornerShape(10.dp)
    val thumb = RoundedCornerShape(8.dp)
}

@Composable
fun SmartEdTheme(
    darkTheme: Boolean = true,
    accentId: String? = "fuchsia",
    fontScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val accent = Accents.byId(accentId)
    val palette = paletteFor(darkTheme, accent)
    val typography = typographyFor(fontScale)
    val baseScheme = if (darkTheme) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = palette.onAccent,
            background = palette.bg,
            onBackground = palette.fg,
            surface = palette.surface,
            onSurface = palette.fg,
            surfaceVariant = palette.surface2,
            onSurfaceVariant = palette.fg2,
            outline = palette.line,
            error = palette.bad,
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = palette.onAccent,
            background = palette.bg,
            onBackground = palette.fg,
            surface = palette.surface,
            onSurface = palette.fg,
            surfaceVariant = palette.surface2,
            onSurfaceVariant = palette.fg2,
            outline = palette.line,
            error = palette.bad,
        )
    }

    val m3Typography = Typography(
        headlineLarge = typography.display,
        headlineMedium = typography.title,
        titleLarge = typography.sectionTitle,
        titleMedium = typography.rowTitle,
        bodyLarge = typography.body,
        bodyMedium = typography.bodySmall,
        bodySmall = typography.caption,
        labelLarge = typography.label,
        labelSmall = typography.label,
    )

    CompositionLocalProvider(
        LocalEdPalette provides palette,
        LocalEdAccent provides accent,
        LocalEdTypography provides typography,
    ) {
        MaterialTheme(
            colorScheme = baseScheme,
            typography = m3Typography,
            content = content,
        )
    }
}

/** Acesso curto aos tokens dentro de @Composable. */
object Ed {
    val palette: EdPalette
        @Composable get() = LocalEdPalette.current
    val type: EdTypography
        @Composable get() = LocalEdTypography.current
    val dimen: EdDimens
        @Composable get() = LocalEdDimens.current
}

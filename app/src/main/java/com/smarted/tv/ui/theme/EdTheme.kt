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
 * Fundação quase preta (#07080B) + **acento branco puro (#FFFFFF)** em todo
 * foco, seleção, anel e ação primária. Não existe matiz de acento: a interface
 * é neutra e o destaque vem de contraste e de camadas de vidro (Glass.kt).
 *
 * O layout cinematográfico (palco + trilhas + doca flutuante) usa os mesmos
 * tokens, apenas com mais camadas translúcidas sobre a arte.
 */
object EdColors {
    // ---- fundação escura (base das camadas de vidro) ----
    // Preto levemente azulado: é o que faz o branco puro do foco "acender" na TV.
    val Bg = Color(0xFF07080B)
    val BgDeep = Color(0xFF030406)

    // ---- superfícies (glassmorphism: mais claras e translúcidas em camadas) ----
    val Surface = Color(0xFF10131A)
    val Surface2 = Color(0xFF171B23)
    val Surface3 = Color(0xFF222833)
    val Line = Color(0xFF2E3542)
    val LineSoft = Color(0xFF1B2029)

    // vidro: preenchimentos translúcidos para empilhar sobre backdrops
    val Glass = Color(0x14FFFFFF)      // 8%  — cartões sobre imagem
    val GlassStrong = Color(0x24FFFFFF) // 14% — painéis/barras
    val GlassBorder = Color(0x1AFFFFFF) // 10% — contorno de 1px
    val GlassBorderStrong = Color(0x33FFFFFF)

    // primeiro plano
    val Fg = Color(0xFFF7F8FA)
    val Fg2 = Color(0xFFAFB6C0)
    val Fg3 = Color(0xFF77808D)

    // ---- acento único: BRANCO PURO (foco/seleção de D-pad) ----
    val Accent = Color(0xFFFFFFFF)
    val AccentSoft = Color(0x1FFFFFFF)
    val OnAccent = Color(0xFF0A0C10)

    // estados
    val Ok = Color(0xFF5FD3A0)
    val Warn = Color(0xFFF2C14E)
    val Bad = Color(0xFFF0736E)

    // tons de imagem
    val Scrim = Color(0xE60A0C10)
    val ScrimSoft = Color(0x990A0C10)

    // tema claro (secundário; o app abre escuro)
    val LightBg = Color(0xFFF4F5F7)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurface2 = Color(0xFFEBEDF1)
    val LightSurface3 = Color(0xFFDFE3E9)
    val LightLine = Color(0xFFC9CFD8)
    val LightFg = Color(0xFF12151B)
    val LightFg2 = Color(0xFF5A6270)
    val LightFg3 = Color(0xFF6B7280)
    val LightAccent = Color(0xFF12151B)
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
    /**
     * O foco do D-pad é BRANCO PURO por decisão de produto. Mantemos apenas
     * variações neutras para não introduzir matiz na interface.
     */
    val all = listOf(
        AccentSwatch("white", "Branco", Color(0xFFFFFFFF), Color(0xFF12151B)),
        AccentSwatch("silver", "Prata", Color(0xFFE6E9EE), Color(0xFF2B3038)),
        AccentSwatch("ash", "Cinza", Color(0xFFC3C9D2), Color(0xFF3A4048)),
    )

    fun byId(id: String?): AccentSwatch = all.firstOrNull { it.id == id } ?: all.first()

    /** Acento efetivo: branco. Ignora configuração antiga com matiz. */
    val Focus: Color = EdColors.Accent
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
    // ---- nova estrutura "palco" (Stremio-like) ----
    /** Largura da doca lateral flutuante (ícone + rótulo). */
    val dockWidth: Dp = 96.dp,
    /** Recuo do conteúdo que fica sob a doca. */
    val dockInset: Dp = 84.dp,
    /** Altura mínima/máxima do palco cinematográfico da Home. */
    val stageMin: Dp = 300.dp,
    val stageMax: Dp = 620.dp,
)

val LocalEdDimens = staticCompositionLocalOf { EdDimens() }

/** Paleta resolvida em tempo de composição (tema + acento). */
@Immutable
data class EdPalette(
    val dark: Boolean,
    val bg: Color,
    /** Preto absoluto das áreas sem conteúdo (esqueleto do palco). */
    val bgDeep: Color = Color(0xFF030406),
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
    // ---- glassmorphism ----
    val glass: Color = Color(0x14FFFFFF),
    val glassStrong: Color = Color(0x1FFFFFFF),
    val glassBorder: Color = Color(0x1AFFFFFF),
    val glassHighlight: Color = Color(0x0DFFFFFF),
    /** Preenchimento de vidro escuro para HUDs e botões secundários. */
    val glassDark: Color = Color(0x66000000),
    /** Contorno de vidro em painéis que estão sobre a arte (doca/HUD). */
    val glassBorderStrong: Color = Color(0x33FFFFFF),
    /** Texto sobre a arte do palco (sempre branco puro). */
    val onArt: Color = Color(0xFFFFFFFF),
)

fun paletteFor(dark: Boolean, accent: AccentSwatch): EdPalette = if (dark) {
    EdPalette(
        dark = true,
        bg = EdColors.Bg,
        bgDeep = EdColors.BgDeep,
        surface = EdColors.Surface,
        surface2 = EdColors.Surface2,
        surface3 = EdColors.Surface3,
        line = EdColors.Line,
        lineSoft = EdColors.LineSoft,
        fg = EdColors.Fg,
        fg2 = EdColors.Fg2,
        fg3 = EdColors.Fg3,
        // foco/seleção é sempre branco puro, independente do swatch
        accent = EdColors.Accent,
        accentSoft = EdColors.AccentSoft,
        onAccent = EdColors.OnAccent,
        ok = EdColors.Ok,
        warn = EdColors.Warn,
        bad = EdColors.Bad,
    )
} else {
    EdPalette(
        dark = false,
        bg = EdColors.LightBg,
        bgDeep = Color(0xFFE8EAEF),
        glassDark = Color(0x1F000000),
        glassBorderStrong = Color(0x33000000),
        surface = EdColors.LightSurface,
        surface2 = EdColors.LightSurface2,
        surface3 = EdColors.LightSurface3,
        line = EdColors.LightLine,
        lineSoft = EdColors.LightSurface2,
        fg = EdColors.LightFg,
        fg2 = EdColors.LightFg2,
        fg3 = EdColors.LightFg3,
        accent = EdColors.LightAccent,
        accentSoft = EdColors.LightAccent.copy(alpha = 0.14f),
        onAccent = Color.White,
        ok = Color(0xFF1D7A50),
        warn = Color(0xFF8A5A08),
        bad = Color(0xFFB3352F),
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
    accentId: String? = "white",
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

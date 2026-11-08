package com.smarted.tv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.focus.onFocusEvent
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarted.tv.core.model.VodEntry
import com.smarted.tv.ui.theme.TvColors
import com.smarted.tv.ui.theme.dpx
import com.smarted.tv.ui.theme.spx
import com.smarted.tv.ui.theme.whiteFocus

/**
 * Camada visual no padrão IPVIO-Stream (composição reimplementada, tema Black Glassmorphism
 * fumê sobre preto quase-OLED): superfícies translúcidas, cantos generosos, anéis de
 * foco/seleção do D-pad em branco puro #FFFFFF e tipografia em escala de design (dpx/spx).
 */

// ---------------------------------------------------------------- superfícies

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    radius: Int = 30,
    padding: PaddingValues = PaddingValues(dpx(22)),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(dpx(radius)))
            .background(Brush.verticalGradient(listOf(Color(0x16FFFFFF), Color(0x08FFFFFF))))
            .border(1.dp, TvColors.Outline, RoundedCornerShape(dpx(radius)))
            .padding(padding),
        content = content,
    )
}

// ---------------------------------------------------------------- cartões Home

@Composable
fun HomeMenuTile(
    label: String,
    subtitle: String,
    icon: ImageVector,
    tag: String,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val scale by animateFloatAsState(if (focused) 1.035f else 1f, label = "menuTile")
    Row(
        Modifier
            .fillMaxWidth()
            .testTag(tag)
            .scale(scale)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .whiteFocus(focused, radius = dpx(38))
            .padding(dpx(4))
            .clip(RoundedCornerShape(dpx(30)))
            .background(
                if (focused) SolidColor(Color(0x2FFFFFFF))
                else Brush.verticalGradient(listOf(Color(0x16FFFFFF), Color(0x08FFFFFF))),
            )
            .border(1.dp, if (focused) Color.White else TvColors.Outline, RoundedCornerShape(dpx(30)))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = dpx(42), vertical = dpx(24)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dpx(28)),
    ) {
        Box(
            Modifier
                .size(dpx(76))
                .clip(RoundedCornerShape(dpx(22)))
                .background(if (focused) Color.White else Color(0x26FFFFFF)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (focused) Color.Black else Color.White, modifier = Modifier.size(dpx(40)))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(dpx(4))) {
            Text(label, color = Color.White, fontSize = spx(28), fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = TvColors.Muted, fontSize = spx(16), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Default.PlayArrow, null, Modifier.size(dpx(26)), tint = if (focused) Color.White else TvColors.Muted)
    }
}

// ---------------------------------------------------------------- seções de conteúdo

@Composable
fun ContentRow(
    title: String,
    count: Int? = null,
    tag: String,
    trailing: (@Composable () -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth().testTag(tag), verticalArrangement = Arrangement.spacedBy(dpx(12))) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dpx(12))) {
            Text(title, color = Color.White, fontSize = spx(23), fontWeight = FontWeight.SemiBold)
            if (count != null && count > 0) {
                Box(Modifier.clip(RoundedCornerShape(dpx(20))).background(Color(0x1FFFFFFF)).padding(horizontal = dpx(12), vertical = dpx(3))) {
                    Text("%,d".format(count).replace(',', '.'), color = TvColors.Muted, fontSize = spx(14))
                }
            }
            if (trailing != null) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { trailing() }
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(dpx(18)), content = content)
    }
}

/**
 * Contêineres roláveis em TV: contêineres com verticalScroll não seguem o foco sozinhos
 * (comportamento do Compose). Este Modifier anexa um BringIntoViewRequester ao item — ao
 * receber foco do D-pad, rola o contêiner pai para torná-lo visível.
 */
@Composable
fun Modifier.keepFocusInView(): Modifier {
    val requester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    return this
        .bringIntoViewRequester(requester)
        .onFocusEvent { state -> if (state.isFocused) scope.launch { runCatching { requester.bringIntoView() } } }
}

/** Cartaz estilo Smarters Pro: pôster 2:3 com título e metadados ABAIXO (padrão do app de referência). */
@Composable
fun PosterCell(entry: VodEntry, onClick: () -> Unit, modifier: Modifier = Modifier, cellWidth: androidx.compose.ui.unit.Dp? = null) {
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier
            .then(if (cellWidth != null) Modifier.width(cellWidth) else Modifier)
            .testTag("vod-card-${entry.id}")
            .keepFocusInView()
            .onFocusChanged { focused = it.isFocused }
            .whiteFocus(focused, radius = dpx(24))
            .padding(dpx(4))
            .clip(RoundedCornerShape(dpx(16)))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(dpx(8)),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(dpx(16))).background(Color(0xFF0D0E10))) {
            RemoteImage(entry.posterUrl, entry.name, Modifier.matchParentSize(), ContentScale.Crop)
            if (entry.rating != null && entry.rating > 0) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(dpx(8))
                        .background(Color(0x99000000), RoundedCornerShape(dpx(10)))
                        .padding(horizontal = dpx(10), vertical = dpx(3)),
                ) {
                    Text("★ %.1f".format(entry.rating), color = Color.White, fontSize = spx(14), fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Text(entry.name, color = if (focused) Color.White else Color(0xE8FFFFFF), fontSize = spx(17), fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            listOfNotNull(entry.year?.toString(), entry.category.takeIf { it.isNotBlank() }).joinToString(" · ")
                .ifBlank { if (entry.kind.name == "SERIES") "Série" else "Filme" },
            color = TvColors.Muted, fontSize = spx(13), maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---------------------------------------------------------------- filtros & pills

@Composable
fun FilterPill(label: String, active: Boolean, tag: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .testTag(tag)
            .keepFocusInView()
            .onFocusChanged { focused = it.isFocused }
            .whiteFocus(focused, active, dpx(28))
            .padding(dpx(3))
            .clip(RoundedCornerShape(dpx(24)))
            .background(if (active) Color.White else if (focused) Color(0x2EFFFFFF) else Color(0x14FFFFFF))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = dpx(20), vertical = dpx(9)),
    ) {
        Text(
            label,
            color = if (active) Color.Black else if (focused || active) Color.White else TvColors.Muted,
            fontSize = spx(16), fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1,
        )
    }
}

// ---------------------------------------------------------------- campo com cola (TV)

@Composable
fun PasteField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    tag: String = "field",
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .testTag(tag)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .keepFocusInView()
            .onFocusChanged { focused = it.isFocused }
            .whiteFocus(focused, radius = dpx(20)),
        placeholder = { Text(hint, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = spx(17)) },
        trailingIcon = {
            TextButton(onClick = { clipboard.getText()?.text?.let { onValueChange(it.trim()) } }) {
                Text("Colar", color = Color.White, fontSize = spx(16))
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(dpx(16)),
        textStyle = MaterialTheme.typography.bodyMedium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.White,
            unfocusedBorderColor = TvColors.Outline,
            cursorColor = Color.White,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedPlaceholderColor = TvColors.Muted,
            unfocusedPlaceholderColor = TvColors.Muted,
            focusedContainerColor = Color(0xFF0C0D0F),
            unfocusedContainerColor = Color(0xFF08090A),
            selectionColors = androidx.compose.foundation.text.selection.TextSelectionColors(Color(0x66FFFFFF), Color.White),
        ),
    )
}

// ---------------------------------------------------------------- linha do header

@Composable
fun HeaderTitleBlock(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier.fillMaxWidth().padding(horizontal = dpx(30), vertical = dpx(12)), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = spx(30), fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp, maxLines = 1)
            if (subtitle.isNotBlank()) Text(subtitle, color = TvColors.Muted, fontSize = spx(15), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        actions()
        ClockHeader(Modifier.padding(horizontal = dpx(10)))
    }
}

/** Botão de ícone do header (engrenagem etc.) — vidro fumê, anel branco no foco. */
@Composable
fun HeaderIconButton(icon: ImageVector, tag: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "headerIcon")
    Box(
        Modifier
            .testTag(tag)
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .whiteFocus(focused, radius = dpx(20))
            .padding(dpx(3))
            .clip(RoundedCornerShape(dpx(16)))
            .background(if (focused) Color.White else Color(0x14FFFFFF))
            .border(1.dp, TvColors.Outline, RoundedCornerShape(dpx(16)))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .size(dpx(48)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (focused) Color.Black else Color.White, modifier = Modifier.size(dpx(26)))
    }
}

@Composable
fun SectionHead(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier, color = TvColors.Muted, fontSize = spx(14), fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp)
}

// ---------------------------------------------------------------- tile de status (Sistema)

@Composable
fun StatTile(label: String, value: Int, modifier: Modifier = Modifier, tag: String = "stat") {
    Column(
        modifier.testTag(tag).clip(RoundedCornerShape(dpx(18)))
            .background(Color(0x10FFFFFF)).padding(horizontal = dpx(26), vertical = dpx(14)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("%,d".format(value).replace(',', '.'), color = Color.White, fontSize = spx(30), fontWeight = FontWeight.SemiBold)
        Text(label, color = TvColors.Muted, fontSize = spx(14))
    }
}

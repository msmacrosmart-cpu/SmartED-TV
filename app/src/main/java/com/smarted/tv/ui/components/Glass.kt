package com.smarted.tv.ui.components

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.focus.onFocusEvent
import kotlinx.coroutines.launch
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.smarted.tv.core.model.DeviceMode
import com.smarted.tv.ui.theme.LocalDeviceMode
import com.smarted.tv.ui.theme.LocalDimens
import com.smarted.tv.ui.theme.Smoke

/** Fundo global: imagem desfocada (API 31+) ou gradiente fumê + véu escuro. */
@Composable
fun GlassBackdrop(
    imageUrl: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.fillMaxSize().background(Smoke.Background)) {
        if (imageUrl != null) {
            val blurMod = if (Build.VERSION.SDK_INT >= 31) Modifier.blur(28.dp) else Modifier
            AsyncImage(
                model = imageUrl, contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize().then(blurMod).alpha(0.55f),
            )
        } else {
            Box(Modifier.fillMaxSize().background(
                Brush.linearGradient(listOf(Color(0xFF1B1D22), Color(0xFF08090B), Color(0xFF000000)))))
        }
        Box(Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.30f), Color.Black.copy(alpha = 0.80f)))))
        content()
    }
}

/** Superfície de vidro fumê (painéis estáticos). */
@Composable
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(LocalDimens.current.corner),
    fill: Color = Smoke.Glass,
    borderColor: Color = Smoke.Hairline,
    borderWidth: Dp = LocalDimens.current.hairline,
): Modifier = this.clip(shape).background(fill).border(borderWidth, borderColor, shape)

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    padding: Dp = 12.dp,
    fill: Color = Smoke.Glass,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.glass(fill = fill).padding(padding), content = content)
}

/**
 * Primitiva interativa única do app (D-pad + toque).
 * - Foco: borda BRANCA #FFFFFF (espessura em dp vem de Dimens) + leve escala na TV.
 * - Selecionado: borda branca fina.
 * - Idle: vidro fumê com hairline branco 12 %.
 * A borda é desenhada POR CIMA do conteúdo (não some atrás de imagens).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlassClickable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    flat: Boolean = false,
    corner: Dp = LocalDimens.current.corner,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onFocused: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val d = LocalDimens.current
    val isTv = LocalDeviceMode.current == DeviceMode.TV
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = when {
            focused && isTv -> d.focusScale
            pressed -> 0.97f
            else -> 1f
        },
        animationSpec = tween(120), label = "glassScale",
    )
    // Fit-or-scroll: o foco do D-pad puxa o item para dentro do viewport (listas, trilhos, pills).
    val bringIntoView = remember { BringIntoViewRequester() }
    val bringScope = rememberCoroutineScope()
    LaunchedEffect(focused) { if (focused) onFocused?.invoke() }

    val shape = RoundedCornerShape(corner)
    val fill = when {
        focused -> Smoke.GlassRaised
        selected -> Smoke.GlassSelected
        flat -> Color.Transparent
        else -> Smoke.Glass
    }
    val borderColor = when {
        focused || selected -> Smoke.FocusWhite
        flat -> Color.Transparent
        else -> Smoke.Hairline
    }
    val borderWidth = when {
        focused -> d.focusBorder
        selected -> d.selectedBorder
        else -> d.hairline
    }

    Box(
        modifier
            .bringIntoViewRequester(bringIntoView)
            .onFocusEvent { e ->
                if (e.isFocused) bringScope.launch { runCatching { bringIntoView.bringIntoView() } }
            }
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(fill)
            .combinedClickable(
                interactionSource = interaction, indication = null,
                onClick = onClick, onLongClick = onLongClick,
            )
            .drawWithContent {
                drawContent()
                val w = borderWidth.toPx()
                drawRoundRect(
                    color = borderColor,
                    topLeft = Offset(w / 2, w / 2),
                    size = Size(size.width - w, size.height - w),
                    cornerRadius = CornerRadius((corner.toPx() - w / 2).coerceAtLeast(0f)),
                    style = Stroke(width = w),
                )
            }
            .padding(contentPadding),
    ) { content(focused) }
}

@Composable
fun GlassChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val d = LocalDimens.current
    GlassClickable(
        onClick = onClick, selected = selected, corner = d.chipHeight / 2,
        modifier = modifier.height(d.chipHeight),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) { focused ->
        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(18.dp), tint = Smoke.TextPrimary)
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text, maxLines = 1, fontSize = d.textBody,
                fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected || focused) Smoke.TextPrimary else Smoke.TextSecondary,
            )
        }
    }
}

@Composable
fun GlassButton(text: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier = Modifier) =
    GlassChip(text, selected = false, onClick = onClick, modifier = modifier, icon = icon)

/** Campo de texto em vidro fumê; borda branca ao focar. Usado por TODAS as buscas. */
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = Icons.Rounded.Search,
    password: Boolean = false,
    imeAction: ImeAction = ImeAction.Search,
    onSubmit: (() -> Unit)? = null,
) {
    val d = LocalDimens.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(d.corner)
    Row(
        modifier.fillMaxWidth().height(d.fieldHeight)
            .clip(shape)
            .background(if (focused) Smoke.GlassRaised else Smoke.Glass)
            .border(
                if (focused) d.focusBorder else d.hairline,
                if (focused) Smoke.FocusWhite else Smoke.Hairline, shape,
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, null, Modifier.size(20.dp), tint = Smoke.TextSecondary)
            Spacer(Modifier.width(10.dp))
        }
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, fontSize = d.textBody, color = Smoke.TextTertiary, maxLines = 1)
            BasicTextField(
                value = value, onValueChange = onValueChange, singleLine = true,
                interactionSource = interaction,
                textStyle = TextStyle(color = Smoke.TextPrimary, fontSize = d.textBody),
                cursorBrush = SolidColor(Smoke.FocusWhite),
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(imeAction = imeAction),
                keyboardActions = KeyboardActions(
                    onSearch = { onSubmit?.invoke() }, onDone = { onSubmit?.invoke() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                Icons.Rounded.Close, "Limpar",
                Modifier.size(20.dp).clickable { onValueChange("") },
                tint = Smoke.TextSecondary,
            )
        }
    }
}

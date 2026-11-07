package com.smarted.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.smarted.tv.ui.theme.Ed

/**
 * Peças de vidro do player.
 *
 * O HUD do player não usa fundo de tela cheia: cada barra é um **painel de
 * vidro escuro flutuante** (preenchimento translúcido + contorno branco de
 * 1dp). Os elementos internos são branco puro (#FFFFFF) e o foco do D-pad
 * acrescenta um anel branco de 2dp. É o mesmo vocabulário do palco da Home,
 * agora sobre o vídeo.
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    padding: Dp = 18.dp,
    spacing: Dp = 10.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = Ed.palette
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(palette.glassDark, shape)
            .border(1.dp, palette.glassBorderStrong, shape)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

/**
 * Pílula de controle do player.
 *
 * [primary] = pílula branca (play/pause). Nas demais, o vidro escuro mantém a
 * leitura sobre qualquer cena, inclusive cenas claras.
 */
@Composable
fun GlassPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
    focusRequester: FocusRequester? = null,
) {
    val palette = Ed.palette
    val type = Ed.type
    val interaction = edInteraction(onClick = onClick, focusRequester = focusRequester)
    val focused = interaction.isFocused
    val shape = RoundedCornerShape(percent = 50)

    val fill = when {
        primary -> palette.onArt
        focused -> palette.glassStrong
        else -> palette.glassDark
    }
    val contentColor = if (primary) palette.onAccent else palette.onArt

    Row(
        modifier = modifier
            .height(42.dp)
            .clip(shape)
            .background(fill, shape)
            .then(
                if (focused) {
                    Modifier.border(2.dp, palette.onArt, shape)
                } else {
                    Modifier.border(1.dp, palette.glassBorderStrong, shape)
                },
            )
            .then(interaction.modifier)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        icon?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(17.dp),
            )
        }
        Text(
            text = label,
            style = type.caption.copy(fontWeight = FontWeight.Medium),
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

package com.smarted.tv.ui.components

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarted.tv.R
import com.smarted.tv.ui.theme.Ed
import com.smarted.tv.ui.theme.EdEasing

/**
 * Doca lateral flutuante — a espinha dorsal da nova navegação.
 *
 * Diferente da rail antiga (uma faixa sólida que empurrava o conteúdo para a
 * direita), a doca **flutua sobre a arte**: o backdrop do palco sangra até a
 * borda esquerda da tela e um véu de vidro em degradê mantém os ícones
 * legíveis. É o que dá o ar cinematográfico do layout de referência.
 *
 * Cada destino tem ícone **e** rótulo em caixa alta (some quando a tela é
 * baixa, para nunca cortar item). O estado de foco é branco puro (#FFFFFF):
 * preenchimento de vidro + anel de 2dp + rótulo aceso.
 */
enum class EdDestination(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    val route: String,
) {
    HOME(R.string.nav_home, EdIcons.home, "home"),
    LIVE(R.string.nav_live, EdIcons.live, "live"),
    MOVIES(R.string.nav_movies, EdIcons.movies, "movies"),
    SERIES(R.string.nav_series, EdIcons.series, "series"),
    GUIDE(R.string.nav_guide, EdIcons.guide, "guide"),
    SEARCH(R.string.nav_search, EdIcons.search, "search"),
    ADDONS(R.string.nav_addons, EdIcons.addon, "addons"),
    SETTINGS(R.string.nav_settings, EdIcons.settings, "settings"),
}

/** Destinos com rótulo (navegação principal) e só ícone (apoio). */
private val PRIMARY_DESTINATIONS = listOf(
    EdDestination.HOME,
    EdDestination.LIVE,
    EdDestination.MOVIES,
    EdDestination.SERIES,
    EdDestination.GUIDE,
    EdDestination.SEARCH,
)

private val SECONDARY_DESTINATIONS = listOf(
    EdDestination.ADDONS,
    EdDestination.SETTINGS,
)

@Composable
fun SideRail(
    current: EdDestination,
    onSelect: (EdDestination) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    firstFocusRequester: FocusRequester? = null,
) {
    val palette = Ed.palette

    BoxWithConstraints(modifier.fillMaxHeight()) {
        // Telas de 720p caem para ~540dp de altura: nesse caso a doca entra em
        // modo compacto e os rótulos dão lugar ao ícone sozinho.
        val lowHeight = maxHeight < 560.dp
        val slim = compact || lowHeight
        val width = if (slim) 84.dp else 96.dp

        Box(Modifier.width(width).fillMaxHeight()) {
            // Véu de vidro: escurece a arte atrás da doca sem criar um painel.
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(width)
                    .background(
                        Brush.horizontalGradient(
                            0f to palette.bg.copy(alpha = 0.96f),
                            0.62f to palette.bg.copy(alpha = 0.82f),
                            1f to palette.bg.copy(alpha = 0.0f),
                        ),
                    ),
            )

            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(width)
                    .padding(vertical = if (slim) 10.dp else 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BrandMark(size = if (slim) 40.dp else 46.dp)

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PRIMARY_DESTINATIONS.forEach { destination ->
                        DockItem(
                            destination = destination,
                            selected = destination == current,
                            slim = slim,
                            showLabel = !lowHeight,
                            focusRequester = if (destination == EdDestination.HOME) firstFocusRequester else null,
                            onClick = { onSelect(destination) },
                        )
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SECONDARY_DESTINATIONS.forEach { destination ->
                        DockItem(
                            destination = destination,
                            selected = destination == current,
                            slim = true,
                            showLabel = false,
                            focusRequester = null,
                            onClick = { onSelect(destination) },
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}

/** Marca do app: quadrado de vidro com o glifo de reprodução em branco puro. */
@Composable
fun BrandMark(size: androidx.compose.ui.unit.Dp = 46.dp) {
    val palette = Ed.palette
    val shape = RoundedCornerShape(size / 4)
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(palette.glassStrong, shape)
            .border(1.dp, palette.glassBorderStrong, shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = EdIcons.play,
            contentDescription = null,
            tint = palette.onArt,
            modifier = Modifier.size(size * 0.46f),
        )
    }
}

@Composable
private fun DockItem(
    destination: EdDestination,
    selected: Boolean,
    slim: Boolean,
    showLabel: Boolean,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    val palette = Ed.palette
    val type = Ed.type
    val interaction = edInteraction(onClick = onClick, focusRequester = focusRequester)
    val focused = interaction.isFocused

    val tileShape = RoundedCornerShape(14.dp)
    val fill by animateColorAsState(
        targetValue = when {
            focused -> palette.glassStrong
            selected -> palette.glass
            else -> Color.Transparent
        },
        animationSpec = tween(160, easing = EdEasing),
        label = "dock-fill",
    )
    val iconColor by animateColorAsState(
        targetValue = when {
            focused || selected -> palette.onArt
            else -> palette.fg3
        },
        animationSpec = tween(160, easing = EdEasing),
        label = "dock-icon",
    )
    val labelColor by animateColorAsState(
        targetValue = when {
            focused || selected -> palette.onArt
            else -> palette.fg3
        },
        animationSpec = tween(160, easing = EdEasing),
        label = "dock-label",
    )

    val label = stringResource(destination.labelRes)

    Column(
        modifier = Modifier
            .width(if (slim) 72.dp else 84.dp)
            .clip(tileShape)
            .background(fill, tileShape)
            .then(if (focused) Modifier.border(2.dp, palette.onArt, tileShape) else Modifier)
            .then(interaction.modifier)
            .padding(vertical = if (showLabel) 7.dp else 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            imageVector = destination.icon,
            contentDescription = label,
            tint = iconColor,
            modifier = Modifier.size(if (slim) 20.dp else 23.dp),
        )
        if (showLabel) {
            Text(
                text = label.uppercase(),
                style = type.label.copy(fontSize = 8.5.sp, letterSpacing = 0.7.sp),
                color = labelColor,
                maxLines = 1,
            )
        }
        if (selected) {
            Box(
                Modifier
                    .size(4.dp)
                    .background(palette.onArt, RoundedCornerShape(percent = 50)),
            )
        } else {
            Spacer(Modifier.height(4.dp))
        }
    }
}

/** Espaçador vertical usado entre seções (compat). */
@Composable
fun RailSpacer() = Spacer(Modifier.height(8.dp))

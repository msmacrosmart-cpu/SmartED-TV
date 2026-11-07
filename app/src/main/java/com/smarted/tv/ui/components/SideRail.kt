package com.smarted.tv.ui.components

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.smarted.tv.R
import com.smarted.tv.ui.theme.Ed
import com.smarted.tv.ui.theme.EdEasing

/**
 * Barra lateral de navegação — réplica fiel da referência:
 *  - 72dp de largura (56dp em telas curtas),
 *  - item ativo: pílula `surface-2` + barra de acento à esquerda,
 *  - foco D-pad: preenchimento `surface-3` + anel de acento de 3dp.
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

@Composable
fun SideRail(
    current: EdDestination,
    onSelect: (EdDestination) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    firstFocusRequester: FocusRequester? = null,
) {
    val palette = Ed.palette

    Column(
        modifier = modifier
            .width(if (compact) 60.dp else 76.dp)
            .fillMaxHeight()
            .background(palette.bg)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        EdDestination.entries.forEach { dest ->
            RailItem(
                destination = dest,
                selected = dest == current,
                compact = compact,
                focusRequester = if (dest == EdDestination.HOME) firstFocusRequester else null,
                onClick = { onSelect(dest) },
            )
        }
    }
}

@Composable
private fun RailItem(
    destination: EdDestination,
    selected: Boolean,
    compact: Boolean,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    val palette = Ed.palette
    val interaction = edInteraction(onClick = onClick, focusRequester = focusRequester)
    val focused = interaction.isFocused

    val background by animateColorAsState(
        when {
            focused -> palette.surface3
            selected -> palette.surface2
            else -> Color.Transparent
        },
        tween(160, easing = EdEasing),
        label = "rail-bg",
    )
    val iconColor by animateColorAsState(
        when {
            focused -> palette.fg
            selected -> palette.accent
            else -> palette.fg2
        },
        tween(160, easing = EdEasing),
        label = "rail-icon",
    )
    val indicatorHeight by animateDpAsState(
        if (selected) 24.dp else 0.dp,
        tween(180, easing = EdEasing),
        label = "rail-indicator",
    )

    val shape = RoundedCornerShape(14.dp)
    val itemWidth = if (compact) 50.dp else 56.dp
    val itemHeight = if (compact) 42.dp else 46.dp

    Box(
        modifier = Modifier.width(itemWidth).height(itemHeight + 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (indicatorHeight > 0.dp) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .width(3.dp)
                    .height(indicatorHeight)
                    .background(palette.accent, RoundedCornerShape(2.dp)),
            )
        }
        Box(
            modifier = Modifier
                .width(itemWidth - 6.dp)
                .height(itemHeight)
                .background(background, shape)
                .then(
                    if (focused) Modifier.border(3.dp, palette.accent, shape) else Modifier,
                )
                .then(interaction.modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = destination.icon,
                contentDescription = stringResource(destination.labelRes),
                tint = iconColor,
                modifier = Modifier.size(if (compact) 21.dp else 23.dp),
            )
        }
    }
}

/** Espaçador vertical usado entre seções da rail (compat). */
@Composable
fun RailSpacer() = Spacer(Modifier.height(8.dp))

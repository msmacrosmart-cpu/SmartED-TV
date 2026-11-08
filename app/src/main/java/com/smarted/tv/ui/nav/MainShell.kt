package com.smarted.tv.ui.nav

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.smarted.tv.core.model.DeviceMode
import com.smarted.tv.core.model.PlaybackRequest
import com.smarted.tv.core.model.VodItem
import com.smarted.tv.core.model.VodKind
import com.smarted.tv.ui.channels.ChannelsScreen
import com.smarted.tv.ui.components.GlassBackdrop
import com.smarted.tv.ui.components.GlassClickable
import com.smarted.tv.ui.components.glass
import com.smarted.tv.ui.home.HomeScreen
import com.smarted.tv.ui.player.PlayerScreen
import com.smarted.tv.ui.repository.RepositoryTreeScreen
import com.smarted.tv.ui.settings.SettingsScreen
import com.smarted.tv.ui.theme.LocalDimens
import com.smarted.tv.ui.theme.Smoke
import com.smarted.tv.ui.vod.VodScreen

@Composable
fun MainShell(mode: DeviceMode, onChangeMode: () -> Unit) {
    val d = LocalDimens.current
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route?.substringBefore('?') ?: Dest.HOME.route
    val current = Dest.entries.firstOrNull { it.route == route } ?: Dest.HOME
    var playback by remember { mutableStateOf<PlaybackRequest?>(null) }

    fun go(dest: Dest) {
        nav.navigate(dest.route) {
            popUpTo(Dest.HOME.route)
            launchSingleTop = true
        }
    }
    fun openTitle(item: VodItem) {
        val base = if (item.kind == VodKind.MOVIE) Dest.MOVIES.route else Dest.SERIES.route
        nav.navigate("$base?open=${item.id}") {
            popUpTo(Dest.HOME.route)
            launchSingleTop = true
        }
    }

    BackHandler(enabled = current != Dest.HOME && playback == null) { go(Dest.HOME) }

    Box(Modifier.fillMaxSize()) {
        GlassBackdrop {
            if (mode == DeviceMode.TV) {
                Row(Modifier.fillMaxSize()) {
                    SideRail(
                        current, ::go,
                        Modifier.padding(start = 20.dp, top = d.screenV, bottom = d.screenV),
                    )
                    AppNavHost(nav, onPlay = { playback = it }, ::openTitle, ::go, mode, onChangeMode,
                        Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize().systemBarsPadding()) {
                    AppNavHost(nav, onPlay = { playback = it }, ::openTitle, ::go, mode, onChangeMode,
                        Modifier.weight(1f).fillMaxWidth())
                    BottomBar(current, ::go)
                }
            }
        }
        playback?.let { PlayerScreen(it, onClose = { playback = null }) }
    }
}

@Composable
private fun AppNavHost(
    nav: NavHostController,
    onPlay: (PlaybackRequest) -> Unit,
    onOpenTitle: (VodItem) -> Unit,
    onNavigate: (Dest) -> Unit,
    mode: DeviceMode,
    onChangeMode: () -> Unit,
    modifier: Modifier,
) {
    val openArg = listOf(navArgument("open") { type = NavType.StringType; nullable = true; defaultValue = null })
    NavHost(
        nav, startDestination = Dest.HOME.route, modifier = modifier,
        enterTransition = { fadeIn(tween(180)) }, exitTransition = { fadeOut(tween(120)) },
        popEnterTransition = { fadeIn(tween(180)) }, popExitTransition = { fadeOut(tween(120)) },
    ) {
        composable(Dest.HOME.route) { HomeScreen(onNavigate, onOpenTitle, onPlay) }
        composable(Dest.CHANNELS.route) { ChannelsScreen(onPlay) }
        composable("${Dest.MOVIES.route}?open={open}", arguments = openArg) { e ->
            VodScreen(VodKind.MOVIE, e.arguments?.getString("open"), onPlay)
        }
        composable("${Dest.SERIES.route}?open={open}", arguments = openArg) { e ->
            VodScreen(VodKind.SERIES, e.arguments?.getString("open"), onPlay)
        }
        composable(Dest.SETTINGS.route) { SettingsScreen(mode, onChangeMode) }
        composable(Dest.REPOSITORY.route) { RepositoryTreeScreen(onPlay = onPlay) }
    }
}

/**
 * TV: rail com as opções da barra do celular — apenas Início, Canais, Filmes, Séries
 * e Configurações, em UMA coluna centralizada e proporcional (borda whiteFocus no
 * item atual/focado vem do GlassClickable).
 */
@Composable
private fun SideRail(current: Dest, onNavigate: (Dest) -> Unit, modifier: Modifier = Modifier) {
    val d = LocalDimens.current
    Box(modifier.fillMaxHeight().width(d.railWidth).glass().padding(vertical = 10.dp)) {
        Column(
            Modifier.align(Alignment.Center).focusGroup(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RailItem(Dest.HOME, current == Dest.HOME) { onNavigate(Dest.HOME) }
            RailItem(Dest.CHANNELS, current == Dest.CHANNELS) { onNavigate(Dest.CHANNELS) }
            RailItem(Dest.MOVIES, current == Dest.MOVIES) { onNavigate(Dest.MOVIES) }
            RailItem(Dest.SERIES, current == Dest.SERIES) { onNavigate(Dest.SERIES) }
            RailItem(Dest.SETTINGS, current == Dest.SETTINGS) { onNavigate(Dest.SETTINGS) }
        }
    }
}

@Composable
private fun RailItem(dest: Dest, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val d = LocalDimens.current
    GlassClickable(
        onClick = onClick, selected = selected, modifier = modifier.size(d.railItem),
    ) { focused ->
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(dest.icon, dest.label, Modifier.size(22.dp), tint = Smoke.TextPrimary)
            Text(
                dest.label, fontSize = d.textCaption, maxLines = 1,
                fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected || focused) Smoke.TextPrimary else Smoke.TextSecondary,
            )
        }
    }
}

/** Celular/Tablet: barra inferior em vidro fumê, toque direto. */
@Composable
private fun BottomBar(current: Dest, onNavigate: (Dest) -> Unit) {
    val d = LocalDimens.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp).glass().padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Dest.entries.forEach { dest ->
            val sel = dest == current
            GlassClickable(
                onClick = { onNavigate(dest) }, selected = sel, flat = true,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
            ) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(dest.icon, dest.label, Modifier.size(22.dp), tint = if (sel) Smoke.TextPrimary else Smoke.TextSecondary)
                    Text(dest.label, fontSize = d.textCaption, maxLines = 1,
                        color = if (sel) Smoke.TextPrimary else Smoke.TextSecondary)
                }
            }
        }
    }
}

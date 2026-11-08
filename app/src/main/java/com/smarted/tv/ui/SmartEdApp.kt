package com.smarted.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Text
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.smarted.tv.AppGraph
import com.smarted.tv.core.model.Tab
import com.smarted.tv.ui.components.ClockHeader
import com.smarted.tv.ui.components.SidebarItem
import com.smarted.tv.ui.screens.ChannelsScreen
import com.smarted.tv.ui.screens.HomeScreen
import com.smarted.tv.ui.screens.PlayerScreen
import com.smarted.tv.ui.screens.PlayRequest
import com.smarted.tv.ui.screens.SettingsScreen
import com.smarted.tv.ui.screens.VodScreen
import com.smarted.tv.ui.state.AppViewModel
import com.smarted.tv.ui.state.ChannelsViewModel
import com.smarted.tv.ui.state.VodViewModel
import com.smarted.tv.ui.theme.SmartEdTheme
import com.smarted.tv.ui.theme.TvColors

private enum class Destination { HOME, CHANNELS, MOVIES, SERIES, SETTINGS }

/**
 * Casca do app: rail lateral persistente (Início, Canais, Filmes, Séries … Configurações
 * perto da borda inferior), relógio/data no topo e fundo fumê com brilho radial.
 */
@Composable
fun SmartEdApp(graph: AppGraph) {
    val appFactory = remember(graph) { viewModelFactory { initializer { AppViewModel(graph) } } }
    val app: AppViewModel = viewModel(factory = appFactory)
    val ui by app.ui.collectAsState()
    var destination by rememberSaveable { mutableStateOf(Destination.HOME) }
    var request by remember { mutableStateOf<PlayRequest?>(null) }
    val railFocus = remember { FocusRequester() }
    var railRequestFocus by remember { mutableStateOf(false) }

    BoxWithConstraints(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF141519), TvColors.Background), radius = 1600f))) {
        val compact = maxHeight < 600.dp || maxWidth < 1000.dp
        SmartEdTheme(compact = compact) {
            Row(Modifier.fillMaxSize().padding(start = if (compact) 20.dp else 32.dp, end = if (compact) 28.dp else 48.dp, top = if (compact) 18.dp else 30.dp, bottom = if (compact) 16.dp else 26.dp)) {
                // rail lateral — Início/Canais/Filmes/Séries centralizados, Configurações no fim.
                Column(
                    Modifier.fillMaxHeight().width(if (compact) 92.dp else 104.dp).padding(top = 44.dp),
                    verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp, Alignment.CenterVertically),
                ) {
                    SidebarItem("Início", Icons.Default.Home, destination == Destination.HOME, { destination = Destination.HOME }, focusRequester = railFocus)
                    SidebarItem("Canais", Icons.Default.LiveTv, destination == Destination.CHANNELS, { destination = Destination.CHANNELS })
                    SidebarItem("Filmes", Icons.Default.Movie, destination == Destination.MOVIES, { destination = Destination.MOVIES })
                    SidebarItem("Séries", Icons.Default.Tv, destination == Destination.SERIES, { destination = Destination.SERIES })
                    Spacer(Modifier.height(if (compact) 18.dp else 34.dp))
                    SidebarItem("Configurações", Icons.Default.Settings, destination == Destination.SETTINGS, { destination = Destination.SETTINGS })
                }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
                    com.smarted.tv.ui.components.HeaderTitleBlock(
                        title = "SmartED TV",
                        subtitle = when (destination) {
                            Destination.HOME -> "Home"
                            Destination.CHANNELS -> "Canais ao vivo"
                            Destination.MOVIES -> "Filmes"
                            Destination.SERIES -> "Séries"
                            Destination.SETTINGS -> "Configurações"
                        },
                    )
                    if (!ui.ready) {
                        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, content = {
                            Text("SmartED TV", color = Color.White, style = androidx.compose.material3.MaterialTheme.typography.headlineLarge)
                            Text(
                                if (ui.startupError.isBlank()) "Abrindo seus repositórios locais…" else ui.startupError,
                                color = TvColors.Muted, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                            )
                            if (ui.startupError.isNotBlank()) com.smarted.tv.ui.components.TvAction("Tentar novamente", app::initialize)
                        })
                    } else when (destination) {
                        Destination.HOME -> HomeScreen(
                            app = app,
                            onOpenChannels = { destination = Destination.CHANNELS },
                            onOpenVod = { tab -> destination = if (tab == Tab.MOVIES) Destination.MOVIES else Destination.SERIES },
                            onOpenSettings = { destination = Destination.SETTINGS },
                            onPlay = { request = it },
                        )
                        Destination.CHANNELS -> ChannelsScreen(
                            app = app,
                            onPlay = { request = it },
                            onOpenSettings = { destination = Destination.SETTINGS },
                            previewActive = request == null,
                        )
                        Destination.MOVIES, Destination.SERIES -> {
                            val tab = if (destination == Destination.MOVIES) Tab.MOVIES else Tab.SERIES
                            VodScreen(
                                tab = tab,
                                app = app,
                                revision = ui.revision,
                                onPlay = { request = it },
                            )
                        }
                        Destination.SETTINGS -> SettingsScreen(app)
                    }
                }
            }
            request?.let { playing ->
                Box(Modifier.fillMaxSize().testTag("fullscreen-player")) {
                    PlayerScreen(playing, app, onDismiss = { request = null }, onQueue = { request = it })
                }
            }
        }
    }

    LaunchedEffect(railRequestFocus) { if (railRequestFocus) { runCatching { railFocus.requestFocus() }; railRequestFocus = false } }
    BackHandler(enabled = request == null && destination != Destination.HOME) { destination = Destination.HOME }
}

/** Fábricas por aba para manter estado de navegação independente (Filmes ≠ Séries). */
@Composable
fun rememberVodModel(graph: AppGraph, tab: Tab): VodViewModel {
    val factory = remember(graph, tab) { viewModelFactory { initializer { VodViewModel(graph, tab) } } }
    return viewModel(key = "vod-${tab.name}", factory = factory)
}

@Composable
fun rememberChannelsModel(graph: AppGraph): ChannelsViewModel {
    val factory = remember(graph) { viewModelFactory { initializer { ChannelsViewModel(graph) } } }
    return viewModel(key = "channels", factory = factory)
}

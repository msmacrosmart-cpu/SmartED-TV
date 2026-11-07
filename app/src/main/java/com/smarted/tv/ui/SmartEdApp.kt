package com.smarted.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.smarted.tv.AppGraph
import com.smarted.tv.core.model.Scope
import com.smarted.tv.ui.components.*
import com.smarted.tv.ui.screens.*
import com.smarted.tv.ui.state.AppViewModel
import com.smarted.tv.ui.state.CatalogViewModel
import com.smarted.tv.ui.theme.SmartEdTheme
import com.smarted.tv.ui.theme.TvColors

private enum class Destination { HOME, CHANNELS, MOVIES, SERIES, SETTINGS }

@Composable
fun SmartEdApp(graph: AppGraph) {
    val factory = remember(graph) { viewModelFactory { initializer { AppViewModel(graph) } } }
    val vm: AppViewModel = viewModel(factory = factory)
    val ready by vm.ready.collectAsState()
    val startupError by vm.startupError.collectAsState()
    var destination by rememberSaveable { mutableStateOf(Destination.HOME) }
    var request by remember { mutableStateOf<PlayRequest?>(null) }
    BoxWithConstraints(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF242529), TvColors.Background), radius = 1500f))) {
        SmartEdTheme(compact = maxHeight < 600.dp || maxWidth < 1000.dp) {
            Box(Modifier.fillMaxSize().padding(horizontal = if (maxHeight < 600.dp) 28.dp else 48.dp, vertical = if (maxHeight < 600.dp) 22.dp else 36.dp)) {
                if (!ready) Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("SmartED TV", color = Color.White, style = MaterialTheme.typography.headlineLarge)
                    Text(if (startupError.isBlank()) "Preparando seus addons…" else startupError, color = TvColors.Muted)
                    if (startupError.isNotBlank()) TvAction("Tentar novamente", vm::initialize)
                } else when (destination) {
                    Destination.HOME -> HomeScreen({ destination = when (it) { Scope.CHANNELS -> Destination.CHANNELS; Scope.MOVIES -> Destination.MOVIES; Scope.SERIES -> Destination.SERIES } }, { destination = Destination.SETTINGS })
                    Destination.SETTINGS -> SettingsScreen(vm) { destination = Destination.HOME }
                    else -> {
                        val scope = when (destination) { Destination.CHANNELS -> Scope.CHANNELS; Destination.MOVIES -> Scope.MOVIES; else -> Scope.SERIES }
                        val catalogFactory = remember(graph, scope) { viewModelFactory { initializer { CatalogViewModel(scope, graph) } } }
                        val catalog: CatalogViewModel = viewModel(key = "catalog-${scope.name}", factory = catalogFactory)
                        val settings by vm.settings.collectAsState()
                        val play: (com.smarted.tv.core.model.MediaItem, com.smarted.tv.core.model.Video?) -> Unit = { item, video -> request = PlayRequest(item, video) }
                        if (scope == Scope.CHANNELS) ChannelsScreen(catalog, settings.revision, { destination = Destination.HOME }, { destination = Destination.SETTINGS }, play, previewEnabled = request == null)
                        else CatalogScreen(catalog, settings.revision, { destination = Destination.HOME }, { destination = Destination.SETTINGS }, play)
                    }
                }
            }
            request?.let { playing ->
                Box(Modifier.fillMaxSize().testTag("fullscreen-player")) {
                    PlayerScreen(playing, vm, { request = null }, { request = it })
                }
            }
        }
    }
    BackHandler(enabled = request == null && destination != Destination.HOME) { destination = Destination.HOME }
}

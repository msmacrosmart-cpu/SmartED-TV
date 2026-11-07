package com.smarted.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smarted.tv.AppGraph
import com.smarted.tv.core.model.MediaItem
import com.smarted.tv.core.model.MediaKind
import com.smarted.tv.ui.components.EdDestination
import com.smarted.tv.ui.components.SideRail
import com.smarted.tv.ui.screens.AddonsScreen
import com.smarted.tv.ui.screens.CatalogScreen
import com.smarted.tv.ui.screens.DetailsScreen
import com.smarted.tv.ui.screens.EpgScreen
import com.smarted.tv.ui.screens.GuideScreen
import com.smarted.tv.ui.screens.LibraryMode
import com.smarted.tv.ui.screens.LibraryScreen
import com.smarted.tv.ui.screens.HomeScreen
import com.smarted.tv.ui.screens.PlayerScreen
import com.smarted.tv.ui.screens.RepositoryScreen
import com.smarted.tv.ui.screens.SearchScreen
import com.smarted.tv.ui.screens.SettingsScreen
import com.smarted.tv.ui.screens.SourcesScreen
import com.smarted.tv.ui.screens.YouTubePlayer
import com.smarted.tv.ui.state.AddonsViewModel
import com.smarted.tv.ui.state.CatalogViewModel
import com.smarted.tv.ui.state.DetailsViewModel
import com.smarted.tv.ui.state.GuideViewModel
import com.smarted.tv.ui.state.HomeViewModel
import com.smarted.tv.ui.state.PlayerViewModel
import com.smarted.tv.ui.state.SearchViewModel
import com.smarted.tv.ui.state.SettingsViewModel
import com.smarted.tv.ui.state.SmartEdViewModelFactory
import com.smarted.tv.ui.theme.Ed
import com.smarted.tv.ui.theme.EdDimens
import com.smarted.tv.ui.theme.LocalEdDimens
import com.smarted.tv.ui.theme.SmartEdTheme

val LocalAppGraph = staticCompositionLocalOf<AppGraph> { error("AppGraph não disponibilizado") }

/**
 * Raiz do app: aplica o tema, o overscan (bordas seguras de TV) e a navegação.
 *
 * A navegação é feita com estado (sem Navigation-Compose no caminho crítico)
 * porque cada transição é instantânea em TV box e o botão VOLTAR tem semântica
 * própria por tela.
 */
@Composable
fun SmartEdRoot(graph: AppGraph) {
    val settings by graph.settings.state.collectAsStateWithLifecycle()

    SmartEdTheme(
        darkTheme = settings.themeDark,
        accentId = settings.accentId,
        fontScale = settings.fontScale,
    ) {
        // Overscan: 0% → 0dp, 3% → ~24dp (bordas seguras para TVs com overscan)
        val overscan = (settings.overscanPercent * 8f).dp

        CompositionLocalProvider(
            LocalAppGraph provides graph,
            LocalEdDimens provides EdDimens(overscan = overscan),
        ) {
            var showSplash by remember { mutableStateOf(true) }
            val bootMessage by graph.bootMessage.collectAsStateWithLifecycle()

            LaunchedEffect(Unit) {
                // o splash fica no mínimo 900ms para a animação respirar
                kotlinx.coroutines.delay(900L)
                showSplash = false
            }

            if (showSplash) {
                com.smarted.tv.ui.screens.SplashScreen(message = bootMessage)
            } else {
                AppScaffold(graph = graph, overscan = overscan)
            }
        }
    }
}

@Composable
private fun AppScaffold(graph: AppGraph, overscan: androidx.compose.ui.unit.Dp) {
    val palette = Ed.palette
    val factory = remember(graph) { SmartEdViewModelFactory(graph) }
    val context = androidx.compose.ui.platform.LocalContext.current

    var destination by rememberSaveable { mutableStateOf(EdDestination.HOME.name) }
    var detailsItem by remember { mutableStateOf<MediaItem?>(null) }
    var playerItem by remember { mutableStateOf<MediaItem?>(null) }
    var trailerId by remember { mutableStateOf<String?>(null) }
    var subScreen by remember { mutableStateOf<String?>(null) }

    val current = EdDestination.entries.firstOrNull { it.name == destination } ?: EdDestination.HOME
    val navFocus = remember { FocusRequester() }
    val heroFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    val catalogFocus = remember { FocusRequester() }

    // Consome pedidos de reprodução vindos de qualquer tela
    LaunchedEffect(Unit) {
        while (true) {
            graph.playerRequest?.let { item ->
                playerItem = item
                graph.playerRequest = null
            }
            graph.trailerRequest?.let { id ->
                trailerId = id
                graph.trailerRequest = null
            }
            kotlinx.coroutines.delay(150L)
        }
    }

    val onBack: () -> Unit = {
        when {
            trailerId != null -> trailerId = null
            playerItem != null -> playerItem = null
            detailsItem != null -> detailsItem = null
            subScreen != null -> subScreen = null
            current != EdDestination.HOME -> destination = EdDestination.HOME.name
            else -> Unit
        }
    }

    BackHandler(enabled = true, onBack = onBack)

    // atalhos numéricos 1..8 para a rail (comportamento de TV)
    Box(
        Modifier
            .fillMaxSize()
            .background(palette.bg)
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action != android.view.KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.One -> { destination = EdDestination.HOME.name; true }
                    Key.Two -> { destination = EdDestination.LIVE.name; true }
                    Key.Three -> { destination = EdDestination.MOVIES.name; true }
                    Key.Four -> { destination = EdDestination.SERIES.name; true }
                    Key.Five -> { destination = EdDestination.GUIDE.name; true }
                    Key.Six -> { destination = EdDestination.SEARCH.name; true }
                    Key.Seven -> { destination = EdDestination.ADDONS.name; true }
                    Key.Eight -> { destination = EdDestination.SETTINGS.name; true }
                    else -> false
                }
            },
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(
                    start = overscan,
                    end = overscan,
                    top = overscan,
                    bottom = overscan,
                ),
        ) {
            SideRail(
                current = current,
                onSelect = {
                    destination = it.name
                    detailsItem = null
                    subScreen = null
                },
                firstFocusRequester = navFocus,
            )

            Box(Modifier.weight(1f).fillMaxSize()) {
                AnimatedContent(
                    targetState = Triple(current, subScreen, detailsItem?.ref?.key),
                    transitionSpec = {
                        fadeIn(tween(160)) togetherWith fadeOut(tween(120))
                    },
                    label = "screen",
                ) { key ->
                    val screen = key.first
                    val sub = key.second
                    val detailKey = key.third

                    when {
                        detailKey != null && detailsItem != null -> {
                            val detailsVm: DetailsViewModel = viewModel(factory = factory)
                            DetailsScreen(
                                viewModel = detailsVm,
                                item = detailsItem!!,
                                onBack = { detailsItem = null },
                            )
                        }

                        sub == "continue" || sub == "favorites" -> {
                            val vm: HomeViewModel = viewModel(factory = factory)
                            LibraryScreen(
                                viewModel = vm,
                                mode = if (sub == "continue") LibraryMode.CONTINUE else LibraryMode.FAVORITES,
                                onPlay = { playerItem = it },
                                onOpen = { detailsItem = it },
                            )
                        }

                        sub != null -> when (sub) {
                            "repository" -> {
                                val settingsVm: SettingsViewModel = viewModel(factory = factory)
                                val addonsVm: AddonsViewModel = viewModel(factory = factory)
                                RepositoryScreen(
                                    settingsViewModel = settingsVm,
                                    addonsViewModel = addonsVm,
                                    onBack = { subScreen = null },
                                )
                            }

                            "addons" -> {
                                val addonsVm: AddonsViewModel = viewModel(factory = factory)
                                AddonsScreen(
                                    viewModel = addonsVm,
                                    onOpenRepository = { subScreen = "repository" },
                                    onBack = { subScreen = null },
                                )
                            }

                            "sources" -> {
                                val settingsVm: SettingsViewModel = viewModel(factory = factory)
                                SourcesScreen(viewModel = settingsVm, onBack = { subScreen = null })
                            }

                            "epg" -> {
                                val settingsVm: SettingsViewModel = viewModel(factory = factory)
                                EpgScreen(viewModel = settingsVm, onBack = { subScreen = null })
                            }

                            else -> HomePlaceholder()
                        }

                        screen == EdDestination.HOME -> {
                            val vm: HomeViewModel = viewModel(factory = factory)
                            HomeScreen(
                                viewModel = vm,
                                onPlay = { playerItem = it },
                                onOpenDetails = { detailsItem = it },
                                onOpenChannel = { playerItem = it },
                                onOpenDestination = { dest ->
                                    when (dest) {
                                        "live" -> destination = EdDestination.LIVE.name
                                        "movies" -> destination = EdDestination.MOVIES.name
                                        "series" -> destination = EdDestination.SERIES.name
                                        "continue", "favorites" -> subScreen = dest
                                        else -> Unit
                                    }
                                },
                                heroFocusRequester = heroFocus,
                            )
                        }

                        screen == EdDestination.LIVE || screen == EdDestination.MOVIES || screen == EdDestination.SERIES -> {
                            val vm: CatalogViewModel = viewModel(factory = factory)
                            val kind = when (screen) {
                                EdDestination.LIVE -> MediaKind.LIVE
                                EdDestination.MOVIES -> MediaKind.MOVIE
                                else -> MediaKind.SERIES
                            }
                            CatalogScreen(
                                viewModel = vm,
                                kind = kind,
                                title = when (kind) {
                                    MediaKind.LIVE -> "Canais"
                                    MediaKind.MOVIE -> "Filmes"
                                    else -> "Séries"
                                },
                                subtitle = when (kind) {
                                    MediaKind.LIVE -> "canais"
                                    MediaKind.MOVIE -> "filmes"
                                    else -> "séries"
                                },
                                searchPlaceholder = when (kind) {
                                    MediaKind.LIVE -> "Buscar canais"
                                    MediaKind.MOVIE -> "Buscar filmes"
                                    else -> "Buscar séries"
                                },
                                onOpen = { item ->
                                    if (item.kind == MediaKind.LIVE) playerItem = item else detailsItem = item
                                },
                                onPlay = { playerItem = it },
                                firstFocusRequester = catalogFocus,
                            )
                        }

                        screen == EdDestination.GUIDE -> {
                            val vm: GuideViewModel = viewModel(factory = factory)
                            GuideScreen(viewModel = vm, onPlayChannel = { playerItem = it })
                        }

                        screen == EdDestination.SEARCH -> {
                            val vm: SearchViewModel = viewModel(factory = factory)
                            SearchScreen(
                                viewModel = vm,
                                onOpen = { detailsItem = it },
                                onPlay = { playerItem = it },
                                searchFocusRequester = searchFocus,
                            )
                        }

                        screen == EdDestination.ADDONS -> {
                            val vm: AddonsViewModel = viewModel(factory = factory)
                            AddonsScreen(
                                viewModel = vm,
                                onOpenRepository = { subScreen = "repository" },
                                onBack = { destination = EdDestination.SETTINGS.name },
                            )
                        }

                        screen == EdDestination.SETTINGS -> {
                            val vm: SettingsViewModel = viewModel(factory = factory)
                            SettingsScreen(
                                viewModel = vm,
                                onOpenAddons = { subScreen = "addons" },
                                onOpenRepository = { subScreen = "repository" },
                                onOpenSources = { subScreen = "sources" },
                                onOpenEpg = { subScreen = "epg" },
                                onOpenTmdbSite = { openUrl(context, "https://www.themoviedb.org/settings/api") },
                            )
                        }

                        else -> HomePlaceholder()
                    }
                }

                // Player por cima de tudo (imersivo)
                playerItem?.let { item ->
                    val vm: PlayerViewModel = viewModel(factory = factory, key = "player-${item.ref.key}")
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        PlayerScreen(
                            viewModel = vm,
                            item = item,
                            onBack = { playerItem = null },
                        )
                    }
                }

                // Trailer do YouTube por cima de tudo
                trailerId?.let { id ->
                    YouTubePlayer(
                        videoId = id,
                        onClose = { trailerId = null },
                        noAds = true,
                    )
                }
            }
        }
    }
}

/** Abre uma URL externa com segurança (usada por atalhos de configuração). */
private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

@Composable
private fun HomePlaceholder() {
    val palette = Ed.palette
    Box(Modifier.fillMaxSize().background(palette.bg))
}

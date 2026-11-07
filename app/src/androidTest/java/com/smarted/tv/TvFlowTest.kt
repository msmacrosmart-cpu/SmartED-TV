package com.smarted.tv

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.getOrNull
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smarted.tv.core.data.InMemoryState
import com.smarted.tv.core.model.*
import com.smarted.tv.core.protocol.StremioApi
import com.smarted.tv.ui.SmartEdApp
import com.smarted.tv.ui.theme.FocusRingColorKey
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class TvFlowTest {
    @get:Rule val compose = createComposeRule()
    private val base = "https://fixture.example/manifest.json"
    private val manifest = Manifest("fixture", "Acervo de teste", "1.0",
        resources = listOf(ResourceSpec("catalog"), ResourceSpec("meta"), ResourceSpec("stream")),
        catalogs = listOf(CatalogSpec("movie", "filmes", "Filmes", listOf(ExtraSpec("skip"), ExtraSpec("search")), listOf("Ação", "Drama")),
            CatalogSpec("series", "series", "Séries", listOf(ExtraSpec("skip"), ExtraSpec("search")), listOf("Drama")),
            CatalogSpec("tv", "canais", "Canais", listOf(ExtraSpec("skip"), ExtraSpec("search")), listOf("Notícias"))))
    private val addon = InstalledAddon(base, Scope.MOVIES, manifest, 1L)
    private val movie = MediaItem(base, "m1", "movie", Scope.MOVIES, "Horizonte de teste", description = "Uma história criada para validar a interface.", genres = listOf("Ação"), releaseInfo = "2024", rating = 8.2)
    private val series = MediaItem(base, "s1", "series", Scope.SERIES, "Órbita de teste", description = "Duas temporadas para validar a navegação.", genres = listOf("Drama"), videos = listOf(
        Video("s1:1:1", "Começo", 1, 1), Video("s1:2:1", "Retorno", 2, 1), Video("s1:2:2", "Destino", 2, 2)))
    private val channel = MediaItem(base, "c1", "tv", Scope.CHANNELS, "Canalle de teste", genres = listOf("Notícias"), videos = listOf(
        Video("p1", "Agora no teste", startTime = "2020-01-01T00:00:00Z", endTime = "2099-01-01T00:00:00Z")))

    private fun graph(scopes: List<Scope> = listOf(Scope.MOVIES, Scope.SERIES, Scope.CHANNELS)): AppGraph {
        val api = object : StremioApi {
            override suspend fun manifest(url: String) = manifest
            override suspend fun catalog(addon: InstalledAddon, catalog: CatalogSpec, extra: Map<String, String>): List<MediaItem> =
                when (addon.scope) { Scope.MOVIES -> listOf(movie); Scope.SERIES -> listOf(series); Scope.CHANNELS -> listOf(channel) }
            override suspend fun meta(item: MediaItem) = item
            override suspend fun streams(item: MediaItem, videoId: String) = emptyList<MediaStream>()
        }
        return AppGraph(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext, InMemoryState(AppData(scopes.map { addon.copy(scope = it) })), api)
    }

    @Test fun homeHasClockAndDpadWhiteFocusAndSettingsNearBottom() {
        compose.setContent { SmartEdApp(graph()) }
        compose.onNodeWithTag("home").assertIsDisplayed()
        compose.onNodeWithText("Configurações").assertIsDisplayed()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("nav-Canais") and isFocused()).fetchSemanticsNodes().isNotEmpty() }
        val channels = compose.onNodeWithTag("nav-Canais").fetchSemanticsNode()
        val settings = compose.onNodeWithTag("settings").fetchSemanticsNode()
        assertTrue("Settings must be below navigation", settings.boundsInRoot.top > channels.boundsInRoot.bottom)
        assertEquals(Color.White, channels.config.getOrNull(FocusRingColorKey))
        compose.onNodeWithTag("nav-Canais").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("nav-Filmes").assertIsFocused()
        assertEquals(Color.White, compose.onNodeWithTag("nav-Filmes").fetchSemanticsNode().config.getOrNull(FocusRingColorKey))
        compose.onNodeWithTag("nav-Filmes").performKeyInput { pressKey(Key.Enter) }
        compose.onNodeWithTag("catalog-MOVIES").assertIsDisplayed()
        compose.onNodeWithTag("content-search").assertIsDisplayed()
        compose.onNodeWithText("Horizonte de teste").assertIsDisplayed()
        compose.onNodeWithText("Órbita de teste").assertDoesNotExist()
    }

    @Test fun movieSearchIsScopedAndDetailsReplaceSearch() {
        compose.setContent { SmartEdApp(graph()) }
        compose.onNodeWithTag("nav-Filmes").performClick()
        compose.onNodeWithTag("content-search").assertIsDisplayed().performTextInput("horizonte")
        compose.onNodeWithText("Horizonte de teste").performClick()
        compose.onNodeWithTag("detail-hero").assertIsDisplayed()
        compose.onNodeWithTag("content-search").assertDoesNotExist()
        compose.onNodeWithText("Uma história criada para validar a interface.").assertIsDisplayed()
        compose.onNodeWithText("Favoritar").performClick()
        compose.onNodeWithText("Favoritado").assertIsDisplayed()
        compose.onNodeWithText("Fechar detalhes").performClick()
        compose.onNodeWithTag("content-search").assertIsDisplayed()
    }

    @Test fun seriesSelectSeasonAndEpisode() {
        compose.setContent { SmartEdApp(graph()) }
        compose.onNodeWithTag("nav-Séries").performClick()
        compose.onNodeWithText("Órbita de teste").performClick()
        compose.onNodeWithTag("season-2").performClick()
        compose.onNodeWithTag("episode-s1:2:2").performClick()
        compose.onNodeWithText("T2 E2 · Destino").assertIsDisplayed()
        compose.onNodeWithTag("episode-s1:1:1").assertDoesNotExist()
    }

    @Test fun channelsHaveThreeDedicatedColumnsAndScopedSearch() {
        compose.setContent { SmartEdApp(graph(listOf(Scope.CHANNELS))) }
        compose.onNodeWithTag("nav-Canais").performClick()
        compose.onNodeWithTag("channels-categories").assertIsDisplayed()
        compose.onNodeWithTag("channels-list").assertIsDisplayed()
        compose.onNodeWithTag("channels-preview").assertIsDisplayed()
        compose.onNodeWithTag("channel-search").assertIsDisplayed().performTextInput("canalle")
        compose.onNodeWithText("Canalle de teste").assertIsDisplayed()
        compose.onNodeWithText("Horizonte de teste").assertDoesNotExist()
    }

    @Test fun settingsExposeThreeSlotsAndExplicitScope() {
        val empty = AppGraph(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext, InMemoryState(), object : StremioApi {
            override suspend fun manifest(url: String) = this@TvFlowTest.manifest
            override suspend fun catalog(addon: InstalledAddon, catalog: CatalogSpec, extra: Map<String, String>) = emptyList<MediaItem>()
            override suspend fun meta(item: MediaItem) = item
            override suspend fun streams(item: MediaItem, videoId: String) = emptyList<MediaStream>()
        })
        compose.setContent { SmartEdApp(empty) }
        compose.onNodeWithTag("settings").performClick()
        compose.onNodeWithText("0 / 3").assertIsDisplayed()
        compose.onNodeWithText("Adicionar meu primeiro addon").performClick()
        compose.onNodeWithTag("manifest-url").assertIsDisplayed().performTextInput("https://new.example/manifest.json")
        compose.onNodeWithTag("install-scope-SERIES").performClick()
        compose.onNodeWithTag("install-addon").performClick()
        compose.waitUntil(10_000) { runBlocking { empty.repository.data.value.addons.size } == 1 }
        assertEquals(Scope.SERIES, runBlocking { empty.repository.data.value.addons.single().scope })
        compose.onNodeWithText("1 / 3").assertIsDisplayed()
    }
}

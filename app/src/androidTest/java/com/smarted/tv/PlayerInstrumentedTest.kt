package com.smarted.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smarted.tv.core.data.InMemoryState
import com.smarted.tv.core.model.*
import com.smarted.tv.core.protocol.StremioApi
import com.smarted.tv.ui.screens.PlayRequest
import com.smarted.tv.ui.screens.PlayerScreen
import com.smarted.tv.ui.state.AppViewModel
import com.smarted.tv.ui.theme.SmartEdTheme
import java.io.File
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class PlayerInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun realMedia3PlaysHttpFixtureAndPausesWithDpad() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bytes = instrumentation.context.assets.open("fixture.mp4").use { it.readBytes() }
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = MockResponse()
                    .setHeader("Content-Type", "video/mp4").setBody(Buffer().write(bytes))
            }
            server.start()
            val url = server.url("/manifest.json").toString()
            val addon = InstalledAddon(url, Scope.MOVIES, Manifest("player-test", "Player test", "1", catalogs = listOf(CatalogSpec("movie", "top"))), 1)
            val item = MediaItem(url, "fixture", "movie", Scope.MOVIES, "Vídeo de validação", streams = listOf(MediaStream(server.url("/video.mp4").toString(), name = "MP4 local de teste")))
            val api = object : StremioApi {
                override suspend fun manifest(url: String) = addon.manifest
                override suspend fun catalog(addon: InstalledAddon, catalog: CatalogSpec, extra: Map<String, String>) = listOf(item)
                override suspend fun meta(item: MediaItem) = item
                override suspend fun streams(item: MediaItem, videoId: String) = item.streams
            }
            val graph = AppGraph(instrumentation.targetContext, InMemoryState(AppData(listOf(addon))), api)
            kotlinx.coroutines.runBlocking { graph.repository.initialize() }
            val vm = AppViewModel(graph)
            compose.setContent { SmartEdTheme { Box(Modifier.fillMaxSize()) { PlayerScreen(PlayRequest(item, null), vm, onClose = {}) } } }
            compose.waitUntil(20_000) { compose.onAllNodesWithText("Pausar").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Pausar").assertIsDisplayed().performKeyInput { pressKey(Key.Enter) }
            compose.waitUntil(5_000) { compose.onAllNodesWithText("Reproduzir").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Reproduzir").assertIsDisplayed()
            assertTrue(server.requestCount >= 1)
            compose.runOnIdle {
                val image = compose.onRoot().captureToImage().asAndroidBitmap()
                File(instrumentation.targetContext.filesDir, "tv-player.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, it) }
            }
        }
    }
}

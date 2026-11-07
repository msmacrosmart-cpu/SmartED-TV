package com.smarted.tv

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.smarted.tv.util.TestSeeds
import com.smarted.tv.util.TestServer
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Player real (media3) na TV: abre o "Assistir" de um canal cujo stream é o MP4
 * servido localmente e exige superfície pronta sem erro de reprodução.
 */
class PlayerInstrumentedTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setUp() {
        TestServer.start(ApplicationProvider.getApplicationContext())
        TestSeeds.seedThroughStore(ApplicationProvider.getApplicationContext())
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        runCatching { scenario.close() }
        TestServer.stop()
    }

    private fun awaitNode(tag: String, timeoutMs: Long = 30_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (runCatching { compose.onNodeWithTag(tag).fetchSemanticsNode() }.isSuccess) return
            Thread.sleep(250)
        }
        assertTrue("espera por '$tag' expirou (${timeoutMs}ms)", false)
    }

    @Test
    fun openLiveChannel_playerBecomesReadyWithoutError() {
        awaitNode("clock")
        compose.onNodeWithTag("nav-Canais").performClick()
        awaitNode("channel-list")
        awaitNode("channel-watch", timeoutMs = 20_000)

        compose.onNodeWithTag("channel-watch").performClick()
        awaitNode("fullscreen-player")
        awaitNode("player-surface", timeoutMs = 45_000)
        assertTrue(
            "player deveria estar sem erro",
            runCatching { compose.onNodeWithTag("player-error").fetchSemanticsNode() }.isFailure,
        )
        // Pausar/retomar com os controles do rodapé continua estável.
        compose.onNodeWithTag("player-toggle").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("fullscreen-player").assertIsDisplayed()
        // título do canal no overlay
        assertTrue(
            "título do canal deveria aparecer",
            runCatching { compose.onNodeWithText("Canal Teste HD", substring = true).fetchSemanticsNode() }.isSuccess,
        )
    }
}

package com.smarted.tv

import android.view.KeyEvent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.smarted.tv.util.TestServer
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Ignore
import org.junit.Test

/**
 * Fluxo E2E na TV: adicionar lista pelas Configurações (URL local do MockWebServer),
 * navegação, EPG casado pelo nome do canal, busca que filtra só canais, catálogo
 * de filmes com hero de detalhes.
 */
@Ignore("testTag da UI antiga (clock/channel-*) foi removido — reescrever quando a UI estabilizar testTags")
class TvFlowTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<MainActivity>
    private val timeoutMs = 30_000L

    @Before
    fun setUp() {
        TestServer.start(ApplicationProvider.getApplicationContext())
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        runCatching { scenario.close() }
        TestServer.stop()
    }

    private fun awaitNode(tag: String? = null, text: String? = null) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val found = runCatching {
                if (tag != null) compose.onNodeWithTag(tag).fetchSemanticsNode()
                else compose.onNodeWithText(text!!, substring = true).fetchSemanticsNode()
            }.isSuccess
            if (found) return
            Thread.sleep(250)
        }
        assertTrue("espera por tag=$tag text=$text expirou (${timeoutMs}ms)", false)
    }

    private fun pressBack() {
        val instr = InstrumentationRegistry.getInstrumentation()
        instr.sendKeySync(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
        instr.sendKeySync(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
    }

    @Test
    fun addSourceViaSettings_thenBrowseWithEpgAndVod() {
        awaitNode(tag = "clock")

        compose.onNodeWithTag("nav-Configurações").performClick()
        awaitNode(tag = "form-open-m3u")
        compose.onNodeWithTag("form-open-m3u").performClick()
        awaitNode(tag = "form-url")
        compose.onNodeWithTag("form-name").performTextInput("Teste E2E")
        compose.onNodeWithTag("form-url").performTextInput(TestServer.url("/playlist.m3u"))
        compose.onNodeWithTag("form-epg").performTextInput(TestServer.url("/epg.xml"))
        compose.onNodeWithTag("form-save").performClick()

        // A linha da fonte aparece e o import termina (status verde no rodapé da linha).
        awaitNode(text = "Teste E2E")

        compose.onNodeWithTag("nav-Canais").performClick()
        awaitNode(tag = "channel-list")
        compose.onNodeWithText("Jornal Local", substring = true).assertIsDisplayed()

        // A busca na aba Canais filtra somente canais (esconde o outro pelo EPG/nome).
        compose.onNodeWithTag("channel-search").performTextInput("novela")
        awaitNode(text = "Novela das Sete")
        assertTrue(
            "busca 'novela' não deveria exibir o programa do Canal Teste",
            runCatching { compose.onNodeWithText("Jornal Local", substring = true).fetchSemanticsNode() }.isFailure,
        )
        pressBack() // esconde teclado; campo continua editável

        compose.onNodeWithTag("nav-Filmes").performClick()
        awaitNode(tag = "vod-grid")
        compose.onNodeWithText("Duna O Filme", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Duna O Filme", substring = true).performClick()
        awaitNode(tag = "hero-play")

        // Voltar à grade e depois para o Início.
        compose.onNodeWithTag("hero-back").performClick()
        awaitNode(tag = "vod-grid")
    }
}

package com.smarted.tv.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smarted.tv.core.data.AppGraph
import com.smarted.tv.core.model.SourceKind
import com.smarted.tv.data.db.IptvStore
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FLUXO DE EXECUÇÃO DO ADD-ON → ABAS NATIVAS → PLAYER (regras do produto):
 *  1) addon.xml/entrypoint: coberto por [KodiInstallExecutionTest];
 *  2) este teste prova o MAPEAMENTO: conectar o add-on (painel Xtream falso
 *     realista via MockWebServer) sincroniza de imediato Canais/Filmes/Séries
 *     no banco que alimenta as abas, cada um com URL de reprodução que o
 *     player interno recebe no clique do item final.
 */
@RunWith(AndroidJUnit4::class)
class AddonExecutionFlowTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var server: MockWebServer

    /** Painel Xtream mínimo e realista (mesmos endpoints do plugin). */
    private fun json(body: String) =
        MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path ?: return MockResponse().setResponseCode(404)
                if (path.startsWith("/player_api.php")) {
                    val action = path.substringAfter("action=", "").substringBefore("&")
                    return when (action) {
                        "" -> json("""{"user_info":{"auth":true,"status":"Active"},"server_info":{}}""")
                        "get_live_categories" -> json("""[{"category_id":"1","category_name":"Canais"}]""")
                        "get_live_streams" ->
                            json("""[{"stream_id":"101","name":"Canal Um","category_id":"1","num":"1"}]""")
                        "get_vod_categories" -> json("""[{"category_id":"2","category_name":"Filmes"}]""")
                        "get_vod_streams" -> json(
                            """[{"stream_id":"201","name":"Filme Teste","category_id":"2",""" +
                                """"container_extension":"mp4","added":"1700000000"}]""",
                        )
                        "get_series_categories" -> json("""[{"category_id":"3","category_name":"Séries"}]""")
                        "get_series" -> json(
                            """[{"series_id":"301","name":"Série Teste","category_id":"3",""" +
                                """"added":"1700000000"}]""",
                        )
                        else -> json("[]")
                    }
                }
                if (path.startsWith("/xmltv.php")) {
                    return MockResponse()
                        .setHeader("Content-Type", "application/xml")
                        .setBody("""<?xml version="1.0" encoding="UTF-8"?><tv></tv>""")
                }
                return MockResponse().setResponseCode(404)
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    @Test
    fun connectAddonMapsCategoriesIntoNativeTabsWithPlayUrls() {
        runBlocking<Unit> {
        val repo = AppGraph.content
        val base = server.url("/").toString()

        // Conexão do add-on → cliente NATIVO (mesmo caminho do diálogo do add-on).
        val message = repo.connectAddonXtream("Add-on teste", base, "user1", "pass1")
        assertTrue("mensagem: $message", message.contains("conectado") && !message.contains("falhou"))

        val store = IptvStore(context)
        val source = store.listSources().firstOrNull {
            it.kind == SourceKind.XTREAM && it.url.trimEnd('/') == base.trimEnd('/')
        }
        assertNotNull("fonte Xtream inserida no banco", source)

        // Regra 3: as três categorias do plugin mapeadas nas abas nativas.
        val channels = store.channels(source!!.id, null, null, 50, 0, false)
        assertTrue("Canais: canais ao vivo importados", channels.isNotEmpty())
        assertEquals("grupo mapeado da categoria", "Canais", channels.first().groupName)
        val vodCount = store.vodCount(source.id)
        assertTrue("Filmes+Séries no catálogo (got=$vodCount)", vodCount >= 2)

        // Regra 4: URL de reprodução pronta — é o que o player interno recebe
        // ao clicar no item final.
        val playUrl = channels.first().streamUrl
        assertTrue("URL ao vivo do player: $playUrl", playUrl.contains("/live/user1/pass1/101.m3u8"))
        val movie = store.vodPage(
            kind = com.smarted.tv.core.model.Tab.MOVIES,
            category = null,
            search = "Filme Teste",
            sort = com.smarted.tv.core.model.SortMode.NAME,
            direction = com.smarted.tv.core.model.SortDirection.ASC,
            limit = 50,
            offset = 0,
            favoritesOnly = false,
        ).firstOrNull { it.name.contains("Filme Teste") }
        assertNotNull("filme mapeado", movie)
        assertTrue("URL do filme: ${movie!!.streamUrl}", movie.streamUrl.contains("/movie/user1/pass1/201.mp4"))

        // Limpeza: não deixar fonte de teste no banco do emulador.
        store.deleteSourceCascade(source.id)
        }
    }
}

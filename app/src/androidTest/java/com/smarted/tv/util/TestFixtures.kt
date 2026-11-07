package com.smarted.tv.util

import android.content.Context
import com.smarted.tv.core.model.ListSource
import com.smarted.tv.core.model.SourceKind
import com.smarted.tv.data.db.IptvStore
import com.smarted.tv.data.import.SourceImporters
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest

/**
 * Servidor local dentro do emulador com uma playlist M3U + XMLTV sintéticos e um
 * MP4 real (asset de teste). Cobria o importador, o matcher de EPG e o player.
 */
object TestServer {
    lateinit var server: MockWebServer
        private set

    fun start(context: Context) {
        if (::server.isInitialized) return
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path?.substringBefore('?')) {
                "/playlist.m3u" -> MockResponse().setResponseCode(200).setBody(playlist())
                "/epg.xml" -> MockResponse().setResponseCode(200).setBody(epg())
                "/video.mp4" -> runCatching {
                    val bytes = context.assets.open("fixture.mp4").use { it.readBytes() }
                    MockResponse().setResponseCode(200).setHeader("Content-Type", "video/mp4").setBody(okio.Buffer().write(bytes))
                }.getOrElse { MockResponse().setResponseCode(410).setBody("fixture missing") }
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
    }

    fun stop() {
        if (::server.isInitialized) runCatching { server.shutdown() }
    }

    fun url(path: String): String = "http://127.0.0.1:${server.port}$path"

    private fun playlist(): String {
        val stream = url("/video.mp4")
        return """
            #EXTM3U
            #EXTINF:-1 tvg-id="canal.teste" group-title="GENERAIS",Canal Teste HD
            $stream
            #EXTINF:-1 tvg-id="canal.novela" group-title="NOVELAS",Canal Novela
            $stream
            #EXTINF:-1 group-title="FILMES",Duna O Filme (2021)
            $stream
        """.trimIndent()
    }

    private fun xmlTime(offsetMinutes: Long): String {
        val format = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
        format.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return format.format(Date(System.currentTimeMillis() + offsetMinutes * 60_000L))
    }

    private fun epg(): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <tv>
          <channel id="canal.teste"><display-name>Canal Teste HD</display-name></channel>
          <channel id="canal.novela"><display-name>Canal Novela</display-name></channel>
          <programme start="${xmlTime(-30)}" stop="${xmlTime(60)}" channel="canal.teste">
            <title>Jornal Local</title><desc>Boa noite</desc>
          </programme>
          <programme start="${xmlTime(60)}" stop="${xmlTime(120)}" channel="canal.teste">
            <title>Ponto Final</title>
          </programme>
          <programme start="${xmlTime(-15)}" stop="${xmlTime(75)}" channel="canal.novela">
            <title>Novela das Sete</title>
          </programme>
        </tv>
    """.trimIndent()
}

/** Semeia a fonte direto no banco (caminho rápido) ou via UI (caminho E2E). */
object TestSeeds {
    fun seedThroughStore(context: Context) {
        val store = IptvStore(context.applicationContext)
        val importers = SourceImporters(store)
        runBlocking(Dispatchers.IO) {
            if (store.listSources().any { it.name == "Teste Local" }) return@runBlocking
            val id = store.insertSource(
                ListSource(id = 0, name = "Teste Local", kind = SourceKind.M3U, url = TestServer.url("/playlist.m3u"), epgUrl = TestServer.url("/epg.xml")),
            )
            val source = store.sourceById(id)!!
            importers.import(source, epgWindowHours = 24, refreshEpg = true)
        }
    }
}

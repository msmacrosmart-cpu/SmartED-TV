package com.smarted.tv.data.net

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.OkHttpClient
import okhttp3.Request

/** Cliente HTTP do app. Headers de playlist respeitam UA; stream usa o UA padrão do player. */
object Http {

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .build()
    }

    const val USER_AGENT = "SmartED-TV/3.0 (Android TV; IPTV client)"

    suspend fun getText(url: String, headers: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
        execute(buildRequest(url, headers))
    }

    suspend fun getBytes(url: String, headers: Map<String, String> = emptyMap()): ByteArray = withContext(Dispatchers.IO) {
        val request = buildRequest(url, headers)
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} ao baixar $url")
            response.body?.bytes() ?: throw IOException("Corpo vazio em $url")
        }
    }

    suspend fun getJson(url: String, headers: Map<String, String> = emptyMap()): JsonElement = withContext(Dispatchers.IO) {
        val text = runCatching { execute(buildRequest(url, headers + mapOf("Accept" to "application/json"))) }
        val raw = text.getOrElse { throw it }
        json.parseToJsonElement(raw)
    }

    fun buildRequest(url: String, headers: Map<String, String>): Request {
        val builder = Request.Builder().url(url).header("User-Agent", headers["User-Agent"] ?: USER_AGENT)
            .header("Accept-Encoding", "gzip")
        headers.forEach { (key, value) -> if (key != "User-Agent") builder.header(key, value) }
        return builder.build()
    }

    private fun execute(request: Request): String =
        request.url.let { url ->
            if (url.scheme != "http" && url.scheme != "https") throw IOException("Esquema não suportado: ${url.scheme}")
        }.let {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code} em ${request.url}")
                response.body?.string() ?: throw IOException("Corpo vazio em ${request.url}")
            }
        }

    /** Validação usada pela tela de fontes: HTTP 200 + conteúdo com cara de lista/JSON. */
    fun looksLikePlaylist(url: String, text: String): Boolean {
        val head = text.take(4096).lowercase()
        return url.substringBefore('?').endsWith(".m3u", true) || url.substringBefore('?').endsWith(".m3u8", true) ||
            head.startsWith("#extm3u") || head.contains("#extinf")
    }
}

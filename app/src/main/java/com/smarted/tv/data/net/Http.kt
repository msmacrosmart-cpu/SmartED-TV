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

    suspend fun getText(url: String, headers: Map<String, String> = emptyMap()): String =
        withContext(Dispatchers.IO) { decodeBytes(getBytes(url, headers), url) }

    suspend fun getBytes(url: String, headers: Map<String, String> = emptyMap()): ByteArray = withContext(Dispatchers.IO) {
        val request = buildRequest(url, headers)
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} ao baixar $url")
            response.body?.bytes() ?: throw IOException("Corpo vazio em $url")
        }
    }

    /**
     * UTF-8 + descompactação por assinatura: alguns servidores mandam .gz com
     * Content-Encoding ausente; sem isso o parser vê bytes binários (erro
     * "Unexpected JSON token at offset 0").
     */
    fun decodeBytes(bytes: ByteArray, origin: String = ""): String {
        val body = if (bytes.size >= 2 && bytes[0] == 0x1F.toByte() && bytes[1] == 0x8B.toByte()) {
            java.util.zip.GZIPInputStream(bytes.inputStream()).use { it.readBytes() }
        } else {
            bytes
        }
        return String(body, Charsets.UTF_8).removePrefix("\uFEFF")
    }

    suspend fun getJson(url: String, headers: Map<String, String> = emptyMap()): JsonElement =
        json.parseToJsonElement(readJsonText(url, headers))

    /** Baixa e valida JSON com erro legível (JSON vs HTML vs binário). */
    suspend fun readJsonText(url: String, headers: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
        val raw = runCatching { execute(buildRequest(url, headers + mapOf("Accept" to "application/json"))) }
            .getOrElse { throw it }
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) throw IOException("O endereço respondeu com corpo vazio.")
        val first = trimmed[0]
        if (first != '{' && first != '[') {
            val looksHtml = trimmed.startsWith("<", true)
            val preview = trimmed.replace(Regex("\s+"), " ").take(80)
            throw IOException(
                if (looksHtml) "A URL respondeu uma página HTML, não o arquivo JSON. Use o link RAW ("Raw"/"Download" no GitHub), terminando em .json"
                else "A resposta não é JSON (começa com byte 0x%02X — arquivo compactado/binário?). Aponte para o índice .json direto.".format(first.code)
                + " Recebido: "$preview…""
            )
        }
        trimmed
    }

    fun buildRequest(url: String, headers: Map<String, String>): Request {
        // Sem Accept-Encoding manual: OkHttp faz gzip/br transparente e decompõe sozinho.
        val builder = Request.Builder().url(url).header("User-Agent", headers["User-Agent"] ?: USER_AGENT)
        headers.forEach { (key, value) -> if (key != "User-Agent") builder.header(key, value) }
        return builder.build()
    }

    private fun execute(request: Request): String =
        request.url.let { url ->
            if (url.scheme != "http" && url.scheme != "https") throw IOException("Esquema não suportado: ${url.scheme}")
        }.let {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code} em ${request.url}")
                val bytes = response.body?.bytes() ?: throw IOException("Corpo vazio em ${request.url}")
                decodeBytes(bytes, request.url.toString())
            }
        }

    /** Validação usada pela tela de fontes: HTTP 200 + conteúdo com cara de lista/JSON. */
    fun looksLikePlaylist(url: String, text: String): Boolean {
        val head = text.take(4096).lowercase()
        return url.substringBefore('?').endsWith(".m3u", true) || url.substringBefore('?').endsWith(".m3u8", true) ||
            head.startsWith("#extm3u") || head.contains("#extinf")
    }
}

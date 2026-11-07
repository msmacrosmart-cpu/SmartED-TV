package com.smarted.tv.core.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Cliente HTTP único do app.
 *
 * - Um pool de conexões compartilhado (addons, EPG, TMDB, indexador).
 * - Cache em disco de 48h para metadados.
 * - Cabeçalhos de player (User-Agent/Referer) por requisição.
 * - Toda chamada é suspensa e roda em [Dispatchers.IO].
 */
class Http(
    cacheDir: File,
    userAgent: String = DEFAULT_UA,
) {

    private val cache = Cache(File(cacheDir, "http"), 96L * 1024 * 1024)

    private val baseHeaders = Interceptor { chain ->
        val req = chain.request().newBuilder()
            .header("User-Agent", userAgent)
            .header("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.8")
            .header("Accept-Encoding", "gzip, deflate")
            .build()
        chain.proceed(req)
    }

    private val retry = Interceptor { chain ->
        val request = chain.request()
        var response: Response? = null
        var attempt = 0
        var lastError: IOException? = null
        while (attempt < 2) {
            try {
                response?.close()
                response = chain.proceed(request)
                if (response.isSuccessful || response.code in 400..499) return@Interceptor response
            } catch (e: IOException) {
                lastError = e
            }
            attempt++
        }
        response ?: throw (lastError ?: IOException("Falha de rede"))
    }

    val client: OkHttpClient = OkHttpClient.Builder()
        .cache(cache)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .callTimeout(40, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor(baseHeaders)
        .addInterceptor(retry)
        .apply {
            if (BUILD_DEBUG) {
                addInterceptor(
                    HttpLoggingInterceptor()
                        .setLevel(HttpLoggingInterceptor.Level.BASIC),
                )
            }
        }
        .build()

    /** Corpo em texto com cabeçalhos adicionais (player/referrer). */
    suspend fun getText(
        url: String,
        headers: Map<String, String> = emptyMap(),
        cacheable: Boolean = true,
        timeoutSeconds: Long = 40,
    ): String = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(url)
        if (!cacheable) builder.header("Cache-Control", "no-cache")
        headers.forEach { (k, v) -> builder.header(k, v) }
        val localClient = if (timeoutSeconds > 40) {
            client.newBuilder().callTimeout(timeoutSeconds, TimeUnit.SECONDS).build()
        } else {
            client
        }
        localClient.newCall(builder.build()).execute().use { res ->
            if (!res.isSuccessful) throw IOException("HTTP ${res.code} em $url")
            res.body?.string() ?: throw IOException("Corpo vazio em $url")
        }
    }

    /** Bytes (para zip de addon / imagem). */
    suspend fun getBytes(url: String, headers: Map<String, String> = emptyMap()): ByteArray =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder().url(url)
            headers.forEach { (k, v) -> builder.header(k, v) }
            client.newCall(builder.build()).execute().use { res ->
                if (!res.isSuccessful) throw IOException("HTTP ${res.code} em $url")
                res.body?.bytes() ?: throw IOException("Corpo vazio em $url")
            }
        }

    /** HEAD rápido usado pelo verificador de links. */
    suspend fun probe(url: String, headers: Map<String, String> = emptyMap()): ProbeResult =
        withContext(Dispatchers.IO) {
            val started = System.currentTimeMillis()
            try {
                val builder = Request.Builder().url(url).head()
                headers.forEach { (k, v) -> builder.header(k, v) }
                val probeClient = client.newBuilder()
                    .callTimeout(10, TimeUnit.SECONDS)
                    .build()
                probeClient.newCall(builder.build()).execute().use { res ->
                    val ok = res.isSuccessful || res.code in 200..399 || res.code == 405
                    ProbeResult(
                        ok = ok,
                        code = res.code,
                        latencyMs = System.currentTimeMillis() - started,
                        contentType = res.header("Content-Type"),
                        contentLength = res.header("Content-Length")?.toLongOrNull() ?: -1L,
                    )
                }
            } catch (e: Exception) {
                // Alguns servidores recusam HEAD; tenta um GET de 1 byte.
                try {
                    val req = Request.Builder().url(url)
                        .header("Range", "bytes=0-1024")
                        .build()
                    client.newCall(req).execute().use { res ->
                        ProbeResult(
                            ok = res.code in 200..399,
                            code = res.code,
                            latencyMs = System.currentTimeMillis() - started,
                            contentType = res.header("Content-Type"),
                            contentLength = res.header("Content-Length")?.toLongOrNull() ?: -1L,
                        )
                    }
                } catch (e2: Exception) {
                    ProbeResult(
                        ok = false,
                        code = -1,
                        latencyMs = System.currentTimeMillis() - started,
                        contentType = null,
                        contentLength = -1L,
                        error = e2.message ?: "falha de rede",
                    )
                }
            }
        }

    fun shutdown() {
        runCatching { cache.close() }
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    companion object {
        const val DEFAULT_UA =
            "Mozilla/5.0 (Linux; Android 12; SmartED TV) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0.0.0 Safari/537.36 SmartEdTV/1.0"

        // Espelhado de BuildConfig.DEBUG sem depender do módulo app.
        private const val BUILD_DEBUG = false
    }
}

data class ProbeResult(
    val ok: Boolean,
    val code: Int,
    val latencyMs: Long,
    val contentType: String?,
    val contentLength: Long,
    val error: String? = null,
)

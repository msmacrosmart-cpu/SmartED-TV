package com.smarted.tv.core.protocol

import com.smarted.tv.core.model.*
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import kotlin.coroutines.coroutineContext

interface StremioApi {
    suspend fun manifest(url: String): Manifest
    suspend fun catalog(addon: InstalledAddon, catalog: CatalogSpec, extra: Map<String, String> = emptyMap()): List<MediaItem>
    suspend fun meta(item: MediaItem): MediaItem
    suspend fun streams(item: MediaItem, videoId: String): List<MediaStream>
}

class StremioClient(private val http: OkHttpClient = defaultClient()) : StremioApi, AutoCloseable {
    override suspend fun manifest(url: String): Manifest = ProtocolJson.manifest(get(AddonUrls.normalize(url)))

    override suspend fun catalog(addon: InstalledAddon, catalog: CatalogSpec, extra: Map<String, String>): List<MediaItem> {
        require(catalog.type in addon.scope.wireTypes) { "Catálogo fora do escopo instalado." }
        val url = AddonUrls.resource(addon.url, "catalog", catalog.type, catalog.id, extra)
        return ProtocolJson.metas(get(url)).mapIndexedNotNull { index, obj ->
            ProtocolJson.item(obj, addon.url, addon.scope, catalog.type)?.copy(
                catalogName = catalog.displayName,
                catalogPosition = (extra["skip"]?.toLongOrNull() ?: 0L) + index,
            )
        }
    }

    override suspend fun meta(item: MediaItem): MediaItem {
        val obj = ProtocolJson.meta(get(AddonUrls.resource(item.addonUrl, "meta", item.type, item.id)))
        return ProtocolJson.item(obj, item.addonUrl, item.scope, item.type)?.copy(
            catalogName = item.catalogName, catalogPosition = item.catalogPosition, addedAt = item.addedAt,
        ) ?: throw AddonException("Detalhes sem id/name válidos ou fora do escopo selecionado.")
    }

    override suspend fun streams(item: MediaItem, videoId: String): List<MediaStream> =
        ProtocolJson.streams(get(AddonUrls.resource(item.addonUrl, "stream", item.type, videoId)))

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("Accept", "application/json, text/plain;q=0.9").build()
        try {
            http.newCall(request).awaitResponse().use { response ->
                if (!response.isSuccessful) throw AddonException(httpError(response.code))
                val body = response.body ?: throw AddonException("O servidor não enviou conteúdo.")
                val declared = body.contentLength()
                if (declared > MAX_BYTES) throw AddonException("Resposta muito grande. Limite: 8 MB por recurso.")
                val source = body.source()
                source.request(MAX_BYTES + 1)
                coroutineContext.ensureActive()
                if (source.buffer.size > MAX_BYTES) throw AddonException("Resposta muito grande. Limite: 8 MB por recurso.")
                source.readString(Charsets.UTF_8)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: AddonException) {
            throw e
        } catch (e: IOException) {
            throw AddonException("Não foi possível conectar ao addon. Confira a URL, a conexão e o certificado HTTPS.", e)
        }
    }

    override fun close() {
        http.dispatcher.executorService.shutdown()
        http.connectionPool.evictAll()
        http.cache?.close()
    }

    companion object {
        const val MAX_BYTES = 8L * 1024 * 1024
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS).retryOnConnectionFailure(true)
            .build()
        fun httpError(code: Int): String = when (code) {
            401, 403 -> "Acesso negado pelo addon (HTTP $code). Confira o token ou a configuração."
            404 -> "Recurso não encontrado (HTTP 404). Confira a URL e os recursos declarados no manifesto."
            429 -> "O addon limitou as requisições (HTTP 429). Aguarde antes de tentar novamente."
            in 500..599 -> "O addon está indisponível (HTTP $code). Tente novamente mais tarde."
            else -> "O servidor recusou a requisição (HTTP $code)."
        }
    }
}

private suspend fun Call.awaitResponse(): Response = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : okhttp3.Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resumeWith(Result.failure(e))
        }
        override fun onResponse(call: Call, response: Response) {
            if (continuation.isActive) continuation.resumeWith(Result.success(response)) else response.close()
        }
    })
}

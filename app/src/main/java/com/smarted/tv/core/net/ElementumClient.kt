package com.smarted.tv.core.net

import com.smarted.tv.core.addons.Elementum
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Cliente do daemon local do **Elementum** (`127.0.0.1:65220`).
 *
 * O addon do repositório Vikings é Python e não roda no Android, mas o
 * Elementum distribui um daemon nativo que expõe HTTP em loopback. É ele que
 * resolve o magnet e serve o vídeo — o app só busca e manda a URL para o
 * player ([Elementum.playUrl]).
 *
 * O daemon responde em formato **Kodi** (`{"items":[…]}`), e o cabeçalho
 * `User-Agent` não pode ser `plugin.video.elementum`: com ele o daemon acha que
 * é o plugin chamando e tenta abrir diálogos no Kodi. Como o app pede `list=1`
 * em todas as chamadas, a resposta vem sempre como lista.
 *
 * Nada aqui lança exceção para a UI: daemon desligado é estado normal
 * ([Elementum.Status.online] = false) e a tela mostra o que fazer.
 */
class ElementumClient(
    private val http: Http,
    private val port: Int = Elementum.DEFAULT_PORT,
) {

    /** O daemon está no ar? Timeout curto: é loopback, responde na hora. */
    suspend fun status(): Elementum.Status = withContext(Dispatchers.IO) {
        val body = runCatching {
            http.getText(
                url = Elementum.statusUrl(port),
                headers = HEADERS,
                cacheable = false,
                timeoutSeconds = LOCAL_TIMEOUT,
            )
        }.getOrNull()
        when {
            body == null -> Elementum.Status(
                online = false,
                port = port,
                message = "Elementum desligado: abra o daemon e tente de novo",
            )
            else -> Elementum.parseStatus(body, port = port)
        }
    }

    /** Menu inicial do daemon (`/`), útil para saber o que ele oferece. */
    suspend fun menu(): List<Elementum.Node> = view(Elementum.browseUrl("/", port))

    /** Filmes populares (`/movies/popular`). */
    suspend fun movies(): List<Elementum.Item> = items(Elementum.browseUrl(Elementum.PATH_MOVIES, port))

    /** Séries populares (`/shows/popular`). */
    suspend fun shows(): List<Elementum.Item> = items(Elementum.browseUrl(Elementum.PATH_SHOWS, port))

    /**
     * Busca torrentes pelo termo.
     *
     * Devolve lista vazia quando o daemon está desligado ou não achou nada — a
     * tela usa [status] para dizer qual dos dois é o caso.
     */
    suspend fun search(query: String): List<Elementum.Item> {
        val term = query.trim()
        if (term.isBlank()) return emptyList()
        return items(Elementum.searchUrl(term, port))
    }

    /** Navega um caminho qualquer do daemon, devolvendo os nós crus. */
    suspend fun view(url: String): List<Elementum.Node> = withContext(Dispatchers.IO) {
        val body = runCatching {
            http.getText(
                url = url,
                headers = HEADERS,
                cacheable = false,
                timeoutSeconds = SEARCH_TIMEOUT,
            )
        }.getOrNull() ?: return@withContext emptyList()
        runCatching { Elementum.parseView(body) }.getOrDefault(emptyList())
    }

    /** Busca um caminho e já converte em resultados reproduzíveis. */
    private suspend fun items(url: String): List<Elementum.Item> = withContext(Dispatchers.IO) {
        val body = runCatching {
            http.getText(
                url = url,
                headers = HEADERS,
                cacheable = false,
                timeoutSeconds = SEARCH_TIMEOUT,
            )
        }.getOrNull() ?: return@withContext emptyList()
        runCatching { Elementum.parseItems(body) }.getOrDefault(emptyList())
    }

    /** Confere se o daemon responde antes de abrir o player. */
    suspend fun isOnline(): Boolean = runCatching { status().online }.getOrDefault(false)

    private companion object {
        /**
         * O daemon trata `plugin.video.elementum` como "chamada do Kodi" e abre
         * diálogo em vez de responder a lista; com outro agente ele responde
         * dados. É o mesmo caminho que o Web UI do Elementum usa.
         */
        val HEADERS = mapOf(
            "User-Agent" to "SmartED-TV",
            "Accept" to "application/json",
        )

        /** Loopback: se não respondeu em 3 s, não está no ar. */
        const val LOCAL_TIMEOUT = 3L

        /** Busca pode envolver todos os provedores do daemon. */
        const val SEARCH_TIMEOUT = 12L
    }
}

/** Falha de rede do daemon (mantida para diagnóstico na tela de Addons). */
class ElementumException(message: String) : IOException(message)

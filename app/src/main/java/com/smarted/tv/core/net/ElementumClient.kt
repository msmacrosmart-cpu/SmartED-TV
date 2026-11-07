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
 * player ([com.smarted.tv.core.addons.Elementum.playUrl]).
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

    /**
     * Busca torrentes pelo termo.
     *
     * Devolve lista vazia quando o daemon está desligado ou não entendeu a
     * consulta — a tela usa [status] para dizer qual dos dois foi.
     */
    suspend fun search(query: String): List<Elementum.Item> = withContext(Dispatchers.IO) {
        val term = query.trim()
        if (term.isBlank()) return@withContext emptyList()
        val body = runCatching {
            http.getText(
                url = Elementum.searchUrl(term, port),
                cacheable = false,
                timeoutSeconds = SEARCH_TIMEOUT,
            )
        }.getOrNull() ?: return@withContext emptyList()
        runCatching { Elementum.parseItems(body) }.getOrDefault(emptyList())
    }

    /** Confere se o daemon responde antes de abrir o player. */
    suspend fun isOnline(): Boolean = runCatching { status().online }.getOrDefault(false)

    private companion object {
        /** Loopback: se não respondeu em 3 s, não está no ar. */
        const val LOCAL_TIMEOUT = 3L

        /** Busca pode envolver indexador externo. */
        const val SEARCH_TIMEOUT = 12L
    }
}

/** Falha de rede do daemon (mantida para diagnóstico na tela de Ajustes). */
class ElementumException(message: String) : IOException(message)

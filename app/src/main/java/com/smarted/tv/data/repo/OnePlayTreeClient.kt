package com.smarted.tv.data.repo

import com.smarted.tv.data.net.Http
import kotlinx.serialization.Serializable
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Url

/**
 * Índice JSON do repositório OnePlay (fantasma) — DTOs recursivos.
 *
 * O campo `tree` descreve a árvore de diretórios que o módulo Repository/Tree lê
 * recursivamente: [Addons] → [TV / Filmes / Séries] → [subpastas de conteúdo].
 * Ausência de `tree` (índice antigo) ou falha de rede/parse => `null`,
 * e a navegação segue com a árvore local do banco (modo offline à prova de falha).
 */
@Serializable
data class OnePlayIndexDto(
    val name: String? = null,
    val tree: OnePlayNodeDto? = null,
)

@Serializable
data class OnePlayNodeDto(
    val name: String? = null,
    /** "root" | "category" | "folder" | "leaf" — intenção do nó (opcional). */
    val kind: String? = null,
    /** "channels" | "movies" | "series" — apenas em nós de categoria. */
    val tab: String? = null,
    /** URL direta de reprodução (nó folha). */
    val url: String? = null,
    val id: String? = null,
    /** Filhos recursivos (subpastas/conteúdo). */
    val children: List<OnePlayNodeDto>? = null,
)

/** Serviço Retrofit do índice OnePlay (interface pública exigida pelo Proxy dinâmico). */
interface OnePlayTreeApi {
    /** [indexUrl] é absoluto (@Url anula o baseUrl configurado). */
    @GET
    suspend fun index(@Url indexUrl: String): String
}

/**
 * Cliente HTTP/JSON nativo ultra leve (Retrofit + kotlinx-serialization sobre o
 * OkHttp já presente no app) para a árvore do repositório OnePlay "fantasma".
 * Sem engines externas pesadas. Toda falha (rede, timeout, JSON inválido) vira `null`.
 */
object OnePlayTreeClient {

    private const val RAW_BASE = "https://raw.githubusercontent.com/"

    private val api: OnePlayTreeApi by lazy {
        Retrofit.Builder()
            .baseUrl(RAW_BASE)
            .client(Http.client)
            .build()
            .create(OnePlayTreeApi::class.java)
    }

    /** Melhor esforço: `null` em qualquer falha (a UI cai para a árvore local). */
    suspend fun load(indexUrl: String): OnePlayIndexDto? = runCatching {
        val text = api.index(indexUrl)
        Http.json.decodeFromString(OnePlayIndexDto.serializer(), text)
    }.getOrNull()
}

package com.smarted.tv.data

import android.content.Context
import com.smarted.tv.AppGraph
import com.smarted.tv.core.model.ListSource
import com.smarted.tv.core.model.SourceKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repositório OnePlay "fantasma".
 *
 * É injetado nativamente no app: inicializa e sincroniza automaticamente em segundo plano
 * na primeira execução, sem exibir qualquer campo de link, URL ou nome ao usuário em
 * Configurações. Nunca interrompe a inicialização em caso de falha de rede ou índice inválido.
 */
object OnePlayBootstrap {

    private const val PREFS = "smarted_prefs"
    private const val KEY_INIT = "oneplay_phantom_inicializado"

    /** Nome da fonte-fantasma (filtrada da UI de Configurações para ficar invisível). */
    const val PHANTOM_NAME = "OnePlay"

    /**
     * Índice JSON (RAW) do repositório OnePlay — ADDONS com tipos universais
     * (video, programs, radio, ...) e menus recursivos lidos pelo módulo
     * Repository/Tree (Retrofit + kotlinx-serialization). Aponta para o branch
     * deste build para o índice evoluir junto com o app.
     */
    const val INDEX_URL =
        "https://raw.githubusercontent.com/msmacrosmart-cpu/SmartED-TV/arena/962c30bf-smarted-tv/app/src/main/assets/oneplay_index.json"

    fun isInitialized(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_INIT, false)

    fun markInitialized(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_INIT, true).apply()
    }

    private fun phantomSource(graph: AppGraph) =
        graph.store.listSources().firstOrNull { it.name == PHANTOM_NAME && it.kind == SourceKind.REPOSITORY }

    /**
     * Adiciona (ou reusa) a fonte-fantasma e dispara a sincronização dos filhos indexados no JSON.
     * Idempotente: nunca cria fontes duplicadas e nunca deixa rastros de erro na UI do usuário.
     */
    suspend fun bootstrap(graph: AppGraph, ctx: Context) {
        withContext(Dispatchers.IO) {
            if (!isInitialized(ctx)) {
                val parent = phantomSource(graph) ?: run {
                    val id = graph.store.insertSource(
                        ListSource(id = 0, name = PHANTOM_NAME, kind = SourceKind.REPOSITORY, url = INDEX_URL),
                    )
                    ListSource(id = id, name = PHANTOM_NAME, kind = SourceKind.REPOSITORY, url = INDEX_URL)
                }
                runCatching { graph.sources.syncRepository(parent) }
                    .onSuccess { markInitialized(ctx) }
                    .onFailure {
                        // Silencioso por design: rede fora do ar ou índice inválido não quebram o boot.
                        // Na próxima inicialização a sincronização é tentada novamente nesta mesma fonte.
                    }
            }
        }
    }
}

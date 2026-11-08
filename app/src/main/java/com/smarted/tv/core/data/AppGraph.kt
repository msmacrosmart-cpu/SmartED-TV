package com.smarted.tv.core.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Composição manual (sem DI framework): as telas só enxergam este serviço.
 * [init] é chamado por SmartEdApplication com o grafo de dados real; assim o
 * layout nunca depende das classes de import/playback diretamente.
 */
object AppGraph {
    @Volatile private var initialized = false

    lateinit var content: ContentRepository
        private set
    lateinit var system: IptvSystem
        private set
    lateinit var modePrefs: ModePrefs
        private set

    fun init(ctx: Context, graph: com.smarted.tv.AppGraph) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            modePrefs = ModePrefs(ctx)
            content = IptvContentRepository(graph, scope)
            system = IptvSystem(graph, ctx)
            initialized = true

            // Repositório OnePlay "fantasma": inicializa e sincroniza em segundo plano na
            // primeira execução, sem exibir qualquer campo de URL/nome ao usuário.
            // (bootstrap já engole falhas de rede/índice sozinho.)
            scope.launch {
                com.smarted.tv.data.OnePlayBootstrap.bootstrap(graph, ctx)
            }
        }
    }
}

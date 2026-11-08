package com.smarted.tv

import com.smarted.tv.core.data.IptvContentRepository
import com.smarted.tv.data.OnePlayBootstrap
import com.smarted.tv.data.system.IptvSystem
import com.smarted.tv.data.system.ModePrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Grafo de dados do app (banco, fontes, Elementum) + composição do layout. */
class SmartEdApplication : android.app.Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        com.smarted.tv.core.data.AppGraph.init(
            IptvContentRepository(graph, scope),
            IptvSystem(graph, this),
            ModePrefs(this),
        )
        // Repositório OnePlay "fantasma": inicializa e sincroniza em segundo plano na
        // primeira execução, sem exibir qualquer campo de URL/nome ao usuário.
        // (o bootstrap engole falhas de rede/índice sozinho.)
        scope.launch { OnePlayBootstrap.bootstrap(graph, this@SmartEdApplication) }
        graph.initialize()
    }
}

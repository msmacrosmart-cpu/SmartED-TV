package com.smarted.tv

/** Grafo de dados do app (banco, fontes, Elementum) + composição do layout. */
class SmartEdApplication : android.app.Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        com.smarted.tv.core.data.AppGraph.init(this, graph)
        graph.initialize()
    }
}

package com.smarted.tv

import android.app.Application

/**
 * Application do app — referenciada pelo manifest (`android:name=".SmartEdApplication"`).
 *
 * Vive em arquivo próprio de propósito: o R8 no release combina classes do mesmo
 * pacote e elimina as que não têm referência estática. Quando esta classe
 * ficava dentro de `AppGraph.kt`, o AGP falhava a geração do manifest de release
 * ("Unable to make field ... accessible") porque o `runtime.txt` civil não expõe
 * a referência usada só pelo manifest.
 */
class SmartEdApplication : Application() {

    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        graph = AppGraph(this)
        graph.boot()
    }

    override fun onTerminate() {
        super.onTerminate()
        instance?.graph?.shutdown()
    }

    companion object {
        @Volatile
        private var instance: SmartEdApplication? = null

        fun graphOrNull(): AppGraph? = instance?.graph
    }
}

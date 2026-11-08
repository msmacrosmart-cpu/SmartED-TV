package com.smarted.tv.core.data

import com.smarted.tv.data.system.IptvSystem
import com.smarted.tv.data.system.ModePrefs

/**
 * Composição manual (sem DI framework): as telas só enxergam este serviço.
 * [init] é chamado por SmartEdApplication com as peças já construídas (banco,
 * sistema, preferências); assim o layout nunca depende das classes de
 * import/playback diretamente — e este núcleo permanece JVM puro (sem Android).
 */
object AppGraph {
    @Volatile private var initialized = false

    lateinit var content: ContentRepository
        private set
    lateinit var system: IptvSystem
        private set
    lateinit var modePrefs: ModePrefs
        private set

    fun init(content: ContentRepository, system: IptvSystem, modePrefs: ModePrefs) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            this.content = content
            this.system = system
            this.modePrefs = modePrefs
            initialized = true
        }
    }
}

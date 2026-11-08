package com.smarted.tv.ui.settings

import androidx.lifecycle.ViewModel
import com.smarted.tv.core.data.IptvSystem
import com.smarted.tv.core.model.PlaylistSource

/** Fachada fina sobre [IptvSystem]: a tela de Configurações não conhece o grafo. */
class SettingsViewModel(private val system: IptvSystem) : ViewModel() {
    val sources = system.sources
    val message = system.message
    val busy = system.busyFlow
    val elementum = system.elementum
    val preferences = system.preferences

    fun add(s: PlaylistSource) = system.add(s)
    fun remove(id: String) = system.remove(id)
    fun refresh(id: String) = system.refresh(id)
    fun toggleEnabled(id: String) = system.toggleEnabled(id)
    fun setInterval(id: String, minutes: Int) = system.setInterval(id, minutes)
    fun refreshAll() = system.refreshAll()
    fun consumeMessage() = system.consumeMessage()

    fun elementumApply(host: String, port: String, enabled: Boolean) = system.elementumApply(host, port, enabled)
    fun elementumTest() = system.elementumTest()
    fun elementumUpdate() = system.elementumUpdate()
    fun setResume(enabled: Boolean) = system.setResumeEnabled(enabled)
}

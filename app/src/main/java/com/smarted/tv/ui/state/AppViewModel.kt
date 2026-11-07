package com.smarted.tv.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smarted.tv.AppGraph
import com.smarted.tv.core.model.Manifest
import com.smarted.tv.core.model.Scope
import com.smarted.tv.core.protocol.AddonUrls
import com.smarted.tv.core.protocol.ManifestValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

data class SettingsUi(val busy: Boolean = false, val message: String = "", val error: String = "", val preview: Manifest? = null, val revision: Int = 0)

class AppViewModel(val graph: AppGraph) : ViewModel() {
    private val _ready = MutableStateFlow(false)
    val ready = _ready.asStateFlow()
    private val _startupError = MutableStateFlow("")
    val startupError = _startupError.asStateFlow()
    private val _settings = MutableStateFlow(SettingsUi())
    val settings = _settings.asStateFlow()
    val data get() = graph.repository.data
    init { initialize() }
    fun initialize() {
        viewModelScope.launch {
            _startupError.value = ""
            try { graph.repository.initialize(); _ready.value = true }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _startupError.value = e.message ?: "Não foi possível abrir os dados locais." }
        }
    }
    fun saveProgress(request: com.smarted.tv.ui.screens.PlayRequest, position: Long, duration: Long) {
        viewModelScope.launch {
            try { graph.repository.saveProgress(request.item, request.item.playbackId(request.video), position, duration) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _settings.update { it.copy(error = "Não foi possível salvar o progresso: ${e.message ?: "armazenamento indisponível"}") } }
        }
    }
    fun clearMessages() { _settings.update { it.copy(message = "", error = "") } }
    fun preview(url: String, scope: Scope) = operation {
        val manifest = graph.api.manifest(AddonUrls.normalize(url))
        ManifestValidator.validate(manifest, scope)
        _settings.update { it.copy(preview = manifest) }
        "Manifesto válido: ${manifest.name} · ${manifest.catalogsFor(scope).size} catálogo(s) neste escopo."
    }
    fun install(url: String, scope: Scope) = operation {
        val addon = graph.repository.install(url, scope)
        _settings.update { it.copy(preview = null, revision = it.revision + 1) }
        "${addon.manifest.name} adicionado. O conteúdo aparecerá somente no escopo escolhido."
    }
    fun remove(url: String) = operation { graph.repository.remove(url); _settings.update { it.copy(revision = it.revision + 1) }; "Addon removido." }
    fun refresh(url: String) = operation { graph.repository.refresh(url); _settings.update { it.copy(revision = it.revision + 1) }; "Manifesto atualizado." }
    fun scope(url: String, scope: Scope) = operation { graph.repository.changeScope(url, scope); _settings.update { it.copy(revision = it.revision + 1) }; "Escopo atualizado." }
    fun preferences(transform: (com.smarted.tv.core.model.UserPreferences) -> com.smarted.tv.core.model.UserPreferences) {
        viewModelScope.launch {
            try { graph.repository.updatePreferences(transform); _settings.update { it.copy(message = "Preferência salva.", error = "") } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _settings.update { it.copy(error = e.message ?: "Não foi possível salvar a preferência.") } }
        }
    }
    fun clearProgress() = operation { graph.repository.clearProgress(); "Histórico de reprodução apagado." }
    fun resetPreferences() = preferences { com.smarted.tv.core.model.UserPreferences() }
    private fun operation(block: suspend () -> String) {
        if (_settings.value.busy) return
        _settings.update { it.copy(busy = true, error = "", message = "") }
        viewModelScope.launch {
            try { val message = block(); _settings.update { it.copy(busy = false, message = message) } }
            catch (e: CancellationException) { _settings.update { it.copy(busy = false) }; throw e }
            catch (e: Exception) { _settings.update { it.copy(busy = false, error = e.message ?: "Falha na operação.") } }
        }
    }
}

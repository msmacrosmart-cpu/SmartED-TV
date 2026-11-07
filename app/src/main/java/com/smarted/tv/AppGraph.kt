package com.smarted.tv

import android.content.Context
import com.smarted.tv.core.model.UserPreferences
import com.smarted.tv.data.CatalogRepository
import com.smarted.tv.data.db.IptvStore
import com.smarted.tv.data.elementum.ElementumClient
import com.smarted.tv.data.import.SourceImporters
import com.smarted.tv.data.settings.SettingsStore
import com.smarted.tv.data.sources.SourceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Grafo manual de dependências (sem DI framework): banco, fontes, importadores, Elementum.
 * O agendador de auto-atualização roda enquanto o app vive (TV fica ligada; WorkManager
 * seria peso morto num leanback foreground app).
 */
class AppGraph(context: Context) {
    val store = IptvStore(context)
    val settings = SettingsStore(context)
    val importers = SourceImporters(store)
    val sources = SourceManager(store, importers, epgWindowHoursProvider = { currentPreferences.epgWindowHours })
    val catalog = CatalogRepository(store)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val preferences = MutableStateFlow(UserPreferences())
    val elementum = MutableStateFlow<ElementumSnapshot>(ElementumSnapshot())

    data class ElementumSnapshot(
        val online: Boolean = false,
        val activeTorrents: Int = 0,
        val version: String = "",
        val message: String = "",
        val lastCheckMs: Long = 0,
        val updating: Boolean = false,
    )

    val currentPreferences: UserPreferences get() = preferences.value

    private var schedulerStarted = false

    fun initialize() {
        if (schedulerStarted) return
        schedulerStarted = true
        scope.launch {
            preferences.value = settings.read()
            // primeira passada logo após abrir; depois, minuto em minuto.
            runAutoUpdateTick()
            checkElementum()
            while (true) {
                delay(60_000)
                runAutoUpdateTick()
                if (preferences.value.elementumEnabled) checkElementum()
            }
        }
    }

    private suspend fun runAutoUpdateTick() {
        runCatching {
            val prefs = preferences.value
            val done = sources.refreshDueSources(prefs.autoUpdateMinutes)
            if (done > 0 && prefs.elementumEnabled) triggerElementumUpdate()
        }
    }

    fun refreshAllNow(onProgress: (String) -> Unit = {}) {
        scope.launch {
            for (source in store.listSources()) {
                if (!source.enabled) continue
                onProgress("Atualizando ${source.name}…")
                runCatching { sources.refresh(source) }
                    .onSuccess(onProgress)
                    .onFailure { onProgress("${source.name}: ${it.message ?: "erro"}") }
            }
            onProgress("Concluído.")
        }
    }

    fun checkElementum() {
        scope.launch {
            val prefs = preferences.value
            if (!prefs.elementumEnabled) {
                elementum.value = ElementumSnapshot(message = "Integração desativada nas Configurações.")
                return@launch
            }
            val status = ElementumClient(prefs.elementumBaseUrl).status()
            elementum.value = ElementumSnapshot(status.online, status.activeTorrents, status.version, status.message, System.currentTimeMillis())
        }
    }

    fun triggerElementumUpdate() {
        scope.launch {
            val prefs = preferences.value
            if (!prefs.elementumEnabled) return@launch
            elementum.update { it.copy(updating = true) }
            val ok = ElementumClient(prefs.elementumBaseUrl).triggerAutoUpdate()
            elementum.update { it.copy(updating = false, message = if (ok) "Elementum atualizado (reload de provedores)." : "Elementum não respondeu ao reload.") }
        }
    }

    fun savePreferences(transform: (UserPreferences) -> UserPreferences) {
        scope.launch {
            val updated = try { transform(preferences.value) } catch (e: IllegalArgumentException) { return@launch }
            preferences.value = updated
            settings.write(updated)
        }
    }

    fun stop() { /* escopo acompanha o processo do app */ }
}

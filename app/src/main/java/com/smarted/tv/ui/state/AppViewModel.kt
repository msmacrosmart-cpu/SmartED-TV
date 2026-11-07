package com.smarted.tv.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smarted.tv.AppGraph
import com.smarted.tv.core.model.ListSource
import com.smarted.tv.core.model.SourceKind
import com.smarted.tv.core.model.Tab
import com.smarted.tv.core.model.UserPreferences
import com.smarted.tv.data.db.IptvStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppUi(
    val ready: Boolean = false,
    val startupError: String = "",
    val busy: String = "",
    val message: String = "",
    val error: String = "",
    /** incrementa a cada escrita no repositório para as telas revalidarem. */
    val revision: Int = 0,
    val sources: List<ListSource> = emptyList(),
    val counts: Map<Tab, Int> = emptyMap(),
    val programCount: Int = 0,
    val continueWatching: List<IptvStore.ProgressRecord> = emptyList(),
    val recentlyAddedMovies: List<com.smarted.tv.core.model.VodEntry> = emptyList(),
    val recentlyAddedSeries: List<com.smarted.tv.core.model.VodEntry> = emptyList(),
    val topMovies: List<com.smarted.tv.core.model.VodEntry> = emptyList(),
    val favoriteChannels: List<com.smarted.tv.core.model.LiveChannel> = emptyList(),
)

class AppViewModel(val graph: AppGraph) : ViewModel() {

    private val _ui = MutableStateFlow(AppUi())
    val ui = _ui.asStateFlow()
    val preferences = graph.preferences
    val elementum = graph.elementum

    init {
        viewModelScope.launch {
            graph.sources.busy.collect { busy -> _ui.update { it.copy(busy = if (busy.isBusy) busy.message else "") } }
        }
        viewModelScope.launch {
            graph.sources.revision.collect { _ui.update { it.copy(revision = it.revision + 1) } }
        }
        initialize()
    }

    fun initialize() {
        viewModelScope.launch {
            _ui.update { it.copy(startupError = "") }
            runCatching {
                graph.store.readableDatabase.version // abre/cria o banco e roda onCreate
                _ui.update { it.copy(ready = true) }
                refreshData()
            }.onFailure { error ->
                _ui.update { it.copy(startupError = error.message ?: "Falha ao abrir os dados locais.") }
            }
        }
    }

    fun refreshData() {
        viewModelScope.launch {
            val sources = withContext2 { graph.store.listSources() }
            val counts = withContext2 { graph.catalog.counts() }
            val programs = withContext2 { graph.store.programmeCount() }
            val progress = withContext2 { graph.catalog.continueWatching() }
            val recentMovies = withContext2 { graph.catalog.recentlyAdded(Tab.MOVIES, 14) }
            val recentSeries = withContext2 { graph.catalog.recentlyAdded(Tab.SERIES, 14) }
            val topMovies = withContext2 { graph.catalog.topRated(Tab.MOVIES, 14) }
            val favChannels = withContext2 { graph.catalog.favoriteChannels() }
            _ui.update {
                it.copy(
                    sources = sources, counts = counts, programCount = programs,
                    continueWatching = progress, recentlyAddedMovies = recentMovies, recentlyAddedSeries = recentSeries,
                    topMovies = topMovies, favoriteChannels = favChannels,
                )
            }
        }
    }

    private suspend fun <T> withContext2(block: () -> T): T =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { block() }

    // ------------------------------------------------------------ fontes

    fun addM3u(name: String, url: String, epgUrl: String) = operation("Adicionando lista…") {
        val source = graph.sources.addM3u(name, url, epgUrl)
        val summary = graph.sources.refresh(source)
        "Lista adicionada — $summary"
    }

    fun addXtream(name: String, url: String, user: String, pass: String, epgUrl: String) = operation("Conectando ao painel…") {
        val source = graph.sources.addXtream(name, url, user, pass, epgUrl)
        val summary = graph.sources.refresh(source)
        "Painel conectado — $summary"
    }

    fun addRepository(name: String, url: String) = operation("Importando repositório…") {
        val source = graph.sources.addRepository(name, url)
        "Repositório \"${source.name}\" sincronizado."
    }

    fun refreshSource(sourceId: Long) = operation("Atualizando fonte…") {
        val source = graph.store.sourceById(sourceId) ?: error("Fonte não encontrada.")
        graph.sources.refresh(source)
    }

    fun refreshEpgOnly(sourceId: Long) = operation("Sincronizando EPG…") {
        val source = graph.store.sourceById(sourceId) ?: error("Fonte não encontrada.")
        graph.sources.refreshEpgOnly(source)
    }

    /** Só EPG em todas as fontes com URL configurada — reindexa o vínculo nome a nome. */
    fun refreshEpgForAll() = operation("Reindexando EPG…") {
        val targets = withContext2 { graph.store.listSources().filter { it.enabled && (it.epgUrl.isNotBlank() || it.kind == SourceKind.XTREAM) } }
        if (targets.isEmpty()) error("Nenhuma fonte com EPG configurado.")
        var done = 0
        for (source in targets) {
            runCatching { graph.sources.refreshEpgOnly(source) }.onSuccess { done++ }
        }
        "EPG reindexado em $done de ${targets.size} fonte(s)."
    }

    fun refreshAll() = operation("Atualizando todas as fontes…") {
        graph.refreshAllNow { message -> _ui.update { it.copy(busy = message) } }
        null
    }

    fun toggleSource(source: ListSource) = operation(if (source.enabled) "Desativando…" else "Ativando…") {
        graph.sources.setEnabled(source, !source.enabled)
        "${source.name}: ${if (source.enabled) "desativada" else "ativada"}. Abra a fonte para sincronizar de novo."
    }

    fun removeSource(source: ListSource) = operation("Removendo fonte…") {
        graph.sources.remove(source.id)
        "Fonte ${source.name} removida (listas, VOD e EPG dela)."
    }

    fun setSourceInterval(source: ListSource, minutes: Int) = operation("Salvando…") {
        graph.sources.setInterval(source, minutes)
        if (minutes == 0) "Auto-update desligado para ${source.name}." else "Auto-update de ${source.name}: a cada ${minutes} min."
    }

    fun setSourceEpgUrl(source: ListSource, epgUrl: String) = operation("Salvando…") {
        graph.sources.update(source.copy(epgUrl = epgUrl.trim()))
        "URL de EPG salva. Use \"Só EPG\" para sincronizar agora."
    }

    // ------------------------------------------------------------ sistema

    fun clearCaches() = operation("Limpando índices…") {
        graph.catalog.wipeCaches()
        "Índes limpos. Atualize as fontes para reconstruir."
    }

    fun clearProgress() = operation("Limpando histórico…") {
        withContext2 { graph.store.clearProgress() }
        "Histórico de reprodução apagado."
    }

    fun resetPreferences() = operation("Restaurando…") {
        graph.savePreferences { UserPreferences() }
        "Preferências restauradas ao padrão."
    }

    fun preferences(transform: (UserPreferences) -> UserPreferences) {
        graph.savePreferences(transform)
    }

    fun elementumTest() {
        graph.checkElementum()
    }

    fun elementumUpdate() {
        graph.triggerElementumUpdate()
    }

    fun saveProgress(favKey: String, title: String, position: Long, duration: Long, kind: String, url: String, isLive: Boolean) {
        if (favKey.isBlank() || isLive || duration <= 0) return
        viewModelScope.launch { withContext2 { graph.catalog.saveProgress(favKey, title, position, duration, kind, url, false) } }
    }

    suspend fun loadProgress(favKey: String): com.smarted.tv.core.model.PlaybackProgress? =
        withContext2 { graph.catalog.progressFor(favKey) }

    fun sourceKindOf(sourceId: Long): SourceKind? = runCatching { graph.store.sourceById(sourceId)?.kind }.getOrDefault(SourceKind.M3U)

    private fun operation(label: String, block: suspend () -> String?) {
        // operações breves de escrita; UI observa busy para feedback.
        viewModelScope.launch {
            _ui.update { it.copy(busy = label, message = "", error = "") }
            try {
                val message = block()
                _ui.update { it.copy(busy = "", message = message.orEmpty()) }
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                _ui.update { it.copy(busy = "", error = error.message ?: "Falha inesperada.") }
            }
            refreshData()
        }
    }
}

/** Configuração de uma fonte pela tela de configurações (sem recriar linha). */
data class SourceDraft(
    val kind: SourceKind,
    val name: String = "",
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val epgUrl: String = "",
    val autoUpdateMinutes: Int = 720,
)

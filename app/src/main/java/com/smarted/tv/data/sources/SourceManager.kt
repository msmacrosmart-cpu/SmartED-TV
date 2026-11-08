package com.smarted.tv.data.sources

import android.util.Log
import com.smarted.tv.core.model.ListSource
import com.smarted.tv.core.model.SourceKind
import com.smarted.tv.core.repo.RepoDiff
import com.smarted.tv.core.repo.computeRepoDiff
import com.smarted.tv.data.db.IptvStore
import com.smarted.tv.data.import.SourceImporters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Ciclo de vida das fontes locais: adicionar, validar, atualizar (agora ou no prazo),
 * remover e sincronizar repositórios (índices JSON estilo OnePlay, com diff de filhos).
 */
class SourceManager(
    private val store: IptvStore,
    private val importers: SourceImporters,
    private val epgWindowHoursProvider: suspend () -> Int,
) {

    data class Busy(val sourceId: Long?, val message: String) {
        val isBusy get() = sourceId != null
    }

    private val _busy = MutableStateFlow(Busy(null, ""))
    val busy = _busy.asStateFlow()

    /** Incrementa a cada escrita relevante; a UI revalida consultas quando muda. */
    private val _revision = MutableStateFlow(0)
    val revision = _revision.asStateFlow()

    fun sources(): List<ListSource> = store.listSources()

    suspend fun addM3u(name: String, url: String, epgUrl: String): ListSource {
        requireValidUrl(url)
        val text = com.smarted.tv.data.net.Http.getText(url)
        if (!com.smarted.tv.data.net.Http.looksLikePlaylist(url, text)) error("O link não respondeu como playlist M3U/M3U8.")
        val source = ListSource(
            id = store.insertSource(ListSource(id = 0, name = name.ifBlank { defaultName(url) }, kind = SourceKind.M3U, url = url.trim(), epgUrl = epgUrl.trim())),
            name = name.ifBlank { defaultName(url) }, kind = SourceKind.M3U, url = url.trim(), epgUrl = epgUrl.trim(),
        )
        return source
    }

    suspend fun addXtream(name: String, url: String, user: String, pass: String, epgUrl: String): ListSource {
        requireValidUrl(url)
        val probe = ListSource(id = 0, name = name, kind = SourceKind.XTREAM, url = url.trim(), username = user.trim(), password = pass.trim())
        if (!com.smarted.tv.data.import.XtreamClient(probe).authenticate()) error("O painel recusou as credenciais (player_api.php).")
        return ListSource(
            id = store.insertSource(probe.copy(name = name.ifBlank { defaultName(url) })),
            name = name.ifBlank { defaultName(url) }, kind = SourceKind.XTREAM, url = url.trim(),
            username = user.trim(), password = pass.trim(), epgUrl = epgUrl.trim(),
        )
    }

    suspend fun addRepository(name: String, url: String): ListSource {
        requireValidUrl(url)
        val source = ListSource(
            id = store.insertSource(ListSource(id = 0, name = name.ifBlank { defaultName(url) }, kind = SourceKind.REPOSITORY, url = url.trim())),
            name = name.ifBlank { defaultName(url) }, kind = SourceKind.REPOSITORY, url = url.trim(),
        )
        syncRepository(source)
        return source
    }

    fun update(source: ListSource) {
        requireValidUrl(source.url)
        store.updateSource(source)
        bump()
    }

    fun setEnabled(source: ListSource, enabled: Boolean) = update(source.copy(enabled = enabled))

    fun setInterval(source: ListSource, minutes: Int) = update(source.copy(autoUpdateMinutes = minutes.coerceIn(0, 10_080)))

    fun remove(sourceId: Long) {
        store.deleteSourceCascade(sourceId)
        bump()
    }

    /** Refresh de uma fonte; repositórios sincronizam os filhos na mesma passada. */
    suspend fun refresh(source: ListSource, includeEpg: Boolean = true): String {
        return try {
            _busy.update { Busy(source.id, "Atualizando ${source.name}…") }
            val message = when (source.kind) {
                SourceKind.REPOSITORY -> syncRepository(source)
                else -> {
                    val outcome = importers.import(source, epgWindowHoursProvider(), includeEpg)
                    store.touchSource(source.id, outcome.summary, "", System.currentTimeMillis())
                    outcome.summary
                }
            }
            bump()
            message
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            val message = error.message ?: "Falha na atualização."
            store.touchSource(source.id, "Falhou", message, source.lastRefreshMs)
            bump()
            throw error
        } finally {
            _busy.value = Busy(null, "")
        }
    }

    /** Tick do agendador: atualiza tudo que está no prazo (auto-update OnePlay/Elementum-style). */
    suspend fun refreshDueSources(globalMinutes: Int): Int {
        val now = System.currentTimeMillis()
        var done = 0
        for (source in store.listSources()) {
            val minutes = if (source.autoUpdateMinutes > 0) source.autoUpdateMinutes else globalMinutes
            val due = source.enabled && minutes > 0 && (now - source.lastRefreshMs) >= minutes * 60_000L
            if (!due) continue
            runCatching {
                when (source.kind) {
                    SourceKind.REPOSITORY -> syncRepository(source)
                    else -> importers.import(source, epgWindowHoursProvider(), true)
                        .also { store.touchSource(source.id, it.summary, "", System.currentTimeMillis()) }
                }
            }.onSuccess { done++ }.onFailure { error ->
                Log.w(TAG, "auto-update falhou para ${source.name}", error)
                store.touchSource(source.id, source.status, "Auto-update: ${error.message ?: "erro"}", source.lastRefreshMs)
            }
        }
        if (done > 0) bump()
        return done
    }

    /** Só EPG (barato): re-sincroniza a grade mantendo a lista intacta. */
    suspend fun refreshEpgOnly(source: ListSource): String {
        val outcome = importers.importEpg(source, epgWindowHoursProvider())
        store.touchSource(source.id, "EPG: ${outcome.epgProgrammes} programas", "", System.currentTimeMillis())
        bump()
        return "EPG atualizado: ${outcome.epgProgrammes} programas · ${outcome.epgUnmatched} canais sem match"
    }

    /** Sincroniza um repositório: baixa índice, calcula diff e aplica nos filhos. */
    suspend fun syncRepository(parent: ListSource): String {
        val index = importers.decodeRepoIndex(parent.url)
        val existing = store.repoChildren(parent.id)
        val existingMap = existing.associate {
            RepoDiff.keyOfNameUrl(it.name, it.url, it.kind) to Triple(it.name, it.epgUrl, it.autoUpdateMinutes)
        }
        val diff = computeRepoDiff(index, existingMap)
        if (diff.isEmpty && existing.size == index.sources.size) {
            store.touchSource(parent.id, "Repositório em dia (${index.sources.size} fontes)", "", System.currentTimeMillis())
            return "Repositório em dia: ${index.sources.size} fonte(s)."
        }
        val children = index.sources.map { repo ->
            ListSource(
                id = 0, name = repo.name, kind = repo.kind, url = repo.url, username = repo.username,
                password = repo.password, epgUrl = repo.epgUrl, enabled = true, autoUpdateMinutes = repo.autoUpdateMinutes,
            )
        }
        store.replaceRepoChildren(parent.id.toString(), children)
        var imported = 0
        var errors = 0
        for (child in store.repoChildren(parent.id)) {
            runCatching { importers.import(child, epgWindowHoursProvider(), true) }
                .onSuccess {
                    imported++
                    store.touchSource(child.id, it.summary, "", System.currentTimeMillis())
                }
                .onFailure {
                    errors++
                    store.touchSource(child.id, "Falhou", it.message ?: "erro", System.currentTimeMillis())
                }
        }
        store.touchSource(parent.id, "Índice: ${index.sources.size} fonte(s) · $imported prontas · $errors com erro", "", System.currentTimeMillis())
        bump()
        return "Repositório sincronizado: $imported fonte(s) atualizadas (${errors} com erro)."
    }

    /** Estado vazio inteligente: se não há fonte e há URL padrão configurada, nada é inventado. */
    fun hasAnyContent(): Boolean {
        val sources = store.listSources()
        if (sources.isEmpty()) return false
        return store.channelCount(sources.first().id) > 0 || sources.any { store.channelCount(it.id) > 0 || store.vodCount(it.id) > 0 }
    }

    private fun bump() = _revision.update { it + 1 }

    private fun requireValidUrl(url: String) {
        val trimmed = url.trim()
        require(
            trimmed.startsWith("http://") || trimmed.startsWith("https://") ||
                trimmed.startsWith("content://") || trimmed.startsWith("file://"),
        ) { "Use um endereço http(s) (lista/painel/índice) ou um arquivo local via seletor." }
    }

    private fun defaultName(url: String): String =
        url.substringAfter("//").substringBefore('/').substringBefore(':').take(40).ifBlank { "Fonte" }

    private companion object { const val TAG = "SmartEDSources" }
}
